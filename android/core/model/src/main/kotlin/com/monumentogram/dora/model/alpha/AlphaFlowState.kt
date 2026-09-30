package com.monumentogram.dora.model.alpha

enum class CapturePhase {
    STARTING,
    RECORDING,
    PAUSING,
    PAUSED,
    RESUMING,
    STOPPING,
}

sealed interface CaptureState {
    /** Adapter did not prove quiescence; retain control and allow only Stop, never Reset. */
    data class Uncertain(val recordingId: RecordingId, val error: FlowFailure) : CaptureState

    data object Idle : CaptureState

    data class Session(val recordingId: RecordingId, val phase: CapturePhase) : CaptureState

    data class Captured(val source: OriginalAudioRef) : CaptureState

    data class Failed(val recordingId: RecordingId, val error: FlowFailure) : CaptureState
}

sealed interface StorageState {
    data object Empty : StorageState

    data class Saving(val source: OriginalAudioRef) : StorageState

    data class Recorded(val audio: StoredAudio) : StorageState

    data class Failed(val source: OriginalAudioRef, val error: FlowFailure) : StorageState
}

sealed interface RecognitionState {
    /** Cancellation failed or is uncertain; retain the job and allow cancellation to be retried. */
    data class CancellationFailed(val request: RecognitionRequest, val error: FlowFailure) :
        RecognitionState

    data object Idle : RecognitionState

    data class Processing(val request: RecognitionRequest) : RecognitionState

    data class Cancelling(val request: RecognitionRequest) : RecognitionState

    data class Cancelled(val request: RecognitionRequest) : RecognitionState

    data class Result(val transcript: TranscriptRef) : RecognitionState

    data class Failed(val request: RecognitionRequest, val error: FlowFailure) : RecognitionState
}

/** Presentation snapshot for one handoff; no final persistence schema or global job queue. */
data class AlphaFlowState(
    val capture: CaptureState = CaptureState.Idle,
    val storage: StorageState = StorageState.Empty,
    val recognition: RecognitionState = RecognitionState.Idle,
)

sealed interface FlowIntent {
    data class Start(val recordingId: RecordingId) : FlowIntent

    data object Pause : FlowIntent

    data object Resume : FlowIntent

    /** Sent after the UI confirms Stop; opening a confirmation dialog is not a Stop. */
    data object Stop : FlowIntent

    data object Save : FlowIntent

    data class Recognize(val jobId: RecognitionJobId) : FlowIntent

    data object CancelRecognition : FlowIntent

    /** Clears a terminal presentation snapshot, never deletes audio or transcript data. */
    data object Reset : FlowIntent
}
