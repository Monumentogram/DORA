package com.monumentogram.dora.poc.recovery.contract

data class StreamingAad(
    val candidate: RecoveryCandidate,
    val runId: RunId,
) {
    init {
        validateCandidateBinding(candidate, RecoveryCandidate.STREAM, "Streaming AAD")
    }
}

object StreamingAadCodec {
    fun encode(value: StreamingAad): ByteArray =
        BoundedBinaryWriter(MAX_AAD_BYTES)
            .apply {
                writeMagic(MAGIC)
                writeU16(RecoveryContract.SCHEMA_VERSION)
                writeContractIdentity(value.candidate, value.runId)
                writeU64(STREAM_GENERATION)
                writeU64(PLAINTEXT_START)
                writeU64(RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN)
                writeSha256(Sha256Value.ZERO)
            }
            .toByteArray()

    fun decode(bytes: ByteArray): StreamingAad {
        val reader = BoundedBinaryReader(bytes, MAX_AAD_BYTES)
        reader.expectMagic(MAGIC)
        reader.readAndValidateSchema()
        val (candidate, runId) = reader.readAndValidateContractIdentity()
        contractRequire(reader.readU64() == STREAM_GENERATION) {
            "Streaming AAD generation must equal one"
        }
        contractRequire(reader.readU64() == PLAINTEXT_START) {
            "Streaming AAD plaintext start must equal zero"
        }
        contractRequire(reader.readU64() == RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN) {
            "Streaming AAD plaintext end does not match the bounded run maximum"
        }
        contractRequire(reader.readSha256().isZero()) {
            "Streaming AAD genesis digest must be zero"
        }
        reader.requireFinished()
        return StreamingAad(candidate, runId)
    }

    const val MAGIC = "DORASA01"
    private const val STREAM_GENERATION = 1UL
    private const val PLAINTEXT_START = 0UL
}

data class MicrofileAad(
    val candidate: RecoveryCandidate,
    val runId: RunId,
    val manifestGeneration: ULong,
    val unitIndex: ULong,
    val plaintextStartInclusive: ULong,
    val plaintextEndExclusive: ULong,
    val cadenceSeconds: ULong,
    val previousManifestCiphertextSha256: Sha256Value,
) {
    init {
        validateCandidateBinding(candidate, RecoveryCandidate.MICROFILE, "Microfile AAD")
        validateGenerationRangeAndU32(
            GenerationRange(
                generation = manifestGeneration,
                unitIndex = unitIndex,
                plaintextStartInclusive = plaintextStartInclusive,
                plaintextEndExclusive = plaintextEndExclusive,
                cadenceSeconds = cadenceSeconds,
            )
        )
        validateGenerationAndPreviousDigest(
            manifestGeneration,
            previousManifestCiphertextSha256,
            "Microfile AAD manifest",
        )
        contractRequire(unitIndex != RecoveryContract.U32_MAX) {
            "Microfile AAD requires a concrete unit index"
        }
        RecoveryCadenceContract.requireDeclaredMicrofileCadence(cadenceSeconds)
    }
}

object MicrofileAadCodec {
    fun encode(value: MicrofileAad): ByteArray =
        BoundedBinaryWriter(MAX_AAD_BYTES)
            .apply {
                writeMagic(MAGIC)
                writeU16(RecoveryContract.SCHEMA_VERSION)
                writeContractIdentity(value.candidate, value.runId)
                writeU64(value.manifestGeneration)
                writeU32(value.unitIndex)
                writeU64(value.plaintextStartInclusive)
                writeU64(value.plaintextEndExclusive)
                writeU32(value.cadenceSeconds)
                writeSha256(value.previousManifestCiphertextSha256)
            }
            .toByteArray()

    fun decode(bytes: ByteArray): MicrofileAad {
        val reader = BoundedBinaryReader(bytes, MAX_AAD_BYTES)
        reader.expectMagic(MAGIC)
        reader.readAndValidateSchema()
        val (candidate, runId) = reader.readAndValidateContractIdentity()
        val result =
            MicrofileAad(
                candidate = candidate,
                runId = runId,
                manifestGeneration = reader.readU64(),
                unitIndex = reader.readU32(),
                plaintextStartInclusive = reader.readU64(),
                plaintextEndExclusive = reader.readU64(),
                cadenceSeconds = reader.readU32(),
                previousManifestCiphertextSha256 = reader.readSha256(),
            )
        reader.requireFinished()
        return result
    }

    const val MAGIC = "DORAMA01"
}

enum class PublicationKind(
    val contractId: String,
    val expectedCandidate: RecoveryCandidate,
) {
    MANIFEST("MANIFEST", RecoveryCandidate.MICROFILE),
    CHECKPOINT("CHECKPOINT", RecoveryCandidate.STREAM);

    companion object {
        fun fromContractId(value: String): PublicationKind =
            entries.singleOrNull { it.contractId == value }
                ?: throw RecoveryContractException("Unsupported publication kind: $value")
    }
}

data class PublicationAad(
    val candidate: RecoveryCandidate,
    val runId: RunId,
    val publicationKind: PublicationKind,
    val generation: ULong,
    val terminalUnitIndex: ULong,
    val plaintextEndExclusive: ULong,
    val previousPublicationCiphertextSha256: Sha256Value,
) {
    init {
        validateCandidateBinding(candidate, publicationKind.expectedCandidate, "Publication AAD")
        validateGenerationAndPreviousDigest(
            generation,
            previousPublicationCiphertextSha256,
            "Publication AAD",
        )
        contractRequire(terminalUnitIndex <= RecoveryContract.U32_MAX) {
            "Terminal unit index does not fit U32"
        }
        validatePlaintextEnd(plaintextEndExclusive, "Publication plaintext end")
        contractRequire(
            (plaintextEndExclusive == EMPTY_END) == (terminalUnitIndex == EMPTY_TERMINAL_UNIT_INDEX)
        ) {
            "Only an empty publication uses the U32 maximum terminal unit index"
        }
    }

    companion object {
        const val EMPTY_TERMINAL_UNIT_INDEX = RecoveryContract.U32_MAX
        private const val EMPTY_END = 0UL
    }
}

object PublicationAadCodec {
    fun encode(value: PublicationAad): ByteArray =
        BoundedBinaryWriter(MAX_AAD_BYTES)
            .apply {
                writeMagic(MAGIC)
                writeU16(RecoveryContract.SCHEMA_VERSION)
                writeContractIdentity(value.candidate, value.runId)
                writeLp16Ascii(value.publicationKind.contractId)
                writeU64(value.generation)
                writeU32(value.terminalUnitIndex)
                writeU64(PLAINTEXT_START)
                writeU64(value.plaintextEndExclusive)
                writeSha256(value.previousPublicationCiphertextSha256)
            }
            .toByteArray()

    fun decode(bytes: ByteArray): PublicationAad {
        val reader = BoundedBinaryReader(bytes, MAX_AAD_BYTES)
        reader.expectMagic(MAGIC)
        reader.readAndValidateSchema()
        val (candidate, runId) = reader.readAndValidateContractIdentity()
        val publicationKind = PublicationKind.fromContractId(reader.readLp16Ascii())
        val generation = reader.readU64()
        val terminalUnitIndex = reader.readU32()
        contractRequire(reader.readU64() == PLAINTEXT_START) {
            "Publication plaintext start must equal zero"
        }
        val result =
            PublicationAad(
                candidate = candidate,
                runId = runId,
                publicationKind = publicationKind,
                generation = generation,
                terminalUnitIndex = terminalUnitIndex,
                plaintextEndExclusive = reader.readU64(),
                previousPublicationCiphertextSha256 = reader.readSha256(),
            )
        reader.requireFinished()
        return result
    }

    const val MAGIC = "DORACP01"
    private const val PLAINTEXT_START = 0UL
}

enum class KeyEnvelopeTargetKind(
    val contractId: String,
    val expectedCandidate: RecoveryCandidate,
) {
    STREAM("STREAM", RecoveryCandidate.STREAM),
    MICROFILE("MICROFILE", RecoveryCandidate.MICROFILE),
    MANIFEST("MANIFEST", RecoveryCandidate.MICROFILE),
    CHECKPOINT("CHECKPOINT", RecoveryCandidate.STREAM);

    companion object {
        fun fromContractId(value: String): KeyEnvelopeTargetKind =
            entries.singleOrNull { it.contractId == value }
                ?: throw RecoveryContractException("Unsupported key-envelope target kind: $value")
    }
}

data class KeyEnvelopeAad(
    val candidate: RecoveryCandidate,
    val runId: RunId,
    val targetKind: KeyEnvelopeTargetKind,
    val generation: ULong,
    val unitIndex: ULong,
    val plaintextStartInclusive: ULong,
    val plaintextEndExclusive: ULong,
    val cadenceSeconds: ULong,
    val previousPublicationCiphertextSha256: Sha256Value,
) {
    init {
        validateCandidateBinding(candidate, targetKind.expectedCandidate, "Key-envelope AAD")
        validateGenerationRangeAndU32(
            value =
                GenerationRange(
                    generation = generation,
                    unitIndex = unitIndex,
                    plaintextStartInclusive = plaintextStartInclusive,
                    plaintextEndExclusive = plaintextEndExclusive,
                    cadenceSeconds = cadenceSeconds,
                ),
            allowEmptyRange = targetKind != KeyEnvelopeTargetKind.MICROFILE,
        )
        validateGenerationAndPreviousDigest(
            generation,
            previousPublicationCiphertextSha256,
            "Key-envelope AAD",
        )
        val unitIndexIsNotApplicable = unitIndex == NOT_APPLICABLE_UNIT_INDEX
        contractRequire(
            (targetKind == KeyEnvelopeTargetKind.MICROFILE) != unitIndexIsNotApplicable
        ) {
            "Only a microfile key envelope uses a concrete unit index"
        }
        if (targetKind == KeyEnvelopeTargetKind.MICROFILE) {
            RecoveryCadenceContract.requireDeclaredMicrofileCadence(cadenceSeconds)
        } else {
            RecoveryCadenceContract.requireNotApplicableCadence(cadenceSeconds)
        }
    }

    companion object {
        const val NOT_APPLICABLE_UNIT_INDEX = RecoveryContract.U32_MAX
    }
}

object KeyEnvelopeAadCodec {
    fun encode(value: KeyEnvelopeAad): ByteArray =
        BoundedBinaryWriter(MAX_AAD_BYTES)
            .apply {
                writeMagic(MAGIC)
                writeU16(RecoveryContract.SCHEMA_VERSION)
                writeContractIdentity(value.candidate, value.runId)
                writeLp16Ascii(value.targetKind.contractId)
                writeU64(value.generation)
                writeU32(value.unitIndex)
                writeU64(value.plaintextStartInclusive)
                writeU64(value.plaintextEndExclusive)
                writeU32(value.cadenceSeconds)
                writeSha256(value.previousPublicationCiphertextSha256)
            }
            .toByteArray()

    fun decode(bytes: ByteArray): KeyEnvelopeAad {
        val reader = BoundedBinaryReader(bytes, MAX_AAD_BYTES)
        reader.expectMagic(MAGIC)
        reader.readAndValidateSchema()
        val (candidate, runId) = reader.readAndValidateContractIdentity()
        val result =
            KeyEnvelopeAad(
                candidate = candidate,
                runId = runId,
                targetKind = KeyEnvelopeTargetKind.fromContractId(reader.readLp16Ascii()),
                generation = reader.readU64(),
                unitIndex = reader.readU32(),
                plaintextStartInclusive = reader.readU64(),
                plaintextEndExclusive = reader.readU64(),
                cadenceSeconds = reader.readU32(),
                previousPublicationCiphertextSha256 = reader.readSha256(),
            )
        reader.requireFinished()
        return result
    }

    const val MAGIC = "DORAKE01"
}

private data class GenerationRange(
    val generation: ULong,
    val unitIndex: ULong,
    val plaintextStartInclusive: ULong,
    val plaintextEndExclusive: ULong,
    val cadenceSeconds: ULong,
)

private fun validateGenerationRangeAndU32(
    value: GenerationRange,
    allowEmptyRange: Boolean = false,
) {
    contractRequire(value.generation >= FIRST_GENERATION) { "Generation must start at one" }
    contractRequire(value.unitIndex <= RecoveryContract.U32_MAX) { "Unit index does not fit U32" }
    contractRequire(value.cadenceSeconds <= RecoveryContract.U32_MAX) {
        "Cadence does not fit U32"
    }
    contractRequire(
        value.plaintextEndExclusive > value.plaintextStartInclusive ||
            (allowEmptyRange && value.plaintextEndExclusive == value.plaintextStartInclusive)
    ) {
        "Plaintext range is invalid"
    }
    validatePlaintextEnd(value.plaintextEndExclusive, "Plaintext end")
}

private const val MAX_AAD_BYTES = 512
private const val FIRST_GENERATION = 1UL
