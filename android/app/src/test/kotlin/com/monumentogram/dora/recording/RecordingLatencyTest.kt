package com.monumentogram.dora.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RecordingLatencyTest {
    @Test
    fun latePauseCannotReattachDurabilityAfterRecordingRetires() {
        val timing = RecordingLatency(true) { 100L }
        timing.recordingStarted("old")
        val old = timing.begin("pause")
        timing.retireDurability()
        timing.awaitDurability(old, 16000, "old")
        timing.recordingStarted("new")
        timing.awaitDurability(old, 16000, "old")
        val current = timing.begin("pause")
        timing.awaitDurability(current, 16000, "new")
        timing.durableThrough(16000)
        assertFalse(timing.samples().first().events.containsKey("durability_caught_up"))
        org.junit.Assert.assertTrue(
            timing.samples().last().events.containsKey("durability_caught_up")
        )
    }

    @Test
    fun failedRecordingCannotCatchUpFromLaterRecording() {
        val timing = RecordingLatency(true) { 100L }
        timing.recordingStarted("old")
        val old = timing.begin("pause")
        timing.awaitDurability(old, 16000, "old")
        timing.retireDurability()
        timing.begin("resume")
        timing.durableThrough(16000)
        assertFalse(timing.samples().first().events.containsKey("durability_caught_up"))
        assertEquals(2, timing.samples().size)
    }

    @Test
    fun retainsLaterDrawCandidatesWhenFirstFrameIsNotPresented() {
        var clock = 100L
        val timing = RecordingLatency(true) { clock }
        val operation = timing.begin("pause")
        clock = 120L
        timing.draw(operation, "confirmed", 110L)
        clock = 140L
        timing.draw(operation, "confirmed", 130L)
        org.junit.Assert.assertTrue(
            timing.dump().contains("draw_candidate sequence=1 label=confirmed draw=140 vsync=130")
        )
    }

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
