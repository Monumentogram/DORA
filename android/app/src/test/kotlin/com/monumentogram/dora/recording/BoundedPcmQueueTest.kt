package com.monumentogram.dora.recording

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedPcmQueueTest {
    @Test
    fun refillDuringDrainCannotStarveControlCommands() {
        val queue = BoundedPcmQueue(2)
        queue.offer(byteArrayOf(1, 2))
        var consumed = 0
        queue.drain(3) {
            consumed++
            queue.offer(byteArrayOf(3, 4))
        }
        assertEquals(3, consumed)
        assertEquals(1, queue.size)
    }

    @Test
    fun capacityIsBoundedAndConsumedPcmIsClearedEvenOnFailure() {
        val queue = BoundedPcmQueue(1)
        val bytes = byteArrayOf(1, 2)
        assertTrue(queue.offer(bytes))
        assertFalse(queue.offer(byteArrayOf(3, 4)))
        try {
            queue.drain(1) { error("synthetic writer failure") }
        } catch (_: IllegalStateException) {}
        assertArrayEquals(ByteArray(2), bytes)
        assertEquals(0, queue.size)
        assertEquals(1, queue.highWater)
    }
}
