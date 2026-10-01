package com.monumentogram.dora.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductAudioContractTest {
    @Test
    fun `canonical frames preserve exact time across physical boundaries`() {
        assertEquals(1_000_000L, AudioTimeline.durationUs(16_000))
        assertEquals(62L, AudioTimeline.durationUs(1))
        assertEquals(0L, AudioTimeline.durationUs(0))
        assertEquals(32_000L, AudioTimeline.nextFrame(16_000, 16_000))
    }

    @Test
    fun `format mutations fail closed`() {
        listOf(
                AudioFormat("PCM_FLOAT", 16_000, 1),
                AudioFormat("PCM_S16BE", 16_000, 1),
                AudioFormat("PCM_S16LE", 48_000, 1),
                AudioFormat("PCM_S16LE", 16_000, 2),
            )
            .forEach { format ->
                assertThrows(IllegalArgumentException::class.java) { format.requireSupported() }
            }
        AudioFormat.PCM.requireSupported()
    }

    @Test
    fun `frame and overflow mutations reject before persistence`() {
        assertThrows(IllegalArgumentException::class.java) { AudioTimeline.frames(3) }
        assertThrows(IllegalArgumentException::class.java) { AudioTimeline.frames(0) }
        assertThrows(IllegalArgumentException::class.java) { AudioTimeline.durationUs(-1) }
        assertThrows(ArithmeticException::class.java) { AudioTimeline.nextFrame(Long.MAX_VALUE, 1) }
        assertEquals(2L, AudioTimeline.frames(4))
    }

    @Test
    fun `production composition has no plaintext or fake persistence fallback`() {
        assertTrue(
            ProductAudioRuntime.availability is AudioAvailability.RequiresEncryptedPersistence
        )
    }
}
