package com.monumentogram.dora.audio.persistence.runtime

import com.monumentogram.dora.audio.AudioIdentity

/** Process-private intent, bound to the exact handle by its captured authority check. */
internal class AudioDeletionConfirmation(
    private val identity: AudioIdentity,
    private val requireActive: () -> Unit,
) {
    private var consumed = false

    @Synchronized
    fun consume(expected: AudioIdentity): Boolean {
        if (consumed || expected != identity) return false
        consumed = true
        return try {
            requireActive()
            true
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun cancel() {
        consumed = true
    }
}
