package com.monumentogram.dora.poc.recovery.journal

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Schema 6 changes only quarantine constraints; historical STREAM definitions remain exact. */
@Suppress("MagicNumber", "MaxLineLength", "TooManyFunctions")
internal object RecoveryMicrofileDispositionSchema {
    const val OLD_STATES =
        """('TEMP_ONLY','TEMP_AND_FINAL','FINAL_ORPHAN','SQLITE_POINTS_TO_TEMP',
     'UNKNOWN_OR_NON_ALLOWLISTED_NAME')"""
    const val NEW_STATES =
        """('TEMP_ONLY','TEMP_AND_FINAL','FINAL_ORPHAN','SQLITE_POINTS_TO_TEMP',
     'UNKNOWN_OR_NON_ALLOWLISTED_NAME','REFERENCED_REJECTED','REFERENCED_DEPENDENT')"""
    const val CONSTRAINT_ANCHOR =
        "  UNIQUE(run_id,candidate_id,source_relative_name,source_sha256),"
    const val REFERENCED_CONSTRAINT =
        """  CHECK(observed_state NOT IN ('REFERENCED_REJECTED','REFERENCED_DEPENDENT') OR
    (candidate_id='REC-MICROFILE-TINK' AND bootstrap_binding='PRESENT' AND
     bootstrap_run_id IS NOT NULL AND bootstrap_candidate_id IS NOT NULL AND
     bootstrap_run_id=run_id AND bootstrap_candidate_id=candidate_id AND
     artifact_role IN ('MICROFILE_CIPHERTEXT','MICROFILE_KEY_ENVELOPE',
                      'MANIFEST_CIPHERTEXT','MANIFEST_KEY_ENVELOPE'))),
"""

    val CREATE_QUARANTINE_TABLE: String =
        RecoveryJournalSchema.CREATE_QUARANTINE_TABLE.let { original ->
            check(original.split(OLD_STATES).size == 2)
            check(original.split(CONSTRAINT_ANCHOR).size == 2)
            original
                .replace(OLD_STATES, NEW_STATES)
                .replace(CONSTRAINT_ANCHOR, REFERENCED_CONSTRAINT + CONSTRAINT_ANCHOR)
        }

    // Bare column declarations have no affinity: even unusual historical storage types survive.
    const val CREATE_BACKUP =
        """CREATE TEMP TABLE microfile_disposition_backup (
intent_id,run_id,candidate_id,bootstrap_binding,bootstrap_run_id,bootstrap_candidate_id,
artifact_role,observed_state,source_relative_name,destination_relative_name,source_bytes,source_sha256,state)"""
    const val COPY_QUARANTINE =
        """INSERT INTO microfile_disposition_backup
SELECT intent_id,run_id,candidate_id,bootstrap_binding,bootstrap_run_id,bootstrap_candidate_id,
artifact_role,observed_state,source_relative_name,destination_relative_name,source_bytes,source_sha256,state
FROM recovery_quarantine_intent_v4"""
    const val DROP_QUARANTINE = "DROP TABLE recovery_quarantine_intent_v4"
    const val RESTORE_QUARANTINE =
        """INSERT INTO recovery_quarantine_intent_v4
(intent_id,run_id,candidate_id,bootstrap_binding,bootstrap_run_id,bootstrap_candidate_id,
artifact_role,observed_state,source_relative_name,destination_relative_name,source_bytes,source_sha256,state)
SELECT intent_id,run_id,candidate_id,bootstrap_binding,bootstrap_run_id,bootstrap_candidate_id,
artifact_role,observed_state,source_relative_name,destination_relative_name,source_bytes,source_sha256,state
FROM microfile_disposition_backup"""
    const val DROP_BACKUP = "DROP TABLE temp.microfile_disposition_backup"

    enum class Step {
        PREFLIGHT,
        CREATE_BACKUP,
        COPY_QUARANTINE,
        VERIFY_BACKUP,
        DROP_QUARANTINE,
        CREATE_QUARANTINE,
        RESTORE_QUARANTINE,
        VERIFY_COPY,
        DROP_BACKUP,
        VERIFIED,
    }

    fun create(database: SQLiteDatabase) {
        RecoveryStreamPrefixSchema.create(database)
        migrate(database)
    }

    /** The caller owns version assignment and the transaction; every failure must escape. */
    fun migrate(database: SQLiteDatabase, failpoint: (Step) -> Unit = {}) {
        if (!database.inTransaction())
            throw SQLiteException("Disposition migration needs transaction")
        RecoveryJournalSchema.requireExactV5(database)
        requireIntegrity(database)
        val before = snapshots(database)
        val quarantineShape = shape(database, RecoveryJournalSchema.QUARANTINE_TABLE)
        failpoint(Step.PREFLIGHT)
        database.execSQL(CREATE_BACKUP)
        failpoint(Step.CREATE_BACKUP)
        database.execSQL(COPY_QUARANTINE)
        failpoint(Step.COPY_QUARANTINE)
        requireDigest(
            before.getValue(RecoveryJournalSchema.QUARANTINE_TABLE),
            digest(database, "microfile_disposition_backup", quarantineShape),
        )
        failpoint(Step.VERIFY_BACKUP)
        database.execSQL(DROP_QUARANTINE)
        failpoint(Step.DROP_QUARANTINE)
        database.execSQL(CREATE_QUARANTINE_TABLE)
        failpoint(Step.CREATE_QUARANTINE)
        database.execSQL(RESTORE_QUARANTINE)
        failpoint(Step.RESTORE_QUARANTINE)
        val after = snapshots(database)
        before.forEach { (table, hash) -> requireDigest(hash, after.getValue(table)) }
        RecoveryJournalSchema.requireExactV6(database)
        failpoint(Step.VERIFY_COPY)
        database.execSQL(DROP_BACKUP)
        failpoint(Step.DROP_BACKUP)
        RecoveryJournalSchema.requireExactV6(database)
        requireIntegrity(database)
        failpoint(Step.VERIFIED)
    }

    @Suppress("ThrowsCount")
    private fun requireIntegrity(database: SQLiteDatabase) {
        database.rawQuery("PRAGMA foreign_keys", null).use { cursor ->
            if (!cursor.moveToFirst() || cursor.getLong(0) != 1L || cursor.moveToNext())
                throw SQLiteException("Disposition migration requires foreign keys")
        }
        database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
            if (!cursor.moveToFirst() || cursor.getString(0) != "ok" || cursor.moveToNext())
                throw SQLiteException("Disposition migration integrity failure")
        }
        database.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
            if (cursor.moveToFirst())
                throw SQLiteException("Disposition migration foreign key failure")
        }
    }

    private data class Shape(val columns: List<String>, val primaryKey: List<String>)

    private fun shape(database: SQLiteDatabase, table: String): Shape {
        val columns = mutableListOf<String>()
        val keys = sortedMapOf<Int, String>()
        database.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            while (cursor.moveToNext()) {
                val column = cursor.getString(1)
                columns += column
                val ordinal = cursor.getInt(5)
                if (ordinal > 0) keys[ordinal] = column
            }
        }
        if (columns.isEmpty() || keys.isEmpty())
            throw SQLiteException("Missing migration table/key")
        return Shape(columns, keys.values.toList())
    }

    private fun snapshots(database: SQLiteDatabase): Map<String, ByteArray> =
        RecoveryJournalSchema.EXACT_V4_OBJECTS.filter { it.type == "table" }
            .associate { it.name to digest(database, it.name, shape(database, it.name)) }

    /** All columns, storage types, raw bytes, row boundaries and full PK order are bound. */
    private fun digest(database: SQLiteDatabase, table: String, shape: Shape): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256")
        fun add(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            hash.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            hash.update(bytes)
        }
        add(shape.columns.size.toString())
        shape.columns.forEach(::add)
        add(shape.primaryKey.size.toString())
        shape.primaryKey.forEach(::add)
        val projection = shape.columns.joinToString(",") { "typeof($it),hex(CAST($it AS BLOB))" }
        var rows = 0L
        database
            .rawQuery(
                "SELECT $projection FROM $table ORDER BY ${shape.primaryKey.joinToString(",")}",
                null,
            )
            .use { cursor ->
                while (cursor.moveToNext()) {
                    add("ROW")
                    for (column in 0 until cursor.columnCount) add(cursor.getString(column))
                    rows++
                }
            }
        hash.update(ByteBuffer.allocate(8).putLong(rows).array())
        return hash.digest()
    }

    private fun requireDigest(expected: ByteArray, actual: ByteArray) {
        if (!expected.contentEquals(actual))
            throw SQLiteException("Disposition migration changed historical cells")
    }
}
