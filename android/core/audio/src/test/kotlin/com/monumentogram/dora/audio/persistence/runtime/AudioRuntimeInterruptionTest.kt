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
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRuntimeInterruptionTest {
    private val identity =
        AudioIdentity(
            RecordingId("00000000-0000-0000-0000-000000000001"),
            AudioAssetId("00000000-0000-0000-0000-000000000002"),
            "00000000-0000-0000-0000-000000000003",
        )

    @Test fun preInterruptedQueuedReadNeverCallsConsumerAfterReturn() = queuedAttempt(false)

    @Test fun preInterruptedQueuedAppendNeverReadsReusedCallerBuffer() = queuedAttempt(true)

    private fun queuedAttempt(append: Boolean) {
        val vault = FixtureVault()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        vault.createAction = {
            entered.countDown()
            await(release)
        }
        AudioRuntimeCoordinator({ false }) { _, _ -> AudioResult.Value(vault) }
            .use { runtime ->
                val session = open(runtime)
                val blocker = Thread { session.writer.create(identity) }.apply { start() }
                await(entered)
                val returned = CountDownLatch(1)
                val consumerClosed = AtomicBoolean(false)
                val lateConsumer = AtomicBoolean(false)
                val interruptRestored = AtomicBoolean(false)
                val input = byteArrayOf(7, 9)
                val observed = mutableListOf<Byte>()
                vault.appendAction = { observed += it[0] }
                var result: AudioResult<*>? = null
                val caller =
                    Thread {
                            Thread.currentThread().interrupt()
                            result =
                                if (append) session.writer.append(unit(), AudioFormat.PCM, input)
                                else
                                    session.reader.extract(identity) { _, _ ->
                                        lateConsumer.set(consumerClosed.get())
                                    }
                            consumerClosed.set(true)
                            input.fill(0)
                            interruptRestored.set(Thread.currentThread().isInterrupted)
                            returned.countDown()
                        }
                        .apply { start() }
                try {
                    await(returned)
                } finally {
                    release.countDown()
                }
                caller.join(5_000)
                blocker.join(5_000)
                session.sourceState(
                    identity
                ) // Serial drain proves a formerly queued closure cannot run later.
                assertEquals(AudioResult.Failed(AudioFailure.UNCERTAIN), result)
                assertTrue(interruptRestored.get())
                assertFalse(lateConsumer.get())
                assertTrue(
                    "Pre-interrupted append must never reach caller bytes",
                    observed.isEmpty(),
                )
            }
    }

    @Test fun interruptedActiveReadWaitsForCallbackAndWipeBeforeReturn() = activeAttempt(false)

    @Test fun interruptedActiveAppendWaitsUntilInputAccessEndsBeforeReturn() = activeAttempt(true)

    private fun activeAttempt(append: Boolean) {
        val vault = FixtureVault()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val finished = AtomicBoolean(false)
        val finishedAtReturn = AtomicBoolean(false)
        val interruptRestored = AtomicBoolean(false)
        val input = byteArrayOf(7, 9)
        var observed: Byte? = null
        var borrowed: ByteArray? = null
        var result: AudioResult<*>? = null
        vault.appendAction = { bytes ->
            entered.countDown()
            await(release)
            observed = bytes[0]
            finished.set(true)
        }
        AudioRuntimeCoordinator({ false }) { _, _ -> AudioResult.Value(vault) }
            .use { runtime ->
                val session = open(runtime)
                val caller =
                    Thread {
                            result =
                                if (append) session.writer.append(unit(), AudioFormat.PCM, input)
                                else
                                    session.reader.extract(identity) { _, bytes ->
                                        borrowed = bytes
                                        entered.countDown()
                                        await(release)
                                        finished.set(true)
                                    }
                            finishedAtReturn.set(finished.get())
                            input.fill(0)
                            interruptRestored.set(Thread.currentThread().isInterrupted)
                            returned.countDown()
                        }
                        .apply { start() }
                await(
                    entered
                ) // The worker already owns the input/callback and the caller is awaiting it.
                caller.interrupt()
                try {
                    assertFalse(
                        "Synchronous call returned while worker still owns caller data",
                        returned.await(1, TimeUnit.SECONDS),
                    )
                } finally {
                    release.countDown()
                }
                await(returned)
                caller.join(5_000)
                assertTrue(finishedAtReturn.get())
                assertTrue(interruptRestored.get())
                assertEquals(AudioResult.Failed(AudioFailure.UNCERTAIN), result)
                if (append) assertEquals(7.toByte(), observed)
                else assertTrue(checkNotNull(borrowed).all { it == 0.toByte() })
            }
    }

    private fun open(runtime: AudioRuntimeCoordinator): ProductAudioSession {
        val lock = AppLockSession { true }
        lock.resume()
        val attempt = lock.begin()
        assertTrue(lock.recordResult(attempt))
        assertTrue(lock.complete(attempt) { true })
        val done = CountDownLatch(1)
        runtime.open(AudioOpenMode.OPEN_EXISTING, lock.capture()) { done.countDown() }
        await(done)
        return (runtime.availability as AudioAvailability.Available).session
    }

    private fun unit() = AudioStorageUnitIdentity(identity, identity.sessionId, 0, 0)

    private fun await(latch: CountDownLatch) = assertTrue(latch.await(5, TimeUnit.SECONDS))

    private class FixtureVault : RuntimeVault {
        var createAction: () -> Unit = {}
        var appendAction: (ByteArray) -> Unit = {}
        override val protection = VaultKeyProtection.SOFTWARE
        override val writer =
            object : ProductAudioWriterPort {
                override fun create(identity: AudioIdentity): AudioResult<Unit> {
                    createAction()
                    return AudioResult.Value(Unit)
                }

                override fun append(
                    segment: AudioStorageUnitIdentity,
                    format: AudioFormat,
                    pcm: ByteArray,
                ): AudioResult<Unit> {
                    appendAction(pcm)
                    return AudioResult.Value(Unit)
                }

                override fun finalize(identity: AudioIdentity) = AudioResult.Value(Unit)

                override fun reconcile(identity: AudioIdentity) = AudioResult.Value(Unit)
            }
        override val reader =
            object : ProductAudioReaderPort {
                override fun extract(
                    identity: AudioIdentity,
                    consume: (Long, ByteArray) -> Unit,
                ): AudioResult<AudioReadSummary> {
                    val bytes = byteArrayOf(1, 2)
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

        override fun sourceState(identity: AudioIdentity) =
            AudioResult.Value(AudioSourceState.Missing)

        override fun deleteConfirmed(identity: AudioIdentity) = AudioResult.Value(Unit)

        override fun retryDeletion(identity: AudioIdentity) =
            AudioResult.Failed(AudioFailure.INVALID_INPUT)

        override fun close() = Unit
    }
}
