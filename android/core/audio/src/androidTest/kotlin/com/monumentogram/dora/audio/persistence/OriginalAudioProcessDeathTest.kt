package com.monumentogram.dora.audio.persistence

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Process
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioIntent
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.EncryptedAudioCatalog
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.StoredAudioAsset
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture.Companion.success
import com.monumentogram.dora.audio.persistence.OriginalAudioRuntimeTest.Companion.reference
import com.monumentogram.dora.audio.persistence.database.SqlCipherJournalHelperFactory
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultBundleStorage
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultKeyBackend
import com.monumentogram.dora.audio.persistence.keys.KeyAccess
import com.monumentogram.dora.audio.persistence.keys.VaultSecretStore
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Driver phases INTENT=finalization intent, PUBLISHED=exact reference, COMMITTED=audio deletion.
 */
class OriginalAudioProcessDeathTest {
    @Test
    fun verifyOriginalAudioProcessDeath() {
        val args = InstrumentationRegistry.getArguments()
        val action = args.getString("persistenceCrashAction")
        if (action == null) {
            listOf("INTENT", "PUBLISHED", "COMMITTED").forEach { phase ->
                val f = Fixture(phase, UUID.randomUUID().toString())
                f.prepare(false)
                f.verify()
            }
        } else {
            val phase = checkNotNull(args.getString("persistenceCrashPhase"))
            check(phase in setOf("INTENT", "PUBLISHED", "COMMITTED"))
            val f = Fixture(phase, "host-$phase")
            when (action) {
                "PREPARE" -> f.prepare(true)
                "VERIFY" -> {
                    f.verify()
                    signal("VERIFIED", phase)
                }
                else -> error("Unsupported process test action")
            }
        }
    }

    private class Fixture(private val phase: String, suffix: String) {
        private val base = ApplicationProvider.getApplicationContext<Context>()
        private val root = File(base.noBackupFilesDir, "original-process-$suffix")
        private val context =
            object : ContextWrapper(base) {
                override fun getApplicationContext(): Context = this

                override fun getNoBackupFilesDir(): File = root
            }
        private val audio = AudioIdentity(RecordingId(id(1)), AudioAssetId(id(2)), id(3))
        private val unit = AudioStorageUnitIdentity(audio, id(4), 0, 0)
        private val pcm = ByteArray(32) { (it * 17 + 3).toByte() }

        fun prepare(host: Boolean) {
            check(root.mkdir())
            val dependencies =
                EncryptedAudioVault.Dependencies(
                    catalog = { real ->
                        object : EncryptedAudioCatalog by real {
                            override fun reserve(
                                expected: StoredAudioAsset,
                                intent: AudioIntent,
                            ): Boolean {
                                val result = real.reserve(expected, intent)
                                if (phase == "INTENT" && intent is AudioIntent.Finalize && result) {
                                    stop(host)
                                    error("Synthetic finalization interruption")
                                }
                                return result
                            }
                        }
                    }
                )
            val result = EncryptedAudioVault.open(context, true, {}, { it() }, dependencies)
            (result as AudioResult.Value).value.use { vault ->
                success(vault.writer.create(audio))
                success(vault.writer.append(unit, AudioFormat.PCM, pcm))
                val finalized = vault.writer.finalize(audio)
                if (phase == "INTENT") assertTrue(finalized is AudioResult.Failed)
                else {
                    success(finalized)
                    reference(vault, audio)
                    if (phase == "COMMITTED") success(vault.deleteAudio(audio))
                    stop(host)
                }
            }
        }

        private fun stop(host: Boolean) {
            if (host) {
                signal("READY", phase)
                CountDownLatch(1).await(120, TimeUnit.SECONDS)
                error("Host did not terminate synthetic process")
            }
        }

        fun verify() {
            check(root.isDirectory)
            val expected = if (phase == "INTENT") null else retainedReference()
            val result = EncryptedAudioVault.openExisting(context) {}
            (result as AudioResult.Value).value.use { vault ->
                when (phase) {
                    "INTENT" -> {
                        assertEquals(
                            AudioResult.Failed(AudioFailure.UNCERTAIN),
                            vault.originals.acquire(audio),
                        )
                        success(vault.writer.reconcile(audio))
                        assertEquals(16L, reference(vault, audio).frames)
                    }
                    "PUBLISHED" -> {
                        assertEquals(expected, reference(vault, audio))
                        assertEquals(
                            AudioResult.Value(OriginalAudioStatus.Available(expected!!)),
                            vault.originals.extract(expected) { _, bytes ->
                                assertTrue(pcm.contentEquals(bytes))
                            },
                        )
                    }
                    "COMMITTED" -> {
                        assertEquals(
                            AudioResult.Value(OriginalAudioStatus.SourceDeleted),
                            vault.originals.withAvailable(expected!!) {
                                fail("Late process callback")
                            },
                        )
                        success(vault.retryDeletion(audio))
                    }
                }
                assertTrue(EncryptedAudioVault.createNew(context) {} is AudioResult.Failed)
            }
        }

        /** Read the pre-death exact reference only from its encrypted admitted table. */
        private fun retainedReference(): OriginalAudioReference {
            val storage = AndroidVaultBundleStorage(context)
            val secrets =
                (VaultSecretStore(storage, AndroidVaultKeyBackend(context)).openExisting()
                        as KeyAccess.Available)
                    .value
            val file = File(storage.vaultDirectory, "journal-${secrets.databaseObjectSelector}.db")
            return secrets.borrowDatabaseSecret { key ->
                val factory = SqlCipherJournalHelperFactory(context, file, key)
                val helper =
                    factory.create(
                        SupportSQLiteOpenHelper.Configuration.builder(context)
                            .name(file.path)
                            .callback(
                                object : SupportSQLiteOpenHelper.Callback(2) {
                                    override fun onCreate(db: SupportSQLiteDatabase) =
                                        error("Existing source required")

                                    override fun onUpgrade(
                                        db: SupportSQLiteDatabase,
                                        oldVersion: Int,
                                        newVersion: Int,
                                    ) = error("No test migration")
                                }
                            )
                            .build()
                    )
                try {
                    helper.writableDatabase
                        .query(
                            "SELECT version,digest,frames FROM original_audio_reference WHERE assetId=?",
                            arrayOf(audio.assetId.value),
                        )
                        .use {
                            check(it.moveToFirst())
                            OriginalAudioReference(
                                it.getInt(0),
                                audio,
                                it.getString(1),
                                it.getLong(2),
                            )
                        }
                } finally {
                    helper.close()
                    factory.close()
                }
            }
        }
    }

    companion object {
        private fun id(n: Int) = "00000000-0000-0000-0000-%012d".format(n)

        private fun signal(kind: String, phase: String) {
            InstrumentationRegistry.getInstrumentation()
                .sendStatus(
                    2,
                    Bundle().apply {
                        putString("stream", "DORA_PERSISTENCE_$kind:$phase:${Process.myPid()}")
                    },
                )
        }
    }
}
