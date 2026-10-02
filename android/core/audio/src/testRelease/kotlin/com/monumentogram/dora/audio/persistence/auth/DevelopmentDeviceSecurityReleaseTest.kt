package com.monumentogram.dora.audio.persistence.auth

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DevelopmentDeviceSecurityReleaseTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun releaseNeverAdmitsInsecureDeviceEvenWithDebugInputsAndBothMarkers() {
        val root = temporary.newFolder("no_backup")
        File(root, "development-device-no-lock").writeText("")
        File(root, "development-app-lock-no-prompt").writeText("")
        for (name in listOf("com.monumentogram.dora", "com.monumentogram.dora.debug")) {
            for (flag in listOf(false, true)) {
                assertFalse(DevelopmentDeviceSecurityOverride.allowed(name, flag, root))
                val policy =
                    DeviceSecurityPolicy({ false }, { false }) {
                        DevelopmentDeviceSecurityOverride.allowed(name, flag, root)
                    }
                val session = AppLockSession(deviceReady = policy::deviceReady)
                session.resume()
                assertFalse(policy.deviceReady())
                assertFalse(DevelopmentAppLockOverride.unlock(session, name, flag) { true })
                assertFalse(session.isUnlocked)
            }
        }
    }
}
