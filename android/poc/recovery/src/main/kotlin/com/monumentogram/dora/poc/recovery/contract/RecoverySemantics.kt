package com.monumentogram.dora.poc.recovery.contract

enum class RecoveryPublicationFamily {
    CHECKPOINT,
    MANIFEST,
}

@Suppress("LongParameterList")
class RecoveryPublicationPredecessor(
    val protocolId: String,
    val family: RecoveryPublicationFamily,
    val candidate: RecoveryCandidate,
    val runId: RunId,
    val generation: ULong,
    val recordedCiphertextSha256: Sha256Value,
    ciphertextBytes: ByteArray,
) {
    private val exactCiphertextBytes = ciphertextBytes.copyOf()

    internal fun ciphertextBytes(): ByteArray = exactCiphertextBytes.copyOf()

    companion object {
        fun checkpoint(
            value: RecoveryCheckpoint,
            ciphertextBytes: ByteArray,
            recordedCiphertextSha256: Sha256Value = Sha256Value.calculate(ciphertextBytes),
        ): RecoveryPublicationPredecessor =
            RecoveryPublicationPredecessor(
                protocolId = RecoveryContract.PROTOCOL_ID,
                family = RecoveryPublicationFamily.CHECKPOINT,
                candidate = value.candidate,
                runId = value.runId,
                generation = value.generation,
                recordedCiphertextSha256 = recordedCiphertextSha256,
                ciphertextBytes = ciphertextBytes,
            )

        fun manifest(
            value: RecoveryManifest,
            ciphertextBytes: ByteArray,
            recordedCiphertextSha256: Sha256Value = Sha256Value.calculate(ciphertextBytes),
        ): RecoveryPublicationPredecessor =
            RecoveryPublicationPredecessor(
                protocolId = RecoveryContract.PROTOCOL_ID,
                family = RecoveryPublicationFamily.MANIFEST,
                candidate = value.candidate,
                runId = value.runId,
                generation = value.generation,
                recordedCiphertextSha256 = recordedCiphertextSha256,
                ciphertextBytes = ciphertextBytes,
            )
    }
}

object RecoveryPublicationChainValidator {
    fun validateCheckpoint(
        candidate: RecoveryCheckpoint,
        predecessor: RecoveryPublicationPredecessor?,
    ) {
        validate(
            family = RecoveryPublicationFamily.CHECKPOINT,
            candidate = candidate.candidate,
            runId = candidate.runId,
            generation = candidate.generation,
            previousCiphertextSha256 = candidate.previousCheckpointCiphertextSha256,
            predecessor = predecessor,
        )
    }

    fun validateManifest(
        candidate: RecoveryManifest,
        predecessor: RecoveryPublicationPredecessor?,
    ) {
        validate(
            family = RecoveryPublicationFamily.MANIFEST,
            candidate = candidate.candidate,
            runId = candidate.runId,
            generation = candidate.generation,
            previousCiphertextSha256 = candidate.previousManifestCiphertextSha256,
            predecessor = predecessor,
        )
    }

    @Suppress("LongParameterList")
    private fun validate(
        family: RecoveryPublicationFamily,
        candidate: RecoveryCandidate,
        runId: RunId,
        generation: ULong,
        previousCiphertextSha256: Sha256Value,
        predecessor: RecoveryPublicationPredecessor?,
    ) {
        if (generation == FIRST_GENERATION) {
            contractRequire(predecessor == null) {
                "A genesis publication must not have a predecessor"
            }
            return
        }

        val exactPredecessor =
            predecessor
                ?: throw RecoveryContractException(
                    "A non-genesis publication requires its exact predecessor"
                )
        contractRequire(exactPredecessor.protocolId == RecoveryContract.PROTOCOL_ID) {
            "Publication predecessor protocol does not match the active contract"
        }
        contractRequire(exactPredecessor.family == family) {
            "Publication predecessor belongs to a different contract family"
        }
        contractRequire(exactPredecessor.candidate == candidate) {
            "Publication predecessor candidate does not match"
        }
        contractRequire(exactPredecessor.runId == runId) {
            "Publication predecessor run identity does not match"
        }
        contractRequire(exactPredecessor.generation < ULong.MAX_VALUE) {
            "Publication predecessor generation cannot be incremented"
        }
        contractRequire(generation == exactPredecessor.generation + 1UL) {
            "Publication generation must increase by exactly one"
        }

        val exactCiphertextSha256 = Sha256Value.calculate(exactPredecessor.ciphertextBytes())
        contractRequire(exactPredecessor.recordedCiphertextSha256 == exactCiphertextSha256) {
            "Recorded predecessor digest does not match its exact ciphertext bytes"
        }
        contractRequire(previousCiphertextSha256 == exactPredecessor.recordedCiphertextSha256) {
            "Publication previous digest does not match the predecessor identity"
        }
    }

    private const val FIRST_GENERATION = 1UL
}

object RecoveryCadenceContract {
    const val FIVE_SECONDS = 5UL
    const val FIFTEEN_SECONDS = 15UL
    const val THIRTY_SECONDS = 30UL
    const val NOT_APPLICABLE_SECONDS = 0UL

    fun requireDeclaredMicrofileCadence(value: ULong) {
        contractRequire(
            value == FIVE_SECONDS || value == FIFTEEN_SECONDS || value == THIRTY_SECONDS
        ) {
            "Microfile cadence must be exactly 5, 15, or 30 seconds"
        }
    }

    fun requireNotApplicableCadence(value: ULong) {
        contractRequire(value == NOT_APPLICABLE_SECONDS) {
            "Cadence must be zero when it is not applicable"
        }
    }
}

internal fun validateCandidateBinding(
    actual: RecoveryCandidate,
    expected: RecoveryCandidate,
    subject: String,
) {
    contractRequire(actual == expected) {
        "$subject is bound to ${expected.contractId}"
    }
}

internal fun validateGenerationAndPreviousDigest(
    generation: ULong,
    previousCiphertextSha256: Sha256Value,
    subject: String,
) {
    contractRequire(generation >= FIRST_GENERATION) { "$subject generation must start at one" }
    if (generation == FIRST_GENERATION) {
        contractRequire(previousCiphertextSha256.isZero()) {
            "$subject genesis previous digest must be zero"
        }
    } else {
        contractRequire(!previousCiphertextSha256.isZero()) {
            "$subject previous digest must not be zero after genesis"
        }
    }
}

internal fun validatePlaintextEnd(
    value: ULong,
    subject: String,
) {
    contractRequire(value <= RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN) {
        "$subject exceeds the bounded run maximum"
    }
}

private const val FIRST_GENERATION = 1UL
