package com.monumentogram.dora.poc.capture.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdempotentStopGateTest {
    @Test
    fun `only the first stop command is accepted`() {
        val gate = IdempotentStopGate()

        assertTrue(gate.request())
        assertFalse(gate.request())
        assertTrue(gate.isRequested())
    }
}
