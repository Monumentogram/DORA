package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.journal.RecoveryStreamingQuarantineReadbackException
import com.monumentogram.dora.poc.recovery.storage.RecoveryArtifactAccessException
import com.monumentogram.dora.poc.recovery.storage.RecoveryArtifactRoleBounds
import com.monumentogram.dora.poc.recovery.storage.RecoveryUnsafePathException

/** Named replay loader shared by Android composition and host decision regressions. */
@Suppress("SwallowedException", "ThrowsCount")
internal class AndroidRecoveryStreamingOrphanArtifacts(
    private val loadActiveArtifact: (RunId, String, Long) -> RecoveryArtifactBytes?,
    private val loadJournalRow: (RunId, String) -> RecoveryQuarantineIntentRow?,
    private val inspect: (RecoveryQuarantineIntentRow) -> QuarantinePathObservation,
    private val loadDestination: (RecoveryQuarantineIntentRow) -> RecoveryArtifactBytes?,
) : RecoveryStreamingOrphanArtifacts {
    @Suppress("ReturnCount")
    override fun load(
        runId: RunId,
        name: String,
        role: RecoveryQuarantineArtifactRole,
    ): RecoveryArtifactBytes? {
        val active = artifactAccess {
            loadActiveArtifact(runId, name, RecoveryArtifactRoleBounds.maximumFor(name))
        }
        // The shared stream envelope remains active and is never replayed from quarantine.
        if (role == RecoveryQuarantineArtifactRole.STREAM_KEY_ENVELOPE) return active
        val row = journalRow(runId, name) ?: return active
        validateRow(row, runId, name, role)
        val observed = artifactAccess { inspect(row) }
        if (
            observed.source == QuarantinePathState.UNSAFE ||
                observed.destination == QuarantinePathState.UNSAFE
        ) {
            throw RecoveryStreamingOrphanAccessException(RecoveryStreamingOrphanFailure.UNSAFE_PATH)
        }
        return when (observed) {
            QuarantinePathObservation(QuarantinePathState.EXACT, QuarantinePathState.ABSENT) -> {
                if (row.state != QuarantineIntentState.PENDING) {
                    throw RecoveryStreamingOrphanAccessException(
                        RecoveryStreamingOrphanFailure.JOURNAL_STRUCTURAL
                    )
                }
                if (
                    active?.sha256 != row.input.sourceSha256 ||
                        active.size.toULong() != row.input.sourceBytes
                ) {
                    throw RecoveryStreamingOrphanAccessException(
                        RecoveryStreamingOrphanFailure.ARTIFACT_STRUCTURAL
                    )
                }
                active
            }
            QuarantinePathObservation(QuarantinePathState.ABSENT, QuarantinePathState.EXACT) ->
                artifactAccess { loadDestination(row) }
            else ->
                throw RecoveryStreamingOrphanAccessException(
                    RecoveryStreamingOrphanFailure.ARTIFACT_STRUCTURAL
                )
        }
    }

    private fun validateRow(
        row: RecoveryQuarantineIntentRow,
        runId: RunId,
        name: String,
        role: RecoveryQuarantineArtifactRole,
    ) {
        val namedSource = row.input.runId == runId && row.input.sourceRelativeName == name
        val orphanPublication =
            row.input.artifactRole == role &&
                row.recordedObservedState == RecoveryQuarantineObservedState.FINAL_ORPHAN &&
                row.bootstrapBinding == QuarantineBootstrapBinding.PRESENT
        if (!namedSource || !orphanPublication) {
            throw RecoveryStreamingOrphanAccessException(
                RecoveryStreamingOrphanFailure.JOURNAL_STRUCTURAL
            )
        }
    }

    private fun journalRow(runId: RunId, name: String): RecoveryQuarantineIntentRow? =
        try {
            loadJournalRow(runId, name)
        } catch (_: RecoveryStreamingQuarantineReadbackException) {
            // Strict field decoders and explicit readback checks carry this marker.
            throw RecoveryStreamingOrphanAccessException(
                RecoveryStreamingOrphanFailure.JOURNAL_STRUCTURAL
            )
        } catch (_: Throwable) {
            // A closed SQLite connection can throw IllegalStateException: it is operational.
            throw RecoveryStreamingOrphanAccessException(
                RecoveryStreamingOrphanFailure.JOURNAL_OPERATIONAL
            )
        }

    private fun <T> artifactAccess(block: () -> T): T =
        try {
            block()
        } catch (_: RecoveryUnsafePathException) {
            throw RecoveryStreamingOrphanAccessException(RecoveryStreamingOrphanFailure.UNSAFE_PATH)
        } catch (failure: RecoveryArtifactAccessException) {
            throw RecoveryStreamingOrphanAccessException(
                if (failure.structural) RecoveryStreamingOrphanFailure.ARTIFACT_STRUCTURAL
                else RecoveryStreamingOrphanFailure.ARTIFACT_OPERATIONAL
            )
        } catch (_: IllegalArgumentException) {
            throw RecoveryStreamingOrphanAccessException(
                RecoveryStreamingOrphanFailure.ARTIFACT_STRUCTURAL
            )
        } catch (_: Throwable) {
            throw RecoveryStreamingOrphanAccessException(
                RecoveryStreamingOrphanFailure.ARTIFACT_OPERATIONAL
            )
        }
}

internal class RecoveryStreamingOrphanAccessException(val failure: RecoveryStreamingOrphanFailure) :
    RuntimeException()

/** Closed classification shared by artifact access and the whole-object quarantine adapter. */
internal enum class RecoveryStreamingOrphanFailure {
    UNSAFE_PATH,
    ARTIFACT_STRUCTURAL,
    ARTIFACT_OPERATIONAL,
    JOURNAL_STRUCTURAL,
    JOURNAL_OPERATIONAL,
    JOURNAL_COMMIT_UNRESOLVED;

    fun result(): RecoveryStreamingReconciliationResult =
        when (this) {
            UNSAFE_PATH ->
                RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification.UNSAFE_PATH,
                )
            ARTIFACT_STRUCTURAL ->
                RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification.STREAM_CHECKPOINT_STRUCTURAL,
                )
            ARTIFACT_OPERATIONAL ->
                RecoveryStreamingReconciliationResult.Retry.of(
                    RecoveryStreamingResultStage.SOURCE_PROOF,
                    RecoveryStreamingResultClassification.ARTIFACT_IO_BEFORE_EXACT_SOURCE_HASH,
                    RecoveryStreamingSafeExceptionType.IO,
                )
            JOURNAL_STRUCTURAL ->
                RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
                    RecoveryStreamingResultStage.JOURNAL,
                    RecoveryStreamingResultClassification.JOURNAL_STRUCTURAL,
                )
            JOURNAL_OPERATIONAL,
            JOURNAL_COMMIT_UNRESOLVED ->
                RecoveryStreamingReconciliationResult.Retry.of(
                    RecoveryStreamingResultStage.JOURNAL,
                    if (this == JOURNAL_COMMIT_UNRESOLVED)
                        RecoveryStreamingResultClassification.JOURNAL_COMMIT_STATE_UNRESOLVED
                    else RecoveryStreamingResultClassification.JOURNAL_OPERATIONAL,
                    RecoveryStreamingSafeExceptionType.SQLITE,
                )
        }
}
