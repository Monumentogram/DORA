package com.monumentogram.dora.audio.persistence

import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticPolicyBindingTest {
    private val bytes = "synthetic-private-policy".toByteArray()
    private val pin =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun ordinaryBuildWithoutArtifactsIsUnrestricted() {
        assertFalse(DiagnosticPolicyBinding.verify(false, null, null))
        assertFalse(DiagnosticPolicyBinding.verify(true, null, null))
    }

    @Test
    fun debugRequiresExactContent() {
        assertTrue(DiagnosticPolicyBinding.verify(true, pin, bytes))
        assertThrows(IllegalStateException::class.java) {
            DiagnosticPolicyBinding.verify(true, pin, bytes + 0)
        }
    }

    @Test
    fun missingPolicyCannotDisablePinnedBuild() {
        assertThrows(IllegalStateException::class.java) {
            DiagnosticPolicyBinding.verify(true, pin, null)
        }
    }

    @Test
    fun missingPinCannotDisableRetainedPolicy() {
        assertThrows(IllegalStateException::class.java) {
            DiagnosticPolicyBinding.verify(true, null, bytes)
        }
    }

    @Test
    fun releaseAlwaysRejectsActivation() {
        assertThrows(IllegalStateException::class.java) {
            DiagnosticPolicyBinding.verify(false, pin, bytes)
        }
        assertThrows(IllegalStateException::class.java) {
            DiagnosticPolicyBinding.verify(false, null, bytes)
        }
    }

    @Test
    fun malformedPinRejected() {
        assertThrows(IllegalStateException::class.java) {
            DiagnosticPolicyBinding.verify(true, "invalid", bytes)
        }
    }

    @Test
    fun subsequentOpenRevalidatesPolicy() {
        assertTrue(DiagnosticPolicyBinding.verify(true, pin, bytes))
        assertThrows(IllegalStateException::class.java) {
            DiagnosticPolicyBinding.verify(true, pin, null)
        }
    }
}
