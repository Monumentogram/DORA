package com.monumentogram.dora.audio.persistence.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevelopmentAppLockOverrideTest {
    @Test
    fun explicitLocalOptInUnlocksOnlyForegroundDebugApp() {
        val session = AppLockSession { true }
        session.resume()
        assertTrue(
            DevelopmentAppLockOverride.unlock(session, "com.monumentogram.dora.debug", true) {
                true
            }
        )
        assertTrue(session.isUnlocked)
        session.pause()
        assertFalse(session.isUnlocked)
        assertFalse(
            DevelopmentAppLockOverride.unlock(session, "com.monumentogram.dora.debug", true) {
                true
            }
        )
    }

    @Test
    fun missingOptInOrWrongPackageOrNonDebuggableNeverGrants() {
        val session = AppLockSession { true }
        session.resume()
        assertFalse(
            DevelopmentAppLockOverride.unlock(session, "com.monumentogram.dora.debug", true) {
                false
            }
        )
        assertFalse(
            DevelopmentAppLockOverride.unlock(session, "com.monumentogram.dora", true) { true }
        )
        assertFalse(
            DevelopmentAppLockOverride.unlock(session, "com.monumentogram.dora.debug", false) {
                true
            }
        )
        assertFalse(session.isUnlocked)
    }

    @Test
    fun lockedDeviceStillDeniesAndNewRequestCannotReviveOldHandle() {
        var ready = true
        val session = AppLockSession { ready }
        session.resume()
        assertTrue(
            DevelopmentAppLockOverride.unlock(session, "com.monumentogram.dora.debug", true) {
                true
            }
        )
        val old = session.capture()
        session.lock()
        ready = false
        assertFalse(
            DevelopmentAppLockOverride.unlock(session, "com.monumentogram.dora.debug", true) {
                true
            }
        )
        ready = true
        assertTrue(
            DevelopmentAppLockOverride.unlock(session, "com.monumentogram.dora.debug", true) {
                true
            }
        )
        org.junit.Assert.assertThrows(AppLockedException::class.java) { old.requireActive() }
    }
}
