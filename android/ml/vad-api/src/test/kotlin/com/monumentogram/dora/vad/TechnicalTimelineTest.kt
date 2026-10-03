package com.monumentogram.dora.vad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalTimelineTest {
    private val cap = 9_600_000L

    private fun timeline() =
        TechnicalTimeline(SegmentationProfile.FROZEN, 0, "epoch-a") { ordinal ->
            "technical-$ordinal"
        }

    @Test
    fun boundaryCases() {
        for (frames in listOf(599L * 16000, cap - 1, cap, cap + 1)) {
            val slices = timeline().accept(FrameRange(0, frames), "epoch-a")
            assertEquals(frames, slices.sumOf { it.range.count })
            assertEquals(if (frames <= cap) 1 else 2, slices.size)
            assertTrue(slices.all { it.range.count <= cap })
        }
    }

    @Test
    fun crossingInsideBlockSplitsExactly() {
        val t = timeline()
        t.accept(FrameRange(0, cap - 17), "epoch-a")
        val slices = t.accept(FrameRange(cap - 17, cap + 783), "epoch-a")
        assertEquals(
            listOf(FrameRange(cap - 17, cap), FrameRange(cap, cap + 783)),
            slices.map { it.range },
        )
        assertEquals(FrameRange(cap - 32000, cap), slices.last().overlapBefore)
        assertNotEquals(slices.first().technicalId, slices.last().technicalId)
    }

    @Test
    fun pauseEpochHasNoStaleOverlap() {
        val t = timeline()
        t.accept(FrameRange(0, 100), "epoch-a")
        t.resume(100, "epoch-b")
        val slice = t.accept(FrameRange(100, 200), "epoch-b").single()
        assertEquals("epoch-b", slice.technicalId)
        assertNull(slice.overlapBefore)
        assertEquals(100L, slice.technicalFirstFrame)
    }

    @Test(expected = IllegalArgumentException::class)
    fun staleEpochRejected() {
        timeline().accept(FrameRange(0, 1), "old")
    }

    @Test(expected = IllegalArgumentException::class)
    fun gapRejected() {
        timeline().accept(FrameRange(1, 2), "epoch-a")
    }

    @Test
    fun oneThreeEightHourReplayIsContinuousAndOverlapDoesNotAddDuration() {
        for (hours in listOf(1, 3, 8)) {
            val total = hours * 3600L * 16000
            val slices = timeline().accept(FrameRange(0, total), "epoch-a")
            assertEquals(hours * 6, slices.size)
            assertEquals(total, slices.sumOf { it.range.count })
            slices.zipWithNext().forEach { (a, b) ->
                assertEquals(a.range.end, b.range.first)
                assertEquals(FrameRange(b.range.first - 32000, b.range.first), b.overlapBefore)
            }
            assertEquals(slices.size, slices.map { it.technicalId }.distinct().size)
        }
    }
}
