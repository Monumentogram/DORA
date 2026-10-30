package com.monumentogram.dora.audio.transcript

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioPort
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProtectedPcmTest {
    private val source =
        OriginalAudioReference(
            1,
            AudioIdentity(
                RecordingId("00000000-0000-4000-8000-000000000001"),
                AudioAssetId("00000000-0000-4000-8000-000000000002"),
                "00000000-0000-4000-8000-000000000003",
            ),
            "a".repeat(64),
            2,
        )

    @Test
    fun windowSelectsExactSamplesAndStillRejectsCorruptTail() {
        val input = byteArrayOf(0, -128, -1, 127)
        val pcm = ProtectedPcm.readWindow(port(input), source, 1, 1) { false }
        assertArrayEquals(floatArrayOf(32767f / 32768f), pcm.samples, 0f)
        pcm.close()
        assertArrayEquals(floatArrayOf(0f), pcm.samples, 0f)
        assertThrows(IllegalStateException::class.java) {
            ProtectedPcm.readWindow(port(byteArrayOf(1, 0, 2, 0), fail = true), source, 0, 1) {
                false
            }
        }
    }

    @Test
    fun canonicalLittleEndianAndOwnedBufferWiped() {
        val input = byteArrayOf(0, -128, -1, 127)
        val pcm = ProtectedPcm.read(port(input), source) { false }
        assertEquals(-1f, pcm.samples[0], 0f)
        assertEquals(32767f / 32768f, pcm.samples[1], 0f)
        pcm.close()
        assertArrayEquals(floatArrayOf(0f, 0f), pcm.samples, 0f)
    }

    @Test
    fun failedTailNeverReturnsEarlierAuthenticatedSamples() {
        assertThrows(IllegalStateException::class.java) {
            ProtectedPcm.read(port(byteArrayOf(1, 0, 2, 0), fail = true), source) { false }
        }
    }

    @Test
    fun gapsCancellationAndOversizeFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            ProtectedPcm.read(port(byteArrayOf(1, 0), first = 1), source) { false }
        }
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            ProtectedPcm.read(port(byteArrayOf(1, 0)), source) { true }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProtectedPcm.read(port(byteArrayOf(1, 0)), source.copy(frames = 4_800_001)) { false }
        }
    }

    private fun port(bytes: ByteArray, first: Long = 0, fail: Boolean = false) =
        object : OriginalAudioPort {
            override fun acquire(identity: AudioIdentity) =
                AudioResult.Value(OriginalAudioStatus.Available(source))

            override fun inspect(reference: OriginalAudioReference) = acquire(reference.identity)

            override fun withAvailable(reference: OriginalAudioReference, action: () -> Unit) =
                inspect(reference)

            override fun extract(
                reference: OriginalAudioReference,
                consume: (Long, ByteArray) -> Unit,
            ): AudioResult<OriginalAudioStatus> {
                consume(first, bytes)
                bytes.fill(0)
                return if (fail) AudioResult.Failed(AudioFailure.CORRUPT) else inspect(reference)
            }
        }
}
