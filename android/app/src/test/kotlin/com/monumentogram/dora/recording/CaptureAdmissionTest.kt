package com.monumentogram.dora.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureAdmissionTest {
    @Test
    fun rejectedBudgetSnapshotSurvivesCompletionBeforeReaderReportsFailure() {
        var clock = 100L
        val queue = BoundedPcmQueue(1)
        val admission = CaptureAdmission(queue) { clock }
        val generation = admission.begin()
        admission.open(generation)
        admission.offer(generation, byteArrayOf(1, 2))
        queue.drain(1) {}
        clock = 200L
        assertEquals(CaptureAdmission.Result.FULL, admission.offer(generation, byteArrayOf(3, 4)))
        admission.durableThrough(1)
        admission.fence()
        val rejected = checkNotNull(admission.rejection())
        assertEquals(200L, rejected.atNanos)
        assertEquals(0L, rejected.admission.durableFrames)
        assertEquals(1, rejected.admission.outstandingBlocks)
        assertEquals(generation, rejected.admission.generation)
    }

    @Test
    fun exactFenceTimeIsSeparateFromLastAcceptedPcmAndStableInSnapshot() {
        var clock = 100L
        val admission = CaptureAdmission(BoundedPcmQueue(4)) { clock }
        val generation = admission.begin()
        admission.open(generation)
        admission.offer(generation, byteArrayOf(1, 2))
        clock = 150L
        val boundary = admission.fence()
        assertEquals(100L, boundary.lastAcceptedNanos)
        assertEquals(150L, boundary.fencedAtNanos)
        clock = 200L
        assertEquals(150L, admission.snapshot().fencedAtNanos)
        assertEquals(CaptureAdmission.Result.FENCED, admission.offer(generation, byteArrayOf(3, 4)))
        assertEquals(1L, admission.snapshot().frames)
    }

    @Test
    fun movingPcmOutOfQueueDoesNotReleaseBudgetUntilDurable() {
        val queue = BoundedPcmQueue(1)
        val admission = CaptureAdmission(queue) { 100L }
        val first = admission.begin()
        admission.open(first)
        admission.offer(first, byteArrayOf(1, 2))
        queue.drain(1) {}
        admission.fence()
        val next = admission.begin()
        admission.open(next)
        assertEquals(CaptureAdmission.Result.FULL, admission.offer(next, byteArrayOf(3, 4)))
        admission.durableThrough(1)
        assertEquals(CaptureAdmission.Result.ACCEPTED, admission.offer(next, byteArrayOf(3, 4)))
    }

    @Test
    fun delayedDrainKeepsOriginalPhysicalIdentityAndFramePosition() {
        val queue = BoundedPcmQueue(4)
        val admission = CaptureAdmission(queue) { 100L }
        val first = admission.begin("A")
        admission.open(first)
        admission.offer(first, ByteArray(32_000))
        admission.fence()
        val next = admission.begin("B")
        admission.open(next)
        admission.offer(next, ByteArray(32_000))
        val blocks = mutableListOf<Triple<String, Long, Long>>()
        queue.drainOwned(4) { blocks.add(Triple(it.captureEpochId, it.firstFrame, it.generation)) }
        assertEquals(listOf("A", "B"), blocks.map { it.first })
        assertEquals(listOf(0L, 16_000L), blocks.map { it.second })
        assertEquals(listOf(first, next), blocks.map { it.third })
    }

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
