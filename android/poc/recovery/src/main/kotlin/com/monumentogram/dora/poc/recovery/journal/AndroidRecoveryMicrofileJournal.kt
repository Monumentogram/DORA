package com.monumentogram.dora.poc.recovery.journal

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.monumentogram.dora.poc.recovery.bootstrap.KeyConfirmationState
import com.monumentogram.dora.poc.recovery.candidate.CandidateBootstrapRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryCandidateSnapshot
import com.monumentogram.dora.poc.recovery.candidate.RecoveryManifestPublicationRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofileJournal
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofileTransaction
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofileUnitRow
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value

internal class AndroidRecoveryMicrofileJournal(context: Context) : RecoveryMicrofileJournal {
    private val applicationContext = context.applicationContext

    override fun loadSnapshot(runId: RunId): RecoveryCandidateSnapshot {
        val database = AndroidRecoveryJournalDatabase.writable(applicationContext)
        val id = runId.toCanonicalString()
        return RecoveryCandidateSnapshot(
            loadBootstrap(database, id),
            loadUnits(database, id),
            loadPublications(database, id),
        )
    }

    override fun beginNonExclusive(): RecoveryMicrofileTransaction {
        val database = AndroidRecoveryJournalDatabase.writable(applicationContext)
        database.beginTransactionNonExclusive()
        return Transaction(database)
    }

    private fun loadBootstrap(
        database: SQLiteDatabase,
        runId: String,
    ): List<CandidateBootstrapRow> =
        database
            .query(
                RecoveryJournalSchema.RUN_TABLE,
                arrayOf("run_id", "candidate_id", "key_confirmation_state"),
                "run_id=?",
                arrayOf(runId),
                null,
                null,
                null,
            )
            .useRows { cursor ->
                CandidateBootstrapRow(
                    cursor.string("run_id"),
                    cursor.string("candidate_id"),
                    KeyConfirmationState.valueOf(cursor.string("key_confirmation_state")),
                )
            }

    private fun loadUnits(database: SQLiteDatabase, runId: String): List<RecoveryMicrofileUnitRow> =
        database
            .query(
                RecoveryJournalSchema.UNIT_TABLE,
                null,
                "run_id=?",
                arrayOf(runId),
                null,
                null,
                "unit_index ASC",
            )
            .useRows { c ->
                RecoveryMicrofileUnitRow(
                    c.string("run_id"),
                    c.string("candidate_id"),
                    c.ulong("unit_index"),
                    c.ulong("plaintext_start"),
                    c.ulong("plaintext_end"),
                    c.ulong("cadence_seconds"),
                    c.string("ciphertext_relative_name"),
                    c.long("ciphertext_bytes"),
                    c.sha("ciphertext_sha256"),
                    c.string("key_envelope_relative_name"),
                    c.long("key_envelope_bytes"),
                    c.sha("key_envelope_sha256"),
                    c.ulong("manifest_generation"),
                    c.sha("processing_intent_id"),
                    c.string("state"),
                )
            }

    private fun loadPublications(
        database: SQLiteDatabase,
        runId: String,
    ): List<RecoveryManifestPublicationRow> =
        database
            .query(
                RecoveryJournalSchema.PUBLICATION_TABLE,
                null,
                "run_id=?",
                arrayOf(runId),
                null,
                null,
                "generation ASC",
            )
            .useRows { c ->
                RecoveryManifestPublicationRow(
                    c.string("run_id"),
                    c.string("candidate_id"),
                    c.ulong("generation"),
                    c.ulong("committed_end"),
                    c.string("publication_relative_name"),
                    c.long("publication_bytes"),
                    c.sha("publication_sha256"),
                    c.string("key_envelope_relative_name"),
                    c.long("key_envelope_bytes"),
                    c.sha("key_envelope_sha256"),
                    c.sha("previous_publication_sha256"),
                    c.string("state"),
                )
            }

    private class Transaction(private val database: SQLiteDatabase) : RecoveryMicrofileTransaction {
        private var ended = false

        override fun insert(
            unit: RecoveryMicrofileUnitRow,
            publication: RecoveryManifestPublicationRow,
        ) {
            check(!ended)
            database.insertOrThrow(RecoveryJournalSchema.UNIT_TABLE, null, unit.values())
            database.insertOrThrow(
                RecoveryJournalSchema.PUBLICATION_TABLE,
                null,
                publication.values(),
            )
        }

        override fun markSuccessful() {
            check(!ended)
            database.setTransactionSuccessful()
        }

        override fun end() {
            check(!ended)
            try {
                database.endTransaction()
            } finally {
                ended = true
            }
        }
    }
}

private fun RecoveryMicrofileUnitRow.values() =
    ContentValues().apply {
        put("run_id", runId)
        put("candidate_id", candidateId)
        put("unit_index", unitIndex.toLong())
        put("plaintext_start", plaintextStartInclusive.toLong())
        put("plaintext_end", plaintextEndExclusive.toLong())
        put("cadence_seconds", cadenceSeconds.toLong())
        put("ciphertext_relative_name", ciphertextRelativeName)
        put("ciphertext_bytes", ciphertextBytes)
        put("ciphertext_sha256", ciphertextSha256.toByteArray())
        put("key_envelope_relative_name", keyEnvelopeRelativeName)
        put("key_envelope_bytes", keyEnvelopeBytes)
        put("key_envelope_sha256", keyEnvelopeSha256.toByteArray())
        put("manifest_generation", manifestGeneration.toLong())
        put("processing_intent_id", processingIntentId.toByteArray())
        put("state", state)
    }

private fun RecoveryManifestPublicationRow.values() =
    ContentValues().apply {
        put("run_id", runId)
        put("candidate_id", candidateId)
        put("generation", generation.toLong())
        put("committed_end", committedEndExclusive.toLong())
        put("publication_relative_name", publicationRelativeName)
        put("publication_bytes", publicationBytes)
        put("publication_sha256", publicationSha256.toByteArray())
        put("key_envelope_relative_name", keyEnvelopeRelativeName)
        put("key_envelope_bytes", keyEnvelopeBytes)
        put("key_envelope_sha256", keyEnvelopeSha256.toByteArray())
        put("previous_publication_sha256", previousPublicationCiphertextSha256.toByteArray())
        put("state", state)
    }

private inline fun <T> Cursor.useRows(block: (Cursor) -> T): List<T> = use {
    buildList { while (it.moveToNext()) add(block(it)) }
}

private fun Cursor.index(name: String) = getColumnIndexOrThrow(name)

private fun Cursor.string(name: String) = getString(index(name))

private fun Cursor.long(name: String) = getLong(index(name))

private fun Cursor.ulong(name: String) = long(name).also { check(it >= 0) }.toULong()

private fun Cursor.sha(name: String) = Sha256Value.fromBytes(getBlob(index(name)))
