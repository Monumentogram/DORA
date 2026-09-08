package com.monumentogram.dora.poc.recovery.candidate

internal data class RecoveryE36GapiDeviceIdentity(
    val api: Int,
    val fingerprint: String,
    val product: String,
    val primaryAbi: String,
)

internal object RecoveryE36GapiDeviceIdentityGuard {
    const val EXPECTED_API = 36
    const val EXPECTED_FINGERPRINT =
        "google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys"
    const val EXPECTED_PRODUCT = "sdk_gphone64_x86_64"
    const val EXPECTED_PRIMARY_ABI = "x86_64"

    fun requireAccepted(identity: RecoveryE36GapiDeviceIdentity) {
        require(identity.api == EXPECTED_API) { "E36-GAPI API 36 is required" }
        require(identity.fingerprint == EXPECTED_FINGERPRINT) {
            "Exact E36-GAPI emulator fingerprint is required"
        }
        require(identity.product == EXPECTED_PRODUCT) {
            "Exact E36-GAPI product is required"
        }
        require(identity.primaryAbi == EXPECTED_PRIMARY_ABI) {
            "E36-GAPI x86_64 primary ABI is required"
        }
    }
}
