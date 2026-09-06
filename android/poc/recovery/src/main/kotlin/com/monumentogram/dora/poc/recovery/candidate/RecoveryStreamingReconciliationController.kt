@file:Suppress("LongParameterList")

package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingExistingEvidence
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournal
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalClassification
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalReadResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeAttempt
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
import com.monumentogram.dora.poc.recovery.contract.StreamSemanticOutcome
import com.monumentogram.dora.poc.recovery.contract.StreamSourceMatch
import com.monumentogram.dora.poc.recovery.contract.StreamTerminal
import com.monumentogram.dora.poc.recovery.coordination.RecoveryRunSingleWriterGuard
import com.monumentogram.dora.poc.recovery.storage.RecoveryOpenedStreamingSource
import com.monumentogram.dora.poc.recovery.storage.RecoveryReplayHashOnlyResult
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamOpenRequest
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamReplayRequest
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamingReplayAccess
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamingSource
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamingSourceControllerAccess
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamingSourceException
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamingSourceFailure
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamingSourceLeaseAccess
import com.monumentogram.dora.poc.recovery.storage.STREAM_SOURCE_RELATIVE_NAME
import java.security.MessageDigest

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

    internal interface RecoveryStreamingPublicRead : AutoCloseable {
        /** Returns a positive completed count, zero progress, or -1 for authenticated EOF. */
        fun read(destination: ByteArray, offset: Int, count: Int): Int
    }

    internal class RecoveryStreamingPublicReadException(
        val safeExceptionType: RecoveryStreamingSafeExceptionType
    ) : RuntimeException() {
        init {
            require(
                safeExceptionType == RecoveryStreamingSafeExceptionType.IO ||
                    safeExceptionType == RecoveryStreamingSafeExceptionType.CRYPTO
            ) {
                "Public read operational failure must be IO or CRYPTO"
            }
        }
    }

    internal class RecoveryStreamingAuthenticationFailureException : RuntimeException()

    internal class RecoveryStreamingOracle private constructor(private val bytes: ByteArray) {
        val acceptedEnd: ULong = bytes.size.toULong()

        fun firstMismatch(
            start: ULong,
            returned: ByteArray,
            count: Int,
        ): Int? {
            val startIndex = start.toInt()
            require(startIndex >= 0 && count in 0..returned.size)
            require(startIndex + count <= bytes.size)
            repeat(count) { offset ->
                if (returned[offset] != bytes[startIndex + offset]) return offset
            }
            return null
        }

        fun prefixSha256(end: ULong): Sha256Value {
            val endIndex = end.toInt()
            require(endIndex >= 0 && endIndex <= bytes.size)
            return Sha256Value.calculate(bytes.copyOfRange(0, endIndex))
        }

        fun byteAt(offset: ULong): UByte {
            val index = offset.toInt()
            require(index >= 0 && index < bytes.size)
            return bytes[index].toUByte()
        }

        companion object {
            fun from(
                witness: RecoveryStreamingWitnessInput,
                oracleBytes: ByteArray,
            ): RecoveryStreamingOracle {
                require(witness.acceptedEnd == oracleBytes.size.toULong()) {
                    "Oracle bytes do not match accepted end"
                }
                require(witness.oraclePlaintextSha256 == Sha256Value.calculate(oracleBytes)) {
                    "Oracle bytes do not match oracle digest"
                }
                require(
                    witness.oracleIdentitySha256 ==
                        RecoveryStreamingIdentity.oracle(
                            witness.acceptedEnd,
                            witness.oraclePlaintextSha256,
                            witness.runId,
                        )
                ) {
                    "Oracle bytes do not match oracle identity"
                }
                return RecoveryStreamingOracle(oracleBytes.copyOf())
            }
        }
    }

    internal sealed interface RecoveryStreamingReadExecution {
        data class Completed(val facts: RecoveryStreamingCompletedReadFacts) :
            RecoveryStreamingReadExecution

        data class Failed(val result: RecoveryStreamingReconciliationResult) :
            RecoveryStreamingReadExecution
    }

    internal object RecoveryStreamingReadLoop {
        @Suppress("LoopWithTooManyJumpStatements")
        fun read(
            publicRead: RecoveryStreamingPublicRead,
            oracle: RecoveryStreamingOracle,
        ): RecoveryStreamingReadExecution {
            val completedDigest = MessageDigest.getInstance("SHA-256")
            var candidateEnd = 0UL
            var firstRequest = true
            while (true) {
                val requested = if (firstRequest) FIRST_REQUEST_BYTES else LATER_REQUEST_BYTES
                firstRequest = false
                val buffer = ByteArray(requested)
                val count =
                    try {
                        publicRead.read(buffer, 0, requested)
                    } catch (_: RecoveryStreamingAuthenticationFailureException) {
                        return completed(
                            candidateEnd,
                            completedDigest,
                            oracle,
                            StreamTerminal.AUTHENTICATION_FAILURE,
                        )
                    } catch (failure: RecoveryStreamingPublicReadException) {
                        return RecoveryStreamingReadExecution.Failed(
                            RecoveryStreamingReconciliationResult.Retry.of(
                                RecoveryStreamingResultStage.STREAM_READ,
                                RecoveryStreamingResultClassification
                                    .STREAM_PUBLIC_READ_OPERATIONAL,
                                failure.safeExceptionType,
                            )
                        )
                    }
                require(count in -1..requested) { "Public read returned an invalid count" }
                if (count == -1) {
                    return completed(
                        candidateEnd,
                        completedDigest,
                        oracle,
                        StreamTerminal.AUTHENTICATED_EOF,
                    )
                }
                if (count == 0) {
                    return RecoveryStreamingReadExecution.Failed(
                        RecoveryStreamingReconciliationResult.Retry.of(
                            RecoveryStreamingResultStage.STREAM_READ,
                            RecoveryStreamingResultClassification.STREAM_ZERO_PROGRESS,
                            RecoveryStreamingSafeExceptionType.NONE,
                        )
                    )
                }
                val nextEnd = candidateEnd + count.toULong()
                if (nextEnd < candidateEnd || nextEnd > oracle.acceptedEnd) {
                    return RecoveryStreamingReadExecution.Failed(
                        RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
                            RecoveryStreamingResultStage.STREAM_READ,
                            RecoveryStreamingResultClassification.STREAM_READ_CROSSES_ACCEPTED_END,
                        )
                    )
                }
                completedDigest.update(buffer, 0, count)
                val mismatch = oracle.firstMismatch(candidateEnd, buffer, count)
                if (mismatch != null) {
                    val mismatchOffset = candidateEnd + mismatch.toULong()
                    return RecoveryStreamingReadExecution.Completed(
                        RecoveryStreamingCompletedReadFacts(
                            candidateEnd = nextEnd,
                            completedPlaintextSha256 =
                                Sha256Value.fromBytes(completedDigest.digest()),
                            oraclePrefixSha256 = oracle.prefixSha256(nextEnd),
                            oraclePrefixEqual = false,
                            terminal = StreamTerminal.COMPLETED_READ_REJECTED,
                            firstMismatchOffset = mismatchOffset,
                            equalPrefixSha256 = oracle.prefixSha256(mismatchOffset),
                            expectedOracleByte = oracle.byteAt(mismatchOffset),
                            observedPlaintextByte = buffer[mismatch].toUByte(),
                        )
                    )
                }
                candidateEnd = nextEnd
            }
        }

        private fun completed(
            candidateEnd: ULong,
            digest: MessageDigest,
            oracle: RecoveryStreamingOracle,
            terminal: StreamTerminal,
        ): RecoveryStreamingReadExecution.Completed {
            val completedSha256 = Sha256Value.fromBytes(digest.digest())
            return RecoveryStreamingReadExecution.Completed(
                RecoveryStreamingCompletedReadFacts(
                    candidateEnd = candidateEnd,
                    completedPlaintextSha256 = completedSha256,
                    oraclePrefixSha256 = oracle.prefixSha256(candidateEnd),
                    oraclePrefixEqual = true,
                    terminal = terminal,
                )
            )
        }

        private const val FIRST_REQUEST_BYTES = 4_056
        private const val LATER_REQUEST_BYTES = 4_080
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

internal sealed interface RecoveryStreamingPersistenceResolution {
    data class ExactReceipt(val receipt: RecoveryStreamingJournalResult.Receipt) :
        RecoveryStreamingPersistenceResolution

    data class ProvenRollback(val semanticOutcome: StreamSemanticOutcome) :
        RecoveryStreamingPersistenceResolution

    data class Failed(val result: RecoveryStreamingReconciliationResult) :
        RecoveryStreamingPersistenceResolution
}

internal object RecoveryStreamingJournalMapper {
    fun readFailure(
        failure: RecoveryStreamingJournalReadResult<*>
    ): RecoveryStreamingReconciliationResult =
        when (failure) {
            is RecoveryStreamingJournalReadResult.Value -> error("Value is not a journal failure")
            is RecoveryStreamingJournalReadResult.Retry -> retry(failure.classification)
            is RecoveryStreamingJournalReadResult.Fatal ->
                fatal(failure.classification, failure.existingEvidence)
        }

    fun resolve(
        attempt: RecoveryStreamingOutcomeAttempt,
        result: RecoveryStreamingJournalResult,
    ): RecoveryStreamingPersistenceResolution =
        when (result) {
            is RecoveryStreamingJournalResult.Receipt -> {
                if (
                    result.outcomeId == attempt.outcome.outcomeId &&
                        result.rangeIntentId == attempt.range?.rangeIntentId
                ) {
                    RecoveryStreamingPersistenceResolution.ExactReceipt(result)
                } else {
                    failedStructural()
                }
            }
            is RecoveryStreamingJournalResult.Original -> {
                if (result.semanticOutcome == attempt.semanticOutcome) {
                    RecoveryStreamingPersistenceResolution.ProvenRollback(result.semanticOutcome)
                } else {
                    failedStructural()
                }
            }
            is RecoveryStreamingJournalResult.Retry -> {
                if (retryAttemptMatches(attempt, result)) {
                    RecoveryStreamingPersistenceResolution.Failed(
                        retry(
                            result.classification,
                            result.attemptedOutcomeId,
                            result.attemptedRangeId,
                        )
                    )
                } else {
                    failedStructural()
                }
            }
            is RecoveryStreamingJournalResult.Fatal ->
                RecoveryStreamingPersistenceResolution.Failed(
                    fatal(result.classification, result.existingEvidence)
                )
            is RecoveryStreamingJournalResult.CheckpointReceipt -> failedStructural()
        }

    private fun retryAttemptMatches(
        attempt: RecoveryStreamingOutcomeAttempt,
        result: RecoveryStreamingJournalResult.Retry,
    ): Boolean =
        if (
            result.classification ==
                RecoveryStreamingJournalClassification.JOURNAL_COMMIT_STATE_UNRESOLVED
        ) {
            result.attemptedOutcomeId == attempt.outcome.outcomeId &&
                result.attemptedRangeId == attempt.range?.rangeIntentId
        } else {
            result.attemptedOutcomeId == null && result.attemptedRangeId == null
        }

    private fun retry(
        classification: RecoveryStreamingJournalClassification,
        attemptedOutcomeId: Sha256Value? = null,
        attemptedRangeId: Sha256Value? = null,
    ): RecoveryStreamingReconciliationResult.Retry =
        when (classification) {
            RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL ->
                RecoveryStreamingReconciliationResult.Retry.of(
                    RecoveryStreamingResultStage.JOURNAL,
                    RecoveryStreamingResultClassification.JOURNAL_OPERATIONAL,
                    RecoveryStreamingSafeExceptionType.SQLITE,
                )
            RecoveryStreamingJournalClassification.JOURNAL_COMMIT_STATE_UNRESOLVED ->
                RecoveryStreamingReconciliationResult.Retry.of(
                    RecoveryStreamingResultStage.JOURNAL,
                    RecoveryStreamingResultClassification.JOURNAL_COMMIT_STATE_UNRESOLVED,
                    RecoveryStreamingSafeExceptionType.SQLITE,
                    attemptedOutcomeId,
                    attemptedRangeId,
                )
            else -> error("Fatal journal classification cannot be returned as Retry")
        }

    private fun fatal(
        classification: RecoveryStreamingJournalClassification,
        evidence:
            List<com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingExistingEvidence>,
    ): RecoveryStreamingReconciliationResult.Fatal {
        val (stage, outward) =
            when (classification) {
                RecoveryStreamingJournalClassification.JOURNAL_ATTEMPT_CONFLICT ->
                    RecoveryStreamingResultStage.JOURNAL to
                        RecoveryStreamingResultClassification.JOURNAL_ATTEMPT_CONFLICT
                RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL ->
                    RecoveryStreamingResultStage.JOURNAL to
                        RecoveryStreamingResultClassification.JOURNAL_STRUCTURAL
                RecoveryStreamingJournalClassification.STREAM_RANGE_QUARANTINE_COLLISION ->
                    RecoveryStreamingResultStage.JOURNAL to
                        RecoveryStreamingResultClassification.STREAM_RANGE_QUARANTINE_COLLISION
                RecoveryStreamingJournalClassification.STREAM_CHECKPOINT_SPLIT_BRAIN ->
                    RecoveryStreamingResultStage.PREREQUISITE to
                        RecoveryStreamingResultClassification.STREAM_CHECKPOINT_SPLIT_BRAIN
                RecoveryStreamingJournalClassification.STREAM_SOURCE_IDENTITY_CHANGED ->
                    RecoveryStreamingResultStage.SOURCE_PROOF to
                        RecoveryStreamingResultClassification.STREAM_SOURCE_IDENTITY_CHANGED
                RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL,
                RecoveryStreamingJournalClassification.JOURNAL_COMMIT_STATE_UNRESOLVED ->
                    error("Retry journal classification cannot be returned as Fatal")
            }
        return RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
            stage,
            outward,
            evidence,
        )
    }

    private fun failedStructural() =
        RecoveryStreamingPersistenceResolution.Failed(
            RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
                RecoveryStreamingResultStage.JOURNAL,
                RecoveryStreamingResultClassification.JOURNAL_STRUCTURAL,
            )
        )
}

internal fun interface RecoveryStreamingPublicStreamOpener {
    fun open(
        source: RecoveryOpenedStreamingSource,
        witness: RecoveryStreamingWitnessInput,
    ): RecoveryStreamingIntentBuilder.RecoveryStreamingPublicRead
}

internal sealed interface RecoveryStreamingCheckpointAuthentication {
    data class Ready(val publicStreamOpener: RecoveryStreamingPublicStreamOpener) :
        RecoveryStreamingCheckpointAuthentication

    data object Missing : RecoveryStreamingCheckpointAuthentication

    data object Structural : RecoveryStreamingCheckpointAuthentication

    data object Rejected : RecoveryStreamingCheckpointAuthentication

    data object Operational : RecoveryStreamingCheckpointAuthentication

    data object UnsafePath : RecoveryStreamingCheckpointAuthentication
}

internal fun interface RecoveryStreamingCheckpointAuthenticator {
    fun authenticate(
        checkpoint: RecoveryStreamingCheckpointRow,
        witness: RecoveryStreamingWitnessInput,
    ): RecoveryStreamingCheckpointAuthentication
}

internal enum class RecoveryStreamingPrerequisiteArtifactKind {
    CHECKPOINT_KEY_ENVELOPE,
    CHECKPOINT_CIPHERTEXT,
    STREAM_KEY_ENVELOPE,
}

internal fun interface RecoveryStreamingPrerequisiteSource {
    fun load(
        runId: com.monumentogram.dora.poc.recovery.contract.RunId,
        relativeName: String,
        kind: RecoveryStreamingPrerequisiteArtifactKind,
    ): RecoveryArtifactBytes?
}

internal fun interface RecoveryStreamingPrerequisiteCrypto {
    fun authenticate(
        checkpoint: RecoveryStreamingCheckpointRow,
        checkpointEnvelope: ByteArray,
        checkpointCiphertext: ByteArray,
        streamEnvelope: ByteArray,
    ): RecoveryStreamingCheckpointAuthentication
}

internal class RecoveryStreamingCheckpointAuthenticatorAdapter(
    private val source: RecoveryStreamingPrerequisiteSource,
    private val crypto: RecoveryStreamingPrerequisiteCrypto,
) : RecoveryStreamingCheckpointAuthenticator {
    override fun authenticate(
        checkpoint: RecoveryStreamingCheckpointRow,
        witness: RecoveryStreamingWitnessInput,
    ): RecoveryStreamingCheckpointAuthentication {
        if (
            checkpoint.runId != witness.runId ||
                checkpoint.generation != witness.checkpointGeneration ||
                checkpoint.checkpointIdentity != witness.checkpointIdentity
        ) {
            return RecoveryStreamingCheckpointAuthentication.Structural
        }
        val checkpointEnvelope =
            exactArtifact(
                checkpoint.runId,
                checkpoint.checkpointKeyEnvelopeRelativeName,
                RecoveryStreamingPrerequisiteArtifactKind.CHECKPOINT_KEY_ENVELOPE,
            ) ?: return RecoveryStreamingCheckpointAuthentication.Missing
        val checkpointCiphertext =
            exactArtifact(
                checkpoint.runId,
                checkpoint.checkpointRelativeName,
                RecoveryStreamingPrerequisiteArtifactKind.CHECKPOINT_CIPHERTEXT,
            ) ?: return RecoveryStreamingCheckpointAuthentication.Missing
        val streamEnvelope =
            exactArtifact(
                checkpoint.runId,
                checkpoint.streamKeyEnvelopeRelativeName,
                RecoveryStreamingPrerequisiteArtifactKind.STREAM_KEY_ENVELOPE,
            ) ?: return RecoveryStreamingCheckpointAuthentication.Missing
        if (
            !checkpointEnvelope.matches(
                checkpoint.checkpointKeyEnvelopeRelativeName,
                checkpoint.checkpointKeyEnvelopeBytes,
                checkpoint.checkpointKeyEnvelopeSha256,
            ) ||
                !checkpointCiphertext.matches(
                    checkpoint.checkpointRelativeName,
                    checkpoint.checkpointBytes,
                    checkpoint.checkpointSha256,
                ) ||
                !streamEnvelope.matches(
                    checkpoint.streamKeyEnvelopeRelativeName,
                    checkpoint.streamKeyEnvelopeBytes,
                    checkpoint.streamKeyEnvelopeSha256,
                )
        ) {
            return RecoveryStreamingCheckpointAuthentication.Structural
        }
        return crypto.authenticate(
            checkpoint,
            checkpointEnvelope.snapshot(),
            checkpointCiphertext.snapshot(),
            streamEnvelope.snapshot(),
        )
    }

    private fun exactArtifact(
        runId: com.monumentogram.dora.poc.recovery.contract.RunId,
        relativeName: String,
        kind: RecoveryStreamingPrerequisiteArtifactKind,
    ): RecoveryArtifactBytes? = source.load(runId, relativeName, kind)

    private fun RecoveryArtifactBytes.matches(
        expectedRelativeName: String,
        expectedBytes: ULong,
        expectedSha256: Sha256Value,
    ): Boolean =
        relativeName == expectedRelativeName &&
            size >= 0L &&
            size.toULong() == expectedBytes &&
            sha256 == expectedSha256

}

internal data class RecoveryStreamingControllerRequest(
    val witness: RecoveryStreamingWitnessInput,
    val oracle: RecoveryStreamingIntentBuilder.RecoveryStreamingOracle,
) {
    init {
        require(witness.acceptedEnd == oracle.acceptedEnd) {
            "Streaming oracle capability does not match witness"
        }
    }
}

internal class RecoveryStreamingReconciliationController(
    private val journal: RecoveryStreamingJournal,
    @Suppress("unused") private val source: RecoveryStreamingSource,
    private val guard: RecoveryRunSingleWriterGuard,
    @Suppress("unused")
    private val checkpointAuthenticator: RecoveryStreamingCheckpointAuthenticator,
    private val evidenceSink: RecoveryStreamingEvidenceSink,
) {
    fun recover(
        request: RecoveryStreamingControllerRequest?
    ): RecoveryStreamingReconciliationResult {
        if (request == null) {
            return nonPersistable(
                RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification.STREAM_SOURCE_WITNESS_MISSING,
                )
            )
        }
        return try {
            RecoveryStreamingSourceControllerAccess.withControllerAccess(
                request.witness.runId,
                guard,
            ) { normalAccess, replayAccess ->
                when (val chain = journal.checkpointChain(request.witness.runId)) {
                    is RecoveryStreamingJournalReadResult.Value -> {
                        val generation =
                            chain.value.filter {
                                it.generation == request.witness.checkpointGeneration
                            }
                        if (generation.isEmpty()) {
                            nonPersistable(
                                RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
                                    RecoveryStreamingResultStage.PREREQUISITE,
                                    RecoveryStreamingResultClassification.STREAM_CHECKPOINT_MISSING,
                                )
                            )
                        } else if (
                            generation.size != 1 ||
                                generation.single().checkpointIdentity !=
                                    request.witness.checkpointIdentity
                        ) {
                            nonPersistable(
                                RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
                                    RecoveryStreamingResultStage.PREREQUISITE,
                                    RecoveryStreamingResultClassification
                                        .STREAM_CHECKPOINT_SPLIT_BRAIN,
                                    generation.map {
                                        RecoveryStreamingExistingEvidence.Checkpoint(
                                            it.checkpointIdentity
                                        )
                                    },
                                )
                            )
                        } else {
                            authenticate(generation.single(), request, normalAccess, replayAccess)
                        }
                    }
                    else -> nonPersistable(RecoveryStreamingJournalMapper.readFailure(chain))
                }
            }
        } catch (failure: RecoveryStreamingSourceException) {
            if (failure.failure != RecoveryStreamingSourceFailure.LEASE_BINDING) throw failure
            nonPersistable(
                RecoveryStreamingReconciliationResult.Retry.of(
                    RecoveryStreamingResultStage.LEASE,
                    RecoveryStreamingResultClassification.RUN_LEASE_CONTENDED,
                    RecoveryStreamingSafeExceptionType.NONE,
                )
            )
        }
    }

    private fun nonPersistable(
        result: RecoveryStreamingReconciliationResult
    ): RecoveryStreamingReconciliationResult =
        RecoveryStreamingEvidenceFinalizer.nonPersistable(result, evidenceSink)

    private fun authenticate(
        checkpoint: RecoveryStreamingCheckpointRow,
        request: RecoveryStreamingControllerRequest,
        normalAccess: RecoveryStreamingSourceLeaseAccess,
        replayAccess: RecoveryStreamingReplayAccess,
    ): RecoveryStreamingReconciliationResult =
        when (
            val authentication = checkpointAuthenticator.authenticate(checkpoint, request.witness)
        ) {
            RecoveryStreamingCheckpointAuthentication.Missing ->
                fatal(RecoveryStreamingResultClassification.STREAM_CHECKPOINT_MISSING)
            RecoveryStreamingCheckpointAuthentication.Structural ->
                fatal(RecoveryStreamingResultClassification.STREAM_CHECKPOINT_STRUCTURAL)
            RecoveryStreamingCheckpointAuthentication.Rejected ->
                fatal(
                    RecoveryStreamingResultClassification.STREAM_CHECKPOINT_AUTHENTICATION_REJECTED
                )
            RecoveryStreamingCheckpointAuthentication.Operational ->
                nonPersistable(
                    RecoveryStreamingReconciliationResult.Retry.of(
                        RecoveryStreamingResultStage.PREREQUISITE,
                        RecoveryStreamingResultClassification
                            .STREAM_CHECKPOINT_AUTHENTICATION_OPERATIONAL,
                        RecoveryStreamingSafeExceptionType.CRYPTO,
                    )
                )
            RecoveryStreamingCheckpointAuthentication.UnsafePath ->
                fatal(RecoveryStreamingResultClassification.UNSAFE_PATH)
            is RecoveryStreamingCheckpointAuthentication.Ready ->
                continueAuthenticated(
                    checkpoint,
                    request,
                    authentication.publicStreamOpener,
                    normalAccess,
                    replayAccess,
                )
        }

    private fun continueAuthenticated(
        checkpoint: RecoveryStreamingCheckpointRow,
        request: RecoveryStreamingControllerRequest,
        opener: RecoveryStreamingPublicStreamOpener,
        normalAccess: RecoveryStreamingSourceLeaseAccess,
        replayAccess: RecoveryStreamingReplayAccess,
    ): RecoveryStreamingReconciliationResult {
        val existing =
            when (
                val result =
                    journal.outcomeByWitness(
                        request.witness.runId,
                        request.witness.checkpointIdentity,
                        RecoveryStreamingIdentity.witness(request.witness),
                    )
            ) {
                is RecoveryStreamingJournalReadResult.Value -> result.value
                else -> return nonPersistable(RecoveryStreamingJournalMapper.readFailure(result))
            }
        return if (existing == null) {
            activeRangeOrFresh(checkpoint, request, opener, normalAccess)
        } else {
            replay(existing, request, replayAccess)
        }
    }

    private fun activeRangeOrFresh(
        checkpoint: RecoveryStreamingCheckpointRow,
        request: RecoveryStreamingControllerRequest,
        opener: RecoveryStreamingPublicStreamOpener,
        normalAccess: RecoveryStreamingSourceLeaseAccess,
    ): RecoveryStreamingReconciliationResult {
        val witness = request.witness
        val ranges =
            when (val result = journal.activeRanges(witness.runId, STREAM_SOURCE_RELATIVE_NAME)) {
                is RecoveryStreamingJournalReadResult.Value -> result.value
                else -> return nonPersistable(RecoveryStreamingJournalMapper.readFailure(result))
            }
        if (ranges.isEmpty()) return recoverFresh(checkpoint, request, opener, normalAccess)
        val evidence = mutableListOf<RecoveryStreamingExistingEvidence>()
        ranges.forEach { range ->
            evidence += RecoveryStreamingExistingEvidence.Range(range.rangeIntentId)
            val parent =
                when (val result = journal.outcomeById(range.outcomeId)) {
                    is RecoveryStreamingJournalReadResult.Value -> result.value
                    else ->
                        return nonPersistable(RecoveryStreamingJournalMapper.readFailure(result))
                }
            if (parent?.outcomeId != range.outcomeId) return journalStructural()
            evidence += RecoveryStreamingExistingEvidence.Outcome(parent.outcomeId)
        }
        return nonPersistable(
            RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
                RecoveryStreamingResultStage.RANGE_ADMISSION,
                RecoveryStreamingResultClassification.STREAM_ACTIVE_RANGE_DENIED,
                evidence,
            )
        )
    }

    private fun recoverFresh(
        checkpoint: RecoveryStreamingCheckpointRow,
        request: RecoveryStreamingControllerRequest,
        opener: RecoveryStreamingPublicStreamOpener,
        normalAccess: RecoveryStreamingSourceLeaseAccess,
    ): RecoveryStreamingReconciliationResult {
        var execution: FreshExecution? = null
        val closeFailure =
            runCatching {
                    source.withSource(
                        normalAccess,
                        RecoveryStreamOpenRequest(
                            request.witness.runId,
                            request.witness.checkpointGeneration,
                            request.witness.checkpointIdentity,
                            STREAM_SOURCE_RELATIVE_NAME,
                            request.witness.acceptedEnd,
                            request.witness.preFaultSourceBytes,
                            requestedCiphertextStart = 0UL,
                            requestedCiphertextEndExclusive = null,
                        ),
                    ) { opened ->
                        execution = executeFresh(checkpoint, request, opener, opened)
                    }
                }
                .exceptionOrNull()
        return when (val completed = execution) {
            is FreshExecution.Pending ->
                completed.value.complete(closeFailure != null, evidenceSink)
            is FreshExecution.NonPersistable -> nonPersistable(completed.result)
            null -> {
                if (closeFailure is RecoveryStreamingSourceException) {
                    return nonPersistable(mapSourceFailure(closeFailure))
                }
                nonPersistable(
                    RecoveryStreamingReconciliationResult.Retry.of(
                        RecoveryStreamingResultStage.SOURCE_PROOF,
                        RecoveryStreamingResultClassification.ARTIFACT_IO_BEFORE_EXACT_SOURCE_HASH,
                        RecoveryStreamingSafeExceptionType.IO,
                    )
                )
            }
        }
    }

    private fun mapSourceFailure(
        failure: RecoveryStreamingSourceException
    ): RecoveryStreamingReconciliationResult =
        when (failure.failure) {
            RecoveryStreamingSourceFailure.LEASE_BINDING ->
                RecoveryStreamingReconciliationResult.Retry.of(
                    RecoveryStreamingResultStage.LEASE,
                    RecoveryStreamingResultClassification.RUN_LEASE_CONTENDED,
                    RecoveryStreamingSafeExceptionType.NONE,
                )
            RecoveryStreamingSourceFailure.UNSAFE_PATH ->
                RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification.UNSAFE_PATH,
                )
            RecoveryStreamingSourceFailure.SOURCE_CHANGED ->
                RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
                    RecoveryStreamingResultStage.SOURCE_PROOF,
                    RecoveryStreamingResultClassification.STREAM_SOURCE_IDENTITY_CHANGED,
                )
            RecoveryStreamingSourceFailure.ACTIVE_RANGE ->
                RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
                    RecoveryStreamingResultStage.RANGE_ADMISSION,
                    RecoveryStreamingResultClassification.STREAM_ACTIVE_RANGE_DENIED,
                )
            RecoveryStreamingSourceFailure.SOURCE_EXTENT_LIMIT ->
                RecoveryStreamingReconciliationResult.Rejected.nonPersistable(
                    RecoveryStreamingResultStage.SOURCE_PROOF,
                    RecoveryStreamingResultClassification.STREAM_SOURCE_EXTENT_LIMIT_EXCEEDED,
                )
            RecoveryStreamingSourceFailure.INVALID_REQUEST,
            RecoveryStreamingSourceFailure.SOURCE_STRUCTURAL ->
                RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification.STREAM_CHECKPOINT_STRUCTURAL,
                )
            RecoveryStreamingSourceFailure.JOURNAL -> {
                val classification = requireNotNull(failure.journalClassification)
                val mapped: RecoveryStreamingJournalReadResult<Nothing> =
                    if (
                        classification ==
                            RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL ||
                            classification ==
                                RecoveryStreamingJournalClassification
                                    .JOURNAL_COMMIT_STATE_UNRESOLVED
                    ) {
                        RecoveryStreamingJournalReadResult.Retry(classification)
                    } else {
                        RecoveryStreamingJournalReadResult.Fatal(classification, emptyList())
                    }
                RecoveryStreamingJournalMapper.readFailure(mapped)
            }
        }

    private fun executeFresh(
        checkpoint: RecoveryStreamingCheckpointRow,
        request: RecoveryStreamingControllerRequest,
        opener: RecoveryStreamingPublicStreamOpener,
        opened: RecoveryOpenedStreamingSource,
    ): FreshExecution {
        val observedHash = opened.sha256Prefix(opened.observedBytes)
        val truncated = opened.observedBytes < request.witness.preFaultSourceBytes
        val checkpointOutsideWitness =
            request.witness.checkpointPrefixBytes > request.witness.preFaultSourceBytes
        val mayProvePrefixes = !truncated && !checkpointOutsideWitness
        val checkpointPrefixMatches =
            mayProvePrefixes &&
                opened.sha256Prefix(request.witness.checkpointPrefixBytes) ==
                    checkpoint.streamCiphertextPrefixSha256
        val preFaultPrefixMatches =
            mayProvePrefixes &&
                opened.sha256Prefix(request.witness.preFaultSourceBytes) ==
                    request.witness.preFaultSourceSha256
        val facts =
            RecoveryStreamingValidatedIntentFacts(
                request.witness,
                opened.observedBytes,
                observedHash,
                checkpointPrefixMatches,
                preFaultPrefixMatches,
                completed = null,
            )
        if (
            truncated ||
                checkpointOutsideWitness ||
                !checkpointPrefixMatches ||
                !preFaultPrefixMatches
        ) {
            return persistFresh(
                RecoveryStreamingIntentBuilder.buildOutcome(facts),
                opened,
                publicStream = null,
            )
        }
        val publicStream =
            try {
                opener.open(opened, request.witness)
            } catch (failure: RecoveryStreamingIntentBuilder.RecoveryStreamingPublicReadException) {
                return FreshExecution.NonPersistable(
                    RecoveryStreamingReconciliationResult.Retry.of(
                        RecoveryStreamingResultStage.STREAM_READ,
                        RecoveryStreamingResultClassification.STREAM_PUBLIC_READ_OPERATIONAL,
                        failure.safeExceptionType,
                    )
                )
            }
        return when (
            val read =
                RecoveryStreamingIntentBuilder.RecoveryStreamingReadLoop.read(
                    publicStream,
                    request.oracle,
                )
        ) {
            is RecoveryStreamingIntentBuilder.RecoveryStreamingReadExecution.Failed -> {
                runCatching { publicStream.close() }
                FreshExecution.NonPersistable(read.result)
            }
            is RecoveryStreamingIntentBuilder.RecoveryStreamingReadExecution.Completed ->
                persistFresh(
                    RecoveryStreamingIntentBuilder.buildOutcome(facts.copy(completed = read.facts)),
                    opened,
                    publicStream,
                )
        }
    }

    private fun persistFresh(
        outcome: RecoveryStreamingOutcomeRow,
        opened: RecoveryOpenedStreamingSource,
        publicStream: RecoveryStreamingIntentBuilder.RecoveryStreamingPublicRead?,
    ): FreshExecution {
        val range =
            outcome.requiredRangeStart?.let { start ->
                com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRangeRow.exact(
                    outcome,
                    opened.sha256Range(start, outcome.observedSourceBytes),
                )
            }
        val semantic =
            when (outcome.decision) {
                StreamDecision.VALID -> StreamSemanticOutcome.PERSISTED_VALID
                StreamDecision.REJECTED -> StreamSemanticOutcome.PERSISTED_REJECTED
                StreamDecision.FATAL -> StreamSemanticOutcome.PERSISTED_FATAL
            }
        val attempt = RecoveryStreamingOutcomeAttempt(outcome, range, semantic)
        return when (
            val resolution =
                RecoveryStreamingJournalMapper.resolve(attempt, journal.persistOutcome(attempt))
        ) {
            is RecoveryStreamingPersistenceResolution.ExactReceipt ->
                FreshExecution.Pending(
                    RecoveryStreamingPendingPersistedResult.exactReadback(
                        outcome,
                        range,
                        resolution.receipt,
                        { publicStream?.close() },
                    )
                )
            is RecoveryStreamingPersistenceResolution.Failed -> {
                runCatching { publicStream?.close() }
                FreshExecution.NonPersistable(resolution.result)
            }
            is RecoveryStreamingPersistenceResolution.ProvenRollback -> {
                runCatching { publicStream?.close() }
                FreshExecution.NonPersistable(
                    if (resolution.semanticOutcome == StreamSemanticOutcome.PERSISTED_VALID) {
                        RecoveryStreamingReconciliationResult.Retry.of(
                            RecoveryStreamingResultStage.JOURNAL,
                            RecoveryStreamingResultClassification.JOURNAL_OPERATIONAL,
                            RecoveryStreamingSafeExceptionType.SQLITE,
                        )
                    } else {
                        when (resolution.semanticOutcome) {
                            StreamSemanticOutcome.PERSISTED_REJECTED ->
                                RecoveryStreamingReconciliationResult.Rejected.original(outcome)
                            StreamSemanticOutcome.PERSISTED_FATAL ->
                                RecoveryStreamingReconciliationResult.Fatal.original(outcome)
                            StreamSemanticOutcome.PERSISTED_VALID -> error("Handled above")
                        }
                    }
                )
            }
        }
    }

    private sealed interface FreshExecution {
        data class Pending(val value: RecoveryStreamingPendingPersistedResult) : FreshExecution

        data class NonPersistable(val result: RecoveryStreamingReconciliationResult) :
            FreshExecution
    }

    private fun replay(
        outcome: RecoveryStreamingOutcomeRow,
        request: RecoveryStreamingControllerRequest,
        replayAccess: RecoveryStreamingReplayAccess,
    ): RecoveryStreamingReconciliationResult {
        if (outcome.witness() != request.witness) return journalStructural()
        val range =
            when (val result = journal.rangeByOutcome(outcome.outcomeId)) {
                is RecoveryStreamingJournalReadResult.Value -> result.value
                else -> return nonPersistable(RecoveryStreamingJournalMapper.readFailure(result))
            }
        if (
            (outcome.requiredRangeStart == null) != (range == null) ||
                (range != null && range.outcomeId != outcome.outcomeId)
        ) {
            return nonPersistable(
                RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
                    RecoveryStreamingResultStage.JOURNAL,
                    RecoveryStreamingResultClassification.STREAM_RANGE_QUARANTINE_COLLISION,
                    buildList {
                        add(RecoveryStreamingExistingEvidence.Outcome(outcome.outcomeId))
                        range?.let {
                            add(RecoveryStreamingExistingEvidence.Range(it.rangeIntentId))
                        }
                    },
                )
            )
        }
        val verified =
            try {
                source.verifyReplayHashOnly(
                    replayAccess,
                    RecoveryStreamReplayRequest(request.witness.runId, outcome.outcomeId),
                )
            } catch (failure: RecoveryStreamingSourceException) {
                return nonPersistable(mapSourceFailure(failure))
            }
        if (
            verified !is RecoveryReplayHashOnlyResult.ExactStoredSourceMetadata ||
                verified.observedBytes != outcome.observedSourceBytes ||
                verified.sourceSha256 != outcome.observedSourceSha256
        ) {
            return nonPersistable(
                RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
                    RecoveryStreamingResultStage.SOURCE_PROOF,
                    RecoveryStreamingResultClassification.STREAM_SOURCE_IDENTITY_CHANGED,
                )
            )
        }
        return RecoveryStreamingPendingPersistedResult.exactReadback(
                outcome,
                range,
                RecoveryStreamingJournalResult.Receipt(
                    outcome.outcomeId,
                    range?.rangeIntentId,
                    replayed = true,
                ),
            ) {}
            .complete(sourceDescriptorCloseFailed = false, evidenceSink)
    }

    private fun journalStructural(): RecoveryStreamingReconciliationResult =
        fatal(RecoveryStreamingResultClassification.JOURNAL_STRUCTURAL)

    private fun fatal(
        classification: RecoveryStreamingResultClassification
    ): RecoveryStreamingReconciliationResult =
        nonPersistable(
            RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
                RecoveryStreamingResultStage.PREREQUISITE,
                classification,
            )
        )
}
