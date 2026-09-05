package com.monumentogram.dora.poc.recovery.journal

import android.content.Context
import android.database.Cursor
import com.monumentogram.dora.poc.recovery.bootstrap.AndroidRecoveryBootstrapCrypto
import com.monumentogram.dora.poc.recovery.candidate.QuarantineBootstrapBinding
import com.monumentogram.dora.poc.recovery.candidate.QuarantinePathState
import com.monumentogram.dora.poc.recovery.candidate.RecoveryArtifactBytes
import com.monumentogram.dora.poc.recovery.candidate.RecoveryArtifactContext
import com.monumentogram.dora.poc.recovery.candidate.RecoveryArtifactPresence
import com.monumentogram.dora.poc.recovery.candidate.RecoveryBootstrapRowState
import com.monumentogram.dora.poc.recovery.candidate.RecoveryCandidateSnapshot
import com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureCategory
import com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureDiagnostic
import com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureStage
import com.monumentogram.dora.poc.recovery.candidate.RecoveryInventoryEntry
import com.monumentogram.dora.poc.recovery.candidate.RecoveryInventorySnapshot
import com.monumentogram.dora.poc.recovery.candidate.RecoveryReconciliationSource
import com.monumentogram.dora.poc.recovery.candidate.RecoveryReportOnlyInventoryEntry
import com.monumentogram.dora.poc.recovery.candidate.RecoverySourceAccessException
import com.monumentogram.dora.poc.recovery.candidate.RecoverySourceFailureContext
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.controller.AliasObservation
import com.monumentogram.dora.poc.recovery.controller.ConfirmationArtifactSnapshot
import com.monumentogram.dora.poc.recovery.controller.ConfirmationPathObservation
import com.monumentogram.dora.poc.recovery.controller.KeyConfirmationSnapshot
import com.monumentogram.dora.poc.recovery.controller.StoredKeyConfirmationIdentity
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryReconciliationStorage
import com.monumentogram.dora.poc.recovery.storage.RecoveryArtifactAccessException
import com.monumentogram.dora.poc.recovery.storage.RecoveryUnsafePathException

/**
 * Production source: rows come from the unified journal and bytes from descriptor-backed storage.
 */
@Suppress("TooManyFunctions")
internal class AndroidRecoveryReconciliationSource
private constructor(
    private val loadBootstrap: (RunId) -> StoredKeyConfirmationIdentity?,
    private val loadSnapshot: (RunId) -> RecoveryCandidateSnapshot,
    private val loadPending:
        (RunId) -> List<com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineIntentRow>,
    private val loadAllIntents:
        (RunId) -> List<com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineIntentRow>,
    private val storage: AndroidOsRecoveryReconciliationStorage,
    private val aliasExists: (RunId) -> Boolean,
) : RecoveryReconciliationSource {
    constructor(
        context: Context
    ) : this(
        loadBootstrap = { runId -> loadBootstrapIdentity(context.applicationContext, runId) },
        loadSnapshot = AndroidRecoveryMicrofileJournal(context.applicationContext)::loadSnapshot,
        loadPending = AndroidRecoveryQuarantineJournal(context.applicationContext)::loadPending,
        loadAllIntents = AndroidRecoveryQuarantineJournal(context.applicationContext)::loadAll,
        storage = AndroidOsRecoveryReconciliationStorage(context.applicationContext),
        aliasExists = AndroidRecoveryBootstrapCrypto()::aliasExists,
    )

    @Suppress("LongParameterList", "UnusedPrivateProperty")
    internal constructor(
        loadBootstrap: (RunId) -> StoredKeyConfirmationIdentity?,
        loadSnapshot: (RunId) -> RecoveryCandidateSnapshot,
        loadPending:
            (RunId) -> List<
                    com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineIntentRow
                >,
        loadAllIntents:
            (RunId) -> List<
                    com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineIntentRow
                >,
        storage: AndroidOsRecoveryReconciliationStorage,
        aliasExists: (RunId) -> Boolean,
        testPort: Unit = Unit,
    ) : this(loadBootstrap, loadSnapshot, loadPending, loadAllIntents, storage, aliasExists)

    override fun loadConfirmation(runId: RunId): KeyConfirmationSnapshot {
        val expected = KeyConfirmationValue(RecoveryCandidate.MICROFILE, runId)
        val row =
            try {
                journalCall { loadBootstrap(runId) }
            } catch (error: RecoverySourceAccessException) {
                throw error.withContext(
                    RecoverySourceFailureContext(
                        RecoveryBootstrapRowState.UNKNOWN,
                        null,
                        null,
                        RecoveryArtifactPresence.UNKNOWN,
                    )
                )
            }
        val rowState =
            if (row == null) RecoveryBootstrapRowState.ABSENT else RecoveryBootstrapRowState.PRESENT
        val final =
            pathCall(rowState, RecoveryArtifactContext.CONFIRMATION_FINAL, CONFIRMATION_FINAL) {
                storage.loadActiveArtifact(runId, CONFIRMATION_FINAL, MAX_CONFIRMATION_BYTES)
            }
        val finalPresence =
            if (final == null) RecoveryArtifactPresence.ABSENT else RecoveryArtifactPresence.PRESENT
        return KeyConfirmationSnapshot(
            expected,
            row,
            final?.let {
                ConfirmationArtifactSnapshot(
                    it.relativeName,
                    ConfirmationPathObservation(true, true, true, true),
                    it.snapshot(),
                )
            },
            pathCall(
                rowState,
                RecoveryArtifactContext.CONFIRMATION_TEMP,
                CONFIRMATION_TEMP,
                finalPresence,
            ) {
                storage.activeArtifactExists(runId, CONFIRMATION_TEMP)
            },
            if (cryptoCall { aliasExists(runId) }) AliasObservation.PRESENT
            else AliasObservation.ABSENT,
        )
    }

    override fun loadCandidate(runId: RunId): RecoveryCandidateSnapshot = journalCall {
        loadSnapshot(runId)
    }

    override fun loadArtifact(
        runId: RunId,
        relativeName: String,
        context: RecoveryArtifactContext,
    ): RecoveryArtifactBytes? =
        pathCall(null, context, relativeName) {
            storage.loadActiveArtifact(runId, relativeName, MAX_CANDIDATE_ARTIFACT_BYTES)
        }

    override fun loadPendingQuarantine(runId: RunId) = journalCall {
        loadPending(runId)
    }

    override fun loadInventory(runId: RunId): List<RecoveryInventoryEntry> =
        loadInventorySnapshot(runId, journalCall { loadSnapshot(runId) }).active

    @Suppress("LongMethod")
    override fun loadInventorySnapshot(
        runId: RunId,
        candidate: RecoveryCandidateSnapshot,
    ): RecoveryInventorySnapshot {
        val binding =
            if (journalCall { loadBootstrap(runId) } == null) QuarantineBootstrapBinding.ABSENT
            else QuarantineBootstrapBinding.PRESENT
        val activeArtifacts = pathCall { storage.listActiveInventory(runId) }
        val activeNames = activeArtifacts.map { it.relativeName }.toSet()
        val referenced = buildSet {
            candidate.units.forEach {
                add(it.ciphertextRelativeName)
                add(it.keyEnvelopeRelativeName)
            }
            candidate.publications.forEach {
                add(it.publicationRelativeName)
                add(it.keyEnvelopeRelativeName)
            }
        }
        val active = activeArtifacts.map { stored ->
            val artifact = requireNotNull(stored.artifact)
            RecoveryInventoryEntry(
                com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput(
                    RecoveryCandidate.MICROFILE,
                    runId,
                    artifact.relativeName,
                    RecoveryInventoryClassifier.role(artifact.relativeName),
                    artifact.size.toULong(),
                    artifact.sha256,
                ),
                RecoveryInventoryClassifier.observed(
                    artifact.relativeName,
                    activeNames,
                    referenced,
                ),
                binding,
            )
        }
        val rows = journalCall { loadAllIntents(runId) }
        val known = rows.associateBy { it.destinationRelativeName }
        val quarantine =
            pathCall { storage.listQuarantineInventory(runId) }
                .map { stored ->
                    val row = known[stored.relativeName]
                    val artifact = stored.artifact
                    val exact =
                        row != null &&
                            artifact != null &&
                            artifact.size.toULong() == row.input.sourceBytes &&
                            artifact.sha256 == row.input.sourceSha256
                    RecoveryReportOnlyInventoryEntry(
                        stored.relativeName,
                        when {
                            stored.pathType !=
                                com.monumentogram.dora.poc.recovery.storage.BootstrapPathType
                                    .REGULAR -> QuarantinePathState.UNSAFE
                            exact -> QuarantinePathState.EXACT
                            else -> QuarantinePathState.OCCUPIED
                        },
                        artifact?.size?.toULong(),
                        artifact?.sha256,
                        row != null,
                    )
                }
        return RecoveryInventorySnapshot(
            java.util.Collections.unmodifiableList(active),
            java.util.Collections.unmodifiableList(quarantine),
        )
    }

    private inline fun <T> journalCall(block: () -> T): T =
        sourceCall(RecoveryFailureCategory.OPERATIONAL, RecoveryFailureStage.JOURNAL, block)

    private inline fun <T> cryptoCall(block: () -> T): T =
        sourceCall(
            RecoveryFailureCategory.OPERATIONAL,
            RecoveryFailureStage.ALIAS_OBSERVATION,
            block,
        )

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> pathCall(block: () -> T): T =
        pathCall(null, null, null, RecoveryArtifactPresence.UNKNOWN, block)

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> pathCall(
        rowState: RecoveryBootstrapRowState?,
        context: RecoveryArtifactContext?,
        relativeName: String?,
        finalPresence: RecoveryArtifactPresence = RecoveryArtifactPresence.UNKNOWN,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (error: RecoverySourceAccessException) {
            throw error
        } catch (error: RecoveryUnsafePathException) {
            throw RecoverySourceAccessException(
                RecoveryFailureDiagnostic.capture(
                    error.category,
                    error,
                    RecoveryFailureStage.ARTIFACT_PATH,
                ),
                error,
                failureContext(
                    rowState,
                    context,
                    relativeName,
                    if (error.category == RecoveryFailureCategory.CORRUPT_LEAF)
                        RecoveryArtifactPresence.PRESENT
                    else RecoveryArtifactPresence.UNKNOWN,
                    finalPresence,
                ),
            )
        } catch (error: RecoveryArtifactAccessException) {
            throw RecoverySourceAccessException(
                RecoveryFailureDiagnostic.capture(
                    if (error.structural) RecoveryFailureCategory.STRUCTURAL
                    else RecoveryFailureCategory.OPERATIONAL,
                    error.cause ?: error,
                    failureStage(context, error.structural),
                ),
                error,
                failureContext(
                    rowState,
                    context,
                    relativeName,
                    error.presence,
                    finalPresence,
                ),
            )
        } catch (error: Throwable) {
            throw RecoverySourceAccessException(
                RecoveryFailureDiagnostic.capture(
                    RecoveryFailureCategory.OPERATIONAL,
                    error,
                    RecoveryFailureStage.ARTIFACT_IO,
                ),
                error,
                failureContext(
                    rowState,
                    context,
                    relativeName,
                    RecoveryArtifactPresence.UNKNOWN,
                    finalPresence,
                ),
            )
        }

    private fun failureStage(
        context: RecoveryArtifactContext?,
        structural: Boolean,
    ): RecoveryFailureStage =
        if (
            structural &&
                context in
                    setOf(
                        RecoveryArtifactContext.UNIT_KEY_ENVELOPE,
                        RecoveryArtifactContext.MANIFEST_KEY_ENVELOPE,
                    )
        )
            RecoveryFailureStage.ENVELOPE_BINDING
        else if (structural) RecoveryFailureStage.ARTIFACT_PATH
        else RecoveryFailureStage.ARTIFACT_IO

    private fun failureContext(
        rowState: RecoveryBootstrapRowState?,
        context: RecoveryArtifactContext?,
        relativeName: String?,
        presence: RecoveryArtifactPresence,
        finalPresence: RecoveryArtifactPresence,
    ) =
        if (context == null && rowState == null) null
        else RecoverySourceFailureContext(rowState, context, relativeName, presence, finalPresence)

    private fun RecoverySourceAccessException.withContext(context: RecoverySourceFailureContext) =
        RecoverySourceAccessException(diagnostic, cause ?: this, context)

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> sourceCall(
        category: RecoveryFailureCategory,
        stage: RecoveryFailureStage,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (error: RecoverySourceAccessException) {
            throw error
        } catch (error: Throwable) {
            throw RecoverySourceAccessException(
                RecoveryFailureDiagnostic.capture(category, error, stage),
                error,
            )
        }

    internal companion object {
        fun withStorage(
            context: Context,
            storage: AndroidOsRecoveryReconciliationStorage,
        ): AndroidRecoveryReconciliationSource =
            AndroidRecoveryReconciliationSource(
                loadBootstrap = { runId ->
                    loadBootstrapIdentity(context.applicationContext, runId)
                },
                loadSnapshot =
                    AndroidRecoveryMicrofileJournal(context.applicationContext)::loadSnapshot,
                loadPending =
                    AndroidRecoveryQuarantineJournal(context.applicationContext)::loadPending,
                loadAllIntents =
                    AndroidRecoveryQuarantineJournal(context.applicationContext)::loadAll,
                storage = storage,
                aliasExists = AndroidRecoveryBootstrapCrypto()::aliasExists,
            )

        fun loadBootstrapIdentity(context: Context, runId: RunId): StoredKeyConfirmationIdentity? =
            AndroidRecoveryJournalDatabase.writable(context)
                .query(
                    RecoveryJournalSchema.RUN_TABLE,
                    null,
                    "run_id=?",
                    arrayOf(runId.toCanonicalString()),
                    null,
                    null,
                    null,
                )
                .use { cursor ->
                    decodeBootstrapIdentity(cursor, runId)
                }

        @Suppress("ThrowsCount")
        internal fun decodeBootstrapIdentity(
            cursor: Cursor,
            runId: RunId,
        ): StoredKeyConfirmationIdentity? {
            when (cursor.count) {
                0 -> return null
                1 -> Unit
                else -> throw bootstrapStructuralFailure("Ambiguous bootstrap row")
            }
            if (!cursor.moveToFirst()) {
                throw bootstrapStructuralFailure("Bootstrap cursor could not position its only row")
            }
            val candidateId = cursor.getString(cursor.getColumnIndexOrThrow("candidate_id"))
            val relativeName =
                cursor.getString(cursor.getColumnIndexOrThrow("key_confirmation_relative_name"))
            val ciphertextBytes =
                cursor.getLong(cursor.getColumnIndexOrThrow("key_confirmation_bytes"))
            val ciphertextSha256 =
                cursor.getBlob(cursor.getColumnIndexOrThrow("key_confirmation_sha256"))
            val canonicalAliasSha256 =
                cursor.getBlob(cursor.getColumnIndexOrThrow("canonical_alias_sha256"))
            return try {
                StoredKeyConfirmationIdentity(
                    KeyConfirmationValue(
                        RecoveryCandidate.fromContractId(candidateId),
                        runId,
                    ),
                    relativeName,
                    ciphertextBytes,
                    Sha256Value.fromBytes(ciphertextSha256),
                    Sha256Value.fromBytes(canonicalAliasSha256),
                )
            } catch (error: IllegalArgumentException) {
                throw bootstrapStructuralFailure("Malformed bootstrap identity", error)
            }
        }

        private fun bootstrapStructuralFailure(
            message: String,
            cause: Throwable = IllegalStateException(message),
        ): RecoverySourceAccessException =
            RecoverySourceAccessException(
                RecoveryFailureDiagnostic.capture(
                    RecoveryFailureCategory.STRUCTURAL,
                    cause,
                    RecoveryFailureStage.JOURNAL,
                ),
                cause,
            )

        const val CONFIRMATION_FINAL = "key-confirmation/run.kc"
        const val CONFIRMATION_TEMP = "key-confirmation/run.kc.tmp"
        const val MAX_CONFIRMATION_BYTES = 512L
        const val MAX_CANDIDATE_ARTIFACT_BYTES = 1_048_576L
    }
}

internal object RecoveryInventoryClassifier {
    fun role(name: String) =
        when {
            name.startsWith("key-confirmation/") -> RecoveryQuarantineArtifactRole.KEY_CONFIRMATION
            name.startsWith("units/") -> RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT
            name.startsWith("manifests/") -> RecoveryQuarantineArtifactRole.MANIFEST_CIPHERTEXT
            name.startsWith("key-envelopes/manifest-") ->
                RecoveryQuarantineArtifactRole.MANIFEST_KEY_ENVELOPE
            name.startsWith("key-envelopes/") ->
                RecoveryQuarantineArtifactRole.MICROFILE_KEY_ENVELOPE
            else -> RecoveryQuarantineArtifactRole.UNKNOWN_REGULAR
        }

    fun observed(
        name: String,
        allNames: Set<String>,
        referenced: Set<String> = emptySet(),
    ): com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState {
        val role = role(name)
        if (role == RecoveryQuarantineArtifactRole.UNKNOWN_REGULAR) {
            return com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
                .UNKNOWN_OR_NON_ALLOWLISTED_NAME
        }
        val counterpart = if (name.endsWith(".tmp")) name.removeSuffix(".tmp") else "$name.tmp"
        return when {
            name.endsWith(".tmp") && name in referenced ->
                com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
                    .SQLITE_POINTS_TO_TEMP
            counterpart in allNames ->
                com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
                    .TEMP_AND_FINAL
            name.endsWith(".tmp") ->
                com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
                    .TEMP_ONLY
            else ->
                com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
                    .FINAL_ORPHAN
        }
    }
}
