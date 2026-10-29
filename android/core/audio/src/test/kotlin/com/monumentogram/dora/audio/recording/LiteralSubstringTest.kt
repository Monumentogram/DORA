package com.monumentogram.dora.audio.recording

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LiteralSubstringTest {
    @Test
    fun longNearMatchIsCancellableWithinText() {
        var checks = 0
        var armed = false
        val matcher = LiteralSubstring("a".repeat(32767) + "b") { armed && ++checks == 4 }
        armed = true
        assertThrows(CancellationException::class.java) { matcher.indexIn("a".repeat(262144)) }
        assertEquals(4, checks)
    }

    @Test
    fun repeatedPrefixAndSupplementaryUnicodeUseLiteralCaseInsensitiveOffsets() {
        assertEquals(
            -1,
            LiteralSubstring("a".repeat(32767) + "b") { false }.indexIn("a".repeat(262144)),
        )
        assertEquals(2, LiteralSubstring("AbAbC") { false }.indexIn("abababC"))
        assertEquals(3, LiteralSubstring("𐐨😀") { false }.indexIn("abc𐐀😀"))
        assertEquals(1, LiteralSubstring("[a]*") { false }.indexIn("x[A]*"))
    }
}
