package com.monumentogram.dora.poc.recovery.candidate

internal object RecoveryExternalSigkillProbeGate {
    private val NONCE = Regex("[0-9a-f]{32}")

    fun normalizeSelinuxContext(raw: String): String = raw.trim {
        it == '\u0000' || it.isWhitespace()
    }

    fun requireAccepted(optIn: String?, profile: String?, nonce: String?): String {
        require(optIn == "true") { "Explicit physical SIGKILL probe opt-in is required" }
        require(profile == RecoveryPhysicalDeviceIdentityGuard.PROFILE) {
            "Explicit physical device profile is required"
        }
        require(nonce != null && NONCE.matches(nonce)) { "Strict hex32 probe nonce is required" }
        return nonce
    }
}
