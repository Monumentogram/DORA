package com.monumentogram.dora.audio.persistence.journal

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal const val AUDIO_INDEX_SCHEMA_VERSION = 6

/** Preserve v5 databases already created during Alpha v9 qualification; only add a lookup index. */
@Suppress(
    "MaxLineLength"
) // Keep the canonical reviewed index DDL identical across schema versions.
internal object AudioDeletionIndexMigration :
    Migration(PAGED_TRANSCRIPT_SCHEMA_VERSION, AUDIO_INDEX_SCHEMA_VERSION) {
    val indexes =
        mapOf(
            "index_deletion_target_assetId_kind_completed_runId_relativeName" to
                "CREATE INDEX IF NOT EXISTS `index_deletion_target_assetId_kind_completed_runId_relativeName` ON `deletion_target` (`assetId`, `kind`, `completed`, `runId`, `relativeName`)"
        )

    override fun migrate(db: SupportSQLiteDatabase) {
        JournalSchemaVerifier.verify(db, PAGED_TRANSCRIPT_SCHEMA_VERSION)
        install(db)
        JournalSchemaVerifier.verify(db, AUDIO_INDEX_SCHEMA_VERSION)
    }

    fun install(db: SupportSQLiteDatabase) = indexes.values.forEach(db::execSQL)
}
