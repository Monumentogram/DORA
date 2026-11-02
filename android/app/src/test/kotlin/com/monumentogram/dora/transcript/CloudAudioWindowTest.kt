package com.monumentogram.dora.transcript

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AuthenticatedAudioSource
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAudioWindowTest {
    private val source =
        OriginalAudioReference(
            1,
            AudioIdentity(
                RecordingId("00000000-0000-4000-8000-000000000001"),
                AudioAssetId("00000000-0000-4000-8000-000000000002"),
                "00000000-0000-4000-8000-000000000003",
            ),
            "a".repeat(64),
            1_382_400_000L,
        )

    private class Ranges(
        override val reference: OriginalAudioReference,
        val corrupt: Boolean = false,
    ) : AuthenticatedAudioSource {
        val requested = mutableListOf<Pair<Long, Long>>()

        override fun extractRange(
            firstFrame: Long,
            frameCount: Long,
            consume: (Long, ByteArray) -> Unit,
        ): AudioResult<OriginalAudioStatus> {
            requested.add(firstFrame to frameCount)
            var first = firstFrame
            while (first < firstFrame + frameCount) {
                val count = minOf(32768L, firstFrame + frameCount - first).toInt()
                consume(first, ByteArray(count * 2) { ((first * 2 + it) % 251).toByte() })
                first += count
            }
            return if (corrupt) AudioResult.Failed(AudioFailure.CORRUPT)
            else AudioResult.Value(OriginalAudioStatus.Available(reference))
        }

        override fun close() = Unit
    }

    @Test
    fun lastThirtyMinuteChildUsesLongGlobalOffsetsAndOnlyRetainsBoundedWavBytes() {
        val port = Ranges(source)
        val first = 1_353_600_000L
        CloudAudio.readWindow(port, first, 28_800_000, 0, 65536) { false }
            .use { window ->
                assertEquals(65536, window.bytes.size)
                assertEquals("RIFF", String(window.bytes, 0, 4, Charsets.US_ASCII))
                assertEquals(
                    57_600_000,
                    ByteBuffer.wrap(window.bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(40),
                )
                assertEquals(((first * 2) % 251).toByte(), window.bytes[44])
            }
        assertEquals(listOf(first to 32746L), port.requested)
        val offset = 57_540_000L
        val tail = CloudAudio.readWindow(port, first, 28_800_000, offset, 60044) { false }
        val bytes = tail.bytes
        assertEquals(((first * 2 + offset - 44) % 251).toByte(), bytes[0])
        tail.close()
        assertTrue(bytes.all { it == 0.toByte() })
    }

    @Test
    fun corruptionCancellationOversizeAndSourceOverrunNeverProduceAnUploadWindow() {
        assertThrows(Exception::class.java) {
            CloudAudio.readWindow(Ranges(source, true), 0, 28_800_000, 0, 65536) { false }
        }
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            CloudAudio.readWindow(Ranges(source), 0, 28_800_000, 0, 65536) { true }
        }
        assertThrows(IllegalArgumentException::class.java) {
            CloudAudio.readWindow(Ranges(source), 0, 28_800_000, 0, 8 * 1024 * 1024 + 1) { false }
        }
        assertThrows(IllegalArgumentException::class.java) {
            CloudAudio.readWindow(Ranges(source), source.frames - 1, 8000, 0, 44) { false }
        }
    }
}
