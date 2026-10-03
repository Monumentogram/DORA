package com.monumentogram.dora.audio.persistence.journal

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioSourceState
import com.monumentogram.dora.audio.persistence.database.SqlCipherJournalHelperFactory
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginalAudioMigrationTest {
    @Test
    fun exportedV1MigratesWithoutLosingAssetsPendingIntentsOrDeletionTombstones() {
        val fixture = V1Fixture()
        fixture.create()
        RoomAudioJournal.open(fixture.context, fixture.file, fixture.factory(), id(1), id(2), {})
            .use { journal ->
                journal.catalog.tryAcquire(fixture.deleted)!!.use {
                    assertEquals(AudioSourceState.UserDeleted, journal.sourceState(fixture.deleted))
                }
                journal.catalog.tryAcquire(fixture.pending)!!.use {
                    assertNotNull(journal.catalog.load(fixture.pending)!!.pending)
                }
            }
        fixture.raw(3) { db ->
            assertEquals(fixture.before, snapshot(db))
            db.query("SELECT count(*) FROM original_audio_reference").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
        assertFalse(fixture.file.readBytes().toString(Charsets.ISO_8859_1).contains(id(1)))
    }

    @Test
    fun alteredV1DdlIsRejectedBeforeAnySourceAuthorityCanBeCreated() {
        val fixture = V1Fixture()
        fixture.create(tamper = true)
        assertThrows(Exception::class.java) {
            RoomAudioJournal.open(
                    fixture.context,
                    fixture.file,
                    fixture.factory(),
                    id(1),
                    id(2),
                    {},
                )
                .close()
        }
        assertTrue(fixture.file.exists())
    }

    private class V1Fixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.noBackupFilesDir, "migration-${UUID.randomUUID()}.db")
        private val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val pending = AudioIdentity(RecordingId(id(3)), AudioAssetId(id(4)), id(5))
        val deleted = AudioIdentity(RecordingId(id(6)), AudioAssetId(id(7)), id(8))
        lateinit var before: List<String>

        fun factory() = SqlCipherJournalHelperFactory(context, file, secret)

        @Suppress("LongMethod") // Exact exported DDL and synthetic v1 fixture in one transaction.
        fun create(tamper: Boolean = false) {
            raw(
                1,
                create = { db ->
                    val schema =
                        JSONObject(
                                InstrumentationRegistry.getInstrumentation()
                                    .context
                                    .assets
                                    .open(
                                        "com.monumentogram.dora.audio.persistence.journal.AudioJournalDatabase/1.json"
                                    )
                                    .bufferedReader()
                                    .use { it.readText() }
                            )
                            .getJSONObject("database")
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                        val indices = entity.optJSONArray("indices")
                        for (j in 0 until (indices?.length() ?: 0)) db.execSQL(
                            indices!!
                                .getJSONObject(j)
                                .getString("createSql")
                                .replace("\${TABLE_NAME}", table)
                        )
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    db.execSQL("INSERT INTO vault_binding VALUES(1,?,?)", arrayOf(id(1), id(2)))
                    listOf(pending, deleted).forEach {
                        db.execSQL(
                            "INSERT INTO audio_asset VALUES(?,?,?,?,?,0)",
                            arrayOf(
                                it.assetId.value,
                                it.recordingId.value,
                                it.sessionId,
                                id(1),
                                id(2),
                            ),
                        )
                    }
                    db.execSQL("INSERT INTO physical_source VALUES(?,?,0)", arrayOf(id(4), id(9)))
                    db.execSQL(
                        "INSERT INTO unit_claim VALUES(?,?,0,0,2,?,0,0,0)",
                        arrayOf(id(10), id(4), id(9)),
                    )
                    db.execSQL(
                        "INSERT INTO audio_intent VALUES(?,'APPEND',?,0)",
                        arrayOf(id(4), id(10)),
                    )
                    db.execSQL(
                        "INSERT INTO deletion_tombstone VALUES(?,?,'USER_DELETED')",
                        arrayOf(id(7), id(11)),
                    )
                    if (tamper) db.execSQL("CREATE TABLE unapproved_plain_metadata(value TEXT)")
                },
            ) {
                before = snapshot(it)
            }
        }

        fun raw(
            version: Int,
            create: (SupportSQLiteDatabase) -> Unit = {},
            block: (SupportSQLiteDatabase) -> Unit,
        ) {
            val factory = factory()
            val helper =
                factory.create(
                    SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name(file.path)
                        .callback(
                            object : SupportSQLiteOpenHelper.Callback(version) {
                                override fun onCreate(db: SupportSQLiteDatabase) = create(db)

                                override fun onUpgrade(
                                    db: SupportSQLiteDatabase,
                                    oldVersion: Int,
                                    newVersion: Int,
                                ) = error("Unexpected test upgrade")
                            }
                        )
                        .build()
                )
            try {
                block(helper.writableDatabase)
            } finally {
                helper.close()
                factory.close()
            }
        }
    }

    companion object {
        private fun id(n: Int) = "00000000-0000-0000-0000-%012d".format(n)

        private fun snapshot(db: SupportSQLiteDatabase): List<String> = buildList {
            listOf(
                    "vault_binding",
                    "audio_asset",
                    "physical_source",
                    "unit_claim",
                    "audio_intent",
                    "bootstrap",
                    "manifest",
                    "microfile",
                    "finalization_source",
                    "quarantine_intent",
                    "deletion_tombstone",
                    "deletion_target",
                )
                .forEach { table ->
                    db.query("SELECT * FROM $table ORDER BY rowid").use { cursor ->
                        while (cursor.moveToNext()) add(
                            table +
                                (0 until cursor.columnCount).joinToString("|") {
                                    if (cursor.isNull(it)) "NULL" else cursor.getString(it)
                                }
                        )
                    }
                }
        }
    }
}
