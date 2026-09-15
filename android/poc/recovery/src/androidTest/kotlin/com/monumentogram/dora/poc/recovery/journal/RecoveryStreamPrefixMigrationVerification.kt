@file:Suppress("LongMethod", "MagicNumber")

package com.monumentogram.dora.poc.recovery.journal

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import com.monumentogram.dora.poc.recovery.candidate.RecoveryStreamingIntentBuilder
import com.monumentogram.dora.poc.recovery.candidate.RecoveryStreamingValidatedIntentFacts
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointIdentityInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalReadResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeAttempt
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRangeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingWitnessInput
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.contract.StreamDecision
import com.monumentogram.dora.poc.recovery.contract.StreamSemanticOutcome
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Runs only inside the existing owned JOURNAL_CONNECTIONS preflight fixture. */
internal object RecoveryStreamPrefixMigrationVerification {
    fun verify(parent: Context) {
        val context =
            object : ContextWrapper(parent) {
                override fun getNoBackupFilesDir() =
                    File(parent.noBackupFilesDir, "stream-prefix-migration")
            }
        check(context.noBackupFilesDir.mkdir())
        val path = RecoveryJournalSqliteHelper.databasePath(context)
        val attempt: RecoveryStreamingOutcomeAttempt
        val before: List<String>
        SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
            database.setForeignKeyConstraintsEnabled(true)
            RecoveryJournalSchema.createV4(database)
            database.version = 4
            attempt = seedHistoricalFatal(database)
            before = rows(database)
            for (stop in RecoveryStreamPrefixSchema.Step.entries) {
                failAndRollback(database, stop)
                assertEquals(4, database.version)
                RecoveryJournalSchema.requireExactV4(database)
                assertEquals(before, rows(database))
            }
        }
        // Real SQLiteOpenHelper upgrade, version assignment, close and reopen.
        repeat(2) {
            RecoveryJournalSqliteHelper(context).use { helper ->
                val database = helper.writableDatabase
                assertEquals(5, database.version)
                RecoveryJournalSchema.requireExactV5(database)
                assertEquals(before, rows(database))
                val journal =
                    AndroidRecoveryStreamingJournal(
                        AndroidSqliteRecoveryStreamingJournalDatabase(database)
                    )
                val read = journal.outcomeById(attempt.outcome.outcomeId)
                assertTrue(read is RecoveryStreamingJournalReadResult.Value)
                read as RecoveryStreamingJournalReadResult.Value
                assertEquals(attempt.outcome, read.value)
                assertEquals(StreamDecision.FATAL, read.value!!.decision)
                val replay = journal.persistOutcome(attempt)
                assertTrue(replay is RecoveryStreamingJournalResult.Receipt)
                replay as RecoveryStreamingJournalResult.Receipt
                assertTrue(replay.replayed)
                assertEquals(attempt.outcome.outcomeId, replay.outcomeId)
                assertEquals(attempt.range!!.rangeIntentId, replay.rangeIntentId)
                assertEquals(before, rows(database))
            }
        }
        println(
            "STREAM_PREFIX_SCHEMA5_MIGRATION verified=true historicalFatalPreserved=true " +
                "rangePreserved=true exactReplay=true rollbackSteps=${RecoveryStreamPrefixSchema.Step.entries.size}"
        )
    }

    private class InjectedMigrationFailure : RuntimeException()

    private fun failAndRollback(database: SQLiteDatabase, stop: RecoveryStreamPrefixSchema.Step) {
        database.beginTransactionNonExclusive()
        try {
            try {
                RecoveryStreamPrefixSchema.migrate(database) { step ->
                    if (step == stop) throw InjectedMigrationFailure()
                }
                error("Migration failpoint was not reached: $stop")
            } catch (_: InjectedMigrationFailure) {
                // Same framework rollback primitive, with no successful transaction mark.
            }
        } finally {
            database.endTransaction()
        }
    }

    private fun seedHistoricalFatal(database: SQLiteDatabase): RecoveryStreamingOutcomeAttempt {
        val run = RunId.fromBytes(ByteArray(16) { (it + 31).toByte() })
        val hash = Sha256Value.calculate(byteArrayOf(1))
        database.execSQL(
            "INSERT INTO recovery_run_bootstrap_v1 VALUES (?,?,?,?,?,?,?)",
            arrayOf(
                run.toCanonicalString(),
                "REC-STREAM-TINK",
                "key-confirmation/run.kc",
                1,
                hash.toByteArray(),
                hash.toByteArray(),
                "VALID",
            ),
        )
        val input =
            RecoveryStreamingCheckpointIdentityInput(
                run,
                1UL,
                2UL,
                8192UL,
                hash,
                4056UL,
                "checkpoints/g-00000000000000000001.ct",
                1UL,
                hash,
                "key-envelopes/checkpoint-g-00000000000000000001.ks",
                1UL,
                hash,
                "stream/stream.ct",
                "key-envelopes/stream.ks",
                1UL,
                hash,
                Sha256Value.ZERO,
            )
        val checkpoint =
            RecoveryStreamingCheckpointRow(
                input.runId,
                input.generation,
                input.durableNonFinalSegmentCount,
                input.streamCiphertextPrefixBytes,
                input.streamCiphertextPrefixSha256,
                input.committedEnd,
                input.checkpointRelativeName,
                input.checkpointBytes,
                input.checkpointSha256,
                input.checkpointEnvelopeRelativeName,
                input.checkpointEnvelopeBytes,
                input.checkpointEnvelopeSha256,
                input.streamRelativeName,
                input.streamEnvelopeRelativeName,
                input.streamEnvelopeBytes,
                input.streamEnvelopeSha256,
                input.previousCheckpointSha256,
                RecoveryStreamingIdentity.checkpoint(input),
            )
        val journal =
            AndroidRecoveryStreamingJournal(AndroidSqliteRecoveryStreamingJournalDatabase(database))
        assertTrue(
            journal.insertCheckpoint(checkpoint) is RecoveryStreamingJournalResult.CheckpointReceipt
        )
        val witness =
            RecoveryStreamingWitnessInput(
                    run,
                    1UL,
                    checkpoint.checkpointIdentity,
                    8192UL,
                    4056UL,
                    RecoveryStreamingIdentity.oracle(8136UL, hash, run),
                    8136UL,
                    hash,
                    12288UL,
                    hash,
                    null,
                )
                .let {
                    it.copy(
                        controllerSnapshotSha256 = RecoveryStreamingIdentity.controllerSnapshot(it)
                    )
                }
        val outcome =
            RecoveryStreamingIntentBuilder.buildOutcome(
                RecoveryStreamingValidatedIntentFacts(witness, 10240UL, hash, false, false, null)
            )
        val range = RecoveryStreamingRangeRow.exact(outcome, hash)
        val attempt =
            RecoveryStreamingOutcomeAttempt(outcome, range, StreamSemanticOutcome.PERSISTED_FATAL)
        assertTrue(journal.persistOutcome(attempt) is RecoveryStreamingJournalResult.Receipt)
        return attempt
    }

    private fun rows(database: SQLiteDatabase): List<String> = buildList {
        for (table in
            listOf(
                RecoveryJournalSchema.RUN_TABLE,
                RecoveryJournalSchema.STREAM_CHECKPOINT_TABLE,
                RecoveryJournalSchema.STREAM_OUTCOME_TABLE,
                RecoveryJournalSchema.STREAM_RANGE_TABLE,
            )) {
            val columns = mutableListOf<String>()
            database.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                while (cursor.moveToNext()) columns += cursor.getString(1)
            }
            val projection = columns.joinToString(",") { "typeof($it),hex(CAST($it AS BLOB))" }
            database
                .rawQuery("SELECT $projection FROM $table ORDER BY ${columns.first()}", null)
                .use { cursor ->
                    while (cursor.moveToNext()) for (index in 0 until cursor.columnCount) add(
                        cursor.getString(index)
                    )
                }
        }
    }
}
