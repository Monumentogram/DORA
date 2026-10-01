package com.monumentogram.dora.flow

import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.Capability
import com.monumentogram.dora.model.alpha.CaptureState
import com.monumentogram.dora.model.alpha.FlowFailure
import com.monumentogram.dora.model.alpha.FlowIntent
import com.monumentogram.dora.model.alpha.OriginalAudioRef
import com.monumentogram.dora.model.alpha.PortResult
import com.monumentogram.dora.model.alpha.RecognitionJobId
import com.monumentogram.dora.model.alpha.RecognitionState
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.model.alpha.StorageState
import com.monumentogram.dora.model.alpha.StoredAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphaFlowFailureTest {
    private val recording = RecordingId("00000000-0000-4000-8000-000000000001")
    private val source =
        OriginalAudioRef(recording, AudioAssetId("00000000-0000-4000-8000-000000000002"))
    private val job = RecognitionJobId("00000000-0000-4000-8000-000000000003")
    private val ports = ControlledPorts()
    private val flow = AlphaFlowCoordinator(ports, ports, ports)

    @Test
    fun capabilityLossMustNotPretendAnActiveCaptureHasStopped() {
        flow.dispatch(FlowIntent.Start(recording))
        ports.captureDone(PortResult.Success(Unit))
        ports.capability = Capability.UNAVAILABLE
        flow.dispatch(FlowIntent.Stop)
        assertTrue(flow.state.capture is CaptureState.Session)
        assertEquals(FlowFailure.INVALID_STATE, flow.dispatch(FlowIntent.Reset))
        ports.stopDone(PortResult.Success(source))
        assertEquals(CaptureState.Captured(source), flow.state.capture)
    }

    @Test
    fun unexpectedCaptureExceptionRetainsControlAndDoesNotLeakText() {
        ports.throwOnStart = true
        flow.dispatch(FlowIntent.Start(recording))
        assertEquals(CaptureState.Uncertain(recording, FlowFailure.INTERNAL), flow.state.capture)
        assertFalse(flow.state.toString().contains("synthetic-private-exception-content"))
        assertEquals(FlowFailure.INVALID_STATE, flow.dispatch(FlowIntent.Reset))
        assertNull(flow.dispatch(FlowIntent.Stop))
        ports.stopDone(PortResult.Success(source))
        assertEquals(CaptureState.Captured(source), flow.state.capture)
    }

    @Test
    fun stopFromPausedKeepsTheSameLogicalRecording() {
        flow.dispatch(FlowIntent.Start(recording))
        ports.captureDone(PortResult.Success(Unit))
        flow.dispatch(FlowIntent.Pause)
        ports.captureDone(PortResult.Success(Unit))
        flow.dispatch(FlowIntent.Stop)
        ports.stopDone(PortResult.Success(source))
        assertEquals(CaptureState.Captured(source), flow.state.capture)
    }

    @Test
    fun recognitionFailureKeepsOriginalAudioAvailable() {
        recorded()
        flow.dispatch(FlowIntent.Recognize(job))
        ports.recognitionDone(PortResult.Failure(FlowFailure.RECOGNITION))
        assertTrue(flow.state.recognition is RecognitionState.Failed)
        assertEquals(StorageState.Recorded(StoredAudio(source)), flow.state.storage)
    }

    @Test
    fun failedCancellationRetainsTheJobAndAllowsAnotherCancellation() {
        recorded()
        flow.dispatch(FlowIntent.Recognize(job))
        flow.dispatch(FlowIntent.CancelRecognition)
        ports.cancelDone(PortResult.Failure(FlowFailure.RECOGNITION))
        assertEquals(FlowFailure.INVALID_STATE, flow.dispatch(FlowIntent.Reset))
        assertNull(flow.dispatch(FlowIntent.CancelRecognition))
        ports.cancelDone(PortResult.Success(Unit))
        assertTrue(flow.state.recognition is RecognitionState.Cancelled)
        assertEquals(StorageState.Recorded(StoredAudio(source)), flow.state.storage)
    }

    @Test
    fun wrongAudioVersionCannotBeRecorded() {
        captured()
        flow.dispatch(FlowIntent.Save)
        ports.saveDone(
            PortResult.Success(
                StoredAudio(
                    source.copy(assetId = AudioAssetId("00000000-0000-4000-8000-000000000009"))
                )
            )
        )
        assertEquals(StorageState.Failed(source, FlowFailure.INVALID_RESULT), flow.state.storage)
        assertEquals(FlowFailure.INVALID_STATE, flow.dispatch(FlowIntent.Recognize(job)))
    }

    @Test
    fun duplicateCaptureAcknowledgmentCannotRegressAStoppedRecording() {
        flow.dispatch(FlowIntent.Start(recording))
        val late = ports.captureDone
        late(PortResult.Success(Unit))
        flow.dispatch(FlowIntent.Stop)
        ports.stopDone(PortResult.Success(source))
        late(PortResult.Failure(FlowFailure.CAPTURE))
        assertEquals(CaptureState.Captured(source), flow.state.capture)
    }

    private fun captured() {
        flow.dispatch(FlowIntent.Start(recording))
        ports.captureDone(PortResult.Success(Unit))
        flow.dispatch(FlowIntent.Stop)
        ports.stopDone(PortResult.Success(source))
    }

    @Test
    fun resetCannotForgetCapturedAudioBeforeDurableStorage() {
        captured()
        assertEquals(FlowFailure.INVALID_STATE, flow.dispatch(FlowIntent.Reset))
        flow.dispatch(FlowIntent.Save)
        ports.saveDone(PortResult.Failure(FlowFailure.STORAGE))
        assertEquals(FlowFailure.INVALID_STATE, flow.dispatch(FlowIntent.Reset))
        assertEquals(CaptureState.Captured(source), flow.state.capture)
        assertNull(flow.dispatch(FlowIntent.Save))
        ports.saveDone(PortResult.Success(StoredAudio(source)))
        assertNull(flow.dispatch(FlowIntent.Reset))
    }

    private fun recorded() {
        captured()
        flow.dispatch(FlowIntent.Save)
        ports.saveDone(PortResult.Success(StoredAudio(source)))
    }
}
