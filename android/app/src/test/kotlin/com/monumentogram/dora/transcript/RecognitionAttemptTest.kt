package com.monumentogram.dora.transcript

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionAttemptTest {
    @Test
    fun cancellationPreventsLatePublication() {
        val attempt = RecognitionAttempt()
        assertTrue(attempt.start())
        assertTrue(attempt.cancel())
        assertTrue(attempt.cancelled())
        assertFalse(attempt.publish())
    }

    @Test
    fun queuedCancellationNeverStartsEngine() {
        val attempt = RecognitionAttempt()
        assertTrue(attempt.cancel())
        assertFalse(attempt.start())
    }

    @Test
    fun publicationHasOneLinearizationPoint() {
        val attempt = RecognitionAttempt()
        assertTrue(attempt.start())
        assertTrue(attempt.publish())
        assertFalse(attempt.publish())
        assertFalse(attempt.cancel())
        assertFalse(attempt.cancelled())
    }
}
