@file:Suppress("LongMethod", "MagicNumber", "TooManyFunctions", "MaxLineLength")

package com.monumentogram.dora.poc.recovery.journal

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Runs inside the owned JOURNAL_CONNECTIONS preflight alongside STREAM sealed-FATAL replay. Failure
 * helper below tests real SQLiteOpenHelper atomicity with production migration functions;
 * successful routes and reopen use the actual RecoveryJournalSqliteHelper.
 */
internal object RecoveryMicrofileDispositionMigrationVerification {
    private const val Q = "recovery_quarantine_intent_v4"
    private const val OLD_Q = "recovery_quarantine_intent_v3"
    private const val MICRO = "REC-MICROFILE-TINK"
    private const val STREAM = "REC-STREAM-TINK"
    private val microRun = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")
    private val streamRun = RunId.fromCanonicalString("10112233-4455-6677-8899-aabbccddeeff")
    private val historicalStates =
        listOf(
            "TEMP_ONLY",
            "TEMP_AND_FINAL",
            "FINAL_ORPHAN",
            "SQLITE_POINTS_TO_TEMP",
            "UNKNOWN_OR_NON_ALLOWLISTED_NAME",
        )
    private val expectedSteps =
        listOf(
            "PREFLIGHT",
            "CREATE_BACKUP",
            "COPY_QUARANTINE",
            "VERIFY_BACKUP",
            "DROP_QUARANTINE",
            "CREATE_QUARANTINE",
            "RESTORE_QUARANTINE",
            "VERIFY_COPY",
            "DROP_BACKUP",
            "VERIFIED",
        )
    private val tables =
        listOf(
            "recovery_run_bootstrap_v1",
            "recovery_microfile_unit_v2",
            "recovery_manifest_publication_v2",
            Q,
            "recovery_stream_checkpoint_v4",
            "recovery_stream_outcome_v4",
            "recovery_stream_range_quarantine_v4",
        )

    fun verify(parent: Context) {
        assertEquals(expectedSteps, RecoveryMicrofileDispositionSchema.Step.entries.map { it.name })
        val fresh = ownedContext(parent, "fresh")
        repeat(2) {
            RecoveryJournalSqliteHelper(fresh).use { helper ->
                val db = helper.writableDatabase
                requireCurrent(db)
                assertEquals(tables.toSet(), snapshots(db).keys)
                assertTrue(snapshots(db).values.all { it.rows.isEmpty() })
            }
        }
        // Test direct creator in its required transaction, independently of onCreate routing.
        val direct = ownedContext(parent, "direct-create")
        raw(direct).use { db ->
            transaction(db) { RecoveryMicrofileDispositionSchema.create(db) }
            RecoveryJournalSchema.requireExactV6(db)
            assertEquals(0, db.version) // only SQLiteOpenHelper assigns user_version
            integrity(db)
            admissionMatrix(db)
        }
        for (old in 1..5) verifyUpgrade(parent, old)
        for (old in 1..5) {
            for (stop in expectedSteps + "AFTER_VERSION_ASSIGNMENT") {
                verifyRollback(parent, old, stop)
            }
        }
        verifyRejectedOpen(parent, "unknown-v5", 5, addIndex = true)
        verifyRejectedOpen(parent, "unknown-v6", 6, addIndex = true)
        verifyRejectedOpen(parent, "downgrade-v7", 7, addIndex = false)
        println(
            "MICROFILE_DISPOSITION_SCHEMA6_MIGRATION verified=true fresh=true oldVersions=1,2,3,4,5 " +
                "typedQuarantinePreserved=true rollbackCases=55 unknownSchemaRejected=true downgradeRejected=true"
        )
    }

    private fun verifyUpgrade(parent: Context, old: Int) {
        val context = ownedContext(parent, "upgrade-$old")
        val before = seed(context, old)
        var first: Map<String, TableSnapshot>? = null
        repeat(2) {
            RecoveryJournalSqliteHelper(context).use { helper ->
                val db = helper.writableDatabase
                requireCurrent(db)
                val after = snapshots(db)
                assertEquals(tables.toSet(), after.keys)
                before.tables.forEach { (name, value) ->
                    assertEquals(
                        "old=$old table=$name",
                        value,
                        after[if (name == OLD_Q) Q else name],
                    )
                }
                if (old == 5) {
                    // Only quarantine's CREATE TABLE text may change; all indexes/other SQL exact.
                    assertEquals(
                        before.objects.filterNot { it[1] == Q },
                        objects(db).filterNot { it[1] == Q },
                    )
                }
                if (first == null) first = after else assertEquals(first, after)
                assertEquals(emptyList<List<String?>>(), temporaryObjects(db))
            }
        }
    }

    private fun verifyRollback(parent: Context, old: Int, stop: String) {
        val context = ownedContext(parent, "rollback-$old-$stop")
        val before = seed(context, old)
        var reached = false
        val marker = InjectedFailure()
        val helper =
            object :
                SQLiteOpenHelper(
                    context,
                    RecoveryJournalSqliteHelper.databasePath(context).path,
                    null,
                    6,
                ) {
                override fun onConfigure(db: SQLiteDatabase) =
                    db.setForeignKeyConstraintsEnabled(true)

                override fun onCreate(db: SQLiteDatabase): Unit =
                    error("Existing database required")

                override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    assertEquals(old, oldVersion)
                    assertEquals(6, newVersion)
                    assertEquals(old, db.version)
                    if (oldVersion == 1) RecoveryJournalSchema.migrateV1ToV2(db)
                    if (oldVersion <= 2) RecoveryJournalSchema.migrateV2ToV3(db)
                    if (oldVersion <= 3) RecoveryJournalSchema.migrateV3ToV4(db)
                    if (oldVersion <= 4) RecoveryStreamPrefixSchema.migrate(db)
                    RecoveryMicrofileDispositionSchema.migrate(db) { step ->
                        assertEquals(
                            old,
                            db.version,
                        ) // chained migration sees original user_version
                        if (step.name == stop) {
                            reached = true
                            throw marker
                        }
                    }
                    if (stop == "AFTER_VERSION_ASSIGNMENT") {
                        db.version = 6
                        reached = true
                        throw marker
                    }
                    error("Unreached failpoint $stop")
                }
            }
        try {
            try {
                helper.writableDatabase
                error("Upgrade unexpectedly succeeded at $stop")
            } catch (failure: InjectedFailure) {
                assertTrue(failure === marker)
            }
        } finally {
            helper.close()
        }
        assertTrue("Failpoint not reached: old=$old $stop", reached)
        raw(context).use { db ->
            assertEquals(before, image(db))
            integrity(db)
        }
    }

    private fun verifyRejectedOpen(parent: Context, name: String, version: Int, addIndex: Boolean) {
        val context = ownedContext(parent, name)
        seed(context, 5)
        val before =
            raw(context).use { db ->
                if (version >= 6) transaction(db) { RecoveryMicrofileDispositionSchema.migrate(db) }
                db.version = version
                if (addIndex) db.execSQL("CREATE INDEX recovery_unadmitted ON $Q(source_bytes)")
                image(db)
            }
        var rejected = false
        try {
            RecoveryJournalSqliteHelper(context).use { it.writableDatabase }
        } catch (_: SQLiteException) {
            rejected = true
        }
        assertTrue("Expected schema/version refusal: $name", rejected)
        raw(context).use { assertEquals(before, image(it)) }
    }

    private fun seed(context: Context, version: Int): DatabaseImage =
        raw(context).use { db ->
            transaction(db) {
                // Literal historical routes: never ask the schema6 creator to manufacture old
                // input.
                db.execSQL(RecoveryJournalSchema.CREATE_RUN_TABLE)
                if (version >= 2) {
                    db.execSQL(RecoveryJournalSchema.CREATE_RUN_IDENTITY_INDEX)
                    db.execSQL(RecoveryJournalSchema.CREATE_UNIT_TABLE)
                    db.execSQL(RecoveryJournalSchema.CREATE_PUBLICATION_TABLE)
                }
                if (version == 3) db.execSQL(RecoveryJournalSchema.CREATE_QUARANTINE_V3_TABLE)
                if (version >= 4) {
                    db.execSQL(RecoveryJournalSchema.CREATE_QUARANTINE_TABLE)
                    db.execSQL(RecoveryJournalSchema.CREATE_STREAM_CHECKPOINT_TABLE)
                    db.execSQL(
                        if (version == 4) RecoveryJournalSchema.CREATE_STREAM_OUTCOME_TABLE
                        else RecoveryStreamPrefixSchema.CREATE_OUTCOME_TABLE
                    )
                    db.execSQL(RecoveryJournalSchema.CREATE_STREAM_RANGE_TABLE)
                    db.execSQL(RecoveryJournalSchema.CREATE_STREAM_ACTIVE_RANGE_INDEX)
                }
                for ((run, candidate) in listOf(microRun to MICRO, streamRun to STREAM)) {
                    db.execSQL(
                        "INSERT INTO recovery_run_bootstrap_v1 VALUES (?,?,?,?,?,?,?)",
                        arrayOf(
                            run.toCanonicalString(),
                            candidate,
                            "key-confirmation/run.kc",
                            1L,
                            bytes(1),
                            bytes(2),
                            "VALID",
                        ),
                    )
                }
                if (version >= 2) seedMicroRows(db)
                if (version >= 3) seedQuarantine(db, version)
                db.version = version
            }
            integrity(db)
            image(db)
        }

    private fun seedMicroRows(db: SQLiteDatabase) {
        // Reverse insertion order catches a snapshot that orders only by shared run_id.
        for (index in listOf(1, 0)) {
            val run = microRun.toCanonicalString()
            val generation = index + 1
            val unitName = "units/u-${index.toString().padStart(10, '0')}.ct"
            val envelopeName = "key-envelopes/u-${index.toString().padStart(10, '0')}.ks"
            db.execSQL(
                "INSERT INTO recovery_microfile_unit_v2 VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf(
                    run,
                    MICRO,
                    index,
                    index * 4,
                    (index + 1) * 4,
                    5,
                    unitName,
                    17,
                    bytes(10 + index),
                    envelopeName,
                    18,
                    bytes(20 + index),
                    generation,
                    bytes(30 + index),
                    "VALID",
                ),
            )
            db.execSQL(
                "INSERT INTO recovery_manifest_publication_v2 VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf(
                    run,
                    MICRO,
                    "MANIFEST",
                    generation,
                    generation * 4,
                    "manifests/g-${generation.toString().padStart(20, '0')}.ct",
                    19,
                    bytes(40 + index),
                    "key-envelopes/manifest-g-${generation.toString().padStart(20, '0')}.ks",
                    20,
                    bytes(50 + index),
                    bytes(60 + index),
                    "VALID",
                ),
            )
        }
    }

    @Suppress("NestedBlockDepth")
    private fun seedQuarantine(db: SQLiteDatabase, version: Int) {
        val table = if (version == 3) OLD_Q else Q
        val candidates =
            if (version == 3) listOf(RecoveryCandidate.MICROFILE)
            else listOf(RecoveryCandidate.MICROFILE, RecoveryCandidate.STREAM)
        var ordinal = 0
        for (candidate in candidates) for (observed in historicalStates) for (state in
            listOf("PENDING", "COMPLETED")) {
            ordinal++
            val run = if (candidate == RecoveryCandidate.MICROFILE) microRun else streamRun
            val input =
                RecoveryQuarantineIntentInput(
                    candidate,
                    run,
                    "historical-$ordinal.bin",
                    RecoveryQuarantineArtifactRole.UNKNOWN_REGULAR,
                    ordinal.toULong(),
                    Sha256Value.fromBytes(bytes(ordinal)),
                )
            val present = ordinal % 2 == 0
            insertQ(
                db,
                table,
                arrayOf(
                    RecoveryQuarantineIntent.calculate(input).toByteArray(),
                    run.toCanonicalString(),
                    candidate.contractId,
                    if (present) "PRESENT" else "ABSENT",
                    if (present) run.toCanonicalString() else null,
                    if (present) candidate.contractId else null,
                    "UNKNOWN_REGULAR",
                    observed,
                    input.sourceRelativeName,
                    RecoveryQuarantineIntent.destination(input),
                    ordinal,
                    input.sourceSha256.toByteArray(),
                    state,
                ),
            )
        }
        if (version >= 4) {
            // Legal historical SQLite cells; migration must not run domain decoders over them.
            val exotic =
                arrayOf<Any?>(
                    bytes(220),
                    microRun.toCanonicalString(),
                    MICRO,
                    "ABSENT",
                    null,
                    null,
                    "UNKNOWN_REGULAR",
                    "FINAL_ORPHAN",
                    "non-ascii-\u03bb\u0000tail",
                    ByteArray(0),
                    0.5,
                    "s".repeat(32),
                    "PENDING",
                )
            insertQ(db, table, exotic)
            insertQ(
                db,
                table,
                exotic.copyOf().apply {
                    this[0] = bytes(221)
                    this[3] = "PRESENT" // old CHECK(NULL) remains historical
                    this[8] = byteArrayOf(0, 0xff.toByte(), 1)
                    this[9] = ""
                    this[10] = 0L
                    this[11] = bytes(222)
                    this[12] = "COMPLETED"
                },
            )
            assertEquals(
                listOf(listOf("real", "text", "text", "blob")),
                query(
                    db,
                    "SELECT typeof(source_bytes),typeof(source_sha256),typeof(source_relative_name),typeof(destination_relative_name) FROM $Q WHERE hex(intent_id)='${bytes(220).hex().uppercase()}'",
                ),
            )
        }
    }

    @Suppress("NestedBlockDepth")
    private fun admissionMatrix(db: SQLiteDatabase) {
        for ((run, candidate) in listOf(microRun to MICRO, streamRun to STREAM)) {
            db.execSQL(
                "INSERT INTO recovery_run_bootstrap_v1 VALUES (?,?,?,?,?,?,?)",
                arrayOf(
                    run.toCanonicalString(),
                    candidate,
                    "key-confirmation/run.kc",
                    1,
                    bytes(1),
                    bytes(2),
                    "VALID",
                ),
            )
        }
        val roles =
            listOf(
                "MICROFILE_CIPHERTEXT",
                "MICROFILE_KEY_ENVELOPE",
                "MANIFEST_CIPHERTEXT",
                "MANIFEST_KEY_ENVELOPE",
            )
        var id = 100
        for (state in listOf("REFERENCED_REJECTED", "REFERENCED_DEPENDENT")) for (role in roles) {
            val row =
                arrayOf<Any?>(
                    bytes(id++),
                    microRun.toCanonicalString(),
                    MICRO,
                    "PRESENT",
                    microRun.toCanonicalString(),
                    MICRO,
                    role,
                    state,
                    "source-$id",
                    "objects/test-$id",
                    1,
                    bytes(id),
                    "COMPLETED",
                )
            insertQ(db, Q, row)
            for (invalid in
                listOf(
                    row.copyOf().apply {
                        this[1] = streamRun.toCanonicalString()
                        this[2] = STREAM
                        this[4] = streamRun.toCanonicalString()
                        this[5] = STREAM
                        this[6] = "CHECKPOINT_CIPHERTEXT"
                    },
                    row.copyOf().apply {
                        this[3] = "ABSENT"
                        this[4] = null
                        this[5] = null
                    },
                    row.copyOf().apply { this[4] = null },
                    row.copyOf().apply { this[5] = null },
                    row.copyOf().apply {
                        this[4] = streamRun.toCanonicalString()
                        this[5] = STREAM
                    },
                    row.copyOf().apply { this[6] = "KEY_CONFIRMATION" },
                    row.copyOf().apply { this[6] = "UNKNOWN_REGULAR" },
                )) {
                invalid[0] = bytes(id++)
                invalid[8] = "negative-$id" // ensure failure is CHECK/FK, not UNIQUE
                val before = snapshots(db)
                var rejected = false
                try {
                    insertQ(db, Q, invalid)
                } catch (_: SQLiteException) {
                    rejected = true
                }
                assertTrue("Unadmitted $state row", rejected)
                assertEquals(before, snapshots(db))
            }
        }
        integrity(db)
    }

    private fun insertQ(db: SQLiteDatabase, table: String, values: Array<Any?>) =
        db.execSQL("INSERT INTO $table VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", values)

    private data class TableSnapshot(
        val tableInfo: List<List<String?>>,
        val columns: List<String>,
        val primaryKey: List<String>,
        val rows: List<List<String?>>,
    )

    private data class DatabaseImage(
        val version: Int,
        val tables: Map<String, TableSnapshot>,
        val objects: List<List<String?>>,
        val temporary: List<List<String?>>,
    )

    private fun image(db: SQLiteDatabase) =
        DatabaseImage(db.version, snapshots(db), objects(db), temporaryObjects(db))

    private fun snapshots(db: SQLiteDatabase): Map<String, TableSnapshot> = buildMap {
        for (table in tables + OLD_Q) {
            val info = query(db, "PRAGMA table_info($table)")
            if (info.isEmpty()) continue
            val columns = info.map { requireNotNull(it[1]) }
            val pk =
                info
                    .filter { requireNotNull(it[5]).toInt() > 0 }
                    .sortedBy { requireNotNull(it[5]).toInt() }
                    .map { requireNotNull(it[1]) }
            check(pk.isNotEmpty())
            val projection = columns.joinToString(",") { "typeof($it),hex(CAST($it AS BLOB))" }
            put(
                table,
                TableSnapshot(
                    info,
                    columns,
                    pk,
                    query(db, "SELECT $projection FROM $table ORDER BY ${pk.joinToString(",")}"),
                ),
            )
        }
    }

    private fun objects(db: SQLiteDatabase) =
        query(
            db,
            "SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name LIKE 'recovery_%' OR tbl_name LIKE 'recovery_%' ORDER BY type,name",
        )

    private fun temporaryObjects(db: SQLiteDatabase) =
        query(
            db,
            "SELECT type,name,tbl_name,sql FROM sqlite_temp_master ORDER BY type,name",
        )

    private fun query(db: SQLiteDatabase, sql: String): List<List<String?>> =
        db.rawQuery(sql, null).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    (0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }
                )
            }
        }

    private fun requireCurrent(db: SQLiteDatabase) {
        assertEquals(6, db.version)
        RecoveryJournalSchema.requireExactV6(db)
        assertEquals(24, objects(db).size)
        integrity(db)
        assertEquals(emptyList<List<String?>>(), temporaryObjects(db))
    }

    private fun integrity(db: SQLiteDatabase) {
        assertEquals(listOf(listOf("1")), query(db, "PRAGMA foreign_keys"))
        assertEquals(listOf(listOf("ok")), query(db, "PRAGMA integrity_check"))
        assertEquals(emptyList<List<String?>>(), query(db, "PRAGMA foreign_key_check"))
    }

    private fun transaction(db: SQLiteDatabase, block: () -> Unit) {
        db.beginTransactionNonExclusive()
        try {
            block()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun raw(context: Context): SQLiteDatabase =
        SQLiteDatabase.openOrCreateDatabase(RecoveryJournalSqliteHelper.databasePath(context), null)
            .also {
                it.setForeignKeyConstraintsEnabled(true)
            }

    private fun ownedContext(parent: Context, suffix: String): Context =
        object : ContextWrapper(parent) {
            private val directory =
                File(parent.noBackupFilesDir, "microfile-disposition-migration-$suffix").also {
                    check(it.mkdir()) { "Fixture directory already consumed: $it" }
                }

            override fun getNoBackupFilesDir() = directory
        }

    private fun bytes(value: Int) = ByteArray(32) { value.toByte() }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }

    private class InjectedFailure : RuntimeException()
}
