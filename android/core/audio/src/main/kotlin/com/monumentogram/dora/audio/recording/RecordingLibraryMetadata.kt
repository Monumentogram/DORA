package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity

/**
 * Journal state for display. It does not assert current encrypted-audio integrity or availability.
 */
enum class RecordingLibraryState {
    FINALIZED,
    NEEDS_RECOVERY,
    DELETION_PENDING,
    DELETED,
    UNAVAILABLE,
}

/** Display-only journal projection; never an authenticated read summary or resume capability. */
data class RecordingLibraryMetadata
internal constructor(
    val identity: AudioIdentity?,
    val committedFrames: Long,
    val state: RecordingLibraryState,
    val failure: AudioFailure? = null,
    val cursor: String = identity?.assetId?.value.orEmpty(),
) {
    val canResume: Boolean
        get() = false

    override fun toString(): String = "RecordingLibraryMetadata(redacted)"
}
