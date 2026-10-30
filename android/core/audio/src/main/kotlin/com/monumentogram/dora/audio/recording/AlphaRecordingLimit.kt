package com.monumentogram.dora.audio.recording

/** Canonical audio frames, independent of wall time, pauses, chunks and transport units. */
object AlphaRecordingLimit {
    const val MAX_ALPHA_FRAMES = 28_800_000L

    fun remaining(frames: Long): Long {
        require(frames >= 0)
        return (MAX_ALPHA_FRAMES - frames).coerceAtLeast(0)
    }

    fun accepts(firstFrame: Long, byteCount: Int): Boolean {
        if (firstFrame < 0 || byteCount <= 0 || byteCount % 2 != 0) return false
        return byteCount / 2L <= remaining(firstFrame)
    }
}
