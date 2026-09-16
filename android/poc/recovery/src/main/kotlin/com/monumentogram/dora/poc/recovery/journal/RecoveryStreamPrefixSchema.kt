package com.monumentogram.dora.poc.recovery.journal

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Forward-only schema 5; physical names and every historical row identity stay unchanged. */
@Suppress("MagicNumber", "MaxLineLength")
internal object RecoveryStreamPrefixSchema {
    const val OLD_INTERSECTION =
        """pre_fault_source_match_state='VERIFIED_SAME_DESCRIPTOR' AND
      checkpoint_intersection_state='PROVEN' AND
      checkpoint_prefix_bytes<=pre_fault_source_bytes AND
      pre_fault_source_bytes<=observed_source_bytes"""
    const val NEW_INTERSECTION =
        """checkpoint_intersection_state='PROVEN' AND
      ((pre_fault_source_match_state='VERIFIED_SAME_DESCRIPTOR' AND
        checkpoint_prefix_bytes<=pre_fault_source_bytes AND
        pre_fault_source_bytes<=observed_source_bytes) OR
       (pre_fault_source_match_state='VERIFIED_SURVIVING_CHECKPOINT_PREFIX' AND
        checkpoint_prefix_bytes<=observed_source_bytes AND
        observed_source_bytes<pre_fault_source_bytes))"""

    val CREATE_OUTCOME_TABLE: String =
        RecoveryJournalSchema.CREATE_STREAM_OUTCOME_TABLE.let { original ->
            check(original.split(OLD_INTERSECTION).size == 3)
            original
                .replace(OLD_INTERSECTION, NEW_INTERSECTION)
                .replace(
                    "('VERIFIED_SAME_DESCRIPTOR','UNPROVEN_OR_MISMATCH')",
                    "('VERIFIED_SAME_DESCRIPTOR','UNPROVEN_OR_MISMATCH','VERIFIED_SURVIVING_CHECKPOINT_PREFIX')",
                )
        }

    const val COPY_OUTCOMES =
        "CREATE TEMP TABLE stream_prefix_outcomes_backup AS SELECT * FROM recovery_stream_outcome_v4"
    const val COPY_RANGES =
        "CREATE TEMP TABLE stream_prefix_ranges_backup AS SELECT * FROM recovery_stream_range_quarantine_v4"
    const val DROP_RANGES = "DROP TABLE recovery_stream_range_quarantine_v4"
    const val DROP_OUTCOMES = "DROP TABLE recovery_stream_outcome_v4"
    const val RESTORE_OUTCOMES =
        "INSERT INTO recovery_stream_outcome_v4 SELECT * FROM stream_prefix_outcomes_backup"
    const val RESTORE_RANGES =
        "INSERT INTO recovery_stream_range_quarantine_v4 SELECT * FROM stream_prefix_ranges_backup"
    const val DROP_OUTCOME_BACKUP = "DROP TABLE temp.stream_prefix_outcomes_backup"
    const val DROP_RANGE_BACKUP = "DROP TABLE temp.stream_prefix_ranges_backup"

    enum class Step {
        PREFLIGHT,
        COPY_OUTCOMES,
        COPY_RANGES,
        DROP_RANGES,
        DROP_OUTCOMES,
        CREATE_OUTCOMES,
        CREATE_RANGES,
        RESTORE_OUTCOMES,
        RESTORE_RANGES,
        CREATE_INDEX,
        VERIFY_COPY,
        DROP_OUTCOME_BACKUP,
        DROP_RANGE_BACKUP,
        VERIFIED,
    }

    fun create(database: SQLiteDatabase) {
        RecoveryJournalSchema.createV4(database)
        migrate(database)
    }

    /** SQLiteOpenHelper owns the transaction. Any exception must escape to its rollback. */
    fun migrate(database: SQLiteDatabase, failpoint: (Step) -> Unit = {}) {
        if (!database.inTransaction())
            throw SQLiteException("Stream prefix migration needs transaction")
        RecoveryJournalSchema.requireExactV4(database)
        requireIntegrity(database)
        val originalOutcomes = digest(database, RecoveryJournalSchema.STREAM_OUTCOME_TABLE)
        val originalRanges = digest(database, RecoveryJournalSchema.STREAM_RANGE_TABLE)
        failpoint(Step.PREFLIGHT)
        database.execSQL(COPY_OUTCOMES)
        failpoint(Step.COPY_OUTCOMES)
        database.execSQL(COPY_RANGES)
        failpoint(Step.COPY_RANGES)
        requireDigest(originalOutcomes, digest(database, "stream_prefix_outcomes_backup"))
        requireDigest(originalRanges, digest(database, "stream_prefix_ranges_backup"))
        database.execSQL(DROP_RANGES)
        failpoint(Step.DROP_RANGES)
        database.execSQL(DROP_OUTCOMES)
        failpoint(Step.DROP_OUTCOMES)
        database.execSQL(CREATE_OUTCOME_TABLE)
        failpoint(Step.CREATE_OUTCOMES)
        database.execSQL(RecoveryJournalSchema.CREATE_STREAM_RANGE_TABLE)
        failpoint(Step.CREATE_RANGES)
        database.execSQL(RESTORE_OUTCOMES)
        failpoint(Step.RESTORE_OUTCOMES)
        database.execSQL(RESTORE_RANGES)
        failpoint(Step.RESTORE_RANGES)
        database.execSQL(RecoveryJournalSchema.CREATE_STREAM_ACTIVE_RANGE_INDEX)
        failpoint(Step.CREATE_INDEX)
        requireDigest(
            originalOutcomes,
            digest(database, RecoveryJournalSchema.STREAM_OUTCOME_TABLE),
        )
        requireDigest(originalRanges, digest(database, RecoveryJournalSchema.STREAM_RANGE_TABLE))
        failpoint(Step.VERIFY_COPY)
        database.execSQL(DROP_OUTCOME_BACKUP)
        failpoint(Step.DROP_OUTCOME_BACKUP)
        database.execSQL(DROP_RANGE_BACKUP)
        failpoint(Step.DROP_RANGE_BACKUP)
        RecoveryJournalSchema.requireExactV5(database)
        requireIntegrity(database)
        failpoint(Step.VERIFIED)
    }

    private fun requireIntegrity(database: SQLiteDatabase) {
        database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
            if (!cursor.moveToFirst() || cursor.getString(0) != "ok" || cursor.moveToNext()) {
                throw SQLiteException("Stream prefix migration integrity failure")
            }
        }
        database.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
            if (cursor.moveToFirst())
                throw SQLiteException("Stream prefix migration foreign key failure")
        }
    }

    /** Storage type plus raw CAST-as-BLOB hex, column order and primary-key order are bound. */
    private fun digest(database: SQLiteDatabase, table: String): ByteArray {
        val columns = mutableListOf<String>()
        database.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            while (cursor.moveToNext()) columns += cursor.getString(1)
        }
        if (columns.isEmpty()) throw SQLiteException("Missing stream migration table")
        val projection = columns.joinToString(",") { "typeof($it),hex(CAST($it AS BLOB))" }
        val digest = MessageDigest.getInstance("SHA-256")
        fun add(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        columns.forEach(::add)
        var rows = 0L
        database.rawQuery("SELECT $projection FROM $table ORDER BY ${columns.first()}", null).use {
            cursor ->
            while (cursor.moveToNext()) {
                for (index in 0 until cursor.columnCount) add(cursor.getString(index))
                rows++
            }
        }
        digest.update(ByteBuffer.allocate(8).putLong(rows).array())
        return digest.digest()
    }

    private fun requireDigest(expected: ByteArray, actual: ByteArray) {
        if (!expected.contentEquals(actual))
            throw SQLiteException("Stream migration changed historical rows")
    }
}
