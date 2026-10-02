package com.monumentogram.dora.recording

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturedTimelineTest {
    @Test
    fun timerAdvancesWhilePersistenceOwnsAndBlocksOnAnEarlierPcmBlock() {
        val queue = BoundedPcmQueue(4)
        val admission = CaptureAdmission(queue)
        val timeline = CapturedTimeline(0, 0)
        val generation = admission.begin()
        admission.open(generation)
        admission.offer(generation, ByteArray(32_000))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val persistedFrames = AtomicLong()
        val writer = Thread {
            queue.drain(1) { bytes ->
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                persistedFrames.addAndGet(bytes.size / 2L)
            }
        }
        writer.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            admission.offer(generation, ByteArray(32_000))
            assertEquals(32_000L, timeline.frames(admission.snapshot().frames))
            assertEquals(1, queue.size)
            val boundary = admission.fence()
            assertEquals(32_000L, boundary.frames)
            assertEquals(
                CaptureAdmission.Result.FENCED,
                admission.offer(generation, ByteArray(32_000)),
            )
        } finally {
            release.countDown()
            writer.join(5_000)
        }
        queue.drain(4) { persistedFrames.addAndGet(it.size / 2L) }
        assertEquals(admission.snapshot().frames, persistedFrames.get())
        assertEquals(0, queue.size)
    }

    @Test
    fun pausedWallTimeAddsNoFramesAndResumeKeepsLogicalOffset() {
        val admission = CaptureAdmission(BoundedPcmQueue(4))
        val timeline = CapturedTimeline(0, 800_000)
        var generation = admission.begin()
        admission.open(generation)
        admission.offer(generation, ByteArray(1_600))
        admission.fence()
        assertEquals(800_800L, timeline.frames(admission.snapshot().frames))
        assertEquals(CaptureAdmission.Result.FENCED, admission.offer(generation, ByteArray(1_600)))
        assertEquals(800_800L, timeline.frames(admission.snapshot().frames))
        generation = admission.begin()
        admission.open(generation)
        admission.offer(generation, ByteArray(1_600))
        assertEquals(801_600L, timeline.frames(admission.snapshot().frames))
    }

    @Test
    fun aNewRecordingDoesNotInheritPreviousCaptureCount() {
        assertEquals(800L, CapturedTimeline(160_000, 0).frames(160_800))
    }
}
