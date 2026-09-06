@file:Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")

package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAad
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeTargetKind
import com.monumentogram.dora.poc.recovery.contract.PublicationAad
import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryCheckpoint
import com.monumentogram.dora.poc.recovery.contract.RecoveryCheckpointCodec
import com.monumentogram.dora.poc.recovery.contract.RecoveryContract
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.contract.StreamingAad
import com.monumentogram.dora.poc.recovery.crypto.RecoveryEncryptedKeysetParseException
import com.monumentogram.dora.poc.recovery.crypto.RecoveryEncryptedKeysetParseFailure
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAead
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import com.monumentogram.dora.poc.recovery.crypto.RecoveryStreamingKeyset
import com.monumentogram.dora.poc.recovery.crypto.RecoveryTinkRuntime
import com.monumentogram.dora.poc.recovery.storage.RecoveryOpenedStreamingSource
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamingSourceException
import java.io.IOException
import java.io.InputStream
import java.security.GeneralSecurityException
import java.security.ProviderException

/** Tink binding kept behind the controller's prerequisite and public-stream ports. */
@Suppress("TooGenericExceptionCaught")
internal class RecoveryStreamingTinkPrerequisiteCrypto(
    private val openRunAead: (com.monumentogram.dora.poc.recovery.contract.RunId) -> RecoveryRunAead
) : RecoveryStreamingPrerequisiteCrypto {
    constructor() : this(RecoveryRunAeadProvider()::openExisting)

    override fun authenticate(
        checkpoint: RecoveryStreamingCheckpointRow,
        checkpointEnvelope: ByteArray,
        checkpointCiphertext: ByteArray,
        streamEnvelope: ByteArray,
    ): RecoveryStreamingCheckpointAuthentication {
        val runAead =
            try {
                openRunAead(checkpoint.runId)
            } catch (_: GeneralSecurityException) {
                return RecoveryStreamingCheckpointAuthentication.Missing
            } catch (_: ProviderException) {
                return RecoveryStreamingCheckpointAuthentication.Operational
            } catch (_: Throwable) {
                return RecoveryStreamingCheckpointAuthentication.Operational
            }
        val checkpointKeyset =
            try {
                RecoveryTinkRuntime.parseEncryptedAeadKeyset(
                    checkpointEnvelope,
                    runAead,
                    checkpointEnvelopeAad(checkpoint),
                )
            } catch (failure: RecoveryEncryptedKeysetParseException) {
                return failure.authenticationResult()
            } catch (_: ProviderException) {
                return RecoveryStreamingCheckpointAuthentication.Operational
            } catch (_: Throwable) {
                return RecoveryStreamingCheckpointAuthentication.Structural
            }
        val plaintext =
            try {
                checkpointKeyset.decryptPublication(
                    checkpointCiphertext,
                    checkpointPublicationAad(checkpoint),
                )
            } catch (_: ProviderException) {
                return RecoveryStreamingCheckpointAuthentication.Operational
            } catch (_: GeneralSecurityException) {
                return RecoveryStreamingCheckpointAuthentication.Rejected
            } catch (_: Throwable) {
                return RecoveryStreamingCheckpointAuthentication.Operational
            }
        val decoded =
            try {
                RecoveryCheckpointCodec.decode(plaintext)
            } catch (_: Throwable) {
                return RecoveryStreamingCheckpointAuthentication.Structural
            }
        if (decoded != checkpoint.checkpointPlaintext()) {
            return RecoveryStreamingCheckpointAuthentication.Structural
        }
        val streamKeyset =
            try {
                RecoveryTinkRuntime.parseEncryptedStreamingKeyset(
                    streamEnvelope,
                    runAead,
                    streamEnvelopeAad(checkpoint),
                )
            } catch (failure: RecoveryEncryptedKeysetParseException) {
                return failure.authenticationResult()
            } catch (_: ProviderException) {
                return RecoveryStreamingCheckpointAuthentication.Operational
            } catch (_: Throwable) {
                return RecoveryStreamingCheckpointAuthentication.Structural
            }
        return RecoveryStreamingCheckpointAuthentication.Ready(
            RecoveryStreamingPublicStreamOpener { source, witness ->
                require(witness.runId == checkpoint.runId) {
                    "Public stream witness does not match authenticated checkpoint"
                }
                TinkRecoveryStreamingPublicRead(source, streamKeyset, checkpoint.runId)
            }
        )
    }

    private fun RecoveryEncryptedKeysetParseException.authenticationResult() =
        when (failure) {
            RecoveryEncryptedKeysetParseFailure.AUTHENTICATION_REJECTED ->
                RecoveryStreamingCheckpointAuthentication.Rejected
            RecoveryEncryptedKeysetParseFailure.OPERATIONAL,
            RecoveryEncryptedKeysetParseFailure.UNKNOWN ->
                RecoveryStreamingCheckpointAuthentication.Operational
            RecoveryEncryptedKeysetParseFailure.OUTER_STRUCTURAL_OR_ENCODING_INVALID,
            RecoveryEncryptedKeysetParseFailure.AUTHENTICATED_INNER_KEYSET_INVALID,
            RecoveryEncryptedKeysetParseFailure.PARSED_BUT_UNSUPPORTED_PARAMETER_SUITE ->
                RecoveryStreamingCheckpointAuthentication.Structural
        }

    private fun checkpointEnvelopeAad(checkpoint: RecoveryStreamingCheckpointRow) =
        KeyEnvelopeAad(
            RecoveryCandidate.STREAM,
            checkpoint.runId,
            KeyEnvelopeTargetKind.CHECKPOINT,
            checkpoint.generation,
            KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
            0UL,
            checkpoint.committedEnd,
            0UL,
            checkpoint.previousCheckpointSha256,
        )

    private fun checkpointPublicationAad(checkpoint: RecoveryStreamingCheckpointRow) =
        PublicationAad(
            RecoveryCandidate.STREAM,
            checkpoint.runId,
            PublicationKind.CHECKPOINT,
            checkpoint.generation,
            if (checkpoint.committedEnd == 0UL) {
                PublicationAad.EMPTY_TERMINAL_UNIT_INDEX
            } else {
                checkpoint.durableNonFinalSegmentCount - 1UL
            },
            checkpoint.committedEnd,
            checkpoint.previousCheckpointSha256,
        )

    private fun streamEnvelopeAad(checkpoint: RecoveryStreamingCheckpointRow) =
        KeyEnvelopeAad(
            RecoveryCandidate.STREAM,
            checkpoint.runId,
            KeyEnvelopeTargetKind.STREAM,
            1UL,
            KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
            0UL,
            RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN,
            0UL,
            Sha256Value.ZERO,
        )

    private fun RecoveryStreamingCheckpointRow.checkpointPlaintext() =
        RecoveryCheckpoint(
            RecoveryCandidate.STREAM,
            runId,
            generation,
            previousCheckpointSha256,
            durableNonFinalSegmentCount,
            streamCiphertextPrefixBytes,
            committedEnd,
            streamKeyEnvelopeBytes,
            streamKeyEnvelopeSha256,
            streamCiphertextRelativeName,
            streamKeyEnvelopeRelativeName,
        )
}

private class TinkRecoveryStreamingPublicRead(
    private val source: RecoveryOpenedStreamingSource,
    private val keyset: RecoveryStreamingKeyset,
    private val runId: com.monumentogram.dora.poc.recovery.contract.RunId,
) : RecoveryStreamingIntentBuilder.RecoveryStreamingPublicRead {
    private var delegate: InputStream? = null
    private var closed = false

    override fun read(destination: ByteArray, offset: Int, count: Int): Int {
        check(!closed) { "Public stream is closed" }
        return try {
            stream().read(destination, offset, count)
        } catch (failure: RecoveryStreamingSourceException) {
            throw failure
        } catch (_: ProviderException) {
            throw RecoveryStreamingIntentBuilder.RecoveryStreamingPublicReadException(
                RecoveryStreamingSafeExceptionType.CRYPTO
            )
        } catch (_: GeneralSecurityException) {
            throw RecoveryStreamingIntentBuilder.RecoveryStreamingAuthenticationFailureException()
        } catch (_: IOException) {
            throw RecoveryStreamingIntentBuilder.RecoveryStreamingAuthenticationFailureException()
        }
    }

    override fun close() {
        closed = true
        delegate?.close()
    }

    private fun stream(): InputStream =
        delegate
            ?: try {
                    keyset.newDecryptingStream(
                        source.boundedInputStream(),
                        StreamingAad(RecoveryCandidate.STREAM, runId),
                    )
                } catch (failure: RecoveryStreamingSourceException) {
                    throw failure
                } catch (_: ProviderException) {
                    throw RecoveryStreamingIntentBuilder.RecoveryStreamingPublicReadException(
                        RecoveryStreamingSafeExceptionType.CRYPTO
                    )
                } catch (_: GeneralSecurityException) {
                    throw RecoveryStreamingIntentBuilder
                        .RecoveryStreamingAuthenticationFailureException()
                } catch (_: IOException) {
                    throw RecoveryStreamingIntentBuilder
                        .RecoveryStreamingAuthenticationFailureException()
                }
                .also { delegate = it }
}
