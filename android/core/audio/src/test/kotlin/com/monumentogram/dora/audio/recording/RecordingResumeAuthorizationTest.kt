package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.persistence.auth.LocalAccessDeniedException
import com.monumentogram.dora.audio.persistence.auth.LocalAccessSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

class RecordingResumeAuthorizationTest {
    private var deviceReady = true
    private val lock = LocalAccessSession { deviceReady }

    private fun unlock() {
        lock.resume()
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
    fun sameForegroundAuthorityResumesWithoutBeginningCredentialAuthentication() {
        val authority = authority()
        val foreground = lock.capture()
        var starts = 0
        repeat(20) { authority.resume(foreground::withPlaintextDelivery) { starts++ } }
        assertEquals(20, starts)
        foreground.requireActive()
    }

    @Test
    fun revokedForegroundGrantStaysInvalidAfterLaterForegroundReturn() {
        val authority = authority()
        val stale = lock.capture()
        lock.pause()
        unlock()
        assertThrows(LocalAccessDeniedException::class.java) {
            authority.resume(stale::withPlaintextDelivery) { fail("Stale grant started") }
        }
        authority.resume(lock.capture()::withPlaintextDelivery) {}
    }

    @Test
    fun deviceLockBetweenPauseAndResumeRequiresNewForegroundAction() {
        val authority = authority()
        val before = lock.capture()
        deviceReady = false
        assertThrows(LocalAccessDeniedException::class.java) {
            authority.resume(before::withPlaintextDelivery) { fail("Locked device started") }
        }
        deviceReady = true
        assertThrows(LocalAccessDeniedException::class.java) { before.requireActive() }
        authority.resume(lock.capture()::withPlaintextDelivery) {}
    }

    @Test
    fun activityRecreationDoesNotKeepForegroundAuthorization() {
        authority()
        lock.pause()
        lock.resume()
        lock.capture().requireActive()
    }

    @Test
    fun authorityRevokedInsideResumeBoundaryRejectsNativeStart() {
        val authority = authority()
        val foreground = lock.capture()
        assertThrows(LocalAccessDeniedException::class.java) {
            authority.resume({ block ->
                authority.revoke()
                foreground.withPlaintextDelivery(block)
            }) {
                fail("Revoked writer started")
            }
        }
    }
}
