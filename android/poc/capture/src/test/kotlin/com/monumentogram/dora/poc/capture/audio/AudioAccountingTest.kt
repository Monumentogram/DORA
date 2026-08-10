package com.monumentogram.dora.poc.capture.audio

import android.media.AudioRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioAccountingTest {
    @Test
    fun `PCM16 sample counters use two bytes per sample`() {
        assertEquals(0, pcm16SampleCount(-1))
        assertEquals(0, pcm16SampleCount(0))
        assertEquals(4_000, pcm16SampleCount(8_000))
    }

    @Test
    fun `AudioRecord errors are classified instead of hidden`() {
        assertEquals(
            "AUDIORECORD_DEAD_OBJECT",
            classifyAudioReadError(AudioRecord.ERROR_DEAD_OBJECT),
        )
        assertEquals(
            "AUDIORECORD_INVALID_OPERATION",
            classifyAudioReadError(AudioRecord.ERROR_INVALID_OPERATION),
        )
    }
}
