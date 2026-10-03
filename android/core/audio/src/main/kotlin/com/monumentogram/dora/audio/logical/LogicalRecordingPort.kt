package com.monumentogram.dora.audio.logical

import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioPort
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.SegmentationMetadata

fun interface LogicalRecordingPort {
    /** Exact source only. Returned provenance grants no authority for subsequent PCM work. */
    fun read(reference: OriginalAudioReference): AudioResult<LogicalRecordingResult>
}

/** The callback reads the existing journal directly under withAvailable's held catalog lease. */
internal class AuthenticatedLogicalRecordings(
    private val originals: OriginalAudioPort,
    private val readPageHeld: (OriginalAudioReference, String) -> List<SegmentationMetadata>,
) : LogicalRecordingPort {
    override fun read(reference: OriginalAudioReference): AudioResult<LogicalRecordingResult> {
        var projected: LogicalRecordingResult? = null
        val checked =
            originals.withAvailable(reference) {
                projected =
                    try {
                        readHeld(reference)
                    } catch (_: com.monumentogram.dora.audio.InvalidSegmentationMetadata) {
                        LogicalRecordingResult.Incomplete(ProjectionFailure.MALFORMED_METADATA)
                    }
            }
        return when (checked) {
            is AudioResult.Failed -> checked
            is AudioResult.Value -> {
                val status = checked.value
                if (status is OriginalAudioStatus.Available && status.reference == reference)
                    AudioResult.Value(checkNotNull(projected))
                else AudioResult.Value(LogicalRecordingResult.SourceUnavailable(status))
            }
        }
    }

    // Each early return discards the entire accumulated projection on a distinct journal failure.
    @Suppress("ReturnCount")
    private fun readHeld(reference: OriginalAudioReference): LogicalRecordingResult {
        val rows = mutableListOf<SegmentationMetadata>()
        var after = ""
        while (true) {
            val page = readPageHeld(reference, after)
            if (page.isEmpty()) return LogicalRecordingProjection.bind(reference, rows)
            for (row in page) {
                if (row.key <= after)
                    return LogicalRecordingResult.Incomplete(ProjectionFailure.MALFORMED_METADATA)
                after = row.key
                rows += row
                if (rows.size > MAX_METADATA_ROWS)
                    return LogicalRecordingResult.Incomplete(ProjectionFailure.METADATA_LIMIT)
            }
        }
    }

    private companion object {
        const val MAX_METADATA_ROWS = 65536
    }
}
