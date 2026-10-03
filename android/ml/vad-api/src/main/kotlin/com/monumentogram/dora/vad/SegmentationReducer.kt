package com.monumentogram.dora.vad

enum class SemanticPhase {
    NO_SPEECH,
    SPEECH,
    SHORT_PAUSE,
    SILENCE_TIMER,
    STOPPED,
}

enum class BoundaryReason {
    SILENCE_90_SECONDS,
    STOP,
}

sealed interface SemanticEvent {
    data class Opened(val ordinal: Long, val range: FrameRange) : SemanticEvent

    data class Closed(
        val ordinal: Long,
        val range: FrameRange,
        val reason: BoundaryReason,
        val degraded: Boolean,
    ) : SemanticEvent

    data class Degraded(
        val atFrame: Long,
        val failure: VadFailure,
        val firstFrame: Long = atFrame,
    ) : SemanticEvent
}

/** Single-owner reducer. Audio source frames are its only clock; it retains no PCM. */
class SegmentationReducer(
    private val profile: SegmentationProfile,
    firstFrame: Long,
    private var generation: Long,
    private val emit: (SemanticEvent) -> Unit,
) {
    init {
        require(firstFrame >= 0)
    }

    private var expected = firstFrame
    private var continuityStart = firstFrame
    private var candidate: Long? = null
    private var activeStart: Long? = null
    private var silenceStart: Long? = null
    private var ordinal = 0L
    private var degraded = false
    var phase = SemanticPhase.NO_SPEECH
        private set

    @Suppress("ReturnCount") // Invalid/stale observations fail closed before semantic transitions.
    fun observe(observation: VadObservation) {
        if (phase == SemanticPhase.STOPPED) return
        if (observation.generation != generation) {
            invalidate(expected, VadFailure.STALE_GENERATION)
            return
        }
        if (observation.range.first != expected) {
            invalidate(maxOf(expected, observation.range.end), VadFailure.COVERAGE_GAP)
            return
        }
        expected = observation.range.end
        if (observation.speech) positive(observation.range) else negative(observation.range)
    }

    fun discontinuity(atFrame: Long, nextGeneration: Long, reason: VadFailure) {
        if (phase == SemanticPhase.STOPPED) return
        require(atFrame >= expected && nextGeneration >= generation)
        generation = nextGeneration
        if (reason == VadFailure.PAUSED || reason == VadFailure.RESUMED) {
            if (atFrame > expected) invalidate(atFrame, VadFailure.COVERAGE_GAP)
            else resetContinuity(atFrame)
        } else invalidate(atFrame, reason)
    }

    fun stop(atFrame: Long) {
        if (phase == SemanticPhase.STOPPED) return
        require(atFrame >= expected)
        if (atFrame > expected) invalidate(atFrame, VadFailure.COVERAGE_GAP)
        try {
            close(atFrame, BoundaryReason.STOP)
        } finally {
            phase = SemanticPhase.STOPPED
        }
    }

    private fun positive(range: FrameRange) {
        silenceStart = null // Every positive frame cancels silence, not only established onset.
        if (activeStart == null) {
            val onset = candidate ?: range.first.also { candidate = it }
            if (range.end - onset >= profile.onsetFrames) {
                val start = maxOf(continuityStart, onset - profile.preRollFrames)
                activeStart = start
                emit(SemanticEvent.Opened(ordinal, FrameRange(start, onset + profile.onsetFrames)))
            }
        }
        phase = if (activeStart == null) SemanticPhase.NO_SPEECH else SemanticPhase.SPEECH
    }

    private fun negative(range: FrameRange) {
        candidate = null
        if (activeStart == null) return
        val start = silenceStart ?: range.first.also { silenceStart = it }
        val duration = range.end - start
        if (duration >= profile.semanticSilenceFrames) {
            close(start + profile.semanticSilenceFrames, BoundaryReason.SILENCE_90_SECONDS)
            phase = SemanticPhase.NO_SPEECH
        } else {
            phase =
                if (duration < profile.hysteresisFrames) SemanticPhase.SHORT_PAUSE
                else SemanticPhase.SILENCE_TIMER
        }
    }

    private fun close(end: Long, reason: BoundaryReason) {
        val event = activeStart?.let {
            SemanticEvent.Closed(ordinal++, FrameRange(it, end), reason, degraded)
        }
        activeStart = null
        candidate = null
        silenceStart = null
        degraded = false
        phase = SemanticPhase.NO_SPEECH
        event?.let(emit)
    }

    private fun invalidate(atFrame: Long, reason: VadFailure) {
        val first = expected
        degraded = activeStart != null
        resetContinuity(atFrame)
        emit(SemanticEvent.Degraded(atFrame, reason, first))
    }

    private fun resetContinuity(atFrame: Long) {
        expected = atFrame
        continuityStart = atFrame
        candidate = null
        silenceStart = null
        phase = if (activeStart == null) SemanticPhase.NO_SPEECH else SemanticPhase.SPEECH
    }
}
