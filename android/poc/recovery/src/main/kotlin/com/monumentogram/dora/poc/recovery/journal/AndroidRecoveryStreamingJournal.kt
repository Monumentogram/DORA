package com.monumentogram.dora.poc.recovery.journal

import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournal
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeAttempt
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRangeRow
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value

/**
 * The Android-facing streaming journal uses this deliberately small adapter so host JVM tests can
 * exercise transaction ordering and all readback decisions without a device SQLite dependency.
 */
internal interface RecoveryStreamingJournalDatabase {
    fun checkpoints(runId: RunId): List<RecoveryStreamingCheckpointRow>

    fun outcomeById(id: Sha256Value): List<RecoveryStreamingOutcomeRow>

    fun outcomesByWitness(runId: RunId, witnessId: Sha256Value): List<RecoveryStreamingOutcomeRow>

    fun rangesByOutcome(outcomeId: Sha256Value): List<RecoveryStreamingRangeRow>

    fun activeRanges(runId: RunId, source: String): List<RecoveryStreamingRangeRow>

    fun rangeByIntentId(id: Sha256Value): List<RecoveryStreamingRangeRow> = emptyList()

    fun rangesBySourceTuple(row: RecoveryStreamingRangeRow): List<RecoveryStreamingRangeRow> =
        emptyList()

    fun beginTransactionNonExclusive(): RecoveryStreamingJournalTransaction
}

internal interface RecoveryStreamingJournalTransaction {
    /** True only when the adapter can prove that the just-ended transaction rolled back. */
    val provenRolledBack: Boolean
        get() = false

    fun insertCheckpoint(row: RecoveryStreamingCheckpointRow) {
        error("Checkpoint inserts are not implemented by this adapter")
    }

    fun insertOutcome(row: RecoveryStreamingOutcomeRow)

    fun insertRange(row: RecoveryStreamingRangeRow)

    fun setSuccessful()

    fun end()
}

internal class AndroidRecoveryStreamingJournal(
    private val database: RecoveryStreamingJournalDatabase,
) : RecoveryStreamingJournal {
    override fun checkpointChain(runId: RunId): List<RecoveryStreamingCheckpointRow> =
        database.checkpoints(runId).also(::requireCheckpointChain)

    override fun outcomeById(outcomeId: Sha256Value): RecoveryStreamingOutcomeRow? =
        singleOrNull(database.outcomeById(outcomeId), "JOURNAL_STRUCTURAL")

    override fun outcomeByWitness(
        runId: RunId,
        witnessId: Sha256Value,
    ): RecoveryStreamingOutcomeRow? =
        singleOrNull(database.outcomesByWitness(runId, witnessId), "JOURNAL_STRUCTURAL")

    override fun rangeByOutcome(outcomeId: Sha256Value): RecoveryStreamingRangeRow? =
        singleOrNull(database.rangesByOutcome(outcomeId), "JOURNAL_STRUCTURAL")

    override fun activeRanges(runId: RunId, sourceRelativeName: String): List<RecoveryStreamingRangeRow> {
        require(sourceRelativeName == "stream/stream.ct") { "Streaming source is invalid" }
        return database.activeRanges(runId, sourceRelativeName).also { ranges ->
            ranges.forEach { range ->
                require(range.runId == runId && range.sourceRelativeName == sourceRelativeName)
            }
        }
    }

    override fun insertCheckpoint(row: RecoveryStreamingCheckpointRow): RecoveryStreamingJournalResult {
        val existing = database.checkpoints(row.runId)
        requireCheckpointChain(existing)
        val sameGeneration = existing.filter { it.generation == row.generation }
        if (sameGeneration.size > 1) {
            return fatalCheckpoints("STREAM_CHECKPOINT_SPLIT_BRAIN", sameGeneration)
        }
        if (sameGeneration.singleOrNull() != null) {
            return if (sameGeneration.single() == row) {
                RecoveryStreamingJournalResult.CheckpointReceipt(row.checkpointIdentity, true)
            } else {
                fatalCheckpoints("STREAM_CHECKPOINT_SPLIT_BRAIN", sameGeneration)
            }
        }
        val transaction = database.beginTransactionNonExclusive()
        return try {
            transaction.insertCheckpoint(row)
            transaction.setSuccessful()
            transaction.end()
            val reread = database.checkpoints(row.runId).filter { it.generation == row.generation }
            if (reread.size == 1 && reread.single() == row) {
                RecoveryStreamingJournalResult.CheckpointReceipt(row.checkpointIdentity, false)
            } else if (reread.isEmpty() && transaction.provenRolledBack) {
                RecoveryStreamingJournalResult.Retry("JOURNAL_OPERATIONAL")
            } else if (reread.isEmpty()) {
                RecoveryStreamingJournalResult.Retry("JOURNAL_COMMIT_STATE_UNRESOLVED")
            } else {
                fatalCheckpoints("STREAM_CHECKPOINT_SPLIT_BRAIN", reread)
            }
        } catch (_: Exception) {
            endQuietly(transaction)
            resolveCheckpointAfterFailure(row, transaction)
        }
    }

    override fun persistOutcome(attempt: RecoveryStreamingOutcomeAttempt): RecoveryStreamingJournalResult {
        resolveExisting(attempt)?.let { return it }
        val rangeCollision = attempt.range?.let { range ->
            database.rangeByIntentId(range.rangeIntentId).any { it != range } ||
                database.rangesBySourceTuple(range).any { it != range }
        } ?: false
        if (rangeCollision) {
            return fatalIds("STREAM_RANGE_QUARANTINE_COLLISION", emptyList())
        }

        val transaction = try {
            database.beginTransactionNonExclusive()
        } catch (_: Exception) {
            return RecoveryStreamingJournalResult.Retry("JOURNAL_OPERATIONAL")
        }
        return try {
            transaction.insertOutcome(attempt.outcome)
            attempt.range?.let(transaction::insertRange)
            transaction.setSuccessful()
            transaction.end()
            receiptAfterReadback(attempt, transaction, replayed = false)
        } catch (_: Exception) {
            endQuietly(transaction)
            resolveAfterFailure(attempt, transaction)
        }
    }

    private fun resolveExisting(
        attempt: RecoveryStreamingOutcomeAttempt,
    ): RecoveryStreamingJournalResult? {
        val byId = database.outcomeById(attempt.outcome.outcomeId)
        val byWitness = database.outcomesByWitness(attempt.outcome.runId, attempt.outcome.sourceWitnessId)
        if (byId.size > 1 || byWitness.size > 1) {
            return fatalOutcomes("JOURNAL_STRUCTURAL", byId + byWitness)
        }
        val existingId = byId.singleOrNull()
        val existingWitness = byWitness.singleOrNull()
        if (existingId == null && existingWitness == null) return null
        if (existingId != attempt.outcome || existingWitness != attempt.outcome) {
            return fatalOutcomes(
                "JOURNAL_ATTEMPT_CONFLICT",
                listOfNotNull(existingId, existingWitness),
            )
        }
        return receiptAfterReadback(attempt, null, replayed = true)
    }

    private fun receiptAfterReadback(
        attempt: RecoveryStreamingOutcomeAttempt,
        transaction: RecoveryStreamingJournalTransaction?,
        replayed: Boolean,
    ): RecoveryStreamingJournalResult {
        val parents = database.outcomeById(attempt.outcome.outcomeId)
        val witnesses = database.outcomesByWitness(attempt.outcome.runId, attempt.outcome.sourceWitnessId)
        if (parents.size != 1 || witnesses.size != 1) {
            return absentOrAmbiguous(attempt, transaction)
        }
        if (parents.single() != attempt.outcome || witnesses.single() != attempt.outcome) {
            return fatalOutcomes("JOURNAL_ATTEMPT_CONFLICT", parents + witnesses)
        }
        val children = database.rangesByOutcome(attempt.outcome.outcomeId)
        val expected = attempt.range
        if (children.size > 1 || (expected == null && children.isNotEmpty()) ||
            (expected != null && (children.size != 1 || children.single() != expected))
        ) {
            return fatalIds(
                "JOURNAL_STRUCTURAL",
                children.map { it.rangeIntentId } + attempt.outcome.outcomeId,
            )
        }
        return RecoveryStreamingJournalResult.Receipt(
            attempt.outcome.outcomeId,
            expected?.rangeIntentId,
            replayed,
        )
    }

    private fun resolveAfterFailure(
        attempt: RecoveryStreamingOutcomeAttempt,
        transaction: RecoveryStreamingJournalTransaction,
    ): RecoveryStreamingJournalResult {
        val readback = receiptAfterReadback(attempt, transaction, replayed = true)
        return when (readback) {
            is RecoveryStreamingJournalResult.Receipt,
            is RecoveryStreamingJournalResult.Fatal -> readback
            else -> if (transaction.provenRolledBack) {
                RecoveryStreamingJournalResult.Original(attempt.semanticOutcome)
            } else {
                RecoveryStreamingJournalResult.Retry("JOURNAL_COMMIT_STATE_UNRESOLVED")
            }
        }
    }

    private fun absentOrAmbiguous(
        attempt: RecoveryStreamingOutcomeAttempt,
        transaction: RecoveryStreamingJournalTransaction?,
    ): RecoveryStreamingJournalResult =
        if (transaction?.provenRolledBack == true) {
            RecoveryStreamingJournalResult.Original(attempt.semanticOutcome)
        } else {
            RecoveryStreamingJournalResult.Retry("JOURNAL_COMMIT_STATE_UNRESOLVED")
        }

    private fun resolveCheckpointAfterFailure(
        row: RecoveryStreamingCheckpointRow,
        transaction: RecoveryStreamingJournalTransaction,
    ): RecoveryStreamingJournalResult {
        val reread = database.checkpoints(row.runId).filter { it.generation == row.generation }
        return when {
            reread.size == 1 && reread.single() == row ->
                RecoveryStreamingJournalResult.CheckpointReceipt(row.checkpointIdentity, true)
            reread.size > 1 || reread.singleOrNull()?.let { it != row } == true ->
                fatalCheckpoints("STREAM_CHECKPOINT_SPLIT_BRAIN", reread)
            transaction.provenRolledBack -> RecoveryStreamingJournalResult.Retry("JOURNAL_OPERATIONAL")
            else -> RecoveryStreamingJournalResult.Retry("JOURNAL_COMMIT_STATE_UNRESOLVED")
        }
    }

    private fun requireCheckpointChain(rows: List<RecoveryStreamingCheckpointRow>) {
        val ordered = rows.sortedBy { it.generation }
        require(ordered.size == rows.map { it.generation }.toSet().size) { "Checkpoint generation duplicates" }
        ordered.firstOrNull()?.let { first ->
            require(first.previousCheckpointSha256 == Sha256Value.ZERO) {
                "Checkpoint genesis is invalid"
            }
        }
        ordered.zipWithNext().forEach { (previous, next) ->
            require(next.previousCheckpointSha256 == previous.checkpointSha256) { "Checkpoint chain is invalid" }
        }
    }

    private fun fatalOutcomes(
        classification: String,
        rows: List<RecoveryStreamingOutcomeRow>,
    ): RecoveryStreamingJournalResult.Fatal =
        RecoveryStreamingJournalResult.Fatal(classification, rows.map { it.outcomeId }.distinct())

    private fun fatalCheckpoints(
        classification: String,
        rows: List<RecoveryStreamingCheckpointRow>,
    ): RecoveryStreamingJournalResult.Fatal =
        RecoveryStreamingJournalResult.Fatal(classification, rows.map { it.checkpointIdentity }.distinct())

    private fun fatalIds(classification: String, ids: List<Sha256Value>): RecoveryStreamingJournalResult.Fatal =
        RecoveryStreamingJournalResult.Fatal(classification, ids.distinct())

    private fun <T> singleOrNull(rows: List<T>, classification: String): T? {
        require(rows.size <= 1) { classification }
        return rows.singleOrNull()
    }

    private fun endQuietly(transaction: RecoveryStreamingJournalTransaction) {
        runCatching { transaction.end() }
    }
}
