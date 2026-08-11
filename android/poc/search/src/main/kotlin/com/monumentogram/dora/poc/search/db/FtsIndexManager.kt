@file:Suppress("TooManyFunctions")

package com.monumentogram.dora.poc.search.db

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase

class FtsIndexManager(private val database: SearchPocDatabase) {
    fun rebuildFromCanonicalRows() {
        database.runInTransaction {
            writableDatabase().execSQL("DELETE FROM transcript_segments_fts")
            writableDatabase()
                .execSQL(
                    "INSERT INTO transcript_segments_fts(rowid, text) " +
                        "SELECT segment_id, text FROM transcript_segments ORDER BY segment_id"
                )
        }
    }

    fun checkpointAndCompact() {
        querySingleString("PRAGMA wal_checkpoint(TRUNCATE)")
        writableDatabase().execSQL("VACUUM")
    }

    fun sqliteIntegrityCheck(): String = querySingleString("PRAGMA integrity_check")

    fun ftsIntegrityCheck() {
        writableDatabase()
            .execSQL(
                "INSERT INTO transcript_segments_fts(transcript_segments_fts) VALUES('integrity-check')"
            )
    }

    fun missingCanonicalMappingCount(): Long =
        querySingleLong(
            "SELECT COUNT(*) FROM transcript_segments_fts " +
                "LEFT JOIN transcript_segments ON transcript_segments.segment_id = transcript_segments_fts.rowid " +
                "WHERE transcript_segments.segment_id IS NULL"
        )

    fun missingIndexRowCount(): Long =
        querySingleLong(
            "SELECT COUNT(*) FROM transcript_segments " +
                "LEFT JOIN transcript_segments_fts ON transcript_segments_fts.rowid = transcript_segments.segment_id " +
                "WHERE transcript_segments_fts.rowid IS NULL"
        )

    fun duplicateCanonicalCount(): Long =
        querySingleLong(
            "SELECT COUNT(*) FROM (" +
                "SELECT segment_id FROM transcript_segments GROUP BY segment_id HAVING COUNT(*) > 1)"
        )

    fun dropIndex(): String {
        val createSql =
            querySingleString(
                "SELECT sql FROM sqlite_master WHERE type='table' AND name='transcript_segments_fts'"
            )
        writableDatabase().execSQL("DROP TABLE transcript_segments_fts")
        return createSql
    }

    fun recreateIndex(createSql: String) {
        writableDatabase().execSQL(createSql)
    }

    fun injectOrphan(rowId: Long, marker: String) {
        writableDatabase()
            .execSQL(
                "INSERT INTO transcript_segments_fts(rowid, text) VALUES(?, ?)",
                arrayOf<Any?>(rowId, marker),
            )
    }

    fun deleteIndexRow(rowId: Long) {
        writableDatabase()
            .execSQL(
                "DELETE FROM transcript_segments_fts WHERE rowid = ?",
                arrayOf<Any?>(rowId),
            )
    }

    fun rawLong(sql: String, arguments: Array<Any?> = emptyArray()): Long =
        querySingleLong(sql, arguments)

    private fun writableDatabase(): SupportSQLiteDatabase = database.openHelper.writableDatabase

    private fun querySingleLong(sql: String, arguments: Array<Any?> = emptyArray()): Long =
        writableDatabase().query(sql, arguments).use { cursor ->
            check(cursor.moveToFirst()) { "Expected a scalar row" }
            cursor.getLong(0)
        }

    private fun querySingleString(sql: String): String =
        writableDatabase().query(sql).use { cursor: Cursor ->
            check(cursor.moveToFirst()) { "Expected a scalar row" }
            cursor.getString(0)
        }
}
