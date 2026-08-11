package com.monumentogram.dora.poc.search.query

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafeFtsQueryCompilerTest {
    @Test
    fun `operators are compiled as quoted literal tokens`() {
        val compiled = SafeFtsQueryCompiler.compile("project OR synthetic", SearchMode.EXACT, false)

        assertEquals(QueryStatus.READY, compiled.status)
        assertEquals(listOf("project", "or", "synthetic"), compiled.tokens)
        assertEquals("\"project\" AND \"or\" AND \"synthetic\"", compiled.matchExpression)
    }

    @Test
    fun `special characters cannot become FTS syntax`() {
        val compiled =
            SafeFtsQueryCompiler.compile(
                "'); DROP TABLE transcript_segments; --",
                SearchMode.EXACT,
                false,
            )

        assertEquals(QueryStatus.READY, compiled.status)
        assertEquals(listOf("drop", "table", "transcript", "segments"), compiled.tokens)
        assertEquals(
            "\"drop\" AND \"table\" AND \"transcript\" AND \"segments\"",
            compiled.matchExpression,
        )
    }

    @Test
    fun `prefix wildcard is added only by the compiler`() {
        val compiled = SafeFtsQueryCompiler.compile("hyperpro*", SearchMode.PREFIX, false)

        assertEquals(QueryStatus.READY, compiled.status)
        assertEquals("\"hyperpro\"*", compiled.matchExpression)
    }

    @Test
    fun `fts4 execution renderer uses syntax portable across standard and enhanced parsers`() {
        val exact = SafeFtsQueryCompiler.compile("project -- synthetic", SearchMode.EXACT, false)
        val phrase = SafeFtsQueryCompiler.compile("silent harbor", SearchMode.PHRASE, false)
        val prefix = SafeFtsQueryCompiler.compile("hyperpro", SearchMode.PREFIX, false)

        assertEquals(
            "\"project\" \"synthetic\"",
            Fts4MatchExpressionRenderer.render(exact, SearchMode.EXACT),
        )
        assertEquals(
            "\"silent harbor\"",
            Fts4MatchExpressionRenderer.render(phrase, SearchMode.PHRASE),
        )
        assertEquals(
            "\"hyperpro*\"",
            Fts4MatchExpressionRenderer.render(prefix, SearchMode.PREFIX),
        )

        // The separately frozen normalization output remains byte-for-byte unchanged.
        assertEquals("\"project\" AND \"synthetic\"", exact.matchExpression)
        assertEquals("\"hyperpro\"*", prefix.matchExpression)
    }

    @Test
    fun `empty input is distinct from filter only search`() {
        val empty = SafeFtsQueryCompiler.compile(" \t\n", SearchMode.EXACT, false)
        val filtered = SafeFtsQueryCompiler.compile("", SearchMode.EXACT, true)

        assertEquals(QueryStatus.EMPTY, empty.status)
        assertEquals(QueryStatus.FILTER_ONLY, filtered.status)
        assertNull(empty.matchExpression)
        assertNull(filtered.matchExpression)
    }

    @Test
    fun `query bounds reject adversarial length and token counts`() {
        assertEquals(
            "QUERY_TOO_LONG",
            SafeFtsQueryCompiler.compile("x".repeat(513), SearchMode.EXACT, false).rejectionCode,
        )
        assertEquals(
            "TOO_MANY_TOKENS",
            SafeFtsQueryCompiler.compile(
                    (0 until 13).joinToString(" ") { "token$it" },
                    SearchMode.EXACT,
                    false,
                )
                .rejectionCode,
        )
        assertEquals(
            "TOKEN_TOO_LONG",
            SafeFtsQueryCompiler.compile("z".repeat(65), SearchMode.EXACT, false).rejectionCode,
        )
        assertEquals(
            "PREFIX_TOO_SHORT",
            SafeFtsQueryCompiler.compile("x", SearchMode.PREFIX, false).rejectionCode,
        )
    }
}
