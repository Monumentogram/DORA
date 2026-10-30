package com.monumentogram.dora.audio.persistence

import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture.Companion.success
import com.monumentogram.dora.audio.persistence.LogicalRecoveryRuntimeTest.Companion.id
import com.monumentogram.dora.audio.persistence.LogicalRecoveryRuntimeTest.Companion.recovery
import com.monumentogram.dora.audio.persistence.LogicalRecoveryRuntimeTest.Companion.reference
import com.monumentogram.dora.audio.recording.AlphaRecordingLimit
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.audio.recording.RecordingSession
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Actual SQLCipher/Keystore/AEAD adapters and synthetic PCM; no physical microphone claim. */
class AlphaRecordingRuntimeTest {
    @Test
    @Suppress("LongMethod") // One encrypted capture/reopen/source continuity scenario.
    // Historical inventory selector; ADR-0030 now exercises 1799 -> 1800 seconds.
    fun pauseReopenAt299SecondsClipsResumeAndPreservesOriginalAcrossNewRecording() {
        val f = EncryptedAudioVaultFaultFixture()
        f.open().use { vault ->
            val session = RecordingSession(f.audio, vault.writer)
            assertTrue(session.start())
            repeat(359) { session.accept(ByteArray(160000) { 7 }) }
            session.accept(ByteArray(128000) { 7 })
            session.pause()
            assertEquals(28_784_000L, session.state.durableFrames)
            assertEquals(RecordingPhase.PAUSED, session.state.phase)
        }
        lateinit var original: OriginalAudioReference
        f.open(false).use { vault ->
            val restored = recovery(vault, f.audio)
            assertTrue(restored.canResume)
            assertEquals(28_784_000L, restored.recoveredFrames)
            val session = RecordingSession(f.audio, vault.writer)
            session.restore(restored.recoveredFrames, restored.nextOrdinal)
            assertTrue(session.resume())
            session.accept(ByteArray(32002) { 7 })
            session.accept(ByteArray(1600) { 99 })
            assertEquals(AlphaRecordingLimit.MAX_ALPHA_FRAMES, session.state.frames)
            assertFalse(session.canCapture)
            session.requestStop()
            session.confirmStop()
            assertEquals(RecordingPhase.SAVED, session.state.phase)
            original = reference(vault, f.audio)
            exact(vault, original)
            com.monumentogram.dora.audio.transcript.ProtectedPcm.readWindow(
                    vault.originals,
                    original,
                    28_799_998,
                    2,
                ) {
                    false
                }
                .use { window ->
                    assertEquals(2, window.samples.size)
                    assertTrue(window.samples.all { it == 1799f / 32768f })
                }
            val other =
                f.audio.copy(
                    recordingId = RecordingId(id(810)),
                    assetId = AudioAssetId(id(811)),
                    sessionId = id(812),
                )
            val independent = RecordingSession(other, vault.writer)
            assertTrue(independent.start())
            independent.accept(ByteArray(640) { 3 })
            independent.requestStop()
            independent.confirmStop()
            assertEquals(RecordingPhase.SAVED, independent.state.phase)
            assertEquals(320L, reference(vault, other).frames)
            assertEquals(original, reference(vault, f.audio))
            var called = false
            success(vault.originals.withAvailable(original) { called = true })
            assertTrue(called)
            called = false
            val stale =
                vault.originals.withAvailable(original.copy(frames = original.frames + 1)) {
                    called = true
                }
            assertEquals(AudioResult.Value(OriginalAudioStatus.StaleReference), stale)
            assertFalse(called)
        }
        f.open(false).use { vault ->
            assertEquals(original, reference(vault, f.audio))
            exact(vault, original)
        }
    }

    @Test
    fun interruptedAtExactLimitReopensPrefixWithoutResumeOrFalseFinalization() {
        val f = EncryptedAudioVaultFaultFixture()
        f.open().use { vault ->
            val session = RecordingSession(f.audio, vault.writer)
            assertTrue(session.start())
            repeat(360) { session.accept(ByteArray(160000) { 7 }) }
            session.interrupt()
            assertEquals(28_800_000L, session.state.durableFrames)
        }
        f.open(false).use { vault ->
            val restored = recovery(vault, f.audio)
            assertEquals(28_800_000L, restored.recoveredFrames)
            assertEquals(
                AudioResult.Value(OriginalAudioStatus.NotFinalized),
                vault.originals.acquire(f.audio),
            )
            val session = RecordingSession(f.audio, vault.writer)
            session.restore(restored.recoveredFrames, restored.nextOrdinal)
            assertFalse(session.resume())
            session.accept(ByteArray(1600))
            assertEquals(28_800_000L, session.state.frames)
            assertEquals(restored, recovery(vault, f.audio))
        }
    }

    private fun exact(vault: EncryptedAudioVault, ref: OriginalAudioReference) {
        var next = 0L
        val borrowed = mutableListOf<ByteArray>()
        assertEquals(
            AudioResult.Value(OriginalAudioStatus.Available(ref)),
            vault.originals.extract(ref) { first, pcm ->
                assertEquals(next, first)
                assertTrue(pcm.all { it == 7.toByte() })
                next += pcm.size / 2
                borrowed.add(pcm)
            },
        )
        assertEquals(28_800_000L, next)
        assertTrue(borrowed.all { bytes -> bytes.all { it == 0.toByte() } })
    }
}
