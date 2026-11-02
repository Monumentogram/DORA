// Keep indexed Room SQL and canonical migration DDL directly reviewable without string joining.
@file:Suppress("MaxLineLength")

package com.monumentogram.dora.audio.persistence.journal

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(
    tableName = "transcript_progress",
    primaryKeys = ["assetId", "jobId"],
    foreignKeys =
        [
            ForeignKey(
                entity = AssetEntity::class,
                parentColumns = ["assetId"],
                childColumns = ["assetId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
)
internal data class PagedJobEntity(
    val assetId: String,
    val jobId: String,
    val attempt: Long,
    val payload: ByteArray,
)

@Entity(
    tableName = "transcript_part",
    primaryKeys = ["assetId", "jobId", "partIndex"],
    foreignKeys =
        [
            ForeignKey(
                entity = PagedJobEntity::class,
                parentColumns = ["assetId", "jobId"],
                childColumns = ["assetId", "jobId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
)
internal data class PagedPartEntity(
    val assetId: String,
    val jobId: String,
    val partIndex: Int,
    val pageCount: Int,
    val textBytes: Long,
    val digest: String,
    val payload: ByteArray,
)

@Entity(
    tableName = "transcript_page",
    primaryKeys = ["assetId", "jobId", "partIndex", "pageIndex"],
    foreignKeys =
        [
            ForeignKey(
                entity = PagedPartEntity::class,
                parentColumns = ["assetId", "jobId", "partIndex"],
                childColumns = ["assetId", "jobId", "partIndex"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
)
internal data class PagedPageEntity(
    val assetId: String,
    val jobId: String,
    val partIndex: Int,
    val pageIndex: Int,
    val payload: ByteArray,
)

@Entity(
    tableName = "transcript_page_edit",
    primaryKeys = ["assetId", "jobId", "partIndex", "pageIndex", "revision"],
    foreignKeys =
        [
            ForeignKey(
                entity = PagedPageEntity::class,
                parentColumns = ["assetId", "jobId", "partIndex", "pageIndex"],
                childColumns = ["assetId", "jobId", "partIndex", "pageIndex"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
)
internal data class PagedEditEntity(
    val assetId: String,
    val jobId: String,
    val partIndex: Int,
    val pageIndex: Int,
    val revision: Long,
    val beforeDigest: String,
    val createdAt: Long,
    val payload: ByteArray,
)

@Dao
@Suppress("TooManyFunctions")
internal interface PagedTranscriptDao {
    @Query("SELECT * FROM transcript_progress WHERE assetId=:asset AND jobId=:job")
    fun job(asset: String, job: String): PagedJobEntity?

    @Insert fun insert(row: PagedJobEntity)

    @Update fun update(row: PagedJobEntity): Int

    @Query("SELECT * FROM transcript_part WHERE assetId=:asset AND jobId=:job AND partIndex=:index")
    fun part(asset: String, job: String, index: Int): PagedPartEntity?

    @Insert fun insert(row: PagedPartEntity)

    @Query(
        "SELECT partIndex FROM transcript_part WHERE assetId=:asset AND jobId=:job ORDER BY partIndex LIMIT 288"
    )
    fun parts(asset: String, job: String): List<Int>

    @Query(
        "SELECT COALESCE(SUM(textBytes),0) FROM transcript_part WHERE assetId=:asset AND jobId=:job"
    )
    fun textBytes(asset: String, job: String): Long

    @Query(
        "SELECT * FROM transcript_page WHERE assetId=:asset AND jobId=:job AND partIndex=:part AND pageIndex=:page"
    )
    fun page(asset: String, job: String, part: Int, page: Int): PagedPageEntity?

    @Query(
        "SELECT * FROM transcript_page WHERE assetId=:asset AND jobId=:job AND (partIndex,pageIndex)>(:part,:page) ORDER BY partIndex,pageIndex LIMIT 1"
    )
    fun nextPage(asset: String, job: String, part: Int, page: Int): PagedPageEntity?

    @Insert fun insert(row: PagedPageEntity)

    @Query(
        "SELECT * FROM transcript_page_edit WHERE assetId=:asset AND jobId=:job AND partIndex=:part AND pageIndex=:page AND revision<=:revision ORDER BY revision DESC LIMIT 1"
    )
    fun edit(asset: String, job: String, part: Int, page: Int, revision: Long): PagedEditEntity?

    @Insert fun insert(row: PagedEditEntity)

    @Query("DELETE FROM transcript_page_edit WHERE assetId=:asset") fun removeEdits(asset: String)

    @Query("DELETE FROM transcript_page WHERE assetId=:asset") fun removePages(asset: String)

    @Query("DELETE FROM transcript_part WHERE assetId=:asset") fun removeParts(asset: String)

    @Query("DELETE FROM transcript_progress WHERE assetId=:asset") fun removeJobs(asset: String)

    @Query("SELECT COUNT(*) FROM transcript_progress WHERE assetId=:asset")
    fun jobCount(asset: String): Int
}

internal const val PAGED_TRANSCRIPT_SCHEMA_VERSION = 5

internal object PagedTranscriptMigration :
    Migration(TRANSCRIPT_SCHEMA_VERSION, PAGED_TRANSCRIPT_SCHEMA_VERSION) {
    val tables =
        linkedMapOf(
            "transcript_progress" to
                "CREATE TABLE IF NOT EXISTS `transcript_progress` (`assetId` TEXT NOT NULL, `jobId` TEXT NOT NULL, `attempt` INTEGER NOT NULL, `payload` BLOB NOT NULL, PRIMARY KEY(`assetId`, `jobId`), FOREIGN KEY(`assetId`) REFERENCES `audio_asset`(`assetId`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "transcript_part" to
                "CREATE TABLE IF NOT EXISTS `transcript_part` (`assetId` TEXT NOT NULL, `jobId` TEXT NOT NULL, `partIndex` INTEGER NOT NULL, `pageCount` INTEGER NOT NULL, `textBytes` INTEGER NOT NULL, `digest` TEXT NOT NULL, `payload` BLOB NOT NULL, PRIMARY KEY(`assetId`, `jobId`, `partIndex`), FOREIGN KEY(`assetId`, `jobId`) REFERENCES `transcript_progress`(`assetId`, `jobId`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "transcript_page" to
                "CREATE TABLE IF NOT EXISTS `transcript_page` (`assetId` TEXT NOT NULL, `jobId` TEXT NOT NULL, `partIndex` INTEGER NOT NULL, `pageIndex` INTEGER NOT NULL, `payload` BLOB NOT NULL, PRIMARY KEY(`assetId`, `jobId`, `partIndex`, `pageIndex`), FOREIGN KEY(`assetId`, `jobId`, `partIndex`) REFERENCES `transcript_part`(`assetId`, `jobId`, `partIndex`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "transcript_page_edit" to
                "CREATE TABLE IF NOT EXISTS `transcript_page_edit` (`assetId` TEXT NOT NULL, `jobId` TEXT NOT NULL, `partIndex` INTEGER NOT NULL, `pageIndex` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `beforeDigest` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `payload` BLOB NOT NULL, PRIMARY KEY(`assetId`, `jobId`, `partIndex`, `pageIndex`, `revision`), FOREIGN KEY(`assetId`, `jobId`, `partIndex`, `pageIndex`) REFERENCES `transcript_page`(`assetId`, `jobId`, `partIndex`, `pageIndex`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )

    override fun migrate(db: SupportSQLiteDatabase) {
        JournalSchemaVerifier.verify(db, TRANSCRIPT_SCHEMA_VERSION)
        tables.values.forEach(db::execSQL)
        AudioCatalogRevisionSchema.install(db)
        JournalSchemaVerifier.verify(db, PAGED_TRANSCRIPT_SCHEMA_VERSION)
    }
}
