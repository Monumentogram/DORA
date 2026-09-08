package com.monumentogram.dora.poc.recovery.candidate

import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryE36GapiDeviceIdentityGuardTest {
    private val accepted =
        RecoveryE36GapiDeviceIdentity(
            api = 36,
            fingerprint =
                "google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys",
            product = "sdk_gphone64_x86_64",
            primaryAbi = "x86_64",
        )

    @Test
    fun `accepts the exact pinned E36 Google APIs identity`() {
        RecoveryE36GapiDeviceIdentityGuard.requireAccepted(accepted)
    }

    @Test
    fun `rejects a different fingerprint`() {
        assertRejected(
            accepted.copy(
                fingerprint =
                    "generic/sdk_gphone64_x86_64/emu64xa:16/test-build:userdebug/test-keys"
            )
        )
    }

    @Test
    fun `rejects a different API`() {
        assertRejected(accepted.copy(api = 35))
    }

    @Test
    fun `rejects a different primary ABI`() {
        assertRejected(accepted.copy(primaryAbi = "arm64-v8a"))
    }

    @Test
    fun `rejects a different product`() {
        assertRejected(accepted.copy(product = "sdk_gphone64_arm64"))
    }

    private fun assertRejected(identity: RecoveryE36GapiDeviceIdentity) {
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryE36GapiDeviceIdentityGuard.requireAccepted(identity)
        }
    }
}
