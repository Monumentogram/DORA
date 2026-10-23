package com.monumentogram.dora.audio.persistence.journal

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal const val TRANSCRIPT_SCHEMA_VERSION = 4

internal object TranscriptMigration : Migration(SEGMENTATION_SCHEMA_VERSION, TRANSCRIPT_SCHEMA_VERSION) {
    @Suppress("MaxLineLength") // Exact Room DDL, validated before and after migration.
    const val TABLE_SQL =
        "CREATE TABLE IF NOT EXISTS `transcript_state` (`assetId` TEXT NOT NULL, `revision` INTEGER NOT NULL, `payload` BLOB NOT NULL, PRIMARY KEY(`assetId`), FOREIGN KEY(`assetId`) REFERENCES `audio_asset`(`assetId`) ON UPDATE NO ACTION ON DELETE RESTRICT )"

    override fun migrate(db: SupportSQLiteDatabase) {
        JournalSchemaVerifier.verify(db, SEGMENTATION_SCHEMA_VERSION)
        db.execSQL(TABLE_SQL)
        JournalSchemaVerifier.verify(db, TRANSCRIPT_SCHEMA_VERSION)
    }
}
