package com.monumentogram.dora.poc.recovery.contract

data class KeyConfirmationValue(
    val candidate: RecoveryCandidate,
    val runId: RunId,
) {
    val canonicalAlias: String
        get() = CanonicalRecoveryAlias.forRun(runId)

    val canonicalAliasSha256: Sha256Value
        get() = CanonicalRecoveryAlias.sha256(runId)
}

object CanonicalRecoveryAlias {
    const val PREFIX = "android-keystore://dora.poc.recovery.v1."
    const val EXACT_ASCII_BYTES = 76

    fun forRun(runId: RunId): String {
        val value = PREFIX + runId.toCanonicalString()
        contractRequire(
            value.length == EXACT_ASCII_BYTES && value.all { it.code in ASCII_MIN..ASCII_MAX }
        ) {
            "Canonical Recovery alias must contain exactly $EXACT_ASCII_BYTES ASCII bytes"
        }
        return value
    }

    fun sha256(runId: RunId): Sha256Value =
        Sha256Value.calculate(forRun(runId).map { it.code.toByte() }.toByteArray())

    private const val ASCII_MIN = 1
    private const val ASCII_MAX = 0x7f
}

object KeyConfirmationPlaintextCodec {
    fun encode(value: KeyConfirmationValue): ByteArray =
        KeyConfirmationBinaryCodec.encode(MAGIC, value)

    fun decode(bytes: ByteArray): KeyConfirmationValue =
        KeyConfirmationBinaryCodec.decode(MAGIC, bytes)

    const val MAGIC = "DORAKC01"
}

object KeyConfirmationAadCodec {
    fun encode(value: KeyConfirmationValue): ByteArray =
        KeyConfirmationBinaryCodec.encode(MAGIC, value)

    fun decode(bytes: ByteArray): KeyConfirmationValue =
        KeyConfirmationBinaryCodec.decode(MAGIC, bytes)

    const val MAGIC = "DORAKA01"
}

object KeyConfirmationBinaryContract {
    const val MAXIMUM_ENCODED_BYTES = 222
}

private object KeyConfirmationBinaryCodec {
    fun encode(
        magic: String,
        value: KeyConfirmationValue,
    ): ByteArray =
        BoundedBinaryWriter(KeyConfirmationBinaryContract.MAXIMUM_ENCODED_BYTES)
            .apply {
                writeMagic(magic)
                writeU16(RecoveryContract.SCHEMA_VERSION)
                writeContractIdentity(value.candidate, value.runId)
                writeSha256(value.canonicalAliasSha256)
            }
            .toByteArray()

    fun decode(
        magic: String,
        bytes: ByteArray,
    ): KeyConfirmationValue {
        val reader = BoundedBinaryReader(bytes, KeyConfirmationBinaryContract.MAXIMUM_ENCODED_BYTES)
        reader.expectMagic(magic)
        reader.readAndValidateSchema()
        val (candidate, runId) = reader.readAndValidateContractIdentity()
        val recordedAliasSha256 = reader.readSha256()
        reader.requireFinished()
        val result = KeyConfirmationValue(candidate, runId)
        contractRequire(recordedAliasSha256 == result.canonicalAliasSha256) {
            "Canonical alias SHA-256 does not match the exact run identity"
        }
        return result
    }
}

enum class KeyRecoveryClassification {
    KEY_REF_COLLISION,
    INCOMPLETE_KEY_BOOTSTRAP,
    KEY_CONFIRMATION_MISSING,
    CORRUPT_KEY_CONFIRMATION,
    KEY_UNAVAILABLE,
    KEY_UNAVAILABLE_KEY_MISMATCH,
    CORRUPT_KEY_ENVELOPE,
    KEY_ENVELOPE_AUTH_FAILURE,
}

enum class KeyConfirmationDecryptOutcome {
    AUTHENTICATION_OR_AAD_FAILURE,
    SUCCESS,
    POST_DECRYPT_PLAINTEXT_MISMATCH,
    OTHER_FAILURE,
}

data class Key04Observation(
    val durableRunRowExists: Boolean,
    val confirmationFinalExists: Boolean,
    val pathTypeLengthAndSha256Match: Boolean,
    val approvedAliasExistsAndIsAccessible: Boolean,
    val exactActiveProtocolAadComputed: Boolean,
    val underlyingAliasKeyReplacedWithCiphertextIdentityPreserved: Boolean,
    val recoveryCreatedOrReplacedKey: Boolean,
    val decryptOutcome: KeyConfirmationDecryptOutcome,
)

enum class Kcf07PlaintextFailure {
    MALFORMED_PLAINTEXT,
    WRONG_MAGIC,
    WRONG_SCHEMA,
    WRONG_PROTOCOL_ID,
    WRONG_CANDIDATE_ID,
    WRONG_RUN_ID,
    WRONG_CANONICAL_ALIAS_SHA256,
}

data class Kcf07Observation(
    val storedCiphertextIdentityPasses: Boolean,
    val decryptSucceeds: Boolean,
    val plaintextFailure: Kcf07PlaintextFailure,
)

object KeyConfirmationRouting {
    fun classifyKey04(observation: Key04Observation): KeyRecoveryClassification {
        contractRequire(observation.durableRunRowExists) { "KEY-04 requires a durable run row" }
        contractRequire(observation.confirmationFinalExists) {
            "KEY-04 requires the confirmation final"
        }
        contractRequire(observation.pathTypeLengthAndSha256Match) {
            "KEY-04 requires path, type, recorded length and SHA-256 identity to match"
        }
        contractRequire(observation.approvedAliasExistsAndIsAccessible) {
            "KEY-04 requires the approved alias to be available"
        }
        contractRequire(observation.exactActiveProtocolAadComputed) {
            "KEY-04 requires exact active-protocol AAD"
        }
        contractRequire(observation.underlyingAliasKeyReplacedWithCiphertextIdentityPreserved) {
            "KEY-04 requires the controlled alias-key replacement fault"
        }
        contractRequire(!observation.recoveryCreatedOrReplacedKey) {
            "KEY-04 forbids Recovery key creation or replacement"
        }
        contractRequire(
            observation.decryptOutcome ==
                KeyConfirmationDecryptOutcome.AUTHENTICATION_OR_AAD_FAILURE
        ) {
            "KEY-04 is only an authentication/AAD failure; successful or post-decrypt interpretations are forbidden"
        }
        return KeyRecoveryClassification.KEY_UNAVAILABLE_KEY_MISMATCH
    }

    fun classifyKcf07(observation: Kcf07Observation): KeyRecoveryClassification {
        contractRequire(observation.storedCiphertextIdentityPasses) {
            "KCF-07 requires the stored ciphertext identity to pass"
        }
        contractRequire(observation.decryptSucceeds) {
            "KCF-07 requires successful decrypt before plaintext validation"
        }
        return KeyRecoveryClassification.CORRUPT_KEY_CONFIRMATION
    }
}

object RecoveryFaultCatalog {
    val orderedIds: List<String> =
        listOf(
            "COR-01",
            "COR-02",
            "COR-03",
            "COR-04",
            "COR-05",
            "COR-06",
            "TRU-01",
            "TRU-02",
            "TRU-03",
            "KEY-01",
            "KEY-02",
            "KEY-03",
            "KEY-04",
            "KEY-05",
            "KEY-06",
            "KEY-07",
            "SPL-01",
            "SPL-02",
            "SPL-03",
            "SPL-04",
            "SPL-05",
            "RBK-01",
            "RBK-02",
            "PAR-01",
            "QUA-01",
            "QUA-02",
            "QUA-03",
            "IDE-01",
            "IDE-02",
            "EVT-01",
            "CLN-01",
            "CLN-02",
            "CLN-03",
            "KCB-01",
            "KCB-02",
            "KCB-03",
            "KCB-04",
            "KCB-05",
            "KCB-06",
            "KCF-01",
            "KCF-02",
            "KCF-03",
            "KCF-04",
            "KCF-05",
            "KCF-06",
            "KCF-07",
        )

    const val EXPECTED_ROW_COUNT = 46
    const val PHASE_A_INJECTIONS = 184
    const val FULL_PHYSICAL_INJECTIONS = 138
    const val BASE_HARD_KILLS_PER_CANDIDATE = 120

    init {
        contractRequire(orderedIds.size == EXPECTED_ROW_COUNT) {
            "Recovery fault catalog must contain 46 rows"
        }
        contractRequire(orderedIds.toSet().size == EXPECTED_ROW_COUNT) {
            "Recovery fault IDs must be unique"
        }
        contractRequire(orderedIds.count { it == KEY_04_ID } == 1) {
            "Recovery fault catalog must contain one KEY-04"
        }
    }

    private const val KEY_04_ID = "KEY-04"
}
