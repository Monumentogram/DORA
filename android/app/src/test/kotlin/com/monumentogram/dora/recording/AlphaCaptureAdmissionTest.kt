package com.monumentogram.dora.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphaCaptureAdmissionTest {
    @Test
    fun restartUsesDurablePrefixAndNewRecordingResetsOnlyAfterRetirement() {
        val queue = BoundedPcmQueue(320)
        val admission = CaptureAdmission(queue)
        admission.retire(1_382_399_999)
        var generation = admission.begin("recovered")
        assertTrue(admission.open(generation))
        assertEquals(
            CaptureAdmission.Result.LIMIT_REACHED,
            admission.offer(generation, ByteArray(1600)),
        )
        assertEquals(1L, admission.snapshot().frames)
        queue.drain(320) { assertEquals(2, it.size) }
        admission.fence()
        generation = admission.begin("attempted-bypass")
        assertFalse(admission.open(generation))
        admission.retire()
        generation = admission.begin("new-recording")
        assertTrue(admission.open(generation))
        assertEquals(CaptureAdmission.Result.ACCEPTED, admission.offer(generation, ByteArray(1600)))
        assertEquals(801L, admission.snapshot().frames)
    }

    @Test
    fun limitDoesNotReleaseOutstandingDurabilityBudget() {
        val queue = BoundedPcmQueue(320)
        val admission = CaptureAdmission(queue)
        admission.retire(1_382_144_000)
        val generation = admission.begin()
        admission.open(generation)
        repeat(319) { admission.offer(generation, ByteArray(1600)) }
        assertEquals(
            CaptureAdmission.Result.LIMIT_REACHED,
            admission.offer(generation, ByteArray(1600)),
        )
        queue.drain(320) {}
        assertEquals(320, admission.diagnostics().outstandingBlocks)
        assertEquals(0L, admission.diagnostics().durableFrames)
        admission.durableThrough(256_000)
        assertEquals(0, admission.diagnostics().outstandingBlocks)
        assertEquals(CaptureAdmission.Result.FENCED, admission.offer(generation, ByteArray(2)))
    }

    @Test
    fun queueCannotOwnFramesBeyondTwentyFourHoursAcrossGenerations() {
        val queue = BoundedPcmQueue(320)
        val admission = CaptureAdmission(queue)
        admission.retire(1_353_600_000)
        var generation = admission.begin("first")
        admission.open(generation)
        repeat(359) {
            admission.offer(generation, ByteArray(160_000))
            queue.drain(320) {}
            admission.durableThrough(admission.snapshot().frames)
        }
        admission.fence()
        generation = admission.begin("resume")
        admission.open(generation)
        admission.offer(generation, ByteArray(159_998))
        val last = byteArrayOf(1, 2, 3, 4)
        admission.offer(generation, last)
        val captured = mutableListOf<Int>()
        queue.drain(320) { captured.add(it.size) }
        assertEquals(28_800_000L, admission.snapshot().frames)
        assertEquals(listOf(159_998, 2), captured)
        assertFalse(admission.diagnostics().accepting)
        assertEquals(CaptureAdmission.Result.FENCED, admission.offer(generation, ByteArray(2)))
        assertEquals(listOf<Byte>(0, 0, 0, 0), last.toList())
    }
}
