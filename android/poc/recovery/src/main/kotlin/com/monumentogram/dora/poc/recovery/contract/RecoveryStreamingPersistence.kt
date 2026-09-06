@file:Suppress("LongParameterList", "MagicNumber", "TooManyFunctions")

package com.monumentogram.dora.poc.recovery.contract

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object RecoveryStreamingPersistenceV07 {
    const val PROTOCOL_ID = "poc-recovery-protocol-stage0-v0.7"
    const val CANDIDATE_ID = "REC-STREAM-TINK"
    const val MAX_PREIMAGE_BYTES = 4_096
}

enum class StreamDecision {
    VALID,
    REJECTED,
    FATAL,
}

enum class StreamDiagnosticBranch {
    NONE,
    PRE_INTERSECTION,
    POST_INTERSECTION,
}

enum class StreamDiagnosticStage {
    NONE,
    STREAM_CHECKPOINT,
    STREAM_SOURCE_EXTENT,
    STREAM_PAYLOAD_DECRYPT,
}

enum class StreamSourceMatch {
    VERIFIED_SAME_DESCRIPTOR,
    UNPROVEN_OR_MISMATCH,
}

enum class StreamCheckpointIntersection {
    PROVEN,
    CONTEXT_ONLY,
}

enum class StreamBoundaryResult {
    NOT_EVALUATED_ORACLE_MISMATCH,
    NOT_APPLICABLE_AUTHENTICATED_EOF,
    EXACT_FORMAT_BOUNDARY,
    NON_CANONICAL_CANDIDATE_END,
    BOUNDARY_EXCEEDS_OBSERVED_SOURCE,
}

enum class StreamTerminal {
    NOT_REACHED,
    COMPLETED_READ_REJECTED,
    AUTHENTICATED_EOF,
    AUTHENTICATION_FAILURE,
}

enum class StreamDiagnosticClassification {
    NONE,
    STREAM_CHECKPOINT_PREFIX_OUTSIDE_WITNESS,
    STREAM_SOURCE_TRUNCATED,
    STREAM_SOURCE_PREFIX_IDENTITY_MISMATCH,
    STREAM_RETURNED_BYTE_ORACLE_MISMATCH,
    STREAM_RECOVERED_BELOW_CHECKPOINT,
    STREAM_TAIL_BOUND_EXCEEDED,
    STREAM_REMAINDER_BOUNDARY_UNPROVEN,
}

enum class StreamRangeCertainty {
    EXACT_FORMAT_BOUNDARY,
    CONSERVATIVE_PROVEN_CHECKPOINT_SUPERSET,
    CONSERVATIVE_WHOLE_SOURCE,
}

sealed interface StreamBoundaryDerivation {
    data class Exact(val bytes: ULong) : StreamBoundaryDerivation

    data object NonCanonicalCandidateEnd : StreamBoundaryDerivation

    data class ExceedsObservedSource(val bytes: ULong) : StreamBoundaryDerivation
}

data class StreamAdmissionResult(
    val decision: StreamDecision,
    val classification: StreamDiagnosticClassification,
    val boundary: StreamBoundaryDerivation?,
    val requiredRangeStart: ULong?,
    val requiredRangeEnd: ULong?,
    val requiredRangeCertainty: StreamRangeCertainty?,
)

@Suppress("LongParameterList")
data class RecoveryStreamingRejectedObservationInput(
    val candidateEnd: ULong,
    val completedPlaintextSha256: Sha256Value,
    val oraclePrefixSha256: Sha256Value,
    val oraclePrefixEqual: Boolean,
    val comparedEnd: ULong,
    val firstMismatchOffset: ULong?,
    val equalPrefixSha256: Sha256Value?,
    val expectedOracleByte: UByte?,
    val observedPlaintextByte: UByte?,
    val observedTailLossBytes: ULong,
    val boundaryResult: StreamBoundaryResult,
    val boundaryBytes: ULong?,
)

@Suppress("LongParameterList")
data class RecoveryStreamingOutcomeIdentityInput(
    val witness: RecoveryStreamingWitnessInput,
    val observedSourceBytes: ULong,
    val observedSourceSha256: Sha256Value,
    val preFaultSourceMatch: StreamSourceMatch,
    val checkpointIntersection: StreamCheckpointIntersection,
    val decision: StreamDecision,
    val diagnosticBranch: StreamDiagnosticBranch,
    val terminal: StreamTerminal,
    val recoveredEnd: ULong?,
    val recoveredBeyondCheckpointBytes: ULong?,
    val tailLossBytes: ULong?,
    val returnedPlaintextSha256: Sha256Value?,
    val remainderBoundaryBytes: ULong?,
    val remainderCertainty: StreamRangeCertainty?,
    val rejectedObservation: RecoveryStreamingRejectedObservationInput?,
    val requiredRangeStart: ULong?,
    val requiredRangeCertainty: StreamRangeCertainty?,
    val diagnosticStage: StreamDiagnosticStage,
    val diagnosticClassification: StreamDiagnosticClassification,
) {
    companion object {
        fun validAuthenticationFailure(
            witness: RecoveryStreamingWitnessInput,
            observedSourceBytes: ULong,
            observedSourceSha256: Sha256Value,
            recoveredEnd: ULong,
            returnedPlaintextSha256: Sha256Value,
            remainderBoundaryBytes: ULong,
        ): RecoveryStreamingOutcomeIdentityInput {
            contractRequire(recoveredEnd >= witness.checkpointContextEnd) {
                "Recovered end is below checkpoint"
            }
            contractRequire(recoveredEnd <= witness.acceptedEnd) {
                "Recovered end exceeds accepted end"
            }
            contractRequire(remainderBoundaryBytes <= observedSourceBytes) {
                "Remainder boundary exceeds observed source"
            }
            val required = remainderBoundaryBytes.takeIf { it < observedSourceBytes }
            return RecoveryStreamingOutcomeIdentityInput(
                witness,
                observedSourceBytes,
                observedSourceSha256,
                StreamSourceMatch.VERIFIED_SAME_DESCRIPTOR,
                StreamCheckpointIntersection.PROVEN,
                StreamDecision.VALID,
                StreamDiagnosticBranch.NONE,
                StreamTerminal.AUTHENTICATION_FAILURE,
                recoveredEnd,
                recoveredEnd - witness.checkpointContextEnd,
                witness.acceptedEnd - recoveredEnd,
                returnedPlaintextSha256,
                remainderBoundaryBytes,
                StreamRangeCertainty.EXACT_FORMAT_BOUNDARY,
                null,
                required,
                required?.let { StreamRangeCertainty.EXACT_FORMAT_BOUNDARY },
                StreamDiagnosticStage.NONE,
                StreamDiagnosticClassification.NONE,
            )
        }
    }
}

@Suppress("LongParameterList")
data class RecoveryStreamingRangeIdentityInput(
    val runId: RunId,
    val outcomeId: Sha256Value,
    val decision: StreamDecision,
    val diagnosticBranch: StreamDiagnosticBranch,
    val terminal: StreamTerminal,
    val classification: StreamDiagnosticClassification,
    val observedSourceBytes: ULong,
    val observedSourceSha256: Sha256Value,
    val rangeStart: ULong,
    val rangeEnd: ULong,
    val rangeSha256: Sha256Value,
    val certainty: StreamRangeCertainty,
) {
    companion object {
        fun exact(
            runId: RunId,
            outcomeId: Sha256Value,
            observedSourceBytes: ULong,
            observedSourceSha256: Sha256Value,
            rangeStart: ULong,
            rangeSha256: Sha256Value,
        ) =
            RecoveryStreamingRangeIdentityInput(
                runId,
                outcomeId,
                StreamDecision.VALID,
                StreamDiagnosticBranch.NONE,
                StreamTerminal.AUTHENTICATION_FAILURE,
                StreamDiagnosticClassification.NONE,
                observedSourceBytes,
                observedSourceSha256,
                rangeStart,
                observedSourceBytes,
                rangeSha256,
                StreamRangeCertainty.EXACT_FORMAT_BOUNDARY,
            )
    }
}

object RecoveryStreamingRules {
    fun validateExtent(
        acceptedEnd: ULong,
        preFaultSourceEnd: ULong,
        observedEnd: ULong,
    ) {
        contractRequire(acceptedEnd <= MAX_ACCEPTED_END) { "Accepted end exceeds its bound" }
        contractRequire(preFaultSourceEnd <= MAX_PRE_FAULT_SOURCE) {
            "Pre-fault source end exceeds its bound"
        }
        contractRequire(observedEnd <= MAX_OBSERVED_SOURCE) { "Observed end exceeds its bound" }
        if (observedEnd >= preFaultSourceEnd) {
            contractRequire(observedEnd - preFaultSourceEnd <= MAX_SOURCE_APPEND) {
                "Observed source append exceeds its bound"
            }
        }
    }

    @Suppress("ReturnCount")
    fun deriveBoundary(candidateEnd: ULong, observedEnd: ULong): StreamBoundaryDerivation {
        val boundary =
            when {
                candidateEnd == 0UL -> 0UL
                candidateEnd < RecoveryStreamingMath.FIRST_PLAINTEXT_SEGMENT_BYTES ->
                    return StreamBoundaryDerivation.NonCanonicalCandidateEnd
                (candidateEnd - RecoveryStreamingMath.FIRST_PLAINTEXT_SEGMENT_BYTES) %
                    RecoveryStreamingMath.LATER_PLAINTEXT_SEGMENT_BYTES != 0UL ->
                    return StreamBoundaryDerivation.NonCanonicalCandidateEnd
                else -> {
                    val laterSegments =
                        (candidateEnd - RecoveryStreamingMath.FIRST_PLAINTEXT_SEGMENT_BYTES) /
                            RecoveryStreamingMath.LATER_PLAINTEXT_SEGMENT_BYTES
                    RecoveryBinaryPrimitives.checkedMultiplyU64(
                        laterSegments + 1UL,
                        RecoveryStreamingMath.CIPHERTEXT_SEGMENT_BYTES,
                    )
                }
            }
        return if (boundary > observedEnd) {
            StreamBoundaryDerivation.ExceedsObservedSource(boundary)
        } else {
            StreamBoundaryDerivation.Exact(boundary)
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "LongParameterList", "ReturnCount")
    fun classifyPostIntersection(
        committedEnd: ULong,
        acceptedEnd: ULong,
        candidateEnd: ULong,
        oracleEqual: Boolean,
        terminal: StreamTerminal,
        observedEnd: ULong,
    ): StreamAdmissionResult {
        contractRequire(committedEnd <= acceptedEnd) { "Committed end exceeds accepted end" }
        contractRequire(candidateEnd <= acceptedEnd) { "Candidate end exceeds accepted end" }
        if (!oracleEqual) {
            contractRequire(
                terminal == StreamTerminal.COMPLETED_READ_REJECTED && candidateEnd > 0UL
            ) {
                "Oracle mismatch requires a completed rejected read"
            }
            contractRequire(observedEnd > 0UL) { "Oracle mismatch requires a non-empty source" }
            return fatalWithRange(
                StreamDiagnosticClassification.STREAM_RETURNED_BYTE_ORACLE_MISMATCH,
                0UL,
                observedEnd,
                StreamRangeCertainty.CONSERVATIVE_WHOLE_SOURCE,
            )
        }
        contractRequire(
            terminal == StreamTerminal.AUTHENTICATED_EOF ||
                terminal == StreamTerminal.AUTHENTICATION_FAILURE
        ) {
            "Oracle-equal admission requires EOF or authentication failure"
        }
        if (candidateEnd < committedEnd) {
            return if (terminal == StreamTerminal.AUTHENTICATED_EOF) {
                fatal(StreamDiagnosticClassification.STREAM_RECOVERED_BELOW_CHECKPOINT)
            } else {
                contractRequire(
                    terminal == StreamTerminal.AUTHENTICATION_FAILURE && observedEnd > 0UL
                ) {
                    "Below-checkpoint authentication failure requires a non-empty source"
                }
                fatalWithRange(
                    StreamDiagnosticClassification.STREAM_RECOVERED_BELOW_CHECKPOINT,
                    0UL,
                    observedEnd,
                    StreamRangeCertainty.CONSERVATIVE_WHOLE_SOURCE,
                )
            }
        }
        val boundary =
            if (terminal == StreamTerminal.AUTHENTICATION_FAILURE) {
                deriveBoundary(candidateEnd, observedEnd)
            } else {
                null
            }
        if (boundary != null && boundary !is StreamBoundaryDerivation.Exact) {
            contractRequire(observedEnd > 0UL) { "Unproven boundary requires a non-empty source" }
            val checkpointBoundary = deriveBoundary(committedEnd, observedEnd)
            val provenCheckpointStart =
                (checkpointBoundary as? StreamBoundaryDerivation.Exact)?.bytes?.takeIf {
                    it < observedEnd
                }
            val rangeStart = provenCheckpointStart ?: 0UL
            val certainty =
                if (provenCheckpointStart != null) {
                    StreamRangeCertainty.CONSERVATIVE_PROVEN_CHECKPOINT_SUPERSET
                } else {
                    StreamRangeCertainty.CONSERVATIVE_WHOLE_SOURCE
                }
            return fatalWithRange(
                StreamDiagnosticClassification.STREAM_REMAINDER_BOUNDARY_UNPROVEN,
                rangeStart,
                observedEnd,
                certainty,
                boundary,
            )
        }
        if (acceptedEnd - candidateEnd > RecoveryStreamingMath.MAXIMUM_BOUNDED_TAIL_BYTES) {
            val exact = boundary as? StreamBoundaryDerivation.Exact
            val rangeStart = exact?.bytes?.takeIf { it < observedEnd }
            return StreamAdmissionResult(
                StreamDecision.REJECTED,
                StreamDiagnosticClassification.STREAM_TAIL_BOUND_EXCEEDED,
                boundary,
                rangeStart,
                rangeStart?.let { observedEnd },
                rangeStart?.let { StreamRangeCertainty.EXACT_FORMAT_BOUNDARY },
            )
        }
        val exact = boundary as? StreamBoundaryDerivation.Exact
        val rangeStart = exact?.bytes?.takeIf { it < observedEnd }
        return StreamAdmissionResult(
            StreamDecision.VALID,
            StreamDiagnosticClassification.NONE,
            boundary,
            rangeStart,
            rangeStart?.let { observedEnd },
            rangeStart?.let { StreamRangeCertainty.EXACT_FORMAT_BOUNDARY },
        )
    }

    private fun fatal(
        classification: StreamDiagnosticClassification,
        boundary: StreamBoundaryDerivation? = null,
    ) = StreamAdmissionResult(StreamDecision.FATAL, classification, boundary, null, null, null)

    private fun fatalWithRange(
        classification: StreamDiagnosticClassification,
        rangeStart: ULong,
        rangeEnd: ULong,
        certainty: StreamRangeCertainty,
        boundary: StreamBoundaryDerivation? = null,
    ) =
        StreamAdmissionResult(
            StreamDecision.FATAL,
            classification,
            boundary,
            rangeStart,
            rangeEnd,
            certainty,
        )

    private const val MAX_ACCEPTED_END = 115_200_000UL
    private const val MAX_PRE_FAULT_SOURCE = 115_654_656UL
    private const val MAX_OBSERVED_SOURCE = 115_662_848UL
    private const val MAX_SOURCE_APPEND = 8_192UL
}

@Suppress("LongParameterList")
data class RecoveryStreamingCheckpointIdentityInput(
    val runId: RunId,
    val generation: ULong,
    val durableNonFinalSegmentCount: ULong,
    val streamCiphertextPrefixBytes: ULong,
    val streamCiphertextPrefixSha256: Sha256Value,
    val committedEnd: ULong,
    val checkpointRelativeName: String,
    val checkpointBytes: ULong,
    val checkpointSha256: Sha256Value,
    val checkpointEnvelopeRelativeName: String,
    val checkpointEnvelopeBytes: ULong,
    val checkpointEnvelopeSha256: Sha256Value,
    val streamRelativeName: String,
    val streamEnvelopeRelativeName: String,
    val streamEnvelopeBytes: ULong,
    val streamEnvelopeSha256: Sha256Value,
    val previousCheckpointSha256: Sha256Value,
)

@Suppress("LongParameterList")
data class RecoveryStreamingWitnessInput(
    val runId: RunId,
    val checkpointGeneration: ULong,
    val checkpointIdentity: Sha256Value,
    val checkpointPrefixBytes: ULong,
    val checkpointContextEnd: ULong,
    val oracleIdentitySha256: Sha256Value,
    val acceptedEnd: ULong,
    val oraclePlaintextSha256: Sha256Value,
    val preFaultSourceBytes: ULong,
    val preFaultSourceSha256: Sha256Value,
    val controllerSnapshotSha256: Sha256Value?,
)

object RecoveryStreamingIdentity {
    fun oracle(
        acceptedEnd: ULong,
        oracleSha256: Sha256Value,
        runId: RunId,
    ): Sha256Value = identity {
        lp16Ascii("DORA_REC_STREAM_ORACLE_V4", 96)
        contractHeader(runId)
        u64(acceptedEnd)
        raw(oracleSha256.toByteArray())
    }

    fun checkpoint(value: RecoveryStreamingCheckpointIdentityInput): Sha256Value = identity {
        lp16Ascii("DORA_REC_STREAM_CHECKPOINT_V4", 96)
        contractHeader(value.runId)
        u64(value.generation)
        u64(value.durableNonFinalSegmentCount)
        u64(value.streamCiphertextPrefixBytes)
        raw(value.streamCiphertextPrefixSha256.toByteArray())
        u64(value.committedEnd)
        lp16Ascii(value.checkpointRelativeName, 512)
        u64(value.checkpointBytes)
        raw(value.checkpointSha256.toByteArray())
        lp16Ascii(value.checkpointEnvelopeRelativeName, 512)
        u64(value.checkpointEnvelopeBytes)
        raw(value.checkpointEnvelopeSha256.toByteArray())
        lp16Ascii(value.streamRelativeName, 512)
        lp16Ascii(value.streamEnvelopeRelativeName, 512)
        u64(value.streamEnvelopeBytes)
        raw(value.streamEnvelopeSha256.toByteArray())
        raw(value.previousCheckpointSha256.toByteArray())
    }

    fun controllerSnapshot(value: RecoveryStreamingWitnessInput): Sha256Value = identity {
        lp16Ascii("DORA_REC_STREAM_SNAPSHOT_V4", 96)
        witnessPrefix(value)
    }

    fun witness(value: RecoveryStreamingWitnessInput): Sha256Value {
        val snapshot =
            value.controllerSnapshotSha256
                ?: throw RecoveryContractException("Source witness requires a controller snapshot")
        return identity {
            lp16Ascii("DORA_REC_STREAM_WITNESS_V4", 96)
            witnessPrefix(value)
            raw(snapshot.toByteArray())
        }
    }

    fun rejected(
        runId: RunId,
        checkpointIdentity: Sha256Value,
        witnessId: Sha256Value,
        observedBytes: ULong,
        observedSha256: Sha256Value,
        value: RecoveryStreamingRejectedObservationInput,
    ): Sha256Value = identity {
        lp16Ascii("DORA_REC_STREAM_REJECTED_OBSERVATION_V4", 96)
        contractHeader(runId)
        raw(checkpointIdentity.toByteArray())
        raw(witnessId.toByteArray())
        u64(observedBytes)
        raw(observedSha256.toByteArray())
        rejectedFields(value, nullable = false)
    }

    fun outcome(value: RecoveryStreamingOutcomeIdentityInput): Sha256Value = identity {
        lp16Ascii("DORA_REC_STREAM_OUTCOME_V4", 96)
        lp16Ascii(RecoveryStreamingPersistenceV07.PROTOCOL_ID, 96)
        raw(value.witness.runId.toByteArray())
        lp16Ascii(RecoveryStreamingPersistenceV07.CANDIDATE_ID, 64)
        u64(value.witness.checkpointGeneration)
        raw(value.witness.checkpointIdentity.toByteArray())
        u64(value.witness.checkpointContextEnd)
        u64(value.witness.checkpointPrefixBytes)
        lp16Ascii("CRYPTOGRAPHICALLY_VALIDATED", 64)
        val witnessId = witness(value.witness)
        raw(witnessId.toByteArray())
        lp16Ascii("INTERNALLY_VERIFIED", 64)
        raw(requireNotNull(value.witness.controllerSnapshotSha256).toByteArray())
        raw(value.witness.oracleIdentitySha256.toByteArray())
        raw(value.witness.oraclePlaintextSha256.toByteArray())
        u64(value.witness.acceptedEnd)
        lp16Ascii("stream/stream.ct", 512)
        u64(value.witness.preFaultSourceBytes)
        raw(value.witness.preFaultSourceSha256.toByteArray())
        u64(value.observedSourceBytes)
        raw(value.observedSourceSha256.toByteArray())
        lp16Ascii(value.preFaultSourceMatch.name, 64)
        lp16Ascii(value.checkpointIntersection.name, 64)
        lp16Ascii(value.decision.name, 64)
        lp16Ascii(value.diagnosticBranch.name, 64)
        lp16Ascii(value.terminal.name, 64)
        nullableU64(value.recoveredEnd)
        nullableU64(value.recoveredBeyondCheckpointBytes)
        nullableU64(value.tailLossBytes)
        nullableSha256(value.returnedPlaintextSha256)
        nullableU64(value.remainderBoundaryBytes)
        nullableAscii(value.remainderCertainty?.name, 64)
        rejectedFields(value.rejectedObservation, nullable = true)
        if (value.rejectedObservation == null) {
            nullableSha256(null)
        } else {
            nullableSha256(
                rejected(
                    value.witness.runId,
                    value.witness.checkpointIdentity,
                    witnessId,
                    value.observedSourceBytes,
                    value.observedSourceSha256,
                    value.rejectedObservation,
                )
            )
        }
        nullableU64(value.requiredRangeStart)
        nullableAscii(value.requiredRangeCertainty?.name, 64)
        lp16Ascii(value.diagnosticStage.name, 64)
        lp16Ascii(value.diagnosticClassification.name, 64)
        u8(0)
        u8(0)
        u8(0)
    }

    fun range(value: RecoveryStreamingRangeIdentityInput): Sha256Value = identity {
        lp16Ascii("DORA_REC_STREAM_RANGE_V4", 96)
        contractHeader(value.runId)
        raw(value.outcomeId.toByteArray())
        lp16Ascii(value.decision.name, 64)
        lp16Ascii(value.diagnosticBranch.name, 64)
        lp16Ascii(value.terminal.name, 64)
        lp16Ascii(value.classification.name, 64)
        lp16Ascii("stream/stream.ct", 512)
        u64(value.observedSourceBytes)
        raw(value.observedSourceSha256.toByteArray())
        u64(value.rangeStart)
        u64(value.rangeEnd)
        raw(value.rangeSha256.toByteArray())
        lp16Ascii(value.certainty.name, 64)
        lp16Ascii("RETAINED_IN_PLACE_DENY_APP_READS", 64)
    }

    private fun BinaryWriter.rejectedFields(
        value: RecoveryStreamingRejectedObservationInput?,
        nullable: Boolean,
    ) {
        if (value == null) {
            nullableU64(null)
            // Re-emit in exact table order rather than grouping by scalar type.
            return writeAbsentRejectedFields()
        }
        if (nullable) nullableU64(value.candidateEnd) else u64(value.candidateEnd)
        if (nullable) nullableSha256(value.completedPlaintextSha256)
        else raw(value.completedPlaintextSha256.toByteArray())
        if (nullable) nullableSha256(value.oraclePrefixSha256)
        else raw(value.oraclePrefixSha256.toByteArray())
        if (nullable) {
            nullableU8(if (value.oraclePrefixEqual) 1U.toUByte() else 0U.toUByte())
        } else {
            u8(if (value.oraclePrefixEqual) 1 else 0)
        }
        if (nullable) nullableU64(value.comparedEnd) else u64(value.comparedEnd)
        nullableU64(value.firstMismatchOffset)
        nullableSha256(value.equalPrefixSha256)
        nullableU8(value.expectedOracleByte)
        nullableU8(value.observedPlaintextByte)
        if (nullable) nullableU64(value.observedTailLossBytes) else u64(value.observedTailLossBytes)
        if (nullable) nullableAscii(value.boundaryResult.name, 64)
        else lp16Ascii(value.boundaryResult.name, 64)
        nullableU64(value.boundaryBytes)
    }

    private fun BinaryWriter.writeAbsentRejectedFields() {
        // The caller already emitted candidate-end; emit the remaining eleven nullable fields.
        nullableSha256(null)
        nullableSha256(null)
        nullableU8(null)
        nullableU64(null)
        nullableU64(null)
        nullableSha256(null)
        nullableU8(null)
        nullableU8(null)
        nullableU64(null)
        nullableAscii(null, 64)
        nullableU64(null)
    }

    private fun BinaryWriter.contractHeader(runId: RunId) {
        lp16Ascii(RecoveryStreamingPersistenceV07.PROTOCOL_ID, 96)
        lp16Ascii(RecoveryStreamingPersistenceV07.CANDIDATE_ID, 64)
        raw(runId.toByteArray())
    }

    private fun BinaryWriter.witnessPrefix(value: RecoveryStreamingWitnessInput) {
        contractHeader(value.runId)
        u64(value.checkpointGeneration)
        raw(value.checkpointIdentity.toByteArray())
        u64(value.checkpointPrefixBytes)
        u64(value.checkpointContextEnd)
        raw(value.oracleIdentitySha256.toByteArray())
        u64(value.acceptedEnd)
        raw(value.oraclePlaintextSha256.toByteArray())
        u64(value.preFaultSourceBytes)
        raw(value.preFaultSourceSha256.toByteArray())
    }

    private fun identity(block: BinaryWriter.() -> Unit): Sha256Value {
        val writer = BinaryWriter(RecoveryStreamingPersistenceV07.MAX_PREIMAGE_BYTES).apply(block)
        return Sha256Value.calculate(writer.bytes())
    }
}

class CanonicalSqliteText
private constructor(
    val value: String,
    private val bytes: ByteArray,
) {
    fun encodedBytes(): ByteArray = bytes.copyOf()

    companion object {
        fun of(decoded: String, sqliteBlob: ByteArray, maximumBytes: Int): CanonicalSqliteText {
            require(maximumBytes in 0..UShort.MAX_VALUE.toInt())
            require(sqliteBlob.size <= maximumBytes)
            val decoder =
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
            val canonical =
                try {
                    decoder.decode(ByteBuffer.wrap(sqliteBlob)).toString()
                } catch (failure: java.nio.charset.CharacterCodingException) {
                    throw IllegalArgumentException("SQLite TEXT is not canonical UTF-8", failure)
                }
            require('\u0000' !in canonical)
            require(canonical == decoded)
            require(canonical.toByteArray(StandardCharsets.UTF_8).contentEquals(sqliteBlob))
            return CanonicalSqliteText(canonical, sqliteBlob.copyOf())
        }
    }
}

class RecoveryQuarantineMigrationRow(
    intentId: ByteArray,
    val runId: CanonicalSqliteText,
    val candidateId: CanonicalSqliteText,
    val bootstrapBinding: CanonicalSqliteText,
    val bootstrapRunId: CanonicalSqliteText?,
    val bootstrapCandidateId: CanonicalSqliteText?,
    val artifactRole: CanonicalSqliteText,
    val observedState: CanonicalSqliteText,
    val sourceRelativeName: CanonicalSqliteText,
    val destinationRelativeName: CanonicalSqliteText,
    val sourceBytes: Long,
    sourceSha256: ByteArray,
    val state: CanonicalSqliteText,
) {
    val intentId = intentId.copyOf()
    val sourceSha256 = sourceSha256.copyOf()
}

object RecoveryStreamingMigration {
    fun exactRowsEqual(
        left: List<RecoveryQuarantineMigrationRow>,
        right: List<RecoveryQuarantineMigrationRow>,
    ): Boolean {
        if (left.size != right.size) return false
        val leftRows = left.sortedWith { first, second ->
            compareBlobs(first.intentId, second.intentId)
        }
        val rightRows = right.sortedWith { first, second ->
            compareBlobs(first.intentId, second.intentId)
        }
        return leftRows.indices.all { index ->
            encode(leftRows[index]).contentEquals(encode(rightRows[index]))
        }
    }

    fun digest(rows: List<RecoveryQuarantineMigrationRow>): ByteArray {
        val ordered = rows.sortedWith { left, right -> compareBlobs(left.intentId, right.intentId) }
        val digest = MessageDigest.getInstance("SHA-256")
        val header = BinaryWriter().lp16Ascii(DOMAIN, 96).u64(ordered.size.toLong()).bytes()
        digest.update(header)
        ordered.forEach { row ->
            val encoded = encode(row)
            digest.update(BinaryWriter().u32(encoded.size).bytes())
            digest.update(encoded)
        }
        return digest.digest()
    }

    private fun encode(row: RecoveryQuarantineMigrationRow): ByteArray {
        require(row.intentId.size == SHA256_BYTES)
        require(row.sourceSha256.size == SHA256_BYTES)
        require(row.sourceBytes >= 0)
        require(row.candidateId.value == "REC-MICROFILE-TINK")
        require(row.bootstrapBinding.value in setOf("ABSENT", "PRESENT"))
        require(row.artifactRole.value in MICROFILE_ROLES)
        require(row.observedState.value in OBSERVED_STATES)
        require(row.state.value in setOf("PENDING", "COMPLETED"))
        if (row.bootstrapBinding.value == "ABSENT") {
            require(row.bootstrapRunId == null && row.bootstrapCandidateId == null)
        } else {
            require(row.bootstrapRunId?.value == row.runId.value)
            require(row.bootstrapCandidateId?.value == row.candidateId.value)
        }
        return BinaryWriter(ROW_MAX_BYTES)
            .raw(row.intentId)
            .lp16Utf8(row.runId, 64)
            .lp16Utf8(row.candidateId, 64)
            .lp16Utf8(row.bootstrapBinding, 16)
            .nullableText(row.bootstrapRunId, 64)
            .nullableText(row.bootstrapCandidateId, 64)
            .lp16Utf8(row.artifactRole, 64)
            .lp16Utf8(row.observedState, 64)
            .lp16Utf8(row.sourceRelativeName, 512)
            .lp16Utf8(row.destinationRelativeName, 512)
            .u64(row.sourceBytes)
            .raw(row.sourceSha256)
            .lp16Utf8(row.state, 16)
            .bytes()
    }

    private fun compareBlobs(left: ByteArray, right: ByteArray): Int {
        val common = minOf(left.size, right.size)
        for (index in 0 until common) {
            val comparison = (left[index].toInt() and 0xff).compareTo(right[index].toInt() and 0xff)
            if (comparison != 0) return comparison
        }
        return left.size.compareTo(right.size)
    }

    private const val DOMAIN = "DORA_RECOVERY_QMIG_V3_V4"
    private const val SHA256_BYTES = 32
    private const val ROW_MAX_BYTES = 4_096
    private val MICROFILE_ROLES =
        setOf(
            "KEY_CONFIRMATION",
            "MICROFILE_KEY_ENVELOPE",
            "MICROFILE_CIPHERTEXT",
            "MANIFEST_KEY_ENVELOPE",
            "MANIFEST_CIPHERTEXT",
            "UNKNOWN_REGULAR",
        )
    private val OBSERVED_STATES =
        setOf(
            "TEMP_ONLY",
            "TEMP_AND_FINAL",
            "FINAL_ORPHAN",
            "SQLITE_POINTS_TO_TEMP",
            "UNKNOWN_OR_NON_ALLOWLISTED_NAME",
        )
}

private class BinaryWriter(private val maximum: Int = Int.MAX_VALUE) {
    private val output = ByteArrayOutputStream()

    fun raw(value: ByteArray) = apply { write(value) }

    fun u8(value: Int) = apply {
        require(value in 0..0xff)
        write(byteArrayOf(value.toByte()))
    }

    fun u32(value: Int) = apply {
        require(value >= 0)
        write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array())
    }

    fun u64(value: Long) = apply {
        require(value >= 0)
        write(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array())
    }

    fun u64(value: ULong) = apply {
        require(value <= Long.MAX_VALUE.toULong())
        write(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value.toLong()).array())
    }

    fun lp16Ascii(value: String, bound: Int) = apply {
        require(value.all { it.code in 0..0x7f })
        lp16(value.toByteArray(StandardCharsets.US_ASCII), bound)
    }

    fun lp16Utf8(value: CanonicalSqliteText, bound: Int) = apply {
        lp16(value.encodedBytes(), bound)
    }

    fun nullableText(value: CanonicalSqliteText?, bound: Int) = apply {
        if (value == null) {
            write(byteArrayOf(0))
        } else {
            write(byteArrayOf(1))
            lp16Utf8(value, bound)
        }
    }

    fun nullableU64(value: ULong?) = apply {
        if (value == null) u8(0)
        else {
            u8(1)
            u64(value)
        }
    }

    fun nullableU8(value: UByte?) = apply {
        if (value == null) u8(0)
        else {
            u8(1)
            u8(value.toInt())
        }
    }

    fun nullableSha256(value: Sha256Value?) = apply {
        if (value == null) u8(0)
        else {
            u8(1)
            raw(value.toByteArray())
        }
    }

    fun nullableAscii(value: String?, bound: Int) = apply {
        if (value == null) u8(0)
        else {
            u8(1)
            lp16Ascii(value, bound)
        }
    }

    fun bytes(): ByteArray = output.toByteArray()

    private fun lp16(value: ByteArray, bound: Int) {
        require(value.size <= bound && value.size <= UShort.MAX_VALUE.toInt())
        write(ByteBuffer.allocate(Short.SIZE_BYTES).putShort(value.size.toShort()).array())
        write(value)
    }

    private fun write(value: ByteArray) {
        require(output.size() + value.size <= maximum)
        output.write(value)
    }
}
