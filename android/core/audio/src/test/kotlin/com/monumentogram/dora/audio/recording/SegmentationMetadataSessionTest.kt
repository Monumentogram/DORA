package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.vad.VadFailure
import java.util.UUID
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentationMetadataSessionTest {
    private class Writer : ProductAudioWriterPort {
        var frames = 0L
        var metadataFailure = false
        var finalized = false
        val metadata = mutableListOf<SegmentationMetadata>()

        override fun create(identity: AudioIdentity) = AudioResult.Value(Unit)

        override fun append(
            segment: AudioStorageUnitIdentity,
            format: AudioFormat,
            pcm: ByteArray,
        ): AudioResult<Unit> {
            assertEquals(frames, segment.firstFrame)
            frames += pcm.size / 2
            return AudioResult.Value(Unit)
        }

        override fun segmentation(
            identity: AudioIdentity,
            metadata: SegmentationMetadata,
        ): AudioResult<Unit> {
            if (metadataFailure) return AudioResult.Failed(AudioFailure.UNAVAILABLE)
            metadata.validate(frames)
            this.metadata.add(metadata)
            return AudioResult.Value(Unit)
        }

        override fun finalize(identity: AudioIdentity): AudioResult<Unit> {
            finalized = true
            return AudioResult.Value(Unit)
        }

        override fun reconcile(identity: AudioIdentity) = AudioResult.Value(Unit)
    }

    private val identity =
        AudioIdentity(
            RecordingId(UUID.randomUUID().toString()),
            AudioAssetId(UUID.randomUUID().toString()),
            UUID.randomUUID().toString(),
        )

    @Test
    fun semanticMetadataWaitsUntilCanonicalFramesAreCommitted() {
        val w = Writer()
        val s = RecordingSession(identity, w)
        s.start()
        s.accept(ByteArray(200))
        val m =
            SegmentationMetadata(
                SegmentationKind.SEMANTIC_CLOSE,
                UUID.randomUUID().toString(),
                0,
                100,
                reason = "STOP",
            )
        s.retainMetadata(m)
        assertTrue(w.metadata.isEmpty())
        s.requestStop()
        s.confirmStop()
        assertTrue(w.metadata.contains(m))
        assertTrue(w.finalized)
        assertNull(s.state.segmentationFailure)
    }

    @Test
    fun metadataFailureDoesNotDiscardPcmOrPreventSaved() {
        val w = Writer()
        w.metadataFailure = true
        val s = RecordingSession(identity, w)
        s.start()
        s.accept(ByteArray(160000))
        s.accept(ByteArray(200))
        s.requestStop()
        s.confirmStop()
        assertEquals(80100L, w.frames)
        assertEquals(RecordingPhase.SAVED, s.state.phase)
        assertEquals(VadFailure.METADATA_FAILED, s.state.segmentationFailure)
    }

    @Test
    fun physicalCapAndOverlapAreDurableSourceViews() {
        val w = Writer()
        val s = RecordingSession(identity, w)
        s.start()
        repeat(121) { s.accept(ByteArray(160000)) }
        s.requestStop()
        s.confirmStop()
        val opens = w.metadata.filter { it.kind == SegmentationKind.TECHNICAL_OPEN }
        val closes = w.metadata.filter { it.kind == SegmentationKind.TECHNICAL_CLOSE }
        assertEquals(2, opens.size)
        assertEquals(2, closes.size)
        assertEquals(9_568_000L, opens.last().overlapFirstFrame)
        assertEquals(9_600_000L, closes.first().endFrame)
        assertEquals("CAP", closes.first().reason)
        assertEquals(opens.first().captureEpochId, opens.last().captureEpochId)
        assertEquals(9_680_000L, w.frames)
    }

    @Test
    fun durabilityPendingCannotCreateFutureMetadata() {
        val tasks = ArrayDeque<Runnable>()
        val w = Writer()
        val s = RecordingSession(identity, w, persistence = Executor { tasks.add(it) })
        s.start()
        s.accept(ByteArray(160000))
        assertTrue(w.metadata.isEmpty())
        assertEquals(0L, w.frames)
        while (tasks.isNotEmpty()) tasks.removeFirst().run()
        assertEquals(80000L, w.frames)
        assertEquals(1, w.metadata.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun futureSourceRangeIsRejected() {
        SegmentationMetadata(
                SegmentationKind.SEMANTIC_CLOSE,
                UUID.randomUUID().toString(),
                0,
                101,
                reason = "STOP",
            )
            .validate(100)
    }
}
