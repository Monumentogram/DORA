package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.persistence.auth.AppLockedException

/** Separate from foreground read authority. Never persisted or revived after revocation. */
internal class RecordingAuthority(
    private val foreground: () -> Unit,
    private val deviceSecure: () -> Boolean,
    private val foregroundBoundary: (() -> Unit) -> Unit,
) {
    private var active = false
    private var revoked = false
    private var onRevoked: (() -> Unit)? = null

    @Synchronized
    fun requireForeground() {
        if (revoked) throw AppLockedException()
        foreground()
    }

    @Synchronized
    fun activate(start: () -> Unit) {
        foregroundBoundary {
            requireForeground()
            if (active || !deviceSecure()) throw AppLockedException()
            start()
            active = true
        }
    }

    @Synchronized
    fun resume(boundary: (() -> Unit) -> Unit, start: () -> Unit) {
        requireWriter()
        if (!active) throw AppLockedException()
        boundary {
            requireWriter()
            start()
        }
    }

    @Synchronized
    fun requireWriter() {
        if (!deviceSecure()) revoked = true
        if (revoked) throw AppLockedException()
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
