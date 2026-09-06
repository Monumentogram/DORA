@file:Suppress("LongMethod", "LongParameterList")

package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.BoundedBinaryWriter
import com.monumentogram.dora.poc.recovery.contract.RecoveryContract
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifest
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingExistingEvidence
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRangeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRowValidation
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.contract.StreamBoundaryResult
import com.monumentogram.dora.poc.recovery.contract.StreamCheckpointIntersection
import com.monumentogram.dora.poc.recovery.contract.StreamDecision
import com.monumentogram.dora.poc.recovery.contract.StreamDiagnosticBranch
import com.monumentogram.dora.poc.recovery.contract.StreamDiagnosticClassification
import com.monumentogram.dora.poc.recovery.contract.StreamDiagnosticStage
import com.monumentogram.dora.poc.recovery.contract.StreamRangeCertainty
import com.monumentogram.dora.poc.recovery.contract.StreamSourceMatch
import com.monumentogram.dora.poc.recovery.contract.StreamTerminal
import com.monumentogram.dora.poc.recovery.contract.writeSha256
import java.util.Collections

internal enum class RecoveryFailureCategory {
    UNSAFE_PARENT,
    CORRUPT_LEAF,
    MISSING_ARTIFACT,
    STRUCTURAL,
    AUTHENTICATION_REJECTED,
    OPERATIONAL,
    UNKNOWN,
    UNKNOWN_OUTCOME,
}

internal enum class RecoveryFailureStage {
    ALIAS_OBSERVATION,
    ALIAS_OPEN,
    ENVELOPE_BINDING,
    ENVELOPE_PARSE,
    MANIFEST_PAYLOAD_DECRYPT,
    MANIFEST_PLAINTEXT,
    MANIFEST_SEMANTICS,
    CONFIRMATION_PAYLOAD_DECRYPT,
    CONFIRMATION_PLAINTEXT,
    UNIT_PAYLOAD_DECRYPT,
    UNIT_PLAINTEXT,
    ARTIFACT_PATH,
    ARTIFACT_IO,
    JOURNAL,
    OPERATIONAL,
}

/** Closed v0.8 controller stage vocabulary. These values are never persisted in schema v4. */
internal enum class RecoveryStreamingResultStage {
    LEASE,
    PREREQUISITE,
    SOURCE_PROOF,
    RANGE_ADMISSION,
    STREAM_READ,
    JOURNAL,
}

/** Closed v0.8 non-persistable controller classification vocabulary. */
internal enum class RecoveryStreamingResultClassification {
    STREAM_CHECKPOINT_MISSING,
    STREAM_CHECKPOINT_STRUCTURAL,
    STREAM_CHECKPOINT_AUTHENTICATION_REJECTED,
    STREAM_CHECKPOINT_AUTHENTICATION_OPERATIONAL,
    STREAM_CHECKPOINT_SPLIT_BRAIN,
    STREAM_SOURCE_WITNESS_MISSING,
    UNSAFE_PATH,
    STREAM_SOURCE_IDENTITY_CHANGED,
    STREAM_SOURCE_EXTENT_LIMIT_EXCEEDED,
    RUN_LEASE_CONTENDED,
    STREAM_ACTIVE_RANGE_DENIED,
    STREAM_ZERO_PROGRESS,
    STREAM_READ_CROSSES_ACCEPTED_END,
    STREAM_PUBLIC_READ_OPERATIONAL,
    JOURNAL_STRUCTURAL,
    JOURNAL_ATTEMPT_CONFLICT,
    STREAM_RANGE_QUARANTINE_COLLISION,
    ARTIFACT_IO_BEFORE_EXACT_SOURCE_HASH,
    JOURNAL_OPERATIONAL,
    JOURNAL_COMMIT_STATE_UNRESOLVED,
}

/** A deliberately short exception projection. It is carried only by a retry mapping. */
internal enum class RecoveryStreamingSafeExceptionType {
    NONE,
    IO,
    CRYPTO,
    SQLITE,
}

internal enum class RecoveryStreamingResultDisposition {
    RETRY,
    REJECTED,
    FATAL,
}

/**
 * The exhaustive v0.8 outward failure map. Durable v0.7 diagnostic classifications remain in
 * [com.monumentogram.dora.poc.recovery.contract.StreamDiagnosticClassification].
 */
internal class RecoveryStreamingResultMapping
private constructor(
    val disposition: RecoveryStreamingResultDisposition,
    val stage: RecoveryStreamingResultStage,
    val classification: RecoveryStreamingResultClassification,
    val allowedSafeExceptionTypes: Set<RecoveryStreamingSafeExceptionType>,
) {
    init {
        kotlin.require(
            (disposition == RecoveryStreamingResultDisposition.RETRY) ==
                allowedSafeExceptionTypes.isNotEmpty()
        ) {
            "Safe exception types belong only to Retry"
        }
    }

    companion object {
        val entries: List<RecoveryStreamingResultMapping> =
            listOf(
                fatal(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification.STREAM_CHECKPOINT_MISSING,
                ),
                fatal(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification.STREAM_CHECKPOINT_STRUCTURAL,
                ),
                fatal(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification.STREAM_CHECKPOINT_AUTHENTICATION_REJECTED,
                ),
                retry(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification
                        .STREAM_CHECKPOINT_AUTHENTICATION_OPERATIONAL,
                    RecoveryStreamingSafeExceptionType.CRYPTO,
                ),
                fatal(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification.STREAM_CHECKPOINT_SPLIT_BRAIN,
                ),
                fatal(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification.STREAM_SOURCE_WITNESS_MISSING,
                ),
                fatal(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification.UNSAFE_PATH,
                ),
                fatal(
                    RecoveryStreamingResultStage.SOURCE_PROOF,
                    RecoveryStreamingResultClassification.STREAM_SOURCE_IDENTITY_CHANGED,
                ),
                rejected(
                    RecoveryStreamingResultStage.SOURCE_PROOF,
                    RecoveryStreamingResultClassification.STREAM_SOURCE_EXTENT_LIMIT_EXCEEDED,
                ),
                retry(
                    RecoveryStreamingResultStage.LEASE,
                    RecoveryStreamingResultClassification.RUN_LEASE_CONTENDED,
                    RecoveryStreamingSafeExceptionType.NONE,
                ),
                fatal(
                    RecoveryStreamingResultStage.RANGE_ADMISSION,
                    RecoveryStreamingResultClassification.STREAM_ACTIVE_RANGE_DENIED,
                ),
                retry(
                    RecoveryStreamingResultStage.STREAM_READ,
                    RecoveryStreamingResultClassification.STREAM_ZERO_PROGRESS,
                    RecoveryStreamingSafeExceptionType.NONE,
                ),
                fatal(
                    RecoveryStreamingResultStage.STREAM_READ,
                    RecoveryStreamingResultClassification.STREAM_READ_CROSSES_ACCEPTED_END,
                ),
                retry(
                    RecoveryStreamingResultStage.STREAM_READ,
                    RecoveryStreamingResultClassification.STREAM_PUBLIC_READ_OPERATIONAL,
                    RecoveryStreamingSafeExceptionType.IO,
                    RecoveryStreamingSafeExceptionType.CRYPTO,
                ),
                fatal(
                    RecoveryStreamingResultStage.JOURNAL,
                    RecoveryStreamingResultClassification.JOURNAL_STRUCTURAL,
                ),
                fatal(
                    RecoveryStreamingResultStage.JOURNAL,
                    RecoveryStreamingResultClassification.JOURNAL_ATTEMPT_CONFLICT,
                ),
                fatal(
                    RecoveryStreamingResultStage.JOURNAL,
                    RecoveryStreamingResultClassification.STREAM_RANGE_QUARANTINE_COLLISION,
                ),
                retry(
                    RecoveryStreamingResultStage.SOURCE_PROOF,
                    RecoveryStreamingResultClassification.ARTIFACT_IO_BEFORE_EXACT_SOURCE_HASH,
                    RecoveryStreamingSafeExceptionType.IO,
                ),
                retry(
                    RecoveryStreamingResultStage.JOURNAL,
                    RecoveryStreamingResultClassification.JOURNAL_OPERATIONAL,
                    RecoveryStreamingSafeExceptionType.SQLITE,
                ),
                retry(
                    RecoveryStreamingResultStage.JOURNAL,
                    RecoveryStreamingResultClassification.JOURNAL_COMMIT_STATE_UNRESOLVED,
                    RecoveryStreamingSafeExceptionType.SQLITE,
                ),
            )

        private val byClassification =
            entries.associateBy(RecoveryStreamingResultMapping::classification).also {
                kotlin.require(it.size == RecoveryStreamingResultClassification.entries.size) {
                    "Result mapping must cover every classification exactly once"
                }
            }

        fun require(
            stage: RecoveryStreamingResultStage,
            classification: RecoveryStreamingResultClassification,
            safeExceptionType: RecoveryStreamingSafeExceptionType?,
        ): RecoveryStreamingResultMapping {
            val mapping = byClassification.getValue(classification)
            kotlin.require(mapping.stage == stage) { "Unlisted result stage/classification" }
            if (mapping.disposition == RecoveryStreamingResultDisposition.RETRY) {
                kotlin.require(safeExceptionType in mapping.allowedSafeExceptionTypes) {
                    "Unlisted retry exception type"
                }
            } else {
                kotlin.require(safeExceptionType == null) {
                    "Rejected and Fatal cannot carry an exception type"
                }
            }
            return mapping
        }

        private fun retry(
            stage: RecoveryStreamingResultStage,
            classification: RecoveryStreamingResultClassification,
            vararg exceptionTypes: RecoveryStreamingSafeExceptionType,
        ) =
            RecoveryStreamingResultMapping(
                RecoveryStreamingResultDisposition.RETRY,
                stage,
                classification,
                exceptionTypes.toSet(),
            )

        private fun rejected(
            stage: RecoveryStreamingResultStage,
            classification: RecoveryStreamingResultClassification,
        ) =
            RecoveryStreamingResultMapping(
                RecoveryStreamingResultDisposition.REJECTED,
                stage,
                classification,
                emptySet(),
            )

        private fun fatal(
            stage: RecoveryStreamingResultStage,
            classification: RecoveryStreamingResultClassification,
        ) =
            RecoveryStreamingResultMapping(
                RecoveryStreamingResultDisposition.FATAL,
                stage,
                classification,
                emptySet(),
            )
    }
}

internal enum class RecoveryStreamingPostReceiptCleanup {
    NONE,
    PUBLIC_STREAM_CLOSE_FAILED,
    SOURCE_DESCRIPTOR_CLOSE_FAILED,
    PUBLIC_STREAM_AND_SOURCE_DESCRIPTOR_CLOSE_FAILED,
}

internal enum class RecoveryStreamingEvidenceDelivery {
    DELIVERED,
    PENDING,
}

internal enum class RecoveryStreamingExistingRecordKind {
    STREAM_CHECKPOINT,
    STREAM_OUTCOME,
    STREAM_RANGE,
}

internal data class RecoveryStreamingPersistenceReceipt(
    val outcomeId: Sha256Value,
    val optionalRangeIntentId: Sha256Value?,
    val replayed: Boolean,
    val postReceiptCleanup: RecoveryStreamingPostReceiptCleanup,
    val evidenceDelivery: RecoveryStreamingEvidenceDelivery,
)

/**
 * Sanitized controller evidence. It deliberately contains neither exception text nor source data.
 */
internal class RecoveryStreamingEvidenceEvent
private constructor(
    val stage: RecoveryStreamingResultStage?,
    val classification: RecoveryStreamingResultClassification?,
    val safeExceptionType: RecoveryStreamingSafeExceptionType?,
    val outcomeId: Sha256Value?,
    val rangeIntentId: Sha256Value?,
    val attemptedOutcomeId: Sha256Value?,
    val attemptedRangeId: Sha256Value?,
    val replayed: Boolean?,
    val persistedDecision: StreamDecision?,
    val diagnosticBranch: StreamDiagnosticBranch?,
    val diagnosticStage: StreamDiagnosticStage?,
    val diagnosticClassification: StreamDiagnosticClassification?,
    val acceptedEnd: ULong?,
    val checkpointContextEnd: ULong?,
    val recoveredEnd: ULong?,
    val recoveredBeyondCheckpointBytes: ULong?,
    val tailLossBytes: ULong?,
    val terminal: StreamTerminal?,
    val preFaultSourceMatch: StreamSourceMatch?,
    val checkpointIntersection: StreamCheckpointIntersection?,
    val checkpointIntersectionProven: Boolean?,
    val observationPresent: Boolean?,
    val boundaryResult: StreamBoundaryResult?,
    val rangeStart: ULong?,
    val rangeEnd: ULong?,
    val rangeCertainty: StreamRangeCertainty?,
    val postReceiptCleanup: RecoveryStreamingPostReceiptCleanup?,
    existingEvidenceReferences: List<RecoveryStreamingExistingEvidenceReference>,
) {
    val existingEvidenceReferences: List<RecoveryStreamingExistingEvidenceReference> =
        Collections.unmodifiableList(ArrayList(existingEvidenceReferences))

    companion object {
        fun persisted(
            row: RecoveryStreamingOutcomeRow,
            range: RecoveryStreamingRangeRow?,
            receiptCore: RecoveryStreamingPersistenceReceiptCore,
            cleanup: RecoveryStreamingPostReceiptCleanup,
        ): RecoveryStreamingEvidenceEvent {
            require((row.requiredRangeStart == null) == (range == null)) {
                "Persisted evidence range presence does not match outcome"
            }
            range?.let { RecoveryStreamingRowValidation.validateParentChild(row, it) }
            return RecoveryStreamingEvidenceEvent(
                stage = null,
                classification = null,
                safeExceptionType = null,
                outcomeId = receiptCore.outcomeId,
                rangeIntentId = receiptCore.optionalRangeIntentId,
                attemptedOutcomeId = null,
                attemptedRangeId = null,
                replayed = receiptCore.replayed,
                persistedDecision = row.decision,
                diagnosticBranch = row.diagnosticBranch,
                diagnosticStage = row.diagnosticStage,
                diagnosticClassification = row.diagnosticClassification,
                acceptedEnd = row.acceptedEnd,
                checkpointContextEnd = row.checkpointContextEnd,
                recoveredEnd = row.recoveredEnd,
                recoveredBeyondCheckpointBytes = row.recoveredBeyondCheckpointBytes,
                tailLossBytes = row.tailLossBytes,
                terminal = row.terminal,
                preFaultSourceMatch = row.preFaultSourceMatch,
                checkpointIntersection = row.checkpointIntersection,
                checkpointIntersectionProven =
                    row.checkpointIntersection == StreamCheckpointIntersection.PROVEN,
                observationPresent = row.rejectedObservation != null,
                boundaryResult = row.rejectedObservation?.boundaryResult,
                rangeStart = range?.rangeStart,
                rangeEnd = range?.rangeEnd,
                rangeCertainty = range?.certainty,
                postReceiptCleanup = cleanup,
                existingEvidenceReferences = emptyList(),
            )
        }

        fun nonPersistable(result: RecoveryStreamingReconciliationResult) =
            when (result) {
                is RecoveryStreamingReconciliationResult.PersistedValid ->
                    error("Persisted result requires persisted evidence")
                is RecoveryStreamingReconciliationResult.Retry ->
                    RecoveryStreamingEvidenceEvent(
                        stage = result.stage,
                        classification = result.classification,
                        safeExceptionType = result.safeExceptionType,
                        outcomeId = null,
                        rangeIntentId = null,
                        attemptedOutcomeId = result.attemptedOutcomeId,
                        attemptedRangeId = result.attemptedRangeId,
                        replayed = null,
                        persistedDecision = null,
                        diagnosticBranch = null,
                        diagnosticStage = null,
                        diagnosticClassification = null,
                        acceptedEnd = null,
                        checkpointContextEnd = null,
                        recoveredEnd = null,
                        recoveredBeyondCheckpointBytes = null,
                        tailLossBytes = null,
                        terminal = null,
                        preFaultSourceMatch = null,
                        checkpointIntersection = null,
                        checkpointIntersectionProven = null,
                        observationPresent = null,
                        boundaryResult = null,
                        rangeStart = null,
                        rangeEnd = null,
                        rangeCertainty = null,
                        postReceiptCleanup = null,
                        existingEvidenceReferences = result.existingEvidenceReferences,
                    )
                is RecoveryStreamingReconciliationResult.Rejected -> {
                    require(result.persistedDiagnostic == null) {
                        "Persisted result requires persisted evidence"
                    }
                    val original = result.originalDiagnostic
                    RecoveryStreamingEvidenceEvent(
                        stage = result.stage,
                        classification = result.classification,
                        safeExceptionType = null,
                        outcomeId = null,
                        rangeIntentId = null,
                        attemptedOutcomeId = null,
                        attemptedRangeId = null,
                        replayed = null,
                        persistedDecision = null,
                        diagnosticBranch = original?.diagnosticBranch,
                        diagnosticStage = original?.diagnosticStage,
                        diagnosticClassification = original?.diagnosticClassification,
                        acceptedEnd = null,
                        checkpointContextEnd = null,
                        recoveredEnd = null,
                        recoveredBeyondCheckpointBytes = null,
                        tailLossBytes = null,
                        terminal = null,
                        preFaultSourceMatch = null,
                        checkpointIntersection = null,
                        checkpointIntersectionProven = null,
                        observationPresent = null,
                        boundaryResult = null,
                        rangeStart = null,
                        rangeEnd = null,
                        rangeCertainty = null,
                        postReceiptCleanup = null,
                        existingEvidenceReferences = result.existingEvidenceReferences,
                    )
                }
                is RecoveryStreamingReconciliationResult.Fatal -> {
                    require(result.persistedDiagnostic == null) {
                        "Persisted result requires persisted evidence"
                    }
                    val original = result.originalDiagnostic
                    RecoveryStreamingEvidenceEvent(
                        stage = result.stage,
                        classification = result.classification,
                        safeExceptionType = null,
                        outcomeId = null,
                        rangeIntentId = null,
                        attemptedOutcomeId = null,
                        attemptedRangeId = null,
                        replayed = null,
                        persistedDecision = null,
                        diagnosticBranch = original?.diagnosticBranch,
                        diagnosticStage = original?.diagnosticStage,
                        diagnosticClassification = original?.diagnosticClassification,
                        acceptedEnd = null,
                        checkpointContextEnd = null,
                        recoveredEnd = null,
                        recoveredBeyondCheckpointBytes = null,
                        tailLossBytes = null,
                        terminal = null,
                        preFaultSourceMatch = null,
                        checkpointIntersection = null,
                        checkpointIntersectionProven = null,
                        observationPresent = null,
                        boundaryResult = null,
                        rangeStart = null,
                        rangeEnd = null,
                        rangeCertainty = null,
                        postReceiptCleanup = null,
                        existingEvidenceReferences = result.existingEvidenceReferences,
                    )
                }
            }
    }
}

internal fun interface RecoveryStreamingEvidenceSink {
    fun emit(event: RecoveryStreamingEvidenceEvent)
}

/** The journal receipt frozen before public-stream and source-descriptor cleanup begins. */
internal data class RecoveryStreamingPersistenceReceiptCore(
    val outcomeId: Sha256Value,
    val optionalRangeIntentId: Sha256Value?,
    val replayed: Boolean,
)

internal class RecoveryStreamingPendingPersistedResult
private constructor(
    private val row: RecoveryStreamingOutcomeRow,
    private val range: RecoveryStreamingRangeRow?,
    private val receiptCore: RecoveryStreamingPersistenceReceiptCore,
    private val publicStreamCloseFailed: Boolean,
) {
    private var completed = false

    fun complete(
        sourceDescriptorCloseFailed: Boolean,
        evidenceSink: RecoveryStreamingEvidenceSink,
    ): RecoveryStreamingReconciliationResult {
        check(!completed) { "Persisted result finalization is single-use" }
        completed = true
        val cleanup = cleanup(publicStreamCloseFailed, sourceDescriptorCloseFailed)
        val delivered =
            runCatching {
                    evidenceSink.emit(
                        RecoveryStreamingEvidenceEvent.persisted(row, range, receiptCore, cleanup)
                    )
                }
                .isSuccess
        val receipt =
            RecoveryStreamingPersistenceReceipt(
                receiptCore.outcomeId,
                receiptCore.optionalRangeIntentId,
                receiptCore.replayed,
                cleanup,
                if (delivered) {
                    RecoveryStreamingEvidenceDelivery.DELIVERED
                } else {
                    RecoveryStreamingEvidenceDelivery.PENDING
                },
            )
        return when (row.decision) {
            StreamDecision.VALID ->
                RecoveryStreamingReconciliationResult.PersistedValid.from(row, receipt)
            StreamDecision.REJECTED ->
                RecoveryStreamingReconciliationResult.Rejected.persisted(row, receipt)
            StreamDecision.FATAL ->
                RecoveryStreamingReconciliationResult.Fatal.persisted(row, receipt)
        }
    }

    companion object {
        fun exactReadback(
            row: RecoveryStreamingOutcomeRow,
            range: RecoveryStreamingRangeRow?,
            receipt: RecoveryStreamingJournalResult.Receipt,
            closePublicStream: () -> Unit,
        ): RecoveryStreamingPendingPersistedResult {
            require(receipt.outcomeId == row.outcomeId) {
                "Journal receipt outcome does not match the exact attempted row"
            }
            require(receipt.rangeIntentId == range?.rangeIntentId) {
                "Journal receipt range does not match the exact attempted row"
            }
            require((row.requiredRangeStart == null) == (range == null)) {
                "Exact attempted range presence does not match outcome"
            }
            require(range == null || range.outcomeId == row.outcomeId) {
                "Exact attempted range has another parent"
            }
            val closeFailed = runCatching(closePublicStream).isFailure
            return RecoveryStreamingPendingPersistedResult(
                row,
                range,
                RecoveryStreamingPersistenceReceiptCore(
                    receipt.outcomeId,
                    receipt.rangeIntentId,
                    receipt.replayed,
                ),
                closeFailed,
            )
        }

        private fun cleanup(
            publicStreamCloseFailed: Boolean,
            sourceDescriptorCloseFailed: Boolean,
        ): RecoveryStreamingPostReceiptCleanup =
            when {
                publicStreamCloseFailed && sourceDescriptorCloseFailed ->
                    RecoveryStreamingPostReceiptCleanup
                        .PUBLIC_STREAM_AND_SOURCE_DESCRIPTOR_CLOSE_FAILED
                publicStreamCloseFailed ->
                    RecoveryStreamingPostReceiptCleanup.PUBLIC_STREAM_CLOSE_FAILED
                sourceDescriptorCloseFailed ->
                    RecoveryStreamingPostReceiptCleanup.SOURCE_DESCRIPTOR_CLOSE_FAILED
                else -> RecoveryStreamingPostReceiptCleanup.NONE
            }
    }
}

internal object RecoveryStreamingEvidenceFinalizer {
    fun nonPersistable(
        result: RecoveryStreamingReconciliationResult,
        evidenceSink: RecoveryStreamingEvidenceSink,
    ): RecoveryStreamingReconciliationResult {
        runCatching { evidenceSink.emit(RecoveryStreamingEvidenceEvent.nonPersistable(result)) }
        return result
    }
}

internal data class RecoveryStreamingExistingEvidenceReference(
    val recordKind: RecoveryStreamingExistingRecordKind,
    val existingId: Sha256Value,
    val existingIdentitySha256: Sha256Value,
) {
    init {
        require(existingId == existingIdentitySha256) {
            "Streaming evidence reference ID and identity must be identical"
        }
    }
}

internal object RecoveryStreamingExistingEvidenceReferences {
    fun forClassification(
        classification: RecoveryStreamingResultClassification,
        strictDecoded: List<RecoveryStreamingExistingEvidence>,
    ): List<RecoveryStreamingExistingEvidenceReference> {
        val allowedKinds = allowedKinds(classification)
        val references = strictDecoded.map { evidence ->
            val kind = evidence.kind()
            require(kind in allowedKinds) { "Existing reference kind is forbidden" }
            RecoveryStreamingExistingEvidenceReference(kind, evidence.identity, evidence.identity)
        }
        val deduplicated = references.distinctBy { it.recordKind to it.existingId }
        val ordered =
            deduplicated.sortedWith(
                compareBy<RecoveryStreamingExistingEvidenceReference> { it.recordKind.ordinal }
                    .thenComparator { left, right ->
                        compareUnsigned(
                            left.existingId.toByteArray(),
                            right.existingId.toByteArray(),
                        )
                    }
            )
        return Collections.unmodifiableList(ordered)
    }

    private fun allowedKinds(
        classification: RecoveryStreamingResultClassification
    ): Set<RecoveryStreamingExistingRecordKind> =
        when (classification) {
            RecoveryStreamingResultClassification.STREAM_CHECKPOINT_SPLIT_BRAIN ->
                setOf(RecoveryStreamingExistingRecordKind.STREAM_CHECKPOINT)
            RecoveryStreamingResultClassification.JOURNAL_ATTEMPT_CONFLICT ->
                setOf(RecoveryStreamingExistingRecordKind.STREAM_OUTCOME)
            RecoveryStreamingResultClassification.STREAM_RANGE_QUARANTINE_COLLISION,
            RecoveryStreamingResultClassification.STREAM_ACTIVE_RANGE_DENIED ->
                setOf(
                    RecoveryStreamingExistingRecordKind.STREAM_OUTCOME,
                    RecoveryStreamingExistingRecordKind.STREAM_RANGE,
                )
            else -> emptySet()
        }

    private fun RecoveryStreamingExistingEvidence.kind(): RecoveryStreamingExistingRecordKind =
        when (this) {
            is RecoveryStreamingExistingEvidence.Checkpoint ->
                RecoveryStreamingExistingRecordKind.STREAM_CHECKPOINT
            is RecoveryStreamingExistingEvidence.Outcome ->
                RecoveryStreamingExistingRecordKind.STREAM_OUTCOME
            is RecoveryStreamingExistingEvidence.Range ->
                RecoveryStreamingExistingRecordKind.STREAM_RANGE
        }

    private fun compareUnsigned(left: ByteArray, right: ByteArray): Int {
        require(left.size == Sha256Value.SIZE_BYTES && right.size == Sha256Value.SIZE_BYTES)
        left.indices.forEach { index ->
            val comparison =
                (left[index].toInt() and UNSIGNED_BYTE_MASK).compareTo(
                    right[index].toInt() and UNSIGNED_BYTE_MASK
                )
            if (comparison != 0) return comparison
        }
        return 0
    }

    private const val UNSIGNED_BYTE_MASK = 0xff
}

internal class RecoveryStreamingRejectedObservation
private constructor(
    val candidateEnd: ULong,
    val completedPlaintextSha256: Sha256Value,
    val oraclePrefixSha256: Sha256Value,
    val oraclePrefixEqual: Boolean,
    val comparedEnd: ULong,
    val firstMismatchOffset: ULong?,
    val equalPrefixSha256: Sha256Value?,
    val expectedOracleByte: UByte?,
    val observedPlaintextByte: UByte?,
    val observedTailLossBytes: ULong,
    val boundaryResult: com.monumentogram.dora.poc.recovery.contract.StreamBoundaryResult,
    val boundaryBytes: ULong?,
    val rejectedObservationSha256: Sha256Value,
) {
    companion object {
        fun fromPersisted(row: RecoveryStreamingOutcomeRow): RecoveryStreamingRejectedObservation {
            val observation = requireNotNull(row.rejectedObservation)
            return RecoveryStreamingRejectedObservation(
                observation.candidateEnd,
                observation.completedPlaintextSha256,
                observation.oraclePrefixSha256,
                observation.oraclePrefixEqual,
                observation.comparedEnd,
                observation.firstMismatchOffset,
                observation.equalPrefixSha256,
                observation.expectedOracleByte,
                observation.observedPlaintextByte,
                observation.observedTailLossBytes,
                observation.boundaryResult,
                observation.boundaryBytes,
                requireNotNull(row.rejectedObservationSha256),
            )
        }
    }
}

internal class RecoveryStreamingPersistedDiagnostic
private constructor(
    val receipt: RecoveryStreamingPersistenceReceipt,
    val diagnosticBranch: StreamDiagnosticBranch,
    val diagnosticStage: StreamDiagnosticStage,
    val diagnosticClassification: StreamDiagnosticClassification,
    val checkpointIntersectionProven: Boolean,
    val provenCheckpointEnd: ULong?,
    val rejectedObservation: RecoveryStreamingRejectedObservation?,
) {
    companion object {
        fun from(
            row: RecoveryStreamingOutcomeRow,
            receipt: RecoveryStreamingPersistenceReceipt,
            expectedDecision: StreamDecision,
        ): RecoveryStreamingPersistedDiagnostic {
            require(row.decision == expectedDecision) { "Persisted decision does not match result" }
            validateReceipt(row, receipt)
            val isPre = row.diagnosticBranch == StreamDiagnosticBranch.PRE_INTERSECTION
            val isPost = row.diagnosticBranch == StreamDiagnosticBranch.POST_INTERSECTION
            require(isPre || isPost) { "Persisted diagnostic branch is invalid" }
            if (isPre) {
                require(row.rejectedObservation == null) {
                    "PRE diagnostic cannot carry observation"
                }
            } else {
                require(row.rejectedObservation != null) { "POST diagnostic requires observation" }
            }
            return RecoveryStreamingPersistedDiagnostic(
                receipt,
                row.diagnosticBranch,
                row.diagnosticStage,
                row.diagnosticClassification,
                isPost,
                row.checkpointContextEnd.takeIf { isPost },
                row.takeIf { isPost }?.let(RecoveryStreamingRejectedObservation::fromPersisted),
            )
        }

        private fun validateReceipt(
            row: RecoveryStreamingOutcomeRow,
            receipt: RecoveryStreamingPersistenceReceipt,
        ) {
            require(receipt.outcomeId == row.outcomeId) { "Receipt outcome ID does not match row" }
            require((receipt.optionalRangeIntentId == null) == (row.requiredRangeStart == null)) {
                "Receipt range presence does not match row"
            }
        }
    }
}

internal class RecoveryStreamingOriginalDiagnostic
private constructor(
    val diagnosticBranch: StreamDiagnosticBranch,
    val diagnosticStage: StreamDiagnosticStage,
    val diagnosticClassification: StreamDiagnosticClassification,
    val checkpointIntersectionProven: Boolean,
    val provenCheckpointEnd: ULong?,
    val rejectedObservation: RecoveryStreamingRejectedObservation?,
) {
    companion object {
        fun from(
            row: RecoveryStreamingOutcomeRow,
            expectedDecision: StreamDecision,
        ): RecoveryStreamingOriginalDiagnostic {
            require(row.decision == expectedDecision) { "Original decision does not match result" }
            val isPre = row.diagnosticBranch == StreamDiagnosticBranch.PRE_INTERSECTION
            val isPost = row.diagnosticBranch == StreamDiagnosticBranch.POST_INTERSECTION
            require(isPre || isPost) { "Original diagnostic branch is invalid" }
            if (isPre) {
                require(row.rejectedObservation == null) {
                    "PRE diagnostic cannot carry observation"
                }
            } else {
                require(row.rejectedObservation != null) { "POST diagnostic requires observation" }
            }
            return RecoveryStreamingOriginalDiagnostic(
                row.diagnosticBranch,
                row.diagnosticStage,
                row.diagnosticClassification,
                isPost,
                row.checkpointContextEnd.takeIf { isPost },
                row.takeIf { isPost }?.let(RecoveryStreamingRejectedObservation::fromPersisted),
            )
        }
    }
}

internal sealed interface RecoveryStreamingReconciliationResult {
    class PersistedValid
    private constructor(
        val receipt: RecoveryStreamingPersistenceReceipt,
        val acceptedEnd: ULong,
        val committedEnd: ULong,
        val recoveredEnd: ULong,
        val terminal: StreamTerminal,
    ) : RecoveryStreamingReconciliationResult {
        companion object {
            fun from(
                row: RecoveryStreamingOutcomeRow,
                receipt: RecoveryStreamingPersistenceReceipt,
            ): PersistedValid {
                require(row.decision == StreamDecision.VALID) { "Valid result requires VALID row" }
                require(row.outcomeId == receipt.outcomeId) {
                    "Receipt outcome ID does not match row"
                }
                require(
                    (row.requiredRangeStart == null) == (receipt.optionalRangeIntentId == null)
                ) {
                    "Receipt range presence does not match row"
                }
                val recoveredEnd = requireNotNull(row.recoveredEnd)
                require(
                    row.checkpointContextEnd <= recoveredEnd && recoveredEnd <= row.acceptedEnd
                ) {
                    "Valid result endpoints are invalid"
                }
                require(row.acceptedEnd - recoveredEnd <= MAX_TAIL_LOSS) {
                    "Valid result exceeds the tail bound"
                }
                require(
                    row.terminal == StreamTerminal.AUTHENTICATED_EOF ||
                        row.terminal == StreamTerminal.AUTHENTICATION_FAILURE
                ) {
                    "Valid result terminal is invalid"
                }
                require(
                    row.terminal != StreamTerminal.AUTHENTICATED_EOF ||
                        receipt.optionalRangeIntentId == null
                ) {
                    "Authenticated EOF cannot retain a child range"
                }
                return PersistedValid(
                    receipt,
                    row.acceptedEnd,
                    row.checkpointContextEnd,
                    recoveredEnd,
                    row.terminal,
                )
            }

            private const val MAX_TAIL_LOSS = 8_160UL
        }
    }

    class Retry
    private constructor(
        val stage: RecoveryStreamingResultStage,
        val classification: RecoveryStreamingResultClassification,
        val safeExceptionType: RecoveryStreamingSafeExceptionType,
        val attemptedOutcomeId: Sha256Value?,
        val attemptedRangeId: Sha256Value?,
        val existingEvidenceReferences: List<RecoveryStreamingExistingEvidenceReference>,
    ) : RecoveryStreamingReconciliationResult {
        companion object {
            fun of(
                stage: RecoveryStreamingResultStage,
                classification: RecoveryStreamingResultClassification,
                safeExceptionType: RecoveryStreamingSafeExceptionType,
                attemptedOutcomeId: Sha256Value? = null,
                attemptedRangeId: Sha256Value? = null,
            ): Retry {
                val mapping =
                    RecoveryStreamingResultMapping.require(
                        stage,
                        classification,
                        safeExceptionType,
                    )
                require(mapping.disposition == RecoveryStreamingResultDisposition.RETRY)
                val unresolved =
                    classification ==
                        RecoveryStreamingResultClassification.JOURNAL_COMMIT_STATE_UNRESOLVED
                require(unresolved || (attemptedOutcomeId == null && attemptedRangeId == null)) {
                    "Attempted IDs belong only to unresolved commit state"
                }
                return Retry(
                    stage,
                    classification,
                    safeExceptionType,
                    attemptedOutcomeId,
                    attemptedRangeId,
                    emptyList(),
                )
            }
        }
    }

    class Rejected
    private constructor(
        val stage: RecoveryStreamingResultStage?,
        val classification: RecoveryStreamingResultClassification?,
        val persistedDiagnostic: RecoveryStreamingPersistedDiagnostic?,
        val originalDiagnostic: RecoveryStreamingOriginalDiagnostic?,
        val existingEvidenceReferences: List<RecoveryStreamingExistingEvidenceReference>,
    ) : RecoveryStreamingReconciliationResult {
        companion object {
            fun nonPersistable(
                stage: RecoveryStreamingResultStage,
                classification: RecoveryStreamingResultClassification,
            ): Rejected {
                val mapping = RecoveryStreamingResultMapping.require(stage, classification, null)
                require(mapping.disposition == RecoveryStreamingResultDisposition.REJECTED)
                return Rejected(stage, classification, null, null, emptyList())
            }

            fun persisted(
                row: RecoveryStreamingOutcomeRow,
                receipt: RecoveryStreamingPersistenceReceipt,
            ): Rejected =
                Rejected(
                    null,
                    null,
                    RecoveryStreamingPersistedDiagnostic.from(
                        row,
                        receipt,
                        StreamDecision.REJECTED,
                    ),
                    null,
                    emptyList(),
                )

            fun original(row: RecoveryStreamingOutcomeRow): Rejected =
                Rejected(
                    null,
                    null,
                    null,
                    RecoveryStreamingOriginalDiagnostic.from(row, StreamDecision.REJECTED),
                    emptyList(),
                )
        }
    }

    class Fatal
    private constructor(
        val stage: RecoveryStreamingResultStage?,
        val classification: RecoveryStreamingResultClassification?,
        val persistedDiagnostic: RecoveryStreamingPersistedDiagnostic?,
        val originalDiagnostic: RecoveryStreamingOriginalDiagnostic?,
        val existingEvidenceReferences: List<RecoveryStreamingExistingEvidenceReference>,
    ) : RecoveryStreamingReconciliationResult {
        companion object {
            fun nonPersistable(
                stage: RecoveryStreamingResultStage,
                classification: RecoveryStreamingResultClassification,
                strictDecodedEvidence: List<RecoveryStreamingExistingEvidence> = emptyList(),
            ): Fatal {
                val mapping = RecoveryStreamingResultMapping.require(stage, classification, null)
                require(mapping.disposition == RecoveryStreamingResultDisposition.FATAL)
                return Fatal(
                    stage,
                    classification,
                    null,
                    null,
                    RecoveryStreamingExistingEvidenceReferences.forClassification(
                        classification,
                        strictDecodedEvidence,
                    ),
                )
            }

            fun persisted(
                row: RecoveryStreamingOutcomeRow,
                receipt: RecoveryStreamingPersistenceReceipt,
            ): Fatal =
                Fatal(
                    null,
                    null,
                    RecoveryStreamingPersistedDiagnostic.from(
                        row,
                        receipt,
                        StreamDecision.FATAL,
                    ),
                    null,
                    emptyList(),
                )

            fun original(row: RecoveryStreamingOutcomeRow): Fatal =
                Fatal(
                    null,
                    null,
                    null,
                    RecoveryStreamingOriginalDiagnostic.from(row, StreamDecision.FATAL),
                    emptyList(),
                )
        }
    }
}

internal data class RecoveryFailureDiagnostic(
    val category: RecoveryFailureCategory,
    val type: String,
    val message: String,
    val stage: RecoveryFailureStage = RecoveryFailureStage.OPERATIONAL,
) {
    companion object {
        fun capture(
            category: RecoveryFailureCategory,
            error: Throwable,
            stage: RecoveryFailureStage = RecoveryFailureStage.OPERATIONAL,
        ) =
            RecoveryFailureDiagnostic(
                category,
                error::class.java.name.take(MAX_TEXT),
                (error.message ?: "").take(MAX_TEXT),
                stage,
            )

        private const val MAX_TEXT = 256
    }
}

internal class RecoveryConfirmationAuthenticationException(
    val diagnostic: RecoveryFailureDiagnostic,
    cause: Throwable,
) : RuntimeException(diagnostic.message, cause)

/** Canonical bounded identity of every authenticated journal-row field. */
internal object RecoveryAuthenticatedRowsDigest {
    private const val MAX_ROW_BYTES = 4_096
    private const val MAX_NAME_BYTES = 1_024
    private const val MAX_STATE_BYTES = 32
    private const val MAX_DOMAIN_BYTES = 32
    private const val MAX_RUN_ID_BYTES = 64

    @Suppress("SwallowedException")
    fun calculateOrNull(rows: List<RecoveryMicrofileUnitRow>): Sha256Value? {
        if (rows.size > RecoveryContract.MAX_MANIFEST_ENTRIES) return null
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.update(u32(rows.size))
            rows.forEach { row ->
                val encoded = encode(row)
                digest.update(u32(encoded.size))
                digest.update(encoded)
            }
            Sha256Value.fromBytes(digest.digest())
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    @Suppress("MagicNumber")
    private fun u32(value: Int): ByteArray {
        require(value >= 0)
        return byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte(),
        )
    }

    private fun encode(row: RecoveryMicrofileUnitRow): ByteArray {
        require(row.ciphertextBytes >= 0 && row.keyEnvelopeBytes >= 0)
        return BoundedBinaryWriter(MAX_ROW_BYTES)
            .apply {
                writeLp16Ascii("REC-I3-ROW-V1", MAX_DOMAIN_BYTES)
                writeLp16Ascii(row.runId, MAX_RUN_ID_BYTES)
                writeLp16Ascii(row.candidateId, RecoveryContract.MAX_CANDIDATE_ID_BYTES)
                writeU32(row.unitIndex)
                writeU64(row.plaintextStartInclusive)
                writeU64(row.plaintextEndExclusive)
                writeU32(row.cadenceSeconds)
                writeLp16Ascii(row.ciphertextRelativeName, MAX_NAME_BYTES)
                writeU64(row.ciphertextBytes.toULong())
                writeSha256(row.ciphertextSha256)
                writeLp16Ascii(row.keyEnvelopeRelativeName, MAX_NAME_BYTES)
                writeU64(row.keyEnvelopeBytes.toULong())
                writeSha256(row.keyEnvelopeSha256)
                writeU64(row.manifestGeneration)
                writeSha256(row.processingIntentId)
                writeLp16Ascii(row.state, MAX_STATE_BYTES)
            }
            .toByteArray()
    }
}

internal sealed interface ManifestAuthenticationOutcome {
    data class Authenticated(val manifest: RecoveryManifest) : ManifestAuthenticationOutcome

    data class Rejected(val diagnostic: RecoveryFailureDiagnostic) : ManifestAuthenticationOutcome
}

internal sealed interface UnitAuthenticationOutcome {
    class Authenticated(bytes: ByteArray) : UnitAuthenticationOutcome {
        private val value = bytes.copyOf()

        fun snapshot(): ByteArray = value.copyOf()
    }

    data class Rejected(val diagnostic: RecoveryFailureDiagnostic) : UnitAuthenticationOutcome
}
