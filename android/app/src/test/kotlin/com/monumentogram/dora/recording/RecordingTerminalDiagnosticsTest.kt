package com.monumentogram.dora.recording

import com.monumentogram.dora.audio.recording.RecordingPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingTerminalDiagnosticsTest {
    @Test
    fun firstFailureRemainsImmutableWhenCleanupReportsAnotherFailure() {
        val diagnostics = RecordingTerminalDiagnostics()
        val first = receipt(CaptureFailure.PERSISTENCE_BACKPRESSURE)
        assertTrue(diagnostics.record(first))
        assertFalse(diagnostics.record(receipt(CaptureFailure.THREAD_TIMEOUT)))
        assertEquals(first, diagnostics.latest)
        assertTrue(diagnostics.dump().contains("outstandingFrames=256000"))
        assertTrue(diagnostics.dump().contains("failure=PERSISTENCE_BACKPRESSURE"))
        diagnostics.begin()
        assertTrue(diagnostics.record(receipt(CaptureFailure.READ_ERROR)))
        assertTrue(diagnostics.dump().contains("failure=READ_ERROR"))
    }

    @Test
    fun serializationContainsOnlyTypedContentFreeValuesAndSeparatesObservationTimes() {
        val text = receipt(CaptureFailure.READ_ERROR).encode()
        assertTrue(text.contains("nativeEventNanos=10"))
        assertTrue(text.contains("observedNanos=20"))
        assertTrue(text.contains("pendingWriterUnits=3"))
        assertTrue(text.contains("phase=RECORDING"))
        assertTrue(text.all { it.isLetterOrDigit() || it in "=_ -\n" })
        assertFalse(text.contains("recordingId"))
        assertFalse(text.contains("sessionId"))
    }

    private fun receipt(failure: CaptureFailure) =
        RecordingTerminalReceipt(
            failure = failure,
            persistenceFailure = null,
            operation = TerminalOperation.CAPTURE,
            phase = RecordingPhase.RECORDING,
            nativeEventNanos = 10,
            observedNanos = 20,
            generation = 1,
            admittedFrames = 256000,
            durableFrames = 0,
            pendingWriterUnits = 3,
            lastCompletedAppendDurationNanos = 30,
            canonicalQueueHighWater = 2,
            vadQueueHighWater = 9,
            servicePresent = true,
            microphoneThreadAlive = false,
            microphonePermissionGranted = true,
            authorityRevoked = false,
        )
}
