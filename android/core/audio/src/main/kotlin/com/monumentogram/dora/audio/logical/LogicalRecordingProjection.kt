package com.monumentogram.dora.audio.logical

import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.vad.SegmentationProfile

/** Called only with the exact authenticated final source and rows read under its catalog lease. */
internal object LogicalRecordingProjection {
    fun bind(
        source: OriginalAudioReference,
        metadata: List<SegmentationMetadata>,
    ): LogicalRecordingResult =
        try {
            require(
                source.version == 1 &&
                    source.frames > 0 &&
                    source.digest.matches(Regex("[0-9a-f]{64}"))
            )
            val rows = metadata.toList()
            rows.forEach { it.validate(source.frames) }
            require(rows.map { it.key }.distinct().size == rows.size)
            if (rows.isEmpty()) LogicalRecordingResult.NotEvaluated(source)
            else project(source, rows)
        } catch (failure: InvalidProjection) {
            LogicalRecordingResult.Incomplete(failure.failure)
        } catch (_: IllegalArgumentException) {
            LogicalRecordingResult.Incomplete(ProjectionFailure.MALFORMED_METADATA)
        }

    private fun project(
        source: OriginalAudioReference,
        rows: List<SegmentationMetadata>,
    ): LogicalRecordingResult {
        val chunks =
            technicalPairs(source.frames, rows).map { (open, close) ->
                TechnicalChunkReference(
                    open.segmentId,
                    source,
                    open.firstFrame,
                    close.endFrame,
                    open.overlapFirstFrame ?: open.firstFrame,
                    checkNotNull(open.captureEpochId),
                    open.reason,
                    close.reason,
                    open.profileId,
                    open.profileSha256,
                )
            }
        val degraded =
            rows
                .filter {
                    it.kind in
                        setOf(SegmentationKind.DEGRADED, SegmentationKind.RECOVERY_INTERRUPTED)
                }
                .sortedWith(compareBy({ it.firstFrame }, { it.segmentId }))
        return LogicalRecordingResult.Ready(
            LogicalRecordingReference(
                source,
                immutable(chunks),
                immutable(semantics(source, rows)),
                immutable(degraded),
            )
        )
    }

    /** Same validation for a recovery observation; never requires a provisional final reference. */
    internal fun technicalPairs(
        frames: Long,
        rows: List<SegmentationMetadata>,
    ): List<Pair<SegmentationMetadata, SegmentationMetadata>> {
        rows.forEach { it.validate(frames) }
        require(rows.map { it.key }.distinct().size == rows.size)
        val allOpens = rows.filter { it.kind == SegmentationKind.TECHNICAL_OPEN }
        val aborted = rows.filter { it.kind == SegmentationKind.TECHNICAL_ABORT }
        validateAborts(rows, allOpens, aborted)
        val opens =
            allOpens
                .filter { open -> aborted.none { it.segmentId == open.segmentId } }
                .sortedBy { it.firstFrame }
        val closes =
            rows.filter { it.kind == SegmentationKind.TECHNICAL_CLOSE }.associateBy { it.segmentId }
        requireProjection(
            opens.isNotEmpty() && opens.size == closes.size && opens.all { it.segmentId in closes },
            ProjectionFailure.INCOMPLETE_PAIRS,
        )
        val pairs = mutableListOf<Pair<SegmentationMetadata, SegmentationMetadata>>()
        val epochs = mutableSetOf<String>()
        var next = 0L
        var epochStart = 0L
        for (open in opens) {
            val close = closes.getValue(open.segmentId)
            require(
                close.firstFrame == open.firstFrame &&
                    close.captureEpochId == open.captureEpochId &&
                    close.overlapFirstFrame == open.overlapFirstFrame
            )
            require(!open.degraded && !close.degraded)
            requireProjection(open.firstFrame == next, ProjectionFailure.COVERAGE)
            val previous = pairs.lastOrNull()?.second
            val interruptedStop =
                previous != null &&
                    rows.any {
                        it.kind == SegmentationKind.RECOVERY_INTERRUPTED &&
                            it.segmentId == previous.segmentId &&
                            it.firstFrame == previous.firstFrame &&
                            it.endFrame == previous.endFrame
                    }
            epochStart = validateTransition(open, previous, epochs, epochStart, interruptedStop)
            pairs += open to close
            next = close.endFrame
        }
        requireProjection(next == frames, ProjectionFailure.COVERAGE)
        require(
            aborted.all { abort ->
                abort.firstFrame == frames || opens.any { it.firstFrame == abort.firstFrame }
            }
        )
        return pairs
    }

    private fun validateAborts(
        rows: List<SegmentationMetadata>,
        allOpens: List<SegmentationMetadata>,
        aborted: List<SegmentationMetadata>,
    ) {
        aborted.forEach { abort ->
            val open = requireNotNull(allOpens.singleOrNull { it.segmentId == abort.segmentId })
            require(
                abort == open.copy(kind = SegmentationKind.TECHNICAL_ABORT, reason = "RECOVERY")
            )
            require(
                rows.none {
                    it.kind == SegmentationKind.TECHNICAL_CLOSE && it.segmentId == abort.segmentId
                }
            )
        }
    }

    private class InvalidProjection(val failure: ProjectionFailure) : IllegalArgumentException()

    private fun requireProjection(valid: Boolean, failure: ProjectionFailure) {
        if (!valid) throw InvalidProjection(failure)
    }

    private fun validateTransition(
        open: SegmentationMetadata,
        previous: SegmentationMetadata?,
        epochs: MutableSet<String>,
        epochStart: Long,
        interruptedStop: Boolean,
    ): Long {
        var currentEpochStart = epochStart
        val valid =
            when (open.reason) {
                "START" -> previous == null && open.firstFrame == 0L
                "RESUME" ->
                    previous != null &&
                        (previous.reason in setOf("PAUSE", "CAP", "RECOVERY") ||
                            (previous.reason == "STOP" && interruptedStop)) &&
                        previous.captureEpochId != open.captureEpochId
                "CAP" ->
                    previous != null &&
                        previous.reason == "CAP" &&
                        previous.captureEpochId == open.captureEpochId
                else -> false
            }
        requireProjection(valid, ProjectionFailure.TRANSITION)
        if (open.reason != "CAP") {
            require(
                open.captureEpochId == open.segmentId &&
                    epochs.add(checkNotNull(open.captureEpochId))
            )
            currentEpochStart = open.firstFrame
            require(open.overlapFirstFrame == null)
        } else {
            require(
                open.firstFrame - currentEpochStart >= SegmentationProfile.FROZEN.technicalCapFrames
            )
            require(
                open.overlapFirstFrame ==
                    maxOf(
                        currentEpochStart,
                        open.firstFrame - SegmentationProfile.FROZEN.overlapFrames,
                    )
            )
        }
        return currentEpochStart
    }

    private fun semantics(source: OriginalAudioReference, rows: List<SegmentationMetadata>) =
        rows
            .filter { it.kind == SegmentationKind.SEMANTIC_CLOSE }
            .sortedWith(compareBy({ it.firstFrame }, { it.segmentId }))
            .map {
                SemanticSegmentReference(
                    it.segmentId,
                    source,
                    it.firstFrame,
                    it.endFrame,
                    it.reason,
                    it.degraded,
                    it.profileId,
                    it.profileSha256,
                )
            }
}
