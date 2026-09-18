package com.monumentogram.dora.poc.recovery.candidate

internal data class RecoveryPhysicalDeviceIdentity(
    val api: Int,
    val fingerprint: String,
    val product: String,
    val primaryAbi: String,
    val model: String,
    val manufacturer: String,
    val buildType: String,
    val release: String,
    val device: String,
)

internal object RecoveryPhysicalDeviceIdentityGuard {
    const val PROFILE = "POCO-M5-PHYSICAL"
    private const val EXPECTED_API = 34

    fun requireAccepted(
        actual: RecoveryPhysicalDeviceIdentity,
        profile: String?,
        expected: RecoveryPhysicalDeviceIdentity?,
    ) {
        require(profile == PROFILE) { "Explicit physical device profile is required" }
        requireNotNull(expected) { "Complete physical device identity is required" }
        require(expected.api == EXPECTED_API)
        require(expected.primaryAbi == "arm64-v8a")
        require(expected.model == "22071219CG")
        require(expected.manufacturer == "Xiaomi")
        require(expected.buildType == "user")
        require(expected.release == "14")
        require(expected.device == "stone")
        require(expected.product == "stone_p_ru")
        require(expected.fingerprint.isNotBlank())
        require(actual == expected) { "Physical device differs from the admitted identity" }
    }
}
