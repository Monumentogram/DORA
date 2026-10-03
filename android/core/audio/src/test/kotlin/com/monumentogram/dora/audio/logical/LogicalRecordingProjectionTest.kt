package com.monumentogram.dora.audio.logical

import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicalRecordingProjectionTest {
    @Test
    fun singleChunkBindsExactSource() {
        val source = source(16000)
        val result = ready(source, rows(16000))
        assertSame(source, result.originalAudioReference)
        assertSame(source, result.technicalChunks.single().sourceAudioReference)
        assertEquals(source.identity.recordingId, result.authorizationUnitId.recordingId)
        assertEquals(id(10), result.technicalChunks.single().chunkId)
    }

    @Test
    fun exactCapNeedsNoEmptySuccessor() {
        assertEquals(1, ready(source(CAP), rows(CAP)).technicalChunks.size)
    }

    @Test
    fun threeRotationsCoverSourceExactly() {
        val result = ready(source(CAP * 3 + 1), rows(CAP * 3 + 1))
        assertEquals(4, result.technicalChunks.size)
        assertEquals(
            listOf(0L, CAP, CAP * 2, CAP * 3),
            result.technicalChunks.map { it.canonicalFirstFrame },
        )
        assertEquals(
            CAP * 3 + 1,
            result.technicalChunks.sumOf { it.canonicalEndFrame - it.canonicalFirstFrame },
        )
    }

    @Test
    fun overlapHasOneOwnerAndTwoViews() {
        val result = ready(source(CAP + 16000), rows(CAP + 16000))
        val chunk = result.technicalChunks[1]
        assertEquals(CAP - 32000, chunk.processingFirstFrame)
        assertEquals(CAP - 32000, chunk.originalFrame(0))
        assertEquals(CAP, chunk.originalFrame(32000))
        assertEquals(32000, chunk.overlapBeforeFrames)
        val found = result.lookup(CAP - 1)!!
        assertEquals(result.technicalChunks[0], found.canonicalOwner)
        assertEquals(2, found.processingViews.size)
        assertEquals(chunk, result.lookup(CAP)!!.canonicalOwner)
        assertNull(result.lookup(CAP + 16000))
        assertThrows(IllegalArgumentException::class.java) { chunk.originalFrame(-1) }
        assertThrows(IllegalArgumentException::class.java) { chunk.originalFrame(48001) }
    }

    @Test
    fun semanticsCrossRotationsAndRetainDegradedState() {
        val metadata =
            rows(CAP * 2 + 1) +
                listOf(
                    SegmentationMetadata(
                        SegmentationKind.SEMANTIC_CLOSE,
                        id(100),
                        0,
                        CAP * 2 + 1,
                        reason = "STOP",
                        degraded = true,
                    ),
                    SegmentationMetadata(
                        SegmentationKind.DEGRADED,
                        id(101),
                        0,
                        512,
                        reason = "COVERAGE_GAP",
                        degraded = true,
                    ),
                )
        val result = ready(source(CAP * 2 + 1), metadata)
        assertEquals(3, result.technicalChunks.size)
        assertEquals(1, result.semanticSegments.size)
        assertTrue(result.semanticSegments.single().degraded)
        assertEquals(1, result.degradedObservations.size)
    }

    @Test
    fun semanticOverlapDoesNotDuplicateCanonicalOwnership() {
        val metadata =
            rows(16000) +
                listOf(
                    SegmentationMetadata(
                        SegmentationKind.SEMANTIC_CLOSE,
                        id(100),
                        0,
                        10000,
                        reason = "SILENCE_90_SECONDS",
                    ),
                    SegmentationMetadata(
                        SegmentationKind.SEMANTIC_CLOSE,
                        id(101),
                        9000,
                        16000,
                        reason = "STOP",
                    ),
                )
        assertEquals(2, ready(source(16000), metadata).semanticSegments.size)
    }

    @Test
    fun pauseResumeKeepsOneTimelineWithoutOverlapAcrossEpochs() {
        val first = pair(id(10), id(10), 0, 16000, "START", "PAUSE")
        val second = pair(id(11), id(11), 16000, 32000, "RESUME", "STOP")
        val result = ready(source(32000), first + second)
        assertEquals(2, result.technicalChunks.size)
        assertEquals(16000, result.technicalChunks[1].processingFirstFrame)
    }

    @Test
    fun pauseAtCapAndStopWhilePausedRemainCompatible() {
        val metadata =
            pair(id(10), id(10), 0, CAP, "START", "CAP") +
                pair(id(11), id(11), CAP, CAP + 1, "RESUME", "PAUSE")
        assertEquals(2, ready(source(CAP + 1), metadata).technicalChunks.size)
    }

    @Test
    fun pauseOneFrameBeforeCapAndResumeCreatesFreshCapOrigin() {
        val metadata =
            pair(id(10), id(10), 0, CAP - 1, "START", "PAUSE") +
                pair(id(11), id(11), CAP - 1, CAP, "RESUME", "STOP")
        assertEquals(2, ready(source(CAP), metadata).technicalChunks.size)
    }

    @Test
    fun stopImmediatelyAfterRotationOwnsOneFinalFrame() {
        assertEquals(
            1L,
            ready(source(CAP + 1), rows(CAP + 1)).technicalChunks.last().canonicalEndFrame - CAP,
        )
    }

    @Test
    fun historicalSourceIsNotEvaluatedWithoutInventedChunks() {
        val result = LogicalRecordingProjection.bind(source(16000), emptyList())
        assertTrue(result is LogicalRecordingResult.NotEvaluated)
        assertEquals(source(16000), (result as LogicalRecordingResult.NotEvaluated).source)
    }

    @Test
    fun noTechnicalRowsIsIncompleteRatherThanHistorical() {
        malformed(
            listOf(
                SegmentationMetadata(
                    SegmentationKind.DEGRADED,
                    id(80),
                    0,
                    0,
                    reason = "COVERAGE_GAP",
                    degraded = true,
                )
            )
        )
    }

    @Test
    fun zeroLengthCloseRejects() {
        malformed(
            rows(16000).map {
                if (it.kind == SegmentationKind.TECHNICAL_CLOSE) it.copy(endFrame = 0) else it
            }
        )
    }

    @Test
    fun duplicateOpenRejects() {
        malformed(rows(16000) + rows(16000).first())
    }

    @Test
    fun duplicateCloseRejects() {
        malformed(rows(16000) + rows(16000).last())
    }

    @Test
    fun missingCloseRejects() {
        malformed(rows(16000).take(1))
    }

    @Test
    fun missingOpenRejects() {
        malformed(rows(16000).takeLast(1))
    }

    @Test
    fun wrongProfileRejects() {
        malformed(rows(16000).map { it.copy(profileSha256 = "0".repeat(64)) })
    }

    @Test
    fun wrongProfileIdRejects() {
        malformed(rows(16000).map { it.copy(profileId = "other") })
    }

    @Test
    fun outOfRangeRejects() {
        malformed(rows(16001))
    }

    @Test
    fun wrongEpochPairRejects() {
        malformed(
            rows(16000).map {
                if (it.kind == SegmentationKind.TECHNICAL_CLOSE) it.copy(captureEpochId = id(12))
                else it
            }
        )
    }

    @Test
    fun gapRejects() {
        malformed(pair(id(10), id(10), 1, 16000, "START", "STOP"))
    }

    @Test
    fun missingTailRejects() {
        malformed(rows(15999))
    }

    @Test
    fun falseCapRejects() {
        malformed(pair(id(10), id(10), 0, 16000, "START", "CAP"))
    }

    @Test
    fun reusedEpochAfterPauseRejects() {
        malformed(
            pair(id(10), id(10), 0, 8000, "START", "PAUSE") +
                pair(id(11), id(10), 8000, 16000, "RESUME", "STOP")
        )
    }

    @Test
    fun stopCannotHaveSuccessor() {
        malformed(
            pair(id(10), id(10), 0, 8000, "START", "STOP") +
                pair(id(11), id(11), 8000, 16000, "RESUME", "STOP")
        )
    }

    @Test
    fun duplicateCanonicalOwnershipRejects() {
        malformed(rows(16000) + pair(id(11), id(11), 0, 16000, "START", "STOP"))
    }

    @Test
    fun mismatchedOverlapRejects() {
        val metadata =
            rows(CAP + 1).map {
                if (it.kind == SegmentationKind.TECHNICAL_CLOSE && it.firstFrame == CAP)
                    it.copy(overlapFirstFrame = CAP - 1)
                else it
            }
        assertTrue(
            LogicalRecordingProjection.bind(source(CAP + 1), metadata)
                is LogicalRecordingResult.Incomplete
        )
    }

    @Test
    fun missingCapOverlapRejects() {
        val metadata = rows(CAP + 1).map { it.copy(overlapFirstFrame = null) }
        assertTrue(
            LogicalRecordingProjection.bind(source(CAP + 1), metadata)
                is LogicalRecordingResult.Incomplete
        )
    }

    @Test
    fun immutableDefensiveSnapshotAndIdempotentRead() {
        val input = rows(CAP + 1).toMutableList()
        val result = ready(source(CAP + 1), input)
        assertEquals(result, ready(source(CAP + 1), input.reversed()))
        input.clear()
        assertEquals(2, result.technicalChunks.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (result.technicalChunks as MutableList).clear()
        }
    }

    @Test
    fun oneThreeEightHoursHaveStableIdsAndNoOverflow() {
        for (hours in listOf(1, 3, 8)) {
            val frames = hours * 3600L * 16000
            val source = source(frames)
            val result = ready(source, rows(frames))
            assertEquals(hours * 6, result.technicalChunks.size)
            assertEquals(result, ready(source, rows(frames)))
            var next = 0L
            result.technicalChunks.forEach {
                assertSame(source, it.sourceAudioReference)
                assertEquals(next, it.canonicalFirstFrame)
                next = it.canonicalEndFrame
            }
            assertEquals(frames, next)
            assertEquals(hours * 3600L * 1000000000, SourceFrameTime.toNanos(next))
        }
    }

    @Test
    fun exactNanosecondMappingAtAllCriticalFrames() {
        for (frame in listOf(0L, 15999, 16000, CAP - 32000, CAP, CAP + 1, 460800000)) {
            assertEquals(frame, SourceFrameTime.frameAtNanos(SourceFrameTime.toNanos(frame)))
            assertEquals(
                frame,
                SourceFrameTime.frameAtNanos(SourceFrameTime.toNanos(frame) + 62499),
            )
        }
        assertEquals(999937500L, SourceFrameTime.toNanos(15999))
        val result = ready(source(CAP + 1), rows(CAP + 1))
        val chunk = result.technicalChunks[1]
        assertEquals(
            SourceFrameTime.toNanos(CAP),
            chunk.originalNanos(SourceFrameTime.toNanos(32000)),
        )
        assertEquals(result.lookup(CAP), result.lookupNanos(SourceFrameTime.toNanos(CAP)))
        assertThrows(ArithmeticException::class.java) { SourceFrameTime.toNanos(Long.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { SourceFrameTime.frameAtNanos(-1) }
    }

    private fun malformed(metadata: List<SegmentationMetadata>) {
        assertTrue(
            LogicalRecordingProjection.bind(source(16000), metadata)
                is LogicalRecordingResult.Incomplete
        )
    }

    companion object {
        const val CAP = 9600000L

        fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"

        fun source(frames: Long) =
            OriginalAudioReference(
                1,
                AudioIdentity(RecordingId(id(1)), AudioAssetId(id(2)), id(3)),
                "a".repeat(64),
                frames,
            )

        fun ready(source: OriginalAudioReference, rows: List<SegmentationMetadata>) =
            (LogicalRecordingProjection.bind(source, rows) as LogicalRecordingResult.Ready)
                .recording

        @Suppress("LongParameterList") // Mirrors the persisted OPEN/CLOSE fixture fields.
        fun pair(
            chunk: String,
            epoch: String,
            first: Long,
            end: Long,
            open: String,
            close: String,
            overlap: Long? = null,
        ) =
            listOf(
                SegmentationMetadata(
                    SegmentationKind.TECHNICAL_OPEN,
                    chunk,
                    first,
                    first,
                    epoch,
                    overlap,
                    open,
                ),
                SegmentationMetadata(
                    SegmentationKind.TECHNICAL_CLOSE,
                    chunk,
                    first,
                    end,
                    epoch,
                    overlap,
                    close,
                ),
            )

        fun rows(frames: Long): List<SegmentationMetadata> = buildList {
            var first = 0L
            var ordinal = 0
            while (first < frames) {
                val end = minOf(first + CAP, frames)
                addAll(
                    pair(
                        id(10 + ordinal),
                        id(10),
                        first,
                        end,
                        if (first == 0L) "START" else "CAP",
                        if (end - first == CAP) "CAP" else "STOP",
                        if (first == 0L) null else first - 32000,
                    )
                )
                first = end
                ordinal++
            }
        }
    }
}
