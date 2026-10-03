package com.monumentogram.dora.audio.persistence.journal

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal const val SEGMENTATION_SCHEMA_VERSION = 3

/** Historical audio receives no invented VAD rows. */
internal object SegmentationMigration : Migration(2, SEGMENTATION_SCHEMA_VERSION) {
    @Suppress(
        "MaxLineLength"
    ) // Exact SQL schema identity; whitespace is verified during migration.
    const val TABLE_SQL =
        "CREATE TABLE IF NOT EXISTS `audio_segmentation` (`assetId` TEXT NOT NULL, `recordKey` TEXT NOT NULL, `kind` TEXT NOT NULL, `segmentId` TEXT NOT NULL, `firstFrame` INTEGER NOT NULL, `endFrame` INTEGER NOT NULL, `captureEpochId` TEXT, `overlapFirstFrame` INTEGER, `reason` TEXT NOT NULL, `degraded` INTEGER NOT NULL, `profileId` TEXT NOT NULL, `profileSha256` TEXT NOT NULL, PRIMARY KEY(`assetId`, `recordKey`), FOREIGN KEY(`assetId`) REFERENCES `audio_asset`(`assetId`) ON UPDATE NO ACTION ON DELETE RESTRICT )"

    override fun migrate(db: SupportSQLiteDatabase) {
        JournalSchemaVerifier.verify(db, 2)
        db.execSQL(TABLE_SQL)
        JournalSchemaVerifier.verify(db, SEGMENTATION_SCHEMA_VERSION)
    }
}
