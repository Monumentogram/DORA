package com.monumentogram.dora.poc.recovery.contract

data class RecoveryProcessingIntentInput(
    val candidate: RecoveryCandidate,
    val runId: RunId,
    val unitIndex: ULong,
    val plaintextStartInclusive: ULong,
    val plaintextEndExclusive: ULong,
    val ciphertextSha256: Sha256Value,
) {
    init {
        validateCandidateBinding(candidate, RecoveryCandidate.MICROFILE, "Processing intent")
        contractRequire(unitIndex <= RecoveryContract.U32_MAX) {
            "Processing-intent unit index does not fit U32"
        }
        contractRequire(plaintextEndExclusive > plaintextStartInclusive) {
            "Processing-intent plaintext range must be non-empty"
        }
        validatePlaintextEnd(plaintextEndExclusive, "Processing-intent plaintext end")
    }
}

object RecoveryProcessingIntent {
    fun encodedIdentity(value: RecoveryProcessingIntentInput): ByteArray =
        BoundedBinaryWriter(MAX_INPUT_BYTES)
            .apply {
                writeLp16Ascii(RecoveryContract.PROTOCOL_ID, RecoveryContract.MAX_PROTOCOL_ID_BYTES)
                writeLp16Ascii(value.candidate.contractId, RecoveryContract.MAX_CANDIDATE_ID_BYTES)
                writeBytes(value.runId.toByteArray())
                writeU32(value.unitIndex)
                writeU64(value.plaintextStartInclusive)
                writeU64(value.plaintextEndExclusive)
                writeSha256(value.ciphertextSha256)
            }
            .toByteArray()

    fun calculate(value: RecoveryProcessingIntentInput): Sha256Value =
        Sha256Value.calculate(encodedIdentity(value))

    private const val MAX_INPUT_BYTES =
        2 +
            RecoveryContract.MAX_PROTOCOL_ID_BYTES +
            2 +
            RecoveryContract.MAX_CANDIDATE_ID_BYTES +
            RunId.SIZE_BYTES +
            4 +
            8 +
            8 +
            Sha256Value.SIZE_BYTES
}
