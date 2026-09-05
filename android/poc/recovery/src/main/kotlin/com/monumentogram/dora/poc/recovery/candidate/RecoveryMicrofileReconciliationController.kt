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
}

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
    ): RecoveryManifest

    fun authenticateUnit(
        runId: RunId,
        unit: RecoveryMicrofileUnitRow,
        previousDigest: Sha256Value,
        envelope: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray
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
        prefix.runId == runId &&
            prefix.manifestGenerationUsed == manifestGenerationUsed &&
            prefix.units.size == authenticatedUnitCount &&
            prefix.authenticatedEndExclusive == authenticatedEndExclusive &&
            Sha256Value.calculate(prefix.plaintextSnapshot()) == authenticatedPlaintextSha256
}

internal class AuthenticatedMicrofilePrefix(
    val runId: RunId,
    val manifestGenerationUsed: ULong,
    val authenticatedEndExclusive: ULong,
    plaintext: ByteArray,
    units: List<RecoveryMicrofileUnitRow>,
) {
    private val bytes = plaintext.copyOf()
    val units: List<RecoveryMicrofileUnitRow> = Collections.unmodifiableList(ArrayList(units))

    fun plaintextSnapshot(): ByteArray = bytes.copyOf()
}

internal sealed interface MicrofileReconciliationResult {
    data class ConcurrentWriter(val runId: RunId) : MicrofileReconciliationResult

    data class NoAuthenticatedPrefix(
        val classification: KeyRecoveryClassification?,
        val confirmationDiagnostic: ConfirmationDiagnostic?,
        val diagnostic: ReconciliationDiagnostic?,
        val quarantine: QuarantineResult? = null,
    ) : MicrofileReconciliationResult

    data class AuthenticatedPrefix(
        val prefix: AuthenticatedMicrofilePrefix,
        val capability: AuthenticatedMicrofilePrefixCapability,
    ) : MicrofileReconciliationResult

    data class PartialPrefix(
        val prefix: AuthenticatedMicrofilePrefix,
        val capability: AuthenticatedMicrofilePrefixCapability,
        val classification: KeyRecoveryClassification?,
        val diagnostic: ReconciliationDiagnostic,
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
    val sourceParentSync: Boolean = false,
    val destinationParentSync: Boolean = false,
    val completionCommit: QuarantineOperationState = QuarantineOperationState.NOT_ATTEMPTED,
)

internal sealed interface QuarantineResult {
    data class Completed(
        val row: RecoveryQuarantineIntentRow,
        val evidenceEmitted: Boolean,
        val evidenceFailure: Throwable?,
        val remainder: QuarantineRemainder,
    ) : QuarantineResult

    data class RetryRequired(
        val failedStep: QuarantineStep,
        val cause: Throwable?,
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
        var row = journal.load(id)
        if (row == null) {
            val transaction = journal.beginNonExclusive()
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
                row = journal.load(id)
                if (row == null)
                    return QuarantineResult.RetryRequired(
                        QuarantineStep.Q01,
                        error,
                        null,
                        remainder,
                    )
            }
        }
        val persisted = requireNotNull(row)
        if (
            !validPersistedRow(persisted) ||
                persisted.input != proposed.input ||
                persisted.bootstrapBinding != proposed.bootstrapBinding
        ) {
            return QuarantineResult.RetryRequired(QuarantineStep.Q01, null, persisted, remainder)
        }
        var observation = storage.inspect(persisted)
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
                    error,
                    persisted,
                    remainder,
                )
            }
            observation = storage.inspect(persisted)
        }
        if (
            observation !=
                QuarantinePathObservation(QuarantinePathState.ABSENT, QuarantinePathState.EXACT)
        ) {
            return QuarantineResult.RetryRequired(QuarantineStep.Q02, null, persisted, remainder)
        }
        try {
            storage.fsyncSourceParent(persisted)
            remainder = remainder.copy(sourceParentSync = true)
        } catch (error: Throwable) {
            return QuarantineResult.RetryRequired(QuarantineStep.Q03, error, persisted, remainder)
        }
        try {
            storage.fsyncDestinationParent(persisted)
            remainder = remainder.copy(destinationParentSync = true)
        } catch (error: Throwable) {
            return QuarantineResult.RetryRequired(QuarantineStep.Q04, error, persisted, remainder)
        }
        val completion = journal.beginNonExclusive()
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
            val loaded = journal.load(id)
            if (
                loaded?.state != QuarantineIntentState.COMPLETED ||
                    !validPersistedRow(loaded) ||
                    loaded.input != proposed.input ||
                    loaded.bootstrapBinding != proposed.bootstrapBinding
            ) {
                return QuarantineResult.RetryRequired(QuarantineStep.Q05, error, loaded, remainder)
            }
            remainder = remainder.copy(completionCommit = QuarantineOperationState.CONFIRMED)
        }
        val completed = journal.load(id) ?: persisted.copy(state = QuarantineIntentState.COMPLETED)
        return emit(completed, remainder)
    }

    private fun validPersistedRow(row: RecoveryQuarantineIntentRow): Boolean =
        row.intentId == RecoveryQuarantineIntent.calculate(row.input) &&
            row.destinationRelativeName == RecoveryQuarantineIntent.destination(row.input)

    @Suppress("TooGenericExceptionCaught")
    private fun emit(
        row: RecoveryQuarantineIntentRow,
        remainder: QuarantineRemainder,
    ): QuarantineResult.Completed =
        try {
            evidence.emit(row)
            QuarantineResult.Completed(row, true, null, remainder)
        } catch (error: Throwable) {
            QuarantineResult.Completed(row, false, error, remainder)
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
    )
    private fun reconcileExclusive(runId: RunId): MicrofileReconciliationResult {
        val confirmationSnapshot = source.loadConfirmation(runId)
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
                    val bytes = artifact.ciphertextSnapshot()
                    val authenticated =
                        crypto.authenticateConfirmationOrphan(confirmationSnapshot.expected, bytes)
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
        val candidate = source.loadCandidate(runId)
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
        var selected: Pair<RecoveryManifestPublicationRow, RecoveryManifest>? = null
        var generation = minOf(validUnits.size, RecoveryContract.MAX_MANIFEST_ENTRIES).toULong()
        while (generation > 0UL && selected == null) {
            val row = publications[generation]
            if (
                row != null &&
                    row.committedEndExclusive ==
                        validUnits[generation.toInt() - 1].plaintextEndExclusive
            ) {
                selected =
                    authenticateManifestOrNull(runId, row, validUnits.take(generation.toInt()))
            }
            generation--
        }
        val (publication, manifest) =
            selected
                ?: return noPrefix(
                    diagnostic = ReconciliationDiagnostic.MANIFEST_MISSING_OR_INVALID
                )
        val output = ByteArrayOutputStream()
        val authenticated = mutableListOf<RecoveryMicrofileUnitRow>()
        var failure: Pair<KeyRecoveryClassification?, ReconciliationDiagnostic>? = null
        for (unit in validUnits.take(manifest.entries.size)) {
            val envelope = source.loadArtifact(runId, unit.keyEnvelopeRelativeName)
            val ciphertext = source.loadArtifact(runId, unit.ciphertextRelativeName)
            if (
                !matches(envelope, unit.keyEnvelopeBytes, unit.keyEnvelopeSha256) ||
                    !matches(ciphertext, unit.ciphertextBytes, unit.ciphertextSha256)
            ) {
                failure =
                    KeyRecoveryClassification.CORRUPT_KEY_ENVELOPE to
                        ReconciliationDiagnostic.UNIT_MISSING_OR_INVALID
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
                val plaintext =
                    crypto.authenticateUnit(
                        runId,
                        unit,
                        previous,
                        envelope!!.snapshot(),
                        ciphertext!!.snapshot(),
                    )
                if (
                    plaintext.size.toULong() !=
                        unit.plaintextEndExclusive - unit.plaintextStartInclusive
                ) {
                    failure = null to ReconciliationDiagnostic.UNIT_MISSING_OR_INVALID
                    break
                }
                output.write(plaintext)
                authenticated += unit
            } catch (_: Throwable) {
                failure =
                    KeyRecoveryClassification.KEY_ENVELOPE_AUTH_FAILURE to
                        ReconciliationDiagnostic.UNIT_MISSING_OR_INVALID
                break
            }
        }
        val prefix = authenticatedPrefix(runId, publication, output.toByteArray(), authenticated)
        return if (
            failure == null && !laterRowsInvalid && authenticated.size == manifest.entries.size
        ) {
            MicrofileReconciliationResult.AuthenticatedPrefix(prefix.first, prefix.second)
        } else {
            MicrofileReconciliationResult.PartialPrefix(
                prefix.first,
                prefix.second,
                failure?.first,
                failure?.second ?: ReconciliationDiagnostic.LATER_JOURNAL_PREFIX_INVALID,
            )
        }
    }

    private fun authenticateManifestOrNull(
        runId: RunId,
        row: RecoveryManifestPublicationRow,
        units: List<RecoveryMicrofileUnitRow>,
    ): Pair<RecoveryManifestPublicationRow, RecoveryManifest>? {
        val envelope = source.loadArtifact(runId, row.keyEnvelopeRelativeName)
        val ciphertext = source.loadArtifact(runId, row.publicationRelativeName)
        if (
            !matches(envelope, row.keyEnvelopeBytes, row.keyEnvelopeSha256) ||
                !matches(ciphertext, row.publicationBytes, row.publicationSha256)
        )
            return null
        return try {
            val manifest =
                crypto.authenticateManifest(
                    runId,
                    row,
                    row.previousPublicationCiphertextSha256,
                    envelope!!.snapshot(),
                    ciphertext!!.snapshot(),
                )
            if (
                manifest.generation != row.generation ||
                    manifest.committedEndExclusive != row.committedEndExclusive ||
                    manifest.entries != units.map(::manifestEntry)
            )
                null
            else row to manifest
        } catch (_: Throwable) {
            null
        }
    }

    @Suppress("ComplexCondition")
    private fun maximalValidUnits(
        rows: List<RecoveryMicrofileUnitRow>,
        runId: String,
    ): List<RecoveryMicrofileUnitRow> {
        val result = mutableListOf<RecoveryMicrofileUnitRow>()
        var start = 0UL
        for (row in rows.sortedBy { it.unitIndex }) {
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
            AuthenticatedMicrofilePrefix(runId, publication.generation, end, plaintext, units)
        val rowsDigest =
            Sha256Value.calculate(
                units.flatMap { it.processingIntentId.toByteArray().asIterable() }.toByteArray()
            )
        val capability =
            AuthenticatedMicrofilePrefixCapability(
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
    ) =
        MicrofileReconciliationResult.NoAuthenticatedPrefix(
            classification,
            confirmationDiagnostic,
            diagnostic,
            quarantine,
        )
}
