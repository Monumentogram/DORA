package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.AudioCompletion
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioReadSummary

enum class RecordingCompletionState {
    FINALIZED,
    RECOVERABLE_PARTIAL,
    PARTIAL_NOT_RESUMABLE,
    SOURCE_CORRUPT,
    SOURCE_UNAVAILABLE,
    DELETION_PENDING,
    DELETED,
}

enum class RecoveryMetadataState {
    NOT_EVALUATED,
    COMPLETE,
    INTERRUPTED,
    INCOMPLETE,
    MALFORMED,
}

/** Authenticated observation, not a reusable capability. Resume revalidates the exact source. */
data class RecordingRecovery
internal constructor(
    val identity: AudioIdentity?,
    val summary: AudioReadSummary?,
    val failure: AudioFailure?,
    val canResume: Boolean,
    val nextOrdinal: Int,
    val completionState: RecordingCompletionState =
        when {
            summary?.completion == AudioCompletion.FINALIZED -> RecordingCompletionState.FINALIZED
            summary != null && canResume -> RecordingCompletionState.RECOVERABLE_PARTIAL
            summary != null -> RecordingCompletionState.PARTIAL_NOT_RESUMABLE
            failure in setOf(AudioFailure.CORRUPT, AudioFailure.AUTHENTICATION_FAILED) ->
                RecordingCompletionState.SOURCE_CORRUPT
            else -> RecordingCompletionState.SOURCE_UNAVAILABLE
        },
    val technicalMetadataState: RecoveryMetadataState = RecoveryMetadataState.NOT_EVALUATED,
    val semanticMetadataState: RecoveryMetadataState = RecoveryMetadataState.NOT_EVALUATED,
    val cursor: String = identity?.assetId?.value.orEmpty(),
) {
    val recoveredFrames: Long
        get() = summary?.frames ?: 0

    val tailFailure: AudioFailure?
        get() = summary?.tailFailure ?: failure

    override fun toString(): String = "RecordingRecovery(redacted)"
}
