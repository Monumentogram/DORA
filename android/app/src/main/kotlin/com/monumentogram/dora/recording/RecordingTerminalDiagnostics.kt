package com.monumentogram.dora.recording

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.audio.recording.RecordingWriterOperation

internal enum class TerminalOperation {
    CAPTURE,
    PERSISTENCE,
    REVOCATION,
    PREPARATION,
    SHUTDOWN,
}

/** No identifiers, free text, routes, exception messages, credentials or payloads. */
internal data class RecordingTerminalReceipt(
    val failure: CaptureFailure?,
    val persistenceFailure: AudioFailure?,
    val operation: TerminalOperation,
    val phase: RecordingPhase,
    val nativeEventNanos: Long?,
    val observedNanos: Long,
    val generation: Long,
    val admittedFrames: Long,
    val durableFrames: Long,
    val pendingWriterUnits: Int,
    val lastCompletedAppendDurationNanos: Long,
    val canonicalQueueHighWater: Int,
    val vadQueueHighWater: Int?,
    val servicePresent: Boolean,
    val microphoneThreadAlive: Boolean,
    val microphonePermissionGranted: Boolean,
    val authorityRevoked: Boolean,
    val nativeBoundary: CaptureAdmission.Diagnostics? = null,
    val captureAttempted: Boolean = true,
    val writerFailureOperation: RecordingWriterOperation? = null,
) {
    fun encode(): String =
        "terminal_version=1 failure=${failure ?: "NONE"} persistenceFailure=${persistenceFailure ?: "NONE"} " +
            "operation=$operation phase=$phase nativeEventNanos=${nativeEventNanos ?: "UNKNOWN"} " +
            "observedNanos=$observedNanos generation=$generation admittedFrames=$admittedFrames " +
            "durableFrames=$durableFrames outstandingFrames=${admittedFrames - durableFrames} " +
            "pendingWriterUnits=$pendingWriterUnits " +
            "lastCompletedAppendDurationNanos=$lastCompletedAppendDurationNanos " +
            "canonicalQueueHighWater=$canonicalQueueHighWater vadQueueHighWater=${vadQueueHighWater ?: "UNKNOWN"} " +
            "servicePresent=$servicePresent microphoneThreadAlive=$microphoneThreadAlive " +
            "microphonePermissionGranted=$microphonePermissionGranted authorityRevoked=$authorityRevoked " +
            "captureAttempted=$captureAttempted nativeGeneration=${nativeBoundary?.generation ?: "UNKNOWN"} " +
            "nativeAdmittedFrames=${nativeBoundary?.frames ?: "UNKNOWN"} " +
            "nativeDurableFrames=${nativeBoundary?.durableFrames ?: "UNKNOWN"} " +
            "nativeOutstandingBlocks=${nativeBoundary?.outstandingBlocks ?: "UNKNOWN"} " +
            "writerFailureOperation=${writerFailureOperation ?: "UNKNOWN"}"
}

/** Control-owner publication; the first failure is never overwritten by cleanup fallout. */
internal class RecordingTerminalDiagnostics {
    @Volatile
    var latest: RecordingTerminalReceipt? = null
        private set

    private var recorded = false

    fun begin() {
        recorded = false
    }

    fun record(receipt: RecordingTerminalReceipt): Boolean {
        if (recorded) return false
        recorded = true
        latest = receipt
        return true
    }

    fun dump(): String = latest?.encode() ?: "terminal=NONE"
}
