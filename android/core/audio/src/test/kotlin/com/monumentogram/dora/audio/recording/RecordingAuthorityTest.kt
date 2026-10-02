package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.persistence.auth.AppLockedException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingAuthorityTest {
    @Test
    fun revokedPausedRecordingCannotResumeUnderFreshForegroundProof() {
        val authority = RecordingAuthority({}, { true }) { it() }
        authority.activate {}
        var notified = false
        authority.onRevocation { notified = true }
        authority.revoke()
        var microphoneStarted = false
        assertThrows(AppLockedException::class.java) {
            authority.resume({ it() }) { microphoneStarted = true }
        }
        assertTrue(notified)
        assertFalse(microphoneStarted)
    }

    @Test
    fun startIsInsideTheForegroundAuthorizationBoundary() {
        var inside = false
        val authority =
            RecordingAuthority({}, { true }) { block ->
                inside = true
                try {
                    block()
                } finally {
                    inside = false
                }
            }
        authority.activate { assertTrue(inside) }
        assertFalse(inside)
    }

    @Test
    fun preparedCapabilityCannotActivateAfterForegroundAuthorizationRevokes() {
        var foreground = true
        val authority =
            RecordingAuthority({ if (!foreground) throw AppLockedException() }, { true }) { it() }
        foreground = false
        assertThrows(AppLockedException::class.java) { authority.activate {} }
    }

    @Test
    fun activeWriterSurvivesUiLockButNotExplicitRevocation() {
        var foreground = true
        val authority =
            RecordingAuthority({ if (!foreground) throw AppLockedException() }, { true }) { it() }
        authority.activate {}
        foreground = false
        authority.requireWriter()
        assertThrows(AppLockedException::class.java) { authority.requireForeground() }
        authority.revoke()
        assertThrows(AppLockedException::class.java) { authority.requireWriter() }
        foreground = true
        assertThrows(AppLockedException::class.java) { authority.activate {} }
    }

    @Test
    fun removingDeviceCredentialRevokesEvenAnActiveWriter() {
        var secure = true
        val authority = RecordingAuthority({}, { secure }) { it() }
        authority.activate {}
        secure = false
        assertThrows(AppLockedException::class.java) { authority.requireWriter() }
        secure = true
        assertThrows(AppLockedException::class.java) { authority.requireWriter() }
    }
}
