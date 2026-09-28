package com.monumentogram.dora.stage0.ownedcorpus

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WavTest {
    @Test
    fun exactMonoPcm16HeaderAndSampleDuration() {
        val bytes = Wav.header(320_000)
        val view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(44, bytes.size)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(640_036, view.getInt(4))
        assertEquals(1, view.getShort(22).toInt())
        assertEquals(16_000, view.getInt(24))
        assertEquals(16, view.getShort(34).toInt())
        assertEquals(640_000, view.getInt(40))
        assertEquals(20_000_000L, Wav.durationUs(320_000))
        assertFalse(CapturePolicy.canStop(319_999))
        assertTrue(CapturePolicy.canStop(324_000))
        assertEquals(704_000, CapturePolicy.maximumFrames("READ"))
        assertEquals(944_000, CapturePolicy.maximumFrames("SPONTANEOUS"))
    }

    @Test
    fun invalidWavAndOversizedHeadersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { Wav.header(-1) }
        assertThrows(IllegalArgumentException::class.java) { Wav.inspect(ByteArray(44)) }
        val truncated = Wav.header(160)
        assertThrows(IllegalArgumentException::class.java) { Wav.inspect(truncated) }
        val exact = Wav.header(2) + byteArrayOf(0, 0, 1, 0)
        assertEquals(125L, Wav.inspect(exact))
    }
}
