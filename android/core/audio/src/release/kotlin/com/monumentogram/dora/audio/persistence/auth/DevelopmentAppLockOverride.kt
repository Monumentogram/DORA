package com.monumentogram.dora.audio.persistence.auth

/** Release has no implementation capable of granting the development exception. */
internal object DevelopmentAppLockOverride {
    @Suppress("UNUSED_PARAMETER", "UnusedParameter", "FunctionOnlyReturningConstant")
    fun unlock(
        session: AppLockSession,
        packageName: String,
        debuggable: Boolean,
        markerPresent: () -> Boolean,
    ): Boolean = false
}
