package com.monumentogram.dora.poc.recovery.journal

import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeIdentityInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRangeIdentityInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingWitnessInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeAttempt
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRangeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointIdentityInput
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.contract.StreamSemanticOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRecoveryStreamingJournalTest {
    @Test
    fun `outcome insert marks successful ends and rereads exact parent and child`() {
        val database = RecordingDatabase()
        val journal = AndroidRecoveryStreamingJournal(database)
        val attempt = fixture()

        val result = journal.persistOutcome(attempt)

        assertEquals(RecoveryStreamingJournalResult.Receipt(attempt.outcome.outcomeId, attempt.range!!.rangeIntentId, false), result)
        assertEquals(listOf("begin", "outcome", "range", "successful", "end"), database.events)
        assertEquals(attempt.outcome, journal.outcomeById(attempt.outcome.outcomeId))
        assertEquals(attempt.range, journal.rangeByOutcome(attempt.outcome.outcomeId))
        assertEquals(listOf(attempt.range), journal.activeRanges(attempt.outcome.runId, "stream/stream.ct"))
    }

    @Test
    fun `exact replay does not begin a second transaction`() {
        val database = RecordingDatabase()
        val journal = AndroidRecoveryStreamingJournal(database)
        val attempt = fixture()
        journal.persistOutcome(attempt)

        val replay = journal.persistOutcome(attempt)

        assertEquals(RecoveryStreamingJournalResult.Receipt(attempt.outcome.outcomeId, attempt.range!!.rangeIntentId, true), replay)
        assertEquals(1, database.events.count { it == "begin" })
    }

    @Test
    fun `same witness with a different outcome id is fatal before mutation`() {
        val database = RecordingDatabase()
        val journal = AndroidRecoveryStreamingJournal(database)
        val attempt = fixture()
        database.outcomes +=
            RecoveryStreamingOutcomeRow.from(
                RecoveryStreamingOutcomeIdentityInput.validAuthenticationFailure(
                    attempt.outcome.witness(),
                    8193UL,
                    sha("source"),
                    4056UL,
                    sha("returned-other"),
                    4096UL,
                )
            )

        val result = journal.persistOutcome(attempt)

        assertTrue(result is RecoveryStreamingJournalResult.Fatal)
        assertEquals(0, database.events.count { it == "begin" })
    }

    @Test
    fun `required range missing after commit is structural fatal`() {
        val database = RecordingDatabase(dropRangeOnReadback = true)
        val journal = AndroidRecoveryStreamingJournal(database)

        val result = journal.persistOutcome(fixture())

        assertTrue(result is RecoveryStreamingJournalResult.Fatal)
        assertEquals(1, database.events.count { it == "end" })
    }

    @Test
    fun `operational failure is retry and proven rollback returns semantic outcome`() {
        val operational = RecordingDatabase(failInsert = true)
        val journal = AndroidRecoveryStreamingJournal(operational)
        val attempt = fixture()
        assertTrue(journal.persistOutcome(attempt) is RecoveryStreamingJournalResult.Retry)

        val rollback = RecordingDatabase(rollbackOnEnd = true)
        val rollbackJournal = AndroidRecoveryStreamingJournal(rollback)
        assertEquals(
            RecoveryStreamingJournalResult.Original(attempt.semanticOutcome),
            rollbackJournal.persistOutcome(attempt),
        )
    }

    @Test
    fun `checkpoint insert is exact replay and generation split brain is fatal`() {
        val database = RecordingDatabase()
        val journal = AndroidRecoveryStreamingJournal(database)
        val checkpoint = checkpoint()

        assertEquals(
            RecoveryStreamingJournalResult.CheckpointReceipt(checkpoint.checkpointIdentity, false),
            journal.insertCheckpoint(checkpoint),
        )
        assertEquals(
            RecoveryStreamingJournalResult.CheckpointReceipt(checkpoint.checkpointIdentity, true),
            journal.insertCheckpoint(checkpoint),
        )
        val split = checkpoint.copy(
            checkpointSha256 = sha("other-checkpoint"),
            checkpointIdentity = RecoveryStreamingIdentity.checkpoint(
                checkpoint.identityInput().copy(checkpointSha256 = sha("other-checkpoint"))
            ),
        )
        database.checkpoints += split

        assertTrue(journal.insertCheckpoint(checkpoint) is RecoveryStreamingJournalResult.Fatal)
    }

    private fun fixture(): RecoveryStreamingOutcomeAttempt {
        val runId = RunId.fromBytes(ByteArray(16) { it.toByte() })
        val witness = RecoveryStreamingWitnessInput(
            runId, 1UL, sha("checkpoint"), 8192UL, 4056UL, sha("oracle-id"), 8137UL,
            sha("oracle"), 8192UL, sha("source-before"), sha("snapshot"),
        )
        val input = RecoveryStreamingOutcomeIdentityInput.validAuthenticationFailure(
            witness, 8193UL, sha("source"), 8136UL, sha("returned"), 8192UL,
        )
        val outcome = RecoveryStreamingOutcomeRow.from(input)
        val range = RecoveryStreamingRangeRow.exact(outcome, sha("range"))
        return RecoveryStreamingOutcomeAttempt(outcome, range, StreamSemanticOutcome.PERSISTED_VALID)
    }

    private fun checkpoint(): RecoveryStreamingCheckpointRow {
        val runId = RunId.fromBytes(ByteArray(16) { it.toByte() })
        val input = RecoveryStreamingCheckpointIdentityInput(
            runId, 1UL, 2UL, 8192UL, sha("prefix"), 4056UL,
            "checkpoints/g-00000000000000000001.ct", 1UL, sha("checkpoint"),
            "key-envelopes/checkpoint-g-00000000000000000001.ks", 1UL, sha("checkpoint-key"),
            "stream/stream.ct", "key-envelopes/stream.ks", 1UL, sha("stream-key"), Sha256Value.ZERO,
        )
        return RecoveryStreamingCheckpointRow(
            input.runId, input.generation, input.durableNonFinalSegmentCount,
            input.streamCiphertextPrefixBytes, input.streamCiphertextPrefixSha256,
            input.committedEnd, input.checkpointRelativeName, input.checkpointBytes,
            input.checkpointSha256, input.checkpointEnvelopeRelativeName,
            input.checkpointEnvelopeBytes, input.checkpointEnvelopeSha256,
            input.streamRelativeName, input.streamEnvelopeRelativeName,
            input.streamEnvelopeBytes, input.streamEnvelopeSha256,
            input.previousCheckpointSha256, RecoveryStreamingIdentity.checkpoint(input),
        )
    }

    private fun sha(value: String) = Sha256Value.calculate(value.toByteArray())

    private class RecordingDatabase(
        private val failInsert: Boolean = false,
        private val rollbackOnEnd: Boolean = false,
        private val dropRangeOnReadback: Boolean = false,
    ) : RecoveryStreamingJournalDatabase {
        val events = mutableListOf<String>()
        val checkpoints = mutableListOf<RecoveryStreamingCheckpointRow>()
        val outcomes = mutableListOf<RecoveryStreamingOutcomeRow>()
        val ranges = mutableListOf<RecoveryStreamingRangeRow>()

        override fun checkpoints(runId: RunId): List<RecoveryStreamingCheckpointRow> = checkpoints.filter { it.runId == runId }
        override fun outcomeById(id: Sha256Value): List<RecoveryStreamingOutcomeRow> = outcomes.filter { it.outcomeId == id }
        override fun outcomesByWitness(runId: RunId, witnessId: Sha256Value): List<RecoveryStreamingOutcomeRow> = outcomes.filter { it.runId == runId && it.sourceWitnessId == witnessId }
        override fun rangesByOutcome(outcomeId: Sha256Value): List<RecoveryStreamingRangeRow> = if (dropRangeOnReadback) emptyList() else ranges.filter { it.outcomeId == outcomeId }
        override fun activeRanges(runId: RunId, source: String): List<RecoveryStreamingRangeRow> = ranges.filter { it.runId == runId && it.sourceRelativeName == source }
        override fun beginTransactionNonExclusive(): RecoveryStreamingJournalTransaction {
            events += "begin"
            return object : RecoveryStreamingJournalTransaction {
                override val provenRolledBack: Boolean
                    get() = rollbackOnEnd

                override fun insertCheckpoint(row: RecoveryStreamingCheckpointRow) { events += "checkpoint"; checkpoints += row }

                override fun insertOutcome(row: RecoveryStreamingOutcomeRow) { events += "outcome"; if (failInsert) error("insert") else outcomes += row }
                override fun insertRange(row: RecoveryStreamingRangeRow) { events += "range"; if (failInsert) error("insert") else ranges += row }
                override fun setSuccessful() { events += "successful" }
                override fun end() { events += "end"; if (rollbackOnEnd) { outcomes.clear(); ranges.clear() } }
            }
        }
    }
}
