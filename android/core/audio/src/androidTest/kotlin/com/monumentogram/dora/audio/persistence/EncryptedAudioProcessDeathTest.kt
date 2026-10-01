package com.monumentogram.dora.audio.persistence

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioCompletion
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioIntent
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.EncryptedAudioCatalog
import com.monumentogram.dora.audio.StoredAudioAsset
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Host mode is accepted only by the separate driver that observes and terminates this process. */
@RunWith(AndroidJUnit4::class)
class EncryptedAudioProcessDeathTest {
    private enum class Phase {
        INTENT,
        PUBLISHED,
        COMMITTED,
    }

    @Test
    fun verifyProcessDeathRecovery() {
        val arguments = InstrumentationRegistry.getArguments()
        val action = arguments.getString("persistenceCrashAction")
        if (action == null) {
            // Ordinary inventory proves the same phase/readback assertions without claiming death.
            Phase.entries.forEach { phase ->
                val fixture = Fixture(phase, UUID.randomUUID().toString())
                fixture.prepare(host = false)
                fixture.verify()
            }
            return
        }
        val phase = Phase.valueOf(checkNotNull(arguments.getString("persistenceCrashPhase")))
        val fixture = Fixture(phase, "host-${phase.name}")
        when (action) {
            "PREPARE" -> fixture.prepare(host = true)
            "VERIFY" -> {
                fixture.verify()
                signal("VERIFIED", phase)
            }
            else -> error("Unsupported synthetic process test action")
        }
    }

    private class Fixture(private val phase: Phase, suffix: String) {
        private val base = ApplicationProvider.getApplicationContext<Context>()
        private val root = File(base.noBackupFilesDir, "process-death-$suffix")
        private val context =
            object : ContextWrapper(base) {
                override fun getApplicationContext(): Context = this

                override fun getNoBackupFilesDir(): File = root
            }
        private val audio = AudioIdentity(RecordingId(uuid(1)), AudioAssetId(uuid(2)), uuid(3))
        private val unit = AudioStorageUnitIdentity(audio, uuid(4), 0, 0)
        private val pcm = ByteArray(320) { (it * 31 + 11).toByte() }
        private lateinit var catalog: EncryptedAudioCatalog

        fun prepare(host: Boolean) {
            assertTrue("Prepare requires a fresh synthetic namespace", root.mkdir())
            var reached = false
            val stop: (Phase) -> Unit = { current ->
                if (current == phase) {
                    reached = true
                    if (host) {
                        signal("READY", phase)
                        // Only host force-stop produces acceptance; expiry fails the test.
                        CountDownLatch(1).await(120, TimeUnit.SECONDS)
                        error("Host did not terminate the prepared process")
                    }
                    throw PhaseInterruption()
                }
            }
            open(create = true, stop).use { vault ->
                success(vault.writer.create(audio))
                assertTrue(
                    "Interrupted append cannot report success",
                    vault.writer.append(unit, AudioFormat.PCM, pcm) is AudioResult.Failed,
                )
                assertTrue("Required durable phase was reached", reached)
            }
        }

        fun verify() {
            assertTrue("Verification must preserve the prepared namespace", root.isDirectory)
            val keysBefore = aliases()
            open(create = false).use { vault ->
                val before = snapshot()
                assertTrue("Prepared identity must remain exact", before.identity == audio)
                if (phase == Phase.COMMITTED) {
                    assertTrue(
                        "Commit must survive death",
                        before.pending == null && before.segments.size == 1,
                    )
                } else {
                    assertTrue(
                        "Intent must survive death without an invented publication",
                        before.pending == AudioIntent.Append(unit, 160) &&
                            before.segments.isEmpty(),
                    )
                }
                if (phase == Phase.INTENT) verifyUnpublished(vault, before)
                else verifyPublished(vault)
                assertTrue(
                    "Reopening cannot replace the vault",
                    EncryptedAudioVault.createNew(context) {} is AudioResult.Failed,
                )
            }
            assertTrue("Recovery must not create or replace keys", aliases() == keysBefore)
        }

        private fun verifyUnpublished(vault: EncryptedAudioVault, before: StoredAudioAsset) {
            repeat(2) {
                assertEquals(
                    AudioResult.Failed(AudioFailure.INCOMPLETE),
                    vault.writer.reconcile(audio),
                )
                assertTrue("Unpublished intent stays durably fenced", snapshot() == before)
            }
            assertEquals(
                AudioResult.Failed(AudioFailure.COLLISION),
                vault.writer.append(unit, AudioFormat.PCM, pcm),
            )
            assertTrue(
                "Missing source cannot finalize",
                vault.writer.finalize(audio) is AudioResult.Failed,
            )
            assertTrue(
                "Reconciliation cannot bootstrap a replacement run",
                !File(root, "dora-vault-v1/poc-recovery/v1/runs/${unit.unitId}").exists(),
            )
        }

        private fun verifyPublished(vault: EncryptedAudioVault) {
            repeat(2) { success(vault.writer.reconcile(audio)) }
            val recovered = snapshot()
            assertTrue(
                "Reconciliation must commit only the exact original unit",
                recovered.pending == null &&
                    recovered.finalization == null &&
                    recovered.segments.size == 1 &&
                    recovered.segments.single().identity == unit &&
                    recovered.segments.single().frames == 160L,
            )
            verifyRead(vault, AudioCompletion.PARTIAL_RECOVERED)
            assertEquals(
                AudioResult.Failed(AudioFailure.INVALID_INPUT),
                vault.writer.append(unit, AudioFormat.PCM, pcm),
            )
            assertTrue("Duplicate retry cannot change the source", snapshot() == recovered)
            success(vault.writer.finalize(audio))
            verifyRead(vault, AudioCompletion.FINALIZED)
        }

        private fun verifyRead(vault: EncryptedAudioVault, expected: AudioCompletion) {
            var calls = 0
            var borrowed: ByteArray? = null
            val result =
                vault.reader.extract(audio) { frame, bytes ->
                    calls++
                    assertEquals(0L, frame)
                    assertTrue("Authenticated bytes must be exact", bytes.contentEquals(pcm))
                    borrowed = bytes
                }
            assertTrue("Authenticated source must be readable", result is AudioResult.Value)
            assertEquals(expected, (result as AudioResult.Value).value.completion)
            assertEquals(160L, result.value.frames)
            assertEquals(1, calls)
            assertTrue(
                "Borrowed bytes must be erased",
                checkNotNull(borrowed).all { it == 0.toByte() },
            )
        }

        private fun snapshot(): StoredAudioAsset =
            checkNotNull(catalog.tryAcquire(audio)).use {
                checkNotNull(catalog.load(audio))
            }

        private fun open(create: Boolean, stop: (Phase) -> Unit = {}): EncryptedAudioVault {
            val dependencies =
                EncryptedAudioVault.Dependencies(
                    catalog = { real ->
                        catalog = real
                        object : EncryptedAudioCatalog by real {
                            override fun reserve(
                                expected: StoredAudioAsset,
                                intent: AudioIntent,
                            ): Boolean =
                                real.reserve(expected, intent).also {
                                    if (it && intent is AudioIntent.Append) stop(Phase.INTENT)
                                }

                            override fun compareAndSet(
                                expected: StoredAudioAsset,
                                next: StoredAudioAsset,
                            ): Boolean {
                                val append = expected.pending is AudioIntent.Append
                                if (append) stop(Phase.PUBLISHED)
                                return real.compareAndSet(expected, next).also {
                                    if (it && append) stop(Phase.COMMITTED)
                                }
                            }
                        }
                    }
                )
            val result = EncryptedAudioVault.open(context, create, {}, { it() }, dependencies)
            assertTrue("Existing encrypted composition must open", result is AudioResult.Value)
            return (result as AudioResult.Value).value
        }

        private fun aliases(): Set<String> =
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.aliases().toList().toSet()
    }

    private class PhaseInterruption : RuntimeException()

    companion object {
        private fun uuid(value: Int) =
            "00000000-0000-4000-8000-${value.toString().padStart(12, '0')}"

        private fun success(result: AudioResult<Unit>) =
            assertTrue("Operation must succeed", result is AudioResult.Value)

        private fun signal(kind: String, phase: Phase) {
            val bundle =
                Bundle().apply {
                    putString("stream", "DORA_PERSISTENCE_$kind:${phase.name}:${Process.myPid()}")
                }
            InstrumentationRegistry.getInstrumentation().sendStatus(2, bundle)
        }
    }
}
