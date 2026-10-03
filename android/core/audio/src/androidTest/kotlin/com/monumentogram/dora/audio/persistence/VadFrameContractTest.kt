package com.monumentogram.dora.audio.persistence

import com.monumentogram.dora.vad.BoundaryReason
import com.monumentogram.dora.vad.FrameRange
import com.monumentogram.dora.vad.SegmentationProfile
import com.monumentogram.dora.vad.SegmentationReducer
import com.monumentogram.dora.vad.SemanticEvent
import com.monumentogram.dora.vad.SemanticPhase
import com.monumentogram.dora.vad.VadFailure
import com.monumentogram.dora.vad.VadObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Executes the production reducer on both Android API matrices. No native VAD evidence. */
class VadFrameContractTest {
    private val profile = SegmentationProfile.FROZEN

    @Test
    fun exactSilenceMatrix() {
        for ((frames, expected) in listOf(1_432_000L to 0, 1_440_000L to 1, 1_448_000L to 1)) {
            val events = mutableListOf<SemanticEvent>()
            val r = SegmentationReducer(profile, 0, 1, events::add)
            r.observe(VadObservation(FrameRange(0, 4800), 1, true))
            r.observe(VadObservation(FrameRange(4800, 4800 + frames), 1, false))
            val closed = events.filterIsInstance<SemanticEvent.Closed>()
            assertEquals(expected, closed.size)
            if (expected == 1) assertEquals(1_444_800L, closed.single().range.end)
        }
    }

    @Test
    fun speechAt899CancelsAndSubsequentSilenceStartsFresh() {
        val events = mutableListOf<SemanticEvent>()
        val r = SegmentationReducer(profile, 0, 1, events::add)
        r.observe(VadObservation(FrameRange(0, 4800), 1, true))
        r.observe(VadObservation(FrameRange(4800, 1_443_200), 1, false))
        r.observe(VadObservation(FrameRange(1_443_200, 1_443_201), 1, true))
        r.observe(VadObservation(FrameRange(1_443_201, 2_883_200), 1, false))
        assertTrue(events.none { it is SemanticEvent.Closed })
        r.observe(VadObservation(FrameRange(2_883_200, 2_883_201), 1, false))
        assertEquals(1, events.count { it is SemanticEvent.Closed })
    }

    @Test
    fun sourceFramesIgnorePauseWallClockAndDoNotCarryPrerollAcrossGap() {
        val events = mutableListOf<SemanticEvent>()
        val r = SegmentationReducer(profile, 0, 1, events::add)
        r.observe(VadObservation(FrameRange(0, 100000), 1, false))
        r.discontinuity(100000, 2, VadFailure.PAUSED)
        // A manual pause of any wall duration supplies zero canonical frames.
        r.discontinuity(100000, 3, VadFailure.RESUMED)
        r.observe(VadObservation(FrameRange(100000, 104799), 3, true))
        assertTrue(events.isEmpty())
        r.observe(VadObservation(FrameRange(104799, 104800), 3, true))
        assertEquals(100000L, events.filterIsInstance<SemanticEvent.Opened>().single().range.first)
        r.stop(104800)
        assertEquals(
            BoundaryReason.STOP,
            events.filterIsInstance<SemanticEvent.Closed>().single().reason,
        )
    }

    @Test
    fun silenceWithoutSpeechNeverCreatesSegments() {
        val events = mutableListOf<SemanticEvent>()
        val r = SegmentationReducer(profile, 0, 1, events::add)
        r.observe(VadObservation(FrameRange(0, 8L * 3600 * 16000), 1, false))
        r.stop(8L * 3600 * 16000)
        assertTrue(events.isEmpty())
    }

    @Test
    fun frozenHysteresisDoesNotAddTimeToSemanticBoundary() {
        val events = mutableListOf<SemanticEvent>()
        val r = SegmentationReducer(profile, 0, 1, events::add)
        r.observe(VadObservation(FrameRange(0, 4800), 1, true))
        r.observe(VadObservation(FrameRange(4800, 20799), 1, false))
        assertEquals(SemanticPhase.SHORT_PAUSE, r.phase)
        r.observe(VadObservation(FrameRange(20799, 20800), 1, false))
        assertEquals(SemanticPhase.SILENCE_TIMER, r.phase)
        r.observe(VadObservation(FrameRange(20800, 1_444_800), 1, false))
        assertEquals(1_444_800L, events.filterIsInstance<SemanticEvent.Closed>().single().range.end)
    }

    @Test
    fun uncertaintyAndStaleGenerationCannotCompleteSilence() {
        val events = mutableListOf<SemanticEvent>()
        val r = SegmentationReducer(profile, 0, 1, events::add)
        r.observe(VadObservation(FrameRange(0, 4800), 1, true))
        r.observe(VadObservation(FrameRange(4800, 1_443_200), 1, false))
        r.discontinuity(1_444_800, 2, VadFailure.BACKPRESSURE)
        r.observe(VadObservation(FrameRange(1_443_200, 1_444_800), 1, false))
        r.observe(VadObservation(FrameRange(1_444_800, 1_446_400), 2, false))
        assertTrue(events.none { it is SemanticEvent.Closed })
        r.stop(1_446_400)
        assertTrue(events.filterIsInstance<SemanticEvent.Closed>().single().degraded)
    }
}
