package com.monumentogram.dora.poc.recovery.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryMathTest {
    @Test
    fun `PCM identity constants remain exact`() {
        assertEquals("PCM_S16LE", RecoveryPcmContract.ENCODING)
        assertEquals(16_000, RecoveryPcmContract.SAMPLE_RATE_HZ)
        assertEquals(1, RecoveryPcmContract.CHANNELS)
        assertEquals(2, RecoveryPcmContract.BYTES_PER_SAMPLE)
        assertEquals(32_000UL, RecoveryPcmContract.BYTES_PER_SECOND)
        assertEquals(115_200_000UL, RecoveryPcmContract.MAXIMUM_PLAINTEXT_BYTES_PER_RUN)
    }

    @Test
    fun `one-segment-lookahead arithmetic covers q zero one two three and maximum`() {
        val expected =
            listOf(
                0UL to (0UL to 0UL),
                1UL to (4_096UL to 0UL),
                2UL to (8_192UL to 4_056UL),
                3UL to (12_288UL to 8_136UL),
                28_236UL to (115_654_656UL to 115_198_776UL),
            )
        expected.forEach { (q, values) ->
            assertEquals(values.first, RecoveryStreamingMath.ciphertextPrefixBytes(q))
            assertEquals(values.second, RecoveryStreamingMath.recoveredEndExclusive(q))
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryStreamingMath.ciphertextPrefixBytes(
                RecoveryStreamingMath.MAXIMUM_SEGMENTS_PER_RUN + 1UL
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryStreamingMath.recoveredEndExclusive(ULong.MAX_VALUE)
        }
    }

    @Test
    fun `C R A algebra enforces ordering loss formula and full run boundaries`() {
        val empty = RecoveryPrefixAlgebra(0UL, 0UL, 0UL)
        assertEquals(0UL, empty.committedLossBytes)
        assertEquals(0UL, empty.tailLossBytes)
        assertTrue(empty.committedLossRequirementSatisfied)

        val bounded = RecoveryPrefixAlgebra(8_136UL, 8_136UL, 16_296UL)
        assertEquals(0UL, bounded.committedLossBytes)
        assertEquals(8_160UL, bounded.tailLossBytes)
        assertEquals(0.255, bounded.tailLossSeconds, DOUBLE_TOLERANCE)
        RecoveryStreamingMath.requireBoundedTail(bounded)

        val maximum =
            RecoveryPrefixAlgebra(
                RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN,
                RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN,
                RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN,
            )
        assertEquals(0UL, maximum.tailLossBytes)

        assertThrows(RecoveryContractException::class.java) {
            RecoveryPrefixAlgebra(2UL, 1UL, 2UL)
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryPrefixAlgebra(0UL, 2UL, 1UL)
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryPrefixAlgebra(
                0UL,
                RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN,
                RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN + 1UL,
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryStreamingMath.requireBoundedTail(RecoveryPrefixAlgebra(0UL, 0UL, 8_161UL))
        }
    }

    @Test
    fun `new read session expects the exact first request size`() {
        val session = StreamingReadContract.newSession()

        assertEquals(4_056, session.nextRequestedBytes)
    }

    @Test
    fun `successful returns advance once then retain the subsequent request size`() {
        val session = StreamingReadContract.newSession()

        val first = session.successfulReturn(requestedBytes = 4_056, returnedBytes = 4_056)
        assertEquals(4_056, first.countedAuthenticatedBytes)
        assertEquals(0, first.discardedCallerBufferBytes)
        assertFalse(first.authenticatedEof)
        assertEquals(4_080, session.nextRequestedBytes)

        val second = session.successfulReturn(requestedBytes = 4_080, returnedBytes = 1)
        assertEquals(1, second.countedAuthenticatedBytes)
        assertEquals(4_080, session.nextRequestedBytes)

        val third = session.successfulReturn(requestedBytes = 4_080, returnedBytes = 0)
        assertEquals(0, third.countedAuthenticatedBytes)
        assertEquals(4_080, session.nextRequestedBytes)
    }

    @Test
    fun `first request cannot skip directly to the subsequent size`() {
        val session = StreamingReadContract.newSession()

        assertThrows(RecoveryContractException::class.java) {
            session.successfulReturn(requestedBytes = 4_080, returnedBytes = 1)
        }
        assertThrows(RecoveryContractException::class.java) {
            session.readException(callerBufferBytes = 4_080)
        }
        assertThrows(RecoveryContractException::class.java) {
            session.returnedStatus(requestedBytes = 4_080, value = -1)
        }
        assertEquals(4_056, session.nextRequestedBytes)
    }

    @Test
    fun `first request size cannot be repeated after its successful return`() {
        val session = StreamingReadContract.newSession()
        session.successfulReturn(requestedBytes = 4_056, returnedBytes = 1)

        assertThrows(RecoveryContractException::class.java) {
            session.successfulReturn(requestedBytes = 4_056, returnedBytes = 1)
        }
        assertThrows(RecoveryContractException::class.java) {
            session.readException(callerBufferBytes = 4_056)
        }
        assertThrows(RecoveryContractException::class.java) {
            session.returnedStatus(requestedBytes = 4_056, value = -1)
        }
        assertEquals(4_080, session.nextRequestedBytes)
    }

    @Test
    fun `each active state rejects every non-exact request size`() {
        val first = StreamingReadContract.newSession()
        listOf(0, 4_055, 4_057, 4_079, 4_081).forEach { requestedBytes ->
            assertThrows(RecoveryContractException::class.java) {
                first.successfulReturn(
                    requestedBytes = requestedBytes,
                    returnedBytes = 0,
                )
            }
            assertThrows(RecoveryContractException::class.java) {
                first.readException(callerBufferBytes = requestedBytes)
            }
            assertThrows(RecoveryContractException::class.java) {
                first.returnedStatus(requestedBytes = requestedBytes, value = -1)
            }
        }
        assertEquals(4_056, first.nextRequestedBytes)

        val subsequent = StreamingReadContract.newSession()
        subsequent.successfulReturn(requestedBytes = 4_056, returnedBytes = 0)
        listOf(0, 4_055, 4_057, 4_079, 4_081).forEach { requestedBytes ->
            assertThrows(RecoveryContractException::class.java) {
                subsequent.successfulReturn(
                    requestedBytes = requestedBytes,
                    returnedBytes = 0,
                )
            }
            assertThrows(RecoveryContractException::class.java) {
                subsequent.readException(callerBufferBytes = requestedBytes)
            }
            assertThrows(RecoveryContractException::class.java) {
                subsequent.returnedStatus(requestedBytes = requestedBytes, value = -1)
            }
        }
        assertEquals(4_080, subsequent.nextRequestedBytes)
    }

    @Test
    fun `successful returned byte count must stay inside the caller buffer`() {
        val session = StreamingReadContract.newSession()

        assertThrows(RecoveryContractException::class.java) {
            session.successfulReturn(requestedBytes = 4_056, returnedBytes = -1)
        }
        assertThrows(RecoveryContractException::class.java) {
            session.successfulReturn(requestedBytes = 4_056, returnedBytes = 4_057)
        }
        assertEquals(4_056, session.nextRequestedBytes)

        session.successfulReturn(requestedBytes = 4_056, returnedBytes = 1)
        assertThrows(RecoveryContractException::class.java) {
            session.successfulReturn(requestedBytes = 4_080, returnedBytes = 4_081)
        }
        assertEquals(4_080, session.nextRequestedBytes)
    }

    @Test
    fun `read exception discards the exact current caller buffer and counts no bytes`() {
        val first = StreamingReadContract.newSession().readException(callerBufferBytes = 4_056)
        assertEquals(0, first.countedAuthenticatedBytes)
        assertEquals(4_056, first.discardedCallerBufferBytes)
        assertFalse(first.authenticatedEof)

        val subsequentSession = StreamingReadContract.newSession()
        subsequentSession.successfulReturn(requestedBytes = 4_056, returnedBytes = 1)
        val subsequent = subsequentSession.readException(callerBufferBytes = 4_080)
        assertEquals(0, subsequent.countedAuthenticatedBytes)
        assertEquals(4_080, subsequent.discardedCallerBufferBytes)
        assertFalse(subsequent.authenticatedEof)
    }

    @Test
    fun `read exception makes the session terminal for every later event`() {
        val session = StreamingReadContract.newSession()
        session.readException(callerBufferBytes = 4_056)

        assertTerminal(session)
    }

    @Test
    fun `only minus one creates authenticated normal EOF`() {
        val session = StreamingReadContract.newSession()
        listOf(-2, 0, 1).forEach { status ->
            assertThrows(RecoveryContractException::class.java) {
                session.returnedStatus(requestedBytes = 4_056, value = status)
            }
        }
        assertEquals(4_056, session.nextRequestedBytes)

        val eof = session.returnedStatus(requestedBytes = 4_056, value = -1)
        assertEquals(0, eof.countedAuthenticatedBytes)
        assertEquals(0, eof.discardedCallerBufferBytes)
        assertTrue(eof.authenticatedEof)
    }

    @Test
    fun `authenticated EOF makes the session terminal for every later event`() {
        val session = StreamingReadContract.newSession()
        session.successfulReturn(requestedBytes = 4_056, returnedBytes = 1)
        session.returnedStatus(requestedBytes = 4_080, value = -1)

        assertTerminal(session)
    }

    @Test
    fun `two read sessions preserve independent first states`() {
        val left = StreamingReadContract.newSession()
        val right = StreamingReadContract.newSession()

        assertEquals(4_056, left.nextRequestedBytes)
        assertEquals(4_056, right.nextRequestedBytes)
        left.successfulReturn(requestedBytes = 4_056, returnedBytes = 1)
        assertEquals(4_080, left.nextRequestedBytes)
        assertEquals(4_056, right.nextRequestedBytes)
    }

    @Test
    fun `caller-facing read API exposes neither a phase argument nor a state reset`() {
        val sessionClass = StreamingReadSession::class.java
        val eventMethods =
            sessionClass.methods.filter {
                it.declaringClass == sessionClass &&
                    it.name in setOf("successfulReturn", "readException", "returnedStatus")
            }

        assertEquals(3, eventMethods.size)
        assertTrue(
            eventMethods.all { method ->
                method.parameterTypes.all { parameter -> parameter == Int::class.javaPrimitiveType }
            }
        )
        assertFalse(sessionClass.constructors.any { it.parameterCount == 0 })
        assertFalse(sessionClass.methods.any { it.name.contains("phase", ignoreCase = true) })
        assertFalse(sessionClass.methods.any { it.name.contains("reset", ignoreCase = true) })
        assertFalse(
            StreamingReadContract::class.java.declaredMethods.any {
                it.name in setOf("successfulReturn", "readException", "returnedStatus")
            }
        )
    }

    @Test
    fun `legacy phase-order probes are impossible through the stateful API`() {
        val skippedFirst = StreamingReadContract.newSession()
        assertThrows(RecoveryContractException::class.java) {
            skippedFirst.successfulReturn(requestedBytes = 4_080, returnedBytes = 1)
        }

        val repeatedFirst = StreamingReadContract.newSession()
        repeatedFirst.successfulReturn(requestedBytes = 4_056, returnedBytes = 1)
        assertThrows(RecoveryContractException::class.java) {
            repeatedFirst.successfulReturn(requestedBytes = 4_056, returnedBytes = 1)
        }
    }

    @Test
    fun `closed predecessor binding and immutable catalog findings remain enforced`() {
        assertThrows(RecoveryContractException::class.java) {
            validateGenerationAndPreviousDigest(
                generation = 1UL,
                previousCiphertextSha256 =
                    Sha256Value.fromBytes(ByteArray(Sha256Value.SIZE_BYTES) { 1 }),
                subject = "Regression predecessor",
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            validateCandidateBinding(
                actual = RecoveryCandidate.MICROFILE,
                expected = RecoveryCandidate.STREAM,
                subject = "Regression binding",
            )
        }

        val before = RecoveryFaultCatalog.orderedIds
        val mutable = before as MutableList<String>
        assertThrows(UnsupportedOperationException::class.java) { mutable.add("MUTATED") }
        val after = RecoveryFaultCatalog.orderedIds
        assertEquals(before, after)
        assertEquals(RecoveryFaultCatalog.EXPECTED_ROW_COUNT, after.size)
        assertEquals(RecoveryFaultCatalog.EXPECTED_ROW_COUNT, after.toSet().size)
        assertEquals(1, after.count { it == "KEY-04" })
    }

    private fun assertTerminal(session: StreamingReadSession) {
        assertThrows(RecoveryContractException::class.java) { session.nextRequestedBytes }
        assertThrows(RecoveryContractException::class.java) {
            session.successfulReturn(requestedBytes = 4_080, returnedBytes = 0)
        }
        assertThrows(RecoveryContractException::class.java) {
            session.readException(callerBufferBytes = 4_080)
        }
        assertThrows(RecoveryContractException::class.java) {
            session.returnedStatus(requestedBytes = 4_080, value = -1)
        }
    }

    private companion object {
        const val DOUBLE_TOLERANCE = 0.0
    }
}
