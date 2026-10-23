package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.persistence.auth.LocalAccessDeniedException

/** Separate from foreground read authority. Never persisted or revived after revocation. */
internal class RecordingAuthority(
    private val foreground: () -> Unit,
    private val ownerCurrent: () -> Boolean,
    private val foregroundBoundary: (() -> Unit) -> Unit,
) {
    private var active = false
    private var revoked = false
    private var onRevoked: (() -> Unit)? = null

    @Synchronized
    fun requireForeground() {
        if (revoked) throw LocalAccessDeniedException()
        foreground()
    }

    @Synchronized
    fun activate(start: () -> Unit) {
        foregroundBoundary {
            requireForeground()
            if (active || !ownerCurrent()) throw LocalAccessDeniedException()
            start()
            active = true
        }
    }

    @Synchronized
    fun resume(boundary: (() -> Unit) -> Unit, start: () -> Unit) {
        requireWriter()
        if (!active) throw LocalAccessDeniedException()
        boundary {
            requireWriter()
            start()
        }
    }

    @Synchronized
    fun requireWriter() {
        if (!ownerCurrent()) revoked = true
        if (revoked) throw LocalAccessDeniedException()
        if (!active) foreground()
    }

    fun onRevocation(listener: () -> Unit) {
        val notify =
            synchronized(this) {
                onRevoked = listener
                revoked
            }
        if (notify) listener()
    }

    fun revoke() {
        val listener =
            synchronized(this) {
                revoked = true
                onRevoked
            }
        listener?.invoke()
    }
}
