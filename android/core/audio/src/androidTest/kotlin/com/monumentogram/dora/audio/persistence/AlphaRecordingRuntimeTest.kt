package com.monumentogram.dora.audio.persistence

import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture.Companion.success
import com.monumentogram.dora.audio.persistence.LogicalRecoveryRuntimeTest.Companion.id
import com.monumentogram.dora.audio.persistence.LogicalRecoveryRuntimeTest.Companion.recovery
import com.monumentogram.dora.audio.persistence.LogicalRecoveryRuntimeTest.Companion.reference
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
    // Historical selector: v9 retains 1799 -> 1800-second recovery below the new 24h cap.
    fun pauseReopenAt299SecondsClipsResumeAndPreservesOriginalAcrossNewRecording() {
        val progress = ScalarProgress()
        val f = EncryptedAudioVaultFaultFixture()
        progress.phase("FIXTURE_READY")
        progress.phase("OPEN_BEGIN")
        f.open().use { vault ->
            progress.phase("OPEN_END")
            val session = RecordingSession(f.audio, vault.writer)
            assertTrue(session.start())
            repeat(359) { progress.accept(session, ByteArray(160000) { 7 }) }
            progress.accept(session, ByteArray(128000) { 7 })
            progress.phase("PAUSE_BEGIN")
            session.pause()
            progress.phase("PAUSE_END", session.state.durableFrames)
            assertEquals(28_784_000L, session.state.durableFrames)
            assertEquals(RecordingPhase.PAUSED, session.state.phase)
        }
        lateinit var original: OriginalAudioReference
        progress.phase("REOPEN_BEGIN")
        f.open(false).use { vault ->
            progress.phase("REOPEN_END")
            progress.phase("RECOVERY_BEGIN")
            val restored = recovery(vault, f.audio)
            progress.phase("RECOVERY_END", restored.recoveredFrames)
            assertTrue(restored.canResume)
            assertEquals(28_784_000L, restored.recoveredFrames)
            val session = RecordingSession(f.audio, vault.writer)
            session.restore(restored.recoveredFrames, restored.nextOrdinal)
            assertTrue(session.resume())
            progress.accept(session, ByteArray(32000) { 7 })
            assertEquals(28_800_000L, session.state.frames)
            assertTrue(session.canCapture)
            progress.phase("FINALIZE_BEGIN")
            session.requestStop()
            session.confirmStop()
            progress.phase("FINALIZE_END", session.state.durableFrames)
            assertEquals(RecordingPhase.SAVED, session.state.phase)
            progress.phase("VERIFY_BEGIN")
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
            progress.phase("VERIFY_END", original.frames)
            val other =
                f.audio.copy(
                    recordingId = RecordingId(id(810)),
                    assetId = AudioAssetId(id(811)),
                    sessionId = id(812),
                )
            val independent = RecordingSession(other, vault.writer)
            progress.phase("SECOND_SOURCE_BEGIN")
            assertTrue(independent.start())
            progress.accept(independent, ByteArray(640) { 3 })
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
            progress.phase("SECOND_SOURCE_END", independent.state.durableFrames)
        }
        progress.phase("FINAL_REOPEN_BEGIN")
        f.open(false).use { vault ->
            progress.phase("FINAL_REOPEN_END")
            progress.phase("VERIFY_BEGIN")
            assertEquals(original, reference(vault, f.audio))
            exact(vault, original)
            progress.phase("VERIFY_END", original.frames)
        }
        progress.phase("DONE")
    }

    @Test
    fun interruptedAtExactLimitReopensPrefixWithoutResumeOrFalseFinalization() {
        val progress = ScalarProgress()
        val f = EncryptedAudioVaultFaultFixture()
        progress.phase("FIXTURE_READY")
        progress.phase("OPEN_BEGIN")
        f.open().use { vault ->
            progress.phase("OPEN_END")
            val session = RecordingSession(f.audio, vault.writer)
            assertTrue(session.start())
            repeat(360) { progress.accept(session, ByteArray(160000) { 7 }) }
            progress.phase("INTERRUPT_BEGIN")
            session.interrupt()
            progress.phase("INTERRUPT_END", session.state.durableFrames)
            assertEquals(28_800_000L, session.state.durableFrames)
        }
        progress.phase("REOPEN_BEGIN")
        f.open(false).use { vault ->
            progress.phase("REOPEN_END")
            progress.phase("RECOVERY_BEGIN")
            val restored = recovery(vault, f.audio)
            progress.phase("RECOVERY_END", restored.recoveredFrames)
            assertEquals(28_800_000L, restored.recoveredFrames)
            assertEquals(
                AudioResult.Value(OriginalAudioStatus.NotFinalized),
                vault.originals.acquire(f.audio),
            )
            val session = RecordingSession(f.audio, vault.writer)
            session.restore(restored.recoveredFrames, restored.nextOrdinal)
            assertTrue(session.resume())
            progress.accept(session, ByteArray(1600))
            assertEquals(28_800_800L, session.state.frames)
            progress.phase("PAUSE_BEGIN")
            session.pause()
            progress.phase("PAUSE_END", session.state.durableFrames)
            progress.phase("RECOVERY_BEGIN")
            assertEquals(28_800_800L, recovery(vault, f.audio).recoveredFrames)
            progress.phase("RECOVERY_END", session.state.durableFrames)
        }
        progress.phase("DONE")
    }

    /** Returned accept calls across all sources in this method, not a storage-commit count. */
    private class ScalarProgress {
        private val started = SystemClock.elapsedRealtime()
        private var acceptedUnits = 0
        private var durableFrames = 0L

        fun accept(session: RecordingSession, pcm: ByteArray) {
            session.accept(pcm)
            acceptedUnits++
            durableFrames = session.state.durableFrames
            if (acceptedUnits % PROGRESS_BATCH_UNITS == 0) phase("APPEND")
        }

        fun phase(name: String, frames: Long = durableFrames) {
            durableFrames = frames
            val elapsed = SystemClock.elapsedRealtime() - started
            val message =
                "phase=$name acceptedUnits=$acceptedUnits durableFrames=$durableFrames elapsedMs=$elapsed"
            InstrumentationRegistry.getInstrumentation()
                .sendStatus(
                    PROGRESS_STATUS_CODE,
                    Bundle().apply { putString("stage9Progress", message) },
                )
        }

        private companion object {
            const val PROGRESS_BATCH_UNITS = 30
            const val PROGRESS_STATUS_CODE = 3
        }
    }

    private fun exact(vault: EncryptedAudioVault, ref: OriginalAudioReference) {
        var next = 0L
        var borrowed: ByteArray? = null
        assertEquals(
            AudioResult.Value(OriginalAudioStatus.Available(ref)),
            vault.originals.extract(ref) { first, pcm ->
                assertEquals(next, first)
                assertTrue(pcm.all { it == 7.toByte() })
                next += pcm.size / 2
                borrowed?.let { previous -> assertTrue(previous.all { it == 0.toByte() }) }
                borrowed = pcm
            },
        )
        assertEquals(28_800_000L, next)
        assertTrue(borrowed!!.all { it == 0.toByte() })
    }
}
