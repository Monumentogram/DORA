package com.monumentogram.dora.vad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameAdapterTest {
    @Test
    fun arbitraryBlocksRetainEverySampleAndExactPositions() {
        val ranges = mutableListOf<FrameRange>()
        val samples = mutableListOf<Float>()
        val adapter =
            PcmWindowAdapter(512) { range, window ->
                ranges.add(range)
                samples.addAll(window.toList())
            }
        var first = 0L
        for (count in listOf(1, 800, 33, 702, 512)) {
            val bytes = ByteArray(count * 2)
            repeat(count) { index ->
                val value = (first + index).toInt()
                bytes[index * 2] = value.toByte()
                bytes[index * 2 + 1] = (value shr 8).toByte()
            }
            adapter.accept(bytes, first)
            first += count
        }
        assertEquals(2048, samples.size)
        repeat(2048) { assertEquals(it / 32768f, samples[it]) }
        assertEquals((0..3).map { FrameRange(it * 512L, (it + 1) * 512L) }, ranges)
        assertEquals(0, adapter.pendingFrames)
    }

    @Test
    fun signedNormalizationAndBorrowedBufferClearing() {
        var borrowed: FloatArray? = null
        val adapter =
            PcmWindowAdapter(2) { _, samples ->
                assertEquals(-1f, samples[0])
                assertEquals(32767 / 32768f, samples[1])
                borrowed = samples
            }
        adapter.accept(byteArrayOf(0, -128, -1, 127), 0)
        assertTrue(borrowed!!.all { it == 0f })
    }

    @Test
    fun resetClearsPartialAndRestartsSourcePosition() {
        val ranges = mutableListOf<FrameRange>()
        val adapter =
            PcmWindowAdapter(512) { range, samples ->
                ranges.add(range)
                assertTrue(samples.all { it == 0f })
            }
        adapter.accept(ByteArray(200) { 127 }, 0)
        adapter.reset()
        adapter.accept(ByteArray(1024), 900)
        assertEquals(listOf(FrameRange(900, 1412)), ranges)
    }

    @Test(expected = IllegalArgumentException::class)
    fun oddByteInputRejected() {
        PcmWindowAdapter(512) { _, _ -> }.accept(ByteArray(3), 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingSamplesRejected() {
        val a = PcmWindowAdapter(512) { _, _ -> }
        a.accept(ByteArray(20), 0)
        a.accept(ByteArray(20), 11)
    }

    @Test
    fun remainderIsExplicitAtStop() {
        val a = PcmWindowAdapter(512) { _, _ -> }
        a.accept(ByteArray(30), 0)
        assertEquals(FrameRange(0, 15), a.unclassifiedTail())
        a.reset()
        assertNull(a.unclassifiedTail())
    }
}
