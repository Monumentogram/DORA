package com.monumentogram.dora.flow

import com.monumentogram.dora.model.alpha.Capability
import com.monumentogram.dora.model.alpha.CapturePort
import com.monumentogram.dora.model.alpha.FlowFailure
import com.monumentogram.dora.model.alpha.OriginalAudioRef
import com.monumentogram.dora.model.alpha.OriginalAudioStoragePort
import com.monumentogram.dora.model.alpha.PortResult
import com.monumentogram.dora.model.alpha.RecognitionJobId
import com.monumentogram.dora.model.alpha.RecognitionPort
import com.monumentogram.dora.model.alpha.RecognitionRequest
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.model.alpha.StoredAudio
import com.monumentogram.dora.model.alpha.TranscriptRef

/** Explicit absence, never a successful fake. No runtime adapter is admitted by 7.2. */
fun unavailableAlphaFlow(): AlphaFlowCoordinator =
    AlphaFlowCoordinator(UnavailableAlphaPorts, UnavailableAlphaPorts, UnavailableAlphaPorts)

internal object UnavailableAlphaPorts : CapturePort, OriginalAudioStoragePort, RecognitionPort {
    override val capability = Capability.UNAVAILABLE

    override fun start(recordingId: RecordingId, complete: (PortResult<Unit>) -> Unit) =
        unavailable(complete)

    override fun pause(recordingId: RecordingId, complete: (PortResult<Unit>) -> Unit) =
        unavailable(complete)

    override fun resume(recordingId: RecordingId, complete: (PortResult<Unit>) -> Unit) =
        unavailable(complete)

    override fun stop(recordingId: RecordingId, complete: (PortResult<OriginalAudioRef>) -> Unit) =
        unavailable(complete)

    override fun persist(source: OriginalAudioRef, complete: (PortResult<StoredAudio>) -> Unit) =
        unavailable(complete)

    override fun resolve(source: OriginalAudioRef, complete: (PortResult<StoredAudio>) -> Unit) =
        unavailable(complete)

    override fun recognize(
        request: RecognitionRequest,
        complete: (PortResult<TranscriptRef>) -> Unit,
    ) = unavailable(complete)

    override fun cancel(jobId: RecognitionJobId, complete: (PortResult<Unit>) -> Unit) =
        unavailable(complete)

    private fun <T> unavailable(complete: (PortResult<T>) -> Unit) =
        complete(PortResult.Failure(FlowFailure.UNAVAILABLE))
}
