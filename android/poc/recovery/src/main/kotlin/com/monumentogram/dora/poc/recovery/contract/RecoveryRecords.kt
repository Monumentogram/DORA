package com.monumentogram.dora.poc.recovery.contract

import java.util.Collections

data class RecoveryCheckpoint(
    val candidate: RecoveryCandidate,
    val runId: RunId,
    val generation: ULong,
    val previousCheckpointCiphertextSha256: Sha256Value,
    val durableNonFinalSegmentCount: ULong,
    val ciphertextPrefixBytes: ULong,
    val committedEndExclusive: ULong,
    val streamKeyEnvelopeBytes: ULong,
    val streamKeyEnvelopeSha256: Sha256Value,
    val streamCiphertextRelativeName: String,
    val streamKeyEnvelopeRelativeName: String,
) {
    init {
        validateCandidateBinding(candidate, RecoveryCandidate.STREAM, "Recovery checkpoint")
        validateGenerationAndPreviousDigest(
            generation,
            previousCheckpointCiphertextSha256,
            "Checkpoint",
        )
        contractRequire(durableNonFinalSegmentCount <= RecoveryContract.U32_MAX) {
            "Checkpoint q does not fit U32"
        }
        contractRequire(
            ciphertextPrefixBytes ==
                RecoveryStreamingMath.ciphertextPrefixBytes(durableNonFinalSegmentCount)
        ) {
            "Checkpoint ciphertext prefix does not match q"
        }
        contractRequire(
            committedEndExclusive ==
                RecoveryStreamingMath.recoveredEndExclusive(durableNonFinalSegmentCount)
        ) {
            "Checkpoint committed end does not match the one-segment-lookahead contract"
        }
        validatePlaintextEnd(committedEndExclusive, "Checkpoint committed end")
        RecoveryRelativeNames.validateStreamCiphertext(streamCiphertextRelativeName)
        RecoveryRelativeNames.validateStreamKeyEnvelope(streamKeyEnvelopeRelativeName)
    }
}

object RecoveryCheckpointCodec {
    fun encode(value: RecoveryCheckpoint): ByteArray =
        BoundedBinaryWriter(RecoveryContract.MAX_GENERAL_RECORD_BYTES)
            .apply {
                writeMagic(MAGIC)
                writeU16(RecoveryContract.SCHEMA_VERSION)
                writeContractIdentity(value.candidate, value.runId)
                writeU64(value.generation)
                writeSha256(value.previousCheckpointCiphertextSha256)
                writeU32(value.durableNonFinalSegmentCount)
                writeU64(value.ciphertextPrefixBytes)
                writeU64(value.committedEndExclusive)
                writeU64(value.streamKeyEnvelopeBytes)
                writeSha256(value.streamKeyEnvelopeSha256)
                writeLp16Ascii(value.streamCiphertextRelativeName)
                writeLp16Ascii(value.streamKeyEnvelopeRelativeName)
            }
            .toByteArray()

    fun decode(bytes: ByteArray): RecoveryCheckpoint {
        val reader = BoundedBinaryReader(bytes, RecoveryContract.MAX_GENERAL_RECORD_BYTES)
        reader.expectMagic(MAGIC)
        reader.readAndValidateSchema()
        val (candidate, runId) = reader.readAndValidateContractIdentity()
        val result =
            RecoveryCheckpoint(
                candidate = candidate,
                runId = runId,
                generation = reader.readU64(),
                previousCheckpointCiphertextSha256 = reader.readSha256(),
                durableNonFinalSegmentCount = reader.readU32(),
                ciphertextPrefixBytes = reader.readU64(),
                committedEndExclusive = reader.readU64(),
                streamKeyEnvelopeBytes = reader.readU64(),
                streamKeyEnvelopeSha256 = reader.readSha256(),
                streamCiphertextRelativeName = reader.readLp16Ascii(),
                streamKeyEnvelopeRelativeName = reader.readLp16Ascii(),
            )
        reader.requireFinished()
        return result
    }

    const val MAGIC = "DORARC01"
}

data class RecoveryManifestEntry(
    val unitIndex: ULong,
    val plaintextStartInclusive: ULong,
    val plaintextEndExclusive: ULong,
    val cadenceSeconds: ULong,
    val ciphertextBytes: ULong,
    val ciphertextSha256: Sha256Value,
    val keyEnvelopeBytes: ULong,
    val keyEnvelopeSha256: Sha256Value,
    val ciphertextRelativeName: String,
    val keyEnvelopeRelativeName: String,
) {
    init {
        contractRequire(unitIndex <= RecoveryContract.U32_MAX) {
            "Manifest unit index does not fit U32"
        }
        contractRequire(cadenceSeconds <= RecoveryContract.U32_MAX) {
            "Manifest cadence does not fit U32"
        }
        RecoveryCadenceContract.requireDeclaredMicrofileCadence(cadenceSeconds)
        contractRequire(plaintextEndExclusive > plaintextStartInclusive) {
            "Manifest ranges must be non-empty"
        }
        validatePlaintextEnd(plaintextEndExclusive, "Manifest entry plaintext end")
        RecoveryRelativeNames.validateMicrofileCiphertext(ciphertextRelativeName, unitIndex)
        RecoveryRelativeNames.validateMicrofileKeyEnvelope(keyEnvelopeRelativeName, unitIndex)
    }
}

class RecoveryManifest
private constructor(
    val candidate: RecoveryCandidate,
    val runId: RunId,
    val generation: ULong,
    val previousManifestCiphertextSha256: Sha256Value,
    val committedEndExclusive: ULong,
    entries: List<RecoveryManifestEntry>,
) {
    val entries: List<RecoveryManifestEntry> = Collections.unmodifiableList(ArrayList(entries))

    init {
        validateCandidateBinding(candidate, RecoveryCandidate.MICROFILE, "Recovery manifest")
        validateGenerationAndPreviousDigest(
            generation,
            previousManifestCiphertextSha256,
            "Manifest",
        )
        validatePlaintextEnd(committedEndExclusive, "Manifest committed end")
        validateEntries(this.entries, committedEndExclusive)
    }

    override fun equals(other: Any?): Boolean =
        other is RecoveryManifest &&
            candidate == other.candidate &&
            runId == other.runId &&
            generation == other.generation &&
            previousManifestCiphertextSha256 == other.previousManifestCiphertextSha256 &&
            committedEndExclusive == other.committedEndExclusive &&
            entries == other.entries

    override fun hashCode(): Int {
        var result = candidate.hashCode()
        result = HASH_FACTOR * result + runId.hashCode()
        result = HASH_FACTOR * result + generation.hashCode()
        result = HASH_FACTOR * result + previousManifestCiphertextSha256.hashCode()
        result = HASH_FACTOR * result + committedEndExclusive.hashCode()
        result = HASH_FACTOR * result + entries.hashCode()
        return result
    }

    companion object {
        @Suppress("LongParameterList")
        fun create(
            candidate: RecoveryCandidate,
            runId: RunId,
            generation: ULong,
            previousManifestCiphertextSha256: Sha256Value,
            committedEndExclusive: ULong,
            entries: List<RecoveryManifestEntry>,
        ): RecoveryManifest =
            RecoveryManifest(
                candidate = candidate,
                runId = runId,
                generation = generation,
                previousManifestCiphertextSha256 = previousManifestCiphertextSha256,
                committedEndExclusive = committedEndExclusive,
                entries = entries,
            )

        private fun validateEntries(
            entries: List<RecoveryManifestEntry>,
            committedEndExclusive: ULong,
        ) {
            contractRequire(entries.size <= RecoveryContract.MAX_MANIFEST_ENTRIES) {
                "Manifest exceeds ${RecoveryContract.MAX_MANIFEST_ENTRIES} entries"
            }
            var expectedStart = 0UL
            entries.forEachIndexed { index, entry ->
                contractRequire(entry.unitIndex == index.toULong()) {
                    "Manifest unit indices must start at zero and increase by one"
                }
                contractRequire(entry.plaintextStartInclusive == expectedStart) {
                    "Manifest plaintext ranges must start at zero and remain contiguous"
                }
                expectedStart = entry.plaintextEndExclusive
            }
            contractRequire(committedEndExclusive == expectedStart) {
                "Manifest committed end must equal the final entry end, or zero when empty"
            }
        }

        private const val HASH_FACTOR = 31
    }
}

object RecoveryManifestCodec {
    fun encode(value: RecoveryManifest): ByteArray =
        BoundedBinaryWriter(RecoveryContract.MAX_MANIFEST_PLAINTEXT_BYTES)
            .apply {
                writeMagic(MAGIC)
                writeU16(RecoveryContract.SCHEMA_VERSION)
                writeContractIdentity(value.candidate, value.runId)
                writeU64(value.generation)
                writeSha256(value.previousManifestCiphertextSha256)
                writeU64(value.committedEndExclusive)
                writeU32(value.entries.size.toULong())
                value.entries.forEach { entry -> writeEntry(entry) }
            }
            .toByteArray()

    fun decode(bytes: ByteArray): RecoveryManifest {
        val reader = BoundedBinaryReader(bytes, RecoveryContract.MAX_MANIFEST_PLAINTEXT_BYTES)
        reader.expectMagic(MAGIC)
        reader.readAndValidateSchema()
        val (candidate, runId) = reader.readAndValidateContractIdentity()
        val generation = reader.readU64()
        val previousDigest = reader.readSha256()
        val committedEndExclusive = reader.readU64()
        val entryCount = reader.readU32()
        contractRequire(entryCount <= RecoveryContract.MAX_MANIFEST_ENTRIES.toULong()) {
            "Manifest exceeds ${RecoveryContract.MAX_MANIFEST_ENTRIES} entries"
        }
        val entries = List(entryCount.toInt()) { reader.readEntry() }
        reader.requireFinished()
        return RecoveryManifest.create(
            candidate = candidate,
            runId = runId,
            generation = generation,
            previousManifestCiphertextSha256 = previousDigest,
            committedEndExclusive = committedEndExclusive,
            entries = entries,
        )
    }

    private fun BoundedBinaryWriter.writeEntry(value: RecoveryManifestEntry) {
        writeU32(value.unitIndex)
        writeU64(value.plaintextStartInclusive)
        writeU64(value.plaintextEndExclusive)
        writeU32(value.cadenceSeconds)
        writeU64(value.ciphertextBytes)
        writeSha256(value.ciphertextSha256)
        writeU64(value.keyEnvelopeBytes)
        writeSha256(value.keyEnvelopeSha256)
        writeLp16Ascii(value.ciphertextRelativeName)
        writeLp16Ascii(value.keyEnvelopeRelativeName)
    }

    private fun BoundedBinaryReader.readEntry(): RecoveryManifestEntry =
        RecoveryManifestEntry(
            unitIndex = readU32(),
            plaintextStartInclusive = readU64(),
            plaintextEndExclusive = readU64(),
            cadenceSeconds = readU32(),
            ciphertextBytes = readU64(),
            ciphertextSha256 = readSha256(),
            keyEnvelopeBytes = readU64(),
            keyEnvelopeSha256 = readSha256(),
            ciphertextRelativeName = readLp16Ascii(),
            keyEnvelopeRelativeName = readLp16Ascii(),
        )

    const val MAGIC = "DORARM01"
}
