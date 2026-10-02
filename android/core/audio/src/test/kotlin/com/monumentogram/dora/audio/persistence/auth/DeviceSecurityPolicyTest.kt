package com.monumentogram.dora.audio.persistence.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSecurityPolicyTest {
    @Test
    fun ordinaryPolicyRequiresSecureUnlockedDevice() {
        var secure = false
        var locked = false
        val policy = DeviceSecurityPolicy({ secure }, { locked }) { false }
        assertFalse(policy.credentialAvailable())
        assertFalse(policy.deviceReady())
        secure = true
        assertTrue(policy.deviceReady())
        locked = true
        assertFalse(policy.deviceReady())
        assertTrue(
            policy.credentialAvailable()
        ) // Existing authorized writer may finish in background.
    }

    @Test
    fun developmentReadinessDoesNotBypassPhysicalLockedState() {
        var locked = true
        var optedIn = true
        val policy = DeviceSecurityPolicy({ false }, { locked }) { optedIn }
        assertTrue(policy.credentialAvailable())
        assertFalse(policy.deviceReady())
        locked = false
        assertTrue(policy.deviceReady())
        optedIn = false
        assertFalse(policy.deviceReady())
        assertFalse(policy.credentialAvailable())
    }

    @Test
    fun unreadableDevelopmentDecisionFailsClosed() {
        val policy = DeviceSecurityPolicy({ false }, { false }) { error("unreadable marker") }
        assertFalse(policy.credentialAvailable())
        assertFalse(policy.deviceReady())
    }
}
