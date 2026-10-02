package com.monumentogram.dora.audio.persistence.auth

import com.monumentogram.dora.audio.recording.RecordingAuthority
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DevelopmentDeviceSecurityTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun symbolicMarkerCannotGrantTheException() {
        val root = temporary.newFolder("no_backup")
        val target = temporary.newFile("outside-marker")
        try {
            Files.createSymbolicLink(
                File(root, "development-device-no-lock").toPath(),
                target.toPath(),
            )
        } catch (error: java.io.IOException) {
            if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) throw error
            assumeNoException(
                "Host cannot create symlinks; Linux CI executes this negative test",
                error,
            )
        }
        assertFalse(allowed(root))
    }

    @Test
    fun insecureDevelopmentStillFencesStartResumeProcessAndWriterEpoch() {
        val root = temporary.newFolder("no_backup")
        File(root, "development-device-no-lock").writeText("")
        val policy = DeviceSecurityPolicy({ false }, { false }) { allowed(root) }
        val session = AppLockSession(deviceReady = policy::deviceReady)
        session.resume()
        assertTrue(
            DevelopmentAppLockOverride.unlock(session, "com.monumentogram.dora.debug", true) {
                true
            }
        )
        val proof = session.capture()
        var epoch = 1
        val authority =
            RecordingAuthority(
                proof::requireActive,
                { policy.credentialAvailable() && epoch == 1 },
                proof::withPlaintextDelivery,
            )
        var starts = 0
        authority.activate { starts++ }
        session.pause()
        authority.requireWriter() // The already-authorized writer survives screen-off.
        assertThrows(AppLockedException::class.java) {
            authority.resume(proof::withPlaintextDelivery) { starts++ }
        }
        session.resume()
        assertTrue(
            DevelopmentAppLockOverride.unlock(session, "com.monumentogram.dora.debug", true) {
                true
            }
        )
        assertThrows(AppLockedException::class.java) {
            authority.resume(proof::withPlaintextDelivery) { starts++ }
        }
        authority.resume(session.capture()::withPlaintextDelivery) { starts++ }
        org.junit.Assert.assertEquals(2, starts)
        epoch++
        assertThrows(AppLockedException::class.java) { authority.requireWriter() }
        epoch = 1
        assertThrows(AppLockedException::class.java) {
            authority.resume(session.capture()::withPlaintextDelivery) {}
        }
        val newProcess = AppLockSession(deviceReady = policy::deviceReady)
        newProcess.resume()
        assertThrows(AppLockedException::class.java) { newProcess.capture() }
    }

    @Test
    fun exactEmptyPrivateMarkerAdmitsOnlyDebugPackageAndFlag() {
        val root = temporary.newFolder("no_backup")
        val marker = File(root, "development-device-no-lock")
        assertFalse(allowed(root))
        marker.writeText("")
        assertTrue(allowed(root))
        assertFalse(DevelopmentDeviceSecurityOverride.allowed("com.monumentogram.dora", true, root))
        assertFalse(
            DevelopmentDeviceSecurityOverride.allowed("com.monumentogram.dora.debug", false, root)
        )
        marker.writeText("not-an-empty-opt-in")
        assertFalse(allowed(root))
        assertTrue(marker.delete())
        assertTrue(marker.mkdir())
        assertFalse(allowed(root))
    }

    @Test
    fun wrongLocationAndOldPromptMarkerCannotAdmitInsecureDevice() {
        val root = temporary.newFolder("no_backup")
        File(temporary.root, "development-device-no-lock").writeText("")
        File(root, "development-app-lock-no-prompt").writeText("")
        assertFalse(allowed(root))
        val policy = DeviceSecurityPolicy({ false }, { false }) { allowed(root) }
        val session = AppLockSession(deviceReady = policy::deviceReady)
        session.resume()
        assertFalse(
            DevelopmentAppLockOverride.unlock(session, "com.monumentogram.dora.debug", true) {
                true
            }
        )
        assertFalse(session.isUnlocked)
    }

    @Test
    fun markerDoesNotGrantAuthorityAndBothOptInsStillRequireForeground() {
        val root = temporary.newFolder("no_backup")
        File(root, "development-device-no-lock").writeText("")
        val policy = DeviceSecurityPolicy({ false }, { false }) { allowed(root) }
        val session = AppLockSession(deviceReady = policy::deviceReady)
        assertTrue(policy.credentialAvailable())
        assertFalse(session.isUnlocked)
        assertFalse(
            DevelopmentAppLockOverride.unlock(session, "com.monumentogram.dora.debug", true) {
                true
            }
        )
        session.resume()
        assertFalse(
            DevelopmentAppLockOverride.unlock(session, "com.monumentogram.dora.debug", true) {
                false
            }
        )
        assertTrue(
            DevelopmentAppLockOverride.unlock(session, "com.monumentogram.dora.debug", true) {
                true
            }
        )
        val old = session.capture()
        session.pause()
        assertThrows(AppLockedException::class.java) { old.requireActive() }
        session.resume()
        assertFalse(session.isUnlocked)
        assertTrue(
            DevelopmentAppLockOverride.unlock(session, "com.monumentogram.dora.debug", true) {
                true
            }
        )
        assertThrows(AppLockedException::class.java) { old.requireActive() }
        assertFalse(AppLockSession(deviceReady = policy::deviceReady).isUnlocked)
    }

    @Test
    fun removedMarkerRevokesOldAuthorityAndReaddingCannotReviveIt() {
        val root = temporary.newFolder("no_backup")
        val marker = File(root, "development-device-no-lock").apply { writeText("") }
        val policy = DeviceSecurityPolicy({ false }, { false }) { allowed(root) }
        val session = AppLockSession(deviceReady = policy::deviceReady)
        session.resume()
        assertTrue(
            DevelopmentAppLockOverride.unlock(session, "com.monumentogram.dora.debug", true) {
                true
            }
        )
        val old = session.capture()
        assertTrue(marker.delete())
        assertThrows(AppLockedException::class.java) { old.requireActive() }
        marker.writeText("")
        assertThrows(AppLockedException::class.java) { old.requireActive() }
        assertFalse(session.isUnlocked)
    }

    @Test
    fun removedMarkerPermanentlyRevokesAnAlreadyActiveRecording() {
        val root = temporary.newFolder("no_backup")
        val marker = File(root, "development-device-no-lock").apply { writeText("") }
        val policy = DeviceSecurityPolicy({ false }, { false }) { allowed(root) }
        val session = AppLockSession(deviceReady = policy::deviceReady)
        session.resume()
        assertTrue(
            DevelopmentAppLockOverride.unlock(session, "com.monumentogram.dora.debug", true) {
                true
            }
        )
        val proof = session.capture()
        val authority =
            RecordingAuthority(
                proof::requireActive,
                policy::credentialAvailable,
                proof::withPlaintextDelivery,
            )
        authority.activate {}
        assertTrue(marker.delete())
        assertThrows(AppLockedException::class.java) { authority.requireWriter() }
        marker.writeText("")
        assertTrue(
            DevelopmentAppLockOverride.unlock(session, "com.monumentogram.dora.debug", true) {
                true
            }
        )
        assertThrows(AppLockedException::class.java) {
            authority.resume(session.capture()::withPlaintextDelivery) {}
        }
        assertThrows(AppLockedException::class.java) { authority.requireWriter() }
    }

    private fun allowed(root: File) =
        DevelopmentDeviceSecurityOverride.allowed("com.monumentogram.dora.debug", true, root)
}
