package com.monumentogram.dora.poc.recovery.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class SqliteCompileOptionsCanonicalizerTest {
    @Test
    fun `canonicalizes empty options as one terminal LF`() {
        val evidence = SqliteCompileOptionsCanonicalizer.canonicalize(emptyList())

        assertEquals(0, evidence.count)
        assertEquals("\n", evidence.canonicalUtf8)
        assertEquals(
            "01ba4719c80b6fe911b091a7c05124b64eeece964e09c058ef8f9805daca546b",
            evidence.sha256,
        )
    }

    @Test
    fun `sorts duplicate exact options and terminates the canonical UTF8 record`() {
        val evidence =
            SqliteCompileOptionsCanonicalizer.canonicalize(
                listOf("ZETA", "ALPHA", "ALPHA", "ΩMEGA")
            )

        assertEquals(4, evidence.count)
        assertEquals("ALPHA\nALPHA\nZETA\nΩMEGA\n", evidence.canonicalUtf8)
        assertEquals(
            "8a8501963a1230884b182753201078dcb3489add932b45e2d5ed0797e8079324",
            evidence.sha256,
        )
    }
}
