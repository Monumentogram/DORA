package com.monumentogram.dora.audio.persistence.journal

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive only: exact accepted v1 must validate before any DDL or Room hash update. */
@Suppress("MaxLineLength") // Exact Room-exported DDL, checked against v2 schema in tests.
internal object OriginalAudioMigration : Migration(1, 2) {
    const val TABLE_SQL =
        "CREATE TABLE IF NOT EXISTS `original_audio_reference` (`assetId` TEXT NOT NULL, `version` INTEGER NOT NULL, `digest` TEXT NOT NULL, `frames` INTEGER NOT NULL, `unavailableReason` TEXT, PRIMARY KEY(`assetId`), FOREIGN KEY(`assetId`) REFERENCES `audio_asset`(`assetId`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
    const val INDEX_SQL =
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_original_audio_reference_digest` ON `original_audio_reference` (`digest`)"

    override fun migrate(db: SupportSQLiteDatabase) {
        JournalSchemaVerifier.verify(db, 1)
        db.execSQL(TABLE_SQL)
        db.execSQL(INDEX_SQL)
        JournalSchemaVerifier.verify(db)
    }
}
