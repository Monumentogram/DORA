package com.monumentogram.dora.poc.recovery.contract

enum class RecoveryQuarantineArtifactRole(val contractId: String) {
    KEY_CONFIRMATION("KEY_CONFIRMATION"),
    MICROFILE_KEY_ENVELOPE("MICROFILE_KEY_ENVELOPE"),
    MICROFILE_CIPHERTEXT("MICROFILE_CIPHERTEXT"),
    MANIFEST_KEY_ENVELOPE("MANIFEST_KEY_ENVELOPE"),
    MANIFEST_CIPHERTEXT("MANIFEST_CIPHERTEXT"),
    STREAM_KEY_ENVELOPE("STREAM_KEY_ENVELOPE"),
    STREAM_CIPHERTEXT("STREAM_CIPHERTEXT"),
    CHECKPOINT_KEY_ENVELOPE("CHECKPOINT_KEY_ENVELOPE"),
    CHECKPOINT_CIPHERTEXT("CHECKPOINT_CIPHERTEXT"),
    UNKNOWN_REGULAR("UNKNOWN_REGULAR"),
}

enum class RecoveryQuarantineObservedState {
    TEMP_ONLY,
    TEMP_AND_FINAL,
    FINAL_ORPHAN,
    SQLITE_POINTS_TO_TEMP,
    UNKNOWN_OR_NON_ALLOWLISTED_NAME,
}

data class RecoveryQuarantineIntentInput(
    val candidate: RecoveryCandidate,
    val runId: RunId,
    val sourceRelativeName: String,
    val artifactRole: RecoveryQuarantineArtifactRole,
    val sourceBytes: ULong,
    val sourceSha256: Sha256Value,
) {
    init {
        val roleAllowed =
            when (candidate) {
                RecoveryCandidate.MICROFILE ->
                    artifactRole in
                        setOf(
                            RecoveryQuarantineArtifactRole.KEY_CONFIRMATION,
                            RecoveryQuarantineArtifactRole.MICROFILE_KEY_ENVELOPE,
                            RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT,
                            RecoveryQuarantineArtifactRole.MANIFEST_KEY_ENVELOPE,
                            RecoveryQuarantineArtifactRole.MANIFEST_CIPHERTEXT,
                            RecoveryQuarantineArtifactRole.UNKNOWN_REGULAR,
                        )
                RecoveryCandidate.STREAM ->
                    artifactRole in
                        setOf(
                            RecoveryQuarantineArtifactRole.KEY_CONFIRMATION,
                            RecoveryQuarantineArtifactRole.STREAM_KEY_ENVELOPE,
                            RecoveryQuarantineArtifactRole.STREAM_CIPHERTEXT,
                            RecoveryQuarantineArtifactRole.CHECKPOINT_KEY_ENVELOPE,
                            RecoveryQuarantineArtifactRole.CHECKPOINT_CIPHERTEXT,
                            RecoveryQuarantineArtifactRole.UNKNOWN_REGULAR,
                        )
            }
        contractRequire(roleAllowed) { "Quarantine role does not belong to its candidate" }
        contractRequire(sourceRelativeName.isNotEmpty()) { "Quarantine source name is empty" }
        contractRequire(sourceRelativeName.length <= MAX_SOURCE_NAME_BYTES) {
            "Quarantine source name exceeds its bound"
        }
        contractRequire(sourceBytes <= Long.MAX_VALUE.toULong()) {
            "Quarantine source size exceeds the journal INTEGER bound"
        }
    }

    companion object {
        const val MAX_SOURCE_NAME_BYTES = 512
    }
}

object RecoveryQuarantineIntent {
    fun encodedIdentity(value: RecoveryQuarantineIntentInput): ByteArray =
        BoundedBinaryWriter(MAX_INPUT_BYTES)
            .apply {
                writeLp16Ascii(RecoveryContract.PROTOCOL_ID, RecoveryContract.MAX_PROTOCOL_ID_BYTES)
                writeLp16Ascii(value.candidate.contractId, RecoveryContract.MAX_CANDIDATE_ID_BYTES)
                writeRunId(value.runId)
                writeLp16Ascii(
                    value.sourceRelativeName,
                    RecoveryQuarantineIntentInput.MAX_SOURCE_NAME_BYTES,
                )
                writeLp16Ascii(value.artifactRole.contractId, MAX_ROLE_BYTES)
                writeU64(value.sourceBytes)
                writeSha256(value.sourceSha256)
            }
            .toByteArray()

    fun calculate(value: RecoveryQuarantineIntentInput): Sha256Value =
        Sha256Value.calculate(encodedIdentity(value))

    fun destination(value: RecoveryQuarantineIntentInput): String =
        "objects/q-${calculate(value).toLowercaseHex()}.bin"

    private const val MAX_ROLE_BYTES = 32
    private const val MAX_INPUT_BYTES =
        2 +
            RecoveryContract.MAX_PROTOCOL_ID_BYTES +
            2 +
            RecoveryContract.MAX_CANDIDATE_ID_BYTES +
            RunId.SIZE_BYTES +
            2 +
            RecoveryQuarantineIntentInput.MAX_SOURCE_NAME_BYTES +
            2 +
            MAX_ROLE_BYTES +
            8 +
            Sha256Value.SIZE_BYTES
}
