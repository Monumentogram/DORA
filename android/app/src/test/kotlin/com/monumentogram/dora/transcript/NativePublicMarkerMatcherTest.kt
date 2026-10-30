package com.monumentogram.dora.transcript

import org.junit.Assert.assertEquals
import org.junit.Test

class NativePublicMarkerMatcherTest {
    @Test
    fun exactAndSingleCharacterChangesHaveSeparateMetrics() {
        for (text in listOf("amber lantern", "amber landern", "amber lanern", "amber lanetern")) {
            val report = NativePublicMarkerMatcher.analyze(text, listOf("amber lantern"))
            assertEquals(1, report.boundedOrdered)
            assertEquals(if (text == "amber lantern") 1 else 0, report.exactOrdered)
        }
    }

    @Test
    fun missingReorderedAndFarWrongMarkersDoNotPass() {
        val expected = listOf("amber lantern", "silver meadow")
        assertEquals(1, NativePublicMarkerMatcher.analyze("amber lantern", expected).boundedOrdered)
        val reordered = NativePublicMarkerMatcher.analyze("silver meadow amber lantern", expected)
        assertEquals(2, reordered.exactPresent)
        assertEquals(1, reordered.boundedOrdered)
        for (text in
            listOf("amber landerm", "amper landern", "amber distant lantern", "ember landerm")) {
            assertEquals(0, NativePublicMarkerMatcher.analyze(text, expected).boundedOrdered)
        }
    }

    @Test
    fun pageBoundariesAndRussianNormalizationDoNotLoseMarkers() {
        val matcher = NativePublicMarkerMatcher(listOf("amber lantern", "зелёный остров"))
        for (page in listOf("prefix AM", "BER lan", "tern. ЗЕЛЁ", "НЫЙ остров")) matcher.consume(
            page
        )
        assertEquals(NativeMarkerCoverage(2, 2, 2, 2), matcher.finish())
    }

    @Test
    fun partialWordsAndOverlongTokensCannotImpersonateMarkers() {
        for (text in listOf("xamber lanternx", "x".repeat(100) + "amber lantern", "amber")) {
            assertEquals(
                0,
                NativePublicMarkerMatcher.analyze(text, listOf("amber lantern")).boundedOrdered,
            )
        }
    }
}
