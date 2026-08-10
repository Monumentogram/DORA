package com.monumentogram.dora.poc.capture.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OptionalMetricTest {
    @Test
    fun `optional telemetry value is preserved`() {
        assertEquals(72L, optionalMetric { 72L })
    }

    @Test
    fun `unsupported optional telemetry cannot abort capture`() {
        val value = optionalMetric<Long> { throw IllegalArgumentException("unsupported property") }

        assertNull(value)
    }
}
