package com.monumentogram.dora.poc.recovery.journal

import android.content.Context
import com.monumentogram.dora.poc.recovery.bootstrap.AndroidRecoveryBootstrapCrypto
import com.monumentogram.dora.poc.recovery.candidate.QuarantineBootstrapBinding
import com.monumentogram.dora.poc.recovery.candidate.RecoveryArtifactBytes
import com.monumentogram.dora.poc.recovery.candidate.RecoveryCandidateSnapshot
import com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureCategory
import com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureDiagnostic
import com.monumentogram.dora.poc.recovery.candidate.RecoveryInventoryEntry
import com.monumentogram.dora.poc.recovery.candidate.RecoveryReconciliationSource
import com.monumentogram.dora.poc.recovery.candidate.RecoverySourceAccessException
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

/**
 * Production source: rows come from the unified journal and bytes from descriptor-backed storage.
 */
internal class AndroidRecoveryReconciliationSource(context: Context) :
    RecoveryReconciliationSource {
    private val applicationContext = context.applicationContext
    private val journal = AndroidRecoveryMicrofileJournal(applicationContext)
    private val storage = AndroidOsRecoveryReconciliationStorage(applicationContext)
    private val bootstrapCrypto = AndroidRecoveryBootstrapCrypto()

    override fun loadConfirmation(runId: RunId): KeyConfirmationSnapshot {
        val expected = KeyConfirmationValue(RecoveryCandidate.MICROFILE, runId)
        val row = journalCall { loadBootstrapIdentity(runId) }
        val final = pathCall {
            storage.loadActiveArtifact(runId, CONFIRMATION_FINAL, MAX_CONFIRMATION_BYTES)
        }
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
            pathCall { storage.activeArtifactExists(runId, CONFIRMATION_TEMP) },
            if (cryptoCall { bootstrapCrypto.aliasExists(runId) }) AliasObservation.PRESENT
            else AliasObservation.ABSENT,
        )
    }

    override fun loadCandidate(runId: RunId): RecoveryCandidateSnapshot = journalCall {
        journal.loadSnapshot(runId)
    }

    override fun loadArtifact(runId: RunId, relativeName: String): RecoveryArtifactBytes? =
        pathCall {
            storage.loadActiveArtifact(runId, relativeName, MAX_CANDIDATE_ARTIFACT_BYTES)
        }

    override fun loadPendingQuarantine(runId: RunId) = journalCall {
        AndroidRecoveryQuarantineJournal(applicationContext).loadPending(runId)
    }

    override fun loadInventory(runId: RunId): List<RecoveryInventoryEntry> {
        val binding =
            if (loadBootstrapIdentity(runId) == null) QuarantineBootstrapBinding.ABSENT
            else QuarantineBootstrapBinding.PRESENT
        return pathCall { storage.listActiveArtifacts(runId) }
            .map { artifact ->
                RecoveryInventoryEntry(
                    com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput(
                        RecoveryCandidate.MICROFILE,
                        runId,
                        artifact.relativeName,
                        RecoveryInventoryClassifier.role(artifact.relativeName),
                        artifact.size.toULong(),
                        artifact.sha256,
                    ),
                    if (artifact.relativeName.endsWith(".tmp"))
                        com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
                            .TEMP_ONLY
                    else
                        com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
                            .FINAL_ORPHAN,
                    binding,
                )
            }
    }

    private inline fun <T> journalCall(block: () -> T): T =
        sourceCall(RecoveryFailureCategory.OPERATIONAL, block)

    private inline fun <T> cryptoCall(block: () -> T): T =
        sourceCall(RecoveryFailureCategory.OPERATIONAL, block)

    private inline fun <T> pathCall(block: () -> T): T =
        sourceCall(RecoveryFailureCategory.UNSAFE_PARENT, block)

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> sourceCall(category: RecoveryFailureCategory, block: () -> T): T =
        try {
            block()
        } catch (error: RecoverySourceAccessException) {
            throw error
        } catch (error: Throwable) {
            throw RecoverySourceAccessException(
                RecoveryFailureDiagnostic.capture(category, error),
                error,
            )
        }

    private fun loadBootstrapIdentity(runId: RunId): StoredKeyConfirmationIdentity? =
        AndroidRecoveryJournalDatabase.writable(applicationContext)
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
                if (!cursor.moveToFirst()) null
                else {
                    check(!cursor.moveToNext()) { "Ambiguous bootstrap row" }
                    StoredKeyConfirmationIdentity(
                        KeyConfirmationValue(
                            RecoveryCandidate.fromContractId(
                                cursor.getString(cursor.getColumnIndexOrThrow("candidate_id"))
                            ),
                            runId,
                        ),
                        cursor.getString(
                            cursor.getColumnIndexOrThrow("key_confirmation_relative_name")
                        ),
                        cursor.getLong(cursor.getColumnIndexOrThrow("key_confirmation_bytes")),
                        Sha256Value.fromBytes(
                            cursor.getBlob(cursor.getColumnIndexOrThrow("key_confirmation_sha256"))
                        ),
                        Sha256Value.fromBytes(
                            cursor.getBlob(cursor.getColumnIndexOrThrow("canonical_alias_sha256"))
                        ),
                    )
                }
            }

    private companion object {
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
}
