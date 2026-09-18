package com.monumentogram.dora.poc.recovery.candidate

import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryPhysicalDeviceIdentityGuardTest {
    // Synthetic unit-test identity; never an admission for a real phone.
    private val physical =
        RecoveryPhysicalDeviceIdentity(
            api = 34,
            fingerprint = "synthetic/poco/fixture:14/test/1:user/release-keys",
            product = "stone_p_ru",
            primaryAbi = "arm64-v8a",
            model = "22071219CG",
            manufacturer = "Xiaomi",
            buildType = "user",
            release = "14",
            device = "stone",
        )

    @Test
    fun `accepts explicit physical profile only with exact identity`() {
        RecoveryPhysicalDeviceIdentityGuard.requireAccepted(
            physical,
            "POCO-M5-PHYSICAL",
            physical,
        )
    }

    @Test
    fun `rejects missing and unrelated profiles`() {
        for (profile in listOf(null, "PHYSICAL", "API33-GAPI", "E36-GAPI", "D2")) {
            assertThrows(IllegalArgumentException::class.java) {
                RecoveryPhysicalDeviceIdentityGuard.requireAccepted(physical, profile, physical)
            }
        }
    }

    @Test
    fun `rejects drift in every actual identity field`() {
        for (actual in
            listOf(
                physical.copy(api = 35),
                physical.copy(fingerprint = "another"),
                physical.copy(product = "another"),
                physical.copy(primaryAbi = "x86_64"),
                physical.copy(model = "another"),
                physical.copy(manufacturer = "another"),
                physical.copy(buildType = "userdebug"),
                physical.copy(release = "15"),
                physical.copy(device = "another"),
            )) {
            assertThrows(IllegalArgumentException::class.java) {
                RecoveryPhysicalDeviceIdentityGuard.requireAccepted(
                    actual,
                    "POCO-M5-PHYSICAL",
                    physical,
                )
            }
        }
    }

    @Test
    fun `rejects incomplete expectations even when actual equals them`() {
        for (expected in
            listOf(
                physical.copy(api = 32),
                physical.copy(fingerprint = ""),
                physical.copy(product = ""),
                physical.copy(primaryAbi = ""),
                physical.copy(model = ""),
                physical.copy(manufacturer = ""),
                physical.copy(buildType = ""),
                physical.copy(release = ""),
                physical.copy(device = ""),
            )) {
            assertThrows(IllegalArgumentException::class.java) {
                RecoveryPhysicalDeviceIdentityGuard.requireAccepted(
                    expected,
                    "POCO-M5-PHYSICAL",
                    expected,
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryPhysicalDeviceIdentityGuard.requireAccepted(physical, "POCO-M5-PHYSICAL", null)
        }
    }

    @Test
    fun `rejects another device even when actual matches expected`() {
        for (other in
            listOf(
                physical.copy(product = "stone_p_global"),
                physical.copy(model = "another"),
                physical.copy(manufacturer = "another"),
                physical.copy(buildType = "userdebug"),
                physical.copy(release = "15"),
                physical.copy(device = "another"),
                physical.copy(primaryAbi = "x86_64"),
            )) {
            assertThrows(IllegalArgumentException::class.java) {
                RecoveryPhysicalDeviceIdentityGuard.requireAccepted(
                    other,
                    "POCO-M5-PHYSICAL",
                    other,
                )
            }
        }
    }
}
