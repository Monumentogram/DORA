package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.persistence.auth.AppLockSession
import com.monumentogram.dora.audio.persistence.auth.AppLockedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

class RecordingResumeAuthorizationTest {
    private var deviceReady = true
    private val lock = AppLockSession({ 100L }) { deviceReady }

    private fun unlock() {
        lock.resume()
        val attempt = lock.begin()
        check(lock.recordResult(attempt))
        check(lock.complete(attempt) { true })
    }

    private fun authority(): RecordingAuthority {
        unlock()
        val original = lock.capture()
        return RecordingAuthority(
                original::requireActive,
                { true },
                original::withPlaintextDelivery,
            )
            .also { it.activate {} }
    }

    @Test
    fun sameForegroundAuthorityResumesWithoutBeginningAnotherAuthentication() {
        val authority = authority()
        val foreground = lock.capture()
        var starts = 0
        repeat(20) { authority.resume(foreground::withPlaintextDelivery) { starts++ } }
        assertEquals(20, starts)
        foreground.requireActive()
    }

    @Test
    fun revokedForegroundGrantStaysInvalidAfterLaterSuccessfulUnlock() {
        val authority = authority()
        val stale = lock.capture()
        lock.pause()
        unlock()
        assertThrows(AppLockedException::class.java) {
            authority.resume(stale::withPlaintextDelivery) { fail("Stale grant started") }
        }
        authority.resume(lock.capture()::withPlaintextDelivery) {}
    }

    @Test
    fun deviceLockBetweenPauseAndResumeRequiresFreshProof() {
        val authority = authority()
        val before = lock.capture()
        deviceReady = false
        assertThrows(AppLockedException::class.java) {
            authority.resume(before::withPlaintextDelivery) { fail("Locked device started") }
        }
        deviceReady = true
        assertThrows(AppLockedException::class.java) { lock.capture() }
        unlock()
        authority.resume(lock.capture()::withPlaintextDelivery) {}
    }

    @Test
    fun activityRecreationDoesNotKeepForegroundAuthorization() {
        authority()
        lock.pause()
        lock.resume()
        assertThrows(AppLockedException::class.java) { lock.capture() }
    }

    @Test
    fun authorityRevokedInsideResumeBoundaryRejectsNativeStart() {
        val authority = authority()
        val foreground = lock.capture()
        assertThrows(AppLockedException::class.java) {
            authority.resume({ block ->
                authority.revoke()
                foreground.withPlaintextDelivery(block)
            }) {
                fail("Revoked writer started")
            }
        }
    }
}
