package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.AudioCompletion
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioReadSummary
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.audio.StoredAudioAsset
import com.monumentogram.dora.audio.StoredAudioSegment
import com.monumentogram.dora.audio.logical.LogicalRecordingProjection
import com.monumentogram.dora.audio.logical.LogicalRecordingProjectionTest.Companion.id
import com.monumentogram.dora.audio.logical.LogicalRecordingProjectionTest.Companion.pair
import com.monumentogram.dora.audio.logical.LogicalRecordingProjectionTest.Companion.source
import com.monumentogram.dora.audio.logical.LogicalRecordingResult
import com.monumentogram.dora.audio.persistence.RecordingRecoveryReader
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicalRecoveryContractTest {
    @Test
    fun preCrashSilenceDoesNotBecomePostRecoveryBoundary() {
        val events = mutableListOf<com.monumentogram.dora.vad.SemanticEvent>()
        val profile = com.monumentogram.dora.vad.SegmentationProfile.FROZEN
        val before = com.monumentogram.dora.vad.SegmentationReducer(profile, 0, 1, events::add)
        before.observe(
            com.monumentogram.dora.vad.VadObservation(
                com.monumentogram.dora.vad.FrameRange(0, 16000),
                generation = 1,
                speech = true,
            )
        )
        before.observe(
            com.monumentogram.dora.vad.VadObservation(
                com.monumentogram.dora.vad.FrameRange(16000, 1440000),
                generation = 1,
                speech = false,
            )
        )
        assertTrue(events.none { it is com.monumentogram.dora.vad.SemanticEvent.Closed })
        events.clear()
        val after = com.monumentogram.dora.vad.SegmentationReducer(profile, 1440000, 1, events::add)
        after.observe(
            com.monumentogram.dora.vad.VadObservation(
                com.monumentogram.dora.vad.FrameRange(1440000, 1456000),
                generation = 1,
                speech = false,
            )
        )
        after.stop(1456000)
        assertTrue(events.isEmpty())
    }

    @Test
    fun temporaryTailKeyFailureCannotResumeFromAnOlderObservation() {
        val identity = source(160).identity
        val unit =
            StoredAudioSegment(
                AudioStorageUnitIdentity(identity, id(20), 0, 0),
                160,
                Sha256Value.ZERO,
            )
        val asset = StoredAudioAsset(identity, listOf(unit))
        val exact = AudioReadSummary(identity, 160, AudioCompletion.PARTIAL_RECOVERED)
        val strict = AudioResult.Value(exact)
        val old = exact.copy(frames = 80, tailFailure = AudioFailure.KEY_UNAVAILABLE)
        assertFalse(RecordingRecoveryReader.cleanPrefix(asset, strict, old))
        assertFalse(RecordingRecoveryReader.cleanPrefix(asset, strict, null))
        assertFalse(RecordingRecoveryReader.cleanPrefix(asset, strict, exact.copy(frames = 80)))
        assertTrue(RecordingRecoveryReader.cleanPrefix(asset, strict, exact))
    }

    @Test
    fun orphanAbortIsMetadataIncompleteNotSourceFailure() {
        val orphan =
            SegmentationMetadata(
                SegmentationKind.TECHNICAL_ABORT,
                id(99),
                0,
                0,
                id(99),
                reason = "RECOVERY",
            )
        val result =
            LogicalRecordingProjection.bind(
                source(80000),
                pair(id(10), id(10), 0, 80000, "START", "STOP") + orphan,
            )
        assertTrue(result is LogicalRecordingResult.Incomplete)
    }

    @Test
    fun stopBeforeFinalizationCanResumeOnlyWithExplicitRecoveryEvidence() {
        val rows =
            pair(id(10), id(10), 0, 80000, "START", "STOP") +
                pair(id(11), id(11), 80000, 160000, "RESUME", "STOP")
        assertTrue(
            LogicalRecordingProjection.bind(source(160000), rows)
                is LogicalRecordingResult.Incomplete
        )
        val boundary =
            SegmentationMetadata(
                SegmentationKind.RECOVERY_INTERRUPTED,
                id(10),
                0,
                80000,
                reason = "RECOVERY",
                degraded = true,
            )
        assertTrue(
            LogicalRecordingProjection.bind(source(160000), rows + boundary)
                is LogicalRecordingResult.Ready
        )
    }

    @Test
    fun interruptedChunkThenExplicitResumeCoversOneFinalSource() {
        val rows =
            pair(id(10), id(10), 0, 80000, "START", "RECOVERY") +
                pair(id(11), id(11), 80000, 160000, "RESUME", "STOP")
        val result = LogicalRecordingProjection.bind(source(160000), rows)
        assertTrue(result is LogicalRecordingResult.Ready)
        val recording = (result as LogicalRecordingResult.Ready).recording
        assertEquals(listOf(0L, 80000L), recording.technicalChunks.map { it.canonicalFirstFrame })
        assertEquals(source(160000), recording.originalAudioReference)
        assertEquals(source(160000).identity.recordingId, recording.authorizationUnitId.recordingId)
    }

    @Test
    fun technicalOpenIsDurableBeforeFirstCanonicalAppend() {
        val operations = mutableListOf<String>()
        val writer =
            object : ProductAudioWriterPort {
                override fun create(identity: AudioIdentity) = AudioResult.Value(Unit)

                override fun segmentation(
                    identity: AudioIdentity,
                    metadata: SegmentationMetadata,
                ): AudioResult<Unit> {
                    operations += metadata.kind.name
                    return AudioResult.Value(Unit)
                }

                override fun append(
                    segment: AudioStorageUnitIdentity,
                    format: AudioFormat,
                    pcm: ByteArray,
                ): AudioResult<Unit> {
                    operations += "APPEND"
                    return AudioResult.Value(Unit)
                }

                override fun finalize(identity: AudioIdentity) = AudioResult.Value(Unit)

                override fun reconcile(identity: AudioIdentity) = AudioResult.Value(Unit)
            }
        val session = RecordingSession(source(1).identity, writer)
        session.start()
        session.accept(ByteArray(160000))
        assertEquals(listOf("TECHNICAL_OPEN", "APPEND"), operations)
    }
}
