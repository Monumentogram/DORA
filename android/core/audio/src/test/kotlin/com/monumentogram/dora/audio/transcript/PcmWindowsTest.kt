package com.monumentogram.dora.audio.transcript

import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioPort
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmWindowsTest {
    private val source =
        OriginalAudioReference(
            1,
            AudioIdentity(
                RecordingId("00000000-0000-4000-8000-000000000001"),
                AudioAssetId("00000000-0000-4000-8000-000000000002"),
                "test",
            ),
            "a".repeat(64),
            9_600_002,
        )

    @Test
    fun nextWindowLoadsWhileCurrentIsOwnedAndPreservesExactBoundary() {
        val secondStarted = CountDownLatch(1)
        var calls = 0
        var openings = 0
        val port =
            object : OriginalAudioPort {
                override fun openAuthenticated(
                    reference: OriginalAudioReference
                ): AudioResult<com.monumentogram.dora.audio.AuthenticatedAudioSource> {
                    openings++
                    return AudioResult.Value(rangeSource(this))
                }

                override fun acquire(identity: AudioIdentity) =
                    AudioResult.Value(OriginalAudioStatus.Available(source))

                override fun inspect(reference: OriginalAudioReference) =
                    acquire(reference.identity)

                override fun withAvailable(reference: OriginalAudioReference, action: () -> Unit) =
                    inspect(reference)

                override fun extract(
                    reference: OriginalAudioReference,
                    consume: (Long, ByteArray) -> Unit,
                ): AudioResult<OriginalAudioStatus> {
                    if (++calls == 2) secondStarted.countDown()
                    var first = 0L
                    while (first < source.frames) {
                        val count = minOf(80_000L, source.frames - first).toInt()
                        consume(
                            first,
                            ByteArray(count * 2) {
                                if (it % 2 == 0) (first / 4_800_000 + 1).toByte() else 0
                            },
                        )
                        first += count
                    }
                    return inspect(reference)
                }
            }
        PcmWindows(port, source, 4_799_999) { false }
            .use { windows ->
                windows.next()!!.use { first ->
                    assertEquals(4_800_000, first.samples.size)
                    assertEquals(1f / 32768f, first.samples.first(), 0f)
                    assertEquals(2f / 32768f, first.samples.last(), 0f)
                    assertTrue(secondStarted.await(10, TimeUnit.SECONDS))
                }
                val second = windows.next()!!
                assertArrayEquals(
                    floatArrayOf(2f / 32768f, 3f / 32768f, 3f / 32768f),
                    second.samples,
                    0f,
                )
                second.close()
                assertArrayEquals(floatArrayOf(0f, 0f, 0f), second.samples, 0f)
                assertNull(windows.next())
            }
        assertEquals(1, openings)
    }

    @Test
    fun interruptedCloseWaitsForInFlightExtractionAndRestoresInterrupt() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closing = CountDownLatch(1)
        val restored = AtomicBoolean(false)
        val port =
            object : OriginalAudioPort {
                override fun openAuthenticated(
                    reference: OriginalAudioReference
                ): AudioResult<com.monumentogram.dora.audio.AuthenticatedAudioSource> =
                    AudioResult.Value(rangeSource(this))

                override fun acquire(identity: AudioIdentity) =
                    AudioResult.Value(OriginalAudioStatus.Available(source))

                override fun inspect(reference: OriginalAudioReference) =
                    acquire(reference.identity)

                override fun withAvailable(reference: OriginalAudioReference, action: () -> Unit) =
                    inspect(reference)

                override fun extract(
                    reference: OriginalAudioReference,
                    consume: (Long, ByteArray) -> Unit,
                ): AudioResult<OriginalAudioStatus> {
                    started.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    consume(0, byteArrayOf(1, 0))
                    return inspect(reference)
                }
            }
        val windows = PcmWindows(port, source, 0) { false }
        assertTrue(started.await(10, TimeUnit.SECONDS))
        val closer = Thread {
            Thread.currentThread().interrupt()
            closing.countDown()
            windows.close()
            restored.set(Thread.currentThread().isInterrupted)
        }
        try {
            closer.start()
            assertTrue(closing.await(10, TimeUnit.SECONDS))
            closer.join(100)
            assertTrue(closer.isAlive)
            release.countDown()
            closer.join(10_000)
            assertTrue(!closer.isAlive)
            assertTrue(restored.get())
        } finally {
            release.countDown()
            closer.join(10_000)
            windows.close()
        }
    }

    private fun rangeSource(port: OriginalAudioPort) =
        object : com.monumentogram.dora.audio.AuthenticatedAudioSource {
            override val reference = source

            override fun close() = Unit

            override fun extractRange(
                firstFrame: Long,
                frameCount: Long,
                consume: (Long, ByteArray) -> Unit,
            ) =
                port.extract(source) { first, bytes ->
                    val begin = maxOf(first, firstFrame)
                    val end = minOf(first + bytes.size / 2, firstFrame + frameCount)
                    if (end > begin) {
                        val selected =
                            bytes.copyOfRange(
                                ((begin - first) * 2).toInt(),
                                ((end - first) * 2).toInt(),
                            )
                        try {
                            consume(begin, selected)
                        } finally {
                            selected.fill(0)
                        }
                    }
                }
        }
}
