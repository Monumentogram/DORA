package com.monumentogram.dora.audio.persistence

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.monumentogram.dora.audio.persistence.journal.JournalSchemaVerifier
import com.monumentogram.dora.audio.persistence.journal.SEGMENTATION_SCHEMA_VERSION

/** Restrict callbacks before Room is allowed to create, migrate or initialize a binding. */
internal class DiagnosticJournalFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory,
    private val policy: DiagnosticSourcePolicy,
    private val owner: String,
    private val vault: String,
) : SupportSQLiteOpenHelper.Factory {
    override fun create(
        configuration: SupportSQLiteOpenHelper.Configuration
    ): SupportSQLiteOpenHelper {
        val callback = configuration.callback
        val fenced =
            object : SupportSQLiteOpenHelper.Callback(callback.version) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    verify(db)
                    callback.onConfigure(db)
                }

                override fun onCreate(db: SupportSQLiteDatabase): Unit =
                    error("Diagnostic schema creation rejected")

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ): Unit = error("Diagnostic migration rejected")

                override fun onDowngrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ): Unit = error("Diagnostic migration rejected")

                override fun onOpen(db: SupportSQLiteDatabase) {
                    verify(db)
                    callback.onOpen(db)
                    verify(db)
                }

                override fun onCorruption(db: SupportSQLiteDatabase): Unit =
                    error("Diagnostic corruption retained")
            }
        return delegate.create(
            SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(fenced)
                .build()
        )
    }

    private fun verify(db: SupportSQLiteDatabase) {
        check(db.version == SEGMENTATION_SCHEMA_VERSION) { "Diagnostic schema version rejected" }
        JournalSchemaVerifier.verify(db, SEGMENTATION_SCHEMA_VERSION)
        // Room otherwise repairs a missing master table during onOpen. Diagnostic opens
        // admit only the already materialized v3 identity, before handing control to Room.
        db.query("SELECT id,identity_hash FROM room_master_table").use { row ->
            check(
                row.moveToFirst() &&
                    row.getInt(0) == ROOM_IDENTITY_ROW &&
                    row.getString(1) == "14326b90c3941099abb8dc04701d9d5c" &&
                    !row.moveToNext()
            )
        }
        db.query("SELECT singleton,ownerId,vaultId FROM vault_binding").use { row ->
            check(
                row.moveToFirst() &&
                    row.getInt(0) == 1 &&
                    row.getString(1) == owner &&
                    row.getString(2) == vault &&
                    !row.moveToNext()
            )
        }
        verifySources(db)
    }

    private fun verifySources(db: SupportSQLiteDatabase) {
        policy.protectedSources().forEach { identity ->
            db.query(
                    "SELECT recordingId,sessionId,ownerId,vaultId FROM audio_asset WHERE assetId=?",
                    arrayOf(identity.assetId.value),
                )
                .use { row ->
                    check(
                        row.moveToFirst() &&
                            row.getString(0) == identity.recordingId.value &&
                            row.getString(1) == identity.sessionId &&
                            row.getString(2) == owner &&
                            row.getString(row.getColumnIndexOrThrow("vaultId")) == vault &&
                            !row.moveToNext()
                    )
                }
        }
    }

    private companion object {
        const val ROOM_IDENTITY_ROW = 42
    }
}
