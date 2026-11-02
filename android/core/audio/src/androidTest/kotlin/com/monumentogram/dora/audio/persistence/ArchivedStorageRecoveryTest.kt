package com.monumentogram.dora.audio.persistence

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.persistence.journal.JournalWalCheckpoint
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Never creates or removes a fixture. The parent restores only the preserved synthetic archive. */
class ArchivedStorageRecoveryTest {
    @Test
    @Suppress("LongMethod") // Keep the preserved-fixture admission and exact-prefix proof together.
    fun preservedFullDiskFixtureCheckpointsAndAuthenticatesItsExactCommittedPrefix() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("doraRecoverFailed24h") == "true"
        )
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.noBackupFilesDir, FAILED_ROOT).canonicalFile
        check(root.parentFile == base.noBackupFilesDir.canonicalFile && root.isDirectory)
        val context =
            object : ContextWrapper(base) {
                override fun getApplicationContext(): Context = this

                override fun getNoBackupFilesDir(): File = root
            }
        var database: SupportSQLiteDatabase? = null
        val dependencies =
            EncryptedAudioVault.Dependencies(
                helperFactory = { delegate ->
                    SupportSQLiteOpenHelper.Factory { config ->
                        val helper = delegate.create(config)
                        object : SupportSQLiteOpenHelper by helper {
                            override val writableDatabase
                                get() = helper.writableDatabase.also { database = it }
                        }
                    }
                }
            )
        val began = SystemClock.elapsedRealtime()
        val opened = EncryptedAudioVault.open(context, false, {}, { it() }, dependencies)
        assertTrue(
            "Preserved synthetic vault must open with its retained keys",
            opened is AudioResult.Value,
        )
        (opened as AudioResult.Value).value.use { vault ->
            val db = checkNotNull(database)
            val identity =
                db.query("SELECT recordingId,assetId,sessionId FROM audio_asset").use {
                    check(it.moveToFirst())
                    val value =
                        AudioIdentity(
                            RecordingId(it.getString(0)),
                            AudioAssetId(it.getString(1)),
                            it.getString(2),
                        )
                    check(!it.moveToNext())
                    value
                }
            val units =
                db.query("SELECT COUNT(*) FROM unit_claim WHERE committed=1").use {
                    check(it.moveToFirst())
                    it.getInt(0)
                }
            check(units in 8_640..17_280)
            // This opt-in diagnostic is serialized on the sole admitted connection, without any
            // source mutation or recovery reconciliation of the possibly incomplete final unit.
            JournalWalCheckpoint.perform(db)
            val actual = MessageDigest.getInstance("SHA-256")
            var next = 0L
            val recovered =
                vault.reader.extract(identity) { first, bytes ->
                    assertEquals(next, first)
                    assertEquals(160_000, bytes.size)
                    actual.update(bytes)
                    next += bytes.size / 2
                }
            assertTrue("Committed prefix must authenticate", recovered is AudioResult.Value)
            assertEquals(units * 80_000L, next)
            assertEquals(next, (recovered as AudioResult.Value).value.frames)
            val expected = MessageDigest.getInstance("SHA-256")
            val pcm = ByteArray(160_000) { (it * 37 + 19).toByte() }
            try {
                repeat(units) { ordinal ->
                    repeat(8) { offset ->
                        pcm[offset] = (ordinal.toLong() ushr (offset * 8)).toByte()
                        pcm[pcm.size - 8 + offset] =
                            (ordinal.toLong().inv() ushr (offset * 8)).toByte()
                    }
                    expected.update(pcm)
                }
                assertArrayEquals(expected.digest(), actual.digest())
            } finally {
                pcm.fill(0)
            }
            Log.i(
                "DoraArchiveRecovery",
                "units=$units frames=$next bytes=${next * 2} " +
                    "elapsed_ms=${SystemClock.elapsedRealtime() - began} available_bytes=${root.usableSpace}",
            )
        }
    }

    private companion object {
        const val FAILED_ROOT = "fault-068708ea-9ed3-45d9-9430-7e880f5932d2"
    }
}
