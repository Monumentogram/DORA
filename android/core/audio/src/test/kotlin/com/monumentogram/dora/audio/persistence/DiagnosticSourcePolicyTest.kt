package com.monumentogram.dora.audio.persistence

import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticSourcePolicyTest {
    private fun id(n: Int) = "00000000-0000-0000-0000-${n.toString().padStart(12, '0')}"

    private fun source(n: Int) =
        AudioIdentity(RecordingId(id(n)), AudioAssetId(id(n + 100)), id(n + 200))

    private val originals = (1..47).map(::source).toSet()
    private val fresh = source(1000)

    private fun policy() = DiagnosticSourcePolicy.protected(originals, setOf(id(500), id(501)))

    @Test
    fun all47SourcesRejectedBeforeOperation() {
        val p = policy()
        originals.forEach { old ->
            assertTrue(p.isProtected(old))
            assertThrows(IllegalStateException::class.java) { p.requireNewSource(old) }
        }
    }

    @Test
    fun newSourceCanUseNormalOperations() {
        val p = policy()
        p.requireNewSource(fresh)
        p.requireRun(id(2000), fresh)
        p.requireComponent(id(2001))
        assertFalse(p.isProtected(fresh))
    }

    @Test
    fun reusedHistoricalRecordingRejectedEvenWithNewAsset() {
        val old = originals.first()
        assertThrows(IllegalStateException::class.java) {
            policy()
                .requireNewSource(AudioIdentity(old.recordingId, fresh.assetId, fresh.sessionId))
        }
    }

    @Test
    fun reusedHistoricalAssetRejectedEvenWithNewRecording() {
        val old = originals.first()
        assertThrows(IllegalStateException::class.java) {
            policy()
                .requireNewSource(AudioIdentity(fresh.recordingId, old.assetId, fresh.sessionId))
        }
    }

    @Test
    fun reusedHistoricalSessionRejected() {
        assertThrows(IllegalStateException::class.java) {
            policy()
                .requireNewSource(
                    AudioIdentity(fresh.recordingId, fresh.assetId, originals.first().sessionId)
                )
        }
    }

    @Test
    fun historicalRunRejectedForNewOwner() {
        assertThrows(IllegalStateException::class.java) { policy().requireRun(id(500), fresh) }
    }

    @Test
    fun historicalComponentRejectedBeforeMetadata() {
        assertThrows(IllegalStateException::class.java) { policy().requireComponent(id(501)) }
    }

    @Test
    fun runWithoutPositiveOwnerRejected() {
        assertThrows(IllegalStateException::class.java) { policy().requireRun(id(2000), null) }
    }

    @Test
    fun sourceComponentCannotBecomeRun() {
        assertThrows(IllegalStateException::class.java) {
            policy().requireRun(fresh.sessionId, fresh)
        }
    }

    @Test
    fun missingHistoricalSourceRejected() {
        assertThrows(IllegalStateException::class.java) {
            DiagnosticSourcePolicy.protected(originals.drop(1).toSet(), setOf(id(500)))
        }
    }

    @Test
    fun inputSetMutationCannotRemoveProtection() {
        val copied = originals.toMutableSet()
        val p = DiagnosticSourcePolicy.protected(copied, setOf(id(500)))
        copied.clear()
        assertThrows(IllegalStateException::class.java) { p.requireNewSource(originals.first()) }
    }

    @Test
    fun ordinaryModeHasNoDiagnosticRestrictions() {
        val p = DiagnosticSourcePolicy.ordinary()
        assertFalse(p.active)
        originals.forEach { p.requireNewSource(it) }
        p.requireRun(id(500), null)
    }
}
