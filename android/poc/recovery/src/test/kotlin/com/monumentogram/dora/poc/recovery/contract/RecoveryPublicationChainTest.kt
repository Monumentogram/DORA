package com.monumentogram.dora.poc.recovery.contract

import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryPublicationChainTest {
    @Test
    fun `checkpoint and manifest accept only the exact generation plus one predecessor`() {
        val checkpointBytes = "checkpoint-generation-one".encodeToByteArray()
        val checkpointDigest = Sha256Value.calculate(checkpointBytes)
        val checkpointPredecessor = checkpoint(generation = 1UL, previousDigest = Sha256Value.ZERO)
        val checkpointCandidate = checkpoint(generation = 2UL, previousDigest = checkpointDigest)
        RecoveryPublicationChainValidator.validateCheckpoint(
            checkpointCandidate,
            RecoveryPublicationPredecessor.checkpoint(checkpointPredecessor, checkpointBytes),
        )

        val manifestBytes = "manifest-generation-one".encodeToByteArray()
        val manifestDigest = Sha256Value.calculate(manifestBytes)
        val manifestPredecessor = manifest(generation = 1UL, previousDigest = Sha256Value.ZERO)
        val manifestCandidate = manifest(generation = 2UL, previousDigest = manifestDigest)
        RecoveryPublicationChainValidator.validateManifest(
            manifestCandidate,
            RecoveryPublicationPredecessor.manifest(manifestPredecessor, manifestBytes),
        )
    }

    @Test
    fun `checkpoint rejects duplicate and skipped generations`() {
        val bytes = "checkpoint-generation-one".encodeToByteArray()
        val digest = Sha256Value.calculate(bytes)
        val generationOne = checkpoint(generation = 1UL, previousDigest = Sha256Value.ZERO)
        val predecessor = RecoveryPublicationPredecessor.checkpoint(generationOne, bytes)

        assertThrows(RecoveryContractException::class.java) {
            RecoveryPublicationChainValidator.validateCheckpoint(
                checkpoint(generation = 2UL, previousDigest = digest),
                predecessor.copyGeneration(2UL),
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryPublicationChainValidator.validateCheckpoint(
                checkpoint(generation = 3UL, previousDigest = digest),
                predecessor,
            )
        }
    }

    @Test
    fun `checkpoint rejects wrong predecessor protocol candidate run family ciphertext and digest`() {
        val bytes = "checkpoint-generation-one".encodeToByteArray()
        val digest = Sha256Value.calculate(bytes)
        val candidate = checkpoint(generation = 2UL, previousDigest = digest)
        val predecessor =
            RecoveryPublicationPredecessor.checkpoint(
                checkpoint(generation = 1UL, previousDigest = Sha256Value.ZERO),
                bytes,
            )

        listOf(
                predecessor.copyProtocol("poc-recovery-protocol-stage0-v0.5"),
                predecessor.copyCandidate(RecoveryCandidate.MICROFILE),
                predecessor.copyRun(OTHER_RUN_ID),
                predecessor.copyFamily(RecoveryPublicationFamily.MANIFEST),
                predecessor.copyCiphertext("wrong-ciphertext".encodeToByteArray()),
                predecessor.copyRecordedDigest(DIGEST_20_3F),
            )
            .forEach { wrongPredecessor ->
                assertThrows(RecoveryContractException::class.java) {
                    RecoveryPublicationChainValidator.validateCheckpoint(
                        candidate,
                        wrongPredecessor,
                    )
                }
            }
    }

    @Test
    fun `genesis rejects a supplied predecessor and non-genesis requires one`() {
        val bytes = "checkpoint-generation-one".encodeToByteArray()
        val predecessor =
            RecoveryPublicationPredecessor.checkpoint(
                checkpoint(generation = 1UL, previousDigest = Sha256Value.ZERO),
                bytes,
            )
        assertThrows(RecoveryContractException::class.java) {
            RecoveryPublicationChainValidator.validateCheckpoint(
                checkpoint(generation = 1UL, previousDigest = Sha256Value.ZERO),
                predecessor,
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryPublicationChainValidator.validateCheckpoint(
                checkpoint(generation = 2UL, previousDigest = Sha256Value.calculate(bytes)),
                null,
            )
        }
    }

    private fun checkpoint(
        generation: ULong,
        previousDigest: Sha256Value,
        runId: RunId = RUN_ID,
    ): RecoveryCheckpoint =
        RecoveryCheckpoint(
            candidate = RecoveryCandidate.STREAM,
            runId = runId,
            generation = generation,
            previousCheckpointCiphertextSha256 = previousDigest,
            durableNonFinalSegmentCount = 2UL,
            ciphertextPrefixBytes = 8_192UL,
            committedEndExclusive = 4_056UL,
            streamKeyEnvelopeBytes = 1UL,
            streamKeyEnvelopeSha256 = DIGEST_20_3F,
            streamCiphertextRelativeName = RecoveryRelativeNames.streamCiphertext(),
            streamKeyEnvelopeRelativeName = RecoveryRelativeNames.streamKeyEnvelope(),
        )

    private fun manifest(
        generation: ULong,
        previousDigest: Sha256Value,
    ): RecoveryManifest =
        RecoveryManifest.create(
            candidate = RecoveryCandidate.MICROFILE,
            runId = RUN_ID,
            generation = generation,
            previousManifestCiphertextSha256 = previousDigest,
            committedEndExclusive = 0UL,
            entries = emptyList(),
        )

    private fun RecoveryPublicationPredecessor.copyProtocol(
        value: String
    ): RecoveryPublicationPredecessor = copy(protocolId = value)

    private fun RecoveryPublicationPredecessor.copyFamily(
        value: RecoveryPublicationFamily
    ): RecoveryPublicationPredecessor = copy(family = value)

    private fun RecoveryPublicationPredecessor.copyCandidate(
        value: RecoveryCandidate
    ): RecoveryPublicationPredecessor = copy(candidate = value)

    private fun RecoveryPublicationPredecessor.copyRun(
        value: RunId
    ): RecoveryPublicationPredecessor = copy(runId = value)

    private fun RecoveryPublicationPredecessor.copyGeneration(
        value: ULong
    ): RecoveryPublicationPredecessor = copy(generation = value)

    private fun RecoveryPublicationPredecessor.copyCiphertext(
        value: ByteArray
    ): RecoveryPublicationPredecessor =
        copy(
            recordedCiphertextSha256 = Sha256Value.calculate(value),
            ciphertextBytes = value,
        )

    private fun RecoveryPublicationPredecessor.copyRecordedDigest(
        value: Sha256Value
    ): RecoveryPublicationPredecessor = copy(recordedCiphertextSha256 = value)

    @Suppress("LongParameterList")
    private fun RecoveryPublicationPredecessor.copy(
        protocolId: String = this.protocolId,
        family: RecoveryPublicationFamily = this.family,
        candidate: RecoveryCandidate = this.candidate,
        runId: RunId = this.runId,
        generation: ULong = this.generation,
        recordedCiphertextSha256: Sha256Value = this.recordedCiphertextSha256,
        ciphertextBytes: ByteArray = this.ciphertextBytes(),
    ): RecoveryPublicationPredecessor =
        RecoveryPublicationPredecessor(
            protocolId = protocolId,
            family = family,
            candidate = candidate,
            runId = runId,
            generation = generation,
            recordedCiphertextSha256 = recordedCiphertextSha256,
            ciphertextBytes = ciphertextBytes,
        )

    private companion object {
        val RUN_ID = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")
        val OTHER_RUN_ID = RunId.fromCanonicalString("11112233-4455-6677-8899-aabbccddeeff")
        val DIGEST_20_3F = Sha256Value.fromBytes(ByteArray(32) { (it + 32).toByte() })
    }
}
