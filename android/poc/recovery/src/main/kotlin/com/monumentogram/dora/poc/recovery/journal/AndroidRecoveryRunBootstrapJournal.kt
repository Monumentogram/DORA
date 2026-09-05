package com.monumentogram.dora.poc.recovery.journal

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.monumentogram.dora.poc.recovery.bootstrap.RecoveryBootstrapRunRow
import com.monumentogram.dora.poc.recovery.bootstrap.RecoveryRunBootstrapJournal
import com.monumentogram.dora.poc.recovery.bootstrap.RecoveryRunBootstrapTransaction
import com.monumentogram.dora.poc.recovery.storage.BootstrapPathType
import com.monumentogram.dora.poc.recovery.storage.RecoveryBootstrapPathPolicy
import java.io.File

/** Versioned PoC-only run-row journal. It has no destructive migration or fallback path. */
internal class AndroidRecoveryRunBootstrapJournal(context: Context) : RecoveryRunBootstrapJournal {
    private val helper = RecoveryBootstrapSqliteHelper(context.applicationContext)

    @Synchronized
    override fun beginNonExclusive(): RecoveryRunBootstrapTransaction {
        val database = helper.writableDatabase
        database.beginTransactionNonExclusive()
        return AndroidRecoveryRunBootstrapTransaction(database)
    }
}

private class AndroidRecoveryRunBootstrapTransaction(private val database: SQLiteDatabase) :
    RecoveryRunBootstrapTransaction {
    private var ended = false

    override fun insert(value: RecoveryBootstrapRunRow) {
        check(!ended) { "Recovery bootstrap transaction already ended" }
        val values =
            ContentValues().apply {
                put("run_id", value.runId)
                put("candidate_id", value.candidateId)
                put("key_confirmation_relative_name", value.keyConfirmationRelativeName)
                put("key_confirmation_bytes", value.keyConfirmationBytes)
                put("key_confirmation_sha256", value.keyConfirmationSha256.toByteArray())
                put("canonical_alias_sha256", value.canonicalAliasSha256.toByteArray())
                put("key_confirmation_state", value.keyConfirmationState.name)
            }
        database.insertOrThrow(TABLE, null, values)
    }

    override fun markSuccessful() {
        check(!ended) { "Recovery bootstrap transaction already ended" }
        database.setTransactionSuccessful()
    }

    override fun end() {
        check(!ended) { "Recovery bootstrap transaction already ended" }
        try {
            database.endTransaction()
        } finally {
            ended = true
        }
    }

    private companion object {
        const val TABLE = "recovery_run_bootstrap_v1"
    }
}

private class RecoveryBootstrapSqliteHelper(context: Context) :
    SQLiteOpenHelper(context, databasePath(context).path, null, SCHEMA_VERSION) {
    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(database: SQLiteDatabase) {
        database.setForeignKeyConstraintsEnabled(true)
        database.execSQL("PRAGMA synchronous=FULL")
        database.execSQL("PRAGMA wal_autocheckpoint=0")
    }

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(CREATE_RUN_TABLE)
    }

    override fun onUpgrade(
        database: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ): Unit =
        throw SQLiteException(
            "PoC Recovery journal migration is not admitted: $oldVersion -> $newVersion"
        )

    override fun onDowngrade(
        database: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ): Unit =
        throw SQLiteException(
            "PoC Recovery journal downgrade is forbidden: $oldVersion -> $newVersion"
        )

    private companion object {
        const val SCHEMA_VERSION = 1
        const val DATABASE_RELATIVE_NAME = "poc-recovery/v1/recovery-journal-v1.db"
        const val DIRECTORY_MODE_OWNER_ONLY = 0x1c0 // 0700
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

        fun databasePath(context: Context): File {
            val file = File(context.noBackupFilesDir, DATABASE_RELATIVE_NAME)
            val fixedRoot = File(context.noBackupFilesDir, "poc-recovery")
            val versionRoot = File(fixedRoot, "v1")
            requireDirectory(context.noBackupFilesDir)
            for (directory in listOf(fixedRoot, versionRoot)) {
                when (val type = existingType(directory)) {
                    BootstrapPathType.ABSENT -> {
                        Os.mkdir(directory.path, DIRECTORY_MODE_OWNER_ONLY)
                        requireDirectory(directory)
                    }
                    else ->
                        RecoveryBootstrapPathPolicy.requireDirectoryComponent(type, directory.name)
                }
            }
            for (leaf in
                listOf(
                    file,
                    File("${file.path}-wal"),
                    File("${file.path}-shm"),
                    File("${file.path}-journal"),
                )) {
                RecoveryBootstrapPathPolicy.requireRegularOrAbsentLeaf(
                    existingType(leaf),
                    leaf.name,
                )
            }
            return file
        }

        private fun requireDirectory(directory: File) {
            RecoveryBootstrapPathPolicy.requireDirectoryComponent(
                existingType(directory),
                directory.name,
            )
        }

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
