package com.monumentogram.dora.audio.logical

import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.model.alpha.RecordingId
import java.util.Collections

/** Recording scope only. This value grants no access to PCM. */
data class AuthorizationUnitId(val recordingId: RecordingId)

/** Immutable metadata only; constructors are internal to the validated projection. */
@ConsistentCopyVisibility
data class TechnicalChunkReference
internal constructor(
    val chunkId: String,
    val sourceAudioReference: OriginalAudioReference,
    val canonicalFirstFrame: Long,
    val canonicalEndFrame: Long,
    val processingFirstFrame: Long,
    val captureEpochId: String,
    val openReason: String,
    val closeReason: String,
    val profileId: String,
    val profileSha256: String,
) {
    val recordingId: RecordingId
        get() = sourceAudioReference.identity.recordingId

    val processingEndFrame: Long
        get() = canonicalEndFrame

    val overlapBeforeFrames: Long
        get() = canonicalFirstFrame - processingFirstFrame

    fun originalFrame(processingFrame: Long): Long {
        require(processingFrame in 0..(processingEndFrame - processingFirstFrame))
        return Math.addExact(processingFirstFrame, processingFrame)
    }

    /** Preserves subframe timestamps; the final endpoint is allowed, but owns no frame. */
    fun originalNanos(processingNanos: Long): Long {
        require(
            processingNanos in 0..SourceFrameTime.toNanos(processingEndFrame - processingFirstFrame)
        )
        return Math.addExact(SourceFrameTime.toNanos(processingFirstFrame), processingNanos)
    }

    override fun toString() = "TechnicalChunkReference(redacted)"
}

@ConsistentCopyVisibility
data class SemanticSegmentReference
internal constructor(
    val segmentId: String,
    val sourceAudioReference: OriginalAudioReference,
    val firstFrame: Long,
    val endFrame: Long,
    val reason: String,
    val degraded: Boolean,
    val profileId: String,
    val profileSha256: String,
)

data class SourceFrameLookup(
    val canonicalOwner: TechnicalChunkReference,
    val processingViews: List<TechnicalChunkReference>,
)

@ConsistentCopyVisibility
data class LogicalRecordingReference
internal constructor(
    val originalAudioReference: OriginalAudioReference,
    val technicalChunks: List<TechnicalChunkReference>,
    val semanticSegments: List<SemanticSegmentReference>,
    val degradedObservations: List<SegmentationMetadata>,
) {
    val recordingId: RecordingId
        get() = originalAudioReference.identity.recordingId

    val authorizationUnitId: AuthorizationUnitId
        get() = AuthorizationUnitId(recordingId)

    fun lookup(frame: Long): SourceFrameLookup? {
        require(frame >= 0)
        if (frame >= originalAudioReference.frames) return null
        val owner = technicalChunks.single {
            frame in it.canonicalFirstFrame until it.canonicalEndFrame
        }
        return SourceFrameLookup(
            owner,
            immutable(
                technicalChunks.filter {
                    frame in it.processingFirstFrame until it.processingEndFrame
                }
            ),
        )
    }

    fun lookupNanos(nanos: Long): SourceFrameLookup? = lookup(SourceFrameTime.frameAtNanos(nanos))

    override fun toString() = "LogicalRecordingReference(redacted)"
}

sealed interface LogicalRecordingResult {
    data class Ready(val recording: LogicalRecordingReference) : LogicalRecordingResult

    data class NotEvaluated(val source: OriginalAudioReference) : LogicalRecordingResult

    data class Incomplete(val reason: ProjectionFailure) : LogicalRecordingResult

    data class SourceUnavailable(val status: OriginalAudioStatus) : LogicalRecordingResult
}

enum class ProjectionFailure {
    MALFORMED_METADATA,
    INCOMPLETE_PAIRS,
    COVERAGE,
    TRANSITION,
    METADATA_LIMIT,
}

/** Exact 16 kHz time, without wall clock or floating point. Overflow rejects. */
object SourceFrameTime {
    private const val NANOS_PER_FRAME = 62500L

    fun toNanos(frame: Long): Long {
        require(frame >= 0)
        return Math.multiplyExact(frame, NANOS_PER_FRAME)
    }

    fun frameAtNanos(nanos: Long): Long {
        require(nanos >= 0)
        return nanos / NANOS_PER_FRAME
    }
}

internal fun <T> immutable(values: Collection<T>): List<T> =
    Collections.unmodifiableList(values.toList())
