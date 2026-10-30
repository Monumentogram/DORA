package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphaRecordingLimitTest {
    private val identity =
        AudioIdentity(
            RecordingId("00000000-0000-4000-8000-000000000001"),
            AudioAssetId("00000000-0000-4000-8000-000000000002"),
            "alpha-synthetic",
        )
    private val writer = Writer()
    private val session = RecordingSession(identity, writer)

    @Test
    fun directInputClipsLastBlockAndRejectsSubsequentInput() {
        session.restore(28_799_999, 60)
        assertTrue(session.resume())
        session.accept(byteArrayOf(1, 2, 3, 4))
        session.accept(ByteArray(1600))
        assertEquals(28_800_000L, session.state.frames)
        assertFalse(session.canCapture)
        session.requestStop()
        session.confirmStop()
        assertEquals(RecordingPhase.SAVED, session.state.phase)
        assertEquals(listOf(2), writer.sizes)
        assertEquals(1, writer.finalizations)
    }

    @Test
    fun pauseAtTwentyNineMinutes59SecondsKeepsOnlyOneSecondRemaining() {
        session.restore(28_784_000, 60)
        session.resume()
        session.pause()
        session.accept(ByteArray(160_000))
        assertEquals(28_784_000L, session.state.frames)
        assertTrue(session.resume())
        session.accept(ByteArray(160_000))
        assertEquals(28_800_000L, session.state.frames)
        session.pause()
        assertFalse(session.resume())
    }

    @Test
    fun authenticatedRestartAtOrAboveLimitCannotResume() {
        for (frames in listOf(28_800_000L, 57_600_000L)) {
            val restored = RecordingSession(identity, writer)
            restored.restore(frames, 120)
            assertFalse(restored.resume())
            restored.accept(ByteArray(1600))
            assertEquals(frames, restored.state.frames)
        }
        assertTrue(writer.sizes.isEmpty())
    }

    @Test
    fun concurrentDirectInputsCannotOverrunAllowance() {
        session.restore(28_799_999, 60)
        session.resume()
        val ready = java.util.concurrent.CountDownLatch(1)
        val threads =
            List(8) {
                Thread {
                        ready.await()
                        session.accept(ByteArray(1600))
                    }
                    .also { it.start() }
            }
        ready.countDown()
        threads.forEach {
            it.join(5000)
            assertFalse(it.isAlive)
        }
        session.requestStop()
        session.confirmStop()
        assertEquals(28_800_000L, session.state.frames)
        assertEquals(listOf(2), writer.sizes)
    }

    @Test
    fun limitDoesNotClaimSavedBeforeQueuedDurabilityAndFinalizeComplete() {
        val tasks = ArrayDeque<Runnable>()
        val queued =
            RecordingSession(
                identity,
                writer,
                persistence =
                    java.util.concurrent.Executor {
                        tasks.add(it)
                    },
            )
        queued.restore(28_799_999, 60)
        queued.resume()
        queued.accept(ByteArray(1600))
        queued.requestStop()
        queued.confirmStop()
        assertEquals(RecordingPhase.FINALIZING, queued.state.phase)
        assertEquals(28_799_999L, queued.state.durableFrames)
        while (tasks.isNotEmpty()) tasks.removeFirst().run()
        assertEquals(RecordingPhase.SAVED, queued.state.phase)
        assertEquals(28_800_000L, queued.state.durableFrames)
        queued.confirmStop()
        assertEquals(1, writer.finalizations)
    }

    @Test
    fun failedFinalizationAtLimitRemainsInterrupted() {
        writer.finalizeFailure = AudioFailure.UNCERTAIN
        session.restore(28_799_999, 60)
        session.resume()
        session.accept(ByteArray(1600))
        session.requestStop()
        session.confirmStop()
        assertEquals(RecordingPhase.INTERRUPTED, session.state.phase)
        assertEquals(AudioFailure.UNCERTAIN, session.state.persistenceFailure)
        assertFalse(session.resume())
    }

    @Test
    fun newLogicalRecordingHasIndependentAllowance() {
        session.restore(28_800_000, 60)
        val next = RecordingSession(identity.copy(sessionId = "independent"), Writer())
        assertTrue(next.start())
        next.accept(ByteArray(1600))
        assertEquals(800L, next.state.frames)
        assertEquals(28_799_200L, next.state.remainingFrames)
        assertEquals(28_800_000L, session.state.frames)
    }

    private class Writer : ProductAudioWriterPort {
        val sizes = mutableListOf<Int>()
        var finalizations = 0
        var finalizeFailure: AudioFailure? = null

        override fun create(identity: AudioIdentity) = AudioResult.Value(Unit)

        override fun append(
            segment: AudioStorageUnitIdentity,
            format: AudioFormat,
            pcm: ByteArray,
        ): AudioResult<Unit> {
            sizes.add(pcm.size)
            return AudioResult.Value(Unit)
        }

        override fun finalize(identity: AudioIdentity): AudioResult<Unit> {
            finalizations++
            return finalizeFailure?.let { AudioResult.Failed(it) } ?: AudioResult.Value(Unit)
        }

        override fun reconcile(identity: AudioIdentity) = AudioResult.Value(Unit)
    }
}
