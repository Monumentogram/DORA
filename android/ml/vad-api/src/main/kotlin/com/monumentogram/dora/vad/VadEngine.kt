package com.monumentogram.dora.vad

/** Half-open canonical source positions, never processing-context ownership. */
data class FrameRange(val first: Long, val end: Long) {
    init {
        require(first >= 0 && end > first)
    }

    val count: Long
        get() = end - first
}

enum class VadFailure {
    RUNTIME_UNAVAILABLE,
    MODEL_UNAVAILABLE,
    MODEL_INVALID,
    INITIALIZATION_FAILED,
    INFERENCE_FAILED,
    BACKPRESSURE,
    INVALID_FRAME,
    STALE_GENERATION,
    COVERAGE_GAP,
    RESET_FAILED,
    METADATA_FAILED,
    PAUSED,
    RESUMED,
    RECOVERY,
}

/** Exceptions contain only a typed code, never model inputs, paths or provider messages. */
class VadException(val failure: VadFailure) : Exception(failure.name)

interface VadEngine : AutoCloseable {
    /** Borrows one normalized window. Implementations may not retain the borrowed array. */
    fun probability(samples: FloatArray): Float

    fun reset()

    override fun close()
}

fun interface VadEngineFactory {
    /** Called exclusively by the observer worker, including all JNI/model initialization. */
    fun create(profile: SegmentationProfile): VadEngine
}

data class VadObservation(val range: FrameRange, val generation: Long, val speech: Boolean)
