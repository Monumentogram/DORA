package com.monumentogram.dora.audio

import com.monumentogram.dora.vad.SegmentationProfile
import com.monumentogram.dora.vad.VadFailure
import java.util.UUID

/** Persisted segmentation decoding failed; does not classify canonical audio as unavailable. */
internal class InvalidSegmentationMetadata :
    IllegalArgumentException("INVALID_SEGMENTATION_METADATA")

enum class SegmentationKind {
    TECHNICAL_OPEN,
    TECHNICAL_CLOSE,
    SEMANTIC_CLOSE,
    DEGRADED,
    TECHNICAL_ABORT,
    RECOVERY_INTERRUPTED,
}

/** Content-free immutable source views, inside the existing encrypted journal only. */
data class SegmentationMetadata(
    val kind: SegmentationKind,
    val segmentId: String,
    val firstFrame: Long,
    val endFrame: Long,
    val captureEpochId: String? = null,
    val overlapFirstFrame: Long? = null,
    val reason: String,
    val degraded: Boolean = false,
    val profileId: String = SegmentationProfile.FROZEN.id,
    val profileSha256: String = SegmentationProfile.FROZEN.sha256,
) {
    val key: String
        get() = "${kind.name}:$segmentId"

    @Suppress("CyclomaticComplexMethod") // Keep every persisted range/identity invariant explicit.
    fun validate(committedFrames: Long) {
        require(UUID.fromString(segmentId).toString() == segmentId)
        require(firstFrame >= 0 && endFrame >= firstFrame && endFrame <= committedFrames)
        require(
            profileId == SegmentationProfile.FROZEN.id &&
                profileSha256 == SegmentationProfile.FROZEN.sha256
        )
        require(reason in REASONS)
        if (
            kind in
                setOf(
                    SegmentationKind.TECHNICAL_OPEN,
                    SegmentationKind.TECHNICAL_CLOSE,
                    SegmentationKind.TECHNICAL_ABORT,
                )
        ) {
            require(
                captureEpochId != null &&
                    UUID.fromString(captureEpochId).toString() == captureEpochId
            )
            require(endFrame - firstFrame <= SegmentationProfile.FROZEN.technicalCapFrames)
            require(
                overlapFirstFrame == null ||
                    overlapFirstFrame in
                        maxOf(0, firstFrame - SegmentationProfile.FROZEN.overlapFrames) until
                            firstFrame
            )
            if (kind == SegmentationKind.TECHNICAL_OPEN) {
                require(firstFrame == endFrame && reason in setOf("START", "RESUME", "CAP"))
            } else if (kind == SegmentationKind.TECHNICAL_ABORT) {
                require(firstFrame == endFrame && reason == "RECOVERY")
            } else {
                require(
                    endFrame > firstFrame && reason in setOf("CAP", "PAUSE", "STOP", "RECOVERY")
                )
                if (reason == "CAP")
                    require(endFrame - firstFrame == SegmentationProfile.FROZEN.technicalCapFrames)
            }
        } else {
            require(captureEpochId == null && overlapFirstFrame == null)
            if (kind == SegmentationKind.SEMANTIC_CLOSE)
                require(endFrame > firstFrame && reason in setOf("STOP", "SILENCE_90_SECONDS"))
            else if (kind == SegmentationKind.RECOVERY_INTERRUPTED)
                require(degraded && reason == "RECOVERY")
            else require(degraded && reason in VadFailure.entries.map { it.name })
        }
    }

    companion object {
        private val REASONS =
            setOf("START", "RESUME", "CAP", "PAUSE", "STOP", "SILENCE_90_SECONDS", "RECOVERY") +
                VadFailure.entries.map { it.name }
    }
}
