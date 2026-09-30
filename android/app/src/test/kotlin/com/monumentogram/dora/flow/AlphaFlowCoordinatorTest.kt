package com.monumentogram.dora.flow

import com.monumentogram.dora.model.alpha.AlphaFlowState
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.Capability
import com.monumentogram.dora.model.alpha.CapturePhase
import com.monumentogram.dora.model.alpha.CapturePort
import com.monumentogram.dora.model.alpha.CaptureState
import com.monumentogram.dora.model.alpha.FlowFailure
import com.monumentogram.dora.model.alpha.FlowIntent
import com.monumentogram.dora.model.alpha.OriginalAudioRef
import com.monumentogram.dora.model.alpha.OriginalAudioStoragePort
import com.monumentogram.dora.model.alpha.PortResult
import com.monumentogram.dora.model.alpha.RecognitionJobId
import com.monumentogram.dora.model.alpha.RecognitionPort
import com.monumentogram.dora.model.alpha.RecognitionRequest
import com.monumentogram.dora.model.alpha.RecognitionState
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.model.alpha.StorageState
import com.monumentogram.dora.model.alpha.StoredAudio
import com.monumentogram.dora.model.alpha.TranscriptId
import com.monumentogram.dora.model.alpha.TranscriptRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphaFlowCoordinatorTest {
    private val recording = RecordingId("00000000-0000-4000-8000-000000000001")
    private val source =
        OriginalAudioRef(recording, AudioAssetId("00000000-0000-4000-8000-000000000002"))
    private val job = RecognitionJobId("00000000-0000-4000-8000-000000000003")
    private val transcript =
        TranscriptRef(TranscriptId("00000000-0000-4000-8000-000000000004"), source, job)
    private val ports = ControlledPorts()
    private val flow = AlphaFlowCoordinator(ports, ports, ports)

    @Test
    fun captureAcknowledgmentsDrivePauseResumeStopAndPersistence() {
        flow.dispatch(FlowIntent.Start(recording))
        assertEquals(CaptureState.Session(recording, CapturePhase.STARTING), flow.state.capture)
        assertEquals(FlowFailure.INVALID_STATE, flow.dispatch(FlowIntent.Stop))
        ports.captureDone(PortResult.Success(Unit))
        assertEquals(CaptureState.Session(recording, CapturePhase.RECORDING), flow.state.capture)
        flow.dispatch(FlowIntent.Pause)
        assertEquals(CaptureState.Session(recording, CapturePhase.PAUSING), flow.state.capture)
        ports.captureDone(PortResult.Success(Unit))
        assertEquals(CaptureState.Session(recording, CapturePhase.PAUSED), flow.state.capture)
        flow.dispatch(FlowIntent.Resume)
        ports.captureDone(PortResult.Success(Unit))
        flow.dispatch(FlowIntent.Stop)
        assertEquals(StorageState.Empty, flow.state.storage)
        ports.stopDone(PortResult.Success(source))
        assertEquals(CaptureState.Captured(source), flow.state.capture)
        assertEquals(FlowFailure.INVALID_STATE, flow.dispatch(FlowIntent.Recognize(job)))
        flow.dispatch(FlowIntent.Save)
        assertEquals(StorageState.Saving(source), flow.state.storage)
        ports.saveDone(PortResult.Success(StoredAudio(source)))
        assertEquals(StorageState.Recorded(StoredAudio(source)), flow.state.storage)
        assertEquals(RecognitionState.Idle, flow.state.recognition)
    }

    @Test
    fun recognitionRequiresMatchingAcknowledgmentAndKeepsOriginal() {
        recorded()
        flow.dispatch(FlowIntent.Recognize(job))
        assertEquals(
            RecognitionState.Processing(RecognitionRequest(StoredAudio(source), job)),
            flow.state.recognition,
        )
        assertEquals(RecognitionRequest(StoredAudio(source), job), ports.request)
        ports.recognitionDone(PortResult.Success(transcript))
        assertEquals(RecognitionState.Result(transcript), flow.state.recognition)
        assertEquals(StorageState.Recorded(StoredAudio(source)), flow.state.storage)
    }

    @Test
    fun wrongSourceOrJobCannotBecomeAResult() {
        recorded()
        flow.dispatch(FlowIntent.Recognize(job))
        ports.recognitionDone(
            PortResult.Success(
                transcript.copy(jobId = RecognitionJobId("00000000-0000-4000-8000-000000000009"))
            )
        )
        assertEquals(
            FlowFailure.INVALID_RESULT,
            (flow.state.recognition as RecognitionState.Failed).error,
        )
        assertEquals(StorageState.Recorded(StoredAudio(source)), flow.state.storage)
    }

    @Test
    fun cancellingRejectsLateAndDuplicateResultsAndPreservesAudio() {
        recorded()
        flow.dispatch(FlowIntent.Recognize(job))
        val late = ports.recognitionDone
        flow.dispatch(FlowIntent.CancelRecognition)
        assertTrue(flow.state.recognition is RecognitionState.Cancelling)
        late(PortResult.Success(transcript))
        assertTrue(flow.state.recognition is RecognitionState.Cancelling)
        ports.cancelDone(PortResult.Success(Unit))
        assertTrue(flow.state.recognition is RecognitionState.Cancelled)
        assertEquals(StorageState.Recorded(StoredAudio(source)), flow.state.storage)
        flow.dispatch(FlowIntent.Reset)
        late(PortResult.Success(transcript))
        assertEquals(AlphaFlowState(), flow.state)
    }

    @Test
    fun resetAndDuplicateDispatchAreRejectedWhileWorkIsPending() {
        flow.dispatch(FlowIntent.Start(recording))
        val before = flow.state
        listOf(
                FlowIntent.Reset,
                FlowIntent.Start(recording),
                FlowIntent.Save,
                FlowIntent.Recognize(job),
                FlowIntent.CancelRecognition,
            )
            .forEach {
                assertEquals(FlowFailure.INVALID_STATE, flow.dispatch(it))
                assertEquals(before, flow.state)
            }
    }

    @Test
    fun failuresAreTypedAndCannotInventStoredOrRecognizedSuccess() {
        flow.dispatch(FlowIntent.Start(recording))
        ports.captureDone(PortResult.Failure(FlowFailure.PERMISSION_DENIED))
        assertEquals(
            CaptureState.Failed(recording, FlowFailure.PERMISSION_DENIED),
            flow.state.capture,
        )
        assertEquals(StorageState.Empty, flow.state.storage)
        assertNull(flow.dispatch(FlowIntent.Reset))
        captured()
        flow.dispatch(FlowIntent.Save)
        ports.saveDone(PortResult.Failure(FlowFailure.STORAGE))
        assertEquals(StorageState.Failed(source, FlowFailure.STORAGE), flow.state.storage)
        assertEquals(CaptureState.Captured(source), flow.state.capture)
    }

    @Test
    fun productionCompositionIsUnavailableAndNeverSucceeds() {
        val unavailable = unavailableAlphaFlow()
        unavailable.dispatch(FlowIntent.Start(recording))
        assertEquals(
            CaptureState.Failed(recording, FlowFailure.UNAVAILABLE),
            unavailable.state.capture,
        )
        assertEquals(StorageState.Empty, unavailable.state.storage)
        assertEquals(RecognitionState.Idle, unavailable.state.recognition)
    }

    private fun captured() {
        flow.dispatch(FlowIntent.Start(recording))
        ports.captureDone(PortResult.Success(Unit))
        flow.dispatch(FlowIntent.Stop)
        ports.stopDone(PortResult.Success(source))
    }

    private fun recorded() {
        captured()
        flow.dispatch(FlowIntent.Save)
        ports.saveDone(PortResult.Success(StoredAudio(source)))
    }
}

internal class ControlledPorts : CapturePort, OriginalAudioStoragePort, RecognitionPort {
    override var capability = Capability.AVAILABLE
    var throwOnStart = false
    lateinit var captureDone: (PortResult<Unit>) -> Unit
    lateinit var stopDone: (PortResult<OriginalAudioRef>) -> Unit
    lateinit var saveDone: (PortResult<StoredAudio>) -> Unit
    lateinit var recognitionDone: (PortResult<TranscriptRef>) -> Unit
    lateinit var cancelDone: (PortResult<Unit>) -> Unit
    var request: RecognitionRequest? = null

    override fun start(recordingId: RecordingId, complete: (PortResult<Unit>) -> Unit) {
        check(!throwOnStart) { "synthetic-private-exception-content" }
        captureDone = complete
    }

    override fun pause(recordingId: RecordingId, complete: (PortResult<Unit>) -> Unit) {
        captureDone = complete
    }

    override fun resume(recordingId: RecordingId, complete: (PortResult<Unit>) -> Unit) {
        captureDone = complete
    }

    override fun stop(recordingId: RecordingId, complete: (PortResult<OriginalAudioRef>) -> Unit) {
        stopDone = complete
    }

    override fun persist(source: OriginalAudioRef, complete: (PortResult<StoredAudio>) -> Unit) {
        saveDone = complete
    }

    override fun resolve(source: OriginalAudioRef, complete: (PortResult<StoredAudio>) -> Unit) {
        saveDone = complete
    }

    override fun recognize(
        request: RecognitionRequest,
        complete: (PortResult<TranscriptRef>) -> Unit,
    ) {
        this.request = request
        recognitionDone = complete
    }

    override fun cancel(jobId: RecognitionJobId, complete: (PortResult<Unit>) -> Unit) {
        cancelDone = complete
    }
}
