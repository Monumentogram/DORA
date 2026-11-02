package com.monumentogram.dora.audio.recording

/** Canonical audio frames, independent of wall time, pauses, chunks and transport units. */
object AlphaRecordingLimit {
    const val MAX_ALPHA_FRAMES =
        1_382_400_000L // 86,400 seconds at 16 kHz; PCM bytes exceed Int.MAX_VALUE.

    fun remaining(frames: Long): Long {
        require(frames >= 0)
        return (MAX_ALPHA_FRAMES - frames).coerceAtLeast(0)
    }

    fun accepts(firstFrame: Long, byteCount: Int): Boolean {
        if (firstFrame < 0 || byteCount <= 0 || byteCount % 2 != 0) return false
        return byteCount / 2L <= remaining(firstFrame)
    }
}
