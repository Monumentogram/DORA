package com.monumentogram.dora.model.alpha

enum class Capability {
    AVAILABLE,
    UNAVAILABLE,
}

enum class FlowFailure {
    UNAVAILABLE,
    PERMISSION_DENIED,
    CAPTURE,
    STORAGE,
    SOURCE_UNAVAILABLE,
    RECOGNITION,
    CANCELLED,
    INVALID_STATE,
    INVALID_RESULT,
    INTERNAL,
}

sealed interface PortResult<out T> {
    data class Success<T>(val value: T) : PortResult<T>

    data class Failure(val error: FlowFailure) : PortResult<Nothing>
}

/**
 * Non-blocking ports. Adapters own scheduling/resources and complete once after a factual outcome.
 * No raw exception, content, path or provider payload may cross this boundary. Start includes
 * future permission/disclosure preflight. Stop preserves captured audio. A capture failure must
 * quiesce the session before completing; it never deletes audio.
 */
interface CapturePort {
    val capability: Capability

    fun start(recordingId: RecordingId, complete: (PortResult<Unit>) -> Unit)

    fun pause(recordingId: RecordingId, complete: (PortResult<Unit>) -> Unit)

    fun resume(recordingId: RecordingId, complete: (PortResult<Unit>) -> Unit)

    fun stop(recordingId: RecordingId, complete: (PortResult<OriginalAudioRef>) -> Unit)
}

/** Stage 8 owns audio and checks durable publication/availability; no bytes enter the UI. */
interface OriginalAudioStoragePort {
    val capability: Capability

    fun persist(source: OriginalAudioRef, complete: (PortResult<StoredAudio>) -> Unit)

    fun resolve(source: OriginalAudioRef, complete: (PortResult<StoredAudio>) -> Unit)
}

/**
 * Generic app request, not an engine/provider adapter. Stage 9 must recheck original audio
 * availability and all route-specific admission/consent/ownership gates before work. A request or
 * AVAILABLE capability is never Cloud authorization. Completion references a validated immutable
 * result only; cancellation acknowledges the local job, not remote erasure.
 */
interface RecognitionPort {
    val capability: Capability

    fun recognize(request: RecognitionRequest, complete: (PortResult<TranscriptRef>) -> Unit)

    fun cancel(jobId: RecognitionJobId, complete: (PortResult<Unit>) -> Unit)
}
