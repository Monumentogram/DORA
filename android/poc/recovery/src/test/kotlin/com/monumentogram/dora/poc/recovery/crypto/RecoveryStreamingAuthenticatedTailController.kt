package com.monumentogram.dora.poc.recovery.crypto

import java.io.ByteArrayOutputStream

/**
 * Synthetic host/JVM seam for the approved Option A recovery accounting rule.
 *
 * The caller supplies only bytes returned by completed public authenticated reads. This seam does
 * not read a source, persist a checkpoint, or adopt metadata or processing intent.
 */
internal class RecoveryStreamingAuthenticatedTailController(
    private val acceptedEndExclusive: Int,
    private val durableCheckpointEndExclusive: Int,
    private val oracle: ByteArray,
) {
    init {
        require(acceptedEndExclusive in 0..oracle.size)
        require(durableCheckpointEndExclusive in 0..acceptedEndExclusive)
    }

    fun recover(
        completedAuthenticatedReads: List<ByteArray>,
        terminal: AuthenticatedTailTerminal,
    ): AuthenticatedTailRecovery {
        val returned = ByteArrayOutputStream()
        completedAuthenticatedReads.forEach { read ->
            require(read.isNotEmpty())
            val start = returned.size()
            val endExclusive = start + read.size
            require(endExclusive <= acceptedEndExclusive)
            require(oracle.copyOfRange(start, endExclusive).contentEquals(read))
            returned.write(read)
        }

        val returnedBytes = returned.toByteArray()
        val recoveredEndExclusive = returnedBytes.size
        require(recoveredEndExclusive >= durableCheckpointEndExclusive)
        val recoveredBeyondCheckpointBytes = recoveredEndExclusive - durableCheckpointEndExclusive
        val tailLossBytes = acceptedEndExclusive - recoveredEndExclusive
        require(tailLossBytes in 0..AuthenticatedTailDesignBound.maximumBoundedTailLossBytes)

        return AuthenticatedTailRecovery(
            durableCheckpointEndExclusive = durableCheckpointEndExclusive,
            recoveredEndExclusive = recoveredEndExclusive,
            recoveredBeyondCheckpointBytes = recoveredBeyondCheckpointBytes,
            tailLossBytes = tailLossBytes,
            returnedBytes = returnedBytes,
            remainder = terminal.toBoundedRemainderClassification(),
            metadataAdopted = false,
            processingIntentAdopted = false,
            designBound = AuthenticatedTailDesignBound,
        )
    }
}

internal sealed interface AuthenticatedTailTerminal {
    data object AuthenticationFailure : AuthenticatedTailTerminal

    data object AuthenticatedEofAfterMinusOne : AuthenticatedTailTerminal
}

internal enum class BoundedRemainderClassification {
    AUTHENTICATION_FAILURE_QUARANTINED,
    AUTHENTICATED_EOF_NO_REMAINDER,
}

internal data class AuthenticatedTailRecovery(
    val durableCheckpointEndExclusive: Int,
    val recoveredEndExclusive: Int,
    val recoveredBeyondCheckpointBytes: Int,
    val tailLossBytes: Int,
    val returnedBytes: ByteArray,
    val remainder: BoundedRemainderClassification,
    val metadataAdopted: Boolean,
    val processingIntentAdopted: Boolean,
    val designBound: AuthenticatedTailDesignBound,
)

internal data object AuthenticatedTailDesignBound {
    const val maximumBoundedTailLossBytes = 8_160
    const val maximumBoundedTailLossMillis = 255
}

private fun AuthenticatedTailTerminal.toBoundedRemainderClassification():
    BoundedRemainderClassification =
    when (this) {
        AuthenticatedTailTerminal.AuthenticationFailure ->
            BoundedRemainderClassification.AUTHENTICATION_FAILURE_QUARANTINED
        AuthenticatedTailTerminal.AuthenticatedEofAfterMinusOne ->
            BoundedRemainderClassification.AUTHENTICATED_EOF_NO_REMAINDER
    }
