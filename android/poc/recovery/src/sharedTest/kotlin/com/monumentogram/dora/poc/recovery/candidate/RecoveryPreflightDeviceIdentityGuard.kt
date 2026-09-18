package com.monumentogram.dora.poc.recovery.candidate

/** API33 expectations come from the separately hash-bound host admission packet. */
internal object RecoveryPreflightDeviceIdentityGuard {
    private const val API33 = 33

    fun requireAccepted(
        actual: RecoveryE36GapiDeviceIdentity,
        profile: String?,
        expected: RecoveryE36GapiDeviceIdentity?,
    ) {
        if (profile == null) {
            RecoveryE36GapiDeviceIdentityGuard.requireAccepted(actual)
            return
        }
        require(profile == "API33-GAPI") { "Unknown preflight device profile" }
        requireNotNull(expected) { "Exact API33 identity is required" }
        require(expected.api == API33 && expected.primaryAbi == "x86_64")
        require(expected.fingerprint.isNotBlank() && expected.product.isNotBlank())
        require(actual == expected) { "API33 device differs from the admitted identity" }
    }
}
