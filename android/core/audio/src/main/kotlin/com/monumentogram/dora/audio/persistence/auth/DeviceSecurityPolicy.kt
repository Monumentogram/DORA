package com.monumentogram.dora.audio.persistence.auth

/** Device preconditions only. Resource authority still belongs to process-local AppLockSession. */
internal class DeviceSecurityPolicy(
    private val systemSecure: () -> Boolean,
    private val systemLocked: () -> Boolean,
    private val developmentNoLock: () -> Boolean,
) {
    fun credentialAvailable(): Boolean =
        try {
            systemSecure() || developmentNoLock()
        } catch (_: Exception) {
            false
        }

    fun deviceReady(): Boolean =
        try {
            credentialAvailable() && !systemLocked()
        } catch (_: Exception) {
            false
        }
}
