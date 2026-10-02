package com.monumentogram.dora.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureAdmissionTest {
    @Test
    fun pauseWithEmptyQueueRejectsReadThatFinishedAfterFence() {
        val queue = BoundedPcmQueue(4)
        val admission = CaptureAdmission(queue) { 100L }
        val generation = admission.begin()
        assertTrue(admission.open(generation))
        assertEquals(0L, admission.fence().frames)
        assertEquals(CaptureAdmission.Result.FENCED, admission.offer(generation, byteArrayOf(1, 2)))
        assertEquals(0, queue.size)
    }

    @Test
    fun pauseWithQueuedPcmPreservesEveryOwnedFrameAndRejectsNewOnes() {
        val queue = BoundedPcmQueue(4)
        val admission = CaptureAdmission(queue) { 100L }
        val generation = admission.begin()
        admission.open(generation)
        assertEquals(
            CaptureAdmission.Result.ACCEPTED,
            admission.offer(generation, byteArrayOf(1, 2)),
        )
        assertEquals(
            CaptureAdmission.Result.ACCEPTED,
            admission.offer(generation, byteArrayOf(3, 4)),
        )
        assertEquals(2L, admission.fence().frames)
        val samples = mutableListOf<Byte>()
        queue.drain(4) { samples.addAll(it.toList()) }
        assertEquals(listOf<Byte>(1, 2, 3, 4), samples)
        assertEquals(CaptureAdmission.Result.FENCED, admission.offer(generation, byteArrayOf(5, 6)))
    }

    @Test
    fun pauseDuringNativeStartCannotBeUndoneByLateOpen() {
        val admission = CaptureAdmission(BoundedPcmQueue(4)) { 100L }
        val generation = admission.begin()
        admission.fence()
        assertFalse(admission.open(generation))
    }

    @Test
    fun staleReaderCannotFeedNewGeneration() {
        val queue = BoundedPcmQueue(4)
        val admission = CaptureAdmission(queue) { 100L }
        val old = admission.begin()
        admission.open(old)
        admission.fence()
        val next = admission.begin()
        assertTrue(admission.open(next))
        assertEquals(CaptureAdmission.Result.FENCED, admission.offer(old, byteArrayOf(1, 2)))
        assertEquals(CaptureAdmission.Result.ACCEPTED, admission.offer(next, byteArrayOf(3, 4)))
        assertEquals(1L, admission.fence().frames)
    }

    @Test
    fun duplicatePauseDoesNotLoseOwnedFrames() {
        val admission = CaptureAdmission(BoundedPcmQueue(4)) { 100L }
        val generation = admission.begin()
        admission.open(generation)
        admission.offer(generation, byteArrayOf(1, 2))
        val first = admission.fence()
        assertEquals(first.frames, admission.fence().frames)
        assertEquals(100L, first.lastAcceptedNanos)
    }

    @Test
    fun fullQueueIsTypedFailureAndDoesNotIncrementOwnedCount() {
        val admission = CaptureAdmission(BoundedPcmQueue(1)) { 100L }
        val generation = admission.begin()
        admission.open(generation)
        admission.offer(generation, byteArrayOf(1, 2))
        assertEquals(CaptureAdmission.Result.FULL, admission.offer(generation, byteArrayOf(3, 4)))
        assertEquals(1L, admission.fence().frames)
    }
}
