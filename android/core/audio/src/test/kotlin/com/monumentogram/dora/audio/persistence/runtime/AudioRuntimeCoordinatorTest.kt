package com.monumentogram.dora.audio.persistence.runtime

import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioCompletion
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.AudioReadSummary
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioSourceState
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.ProductAudioReaderPort
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.audio.VaultKeyProtection
import com.monumentogram.dora.audio.persistence.auth.AppLockSession
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRuntimeCoordinatorTest {
    private val identity =
        AudioIdentity(
            RecordingId("00000000-0000-0000-0000-000000000001"),
            AudioAssetId("00000000-0000-0000-0000-000000000002"),
            "00000000-0000-0000-0000-000000000003",
        )

    private fun authorize(lock: AppLockSession): AppLockSession.Authorization {
        lock.resume()
        val attempt = lock.begin()
        assertTrue(lock.recordResult(attempt))
        assertTrue(lock.complete(attempt) { true })
        return lock.capture()
    }

    @Test
    fun revocationAfterSuccessfulPromptBeforeCaptureCompletesLockedExactlyOnce() {
        val lock = AppLockSession { true }
        authorize(lock) // The real session state has already accepted successful proof.
        lock.lock() // Revocation wins before the subsequent capture, without changing auth policy.
        var opens = 0
        val completions = mutableListOf<AudioAvailability>()
        AudioRuntimeCoordinator({ false }) { _, _ ->
                opens++
                AudioResult.Value(FixtureVault())
            }
            .use { runtime ->
                val call = runCatching {
                    runtime.openAuthenticated(
                        AudioOpenMode.CREATE_NEW,
                        lock::capture,
                        completions::add,
                    )
                }
                assertTrue("Capture revocation must not escape the completion path", call.isSuccess)
                assertEquals(listOf(AudioAvailability.Locked), completions)
                assertEquals(AudioAvailability.Locked, runtime.availability)
                assertEquals(0, opens)
            }
    }

    @Test
    fun successfulPromptHandoffCapturesOnceAndOpensNormally() {
        val lock = AppLockSession { true }
        authorize(lock)
        var captures = 0
        var opens = 0
        val done = CountDownLatch(1)
        AudioRuntimeCoordinator({ false }) { _, _ ->
                opens++
                AudioResult.Value(FixtureVault())
            }
            .use { runtime ->
                runtime.openAuthenticated(
                    AudioOpenMode.OPEN_EXISTING,
                    {
                        captures++
                        lock.capture()
                    },
                ) {
                    done.countDown()
                }
                assertTrue(done.await(5, TimeUnit.SECONDS))
                assertTrue(runtime.availability is AudioAvailability.Available)
                assertEquals(1, captures)
                assertEquals(1, opens)
            }
    }

    @Test
    fun oldHandleNeverRevivesAfterReauthentication() {
        val lock = AppLockSession { true }
        val vault = FixtureVault()
        AudioRuntimeCoordinator({ false }) { _, _ -> AudioResult.Value(vault) }
            .use { runtime ->
                val first = opened(runtime, authorize(lock))
                assertEquals(AudioResult.Value(Unit), first.writer.create(identity))
                lock.lock()
                runtime.revoke()
                assertEquals(AudioAvailability.Locked, runtime.availability)
                assertEquals(AudioResult.Failed(AudioFailure.LOCKED), first.writer.create(identity))
                val second = opened(runtime, authorize(lock))
                assertEquals(AudioResult.Failed(AudioFailure.LOCKED), first.writer.create(identity))
                assertEquals(AudioResult.Value(Unit), second.writer.create(identity))
            }
    }

    @Test
    fun pendingOpenRevokedBeforePublicationClosesAndStaysLocked() {
        val lock = AppLockSession { true }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val complete = CountDownLatch(1)
        val vault = FixtureVault()
        AudioRuntimeCoordinator({ false }) { _, _ ->
                entered.countDown()
                assertTrue(release.await(5, TimeUnit.SECONDS))
                AudioResult.Value(vault)
            }
            .use { runtime ->
                runtime.open(AudioOpenMode.OPEN_EXISTING, authorize(lock)) { complete.countDown() }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                lock.pause()
                runtime.revoke()
                assertEquals(AudioAvailability.Locked, runtime.availability)
                release.countDown()
                assertTrue(complete.await(5, TimeUnit.SECONDS))
                assertEquals(AudioAvailability.Locked, runtime.availability)
                assertTrue(vault.closed)
            }
    }

    @Test
    fun borrowedCallbackCannotReenterWorkerAndMainThreadCannotPerformIo() {
        val lock = AppLockSession { true }
        var main = false
        val vault = FixtureVault()
        AudioRuntimeCoordinator({ main }) { _, _ -> AudioResult.Value(vault) }
            .use { runtime ->
                val session = opened(runtime, authorize(lock))
                session.reader.extract(identity) { _, _ ->
                    assertEquals(
                        AudioResult.Failed(AudioFailure.BUSY),
                        session.writer.create(identity),
                    )
                }
                main = true
                assertEquals(AudioResult.Failed(AudioFailure.BUSY), session.writer.create(identity))
                assertEquals(0, vault.writes)
            }
    }

    @Test
    fun retryCannotCreateConfirmationAndTypedFailureIsPreserved() {
        val lock = AppLockSession { true }
        val vault = FixtureVault()
        AudioRuntimeCoordinator({ false }) { _, _ -> AudioResult.Value(vault) }
            .use { runtime ->
                val session = opened(runtime, authorize(lock))
                assertEquals(
                    AudioResult.Failed(AudioFailure.INVALID_INPUT),
                    session.retryRemainingDeletion(identity),
                )
                assertEquals(0, vault.deletions)
                assertEquals(VaultKeyProtection.SOFTWARE, session.protection)
            }
        AudioRuntimeCoordinator({ false }) { _, _ ->
                AudioResult.Failed(AudioFailure.KEY_INVALIDATED)
            }
            .use { runtime ->
                val done = CountDownLatch(1)
                runtime.open(AudioOpenMode.OPEN_EXISTING, authorize(lock)) { done.countDown() }
                assertTrue(done.await(5, TimeUnit.SECONDS))
                assertEquals(
                    AudioAvailability.Failed(AudioFailure.KEY_INVALIDATED),
                    runtime.availability,
                )
            }
    }

    private fun opened(
        runtime: AudioRuntimeCoordinator,
        authorization: AppLockSession.Authorization,
    ): ProductAudioSession {
        val done = CountDownLatch(1)
        runtime.open(AudioOpenMode.CREATE_NEW, authorization) { done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        return (runtime.availability as AudioAvailability.Available).session
    }

    private class FixtureVault : RuntimeVault {
        var closed = false
        var writes = 0
        var deletions = 0
        override val protection = VaultKeyProtection.SOFTWARE
        override val writer =
            object : ProductAudioWriterPort {
                override fun create(identity: AudioIdentity): AudioResult<Unit> {
                    writes++
                    return AudioResult.Value(Unit)
                }

                override fun append(
                    segment: AudioStorageUnitIdentity,
                    format: AudioFormat,
                    pcm: ByteArray,
                ) = AudioResult.Value(Unit)

                override fun finalize(identity: AudioIdentity) = AudioResult.Value(Unit)

                override fun reconcile(identity: AudioIdentity) = AudioResult.Value(Unit)
            }
        override val reader =
            object : ProductAudioReaderPort {
                override fun extract(
                    identity: AudioIdentity,
                    consume: (Long, ByteArray) -> Unit,
                ): AudioResult<AudioReadSummary> {
                    val bytes = ByteArray(2)
                    try {
                        consume(0, bytes)
                    } finally {
                        bytes.fill(0)
                    }
                    return AudioResult.Value(
                        AudioReadSummary(identity, 1, AudioCompletion.FINALIZED)
                    )
                }
            }

        override fun sourceState(identity: AudioIdentity): AudioResult<AudioSourceState> =
            AudioResult.Value(AudioSourceState.Missing)

        override fun deleteConfirmed(identity: AudioIdentity): AudioResult<Unit> {
            deletions++
            return AudioResult.Value(Unit)
        }

        override fun retryDeletion(identity: AudioIdentity): AudioResult<Unit> =
            AudioResult.Failed(AudioFailure.INVALID_INPUT)

        override fun close() {
            closed = true
        }
    }
}
