package com.monumentogram.dora.audio.persistence.auth

/** Owner-authorized local development exception. Never included in the release variant. */
internal object DevelopmentAppLockOverride {
    fun unlock(
        session: AppLockSession,
        packageName: String,
        debuggable: Boolean,
        markerPresent: () -> Boolean,
    ): Boolean {
        if (!debuggable || packageName != "com.monumentogram.dora.debug" || !markerPresent())
            return false
        val attempt = session.begin()
        session.recordResult(attempt)
        return session.complete(attempt) { true }
    }
}
