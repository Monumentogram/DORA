package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioReadSummary

/** Authenticated observation, not a reusable capability. Resume revalidates the exact source. */
data class RecordingRecovery
internal constructor(
    val identity: AudioIdentity,
    val summary: AudioReadSummary?,
    val failure: AudioFailure?,
    val canResume: Boolean,
    val nextOrdinal: Int,
) {
    override fun toString(): String = "RecordingRecovery(redacted)"
}
