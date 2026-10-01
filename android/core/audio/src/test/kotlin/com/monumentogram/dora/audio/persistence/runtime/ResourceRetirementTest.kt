package com.monumentogram.dora.audio.persistence.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceRetirementTest {
    @Test
    fun aFailedCloseRetainsTheResourceUntilRetryActuallySucceeds() {
        val retirement = ResourceRetirement()
        var calls = 0
        val resource = AutoCloseable {
            calls++
            if (calls == 1) error("Synthetic close failure")
        }
        retirement.retire(resource)
        assertFalse(retirement.isEmpty)
        retirement.retry()
        assertTrue(retirement.isEmpty)
        retirement.retry()
        assertEquals(2, calls)
    }
}
