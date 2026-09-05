package com.monumentogram.dora.poc.recovery.journal

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.monumentogram.dora.poc.recovery.storage.BootstrapPathType
import com.monumentogram.dora.poc.recovery.storage.RecoveryBootstrapPathPolicy
import java.io.File

internal object RecoveryJournalSchema {
    const val VERSION = 2
    const val DATABASE_RELATIVE_NAME = "poc-recovery/v1/recovery-journal-v1.db"
    const val RUN_TABLE = "recovery_run_bootstrap_v1"
    const val UNIT_TABLE = "recovery_microfile_unit_v2"
    const val PUBLICATION_TABLE = "recovery_manifest_publication_v2"

    enum class UpgradePlan {
        V1_TO_V2,
        REJECT,
    }

    fun upgradePlan(oldVersion: Int, newVersion: Int): UpgradePlan =
        if (oldVersion == 1 && newVersion == 2) UpgradePlan.V1_TO_V2 else UpgradePlan.REJECT

    const val CREATE_RUN_TABLE =
        """CREATE TABLE recovery_run_bootstrap_v1 (
        run_id TEXT NOT NULL PRIMARY KEY,
        candidate_id TEXT NOT NULL CHECK(candidate_id IN ('REC-STREAM-TINK','REC-MICROFILE-TINK')),
        key_confirmation_relative_name TEXT NOT NULL CHECK(key_confirmation_relative_name = 'key-confirmation/run.kc'),
        key_confirmation_bytes INTEGER NOT NULL CHECK(key_confirmation_bytes > 0),
        key_confirmation_sha256 BLOB NOT NULL CHECK(length(key_confirmation_sha256) = 32),
        canonical_alias_sha256 BLOB NOT NULL CHECK(length(canonical_alias_sha256) = 32),
        key_confirmation_state TEXT NOT NULL CHECK(key_confirmation_state IN ('VALID','QUARANTINE_PENDING','QUARANTINED'))
    )"""
    const val CREATE_RUN_IDENTITY_INDEX =
        "CREATE UNIQUE INDEX recovery_run_candidate_v2 ON recovery_run_bootstrap_v1(run_id,candidate_id)"
    const val CREATE_UNIT_TABLE =
        """CREATE TABLE recovery_microfile_unit_v2 (
        run_id TEXT NOT NULL, candidate_id TEXT NOT NULL CHECK(candidate_id='REC-MICROFILE-TINK'),
        unit_index INTEGER NOT NULL CHECK(unit_index BETWEEN 0 AND 4294967295),
        plaintext_start INTEGER NOT NULL CHECK(plaintext_start >= 0),
        plaintext_end INTEGER NOT NULL CHECK(plaintext_end > plaintext_start AND plaintext_end <= 115200000),
        cadence_seconds INTEGER NOT NULL CHECK(cadence_seconds IN (5,15,30)),
        ciphertext_relative_name TEXT NOT NULL CHECK(ciphertext_relative_name = printf('units/u-%010d.ct',unit_index)), ciphertext_bytes INTEGER NOT NULL CHECK(ciphertext_bytes > 0),
        ciphertext_sha256 BLOB NOT NULL CHECK(length(ciphertext_sha256)=32),
        key_envelope_relative_name TEXT NOT NULL CHECK(key_envelope_relative_name = printf('key-envelopes/u-%010d.ks',unit_index)), key_envelope_bytes INTEGER NOT NULL CHECK(key_envelope_bytes > 0),
        key_envelope_sha256 BLOB NOT NULL CHECK(length(key_envelope_sha256)=32),
        manifest_generation INTEGER NOT NULL CHECK(manifest_generation = unit_index + 1),
        processing_intent_id BLOB NOT NULL UNIQUE CHECK(length(processing_intent_id)=32),
        state TEXT NOT NULL CHECK(state='VALID'),
        PRIMARY KEY(run_id,candidate_id,unit_index),
        FOREIGN KEY(run_id,candidate_id) REFERENCES recovery_run_bootstrap_v1(run_id,candidate_id) ON UPDATE RESTRICT ON DELETE RESTRICT
    )"""
    const val CREATE_PUBLICATION_TABLE =
        """CREATE TABLE recovery_manifest_publication_v2 (
        run_id TEXT NOT NULL, candidate_id TEXT NOT NULL CHECK(candidate_id='REC-MICROFILE-TINK'),
        generation INTEGER NOT NULL CHECK(generation > 0), committed_end INTEGER NOT NULL CHECK(committed_end BETWEEN 1 AND 115200000),
        publication_relative_name TEXT NOT NULL CHECK(publication_relative_name = printf('manifests/g-%020d.ct',generation)), publication_bytes INTEGER NOT NULL CHECK(publication_bytes > 0),
        publication_sha256 BLOB NOT NULL CHECK(length(publication_sha256)=32),
        key_envelope_relative_name TEXT NOT NULL CHECK(key_envelope_relative_name = printf('key-envelopes/manifest-g-%020d.ks',generation)), key_envelope_bytes INTEGER NOT NULL CHECK(key_envelope_bytes > 0),
        key_envelope_sha256 BLOB NOT NULL CHECK(length(key_envelope_sha256)=32),
        previous_publication_sha256 BLOB NOT NULL CHECK(length(previous_publication_sha256)=32),
        state TEXT NOT NULL CHECK(state='VALID'),
        PRIMARY KEY(run_id,candidate_id,generation),
        FOREIGN KEY(run_id,candidate_id) REFERENCES recovery_run_bootstrap_v1(run_id,candidate_id) ON UPDATE RESTRICT ON DELETE RESTRICT
    )"""

    fun createV2(database: SQLiteDatabase) {
        database.execSQL(CREATE_RUN_TABLE)
        database.execSQL(CREATE_RUN_IDENTITY_INDEX)
        database.execSQL(CREATE_UNIT_TABLE)
        database.execSQL(CREATE_PUBLICATION_TABLE)
    }

    fun migrateV1ToV2(database: SQLiteDatabase) {
        requireExactV1(database)
        database.execSQL(CREATE_RUN_IDENTITY_INDEX)
        database.execSQL(CREATE_UNIT_TABLE)
        database.execSQL(CREATE_PUBLICATION_TABLE)
    }

    private fun requireExactV1(database: SQLiteDatabase) {
        val expected =
            listOf(
                "run_id",
                "candidate_id",
                "key_confirmation_relative_name",
                "key_confirmation_bytes",
                "key_confirmation_sha256",
                "canonical_alias_sha256",
                "key_confirmation_state",
            )
        val actual = mutableListOf<String>()
        database.rawQuery("PRAGMA table_info($RUN_TABLE)", null).use { cursor ->
            val index = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) actual += cursor.getString(index)
        }
        if (actual != expected) throw SQLiteException("Recovery journal v1 schema is not exact")
    }
}

internal object AndroidRecoveryJournalDatabase {
    @Volatile private var helper: RecoveryJournalSqliteHelper? = null

    fun writable(context: Context): SQLiteDatabase {
        val app = context.applicationContext
        val selected =
            helper
                ?: synchronized(this) {
                    helper ?: RecoveryJournalSqliteHelper(app).also { helper = it }
                }
        return selected.writableDatabase
    }
}

private class RecoveryJournalSqliteHelper(context: Context) :
    SQLiteOpenHelper(context, databasePath(context).path, null, RecoveryJournalSchema.VERSION) {
    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(database: SQLiteDatabase) {
        database.setForeignKeyConstraintsEnabled(true)
        database.execSQL("PRAGMA synchronous=FULL")
        database.execSQL("PRAGMA wal_autocheckpoint=0")
    }

    override fun onCreate(database: SQLiteDatabase) = RecoveryJournalSchema.createV2(database)

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        when (RecoveryJournalSchema.upgradePlan(oldVersion, newVersion)) {
            RecoveryJournalSchema.UpgradePlan.V1_TO_V2 ->
                RecoveryJournalSchema.migrateV1ToV2(database)
            RecoveryJournalSchema.UpgradePlan.REJECT ->
                throw SQLiteException(
                    "PoC Recovery journal migration is not admitted: $oldVersion -> $newVersion"
                )
        }
    }

    override fun onDowngrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit =
        throw SQLiteException(
            "PoC Recovery journal downgrade is forbidden: $oldVersion -> $newVersion"
        )

    companion object {
        private const val DIRECTORY_MODE_OWNER_ONLY = 0x1c0

        fun databasePath(context: Context): File {
            val file = File(context.noBackupFilesDir, RecoveryJournalSchema.DATABASE_RELATIVE_NAME)
            val fixed = File(context.noBackupFilesDir, "poc-recovery")
            val version = File(fixed, "v1")
            requireDirectory(context.noBackupFilesDir)
            for (directory in listOf(fixed, version)) when (val type = existingType(directory)) {
                BootstrapPathType.ABSENT -> {
                    Os.mkdir(directory.path, DIRECTORY_MODE_OWNER_ONLY)
                    requireDirectory(directory)
                }
                else -> RecoveryBootstrapPathPolicy.requireDirectoryComponent(type, directory.name)
            }
            for (leaf in
                listOf(
                    file,
                    File("${file.path}-wal"),
                    File("${file.path}-shm"),
                    File("${file.path}-journal"),
                )) RecoveryBootstrapPathPolicy.requireRegularOrAbsentLeaf(
                existingType(leaf),
                leaf.name,
            )
            return file
        }

        private fun requireDirectory(file: File) =
            RecoveryBootstrapPathPolicy.requireDirectoryComponent(existingType(file), file.name)

        private fun existingType(file: File): BootstrapPathType =
            try {
                val mode = Os.lstat(file.path).st_mode
                when {
                    OsConstants.S_ISLNK(mode) -> BootstrapPathType.SYMLINK
                    OsConstants.S_ISREG(mode) -> BootstrapPathType.REGULAR
                    OsConstants.S_ISDIR(mode) -> BootstrapPathType.DIRECTORY
                    else -> BootstrapPathType.OTHER
                }
            } catch (error: ErrnoException) {
                if (error.errno == OsConstants.ENOENT) BootstrapPathType.ABSENT else throw error
            }
    }
}
