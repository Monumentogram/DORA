package com.monumentogram.dora.poc.recovery.contract

object RecoveryPcmContract {
    const val ENCODING = "PCM_S16LE"
    const val SAMPLE_RATE_HZ = 16_000
    const val CHANNELS = 1
    const val BYTES_PER_SAMPLE = 2
    const val BYTES_PER_SECOND = 32_000UL
    const val MAXIMUM_PLAINTEXT_BYTES_PER_RUN = 115_200_000UL
}

data class RecoveryPrefixAlgebra(
    val committedEndExclusive: ULong,
    val recoveredAuthenticatedEndExclusive: ULong,
    val acceptedEndExclusive: ULong,
) {
    init {
        contractRequire(committedEndExclusive <= recoveredAuthenticatedEndExclusive) {
            "Recovery algebra requires C <= R"
        }
        contractRequire(recoveredAuthenticatedEndExclusive <= acceptedEndExclusive) {
            "Recovery algebra requires R <= A"
        }
        contractRequire(acceptedEndExclusive <= RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN) {
            "Accepted end exceeds the bounded run maximum"
        }
    }

    val committedLossBytes: ULong
        get() =
            committedEndExclusive - minOf(committedEndExclusive, recoveredAuthenticatedEndExclusive)

    val tailLossBytes: ULong
        get() = acceptedEndExclusive - recoveredAuthenticatedEndExclusive

    val tailLossSeconds: Double
        get() = tailLossBytes.toDouble() / RecoveryPcmContract.BYTES_PER_SECOND.toDouble()

    val committedLossRequirementSatisfied: Boolean
        get() = committedLossBytes == 0UL
}

object RecoveryStreamingMath {
    const val CIPHERTEXT_SEGMENT_BYTES = 4_096UL
    const val FIRST_PLAINTEXT_SEGMENT_BYTES = 4_056UL
    const val LATER_PLAINTEXT_SEGMENT_BYTES = 4_080UL
    const val MAXIMUM_SEGMENTS_PER_RUN = 28_236UL
    const val MAXIMUM_BOUNDED_TAIL_BYTES = 8_160UL
    const val MAXIMUM_BOUNDED_TAIL_SECONDS = 0.255

    const val FIRST_REQUESTED_PLAINTEXT_READ_BYTES = 4_056
    const val LATER_REQUESTED_PLAINTEXT_READ_BYTES = 4_080

    const val DURABLE_LAST_NON_FINAL_SEGMENT_IS_SACRIFICIAL = true
    const val SACRIFICIAL_SEGMENT_IS_COMMITTED = false

    fun ciphertextPrefixBytes(durableNonFinalSegmentCount: ULong): ULong {
        validateSegmentCount(durableNonFinalSegmentCount)
        return RecoveryBinaryPrimitives.checkedMultiplyU64(
            durableNonFinalSegmentCount,
            CIPHERTEXT_SEGMENT_BYTES,
        )
    }

    fun recoveredEndExclusive(durableNonFinalSegmentCount: ULong): ULong {
        validateSegmentCount(durableNonFinalSegmentCount)
        if (durableNonFinalSegmentCount < MINIMUM_RECOVERABLE_SEGMENTS) {
            return 0UL
        }
        val laterSegmentCount = durableNonFinalSegmentCount - MINIMUM_RECOVERABLE_SEGMENTS
        val laterBytes =
            RecoveryBinaryPrimitives.checkedMultiplyU64(
                laterSegmentCount,
                LATER_PLAINTEXT_SEGMENT_BYTES,
            )
        return RecoveryBinaryPrimitives.checkedAddU64(FIRST_PLAINTEXT_SEGMENT_BYTES, laterBytes)
    }

    fun requireBoundedTail(algebra: RecoveryPrefixAlgebra) {
        contractRequire(algebra.tailLossBytes <= MAXIMUM_BOUNDED_TAIL_BYTES) {
            "Streaming tail exceeds the one-segment-lookahead bound"
        }
    }

    private fun validateSegmentCount(value: ULong) {
        contractRequire(value <= MAXIMUM_SEGMENTS_PER_RUN) {
            "Durable segment count exceeds the bounded run maximum"
        }
    }

    private const val MINIMUM_RECOVERABLE_SEGMENTS = 2UL
}

data class StreamingReadAccounting(
    val countedAuthenticatedBytes: Int,
    val discardedCallerBufferBytes: Int,
    val authenticatedEof: Boolean,
)

enum class StreamingReadPhase(val exactRequestedBytes: Int) {
    FIRST(RecoveryStreamingMath.FIRST_REQUESTED_PLAINTEXT_READ_BYTES),
    SUBSEQUENT(RecoveryStreamingMath.LATER_REQUESTED_PLAINTEXT_READ_BYTES),
}

object StreamingReadContract {
    fun successfulReturn(
        phase: StreamingReadPhase,
        requestedBytes: Int,
        returnedBytes: Int,
    ): StreamingReadAccounting {
        validateRequestedBytes(phase, requestedBytes)
        contractRequire(returnedBytes in 0..requestedBytes) {
            "Successful read byte count must fit the caller buffer"
        }
        return StreamingReadAccounting(
            countedAuthenticatedBytes = returnedBytes,
            discardedCallerBufferBytes = 0,
            authenticatedEof = false,
        )
    }

    fun readException(
        phase: StreamingReadPhase,
        callerBufferBytes: Int,
    ): StreamingReadAccounting {
        validateRequestedBytes(phase, callerBufferBytes)
        return StreamingReadAccounting(
            countedAuthenticatedBytes = 0,
            discardedCallerBufferBytes = callerBufferBytes,
            authenticatedEof = false,
        )
    }

    fun returnedStatus(
        phase: StreamingReadPhase,
        requestedBytes: Int,
        value: Int,
    ): StreamingReadAccounting {
        validateRequestedBytes(phase, requestedBytes)
        contractRequire(value == AUTHENTICATED_EOF) {
            "Only -1 represents authenticated normal EOF"
        }
        return StreamingReadAccounting(
            countedAuthenticatedBytes = 0,
            discardedCallerBufferBytes = 0,
            authenticatedEof = true,
        )
    }

    private fun validateRequestedBytes(
        phase: StreamingReadPhase,
        requestedBytes: Int,
    ) {
        contractRequire(requestedBytes == phase.exactRequestedBytes) {
            "Requested byte count does not match the Recovery read phase"
        }
    }

    private const val AUTHENTICATED_EOF = -1
}
