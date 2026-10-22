package com.monumentogram.dora.recording

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.recording.AlphaRecordingLimit

internal data class VerifiedRecording(val identity: AudioIdentity, val frames: Long, val units: Int)

internal object SavedRecordingVerification {
    /** Worker-only full authenticated readback. No PCM is retained, played or exported. */
    @Suppress(
        "ReturnCount"
    ) // Reject incomplete state before streaming, then validate its terminal result.
    fun verify(
        session: ProductAudioSession,
        identity: AudioIdentity,
    ): AudioResult<VerifiedRecording> {
        val acquired = session.originals.acquire(identity)
        if (acquired is AudioResult.Failed) return acquired
        val reference =
            ((acquired as AudioResult.Value).value as? OriginalAudioStatus.Available)?.reference
                ?: return AudioResult.Failed(AudioFailure.INCOMPLETE)
        if (
            reference.identity != identity ||
                reference.frames !in 1..AlphaRecordingLimit.MAX_ALPHA_FRAMES
        )
            return AudioResult.Failed(AudioFailure.INCOMPLETE)
        var frames = 0L
        var units = 0
        var valid = true
        val read =
            session.originals.extract(reference) { first, pcm ->
                try {
                    if (first != frames || pcm.isEmpty() || pcm.size % 2 != 0) valid = false
                    frames += pcm.size.toLong() / 2
                    units++
                    if (frames > reference.frames) valid = false
                } finally {
                    pcm.fill(0)
                }
            }
        if (read is AudioResult.Failed) return read
        val completeRange = valid && units > 0 && frames == reference.frames
        if (
            !completeRange ||
                (read as AudioResult.Value).value != OriginalAudioStatus.Available(reference)
        )
            return AudioResult.Failed(AudioFailure.CORRUPT)
        return AudioResult.Value(VerifiedRecording(identity, frames, units))
    }
}
