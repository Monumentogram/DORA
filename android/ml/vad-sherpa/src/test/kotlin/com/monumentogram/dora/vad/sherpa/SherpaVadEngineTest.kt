package com.monumentogram.dora.vad.sherpa

import com.monumentogram.dora.vad.SegmentationProfile
import com.monumentogram.dora.vad.VadException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaVadEngineTest {
    private class Binding : SherpaBinding {
        val calls = mutableListOf<FloatArray>()
        var resets = 0
        var closes = 0

        override fun compute(input: FloatArray): Float {
            calls.add(input.copyOf())
            return .75f
        }

        override fun reset() {
            resets++
        }

        override fun close() {
            closes++
        }
    }

    @Test
    fun exactTensorHas64ContextAnd512NewSamples() {
        val b = Binding()
        val engine = SherpaVadEngine(b, SegmentationProfile.FROZEN)
        val first = FloatArray(512) { it / 1024f }
        assertEquals(.75f, engine.probability(first))
        engine.probability(FloatArray(512))
        assertArrayEquals(FloatArray(64), b.calls[0].copyOfRange(0, 64), 0f)
        assertArrayEquals(first, b.calls[0].copyOfRange(64, 576), 0f)
        assertArrayEquals(first.copyOfRange(448, 512), b.calls[1].copyOfRange(0, 64), 0f)
        assertEquals(511 / 1024f, first.last())
    }

    @Test
    fun resetAndCloseClearStateAcrossRecordings() {
        val b = Binding()
        val e = SherpaVadEngine(b, SegmentationProfile.FROZEN)
        e.probability(FloatArray(512) { .5f })
        e.reset()
        e.probability(FloatArray(512))
        assertTrue(b.calls.last().all { it == 0f })
        assertEquals(1, b.resets)
        e.close()
        e.close()
        assertEquals(1, b.closes)
    }

    @Test(expected = VadException::class)
    fun invalidWindowIsTyped() {
        SherpaVadEngine(Binding(), SegmentationProfile.FROZEN).probability(FloatArray(511))
    }

    @Test(expected = VadException::class)
    fun nonFiniteSamplesAreRejected() {
        SherpaVadEngine(Binding(), SegmentationProfile.FROZEN)
            .probability(FloatArray(512) { Float.NaN })
    }

    @Test(expected = VadException::class)
    fun cannotUseClosedNativeState() {
        val e = SherpaVadEngine(Binding(), SegmentationProfile.FROZEN)
        e.close()
        e.probability(FloatArray(512))
    }
}
