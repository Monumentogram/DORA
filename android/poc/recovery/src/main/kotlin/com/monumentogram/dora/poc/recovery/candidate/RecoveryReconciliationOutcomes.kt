package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.BoundedBinaryWriter
import com.monumentogram.dora.poc.recovery.contract.RecoveryContract
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifest
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.contract.writeSha256

internal enum class RecoveryFailureCategory {
    UNSAFE_PARENT,
    CORRUPT_LEAF,
    MISSING_ARTIFACT,
    STRUCTURAL,
    AUTHENTICATION_REJECTED,
    OPERATIONAL,
    UNKNOWN,
    UNKNOWN_OUTCOME,
}

internal enum class RecoveryFailureStage {
    ALIAS_OBSERVATION,
    ALIAS_OPEN,
    ENVELOPE_BINDING,
    ENVELOPE_PARSE,
    MANIFEST_PAYLOAD_DECRYPT,
    MANIFEST_PLAINTEXT,
    MANIFEST_SEMANTICS,
    CONFIRMATION_PAYLOAD_DECRYPT,
    CONFIRMATION_PLAINTEXT,
    UNIT_PAYLOAD_DECRYPT,
    UNIT_PLAINTEXT,
    ARTIFACT_PATH,
    ARTIFACT_IO,
    JOURNAL,
    OPERATIONAL,
}

/** Closed v0.8 controller stage vocabulary. These values are never persisted in schema v4. */
internal enum class RecoveryStreamingResultStage {
    LEASE,
    PREREQUISITE,
    SOURCE_PROOF,
    RANGE_ADMISSION,
    STREAM_READ,
    JOURNAL,
}

/** Closed v0.8 non-persistable controller classification vocabulary. */
internal enum class RecoveryStreamingResultClassification {
    STREAM_CHECKPOINT_MISSING,
    STREAM_CHECKPOINT_STRUCTURAL,
    STREAM_CHECKPOINT_AUTHENTICATION_REJECTED,
    STREAM_CHECKPOINT_AUTHENTICATION_OPERATIONAL,
    STREAM_CHECKPOINT_SPLIT_BRAIN,
    STREAM_SOURCE_WITNESS_MISSING,
    UNSAFE_PATH,
    STREAM_SOURCE_IDENTITY_CHANGED,
    STREAM_SOURCE_EXTENT_LIMIT_EXCEEDED,
    RUN_LEASE_CONTENDED,
    STREAM_ACTIVE_RANGE_DENIED,
    STREAM_ZERO_PROGRESS,
    STREAM_READ_CROSSES_ACCEPTED_END,
    STREAM_PUBLIC_READ_OPERATIONAL,
    JOURNAL_STRUCTURAL,
    JOURNAL_ATTEMPT_CONFLICT,
    STREAM_RANGE_QUARANTINE_COLLISION,
    ARTIFACT_IO_BEFORE_EXACT_SOURCE_HASH,
    JOURNAL_OPERATIONAL,
    JOURNAL_COMMIT_STATE_UNRESOLVED,
}

/** A deliberately short exception projection. It is carried only by a retry mapping. */
internal enum class RecoveryStreamingSafeExceptionType {
    NONE,
    IO,
    CRYPTO,
    SQLITE,
}

internal enum class RecoveryStreamingResultDisposition {
    RETRY,
    REJECTED,
    FATAL,
}

/**
 * The exhaustive v0.8 outward failure map. Durable v0.7 diagnostic classifications remain in
 * [com.monumentogram.dora.poc.recovery.contract.StreamDiagnosticClassification].
 */
internal class RecoveryStreamingResultMapping
private constructor(
    val disposition: RecoveryStreamingResultDisposition,
    val stage: RecoveryStreamingResultStage,
    val classification: RecoveryStreamingResultClassification,
    val allowedSafeExceptionTypes: Set<RecoveryStreamingSafeExceptionType>,
) {
    init {
        kotlin.require(
            (disposition == RecoveryStreamingResultDisposition.RETRY) ==
                allowedSafeExceptionTypes.isNotEmpty()
        ) {
            "Safe exception types belong only to Retry"
        }
    }

    companion object {
        val entries: List<RecoveryStreamingResultMapping> =
            listOf(
                fatal(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification.STREAM_CHECKPOINT_MISSING,
                ),
                fatal(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification.STREAM_CHECKPOINT_STRUCTURAL,
                ),
                fatal(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification.STREAM_CHECKPOINT_AUTHENTICATION_REJECTED,
                ),
                retry(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification
                        .STREAM_CHECKPOINT_AUTHENTICATION_OPERATIONAL,
                    RecoveryStreamingSafeExceptionType.CRYPTO,
                ),
                fatal(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification.STREAM_CHECKPOINT_SPLIT_BRAIN,
                ),
                fatal(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification.STREAM_SOURCE_WITNESS_MISSING,
                ),
                fatal(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification.UNSAFE_PATH,
                ),
                fatal(
                    RecoveryStreamingResultStage.SOURCE_PROOF,
                    RecoveryStreamingResultClassification.STREAM_SOURCE_IDENTITY_CHANGED,
                ),
                rejected(
                    RecoveryStreamingResultStage.SOURCE_PROOF,
                    RecoveryStreamingResultClassification.STREAM_SOURCE_EXTENT_LIMIT_EXCEEDED,
                ),
                retry(
                    RecoveryStreamingResultStage.LEASE,
                    RecoveryStreamingResultClassification.RUN_LEASE_CONTENDED,
                    RecoveryStreamingSafeExceptionType.NONE,
                ),
                fatal(
                    RecoveryStreamingResultStage.RANGE_ADMISSION,
                    RecoveryStreamingResultClassification.STREAM_ACTIVE_RANGE_DENIED,
                ),
                retry(
                    RecoveryStreamingResultStage.STREAM_READ,
                    RecoveryStreamingResultClassification.STREAM_ZERO_PROGRESS,
                    RecoveryStreamingSafeExceptionType.NONE,
                ),
                fatal(
                    RecoveryStreamingResultStage.STREAM_READ,
                    RecoveryStreamingResultClassification.STREAM_READ_CROSSES_ACCEPTED_END,
                ),
                retry(
                    RecoveryStreamingResultStage.STREAM_READ,
                    RecoveryStreamingResultClassification.STREAM_PUBLIC_READ_OPERATIONAL,
                    RecoveryStreamingSafeExceptionType.IO,
                    RecoveryStreamingSafeExceptionType.CRYPTO,
                ),
                fatal(
                    RecoveryStreamingResultStage.JOURNAL,
                    RecoveryStreamingResultClassification.JOURNAL_STRUCTURAL,
                ),
                fatal(
                    RecoveryStreamingResultStage.JOURNAL,
                    RecoveryStreamingResultClassification.JOURNAL_ATTEMPT_CONFLICT,
                ),
                fatal(
                    RecoveryStreamingResultStage.JOURNAL,
                    RecoveryStreamingResultClassification.STREAM_RANGE_QUARANTINE_COLLISION,
                ),
                retry(
                    RecoveryStreamingResultStage.SOURCE_PROOF,
                    RecoveryStreamingResultClassification.ARTIFACT_IO_BEFORE_EXACT_SOURCE_HASH,
                    RecoveryStreamingSafeExceptionType.IO,
                ),
                retry(
                    RecoveryStreamingResultStage.JOURNAL,
                    RecoveryStreamingResultClassification.JOURNAL_OPERATIONAL,
                    RecoveryStreamingSafeExceptionType.SQLITE,
                ),
                retry(
                    RecoveryStreamingResultStage.JOURNAL,
                    RecoveryStreamingResultClassification.JOURNAL_COMMIT_STATE_UNRESOLVED,
                    RecoveryStreamingSafeExceptionType.SQLITE,
                ),
            )

        private val byClassification =
            entries.associateBy(RecoveryStreamingResultMapping::classification).also {
                kotlin.require(it.size == RecoveryStreamingResultClassification.entries.size) {
                    "Result mapping must cover every classification exactly once"
                }
            }

        fun require(
            stage: RecoveryStreamingResultStage,
            classification: RecoveryStreamingResultClassification,
            safeExceptionType: RecoveryStreamingSafeExceptionType?,
        ): RecoveryStreamingResultMapping {
            val mapping = byClassification.getValue(classification)
            kotlin.require(mapping.stage == stage) { "Unlisted result stage/classification" }
            if (mapping.disposition == RecoveryStreamingResultDisposition.RETRY) {
                kotlin.require(safeExceptionType in mapping.allowedSafeExceptionTypes) {
                    "Unlisted retry exception type"
                }
            } else {
                kotlin.require(safeExceptionType == null) {
                    "Rejected and Fatal cannot carry an exception type"
                }
            }
            return mapping
        }

        private fun retry(
            stage: RecoveryStreamingResultStage,
            classification: RecoveryStreamingResultClassification,
            vararg exceptionTypes: RecoveryStreamingSafeExceptionType,
        ) =
            RecoveryStreamingResultMapping(
                RecoveryStreamingResultDisposition.RETRY,
                stage,
                classification,
                exceptionTypes.toSet(),
            )

        private fun rejected(
            stage: RecoveryStreamingResultStage,
            classification: RecoveryStreamingResultClassification,
        ) =
            RecoveryStreamingResultMapping(
                RecoveryStreamingResultDisposition.REJECTED,
                stage,
                classification,
                emptySet(),
            )

        private fun fatal(
            stage: RecoveryStreamingResultStage,
            classification: RecoveryStreamingResultClassification,
        ) =
            RecoveryStreamingResultMapping(
                RecoveryStreamingResultDisposition.FATAL,
                stage,
                classification,
                emptySet(),
            )
    }
}

internal data class RecoveryFailureDiagnostic(
    val category: RecoveryFailureCategory,
    val type: String,
    val message: String,
    val stage: RecoveryFailureStage = RecoveryFailureStage.OPERATIONAL,
) {
    companion object {
        fun capture(
            category: RecoveryFailureCategory,
            error: Throwable,
            stage: RecoveryFailureStage = RecoveryFailureStage.OPERATIONAL,
        ) =
            RecoveryFailureDiagnostic(
                category,
                error::class.java.name.take(MAX_TEXT),
                (error.message ?: "").take(MAX_TEXT),
                stage,
            )

        private const val MAX_TEXT = 256
    }
}

internal class RecoveryConfirmationAuthenticationException(
    val diagnostic: RecoveryFailureDiagnostic,
    cause: Throwable,
) : RuntimeException(diagnostic.message, cause)

/** Canonical bounded identity of every authenticated journal-row field. */
internal object RecoveryAuthenticatedRowsDigest {
    private const val MAX_ROW_BYTES = 4_096
    private const val MAX_NAME_BYTES = 1_024
    private const val MAX_STATE_BYTES = 32
    private const val MAX_DOMAIN_BYTES = 32
    private const val MAX_RUN_ID_BYTES = 64

    @Suppress("SwallowedException")
    fun calculateOrNull(rows: List<RecoveryMicrofileUnitRow>): Sha256Value? {
        if (rows.size > RecoveryContract.MAX_MANIFEST_ENTRIES) return null
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.update(u32(rows.size))
            rows.forEach { row ->
                val encoded = encode(row)
                digest.update(u32(encoded.size))
                digest.update(encoded)
            }
            Sha256Value.fromBytes(digest.digest())
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    @Suppress("MagicNumber")
    private fun u32(value: Int): ByteArray {
        require(value >= 0)
        return byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte(),
        )
    }

    private fun encode(row: RecoveryMicrofileUnitRow): ByteArray {
        require(row.ciphertextBytes >= 0 && row.keyEnvelopeBytes >= 0)
        return BoundedBinaryWriter(MAX_ROW_BYTES)
            .apply {
                writeLp16Ascii("REC-I3-ROW-V1", MAX_DOMAIN_BYTES)
                writeLp16Ascii(row.runId, MAX_RUN_ID_BYTES)
                writeLp16Ascii(row.candidateId, RecoveryContract.MAX_CANDIDATE_ID_BYTES)
                writeU32(row.unitIndex)
                writeU64(row.plaintextStartInclusive)
                writeU64(row.plaintextEndExclusive)
                writeU32(row.cadenceSeconds)
                writeLp16Ascii(row.ciphertextRelativeName, MAX_NAME_BYTES)
                writeU64(row.ciphertextBytes.toULong())
                writeSha256(row.ciphertextSha256)
                writeLp16Ascii(row.keyEnvelopeRelativeName, MAX_NAME_BYTES)
                writeU64(row.keyEnvelopeBytes.toULong())
                writeSha256(row.keyEnvelopeSha256)
                writeU64(row.manifestGeneration)
                writeSha256(row.processingIntentId)
                writeLp16Ascii(row.state, MAX_STATE_BYTES)
            }
            .toByteArray()
    }
}

internal sealed interface ManifestAuthenticationOutcome {
    data class Authenticated(val manifest: RecoveryManifest) : ManifestAuthenticationOutcome

    data class Rejected(val diagnostic: RecoveryFailureDiagnostic) : ManifestAuthenticationOutcome
}

internal sealed interface UnitAuthenticationOutcome {
    class Authenticated(bytes: ByteArray) : UnitAuthenticationOutcome {
        private val value = bytes.copyOf()

        fun snapshot(): ByteArray = value.copyOf()
    }

    data class Rejected(val diagnostic: RecoveryFailureDiagnostic) : UnitAuthenticationOutcome
}
