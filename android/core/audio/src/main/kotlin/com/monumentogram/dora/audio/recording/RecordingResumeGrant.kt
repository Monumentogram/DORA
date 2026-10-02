package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.persistence.auth.AppLockedException

/** One explicit resume, with current foreground authority checked at the microphone boundary. */
class RecordingResumeGrant internal constructor(private val startBoundary: (() -> Unit) -> Unit) {
    private var consumed = false

    @Synchronized
    fun consume(start: () -> Unit) {
        if (consumed) throw AppLockedException()
        consumed = true
        startBoundary(start)
    }
}
