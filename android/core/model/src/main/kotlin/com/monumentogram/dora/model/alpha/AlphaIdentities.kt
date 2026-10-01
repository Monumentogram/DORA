package com.monumentogram.dora.model.alpha

private val opaqueIdPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

private fun validateId(value: String) {
    require(opaqueIdPattern.matches(value)) { "Expected a canonical opaque UUID" }
}

@JvmInline
value class RecordingId(val value: String) {
    init {
        validateId(value)
    }
}

/** Identity of one immutable original-audio version, independent of its physical storage. */
@JvmInline
value class AudioAssetId(val value: String) {
    init {
        validateId(value)
    }
}

@JvmInline
value class RecognitionJobId(val value: String) {
    init {
        validateId(value)
    }
}

/** The frozen ASR contract's result-version identity; not an active transcript pointer. */
@JvmInline
value class TranscriptId(val value: String) {
    init {
        validateId(value)
    }
}

data class OriginalAudioRef(val recordingId: RecordingId, val assetId: AudioAssetId)

/** An adapter acknowledgment of durable, validated original audio, not a file path. */
data class StoredAudio(val source: OriginalAudioRef)

data class RecognitionRequest(val audio: StoredAudio, val jobId: RecognitionJobId)

/** Cross-stage link only. It neither carries text nor activates/overwrites a transcript. */
data class TranscriptRef(
    val transcriptId: TranscriptId,
    val source: OriginalAudioRef,
    val jobId: RecognitionJobId,
)
