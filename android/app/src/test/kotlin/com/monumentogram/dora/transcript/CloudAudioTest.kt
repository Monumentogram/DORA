package com.monumentogram.dora.transcript

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioPort
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAudioTest {
    private val identity =
        AudioIdentity(
            RecordingId("00000000-0000-0000-0000-000000000001"),
            AudioAssetId("00000000-0000-0000-0000-000000000002"),
            "00000000-0000-0000-0000-000000000003",
        )
    private val source = OriginalAudioReference(1, identity, "a".repeat(64), 8000)

    private fun port(corrupt: Boolean = false) =
        object : OriginalAudioPort {
            override fun acquire(identity: AudioIdentity) = inspect(source)

            override fun inspect(reference: OriginalAudioReference) =
                AudioResult.Value(OriginalAudioStatus.Available(reference))

            override fun withAvailable(
                reference: OriginalAudioReference,
                action: () -> Unit,
            ): AudioResult<OriginalAudioStatus> {
                action()
                return inspect(reference)
            }

            override fun extract(
                reference: OriginalAudioReference,
                consume: (Long, ByteArray) -> Unit,
            ): AudioResult<OriginalAudioStatus> {
                consume(
                    0,
                    ByteArray(16000).apply {
                        this[0] = 127
                        this[1] = -128
                    },
                )
                return if (corrupt) AudioResult.Failed(AudioFailure.CORRUPT) else inspect(reference)
            }
        }

    @Test
    fun wavUsesExactSavedPcmAndWipesOwnedBuffer() {
        val wav = CloudAudio.read(port(), source) { false }
        val bytes = wav.bytes
        assertEquals(16044, bytes.size)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(127, bytes[44].toInt())
        assertEquals(-128, bytes[45].toInt())
        assertEquals(
            16000,
            java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(24),
        )
        wav.close()
        assertTrue(bytes.all { it == 0.toByte() })
    }

    @Test
    fun corruptOrCancelledExtractionNeverProducesUploadInput() {
        assertThrows(Exception::class.java) { CloudAudio.read(port(true), source) { false } }
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            CloudAudio.read(port(), source) { true }
        }
    }

    @Test
    fun endpointRejectsCredentialsPathsAndCleartext() {
        listOf(
                "http://example.com",
                "https://user:pass@example.com",
                "https://example.com/path",
                "https://example.com?query",
                "https://example.com#fragment",
            )
            .forEach {
                assertThrows(IllegalArgumentException::class.java) { CloudEndpoint.validate(it) }
            }
        assertEquals(
            "https://example.com:8443",
            CloudEndpoint.validate("https://example.com:8443/"),
        )
    }
}
