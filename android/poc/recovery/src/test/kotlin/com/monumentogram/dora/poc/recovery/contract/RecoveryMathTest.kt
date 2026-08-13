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
    fun `read accounting counts only successful bytes discards exception buffers and reserves minus one for EOF`() {
        val successful =
            StreamingReadContract.successfulReturn(
                phase = StreamingReadPhase.FIRST,
                requestedBytes = 4_056,
                returnedBytes = 123,
            )
        assertEquals(123, successful.countedAuthenticatedBytes)
        assertEquals(0, successful.discardedCallerBufferBytes)
        assertFalse(successful.authenticatedEof)

        val failed =
            StreamingReadContract.readException(
                phase = StreamingReadPhase.SUBSEQUENT,
                callerBufferBytes = 4_080,
            )
        assertEquals(0, failed.countedAuthenticatedBytes)
        assertEquals(4_080, failed.discardedCallerBufferBytes)
        assertFalse(failed.authenticatedEof)

        val eof =
            StreamingReadContract.returnedStatus(
                phase = StreamingReadPhase.SUBSEQUENT,
                requestedBytes = 4_080,
                value = -1,
            )
        assertTrue(eof.authenticatedEof)
        assertEquals(0, eof.countedAuthenticatedBytes)
        assertThrows(RecoveryContractException::class.java) {
            StreamingReadContract.returnedStatus(
                phase = StreamingReadPhase.FIRST,
                requestedBytes = 4_056,
                value = -2,
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            StreamingReadContract.successfulReturn(
                phase = StreamingReadPhase.FIRST,
                requestedBytes = 4_056,
                returnedBytes = 4_057,
            )
        }
    }

    @Test
    fun `read phases reject every non-exact request size`() {
        listOf(0, 4_055, 4_057, 4_080).forEach { requestedBytes ->
            assertThrows(RecoveryContractException::class.java) {
                StreamingReadContract.successfulReturn(
                    phase = StreamingReadPhase.FIRST,
                    requestedBytes = requestedBytes,
                    returnedBytes = 0,
                )
            }
        }
        listOf(0, 4_056, 4_079, 4_081).forEach { requestedBytes ->
            assertThrows(RecoveryContractException::class.java) {
                StreamingReadContract.successfulReturn(
                    phase = StreamingReadPhase.SUBSEQUENT,
                    requestedBytes = requestedBytes,
                    returnedBytes = 0,
                )
            }
        }
        assertThrows(RecoveryContractException::class.java) {
            StreamingReadContract.readException(
                phase = StreamingReadPhase.FIRST,
                callerBufferBytes = 4_080,
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            StreamingReadContract.returnedStatus(
                phase = StreamingReadPhase.SUBSEQUENT,
                requestedBytes = 4_056,
                value = -1,
            )
        }
    }

    private companion object {
        const val DOUBLE_TOLERANCE = 0.0
    }
}
