@file:Suppress("LongParameterList")

package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeIdentityInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRejectedObservationInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRules
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingWitnessInput
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.contract.StreamAdmissionResult
import com.monumentogram.dora.poc.recovery.contract.StreamBoundaryDerivation
import com.monumentogram.dora.poc.recovery.contract.StreamBoundaryResult
import com.monumentogram.dora.poc.recovery.contract.StreamCheckpointIntersection
import com.monumentogram.dora.poc.recovery.contract.StreamDecision
import com.monumentogram.dora.poc.recovery.contract.StreamDiagnosticBranch
import com.monumentogram.dora.poc.recovery.contract.StreamDiagnosticClassification
import com.monumentogram.dora.poc.recovery.contract.StreamDiagnosticStage
import com.monumentogram.dora.poc.recovery.contract.StreamRangeCertainty
import com.monumentogram.dora.poc.recovery.contract.StreamSourceMatch
import com.monumentogram.dora.poc.recovery.contract.StreamTerminal

internal data class RecoveryStreamingCompletedReadFacts(
    val candidateEnd: ULong,
    val completedPlaintextSha256: Sha256Value,
    val oraclePrefixSha256: Sha256Value,
    val oraclePrefixEqual: Boolean,
    val terminal: StreamTerminal,
    val firstMismatchOffset: ULong? = null,
    val equalPrefixSha256: Sha256Value? = null,
    val expectedOracleByte: UByte? = null,
    val observedPlaintextByte: UByte? = null,
) {
    init {
        val mismatchTuple =
            listOf(
                firstMismatchOffset,
                equalPrefixSha256,
                expectedOracleByte,
                observedPlaintextByte,
            )
        if (oraclePrefixEqual) {
            require(completedPlaintextSha256 == oraclePrefixSha256) {
                "Equal oracle proof requires equal digests"
            }
            require(mismatchTuple.all { it == null }) { "Equal oracle proof has mismatch fields" }
            require(
                terminal == StreamTerminal.AUTHENTICATED_EOF ||
                    terminal == StreamTerminal.AUTHENTICATION_FAILURE
            ) {
                "Equal oracle proof requires a terminal boundary"
            }
        } else {
            require(completedPlaintextSha256 != oraclePrefixSha256) {
                "Oracle mismatch requires distinct digests"
            }
            require(mismatchTuple.all { it != null }) { "Oracle mismatch tuple is incomplete" }
            require(terminal == StreamTerminal.COMPLETED_READ_REJECTED && candidateEnd > 0UL) {
                "Oracle mismatch requires a completed rejected read"
            }
            require(requireNotNull(firstMismatchOffset) < candidateEnd) {
                "Mismatch offset is outside completed bytes"
            }
            require(expectedOracleByte != observedPlaintextByte) {
                "Mismatch bytes must differ"
            }
        }
    }
}

internal data class RecoveryStreamingValidatedIntentFacts(
    val witness: RecoveryStreamingWitnessInput,
    val observedSourceBytes: ULong,
    val observedSourceSha256: Sha256Value,
    val checkpointPrefixMatches: Boolean,
    val preFaultPrefixMatches: Boolean,
    val completed: RecoveryStreamingCompletedReadFacts?,
) {
    init {
        require(
            witness.controllerSnapshotSha256 ==
                RecoveryStreamingIdentity.controllerSnapshot(
                    witness.copy(controllerSnapshotSha256 = null)
                )
        ) {
            "Controller snapshot identity is invalid"
        }
        require(
            witness.oracleIdentitySha256 ==
                RecoveryStreamingIdentity.oracle(
                    witness.acceptedEnd,
                    witness.oraclePlaintextSha256,
                    witness.runId,
                )
        ) {
            "Oracle identity is invalid"
        }
        require(witness.checkpointPrefixBytes % CIPHERTEXT_SEGMENT_BYTES == 0UL) {
            "Checkpoint prefix is not canonical"
        }
        require(
            witness.checkpointContextEnd == checkpointContextEnd(witness.checkpointPrefixBytes)
        ) {
            "Checkpoint context end is not canonical"
        }
    }

    private companion object {
        const val CIPHERTEXT_SEGMENT_BYTES = 4_096UL
        const val FIRST_PLAINTEXT_SEGMENT_BYTES = 4_056UL
        const val LATER_PLAINTEXT_SEGMENT_BYTES = 4_080UL

        fun checkpointContextEnd(prefixBytes: ULong): ULong {
            val segments = prefixBytes / CIPHERTEXT_SEGMENT_BYTES
            return if (segments < 2UL) {
                0UL
            } else {
                FIRST_PLAINTEXT_SEGMENT_BYTES + (segments - 2UL) * LATER_PLAINTEXT_SEGMENT_BYTES
            }
        }
    }
}

internal object RecoveryStreamingIntentBuilder {
    fun buildOutcome(facts: RecoveryStreamingValidatedIntentFacts): RecoveryStreamingOutcomeRow {
        RecoveryStreamingRules.validateExtent(
            facts.witness.acceptedEnd,
            facts.witness.preFaultSourceBytes,
            facts.observedSourceBytes,
        )
        return when {
            facts.observedSourceBytes < facts.witness.preFaultSourceBytes ->
                preIntersection(
                    facts,
                    StreamDiagnosticStage.STREAM_SOURCE_EXTENT,
                    StreamDiagnosticClassification.STREAM_SOURCE_TRUNCATED,
                )
            facts.witness.checkpointPrefixBytes > facts.witness.preFaultSourceBytes ->
                preIntersection(
                    facts,
                    StreamDiagnosticStage.STREAM_CHECKPOINT,
                    StreamDiagnosticClassification.STREAM_CHECKPOINT_PREFIX_OUTSIDE_WITNESS,
                )
            !facts.checkpointPrefixMatches || !facts.preFaultPrefixMatches ->
                preIntersection(
                    facts,
                    StreamDiagnosticStage.STREAM_SOURCE_EXTENT,
                    StreamDiagnosticClassification.STREAM_SOURCE_PREFIX_IDENTITY_MISMATCH,
                )
            else -> postIntersection(facts)
        }
    }

    private fun preIntersection(
        facts: RecoveryStreamingValidatedIntentFacts,
        stage: StreamDiagnosticStage,
        classification: StreamDiagnosticClassification,
    ): RecoveryStreamingOutcomeRow {
        val rangeStart = 0UL.takeIf { facts.observedSourceBytes > 0UL }
        return RecoveryStreamingOutcomeRow.from(
            RecoveryStreamingOutcomeIdentityInput(
                witness = facts.witness,
                observedSourceBytes = facts.observedSourceBytes,
                observedSourceSha256 = facts.observedSourceSha256,
                preFaultSourceMatch = StreamSourceMatch.UNPROVEN_OR_MISMATCH,
                checkpointIntersection = StreamCheckpointIntersection.CONTEXT_ONLY,
                decision = StreamDecision.FATAL,
                diagnosticBranch = StreamDiagnosticBranch.PRE_INTERSECTION,
                terminal = StreamTerminal.NOT_REACHED,
                recoveredEnd = null,
                recoveredBeyondCheckpointBytes = null,
                tailLossBytes = null,
                returnedPlaintextSha256 = null,
                remainderBoundaryBytes = null,
                remainderCertainty = null,
                rejectedObservation = null,
                requiredRangeStart = rangeStart,
                requiredRangeCertainty =
                    rangeStart?.let { StreamRangeCertainty.CONSERVATIVE_WHOLE_SOURCE },
                diagnosticStage = stage,
                diagnosticClassification = classification,
            )
        )
    }

    private fun postIntersection(
        facts: RecoveryStreamingValidatedIntentFacts
    ): RecoveryStreamingOutcomeRow {
        val completed =
            requireNotNull(facts.completed) { "POST facts require completed read facts" }
        require(completed.candidateEnd <= facts.witness.acceptedEnd) {
            "Completed read crosses accepted end"
        }
        val admission =
            RecoveryStreamingRules.classifyPostIntersection(
                committedEnd = facts.witness.checkpointContextEnd,
                acceptedEnd = facts.witness.acceptedEnd,
                candidateEnd = completed.candidateEnd,
                oracleEqual = completed.oraclePrefixEqual,
                terminal = completed.terminal,
                observedEnd = facts.observedSourceBytes,
            )
        return if (admission.decision == StreamDecision.VALID) {
            valid(facts, completed, admission)
        } else {
            diagnostic(facts, completed, admission)
        }
    }

    private fun valid(
        facts: RecoveryStreamingValidatedIntentFacts,
        completed: RecoveryStreamingCompletedReadFacts,
        admission: StreamAdmissionResult,
    ): RecoveryStreamingOutcomeRow {
        val exactBoundary = admission.boundary as? StreamBoundaryDerivation.Exact
        return RecoveryStreamingOutcomeRow.from(
            RecoveryStreamingOutcomeIdentityInput(
                witness = facts.witness,
                observedSourceBytes = facts.observedSourceBytes,
                observedSourceSha256 = facts.observedSourceSha256,
                preFaultSourceMatch = StreamSourceMatch.VERIFIED_SAME_DESCRIPTOR,
                checkpointIntersection = StreamCheckpointIntersection.PROVEN,
                decision = StreamDecision.VALID,
                diagnosticBranch = StreamDiagnosticBranch.NONE,
                terminal = completed.terminal,
                recoveredEnd = completed.candidateEnd,
                recoveredBeyondCheckpointBytes =
                    completed.candidateEnd - facts.witness.checkpointContextEnd,
                tailLossBytes = facts.witness.acceptedEnd - completed.candidateEnd,
                returnedPlaintextSha256 = completed.completedPlaintextSha256,
                remainderBoundaryBytes = exactBoundary?.bytes,
                remainderCertainty =
                    exactBoundary?.let { StreamRangeCertainty.EXACT_FORMAT_BOUNDARY },
                rejectedObservation = null,
                requiredRangeStart = admission.requiredRangeStart,
                requiredRangeCertainty = admission.requiredRangeCertainty,
                diagnosticStage = StreamDiagnosticStage.NONE,
                diagnosticClassification = StreamDiagnosticClassification.NONE,
            )
        )
    }

    private fun diagnostic(
        facts: RecoveryStreamingValidatedIntentFacts,
        completed: RecoveryStreamingCompletedReadFacts,
        admission: StreamAdmissionResult,
    ): RecoveryStreamingOutcomeRow =
        RecoveryStreamingOutcomeRow.from(
            RecoveryStreamingOutcomeIdentityInput(
                witness = facts.witness,
                observedSourceBytes = facts.observedSourceBytes,
                observedSourceSha256 = facts.observedSourceSha256,
                preFaultSourceMatch = StreamSourceMatch.VERIFIED_SAME_DESCRIPTOR,
                checkpointIntersection = StreamCheckpointIntersection.PROVEN,
                decision = admission.decision,
                diagnosticBranch = StreamDiagnosticBranch.POST_INTERSECTION,
                terminal = completed.terminal,
                recoveredEnd = null,
                recoveredBeyondCheckpointBytes = null,
                tailLossBytes = null,
                returnedPlaintextSha256 = null,
                remainderBoundaryBytes = null,
                remainderCertainty = null,
                rejectedObservation = rejectedObservation(facts, completed, admission),
                requiredRangeStart = admission.requiredRangeStart,
                requiredRangeCertainty = admission.requiredRangeCertainty,
                diagnosticStage = StreamDiagnosticStage.STREAM_PAYLOAD_DECRYPT,
                diagnosticClassification = admission.classification,
            )
        )

    private fun rejectedObservation(
        facts: RecoveryStreamingValidatedIntentFacts,
        completed: RecoveryStreamingCompletedReadFacts,
        admission: StreamAdmissionResult,
    ) =
        RecoveryStreamingRejectedObservationInput(
            candidateEnd = completed.candidateEnd,
            completedPlaintextSha256 = completed.completedPlaintextSha256,
            oraclePrefixSha256 = completed.oraclePrefixSha256,
            oraclePrefixEqual = completed.oraclePrefixEqual,
            comparedEnd = completed.candidateEnd,
            firstMismatchOffset = completed.firstMismatchOffset,
            equalPrefixSha256 = completed.equalPrefixSha256,
            expectedOracleByte = completed.expectedOracleByte,
            observedPlaintextByte = completed.observedPlaintextByte,
            observedTailLossBytes = facts.witness.acceptedEnd - completed.candidateEnd,
            boundaryResult = boundaryResult(completed, admission),
            boundaryBytes = boundaryBytes(completed, admission),
        )

    private fun boundaryResult(
        completed: RecoveryStreamingCompletedReadFacts,
        admission: StreamAdmissionResult,
    ): StreamBoundaryResult =
        when {
            !completed.oraclePrefixEqual -> StreamBoundaryResult.NOT_EVALUATED_ORACLE_MISMATCH
            completed.terminal == StreamTerminal.AUTHENTICATED_EOF ->
                StreamBoundaryResult.NOT_APPLICABLE_AUTHENTICATED_EOF
            admission.boundary is StreamBoundaryDerivation.Exact ->
                StreamBoundaryResult.EXACT_FORMAT_BOUNDARY
            admission.boundary is StreamBoundaryDerivation.NonCanonicalCandidateEnd ->
                StreamBoundaryResult.NON_CANONICAL_CANDIDATE_END
            admission.boundary is StreamBoundaryDerivation.ExceedsObservedSource ->
                StreamBoundaryResult.BOUNDARY_EXCEEDS_OBSERVED_SOURCE
            else -> error("Authentication failure is missing its boundary")
        }

    private fun boundaryBytes(
        completed: RecoveryStreamingCompletedReadFacts,
        admission: StreamAdmissionResult,
    ): ULong? =
        if (
            !completed.oraclePrefixEqual || completed.terminal == StreamTerminal.AUTHENTICATED_EOF
        ) {
            null
        } else {
            when (val boundary = admission.boundary) {
                is StreamBoundaryDerivation.Exact -> boundary.bytes
                is StreamBoundaryDerivation.ExceedsObservedSource -> boundary.bytes
                StreamBoundaryDerivation.NonCanonicalCandidateEnd -> null
                null -> error("Authentication failure is missing its boundary")
            }
        }
}
