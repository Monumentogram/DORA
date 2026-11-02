package com.monumentogram.dora.audio.persistence.journal

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit maintenance on the existing admitted connection; auto-checkpointing remains disabled.
 */
internal object JournalWalCheckpoint {
    const val TRIGGER_BYTES = 8L * 1024 * 1024
    private const val COLUMN_COUNT = 3

    fun perform(database: SupportSQLiteDatabase) {
        check(!database.inTransaction()) { "Checkpoint inside transaction rejected" }
        observe(database, "PRAGMA wal_checkpoint(PASSIVE)", truncate = false)
        // PASSIVE makes the main database durable but retains the WAL's allocated high-water mark.
        // All cursors have closed and the same sole connection is still owned by this operation.
        observe(database, "PRAGMA wal_checkpoint(TRUNCATE)", truncate = true)
    }

    private fun observe(database: SupportSQLiteDatabase, sql: String, truncate: Boolean) {
        database.query(sql).use { cursor ->
            val columns = cursor.columnCount
            val first = cursor.moveToFirst()
            if (columns == COLUMN_COUNT && first)
                check(
                    (0 until COLUMN_COUNT).all { cursor.getType(it) == Cursor.FIELD_TYPE_INTEGER }
                ) {
                    "Checkpoint counter type rejected"
                }
            val values =
                if (columns == COLUMN_COUNT && first)
                    longArrayOf(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2))
                else null
            val rows = if (!first) 0 else if (cursor.moveToNext()) 2 else 1
            requireComplete(columns, rows, values, truncate)
        }
    }

    internal fun requireComplete(columns: Int, rows: Int, values: LongArray?, truncate: Boolean) {
        check(columns == COLUMN_COUNT && rows == 1 && values?.size == COLUMN_COUNT) {
            "Checkpoint result shape rejected"
        }
        val result = checkNotNull(values)
        check(result[0] == 0L && result[1] >= 0 && result[1] == result[2]) {
            "Checkpoint incomplete"
        }
        if (truncate) check(result[1] == 0L) { "WAL truncation incomplete" }
    }
}
