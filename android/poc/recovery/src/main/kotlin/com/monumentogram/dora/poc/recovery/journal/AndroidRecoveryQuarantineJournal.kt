package com.monumentogram.dora.poc.recovery.journal

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.monumentogram.dora.poc.recovery.candidate.QuarantineBootstrapBinding
import com.monumentogram.dora.poc.recovery.candidate.QuarantineIntentState
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineIntentRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineJournal
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineTransaction
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value

@Suppress("MagicNumber")
internal class AndroidRecoveryQuarantineJournal(context: Context) : RecoveryQuarantineJournal {
    private val applicationContext = context.applicationContext

    override fun load(intentId: Sha256Value): RecoveryQuarantineIntentRow? =
        AndroidRecoveryJournalDatabase.writable(applicationContext)
            .query(
                RecoveryJournalSchema.QUARANTINE_TABLE,
                COLUMNS,
                "hex(intent_id)=?",
                arrayOf(intentId.toLowercaseHex().uppercase()),
                null,
                null,
                null,
            )
            .use { cursor ->
                if (!cursor.moveToFirst()) null
                else {
                    val row = cursor.row()
                    check(!cursor.moveToNext()) { "Ambiguous quarantine intent readback" }
                    row
                }
            }

    override fun loadBySource(input: RecoveryQuarantineIntentInput): RecoveryQuarantineIntentRow? =
        queryOne(
            "run_id=? AND candidate_id=? AND source_relative_name=? AND hex(source_sha256)=?",
            arrayOf(
                input.runId.toCanonicalString(),
                input.candidate.contractId,
                input.sourceRelativeName,
                input.sourceSha256.toLowercaseHex().uppercase(),
            ),
        )

    override fun loadPending(runId: RunId): List<RecoveryQuarantineIntentRow> {
        return loadAll(runId).filter { it.state == QuarantineIntentState.PENDING }
    }

    fun loadAll(runId: RunId): List<RecoveryQuarantineIntentRow> {
        val rows = mutableListOf<RecoveryQuarantineIntentRow>()
        AndroidRecoveryJournalDatabase.writable(applicationContext)
            .query(
                RecoveryJournalSchema.QUARANTINE_TABLE,
                COLUMNS,
                "run_id=?",
                arrayOf(runId.toCanonicalString()),
                null,
                null,
                "intent_id ASC",
            )
            .use { cursor -> while (cursor.moveToNext()) rows += cursor.row() }
        return java.util.Collections.unmodifiableList(rows)
    }

    private fun queryOne(selection: String, arguments: Array<String>) =
        AndroidRecoveryJournalDatabase.writable(applicationContext)
            .query(
                RecoveryJournalSchema.QUARANTINE_TABLE,
                COLUMNS,
                selection,
                arguments,
                null,
                null,
                null,
            )
            .use { cursor ->
                if (!cursor.moveToFirst()) null
                else {
                    val row = cursor.row()
                    check(!cursor.moveToNext()) { "Ambiguous quarantine source readback" }
                    row
                }
            }

    private fun android.database.Cursor.row(): RecoveryQuarantineIntentRow {
        val runId = RunId.fromCanonicalString(getString(1))
        val input =
            RecoveryQuarantineIntentInput(
                RecoveryCandidate.fromContractId(getString(2)),
                runId,
                getString(8),
                RecoveryQuarantineArtifactRole.valueOf(getString(6)),
                getLong(10).toULong(),
                Sha256Value.fromBytes(getBlob(11)),
            )
        val bootstrapBinding = QuarantineBootstrapBinding.valueOf(getString(3))
        val bootstrapRunId = if (isNull(4)) null else getString(4)
        val bootstrapCandidateId = if (isNull(5)) null else getString(5)
        check(
            (bootstrapBinding == QuarantineBootstrapBinding.ABSENT &&
                bootstrapRunId == null &&
                bootstrapCandidateId == null) ||
                (bootstrapBinding == QuarantineBootstrapBinding.PRESENT &&
                    bootstrapRunId == runId.toCanonicalString() &&
                    bootstrapCandidateId == input.candidate.contractId)
        ) {
            "Quarantine bootstrap binding readback is inconsistent"
        }
        val row =
            RecoveryQuarantineIntentRow(
                Sha256Value.fromBytes(getBlob(0)),
                input,
                RecoveryQuarantineObservedState.valueOf(getString(7)),
                bootstrapBinding,
                getString(9),
                QuarantineIntentState.valueOf(getString(12)),
            )
        check(row.intentId == RecoveryQuarantineIntent.calculate(input)) {
            "Quarantine intent identity mismatch"
        }
        check(row.destinationRelativeName == RecoveryQuarantineIntent.destination(input)) {
            "Quarantine destination identity mismatch"
        }
        return row
    }

    override fun beginNonExclusive(): RecoveryQuarantineTransaction {
        val database = AndroidRecoveryJournalDatabase.writable(applicationContext)
        database.beginTransactionNonExclusive()
        return Transaction(database)
    }

    private class Transaction(private val database: SQLiteDatabase) :
        RecoveryQuarantineTransaction {
        override fun insert(row: RecoveryQuarantineIntentRow) {
            val values =
                ContentValues().apply {
                    put("intent_id", row.intentId.toByteArray())
                    put("run_id", row.input.runId.toCanonicalString())
                    put("candidate_id", row.input.candidate.contractId)
                    put("bootstrap_binding", row.bootstrapBinding.name)
                    if (row.bootstrapBinding == QuarantineBootstrapBinding.PRESENT) {
                        put("bootstrap_run_id", row.input.runId.toCanonicalString())
                        put("bootstrap_candidate_id", row.input.candidate.contractId)
                    } else {
                        putNull("bootstrap_run_id")
                        putNull("bootstrap_candidate_id")
                    }
                    put("artifact_role", row.input.artifactRole.contractId)
                    put("observed_state", row.recordedObservedState.name)
                    put("source_relative_name", row.input.sourceRelativeName)
                    put("destination_relative_name", row.destinationRelativeName)
                    put("source_bytes", row.input.sourceBytes.toLong())
                    put("source_sha256", row.input.sourceSha256.toByteArray())
                    put("state", row.state.name)
                }
            database.insertOrThrow(RecoveryJournalSchema.QUARANTINE_TABLE, null, values)
        }

        override fun complete(intentId: Sha256Value) {
            val values =
                ContentValues().apply { put("state", QuarantineIntentState.COMPLETED.name) }
            check(
                database.update(
                    RecoveryJournalSchema.QUARANTINE_TABLE,
                    values,
                    "hex(intent_id)=? AND state='PENDING'",
                    arrayOf(intentId.toLowercaseHex().uppercase()),
                ) == 1
            ) {
                "Quarantine completion requires one exact pending row"
            }
        }

        override fun markSuccessful() = database.setTransactionSuccessful()

        override fun end() = database.endTransaction()
    }

    private companion object {
        val COLUMNS =
            arrayOf(
                "intent_id",
                "run_id",
                "candidate_id",
                "bootstrap_binding",
                "bootstrap_run_id",
                "bootstrap_candidate_id",
                "artifact_role",
                "observed_state",
                "source_relative_name",
                "destination_relative_name",
                "source_bytes",
                "source_sha256",
                "state",
            )
    }
}
