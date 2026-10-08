package com.monumentogram.dora.audio.persistence

import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class DiagnosticReleaseFenceTest {
    @Test
    fun actualReleaseVariantCannotActivateEvenMatchingPolicy() {
        assertFalse(DiagnosticBuild.ENABLED)
        val bytes = "synthetic-private-policy".toByteArray()
        val pin =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }
        assertThrows(IllegalStateException::class.java) {
            DiagnosticPolicyBinding.verify(DiagnosticBuild.ENABLED, pin, bytes)
        }
    }
}
