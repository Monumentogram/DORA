package com.monumentogram.dora.poc.capture.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CaptureStartFailureTest {
    @Test
    fun `failure message identifies the capture stage without exposing the cause message`() {
        val failure =
            CaptureStartException(
                CaptureStartStage.SYSTEM_SNAPSHOT,
                IllegalArgumentException("UNSAFE_INTERNAL_DETAIL"),
            )

        val message = captureStartFailureMessage(failure)

        assertEquals(
            "Запись не началась: CAPTURE_START_SYSTEM_SNAPSHOT (IllegalArgumentException)",
            message,
        )
        assertFalse(message.contains("UNSAFE_INTERNAL_DETAIL"))
    }

    @Test
    fun `unclassified failure uses a stable unknown stage`() {
        val message = captureStartFailureMessage(IllegalStateException("vendor detail"))

        assertEquals("Запись не началась: CAPTURE_START_UNKNOWN (IllegalStateException)", message)
    }
}
