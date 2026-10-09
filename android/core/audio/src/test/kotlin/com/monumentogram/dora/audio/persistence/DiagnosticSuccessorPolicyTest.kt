package com.monumentogram.dora.audio.persistence

import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticSuccessorPolicyTest {
    private fun id(n: Int) = "00000000-0000-0000-0000-${n.toString().padStart(12, '0')}"

    private fun source(n: Int) =
        AudioIdentity(RecordingId(id(n)), AudioAssetId(id(n + 100)), id(n + 200))

    private val historical = (1..47).map(::source).toSet()
    private val added = source(48)
    private val fresh = source(1000)

    @Test
    fun compiledVariantControlsSuccessorPinAdmission() {
        val bytes = """{"format":"DORA_PROTECTED_SUCCESSOR_V2","synthetic":true}""".toByteArray()
        val pin =
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }
        if (DiagnosticBuild.ENABLED) {
            assertTrue(DiagnosticPolicyBinding.verify(DiagnosticBuild.ENABLED, pin, bytes))
        } else {
            assertThrows(IllegalStateException::class.java) {
                DiagnosticPolicyBinding.verify(DiagnosticBuild.ENABLED, pin, bytes)
            }
        }
    }

    @Test
    fun successorProtectsAllHistoricalSourcesAndAdditionalSource() {
        val policy = DiagnosticSourcePolicy.protected(historical + added, setOf(id(500), id(501)))
        assertEquals(48, policy.protectedSources().size)
        (historical + added).forEach {
            assertTrue(policy.isProtected(it))
            assertThrows(IllegalStateException::class.java) { policy.requireNewSource(it) }
        }
        assertThrows(IllegalStateException::class.java) { policy.requireRun(id(500), fresh) }
        assertThrows(IllegalStateException::class.java) { policy.requireComponent(id(501)) }
        assertThrows(IllegalStateException::class.java) { policy.requireRun(id(2000), added) }
        assertThrows(IllegalStateException::class.java) { policy.requireRun(id(2000), null) }
        policy.requireNewSource(fresh)
        policy.requireRun(id(2000), fresh)
        assertFalse(policy.isProtected(fresh))
    }

    @Test
    fun successorRestartRetainsBindingAndRejectsWrongVault() {
        repeat(2) {
            val policy =
                DiagnosticSourcePolicy.protected(historical + added, setOf(id(500)))
                    .boundTo(id(3000), id(3001))
            policy.requireBinding(id(3000), id(3001))
            assertThrows(IllegalStateException::class.java) {
                policy.requireBinding(id(3000), id(3002))
            }
            assertThrows(IllegalStateException::class.java) { policy.requireNewSource(added) }
        }
    }

    @Test
    fun successorRejectsAdditionalUnapprovedSource() {
        assertThrows(IllegalStateException::class.java) {
            DiagnosticSourcePolicy.protected(historical + added + source(49), setOf(id(500)))
        }
    }
}
