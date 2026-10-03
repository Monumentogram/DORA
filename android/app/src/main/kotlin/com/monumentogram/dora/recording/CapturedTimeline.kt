package com.monumentogram.dora.recording

/**
 * Map the native admission counter to this logical recording; paused wall time is never counted.
 */
internal data class CapturedTimeline(private val origin: Long, private val restoredFrames: Long) {
    fun frames(admitted: Long): Long = restoredFrames + (admitted - origin).coerceAtLeast(0)

    fun admitted(frames: Long): Long = origin + (frames - restoredFrames).coerceAtLeast(0)
}
