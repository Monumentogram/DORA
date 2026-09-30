package com.monumentogram.dora.flow

import com.monumentogram.dora.model.alpha.AlphaFlowState
import com.monumentogram.dora.model.alpha.Capability
import com.monumentogram.dora.model.alpha.CapturePhase
import com.monumentogram.dora.model.alpha.CapturePort
import com.monumentogram.dora.model.alpha.CaptureState
import com.monumentogram.dora.model.alpha.FlowFailure
import com.monumentogram.dora.model.alpha.FlowIntent
import com.monumentogram.dora.model.alpha.OriginalAudioStoragePort
import com.monumentogram.dora.model.alpha.PortResult
import com.monumentogram.dora.model.alpha.RecognitionPort
import com.monumentogram.dora.model.alpha.RecognitionRequest
import com.monumentogram.dora.model.alpha.RecognitionState
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.model.alpha.StorageState

/**
 * One in-memory handoff. Ports must return immediately and own asynchronous work. Dispatch and
 * completion are serialized; snapshots are immutable. Operation tickets reject duplicate/stale
 * callbacks. This is not restart recovery, a queue or transcript persistence.
 */
class AlphaFlowCoordinator(
    private val capture: CapturePort,
    private val storage: OriginalAudioStoragePort,
    private val recognition: RecognitionPort,
) {
    @Volatile
    var state = AlphaFlowState()
        private set

    private var generation = 0L

    /** Rejected intents leave the current state and in-flight operation unchanged. */
    @Synchronized
    fun dispatch(intent: FlowIntent): FlowFailure? =
        when (intent) {
            is FlowIntent.Start -> start(intent)
            FlowIntent.Pause,
            FlowIntent.Resume -> controlCapture(intent)
            FlowIntent.Stop -> stop()
            FlowIntent.Save -> save()
            is FlowIntent.Recognize -> recognize(intent)
            FlowIntent.CancelRecognition -> cancel()
            FlowIntent.Reset -> reset()
        }

    private fun start(intent: FlowIntent.Start): FlowFailure? {
        if (state != AlphaFlowState()) return FlowFailure.INVALID_STATE
        state =
            state.copy(capture = CaptureState.Session(intent.recordingId, CapturePhase.STARTING))
        launch<Unit>(capture.capability, { capture.start(intent.recordingId, it) }) { result ->
            state =
                state.copy(
                    capture =
                        when (result) {
                            is PortResult.Success ->
                                CaptureState.Session(intent.recordingId, CapturePhase.RECORDING)
                            is PortResult.Failure ->
                                captureFailure(intent.recordingId, result.error)
                        }
                )
        }
        return null
    }

    private fun controlCapture(intent: FlowIntent): FlowFailure? {
        val session = state.capture as? CaptureState.Session
        val pause = intent == FlowIntent.Pause
        val required = if (pause) CapturePhase.RECORDING else CapturePhase.PAUSED
        if (session == null || session.phase != required) return FlowFailure.INVALID_STATE
        val pending = if (pause) CapturePhase.PAUSING else CapturePhase.RESUMING
        val completed = if (pause) CapturePhase.PAUSED else CapturePhase.RECORDING
        state = state.copy(capture = session.copy(phase = pending))
        launch<Unit>(
            Capability.AVAILABLE,
            {
                if (pause) capture.pause(session.recordingId, it)
                else capture.resume(session.recordingId, it)
            },
        ) { result ->
            state =
                state.copy(
                    capture =
                        when (result) {
                            is PortResult.Success -> session.copy(phase = completed)
                            is PortResult.Failure ->
                                captureFailure(session.recordingId, result.error)
                        }
                )
        }
        return null
    }

    private fun stop(): FlowFailure? {
        val session =
            when (val current = state.capture) {
                is CaptureState.Session ->
                    current.takeIf {
                        it.phase in setOf(CapturePhase.RECORDING, CapturePhase.PAUSED)
                    }
                is CaptureState.Uncertain ->
                    CaptureState.Session(current.recordingId, CapturePhase.STOPPING)
                else -> null
            }
        if (session == null) return FlowFailure.INVALID_STATE
        state = state.copy(capture = session.copy(phase = CapturePhase.STOPPING))
        launch(Capability.AVAILABLE, { capture.stop(session.recordingId, it) }) { result ->
            state =
                state.copy(
                    capture =
                        when (result) {
                            is PortResult.Success ->
                                if (result.value.recordingId == session.recordingId) {
                                    CaptureState.Captured(result.value)
                                } else
                                    CaptureState.Uncertain(
                                        session.recordingId,
                                        FlowFailure.INVALID_RESULT,
                                    )
                            is PortResult.Failure ->
                                captureFailure(session.recordingId, result.error)
                        }
                )
        }
        return null
    }

    private fun save(): FlowFailure? {
        val captured = state.capture as? CaptureState.Captured
        val canSave = state.storage == StorageState.Empty || state.storage is StorageState.Failed
        if (captured == null || !canSave) return FlowFailure.INVALID_STATE
        state = state.copy(storage = StorageState.Saving(captured.source))
        launch(storage.capability, { storage.persist(captured.source, it) }) { result ->
            state =
                state.copy(
                    storage =
                        when (result) {
                            is PortResult.Success ->
                                if (result.value.source == captured.source) {
                                    StorageState.Recorded(result.value)
                                } else
                                    StorageState.Failed(captured.source, FlowFailure.INVALID_RESULT)
                            is PortResult.Failure ->
                                StorageState.Failed(captured.source, result.error)
                        }
                )
        }
        return null
    }

    private fun recognize(intent: FlowIntent.Recognize): FlowFailure? {
        val recorded = state.storage as? StorageState.Recorded
        if (recorded == null || state.recognition != RecognitionState.Idle)
            return FlowFailure.INVALID_STATE
        val request = RecognitionRequest(recorded.audio, intent.jobId)
        state = state.copy(recognition = RecognitionState.Processing(request))
        launch(recognition.capability, { recognition.recognize(request, it) }) { result ->
            state =
                state.copy(
                    recognition =
                        when (result) {
                            is PortResult.Success ->
                                if (
                                    result.value.source == request.audio.source &&
                                        result.value.jobId == request.jobId
                                ) {
                                    RecognitionState.Result(result.value)
                                } else RecognitionState.Failed(request, FlowFailure.INVALID_RESULT)
                            is PortResult.Failure -> RecognitionState.Failed(request, result.error)
                        }
                )
        }
        return null
    }

    private fun cancel(): FlowFailure? {
        val request =
            when (val current = state.recognition) {
                is RecognitionState.Processing -> current.request
                is RecognitionState.CancellationFailed -> current.request
                else -> null
            } ?: return FlowFailure.INVALID_STATE
        state = state.copy(recognition = RecognitionState.Cancelling(request))
        launch<Unit>(
            Capability.AVAILABLE,
            { recognition.cancel(request.jobId, it) },
        ) { result ->
            state =
                state.copy(
                    recognition =
                        when (result) {
                            is PortResult.Success -> RecognitionState.Cancelled(request)
                            is PortResult.Failure ->
                                RecognitionState.CancellationFailed(request, result.error)
                        }
                )
        }
        return null
    }

    private fun reset(): FlowFailure? {
        val unsavedAudio =
            state.capture is CaptureState.Captured && state.storage !is StorageState.Recorded
        val captureBusy =
            state.capture is CaptureState.Session ||
                state.capture is CaptureState.Uncertain ||
                unsavedAudio
        val recognitionBusy =
            when (state.recognition) {
                is RecognitionState.Processing,
                is RecognitionState.Cancelling,
                is RecognitionState.CancellationFailed -> true
                else -> false
            }
        if (captureBusy || state.storage is StorageState.Saving || recognitionBusy)
            return FlowFailure.INVALID_STATE
        generation++
        state = AlphaFlowState()
        return null
    }

    private fun <T> launch(
        capability: Capability,
        operation: ((PortResult<T>) -> Unit) -> Unit,
        complete: (PortResult<T>) -> Unit,
    ) {
        val ticket = ++generation
        val guarded: (PortResult<T>) -> Unit = { result ->
            synchronized(this) {
                if (ticket == generation) {
                    generation++
                    complete(result)
                }
            }
        }
        if (capability == Capability.UNAVAILABLE) {
            guarded(PortResult.Failure(FlowFailure.UNAVAILABLE))
        } else {
            try {
                operation(guarded)
            } catch (_: RuntimeException) {
                guarded(PortResult.Failure(FlowFailure.INTERNAL))
            }
        }
    }
}

private fun captureFailure(recordingId: RecordingId, error: FlowFailure): CaptureState =
    if (error == FlowFailure.INTERNAL) CaptureState.Uncertain(recordingId, error)
    else CaptureState.Failed(recordingId, error)
