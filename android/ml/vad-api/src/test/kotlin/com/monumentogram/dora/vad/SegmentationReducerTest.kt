package com.monumentogram.dora.vad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentationReducerTest {
    @Test
    fun windowPartitionDoesNotMoveSourceBoundaries() {
        val out = mutableListOf<SemanticEvent>()
        val r = SegmentationReducer(SegmentationProfile.FROZEN, 0, 1, out::add)
        var f = 0L
        repeat(10) {
            r.observe(VadObservation(FrameRange(f, f + 512), 1, true))
            f += 512
        }
        val silenceStart = f
        repeat(2813) {
            r.observe(VadObservation(FrameRange(f, f + 512), 1, false))
            f += 512
        }
        assertEquals(1, out.filterIsInstance<SemanticEvent.Closed>().size)
        assertEquals(
            silenceStart + 1_440_000,
            out.filterIsInstance<SemanticEvent.Closed>().single().range.end,
        )
        assertEquals(4800L, out.filterIsInstance<SemanticEvent.Opened>().single().range.end)
    }

    @Test
    fun throwingMetadataSinkCannotDuplicateBoundary() {
        var calls = 0
        val r =
            SegmentationReducer(SegmentationProfile.FROZEN, 0, 1) {
                if (it is SemanticEvent.Closed) {
                    calls++
                    error("Injected synthetic failure")
                }
            }
        r.observe(VadObservation(FrameRange(0, 4800), 1, true))
        assertThrows(IllegalStateException::class.java) {
            r.observe(VadObservation(FrameRange(4800, 1_444_800), 1, false))
        }
        r.observe(VadObservation(FrameRange(1_444_800, 2_884_800), 1, false))
        assertEquals(1, calls)
        assertEquals(SemanticPhase.NO_SPEECH, r.phase)
    }

    private val profile = SegmentationProfile.FROZEN
    private val events = mutableListOf<SemanticEvent>()
    private var frame = 0L
    private var generation = 1L
    private val reducer = SegmentationReducer(profile, 0, generation, events::add)

    private fun feed(speech: Boolean, frames: Long) {
        reducer.observe(VadObservation(FrameRange(frame, frame + frames), generation, speech))
        frame += frames
    }

    private fun speech() = feed(true, profile.onsetFrames)

    private fun closes() = events.filterIsInstance<SemanticEvent.Closed>()

    @Test
    fun silenceAt895DoesNotClose() {
        speech()
        feed(false, 1_432_000)
        assertTrue(closes().isEmpty())
    }

    @Test
    fun silenceAt900ClosesExactly() {
        speech()
        feed(false, 1_440_000)
        assertEquals(1, closes().size)
        assertEquals(frame, closes().single().range.end)
    }

    @Test
    fun silenceAt905ClosesOnceInsideObservation() {
        speech()
        val start = frame
        feed(false, 1_448_000)
        assertEquals(start + 1_440_000, closes().single().range.end)
        feed(false, 1_440_000)
        assertEquals(1, closes().size)
    }

    @Test
    fun positiveAt899CancelsAndNewSilenceStartsFresh() {
        speech()
        feed(false, 1_438_400)
        feed(true, 1)
        feed(false, 1_439_999)
        assertTrue(closes().isEmpty())
        feed(false, 1)
        assertEquals(1, closes().size)
    }

    @Test
    fun hysteresisDoesNotDelayNinetySeconds() {
        speech()
        feed(false, profile.hysteresisFrames - 1)
        assertEquals(SemanticPhase.SHORT_PAUSE, reducer.phase)
        feed(false, 1)
        assertEquals(SemanticPhase.SILENCE_TIMER, reducer.phase)
        feed(false, 1)
        assertTrue(closes().isEmpty())
        feed(false, 1_440_000 - profile.hysteresisFrames - 1)
        assertEquals(1, closes().size)
    }

    @Test
    fun repeatedBreathingPausesDoNotFragment() {
        speech()
        repeat(100) {
            feed(false, profile.hysteresisFrames + 1)
            feed(true, 512)
        }
        assertTrue(closes().isEmpty())
    }

    @Test
    fun noSpeechNeverOpensEmptySegments() {
        feed(false, 8L * 3600 * 16000)
        assertTrue(events.isEmpty())
        assertEquals(SemanticPhase.NO_SPEECH, reducer.phase)
    }

    @Test
    fun onsetRequiresAllFrozenFrames() {
        feed(true, profile.onsetFrames - 1)
        assertTrue(events.isEmpty())
        feed(true, 1)
        assertEquals(1, events.filterIsInstance<SemanticEvent.Opened>().size)
    }

    @Test
    fun transientOnsetIsCancelledBySilence() {
        feed(true, profile.onsetFrames - 1)
        feed(false, 1)
        feed(true, profile.onsetFrames - 1)
        assertTrue(events.isEmpty())
    }

    @Test
    fun preRollUsesCanonicalSourceWithoutDuplication() {
        feed(false, 64_000)
        speech()
        assertEquals(
            FrameRange(32_000, 64_000 + profile.onsetFrames),
            events.filterIsInstance<SemanticEvent.Opened>().single().range,
        )
    }

    @Test
    fun preRollClampsAtRecordingStart() {
        speech()
        assertEquals(0L, events.filterIsInstance<SemanticEvent.Opened>().single().range.first)
    }

    @Test
    fun pauseCancelsSilenceWithoutClosingContent() {
        speech()
        feed(false, 1_438_400)
        reducer.discontinuity(frame, ++generation, VadFailure.PAUSED)
        /* arbitrary wall time has no input */ feed(false, 1600)
        assertTrue(closes().isEmpty())
        feed(false, 1_438_400)
        assertEquals(1, closes().size)
    }

    @Test
    fun resumePreRollCannotCrossPause() {
        feed(false, 100_000)
        reducer.discontinuity(frame, ++generation, VadFailure.PAUSED)
        speech()
        assertEquals(100_000L, events.filterIsInstance<SemanticEvent.Opened>().single().range.first)
    }

    @Test
    fun gapDoesNotBecomeSilence() {
        speech()
        feed(false, 1_438_400)
        frame += 16_000
        reducer.discontinuity(frame, ++generation, VadFailure.BACKPRESSURE)
        feed(false, 1600)
        assertTrue(closes().isEmpty())
    }

    @Test
    fun unannouncedMissingFramesInvalidateContinuity() {
        speech()
        feed(false, 1_438_400)
        frame += 100
        feed(false, 1600)
        assertTrue(closes().isEmpty())
        assertTrue(events.any { it is SemanticEvent.Degraded })
    }

    @Test
    fun staleObservationCannotCloseSegment() {
        speech()
        feed(false, 1_438_400)
        reducer.observe(VadObservation(FrameRange(frame, frame + 1600), 0, false))
        feed(false, 1600)
        assertTrue(closes().isEmpty())
    }

    @Test
    fun stopDuringSilenceHasStopReason() {
        speech()
        feed(false, 1_432_000)
        reducer.stop(frame)
        assertEquals(BoundaryReason.STOP, closes().single().reason)
        reducer.stop(frame)
        assertEquals(1, closes().size)
    }

    @Test
    fun stopWithoutSpeechHasNoSegment() {
        feed(false, 1_440_000)
        reducer.stop(frame)
        assertTrue(closes().isEmpty())
    }

    @Test
    fun lateCallbackAfterStopCannotOpenOrClose() {
        reducer.stop(0)
        speech()
        assertTrue(events.isEmpty())
    }

    @Test
    fun repeatedSegmentsHaveDistinctOrderedIds() {
        repeat(3) {
            speech()
            feed(false, 1_440_000)
        }
        assertEquals(listOf(0L, 1L, 2L), closes().map { it.ordinal })
    }

    @Test
    fun wallClockAndTimezoneAreNotInputs() {
        speech()
        feed(false, 1_439_999)
        assertTrue(closes().isEmpty())
        feed(false, 1)
        assertEquals(1, closes().size)
    }

    @Test
    fun newRecordingHasNoPriorState() {
        speech()
        val fresh = SegmentationReducer(profile, 0, 1) {}
        assertEquals(SemanticPhase.NO_SPEECH, fresh.phase)
    }

    @Test
    fun continuousSpeechSpansTwentyThreeMinutes() {
        feed(true, 23L * 60 * 16000)
        assertTrue(closes().isEmpty())
        reducer.stop(frame)
        assertEquals(frame, closes().single().range.end)
    }

    @Test
    fun virtualEndurance() {
        for (hours in listOf(1, 3, 8)) {
            val out = mutableListOf<SemanticEvent>()
            val r = SegmentationReducer(profile, 0, 1, out::add)
            var f = 0L
            repeat(hours * 30) {
                r.observe(VadObservation(FrameRange(f, f + 480_000), 1, true))
                f += 480_000
                r.observe(VadObservation(FrameRange(f, f + 1_440_000), 1, false))
                f += 1_440_000
            }
            assertEquals(hours * 30, out.filterIsInstance<SemanticEvent.Closed>().size)
            assertEquals(hours * 3600L * 16000, f)
        }
    }
}
