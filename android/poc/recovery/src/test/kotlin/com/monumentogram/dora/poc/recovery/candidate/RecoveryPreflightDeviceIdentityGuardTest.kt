package com.monumentogram.dora.poc.recovery.candidate

import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryPreflightDeviceIdentityGuardTest {
    private val api33 =
        RecoveryE36GapiDeviceIdentity(
            33,
            "google/sdk_gphone64_x86_64/emu64xa:13/test/123:userdebug/dev-keys",
            "sdk_gphone64_x86_64",
            "x86_64",
        )

    @Test
    fun `accepts only the explicit exact API33 profile`() {
        RecoveryPreflightDeviceIdentityGuard.requireAccepted(api33, "API33-GAPI", api33)
    }

    @Test
    fun `rejects absent profile and unknown profile for API33`() {
        for (profile in listOf(null, "E36-GAPI", "PHYSICAL", "API34-GAPI")) {
            assertThrows(IllegalArgumentException::class.java) {
                RecoveryPreflightDeviceIdentityGuard.requireAccepted(api33, profile, api33)
            }
        }
    }

    @Test
    fun `rejects wrong actual identity fields`() {
        for (wrong in
            listOf(
                api33.copy(api = 36),
                api33.copy(fingerprint = "another"),
                api33.copy(product = "another"),
                api33.copy(primaryAbi = "arm64-v8a"),
            )) {
            assertThrows(IllegalArgumentException::class.java) {
                RecoveryPreflightDeviceIdentityGuard.requireAccepted(wrong, "API33-GAPI", api33)
            }
        }
    }

    @Test
    fun `rejects missing or invalid expected API33 identity`() {
        for (expected in
            listOf(
                null,
                api33.copy(api = 36),
                api33.copy(fingerprint = ""),
                api33.copy(product = ""),
                api33.copy(primaryAbi = "arm64-v8a"),
            )) {
            assertThrows(IllegalArgumentException::class.java) {
                RecoveryPreflightDeviceIdentityGuard.requireAccepted(api33, "API33-GAPI", expected)
            }
        }
    }

    @Test
    fun `default E36 retains every exact identity constraint`() {
        val e36 =
            RecoveryE36GapiDeviceIdentity(
                RecoveryE36GapiDeviceIdentityGuard.EXPECTED_API,
                RecoveryE36GapiDeviceIdentityGuard.EXPECTED_FINGERPRINT,
                RecoveryE36GapiDeviceIdentityGuard.EXPECTED_PRODUCT,
                RecoveryE36GapiDeviceIdentityGuard.EXPECTED_PRIMARY_ABI,
            )
        RecoveryPreflightDeviceIdentityGuard.requireAccepted(e36, null, null)
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryPreflightDeviceIdentityGuard.requireAccepted(e36.copy(api = 33), null, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryPreflightDeviceIdentityGuard.requireAccepted(
                e36.copy(fingerprint = api33.fingerprint),
                null,
                null,
            )
        }
    }
}
