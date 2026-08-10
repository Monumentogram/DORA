package com.monumentogram.dora.poc.search.data

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticDatasetGeneratorTest {
    @Test
    fun `reference scale and id contract are exact`() {
        assertEquals(10_000, SyntheticDatasetGenerator.REFERENCE_CONVERSATIONS)
        assertEquals(1_000_000, SyntheticDatasetGenerator.REFERENCE_TRANSCRIPT_ROWS)
        assertEquals(1L, SyntheticDatasetGenerator.segmentId(1, 0))
        assertEquals(1_000_000L, SyntheticDatasetGenerator.segmentId(10_000, 99))
    }

    @Test
    fun `same id always produces the same synthetic row`() {
        val first = SyntheticDatasetGenerator.segment(424_242)
        val second = SyntheticDatasetGenerator.segment(424_242)

        assertEquals(first, second)
        assertEquals(
            "d610a938b342af29d918450f37a6e7d880d8da2bd34a11731f7fef3106e467af",
            sha256(first.text),
        )
        assertTrue(first.text.contains("uniquemarkerquasar"))
        assertFalse(first.text.contains("@"))
    }

    @Test
    fun `generator covers russian english mixed unicode punctuation and repeated words`() {
        val samples =
            listOf(5_003L, 10_007L, 10_009L, 12_347L, 30L).map(SyntheticDatasetGenerator::segment)
        val combined = samples.joinToString(" ") { it.text }

        assertTrue(combined.contains("alpha-beta"))
        assertTrue(combined.contains("красный спутник"))
        assertTrue(combined.contains("silent harbor"))
        assertTrue(combined.contains("ёжик café 東京"))
        assertTrue(combined.contains("проект проект project project"))
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(
            ""
        ) {
            "%02x".format(it)
        }
}
