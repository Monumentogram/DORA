package com.monumentogram.dora.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RecordingLatencyTest {
    @Test
    fun staleCallbacksCannotContaminateNextOperation() {
        var clock = 100L
        val timing = RecordingLatency(true) { clock }
        val first = timing.begin("pause")
        clock = 130L
        timing.mark(first, "fence")
        clock = 200L
        val second = timing.begin("resume")
        timing.mark(first, "stale")
        clock = 230L
        timing.mark(second, "native_recording")
        assertEquals(30L, timing.samples().first().events["fence"])
        assertFalse(timing.samples().last().events.containsKey("stale"))
        assertEquals(30L, timing.samples().last().events["native_recording"])
    }

    @Test
    fun drawRetainsExactVsyncForExternalDisplayPresentationCorrelation() {
        var clock = 100L
        val timing = RecordingLatency(true) { clock }
        val operation = timing.begin("pause")
        clock = 120L
        timing.draw(operation, "ack", 110L)
        timing.draw(operation, "ack", 999L)
        assertFalse(timing.samples().single().events.containsKey("ack_frame"))
        assertEquals(110L, timing.samples().single().events["ack_vsync"])
        assertEquals(20L, timing.samples().single().events["ack_draw"])
    }

    @Test
    fun disabledMeasurementCollectsNothingAndRetentionIsBounded() {
        val disabled = RecordingLatency(false) { 100L }
        disabled.mark(disabled.begin("pause"), "fence")
        assertEquals(0, disabled.samples().size)
        val bounded = RecordingLatency(true) { 100L }
        repeat(100) { bounded.begin("pause") }
        assertEquals(64, bounded.samples().size)
    }
}
