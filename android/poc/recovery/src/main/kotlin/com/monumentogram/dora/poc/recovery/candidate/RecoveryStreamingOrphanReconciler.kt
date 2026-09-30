@file:Suppress(
    "LongMethod",
    "ReturnCount",
    "LongParameterList",
    "ComplexCondition",
    "CyclomaticComplexMethod",
)

package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryCheckpoint
import com.monumentogram.dora.poc.recovery.contract.RecoveryContract
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointIdentityInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingWitnessInput
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value

/**
 * Only named checkpoint publications. This port never opens stream/stream.ct or inventories files.
 */
internal fun interface RecoveryStreamingOrphanArtifacts {
    fun load(
        runId: RunId,
        name: String,
        role: RecoveryQuarantineArtifactRole,
    ): RecoveryArtifactBytes?
}

internal fun interface RecoveryStreamingOrphanHandler {
    /** Invoked under the controller's run lease after exact journal-chain validation. */
    fun reconcile(
        witness: RecoveryStreamingWitnessInput,
        chain: List<RecoveryStreamingCheckpointRow>,
    ): RecoveryStreamingReconciliationResult
}

/**
 * Authentication and Q01-Q05 only. No checkpoint row is constructed or inserted, no stream is
 * decrypted, and no outcome/range is created without its committed checkpoint parent.
 */
internal class RecoveryStreamingOrphanReconciler(
    private val artifacts: RecoveryStreamingOrphanArtifacts,
    private val crypto: RecoveryStreamingTinkPrerequisiteCrypto,
    private val quarantine: RecoveryQuarantineController,
    private val authenticationObserved: (RecoveryStreamingCheckpointAuthentication) -> Unit = {},
) : RecoveryStreamingOrphanHandler {
    override fun reconcile(
        witness: RecoveryStreamingWitnessInput,
        chain: List<RecoveryStreamingCheckpointRow>,
    ): RecoveryStreamingReconciliationResult {
        val previous = chain.maxByOrNull { it.generation }
        if (
            witness.checkpointGeneration !in 1UL..Long.MAX_VALUE.toULong() ||
                witness.checkpointGeneration != (previous?.generation ?: 0UL) + 1UL ||
                chain.any { it.runId != witness.runId } ||
                witness.checkpointPrefixBytes > witness.preFaultSourceBytes ||
                witness.checkpointContextEnd > witness.acceptedEnd ||
                witness.acceptedEnd > RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN ||
                witness.preFaultSourceBytes > 115_654_656UL ||
                witness.checkpointPrefixBytes % 4_096UL != 0UL ||
                witness.controllerSnapshotSha256 !=
                    RecoveryStreamingIdentity.controllerSnapshot(
                        witness.copy(controllerSnapshotSha256 = null)
                    ) ||
                witness.oracleIdentitySha256 !=
                    RecoveryStreamingIdentity.oracle(
                        witness.acceptedEnd,
                        witness.oraclePlaintextSha256,
                        witness.runId,
                    )
        )
            return fatal(RecoveryStreamingResultClassification.STREAM_CHECKPOINT_STRUCTURAL)

        // The serialized checkpoint omits this digest. Use it only when its exact extent is
        // independently present in the frozen witness or the surviving predecessor, then bind
        // every artifact byte back to the original witness checkpointIdentity below.
        val prefixHash =
            when {
                witness.checkpointPrefixBytes == witness.preFaultSourceBytes ->
                    witness.preFaultSourceSha256
                previous?.streamCiphertextPrefixBytes == witness.checkpointPrefixBytes ->
                    previous.streamCiphertextPrefixSha256
                else ->
                    return fatal(RecoveryStreamingResultClassification.STREAM_CHECKPOINT_MISSING)
            }
        val checkpointName =
            RecoveryRelativeNames.checkpointCiphertext(witness.checkpointGeneration)
        val envelopeName = RecoveryRelativeNames.checkpointKeyEnvelope(witness.checkpointGeneration)
        val checkpoint: RecoveryArtifactBytes
        val envelope: RecoveryArtifactBytes
        val streamEnvelope: RecoveryArtifactBytes
        try {
            checkpoint =
                artifacts.load(
                    witness.runId,
                    checkpointName,
                    RecoveryQuarantineArtifactRole.CHECKPOINT_CIPHERTEXT,
                ) ?: return fatal(RecoveryStreamingResultClassification.STREAM_CHECKPOINT_MISSING)
            envelope =
                artifacts.load(
                    witness.runId,
                    envelopeName,
                    RecoveryQuarantineArtifactRole.CHECKPOINT_KEY_ENVELOPE,
                ) ?: return fatal(RecoveryStreamingResultClassification.STREAM_CHECKPOINT_MISSING)
            streamEnvelope =
                artifacts.load(
                    witness.runId,
                    "key-envelopes/stream.ks",
                    RecoveryQuarantineArtifactRole.STREAM_KEY_ENVELOPE,
                ) ?: return fatal(RecoveryStreamingResultClassification.STREAM_CHECKPOINT_MISSING)
        } catch (failure: RecoveryStreamingOrphanAccessException) {
            return failure.failure.result()
        } catch (failure: RecoveryStreamingPrerequisiteSourceException) {
            return when (failure.failure) {
                RecoveryStreamingPrerequisiteSourceFailure.UNSAFE_PATH ->
                    fatal(RecoveryStreamingResultClassification.UNSAFE_PATH)
                RecoveryStreamingPrerequisiteSourceFailure.STRUCTURAL ->
                    fatal(RecoveryStreamingResultClassification.STREAM_CHECKPOINT_STRUCTURAL)
                RecoveryStreamingPrerequisiteSourceFailure.OPERATIONAL -> ioRetry()
            }
        }
        if (
            checkpoint.relativeName != checkpointName ||
                envelope.relativeName != envelopeName ||
                streamEnvelope.relativeName != "key-envelopes/stream.ks" ||
                checkpoint.size !in 1..MAX_CHECKPOINT_BYTES ||
                envelope.size !in 1..MAX_ENVELOPE_BYTES ||
                streamEnvelope.size !in 1..MAX_ENVELOPE_BYTES
        )
            return fatal(RecoveryStreamingResultClassification.STREAM_CHECKPOINT_STRUCTURAL)
        val identity =
            RecoveryStreamingCheckpointIdentityInput(
                witness.runId,
                witness.checkpointGeneration,
                witness.checkpointPrefixBytes / 4_096UL,
                witness.checkpointPrefixBytes,
                prefixHash,
                witness.checkpointContextEnd,
                checkpointName,
                checkpoint.size.toULong(),
                checkpoint.sha256,
                envelopeName,
                envelope.size.toULong(),
                envelope.sha256,
                "stream/stream.ct",
                "key-envelopes/stream.ks",
                streamEnvelope.size.toULong(),
                streamEnvelope.sha256,
                previous?.checkpointSha256 ?: Sha256Value.ZERO,
            )
        if (RecoveryStreamingIdentity.checkpoint(identity) != witness.checkpointIdentity)
            return fatal(RecoveryStreamingResultClassification.STREAM_CHECKPOINT_STRUCTURAL)
        val expected =
            try {
                RecoveryCheckpoint(
                    RecoveryCandidate.STREAM,
                    identity.runId,
                    identity.generation,
                    identity.previousCheckpointSha256,
                    identity.durableNonFinalSegmentCount,
                    identity.streamCiphertextPrefixBytes,
                    identity.committedEnd,
                    identity.streamEnvelopeBytes,
                    identity.streamEnvelopeSha256,
                    identity.streamRelativeName,
                    identity.streamEnvelopeRelativeName,
                )
            } catch (_: IllegalArgumentException) {
                return fatal(RecoveryStreamingResultClassification.STREAM_CHECKPOINT_STRUCTURAL)
            }
        val authentication =
            crypto.authenticateArtifact(
                expected,
                envelope.snapshot(),
                checkpoint.snapshot(),
                streamEnvelope.snapshot(),
            )
        // Observation is advisory and cannot suppress or authorize quarantine.
        runCatching { authenticationObserved(authentication) }
        when (authentication) {
            RecoveryStreamingCheckpointAuthentication.Missing ->
                return fatal(RecoveryStreamingResultClassification.STREAM_CHECKPOINT_MISSING)
            RecoveryStreamingCheckpointAuthentication.Rejected ->
                return fatal(
                    RecoveryStreamingResultClassification.STREAM_CHECKPOINT_AUTHENTICATION_REJECTED
                )
            RecoveryStreamingCheckpointAuthentication.Structural ->
                return fatal(RecoveryStreamingResultClassification.STREAM_CHECKPOINT_STRUCTURAL)
            RecoveryStreamingCheckpointAuthentication.UnsafePath ->
                return fatal(RecoveryStreamingResultClassification.UNSAFE_PATH)
            RecoveryStreamingCheckpointAuthentication.Operational ->
                return RecoveryStreamingReconciliationResult.Retry.of(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification
                        .STREAM_CHECKPOINT_AUTHENTICATION_OPERATIONAL,
                    RecoveryStreamingSafeExceptionType.CRYPTO,
                )
            is RecoveryStreamingCheckpointAuthentication.Ready -> Unit
        }
        // Never invoke the returned publicStreamOpener: authenticated publication != committed C.
        for ((artifact, role) in
            listOf(
                checkpoint to RecoveryQuarantineArtifactRole.CHECKPOINT_CIPHERTEXT,
                envelope to RecoveryQuarantineArtifactRole.CHECKPOINT_KEY_ENVELOPE,
            )) {
            val input =
                RecoveryQuarantineIntentInput(
                    RecoveryCandidate.STREAM,
                    witness.runId,
                    artifact.relativeName,
                    role,
                    artifact.size.toULong(),
                    artifact.sha256,
                )
            when (
                val result =
                    quarantine.quarantine(
                        input,
                        RecoveryQuarantineObservedState.FINAL_ORPHAN,
                        QuarantineBootstrapBinding.PRESENT,
                    )
            ) {
                is QuarantineResult.Completed -> Unit
                is QuarantineResult.UnsafePath ->
                    return fatal(RecoveryStreamingResultClassification.UNSAFE_PATH)
                is QuarantineResult.Collision ->
                    return RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
                        RecoveryStreamingResultStage.JOURNAL,
                        RecoveryStreamingResultClassification.JOURNAL_STRUCTURAL,
                    )
                is QuarantineResult.RetryRequired -> return quarantineRetry(result)
            }
        }
        // The original committed parent is still missing. Quarantine evidence uses its existing
        // independent sink and stable intent IDs; it is never a streaming persistence receipt.
        return fatal(RecoveryStreamingResultClassification.STREAM_CHECKPOINT_MISSING)
    }

    /** Q intent IDs are not streaming outcome/range IDs and must never populate those fields. */
    internal fun quarantineRetry(
        result: QuarantineResult.RetryRequired
    ): RecoveryStreamingReconciliationResult {
        val journalFailure =
            result.failedStep == QuarantineStep.Q01 ||
                result.failedStep == QuarantineStep.Q05 ||
                result.diagnostic?.stage == RecoveryFailureStage.JOURNAL
        val unresolved =
            when (result.failedStep) {
                QuarantineStep.Q01 ->
                    result.remainder.intentCommit == QuarantineOperationState.OUTCOME_UNKNOWN
                QuarantineStep.Q05 ->
                    result.remainder.completionCommit == QuarantineOperationState.OUTCOME_UNKNOWN
                else -> false
            }
        val failure =
            when {
                result.diagnostic?.category == RecoveryFailureCategory.UNSAFE_PARENT ||
                    result.diagnostic?.category == RecoveryFailureCategory.CORRUPT_LEAF ->
                    RecoveryStreamingOrphanFailure.UNSAFE_PATH
                result.diagnostic?.category == RecoveryFailureCategory.STRUCTURAL ->
                    if (journalFailure) RecoveryStreamingOrphanFailure.JOURNAL_STRUCTURAL
                    else RecoveryStreamingOrphanFailure.ARTIFACT_STRUCTURAL
                unresolved -> RecoveryStreamingOrphanFailure.JOURNAL_COMMIT_UNRESOLVED
                journalFailure -> RecoveryStreamingOrphanFailure.JOURNAL_OPERATIONAL
                else -> RecoveryStreamingOrphanFailure.ARTIFACT_OPERATIONAL
            }
        return failure.result()
    }

    private fun fatal(classification: RecoveryStreamingResultClassification) =
        RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
            RecoveryStreamingResultStage.PREREQUISITE,
            classification,
        )

    private fun ioRetry() =
        RecoveryStreamingReconciliationResult.Retry.of(
            RecoveryStreamingResultStage.SOURCE_PROOF,
            RecoveryStreamingResultClassification.ARTIFACT_IO_BEFORE_EXACT_SOURCE_HASH,
            RecoveryStreamingSafeExceptionType.IO,
        )

    private companion object {
        const val MAX_CHECKPOINT_BYTES = 1_048_576L
        const val MAX_ENVELOPE_BYTES = 65_536L
    }
}
