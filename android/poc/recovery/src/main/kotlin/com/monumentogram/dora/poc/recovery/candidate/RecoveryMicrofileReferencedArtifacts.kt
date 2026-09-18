package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.storage.RecoveryRetainedOriginalMismatch

/** Scoped to one bootstrap-validated reconciliation and an authenticated manifest's members. */
internal class RecoveryMicrofileReferencedArtifacts(
    private val source: RecoveryReconciliationSource,
    private val quarantine: RecoveryQuarantineController?,
    private val outcomes: MutableList<QuarantineResult>,
) {
    private val verifiedRejected = mutableSetOf<RecoveryQuarantineIntentInput>()

    fun wasRejected(original: RecoveryQuarantineIntentInput): Boolean = original in verifiedRejected

    fun loadOriginal(original: RecoveryQuarantineIntentInput): RecoveryArtifactBytes? =
        source.loadArtifact(original.runId, original.sourceRelativeName, context(original))
            ?: retained(original)

    @Suppress("ReturnCount")
    fun loadUnit(original: RecoveryQuarantineIntentInput): RecoveryArtifactBytes? {
        val active =
            source.loadArtifact(original.runId, original.sourceRelativeName, context(original))
                ?: return retained(original)
        if (matches(active, original)) return active
        if (!move(original, active, RecoveryQuarantineObservedState.REFERENCED_REJECTED))
            return active
        return retained(original)
    }

    /** Caller has authenticated this original member's dependency on a rejected unit. */
    fun retainDependent(original: RecoveryQuarantineIntentInput): Boolean {
        val active =
            source.loadArtifact(original.runId, original.sourceRelativeName, context(original))
        if (active == null) {
            return try {
                retained(original) != null
            } catch (error: RecoverySourceAccessException) {
                if (verifiedMismatch(error, original)) true else throw error
            }
        }
        val state =
            if (matches(active, original)) RecoveryQuarantineObservedState.REFERENCED_DEPENDENT
            else RecoveryQuarantineObservedState.REFERENCED_REJECTED
        return move(original, active, state)
    }

    private fun retained(original: RecoveryQuarantineIntentInput): RecoveryArtifactBytes? =
        try {
            source.loadRetainedArtifact(original, context(original))
        } catch (error: RecoverySourceAccessException) {
            if (verifiedMismatch(error, original)) verifiedRejected += original
            throw error
        }

    private fun verifiedMismatch(
        error: RecoverySourceAccessException,
        original: RecoveryQuarantineIntentInput,
    ): Boolean {
        val mismatch = error.cause?.cause as? RecoveryRetainedOriginalMismatch
        return mismatch?.original == original &&
            mismatch.retained.recordedObservedState ==
                RecoveryQuarantineObservedState.REFERENCED_REJECTED
    }

    private fun move(
        original: RecoveryQuarantineIntentInput,
        actual: RecoveryArtifactBytes,
        state: RecoveryQuarantineObservedState,
    ): Boolean {
        val result =
            quarantine?.quarantine(
                original.copy(sourceBytes = actual.size.toULong(), sourceSha256 = actual.sha256),
                state,
                QuarantineBootstrapBinding.PRESENT,
            ) ?: return false
        outcomes += result
        return result is QuarantineResult.Completed
    }

    private fun matches(actual: RecoveryArtifactBytes, original: RecoveryQuarantineIntentInput) =
        actual.relativeName == original.sourceRelativeName &&
            actual.size.toULong() == original.sourceBytes &&
            actual.sha256 == original.sourceSha256

    companion object {
        fun unitCiphertext(run: RunId, row: RecoveryMicrofileUnitRow) =
            original(
                run,
                row.ciphertextRelativeName,
                RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT,
                row.ciphertextBytes,
                row.ciphertextSha256,
            )

        fun unitEnvelope(run: RunId, row: RecoveryMicrofileUnitRow) =
            original(
                run,
                row.keyEnvelopeRelativeName,
                RecoveryQuarantineArtifactRole.MICROFILE_KEY_ENVELOPE,
                row.keyEnvelopeBytes,
                row.keyEnvelopeSha256,
            )

        fun manifestCiphertext(run: RunId, row: RecoveryManifestPublicationRow) =
            original(
                run,
                row.publicationRelativeName,
                RecoveryQuarantineArtifactRole.MANIFEST_CIPHERTEXT,
                row.publicationBytes,
                row.publicationSha256,
            )

        fun manifestEnvelope(run: RunId, row: RecoveryManifestPublicationRow) =
            original(
                run,
                row.keyEnvelopeRelativeName,
                RecoveryQuarantineArtifactRole.MANIFEST_KEY_ENVELOPE,
                row.keyEnvelopeBytes,
                row.keyEnvelopeSha256,
            )

        private fun original(
            run: RunId,
            name: String,
            role: RecoveryQuarantineArtifactRole,
            bytes: Long,
            sha256: Sha256Value,
        ) =
            RecoveryQuarantineIntentInput(
                RecoveryCandidate.MICROFILE,
                run,
                name,
                role,
                bytes.toULong(),
                sha256,
            )

        private fun context(original: RecoveryQuarantineIntentInput) =
            when (original.artifactRole) {
                RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT ->
                    RecoveryArtifactContext.UNIT_CIPHERTEXT
                RecoveryQuarantineArtifactRole.MICROFILE_KEY_ENVELOPE ->
                    RecoveryArtifactContext.UNIT_KEY_ENVELOPE
                RecoveryQuarantineArtifactRole.MANIFEST_CIPHERTEXT ->
                    RecoveryArtifactContext.MANIFEST_CIPHERTEXT
                RecoveryQuarantineArtifactRole.MANIFEST_KEY_ENVELOPE ->
                    RecoveryArtifactContext.MANIFEST_KEY_ENVELOPE
                else -> error("Not a referenced MICROFILE role")
            }
    }
}
