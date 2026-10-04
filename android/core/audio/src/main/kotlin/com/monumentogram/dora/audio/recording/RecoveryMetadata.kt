package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.audio.StoredAudioSegment

/**
 * Pure plan over already authenticated canonical units; no audio, random IDs or final reference.
 */
internal object RecoveryMetadata {
    data class Plan(
        val additions: List<SegmentationMetadata>,
        val technical: RecoveryMetadataState,
        val semantic: RecoveryMetadataState,
        val continuationSafe: Boolean = true,
    )

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun plan(
        units: List<StoredAudioSegment>,
        rows: List<SegmentationMetadata>,
        frames: Long,
    ): Plan {
        require(units.sumOf { it.frames } == frames)
        rows.forEach { it.validate(frames) }
        require(rows.map { it.key }.distinct().size == rows.size)
        if (rows.isEmpty())
            return Plan(
                emptyList(),
                RecoveryMetadataState.NOT_EVALUATED,
                RecoveryMetadataState.NOT_EVALUATED,
            )
        val groups = units.groupBy { it.identity.physicalSegmentId }
        val opens =
            rows.filter { it.kind == SegmentationKind.TECHNICAL_OPEN }.sortedBy { it.firstFrame }
        val closes =
            rows.filter { it.kind == SegmentationKind.TECHNICAL_CLOSE }.associateBy { it.segmentId }
        val aborts =
            rows.filter { it.kind == SegmentationKind.TECHNICAL_ABORT }.associateBy { it.segmentId }
        val missingGroups = groups.filterKeys { key -> opens.none { it.segmentId == key } }
        val firstKnownFrame = opens.minOfOrNull { it.firstFrame }
        val missingKnownHistory =
            (rows.any { it.kind == SegmentationKind.RECORDING_ORIGIN } &&
                missingGroups.isNotEmpty()) ||
                (firstKnownFrame != null &&
                    missingGroups.values.any {
                        it.first().identity.firstFrame >= firstKnownFrame
                    })
        require(closes.keys.all { key -> opens.any { it.segmentId == key } })
        require(aborts.keys.all { key -> opens.any { it.segmentId == key } })
        val additions = mutableListOf<SegmentationMetadata>()
        opens.forEach { open ->
            val group = groups[open.segmentId].orEmpty()
            val close = closes[open.segmentId]
            val abort = aborts[open.segmentId]
            require(close == null || abort == null)
            if (group.isEmpty()) {
                require(close == null)
                val expected =
                    open.copy(kind = SegmentationKind.TECHNICAL_ABORT, reason = "RECOVERY")
                if (abort == null) {
                    require(open.firstFrame == frames)
                    additions += expected
                } else require(abort == expected)
            } else {
                require(
                    abort == null && group.first().identity.physicalFirstFrame == open.firstFrame
                )
                require(group.first().identity.firstFrame == open.firstFrame)
                val end = group.last().identity.firstFrame + group.last().frames
                when {
                    close != null -> {
                        require(close.firstFrame == open.firstFrame && close.endFrame == end)
                        require(
                            close.captureEpochId == open.captureEpochId &&
                                close.overlapFirstFrame == open.overlapFirstFrame
                        )
                    }
                    end == frames -> {
                        additions +=
                            open.copy(
                                kind = SegmentationKind.TECHNICAL_CLOSE,
                                endFrame = end,
                                reason = "RECOVERY",
                            )
                    }
                    else ->
                        require(missingKnownHistory) // Retain unknown historical close evidence.
                }
            }
        }
        // An open semantic interval was process-local. Record uncertainty, never a normal end.
        opens
            .maxWithOrNull(
                compareBy<SegmentationMetadata>(
                    { it.firstFrame },
                    { open ->
                        groups[open.segmentId]?.lastOrNull()?.let {
                            it.identity.firstFrame + it.frames
                        } ?: open.endFrame
                    },
                    { it.segmentId },
                )
            )
            ?.let { last ->
                val marker =
                    SegmentationMetadata(
                        SegmentationKind.RECOVERY_INTERRUPTED,
                        last.segmentId,
                        last.firstFrame,
                        groups[last.segmentId]?.lastOrNull()?.let {
                            it.identity.firstFrame + it.frames
                        } ?: last.endFrame,
                        reason = "RECOVERY",
                        degraded = true,
                    )
                val old = rows.singleOrNull { it.key == marker.key }
                if (old == null) additions += marker else require(old == marker)
            }
        val missing = missingGroups.isNotEmpty()
        if (!missing && frames > 0)
            com.monumentogram.dora.audio.logical.LogicalRecordingProjection.technicalPairs(
                frames,
                rows + additions,
            )
        return Plan(
            additions.toList(),
            if (missing) RecoveryMetadataState.INCOMPLETE else RecoveryMetadataState.INTERRUPTED,
            RecoveryMetadataState.INTERRUPTED,
            continuationSafe = !missingKnownHistory,
        )
    }
}
