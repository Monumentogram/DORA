package com.monumentogram.dora.poc.capture.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SyntheticFixtureTest {
    @Test
    fun `fixture is deterministic and has fixed manifest digest`() {
        val first = SyntheticFixture.generate()
        val second = SyntheticFixture.generate()

        assertArrayEquals(first.samples, second.samples)
        assertEquals(first.sha256, second.sha256)
        assertEquals(EXPECTED_SHA256, first.sha256)
        assertEquals(
            SyntheticFixture.SAMPLE_RATE * SyntheticFixture.DURATION_SECONDS,
            first.samples.size,
        )
        assertFalse(first.segments.any { it.contains("speech", ignoreCase = true) })
    }

    companion object {
        private const val EXPECTED_SHA256 =
            "f77826d52cd0fde219c4e4f98f9db11a9cd66708b36614debabf5720315f2013"
    }
}
