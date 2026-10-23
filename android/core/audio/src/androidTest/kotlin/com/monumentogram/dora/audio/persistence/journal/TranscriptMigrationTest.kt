package com.monumentogram.dora.audio.persistence.journal

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.persistence.database.SqlCipherJournalHelperFactory
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptMigrationTest {
    @Test
    fun encryptedV3MigratesWithoutChangingOriginalRows() {
        val f = Fixture()
        f.create(false)
        RoomAudioJournal.open(f.context, f.file, f.factory(), id(1), id(2), {}).use {
            assertTrue(it.transcriptHistory().isEmpty())
        }
        f.raw(4) { db ->
            JournalSchemaVerifier.verify(db)
            db.query(
                    "SELECT recordingId,sessionId,ownerId,vaultId,finalized FROM audio_asset WHERE assetId=?",
                    arrayOf(id(4)),
                )
                .use {
                    assertTrue(it.moveToFirst())
                    assertEquals(id(3), it.getString(0))
                    assertEquals(id(5), it.getString(1))
                    assertEquals(id(1), it.getString(2))
                    assertEquals(id(2), it.getString(3))
                    assertEquals(0, it.getInt(4))
                }
            db.query("SELECT count(*) FROM transcript_state").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        }
        assertFalse(f.file.readBytes().toString(Charsets.ISO_8859_1).contains(id(3)))
    }

    @Test
    fun tamperedV3RollsBackBeforeTranscriptTableCreation() {
        val f = Fixture()
        f.create(true)
        assertThrows(Exception::class.java) {
            RoomAudioJournal.open(f.context, f.file, f.factory(), id(1), id(2), {}).close()
        }
        f.raw(3) { db ->
            db.query("SELECT count(*) FROM sqlite_master WHERE name='transcript_state'").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        }
    }

    private class Fixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.noBackupFilesDir, "transcript-migration-${UUID.randomUUID()}.db")
        private val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }

        fun factory() = SqlCipherJournalHelperFactory(context, file, secret)

        fun create(tamper: Boolean) =
            raw(
                3,
                { db ->
                    val schema =
                        JSONObject(
                                InstrumentationRegistry.getInstrumentation()
                                    .context
                                    .assets
                                    .open(
                                        "com.monumentogram.dora.audio.persistence.journal.AudioJournalDatabase/3.json"
                                    )
                                    .bufferedReader()
                                    .use { it.readText() }
                            )
                            .getJSONObject("database")
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val name = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", name))
                        val indices = entity.getJSONArray("indices")
                        for (j in 0 until indices.length()) db.execSQL(
                            indices
                                .getJSONObject(j)
                                .getString("createSql")
                                .replace("\${TABLE_NAME}", name)
                        )
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    db.execSQL("INSERT INTO vault_binding VALUES(1,?,?)", arrayOf(id(1), id(2)))
                    db.execSQL(
                        "INSERT INTO audio_asset VALUES(?,?,?,?,?,0)",
                        arrayOf(id(4), id(3), id(5), id(1), id(2)),
                    )
                    if (tamper) db.execSQL("CREATE TABLE forbidden(value TEXT)")
                },
            ) {}

        fun raw(
            version: Int,
            create: (SupportSQLiteDatabase) -> Unit = { error("Missing fixture") },
            action: (SupportSQLiteDatabase) -> Unit,
        ) {
            val helper =
                factory()
                    .create(
                        SupportSQLiteOpenHelper.Configuration.builder(context)
                            .name(file.path)
                            .callback(
                                object : SupportSQLiteOpenHelper.Callback(version) {
                                    override fun onCreate(db: SupportSQLiteDatabase) = create(db)

                                    override fun onUpgrade(
                                        db: SupportSQLiteDatabase,
                                        oldVersion: Int,
                                        newVersion: Int,
                                    ) = error("Unexpected migration")
                                }
                            )
                            .build()
                    )
            try {
                action(helper.writableDatabase)
            } finally {
                helper.close()
            }
        }
    }

    companion object {
        private fun id(number: Int) =
            "00000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
    }
}
