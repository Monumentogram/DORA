package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingSessionTest {
    @Test
    fun recoveredResumeDoesNotCreateAgainAndPreservesCommittedEnd() {
        session.restore(1234L, 3)
        assertTrue(session.resume())
        session.accept(ByteArray(640))
        session.pause()
        assertEquals(0, writer.creates)
        assertEquals(1554L, session.state.frames)
        assertEquals(1234L, writer.units.single().firstFrame)
        assertEquals(1234L, writer.units.single().physicalFirstFrame)
        assertEquals(3, writer.units.single().ordinal)
    }

    private val writer = MemoryWriter()
    private val identity = AudioIdentity(RecordingId(id()), AudioAssetId(id()), id())
    private val session = RecordingSession(identity, writer)

    @Test
    fun pauseAddsNoFramesAndResumeChangesOnlyPhysicalIdentity() {
        assertTrue(session.start())
        session.accept(ByteArray(640))
        session.pause()
        assertEquals(320L, session.state.frames)
        session.accept(ByteArray(640))
        assertEquals(320L, session.state.frames)
        assertTrue(session.resume())
        session.accept(ByteArray(1280))
        session.pause()
        assertEquals(960L, session.state.frames)
        assertEquals(listOf(0L, 320L), writer.units.map { it.firstFrame })
        assertNotEquals(writer.units[0].physicalSegmentId, writer.units[1].physicalSegmentId)
        assertEquals(identity, writer.units[1].audio)
        assertEquals(320L, writer.units[1].physicalFirstFrame)
        assertEquals(0L, writer.units[1].sourceFrameOffset)
    }

    @Test
    fun stopConfirmationDoesNotPauseAndDuplicateStopCannotFinalizeTwice() {
        session.start()
        session.requestStop()
        session.requestStop()
        session.accept(ByteArray(640))
        assertEquals(RecordingPhase.RECORDING, session.state.phase)
        assertTrue(session.state.stopConfirmation)
        session.cancelStop()
        assertFalse(session.state.stopConfirmation)
        session.requestStop()
        session.confirmStop()
        session.confirmStop()
        assertEquals(1, writer.finalizations)
        assertEquals(RecordingPhase.SAVED, session.state.phase)
        assertFalse(session.resume())
        assertFalse(session.start())
    }

    @Test
    fun uncertainWriteNeverBecomesSavedOrContinuesAppending() {
        session.start()
        writer.failure = AudioFailure.UNCERTAIN
        session.accept(ByteArray(640))
        session.pause()
        assertEquals(RecordingPhase.INTERRUPTED, session.state.phase)
        assertEquals(AudioFailure.UNCERTAIN, session.state.persistenceFailure)
        assertEquals(0L, session.state.durableFrames)
        session.requestStop()
        session.confirmStop()
        assertEquals(0, writer.finalizations)
        assertFalse(session.resume())
    }

    @Test
    fun duplicateStartPauseResumePreserveOneRecording() {
        assertTrue(session.start())
        assertFalse(session.start())
        session.accept(ByteArray(640))
        session.pause()
        session.pause()
        assertTrue(session.resume())
        assertFalse(session.resume())
        session.accept(ByteArray(640))
        session.pause()
        assertEquals(1, writer.creates)
        assertEquals(2, writer.units.size)
        assertEquals(640L, session.state.frames)
    }

    @Test
    fun finalizationFailureCannotPublishSaved() {
        session.start()
        session.accept(ByteArray(640))
        session.pause()
        writer.failure = AudioFailure.KEY_UNAVAILABLE
        session.requestStop()
        session.confirmStop()
        assertEquals(RecordingPhase.INTERRUPTED, session.state.phase)
        assertEquals(AudioFailure.KEY_UNAVAILABLE, session.state.persistenceFailure)
        assertTrue(session.state.durableFrames > 0)
    }

    @Test
    fun transportUnitsAreBoundedAndContiguousInsideOnePhysicalSegment() {
        session.start()
        repeat(501) { session.accept(ByteArray(640)) }
        session.pause()
        assertEquals(listOf(160000, 160000, 640), writer.sizes)
        assertEquals(listOf(0L, 80000L, 160000L), writer.units.map { it.firstFrame })
        assertEquals(1, writer.units.map { it.physicalSegmentId }.distinct().size)
        assertEquals(listOf(0L, 80000L, 160000L), writer.units.map { it.sourceFrameOffset })
        assertEquals(160320L, session.state.durableFrames)
    }

    @Test
    fun pausedConfirmationRemainsPausedAndZeroAudioIsNotSaved() {
        session.start()
        session.pause()
        session.requestStop()
        session.cancelStop()
        assertEquals(RecordingPhase.PAUSED, session.state.phase)
        session.requestStop()
        session.confirmStop()
        assertEquals(RecordingPhase.EMPTY, session.state.phase)
        assertEquals(0, writer.finalizations)
    }

    private class MemoryWriter : ProductAudioWriterPort {
        var creates = 0
        var finalizations = 0
        var failure: AudioFailure? = null
        val units = mutableListOf<AudioStorageUnitIdentity>()
        val sizes = mutableListOf<Int>()

        override fun create(identity: AudioIdentity): AudioResult<Unit> {
            creates++
            return AudioResult.Value(Unit)
        }

        override fun append(
            segment: AudioStorageUnitIdentity,
            format: AudioFormat,
            pcm: ByteArray,
        ): AudioResult<Unit> {
            failure?.let {
                return AudioResult.Failed(it)
            }
            assertEquals(AudioFormat.PCM, format)
            units.add(segment)
            sizes.add(pcm.size)
            return AudioResult.Value(Unit)
        }

        override fun finalize(identity: AudioIdentity): AudioResult<Unit> {
            finalizations++
            return failure?.let { AudioResult.Failed(it) } ?: AudioResult.Value(Unit)
        }

        override fun reconcile(identity: AudioIdentity): AudioResult<Unit> = AudioResult.Value(Unit)
    }

    companion object {
        private fun id() = UUID.randomUUID().toString()
    }
}
