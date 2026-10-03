package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.audio.persistence.auth.AppLockSession
import com.monumentogram.dora.audio.persistence.auth.AppLockedException
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsyncRecordingSessionTest {
    @Test
    fun deviceLockDuringActiveAppendFencesResumeAndRetiresOnlyAfterWriterReturns() {
        val secure = AtomicBoolean(true)
        val appLock = AppLockSession({ 100L }) { secure.get() }
        appLock.resume()
        val attempt = appLock.begin()
        check(appLock.recordResult(attempt))
        check(appLock.complete(attempt) { true })
        val foreground = appLock.capture()
        val authority =
            RecordingAuthority(
                foreground::requireActive,
                secure::get,
                foreground::withPlaintextDelivery,
            )
        authority.activate {}
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val io = Executors.newSingleThreadExecutor()
        writer.duringAppend = {
            entered.countDown()
            check(finish.await(5, TimeUnit.SECONDS))
        }
        val authorized =
            object : ProductAudioWriterPort by writer {
                override fun append(
                    segment: AudioStorageUnitIdentity,
                    format: AudioFormat,
                    pcm: ByteArray,
                ): AudioResult<Unit> =
                    try {
                        authority.requireWriter()
                        writer.append(segment, format, pcm).also { authority.requireWriter() }
                    } catch (_: AppLockedException) {
                        AudioResult.Failed(AudioFailure.LOCKED)
                    }
            }
        val recording =
            RecordingSession(identity, authorized, persistence = io, completion = completions)
        var retired = false
        try {
            recording.start()
            recording.accept(ByteArray(160_000))
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            recording.accept(ByteArray(1600))
            recording.pause()
            assertEquals(RecordingPhase.PAUSED, recording.state.phase)
            secure.set(false)
            org.junit.Assert.assertThrows(AppLockedException::class.java) {
                authority.resume(foreground::withPlaintextDelivery) {
                    org.junit.Assert.fail("Locked Resume")
                }
            }
            recording.whenSettled { retired = true }
            assertFalse(retired)
            finish.countDown()
            io.execute { exited.countDown() }
            assertTrue(exited.await(5, TimeUnit.SECONDS))
            completions.all()
            assertTrue(retired)
            assertEquals(RecordingPhase.INTERRUPTED, recording.state.phase)
            assertEquals(AudioFailure.LOCKED, recording.state.persistenceFailure)
            assertFalse(recording.resume())
            assertEquals(0, writer.finalizations)
            assertEquals(1, writer.units.size)
        } finally {
            finish.countDown()
            io.shutdownNow()
        }
    }

    @Test
    fun pauseAndResumeCompleteInsideActiveAppendWithoutWaitingForIt() {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val io = Executors.newSingleThreadExecutor()
        writer.duringAppend = {
            entered.countDown()
            check(finish.await(5, TimeUnit.SECONDS))
        }
        val recording =
            RecordingSession(identity, writer, persistence = io, completion = completions)
        try {
            recording.start()
            recording.accept(ByteArray(160_000))
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            recording.accept(ByteArray(1600))
            recording.pause()
            assertEquals(RecordingPhase.PAUSED, recording.state.phase)
            assertTrue(recording.resume())
            recording.accept(ByteArray(1600))
            recording.pause()
            assertEquals(81_600L, recording.state.frames)
            assertEquals(0L, recording.state.durableFrames)
            finish.countDown()
            io.execute { exited.countDown() }
            assertTrue(exited.await(5, TimeUnit.SECONDS))
            completions.all()
            assertEquals(listOf(0L, 80_000L, 80_800L), writer.units.map { it.firstFrame })
            assertEquals(writer.units[0].physicalSegmentId, writer.units[1].physicalSegmentId)
            assertNotEquals(writer.units[1].physicalSegmentId, writer.units[2].physicalSegmentId)
        } finally {
            finish.countDown()
            io.shutdownNow()
        }
    }

    @Test
    fun lockDuringPendingPauseCannotReviveCaptureOrFinalizeOnLateCompletion() {
        session.start()
        session.accept(ByteArray(1600))
        session.pause()
        session.interrupt(AudioFailure.LOCKED)
        assertFalse(session.resume())
        var retired = false
        session.whenSettled { retired = true }
        assertFalse(retired)
        writes.all()
        completions.all()
        assertTrue(retired)
        assertEquals(RecordingPhase.INTERRUPTED, session.state.phase)
        assertEquals(0L, session.state.durableFrames)
        assertEquals(0, writer.finalizations)
    }

    @Test
    fun mismatchedAdmissionIdentityCannotBeReattributedToNewSegment() {
        session.start()
        val old = session.physicalId
        session.accept(ByteArray(1600), old, 0)
        session.pause()
        session.resume()
        session.accept(ByteArray(1600), old, 800)
        assertEquals(RecordingPhase.INTERRUPTED, session.state.phase)
        assertEquals(AudioFailure.INVALID_INPUT, session.state.persistenceFailure)
    }

    private val writes = Steps()
    private val completions = Steps()
    private val writer = Writer()
    private val identity = AudioIdentity(RecordingId(id()), AudioAssetId(id()), id())
    private val session =
        RecordingSession(identity, writer, persistence = writes, completion = completions)

    @Test
    fun resumedSegmentStartsBeforeOldTailCommitsWithExactOrdering() {
        assertTrue(session.start())
        session.accept(ByteArray(32_000) { 1 })
        session.pause()
        assertEquals(RecordingPhase.PAUSED, session.state.phase)
        assertEquals(0L, session.state.durableFrames)
        assertEquals(RecordingDurability.PENDING, session.state.durability)
        assertTrue(session.resume())
        session.accept(ByteArray(32_000) { 2 })
        session.pause()
        assertEquals(32_000L, session.state.frames)
        assertTrue(writer.units.isEmpty())
        writes.all()
        completions.all()
        assertEquals(listOf(0L, 16_000L), writer.units.map { it.firstFrame })
        assertEquals(listOf(0L, 16_000L), writer.units.map { it.physicalFirstFrame })
        assertEquals(listOf(0L, 0L), writer.units.map { it.sourceFrameOffset })
        assertNotEquals(writer.units[0].physicalSegmentId, writer.units[1].physicalSegmentId)
        assertEquals(listOf(1.toByte(), 2.toByte()), writer.firstBytes)
        assertEquals(32_000L, session.state.durableFrames)
        assertEquals(RecordingDurability.CAUGHT_UP, session.state.durability)
    }

    @Test
    fun stopWaitsForEveryOldTailAndFinalReadback() {
        session.start()
        session.accept(ByteArray(1600))
        session.pause()
        session.resume()
        session.accept(ByteArray(1600))
        session.requestStop()
        session.confirmStop()
        assertEquals(RecordingPhase.FINALIZING, session.state.phase)
        writes.one()
        completions.all()
        assertEquals(RecordingPhase.FINALIZING, session.state.phase)
        assertEquals(800L, session.state.durableFrames)
        assertEquals(0, writer.finalizations)
        writes.all()
        completions.all()
        assertEquals(RecordingPhase.SAVED, session.state.phase)
        assertEquals(1600L, session.state.durableFrames)
        assertEquals(1, writer.finalizations)
    }

    @Test
    fun failureAfterVisiblePauseStopsResumedCaptureAndNeverSaves() {
        session.start()
        session.accept(ByteArray(1600))
        session.pause()
        assertEquals(RecordingPhase.PAUSED, session.state.phase)
        session.resume()
        session.accept(ByteArray(1600))
        session.pause()
        writer.failure = AudioFailure.UNCERTAIN
        writes.one()
        assertFalse(session.canCapture)
        completions.all()
        assertEquals(RecordingPhase.INTERRUPTED, session.state.phase)
        assertEquals(RecordingDurability.FAILED, session.state.durability)
        assertEquals(AudioFailure.UNCERTAIN, session.state.persistenceFailure)
        session.requestStop()
        session.confirmStop()
        writes.all()
        completions.all()
        assertEquals(0, writer.finalizations)
        assertTrue(writer.units.isEmpty())
    }

    @Test
    fun duplicateAndRapidControlsDoNotDuplicateTailOrAddPauseTime() {
        session.start()
        repeat(20) {
            session.accept(ByteArray(1600))
            session.pause()
            session.pause()
            assertTrue(session.resume())
            assertFalse(session.resume())
        }
        session.pause()
        writes.all()
        completions.all()
        assertEquals(16_000L, session.state.frames)
        assertEquals(16_000L, session.state.durableFrames)
        assertEquals(20, writer.units.size)
        assertEquals(20, writer.units.map { it.physicalSegmentId }.distinct().size)
    }

    @Test
    fun emptyPauseNeedsNoPersistenceTask() {
        session.start()
        session.pause()
        assertEquals(RecordingPhase.PAUSED, session.state.phase)
        assertEquals(RecordingDurability.CAUGHT_UP, session.state.durability)
        assertEquals(0, writes.tasks.size)
    }

    private class Steps : Executor {
        val tasks = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun one() {
            tasks.removeFirst().run()
        }

        fun all() {
            while (tasks.isNotEmpty()) one()
        }
    }

    private class Writer : ProductAudioWriterPort {
        var duringAppend: () -> Unit = {}
        val units = mutableListOf<AudioStorageUnitIdentity>()
        val firstBytes = mutableListOf<Byte>()
        var failure: AudioFailure? = null
        var finalizations = 0

        override fun create(identity: AudioIdentity): AudioResult<Unit> = AudioResult.Value(Unit)

        override fun append(
            segment: AudioStorageUnitIdentity,
            format: AudioFormat,
            pcm: ByteArray,
        ): AudioResult<Unit> {
            duringAppend()
            failure?.let {
                return AudioResult.Failed(it)
            }
            units.add(segment)
            firstBytes.add(pcm.first())
            return AudioResult.Value(Unit)
        }

        override fun finalize(identity: AudioIdentity): AudioResult<Unit> {
            finalizations++
            return AudioResult.Value(Unit)
        }

        override fun reconcile(identity: AudioIdentity): AudioResult<Unit> = AudioResult.Value(Unit)
    }

    companion object {
        private fun id() = UUID.randomUUID().toString()
    }
}
