package com.monumentogram.dora.poc.recovery.journal

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.monumentogram.dora.poc.recovery.bootstrap.RecoveryBootstrapRunRow
import com.monumentogram.dora.poc.recovery.bootstrap.RecoveryRunBootstrapJournal
import com.monumentogram.dora.poc.recovery.bootstrap.RecoveryRunBootstrapTransaction

/** Versioned PoC-only run-row journal. It has no destructive migration or fallback path. */
internal class AndroidRecoveryRunBootstrapJournal(context: Context) : RecoveryRunBootstrapJournal {
    private val applicationContext = context.applicationContext

    @Synchronized
    override fun beginNonExclusive(): RecoveryRunBootstrapTransaction {
        val database = AndroidRecoveryJournalDatabase.writable(applicationContext)
        database.beginTransactionNonExclusive()
        return AndroidRecoveryRunBootstrapTransaction(database)
    }
}

private class AndroidRecoveryRunBootstrapTransaction(private val database: SQLiteDatabase) :
    RecoveryRunBootstrapTransaction {
    private var ended = false

    override fun insert(value: RecoveryBootstrapRunRow) {
        check(!ended) { "Recovery bootstrap transaction already ended" }
        val values =
            ContentValues().apply {
                put("run_id", value.runId)
                put("candidate_id", value.candidateId)
                put("key_confirmation_relative_name", value.keyConfirmationRelativeName)
                put("key_confirmation_bytes", value.keyConfirmationBytes)
                put("key_confirmation_sha256", value.keyConfirmationSha256.toByteArray())
                put("canonical_alias_sha256", value.canonicalAliasSha256.toByteArray())
                put("key_confirmation_state", value.keyConfirmationState.name)
            }
        database.insertOrThrow(TABLE, null, values)
    }

    override fun markSuccessful() {
        check(!ended) { "Recovery bootstrap transaction already ended" }
        database.setTransactionSuccessful()
    }

    override fun end() {
        check(!ended) { "Recovery bootstrap transaction already ended" }
        try {
            database.endTransaction()
        } finally {
            ended = true
        }
    }

    private companion object {
        const val TABLE = RecoveryJournalSchema.RUN_TABLE
    }
}
