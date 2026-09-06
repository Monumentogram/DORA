@file:Suppress("LongParameterList", "MagicNumber")

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
