package com.monumentogram.dora.audio.diagnostics

import android.os.Build
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture.Companion.success
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.audio.recording.RecordingSession
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real encrypted writer with deterministic task scheduling; no physical audio capture. */
class PersistenceAsyncDurabilityTest {
    @Test
    fun encryptedCommitDoesNotAcknowledgeFramesUntilCompletionReturns() {
        assertTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val fixture = EncryptedAudioVaultFaultFixture()
        fixture.open().use { vault ->
            val io = Steps()
            val callbacks = Steps()
            val session =
                RecordingSession(
                    fixture.audio,
                    vault.writer,
                    persistence = io,
                    completion = callbacks,
                )
            assertTrue(session.start())
            repeat(320) { session.accept(ByteArray(1600) { 7 }) }
            assertEquals(256000L, session.state.frames - session.state.durableFrames)
            assertEquals(3, session.pendingWriterUnits)
            io.next()
            var readable = 0L
            success(
                vault.reader.extract(fixture.audio) { frame, pcm ->
                    assertEquals(readable, frame)
                    assertTrue(pcm.all { it == 7.toByte() })
                    readable += pcm.size / 2
                }
            )
            assertEquals(80000L, readable)
            assertEquals(0L, session.state.durableFrames)
            callbacks.all()
            assertEquals(80000L, session.state.durableFrames)
            assertEquals(176000L, session.state.frames - session.state.durableFrames)
            session.requestStop()
            session.confirmStop()
            assertEquals(RecordingPhase.FINALIZING, session.state.phase)
            io.all()
            assertEquals(RecordingPhase.FINALIZING, session.state.phase)
            callbacks.all()
            assertEquals(RecordingPhase.SAVED, session.state.phase)
            assertEquals(256000L, session.state.durableFrames)
            assertEquals(0, session.pendingWriterUnits)
        }
    }

    @Test
    fun normalEncryptedWriterFinalizesExactSyntheticFrames() {
        assertTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val fixture = EncryptedAudioVaultFaultFixture()
        fixture.open().use { vault ->
            val session = RecordingSession(fixture.audio, vault.writer)
            assertTrue(session.start())
            session.accept(ByteArray(160000) { 9 })
            assertEquals(80000L, session.state.durableFrames)
            session.requestStop()
            session.confirmStop()
            assertEquals(RecordingPhase.SAVED, session.state.phase)
            val read =
                vault.reader.extract(fixture.audio) { frame, pcm ->
                    assertEquals(0L, frame)
                    assertEquals(160000, pcm.size)
                    assertTrue(pcm.all { it == 9.toByte() })
                }
            assertTrue(read is AudioResult.Value)
            assertEquals(80000L, (read as AudioResult.Value).value.frames)
        }
    }

    private class Steps : Executor {
        private val pending = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            pending.addLast(command)
        }

        fun next() {
            pending.removeFirst().run()
        }

        fun all() {
            while (pending.isNotEmpty()) next()
        }
    }
}
