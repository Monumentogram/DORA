package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.KeyRecoveryClassification
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryContract
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifest
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifestEntry
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.controller.ConfirmationDiagnostic
import com.monumentogram.dora.poc.recovery.controller.ConfirmationResult
import com.monumentogram.dora.poc.recovery.controller.KeyConfirmationSnapshot
import com.monumentogram.dora.poc.recovery.controller.RecoveryKeyConfirmationController
import com.monumentogram.dora.poc.recovery.coordination.ProcessRecoveryRunSingleWriterGuard
import com.monumentogram.dora.poc.recovery.coordination.RecoveryRunSingleWriterGuard
import com.monumentogram.dora.poc.recovery.crypto.KeyConfirmationDecryption
import java.io.ByteArrayOutputStream
import java.util.Collections

internal class RecoveryArtifactBytes(val relativeName: String, bytes: ByteArray) {
    private val value = bytes.copyOf()

    val size: Long
        get() = value.size.toLong()

    val sha256: Sha256Value
        get() = Sha256Value.calculate(value)

    fun snapshot(): ByteArray = value.copyOf()
}

internal interface RecoveryReconciliationSource {
    fun loadConfirmation(runId: RunId): KeyConfirmationSnapshot

    fun loadCandidate(runId: RunId): RecoveryCandidateSnapshot

    fun loadArtifact(runId: RunId, relativeName: String): RecoveryArtifactBytes?

    fun loadPendingQuarantine(runId: RunId): List<RecoveryQuarantineIntentRow> = emptyList()

    fun loadInventory(runId: RunId): List<RecoveryInventoryEntry> = emptyList()

    fun loadInventorySnapshot(
        runId: RunId,
        candidate: RecoveryCandidateSnapshot,
    ): RecoveryInventorySnapshot = RecoveryInventorySnapshot(loadInventory(runId), emptyList())
}

internal class RecoverySourceAccessException(
    val diagnostic: RecoveryFailureDiagnostic,
    cause: Throwable,
) : RuntimeException(diagnostic.message, cause)

internal data class RecoveryInventoryEntry(
    val input: RecoveryQuarantineIntentInput,
    val observedState: RecoveryQuarantineObservedState,
    val bootstrapBinding: QuarantineBootstrapBinding,
)

internal data class RecoveryReportOnlyInventoryEntry(
    val relativeName: String,
    val pathState: QuarantinePathState,
    val sourceBytes: ULong?,
    val sourceSha256: Sha256Value?,
    val knownIntentDestination: Boolean,
)

internal data class RecoveryInventorySnapshot(
    val active: List<RecoveryInventoryEntry>,
    val quarantine: List<RecoveryReportOnlyInventoryEntry>,
)

internal interface RecoveryReconciliationCrypto {
    fun authenticateConfirmationOrphan(
        expected: com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue,
        ciphertext: ByteArray,
    ): KeyConfirmationDecryption

    fun authenticateManifest(
        runId: RunId,
        publication: RecoveryManifestPublicationRow,
        previousDigest: Sha256Value,
        envelope: ByteArray,
        ciphertext: ByteArray,
    ): ManifestAuthenticationOutcome

    fun authenticateUnit(
        runId: RunId,
        unit: RecoveryMicrofileUnitRow,
        previousDigest: Sha256Value,
        envelope: ByteArray,
        ciphertext: ByteArray,
    ): UnitAuthenticationOutcome
}

internal enum class ReconciliationDiagnostic {
    UNSAFE_PATH,
    INVALID_BOOTSTRAP_ROOT,
    LATER_JOURNAL_PREFIX_INVALID,
    MANIFEST_MISSING_OR_INVALID,
    UNIT_MISSING_OR_INVALID,
    CRYPTO_OPERATIONAL,
}

private val successfulAuthenticatedPrefixProof = Any()

@Suppress("LongParameterList")
internal class AuthenticatedMicrofilePrefixCapability
internal constructor(
    val candidate: RecoveryCandidate,
    val runId: RunId,
    val manifestGenerationUsed: ULong,
    val authenticatedUnitCount: Int,
    val authenticatedEndExclusive: ULong,
    val authenticatedPlaintextSha256: Sha256Value,
    val manifestCiphertextSha256: Sha256Value,
    val orderedAuthenticatedRowsDigest: Sha256Value,
    proof: Any,
) {
    init {
        check(proof === successfulAuthenticatedPrefixProof) {
            "Authenticated prefix capability requires private crypto proof"
        }
    }

    fun authorizes(prefix: AuthenticatedMicrofilePrefix): Boolean =
        prefix.candidate == candidate &&
            prefix.runId == runId &&
            prefix.manifestGenerationUsed == manifestGenerationUsed &&
            prefix.units.size == authenticatedUnitCount &&
            prefix.authenticatedEndExclusive == authenticatedEndExclusive &&
            Sha256Value.calculate(prefix.plaintextSnapshot()) == authenticatedPlaintextSha256 &&
            prefix.manifestCiphertextSha256 == manifestCiphertextSha256 &&
            prefix.orderedRowsDigestOrNull() == orderedAuthenticatedRowsDigest
}

@Suppress("LongParameterList")
internal class AuthenticatedMicrofilePrefix(
    val candidate: RecoveryCandidate,
    val runId: RunId,
    val manifestGenerationUsed: ULong,
    val authenticatedEndExclusive: ULong,
    plaintext: ByteArray,
    units: List<RecoveryMicrofileUnitRow>,
    val manifestCiphertextSha256: Sha256Value,
) {
    private val bytes = plaintext.copyOf()
    val units: List<RecoveryMicrofileUnitRow> = Collections.unmodifiableList(ArrayList(units))

    fun plaintextSnapshot(): ByteArray = bytes.copyOf()

    fun orderedRowsDigestOrNull(): Sha256Value? =
        RecoveryAuthenticatedRowsDigest.calculateOrNull(units)
}

internal sealed interface MicrofileReconciliationResult {
    data class ConcurrentWriter(val runId: RunId) : MicrofileReconciliationResult

    data class NoAuthenticatedPrefix(
        val classification: KeyRecoveryClassification?,
        val confirmationDiagnostic: ConfirmationDiagnostic?,
        val diagnostic: ReconciliationDiagnostic?,
        val quarantine: QuarantineResult? = null,
        val failure: RecoveryFailureDiagnostic? = null,
    ) : MicrofileReconciliationResult

    data class AuthenticatedPrefix(
        val prefix: AuthenticatedMicrofilePrefix,
        val capability: AuthenticatedMicrofilePrefixCapability,
        val quarantineOutcomes: List<QuarantineResult> = emptyList(),
        val inventoryReports: List<RecoveryReportOnlyInventoryEntry> = emptyList(),
    ) : MicrofileReconciliationResult

    data class PartialPrefix(
        val prefix: AuthenticatedMicrofilePrefix,
        val capability: AuthenticatedMicrofilePrefixCapability,
        val classification: KeyRecoveryClassification?,
        val diagnostic: ReconciliationDiagnostic,
        val quarantineOutcomes: List<QuarantineResult> = emptyList(),
        val failure: RecoveryFailureDiagnostic? = null,
        val inventoryReports: List<RecoveryReportOnlyInventoryEntry> = emptyList(),
    ) : MicrofileReconciliationResult
}

internal enum class QuarantineBootstrapBinding {
    ABSENT,
    PRESENT,
}

internal enum class QuarantineIntentState {
    PENDING,
    COMPLETED,
}

internal enum class QuarantinePathState {
    ABSENT,
    EXACT,
    OCCUPIED,
    UNSAFE,
}

internal data class RecoveryQuarantineIntentRow(
    val intentId: Sha256Value,
    val input: RecoveryQuarantineIntentInput,
    val recordedObservedState: RecoveryQuarantineObservedState,
    val bootstrapBinding: QuarantineBootstrapBinding,
    val destinationRelativeName: String,
    val state: QuarantineIntentState,
)

internal data class QuarantinePathObservation(
    val source: QuarantinePathState,
    val destination: QuarantinePathState,
)

internal interface RecoveryQuarantineStorage {
    fun prepare(runId: RunId)

    fun inspect(row: RecoveryQuarantineIntentRow): QuarantinePathObservation

    fun renameNoOverwrite(row: RecoveryQuarantineIntentRow)

    fun fsyncSourceParent(row: RecoveryQuarantineIntentRow)

    fun fsyncDestinationParent(row: RecoveryQuarantineIntentRow)
}

internal interface RecoveryQuarantineJournal {
    fun load(intentId: Sha256Value): RecoveryQuarantineIntentRow?

    fun loadBySource(input: RecoveryQuarantineIntentInput): RecoveryQuarantineIntentRow? = null

    fun loadPending(runId: RunId): List<RecoveryQuarantineIntentRow> = emptyList()

    fun beginNonExclusive(): RecoveryQuarantineTransaction
}

internal interface RecoveryQuarantineTransaction {
    fun insert(row: RecoveryQuarantineIntentRow)

    fun complete(intentId: Sha256Value)

    fun markSuccessful()

    fun end()
}

internal fun interface RecoveryQuarantineEvidenceSink {
    fun emit(row: RecoveryQuarantineIntentRow)
}

internal enum class QuarantineStep {
    PREPARE,
    Q01,
    Q02,
    Q03,
    Q04,
    Q05,
    EVIDENCE,
}

internal enum class QuarantineOperationState {
    NOT_ATTEMPTED,
    OUTCOME_UNKNOWN,
    CONFIRMED,
}

internal data class QuarantineRemainder(
    val intentCommit: QuarantineOperationState = QuarantineOperationState.NOT_ATTEMPTED,
    val rename: QuarantineOperationState = QuarantineOperationState.NOT_ATTEMPTED,
    val sourceParentSync: QuarantineOperationState = QuarantineOperationState.NOT_ATTEMPTED,
    val destinationParentSync: QuarantineOperationState = QuarantineOperationState.NOT_ATTEMPTED,
    val completionCommit: QuarantineOperationState = QuarantineOperationState.NOT_ATTEMPTED,
)

internal sealed interface QuarantineResult {
    data class Completed(
        val row: RecoveryQuarantineIntentRow,
        val evidenceEmitted: Boolean,
        val evidenceFailure: RecoveryFailureDiagnostic?,
        val remainder: QuarantineRemainder,
    ) : QuarantineResult

    data class RetryRequired(
        val failedStep: QuarantineStep,
        val diagnostic: RecoveryFailureDiagnostic?,
        val row: RecoveryQuarantineIntentRow?,
        val remainder: QuarantineRemainder,
    ) : QuarantineResult

    data class Collision(
        val row: RecoveryQuarantineIntentRow,
        val observation: QuarantinePathObservation,
    ) : QuarantineResult

    data class UnsafePath(val row: RecoveryQuarantineIntentRow?, val failedStep: QuarantineStep) :
        QuarantineResult
}

internal class RecoveryQuarantineController(
    private val storage: RecoveryQuarantineStorage,
    private val journal: RecoveryQuarantineJournal,
    private val evidence: RecoveryQuarantineEvidenceSink,
) {
    @Suppress(
        "ReturnCount",
        "TooGenericExceptionCaught",
        "LongMethod",
        "CyclomaticComplexMethod",
        "NestedBlockDepth",
        "ComplexCondition",
    )
    fun quarantine(
        input: RecoveryQuarantineIntentInput,
        observedState: RecoveryQuarantineObservedState,
        bootstrapBinding: QuarantineBootstrapBinding,
    ): QuarantineResult {
        var remainder = QuarantineRemainder()
        try {
            storage.prepare(input.runId)
        } catch (_: Throwable) {
            return QuarantineResult.UnsafePath(null, QuarantineStep.PREPARE)
        }
        val id = RecoveryQuarantineIntent.calculate(input)
        val proposed =
            RecoveryQuarantineIntentRow(
                id,
                input,
                observedState,
                bootstrapBinding,
                RecoveryQuarantineIntent.destination(input),
                QuarantineIntentState.PENDING,
            )
        val initial = readback(id, input)
        if (initial.failure != null) {
            return QuarantineResult.RetryRequired(
                QuarantineStep.Q01,
                initial.failure,
                null,
                remainder,
            )
        }
        var row = initial.row
        if (row == null) {
            val transaction =
                try {
                    journal.beginNonExclusive()
                } catch (error: Throwable) {
                    return QuarantineResult.RetryRequired(
                        QuarantineStep.Q01,
                        RecoveryFailureDiagnostic.capture(
                            RecoveryFailureCategory.OPERATIONAL,
                            error,
                        ),
                        null,
                        remainder,
                    )
                }
            var endAttempted = false
            try {
                transaction.insert(proposed)
                transaction.markSuccessful()
                remainder = remainder.copy(intentCommit = QuarantineOperationState.OUTCOME_UNKNOWN)
                endAttempted = true
                transaction.end()
                remainder = remainder.copy(intentCommit = QuarantineOperationState.CONFIRMED)
                row = proposed
            } catch (error: Throwable) {
                if (!endAttempted)
                    try {
                        transaction.end()
                    } catch (close: Throwable) {
                        error.addSuppressed(close)
                    }
                val recovered = readback(id, input)
                row = recovered.row
                if (row == null || recovered.failure != null)
                    return QuarantineResult.RetryRequired(
                        QuarantineStep.Q01,
                        recovered.failure
                            ?: RecoveryFailureDiagnostic.capture(
                                RecoveryFailureCategory.UNKNOWN_OUTCOME,
                                error,
                                RecoveryFailureStage.JOURNAL,
                            ),
                        row,
                        remainder,
                    )
            }
        }
        val persisted = requireNotNull(row)
        if (!matchesProposed(persisted, proposed)) {
            return QuarantineResult.RetryRequired(QuarantineStep.Q01, null, persisted, remainder)
        }
        var observation =
            try {
                storage.inspect(persisted)
            } catch (error: Throwable) {
                return QuarantineResult.RetryRequired(
                    QuarantineStep.Q02,
                    RecoveryFailureDiagnostic.capture(RecoveryFailureCategory.OPERATIONAL, error),
                    persisted,
                    remainder,
                )
            }
        if (
            observation.source == QuarantinePathState.UNSAFE ||
                observation.destination == QuarantinePathState.UNSAFE
        ) {
            return QuarantineResult.UnsafePath(persisted, QuarantineStep.Q02)
        }
        if (
            observation.source != QuarantinePathState.ABSENT &&
                observation.destination != QuarantinePathState.ABSENT
        ) {
            return QuarantineResult.Collision(persisted, observation)
        }
        if (observation.destination == QuarantinePathState.OCCUPIED) {
            return QuarantineResult.Collision(persisted, observation)
        }
        if (persisted.state == QuarantineIntentState.COMPLETED) {
            if (
                observation !=
                    QuarantinePathObservation(QuarantinePathState.ABSENT, QuarantinePathState.EXACT)
            ) {
                return QuarantineResult.RetryRequired(
                    QuarantineStep.Q02,
                    null,
                    persisted,
                    remainder,
                )
            }
            return emit(
                persisted,
                remainder.copy(completionCommit = QuarantineOperationState.CONFIRMED),
            )
        }
        if (
            observation ==
                QuarantinePathObservation(QuarantinePathState.EXACT, QuarantinePathState.ABSENT)
        ) {
            remainder = remainder.copy(rename = QuarantineOperationState.OUTCOME_UNKNOWN)
            try {
                storage.renameNoOverwrite(persisted)
                remainder = remainder.copy(rename = QuarantineOperationState.CONFIRMED)
            } catch (error: Throwable) {
                return QuarantineResult.RetryRequired(
                    QuarantineStep.Q02,
                    RecoveryFailureDiagnostic.capture(
                        RecoveryFailureCategory.UNKNOWN_OUTCOME,
                        error,
                        RecoveryFailureStage.ARTIFACT_IO,
                    ),
                    persisted,
                    remainder,
                )
            }
            observation =
                try {
                    storage.inspect(persisted)
                } catch (error: Throwable) {
                    return QuarantineResult.RetryRequired(
                        QuarantineStep.Q02,
                        RecoveryFailureDiagnostic.capture(
                            RecoveryFailureCategory.UNKNOWN_OUTCOME,
                            error,
                        ),
                        persisted,
                        remainder,
                    )
                }
        }
        if (
            observation !=
                QuarantinePathObservation(QuarantinePathState.ABSENT, QuarantinePathState.EXACT)
        ) {
            return QuarantineResult.RetryRequired(QuarantineStep.Q02, null, persisted, remainder)
        }
        try {
            storage.fsyncSourceParent(persisted)
            remainder = remainder.copy(sourceParentSync = QuarantineOperationState.CONFIRMED)
        } catch (error: Throwable) {
            return QuarantineResult.RetryRequired(
                QuarantineStep.Q03,
                RecoveryFailureDiagnostic.capture(RecoveryFailureCategory.UNKNOWN_OUTCOME, error),
                persisted,
                remainder.copy(sourceParentSync = QuarantineOperationState.OUTCOME_UNKNOWN),
            )
        }
        try {
            storage.fsyncDestinationParent(persisted)
            remainder = remainder.copy(destinationParentSync = QuarantineOperationState.CONFIRMED)
        } catch (error: Throwable) {
            return QuarantineResult.RetryRequired(
                QuarantineStep.Q04,
                RecoveryFailureDiagnostic.capture(RecoveryFailureCategory.UNKNOWN_OUTCOME, error),
                persisted,
                remainder.copy(destinationParentSync = QuarantineOperationState.OUTCOME_UNKNOWN),
            )
        }
        val completion =
            try {
                journal.beginNonExclusive()
            } catch (error: Throwable) {
                return QuarantineResult.RetryRequired(
                    QuarantineStep.Q05,
                    RecoveryFailureDiagnostic.capture(RecoveryFailureCategory.OPERATIONAL, error),
                    persisted,
                    remainder,
                )
            }
        var completionEndAttempted = false
        try {
            completion.complete(id)
            completion.markSuccessful()
            remainder = remainder.copy(completionCommit = QuarantineOperationState.OUTCOME_UNKNOWN)
            completionEndAttempted = true
            completion.end()
            remainder = remainder.copy(completionCommit = QuarantineOperationState.CONFIRMED)
        } catch (error: Throwable) {
            if (!completionEndAttempted)
                try {
                    completion.end()
                } catch (close: Throwable) {
                    error.addSuppressed(close)
                }
            val readback = readback(id, input)
            val loaded = readback.row
            if (
                readback.failure != null ||
                    loaded?.state != QuarantineIntentState.COMPLETED ||
                    !matchesProposed(loaded, proposed)
            ) {
                return QuarantineResult.RetryRequired(
                    QuarantineStep.Q05,
                    readback.failure
                        ?: RecoveryFailureDiagnostic.capture(
                            RecoveryFailureCategory.UNKNOWN_OUTCOME,
                            error,
                            RecoveryFailureStage.JOURNAL,
                        ),
                    loaded,
                    remainder,
                )
            }
            remainder = remainder.copy(completionCommit = QuarantineOperationState.CONFIRMED)
        }
        val finalReadback = readback(id, input)
        val completed = finalReadback.row
        if (
            finalReadback.failure != null ||
                completed?.state != QuarantineIntentState.COMPLETED ||
                !matchesProposed(completed, proposed)
        ) {
            return QuarantineResult.RetryRequired(
                QuarantineStep.Q05,
                finalReadback.failure,
                completed,
                remainder.copy(completionCommit = QuarantineOperationState.OUTCOME_UNKNOWN),
            )
        }
        return emit(completed, remainder)
    }

    private fun validPersistedRow(row: RecoveryQuarantineIntentRow): Boolean =
        row.intentId == RecoveryQuarantineIntent.calculate(row.input) &&
            row.destinationRelativeName == RecoveryQuarantineIntent.destination(row.input)

    private fun matchesProposed(
        row: RecoveryQuarantineIntentRow,
        proposed: RecoveryQuarantineIntentRow,
    ): Boolean =
        validPersistedRow(row) &&
            row.intentId == proposed.intentId &&
            row.input == proposed.input &&
            row.recordedObservedState == proposed.recordedObservedState &&
            row.bootstrapBinding == proposed.bootstrapBinding &&
            row.destinationRelativeName == proposed.destinationRelativeName

    private data class QuarantineReadback(
        val row: RecoveryQuarantineIntentRow?,
        val failure: RecoveryFailureDiagnostic?,
    )

    @Suppress("TooGenericExceptionCaught")
    private fun readback(
        id: Sha256Value,
        input: RecoveryQuarantineIntentInput,
    ): QuarantineReadback =
        try {
            val exact = journal.load(id)
            val bySource = journal.loadBySource(input)
            if (exact != null && bySource != null && exact != bySource) {
                QuarantineReadback(
                    bySource,
                    RecoveryFailureDiagnostic(
                        RecoveryFailureCategory.STRUCTURAL,
                        "QuarantineRowConflict",
                        "Exact and unique-source quarantine rows disagree",
                        RecoveryFailureStage.JOURNAL,
                    ),
                )
            } else QuarantineReadback(exact ?: bySource, null)
        } catch (error: Throwable) {
            QuarantineReadback(
                null,
                RecoveryFailureDiagnostic.capture(
                    RecoveryFailureCategory.UNKNOWN_OUTCOME,
                    error,
                    RecoveryFailureStage.JOURNAL,
                ),
            )
        }

    @Suppress("TooGenericExceptionCaught")
    private fun emit(
        row: RecoveryQuarantineIntentRow,
        remainder: QuarantineRemainder,
    ): QuarantineResult.Completed =
        try {
            evidence.emit(row)
            QuarantineResult.Completed(row, true, null, remainder)
        } catch (error: Throwable) {
            QuarantineResult.Completed(
                row,
                false,
                RecoveryFailureDiagnostic.capture(RecoveryFailureCategory.OPERATIONAL, error),
                remainder,
            )
        }
}

internal class RecoveryMicrofileReconciliationController(
    private val source: RecoveryReconciliationSource,
    private val crypto: RecoveryReconciliationCrypto,
    private val confirmationController: RecoveryKeyConfirmationController =
        RecoveryKeyConfirmationController(),
    private val quarantineController: RecoveryQuarantineController? = null,
    private val writerGuard: RecoveryRunSingleWriterGuard = ProcessRecoveryRunSingleWriterGuard,
) {
    fun reconcile(runId: RunId): MicrofileReconciliationResult {
        val lease =
            writerGuard.tryAcquire(runId)
                ?: return MicrofileReconciliationResult.ConcurrentWriter(runId)
        return lease.use { reconcileExclusive(runId) }
    }

    @Suppress(
        "ReturnCount",
        "TooGenericExceptionCaught",
        "LongMethod",
        "CyclomaticComplexMethod",
        "NestedBlockDepth",
        "LoopWithTooManyJumpStatements",
        "ComplexCondition",
        "SwallowedException",
    )
    private fun reconcileExclusive(runId: RunId): MicrofileReconciliationResult {
        val confirmationSnapshot =
            try {
                source.loadConfirmation(runId)
            } catch (error: RecoverySourceAccessException) {
                return noPrefix(
                    diagnostic =
                        if (error.diagnostic.category == RecoveryFailureCategory.UNSAFE_PARENT)
                            ReconciliationDiagnostic.UNSAFE_PATH
                        else ReconciliationDiagnostic.CRYPTO_OPERATIONAL,
                    failure = error.diagnostic,
                )
            }
        when (val confirmation = confirmationController.evaluate(confirmationSnapshot)) {
            is ConfirmationResult.Absent -> return noPrefix()
            is ConfirmationResult.Rejected -> {
                if (
                    confirmation.classification ==
                        KeyRecoveryClassification.INCOMPLETE_KEY_BOOTSTRAP &&
                        confirmationSnapshot.durableRow == null &&
                        confirmationSnapshot.finalArtifact != null
                ) {
                    val artifact = confirmationSnapshot.finalArtifact
                    if (
                        !artifact.path.containedBeneathRunRoot ||
                            !artifact.path.everyExistingComponentLstatObserved
                    ) {
                        return noPrefix(
                            classification = confirmation.classification,
                            diagnostic = ReconciliationDiagnostic.UNSAFE_PATH,
                        )
                    }
                    if (
                        !artifact.path.leafIsRegularFile || !artifact.path.noComponentOrLeafSymlink
                    ) {
                        return noPrefix(
                            classification = KeyRecoveryClassification.CORRUPT_KEY_CONFIRMATION
                        )
                    }
                    val bytes = artifact.ciphertextSnapshot()
                    if (bytes.isEmpty() || bytes.size > MAX_CONFIRMATION_CIPHERTEXT_BYTES) {
                        return noPrefix(classification = confirmation.classification)
                    }
                    val authenticated =
                        try {
                            crypto.authenticateConfirmationOrphan(
                                confirmationSnapshot.expected,
                                bytes,
                            )
                        } catch (error: RecoveryConfirmationAuthenticationException) {
                            return noPrefix(
                                classification =
                                    if (error.diagnostic.stage == RecoveryFailureStage.ALIAS_OPEN)
                                        KeyRecoveryClassification.KEY_UNAVAILABLE
                                    else confirmation.classification,
                                diagnostic = ReconciliationDiagnostic.CRYPTO_OPERATIONAL,
                                failure = error.diagnostic,
                            )
                        } catch (error: Throwable) {
                            return noPrefix(
                                classification = confirmation.classification,
                                diagnostic = ReconciliationDiagnostic.CRYPTO_OPERATIONAL,
                                failure =
                                    RecoveryFailureDiagnostic.capture(
                                        RecoveryFailureCategory.OPERATIONAL,
                                        error,
                                        RecoveryFailureStage.OPERATIONAL,
                                    ),
                            )
                        }
                    val quarantine =
                        if (
                            authenticated is KeyConfirmationDecryption.Success &&
                                authenticated.value == confirmationSnapshot.expected
                        ) {
                            quarantineController?.quarantine(
                                RecoveryQuarantineIntentInput(
                                    RecoveryCandidate.MICROFILE,
                                    runId,
                                    artifact.relativeName,
                                    RecoveryQuarantineArtifactRole.KEY_CONFIRMATION,
                                    bytes.size.toULong(),
                                    Sha256Value.calculate(bytes),
                                ),
                                RecoveryQuarantineObservedState.FINAL_ORPHAN,
                                QuarantineBootstrapBinding.ABSENT,
                            )
                        } else null
                    return noPrefix(
                        classification = confirmation.classification,
                        quarantine = quarantine,
                    )
                }
                return noPrefix(classification = confirmation.classification)
            }
            is ConfirmationResult.Unclassified ->
                return noPrefix(confirmationDiagnostic = confirmation.diagnostic)
            is ConfirmationResult.Validated -> Unit
        }
        val quarantineOutcomes = mutableListOf<QuarantineResult>()
        val pendingRows =
            try {
                source.loadPendingQuarantine(runId)
            } catch (error: RecoverySourceAccessException) {
                return noPrefix(
                    diagnostic = ReconciliationDiagnostic.LATER_JOURNAL_PREFIX_INVALID,
                    failure = error.diagnostic,
                )
            }
        pendingRows.forEach { pending ->
            quarantineController
                ?.quarantine(
                    pending.input,
                    pending.recordedObservedState,
                    pending.bootstrapBinding,
                )
                ?.let(quarantineOutcomes::add)
        }
        val candidate =
            try {
                source.loadCandidate(runId)
            } catch (error: RecoverySourceAccessException) {
                return noPrefix(
                    diagnostic = ReconciliationDiagnostic.LATER_JOURNAL_PREFIX_INVALID,
                    failure = error.diagnostic,
                )
            }
        val referenced = buildSet {
            add("key-confirmation/run.kc")
            candidate.units.forEach {
                add(it.ciphertextRelativeName)
                add(it.keyEnvelopeRelativeName)
            }
            candidate.publications.forEach {
                add(it.publicationRelativeName)
                add(it.keyEnvelopeRelativeName)
            }
        }
        val inventory =
            try {
                source.loadInventorySnapshot(runId, candidate)
            } catch (error: RecoverySourceAccessException) {
                return noPrefix(
                    diagnostic =
                        if (error.diagnostic.category == RecoveryFailureCategory.UNSAFE_PARENT)
                            ReconciliationDiagnostic.UNSAFE_PATH
                        else ReconciliationDiagnostic.LATER_JOURNAL_PREFIX_INVALID,
                    failure = error.diagnostic,
                )
            }
        val activeNames = inventory.active.map { it.input.sourceRelativeName }.toSet()
        inventory.active
            .filter {
                it.input.sourceRelativeName.endsWith(".tmp") ||
                    it.input.sourceRelativeName !in referenced
            }
            .forEach { original ->
                val name = original.input.sourceRelativeName
                val entry =
                    original.copy(
                        observedState =
                            when {
                                name.endsWith(".tmp") && name in referenced ->
                                    RecoveryQuarantineObservedState.SQLITE_POINTS_TO_TEMP
                                name.endsWith(".tmp") && name.removeSuffix(".tmp") in activeNames ->
                                    RecoveryQuarantineObservedState.TEMP_AND_FINAL
                                !name.endsWith(".tmp") && "$name.tmp" in activeNames ->
                                    RecoveryQuarantineObservedState.TEMP_AND_FINAL
                                original.input.artifactRole ==
                                    RecoveryQuarantineArtifactRole.UNKNOWN_REGULAR ->
                                    RecoveryQuarantineObservedState.UNKNOWN_OR_NON_ALLOWLISTED_NAME
                                else -> original.observedState
                            }
                    )
                quarantineController
                    ?.quarantine(
                        entry.input,
                        entry.observedState,
                        entry.bootstrapBinding,
                    )
                    ?.let(quarantineOutcomes::add)
            }
        val canonicalRun = runId.toCanonicalString()
        if (
            candidate.bootstrapRows.singleOrNull() !=
                CandidateBootstrapRow(
                    canonicalRun,
                    RecoveryCandidate.MICROFILE.contractId,
                    com.monumentogram.dora.poc.recovery.bootstrap.KeyConfirmationState.VALID,
                )
        ) {
            return noPrefix(diagnostic = ReconciliationDiagnostic.INVALID_BOOTSTRAP_ROOT)
        }
        val validUnits = maximalValidUnits(candidate.units, canonicalRun)
        val laterRowsInvalid = validUnits.size != candidate.units.size
        val publications = candidate.publications.associateBy { it.generation }
        val latestGeneration = candidate.publications.maxOfOrNull { it.generation } ?: 0UL
        val publicationIssue =
            publications.size != candidate.publications.size ||
                latestGeneration > validUnits.size.toULong()
        var selected: Pair<RecoveryManifestPublicationRow, RecoveryManifest>? = null
        var manifestFailure: RecoveryFailureDiagnostic? = null
        var generation = minOf(validUnits.size, RecoveryContract.MAX_MANIFEST_ENTRIES).toULong()
        while (generation > 0UL && selected == null) {
            val row = publications[generation]
            if (
                row != null &&
                    row.committedEndExclusive ==
                        validUnits[generation.toInt() - 1].plaintextEndExclusive
            ) {
                val expectedPrevious =
                    if (generation == 1UL) Sha256Value.ZERO
                    else publications[generation - 1UL]?.publicationSha256
                if (
                    expectedPrevious == null ||
                        row.previousPublicationCiphertextSha256 != expectedPrevious
                ) {
                    manifestFailure =
                        RecoveryFailureDiagnostic(
                            RecoveryFailureCategory.STRUCTURAL,
                            "PublicationDigestChainMismatch",
                            "Manifest publication does not bind the actual prior publication",
                            RecoveryFailureStage.MANIFEST_SEMANTICS,
                        )
                } else {
                    val attempt =
                        authenticateManifest(runId, row, validUnits.take(generation.toInt()))
                    selected = attempt.value
                    if (attempt.failure != null) manifestFailure = attempt.failure
                }
            }
            generation--
        }
        val (publication, manifest) =
            selected
                ?: return noPrefix(
                    diagnostic = ReconciliationDiagnostic.MANIFEST_MISSING_OR_INVALID,
                    failure = manifestFailure,
                )
        val fallbackIssue = publicationIssue || publication.generation != latestGeneration
        val output = ByteArrayOutputStream()
        val authenticated = mutableListOf<RecoveryMicrofileUnitRow>()
        var failure: Pair<KeyRecoveryClassification?, ReconciliationDiagnostic>? = null
        var failureDetail: RecoveryFailureDiagnostic? = manifestFailure.takeIf { fallbackIssue }
        for (unit in validUnits.take(manifest.entries.size)) {
            val loaded =
                try {
                    source.loadArtifact(runId, unit.keyEnvelopeRelativeName) to
                        source.loadArtifact(runId, unit.ciphertextRelativeName)
                } catch (error: RecoverySourceAccessException) {
                    failure = null to ReconciliationDiagnostic.CRYPTO_OPERATIONAL
                    failureDetail = error.diagnostic
                    break
                }
            val (envelope, ciphertext) = loaded
            if (envelope == null) {
                failure =
                    KeyRecoveryClassification.KEY_UNAVAILABLE to
                        ReconciliationDiagnostic.UNIT_MISSING_OR_INVALID
                failureDetail =
                    RecoveryFailureDiagnostic(
                        RecoveryFailureCategory.MISSING_ARTIFACT,
                        "MissingUnitKeyEnvelope",
                        "Mandatory unit key envelope is absent",
                        RecoveryFailureStage.ARTIFACT_PATH,
                    )
                break
            }
            if (!matches(envelope, unit.keyEnvelopeBytes, unit.keyEnvelopeSha256)) {
                failure =
                    KeyRecoveryClassification.CORRUPT_KEY_ENVELOPE to
                        ReconciliationDiagnostic.UNIT_MISSING_OR_INVALID
                failureDetail =
                    RecoveryFailureDiagnostic(
                        RecoveryFailureCategory.STRUCTURAL,
                        "UnitKeyEnvelopeIdentityMismatch",
                        "Unit key envelope does not match its journal identity",
                        RecoveryFailureStage.ENVELOPE_BINDING,
                    )
                break
            }
            if (!matches(ciphertext, unit.ciphertextBytes, unit.ciphertextSha256)) {
                failure = null to ReconciliationDiagnostic.UNIT_MISSING_OR_INVALID
                failureDetail =
                    RecoveryFailureDiagnostic(
                        if (ciphertext == null) RecoveryFailureCategory.MISSING_ARTIFACT
                        else RecoveryFailureCategory.STRUCTURAL,
                        "UnitCiphertextIdentityMismatch",
                        "Unit ciphertext is absent or does not match its journal identity",
                        RecoveryFailureStage.ARTIFACT_PATH,
                    )
                break
            }
            try {
                val previous =
                    if (unit.manifestGeneration == 1UL) Sha256Value.ZERO
                    else
                        publications[unit.manifestGeneration - 1UL]?.publicationSha256
                            ?: run {
                                failure =
                                    null to ReconciliationDiagnostic.LATER_JOURNAL_PREFIX_INVALID
                                break
                            }
                val outcome =
                    crypto.authenticateUnit(
                        runId,
                        unit,
                        previous,
                        envelope.snapshot(),
                        ciphertext!!.snapshot(),
                    )
                val plaintext =
                    when (outcome) {
                        is UnitAuthenticationOutcome.Authenticated -> outcome.snapshot()
                        is UnitAuthenticationOutcome.Rejected -> {
                            failureDetail = outcome.diagnostic
                            failure =
                                when (outcome.diagnostic.stage) {
                                    RecoveryFailureStage.ALIAS_OPEN ->
                                        KeyRecoveryClassification.KEY_UNAVAILABLE to
                                            ReconciliationDiagnostic.CRYPTO_OPERATIONAL
                                    RecoveryFailureStage.ENVELOPE_PARSE ->
                                        when (outcome.diagnostic.category) {
                                            RecoveryFailureCategory.AUTHENTICATION_REJECTED ->
                                                KeyRecoveryClassification
                                                    .KEY_ENVELOPE_AUTH_FAILURE to
                                                    ReconciliationDiagnostic.UNIT_MISSING_OR_INVALID
                                            RecoveryFailureCategory.STRUCTURAL ->
                                                KeyRecoveryClassification.CORRUPT_KEY_ENVELOPE to
                                                    ReconciliationDiagnostic.UNIT_MISSING_OR_INVALID
                                            else ->
                                                null to ReconciliationDiagnostic.CRYPTO_OPERATIONAL
                                        }
                                    RecoveryFailureStage.UNIT_PAYLOAD_DECRYPT ->
                                        null to ReconciliationDiagnostic.UNIT_MISSING_OR_INVALID
                                    else -> null to ReconciliationDiagnostic.CRYPTO_OPERATIONAL
                                }
                            break
                        }
                    }
                if (
                    plaintext.size.toULong() !=
                        unit.plaintextEndExclusive - unit.plaintextStartInclusive
                ) {
                    failure = null to ReconciliationDiagnostic.UNIT_MISSING_OR_INVALID
                    failureDetail =
                        RecoveryFailureDiagnostic(
                            RecoveryFailureCategory.STRUCTURAL,
                            "UnitPlaintextLengthMismatch",
                            "Authenticated unit plaintext length does not match its journal row",
                            RecoveryFailureStage.UNIT_PLAINTEXT,
                        )
                    break
                }
                output.write(plaintext)
                authenticated += unit
            } catch (error: Throwable) {
                failure = null to ReconciliationDiagnostic.CRYPTO_OPERATIONAL
                failureDetail =
                    RecoveryFailureDiagnostic.capture(
                        RecoveryFailureCategory.OPERATIONAL,
                        error,
                        RecoveryFailureStage.OPERATIONAL,
                    )
                break
            }
        }
        val prefix = authenticatedPrefix(runId, publication, output.toByteArray(), authenticated)
        return if (
            failure == null &&
                !laterRowsInvalid &&
                !fallbackIssue &&
                authenticated.size == manifest.entries.size
        ) {
            MicrofileReconciliationResult.AuthenticatedPrefix(
                prefix.first,
                prefix.second,
                Collections.unmodifiableList(ArrayList(quarantineOutcomes)),
                inventory.quarantine,
            )
        } else {
            MicrofileReconciliationResult.PartialPrefix(
                prefix.first,
                prefix.second,
                failure?.first,
                failure?.second ?: ReconciliationDiagnostic.LATER_JOURNAL_PREFIX_INVALID,
                Collections.unmodifiableList(ArrayList(quarantineOutcomes)),
                failure = failureDetail,
                inventoryReports = inventory.quarantine,
            )
        }
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught", "SwallowedException", "ComplexCondition")
    private data class ManifestAttempt(
        val value: Pair<RecoveryManifestPublicationRow, RecoveryManifest>?,
        val failure: RecoveryFailureDiagnostic?,
    )

    @Suppress("ComplexCondition", "LongMethod", "ReturnCount", "TooGenericExceptionCaught")
    private fun authenticateManifest(
        runId: RunId,
        row: RecoveryManifestPublicationRow,
        units: List<RecoveryMicrofileUnitRow>,
    ): ManifestAttempt {
        val loaded =
            try {
                source.loadArtifact(runId, row.keyEnvelopeRelativeName) to
                    source.loadArtifact(runId, row.publicationRelativeName)
            } catch (error: RecoverySourceAccessException) {
                return ManifestAttempt(null, error.diagnostic)
            }
        val (envelope, ciphertext) = loaded
        if (
            !matches(envelope, row.keyEnvelopeBytes, row.keyEnvelopeSha256) ||
                !matches(ciphertext, row.publicationBytes, row.publicationSha256)
        )
            return ManifestAttempt(
                null,
                RecoveryFailureDiagnostic(
                    RecoveryFailureCategory.MISSING_ARTIFACT,
                    "ManifestArtifactIdentityMismatch",
                    "Manifest artifact is missing or has the wrong durable identity",
                    RecoveryFailureStage.ARTIFACT_IO,
                ),
            )
        return try {
            val outcome =
                crypto.authenticateManifest(
                    runId,
                    row,
                    row.previousPublicationCiphertextSha256,
                    envelope!!.snapshot(),
                    ciphertext!!.snapshot(),
                )
            val manifest =
                (outcome as? ManifestAuthenticationOutcome.Authenticated)?.manifest
                    ?: return ManifestAttempt(
                        null,
                        (outcome as ManifestAuthenticationOutcome.Rejected).diagnostic,
                    )
            if (
                manifest.candidate != RecoveryCandidate.MICROFILE ||
                    manifest.runId != runId ||
                    manifest.generation != row.generation ||
                    manifest.previousManifestCiphertextSha256 !=
                        row.previousPublicationCiphertextSha256 ||
                    manifest.committedEndExclusive != row.committedEndExclusive ||
                    manifest.entries != units.map(::manifestEntry)
            )
                ManifestAttempt(
                    null,
                    RecoveryFailureDiagnostic(
                        RecoveryFailureCategory.STRUCTURAL,
                        "ManifestIdentityMismatch",
                        "Authenticated manifest identity does not match its journal row",
                        RecoveryFailureStage.MANIFEST_SEMANTICS,
                    ),
                )
            else ManifestAttempt(row to manifest, null)
        } catch (error: Throwable) {
            ManifestAttempt(
                null,
                RecoveryFailureDiagnostic.capture(
                    RecoveryFailureCategory.OPERATIONAL,
                    error,
                    RecoveryFailureStage.OPERATIONAL,
                ),
            )
        }
    }

    @Suppress("ComplexCondition", "LoopWithTooManyJumpStatements")
    private fun maximalValidUnits(
        rows: List<RecoveryMicrofileUnitRow>,
        runId: String,
    ): List<RecoveryMicrofileUnitRow> {
        val result = mutableListOf<RecoveryMicrofileUnitRow>()
        var start = 0UL
        for (row in rows.sortedBy { it.unitIndex }) {
            if (result.size == RecoveryContract.MAX_MANIFEST_ENTRIES) break
            val width = row.plaintextEndExclusive - row.plaintextStartInclusive
            if (
                row.runId != runId ||
                    row.candidateId != RecoveryCandidate.MICROFILE.contractId ||
                    row.unitIndex != result.size.toULong() ||
                    row.plaintextStartInclusive != start ||
                    row.manifestGeneration != row.unitIndex + 1UL ||
                    row.state != "VALID" ||
                    row.cadenceSeconds !in setOf(5UL, 15UL, 30UL) ||
                    width > row.cadenceSeconds * 32_000UL
            )
                break
            result += row
            start = row.plaintextEndExclusive
        }
        return result
    }

    private fun authenticatedPrefix(
        runId: RunId,
        publication: RecoveryManifestPublicationRow,
        plaintext: ByteArray,
        units: List<RecoveryMicrofileUnitRow>,
    ): Pair<AuthenticatedMicrofilePrefix, AuthenticatedMicrofilePrefixCapability> {
        val end = units.lastOrNull()?.plaintextEndExclusive ?: 0UL
        val prefix =
            AuthenticatedMicrofilePrefix(
                RecoveryCandidate.MICROFILE,
                runId,
                publication.generation,
                end,
                plaintext,
                units,
                publication.publicationSha256,
            )
        val rowsDigest = requireNotNull(RecoveryAuthenticatedRowsDigest.calculateOrNull(units))
        val capability =
            AuthenticatedMicrofilePrefixCapability(
                RecoveryCandidate.MICROFILE,
                runId,
                publication.generation,
                units.size,
                end,
                Sha256Value.calculate(plaintext),
                publication.publicationSha256,
                rowsDigest,
                successfulAuthenticatedPrefixProof,
            )
        return prefix to capability
    }

    private fun matches(value: RecoveryArtifactBytes?, bytes: Long, digest: Sha256Value): Boolean =
        value != null && value.size == bytes && value.sha256 == digest

    private fun manifestEntry(row: RecoveryMicrofileUnitRow) =
        RecoveryManifestEntry(
            row.unitIndex,
            row.plaintextStartInclusive,
            row.plaintextEndExclusive,
            row.cadenceSeconds,
            row.ciphertextBytes.toULong(),
            row.ciphertextSha256,
            row.keyEnvelopeBytes.toULong(),
            row.keyEnvelopeSha256,
            row.ciphertextRelativeName,
            row.keyEnvelopeRelativeName,
        )

    private fun noPrefix(
        classification: KeyRecoveryClassification? = null,
        confirmationDiagnostic: ConfirmationDiagnostic? = null,
        diagnostic: ReconciliationDiagnostic? = null,
        quarantine: QuarantineResult? = null,
        failure: RecoveryFailureDiagnostic? = null,
    ) =
        MicrofileReconciliationResult.NoAuthenticatedPrefix(
            classification,
            confirmationDiagnostic,
            diagnostic,
            quarantine,
            failure,
        )

    private companion object {
        const val MAX_CONFIRMATION_CIPHERTEXT_BYTES = 512
    }
}
