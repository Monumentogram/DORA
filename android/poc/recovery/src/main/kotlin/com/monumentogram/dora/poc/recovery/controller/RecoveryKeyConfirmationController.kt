package com.monumentogram.dora.poc.recovery.controller

import com.monumentogram.dora.poc.recovery.contract.Key04Observation
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationDecryptOutcome
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationRouting
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.KeyRecoveryClassification
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.crypto.KeyConfirmationDecryption
import com.monumentogram.dora.poc.recovery.crypto.RecoveryDecryptFailureSignal
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAead
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import java.security.GeneralSecurityException
import java.security.ProviderException

/** Harness observations only: this slice performs no filesystem, SQLite or lifecycle operations. */
internal data class ConfirmationPathObservation(
    val containedBeneathRunRoot: Boolean,
    val everyExistingComponentLstatObserved: Boolean,
    val leafIsRegularFile: Boolean,
    val noComponentOrLeafSymlink: Boolean,
)

internal enum class AliasObservation {
    ABSENT,
    PRESENT,
    INVALIDATED,
    UNUSABLE,
}

internal data class StoredKeyConfirmationIdentity(
    val value: KeyConfirmationValue,
    val relativeName: String,
    val ciphertextBytes: Long,
    val ciphertextSha256: Sha256Value,
    val canonicalAliasSha256: Sha256Value,
)

internal class ConfirmationArtifactSnapshot(
    val relativeName: String,
    val path: ConfirmationPathObservation,
    ciphertext: ByteArray,
) {
    private val bytes = ciphertext.copyOf()

    fun ciphertextSnapshot(): ByteArray = bytes.copyOf()
}

/**
 * Harness receipt asserting a controlled KEY-04 replacement with another valid AEAD key BEFORE
 * recovery, preserving the old bytes and recorded identity. This controller does not perform or
 * independently witness replacement. A receipt alone can never establish a crypto outcome.
 */
internal data class ControlledKey04Replacement(
    val eventId: String,
    val canonicalAlias: String,
    val preservedIdentity: StoredKeyConfirmationIdentity,
)

internal data class BootstrapEvidence(
    val temporaryPresent: Boolean,
    val finalPresent: Boolean,
    val alias: AliasObservation,
)

internal data class KeyConfirmationSnapshot(
    val expected: KeyConfirmationValue,
    val durableRow: StoredKeyConfirmationIdentity?,
    val finalArtifact: ConfirmationArtifactSnapshot?,
    val temporaryPresent: Boolean,
    val alias: AliasObservation,
    val controlledReplacement: ControlledKey04Replacement? = null,
) {
    val evidence: BootstrapEvidence
        get() = BootstrapEvidence(temporaryPresent, finalArtifact != null, alias)
}

internal enum class ConfirmationPhase {
    BOOTSTRAP,
    STORED_IDENTITY,
    ALIAS_ACCESS,
    KEY04,
    PLAINTEXT_CONTRACT,
}

/** Diagnostics are NOT a ninth KEY classification or an assertion of ciphertext corruption. */
internal enum class ConfirmationDiagnostic {
    OPEN_OPERATIONAL_FAILURE,
    OPEN_UNEXPECTED_FAILURE,
    OPEN_IDENTITY_MISMATCH,
    AUTHENTICATION_WITHOUT_KEY04_PROVENANCE,
    DECRYPT_UNKNOWN_FAILURE,
    DECRYPT_OPERATIONAL_FAILURE,
    DECRYPT_UNEXPECTED_FAILURE,
}

/** All results concern confirmation only; none grants run readiness or publication authority. */
internal sealed interface ConfirmationResult {
    data class Absent(val evidence: BootstrapEvidence) : ConfirmationResult

    data class Validated(val value: KeyConfirmationValue, val evidence: BootstrapEvidence) :
        ConfirmationResult

    data class Rejected(
        val classification: KeyRecoveryClassification,
        val phase: ConfirmationPhase,
        val evidence: BootstrapEvidence,
    ) : ConfirmationResult

    data class Unclassified(
        val diagnostic: ConfirmationDiagnostic,
        val evidence: BootstrapEvidence,
    ) : ConfirmationResult
}

/**
 * The injected boundary exposes opening only. The default uses the approved existing-alias path.
 */
internal fun interface ExistingRecoveryRunAeadOpener {
    fun openExisting(runId: RunId): RecoveryRunAead
}

internal class RecoveryKeyConfirmationController(
    private val opener: ExistingRecoveryRunAeadOpener =
        ExistingRecoveryRunAeadOpener(RecoveryRunAeadProvider()::openExisting)
) {
    // Keep the protocol's ordered fail-closed gates visible rather than nesting later operations.
    @Suppress("ReturnCount")
    fun evaluate(snapshot: KeyConfirmationSnapshot): ConfirmationResult {
        val row = snapshot.durableRow ?: return absentRowResult(snapshot)
        val artifact =
            snapshot.finalArtifact
                ?: return rejected(
                    snapshot,
                    KeyRecoveryClassification.KEY_CONFIRMATION_MISSING,
                    ConfirmationPhase.STORED_IDENTITY,
                )
        // One private local copy is both hashed and decrypted. No caller-owned buffer crosses this
        // gate.
        val ciphertext = artifact.ciphertextSnapshot()
        if (!storedIdentityMatches(snapshot.expected, row, artifact, ciphertext)) {
            return rejected(
                snapshot,
                KeyRecoveryClassification.CORRUPT_KEY_CONFIRMATION,
                ConfirmationPhase.STORED_IDENTITY,
            )
        }
        if (snapshot.alias != AliasObservation.PRESENT) {
            return rejected(
                snapshot,
                KeyRecoveryClassification.KEY_UNAVAILABLE,
                ConfirmationPhase.ALIAS_ACCESS,
            )
        }
        return openAndValidate(snapshot, row, ciphertext)
    }

    private fun absentRowResult(snapshot: KeyConfirmationSnapshot): ConfirmationResult =
        if (
            snapshot.temporaryPresent ||
                snapshot.finalArtifact != null ||
                snapshot.alias != AliasObservation.ABSENT
        ) {
            rejected(
                snapshot,
                KeyRecoveryClassification.INCOMPLETE_KEY_BOOTSTRAP,
                ConfirmationPhase.BOOTSTRAP,
            )
        } else {
            ConfirmationResult.Absent(snapshot.evidence)
        }

    private fun storedIdentityMatches(
        expected: KeyConfirmationValue,
        row: StoredKeyConfirmationIdentity,
        artifact: ConfirmationArtifactSnapshot,
        ciphertext: ByteArray,
    ): Boolean =
        row.value == expected &&
            row.relativeName == FINAL_RELATIVE_NAME &&
            artifact.relativeName == FINAL_RELATIVE_NAME &&
            artifact.path.containedBeneathRunRoot &&
            artifact.path.everyExistingComponentLstatObserved &&
            artifact.path.leafIsRegularFile &&
            artifact.path.noComponentOrLeafSymlink &&
            row.ciphertextBytes == ciphertext.size.toLong() &&
            row.ciphertextSha256 == Sha256Value.calculate(ciphertext) &&
            row.canonicalAliasSha256 == expected.canonicalAliasSha256

    @Suppress("ReturnCount")
    private fun openAndValidate(
        snapshot: KeyConfirmationSnapshot,
        row: StoredKeyConfirmationIdentity,
        ciphertext: ByteArray,
    ): ConfirmationResult {
        val runAead =
            try {
                opener.openExisting(snapshot.expected.runId)
            } catch (_: GeneralSecurityException) {
                return rejected(
                    snapshot,
                    KeyRecoveryClassification.KEY_UNAVAILABLE,
                    ConfirmationPhase.ALIAS_ACCESS,
                )
            } catch (_: ProviderException) {
                return diagnostic(snapshot, ConfirmationDiagnostic.OPEN_OPERATIONAL_FAILURE)
            } catch (_: RuntimeException) {
                return diagnostic(snapshot, ConfirmationDiagnostic.OPEN_UNEXPECTED_FAILURE)
            }
        if (runAead.keyUri != snapshot.expected.canonicalAlias) {
            return diagnostic(snapshot, ConfirmationDiagnostic.OPEN_IDENTITY_MISMATCH)
        }
        val decrypted =
            try {
                runAead.decryptKeyConfirmation(ciphertext, snapshot.expected)
            } catch (_: RuntimeException) {
                return diagnostic(snapshot, ConfirmationDiagnostic.DECRYPT_UNEXPECTED_FAILURE)
            }
        return when (decrypted) {
            is KeyConfirmationDecryption.Success ->
                ConfirmationResult.Validated(decrypted.value, snapshot.evidence)
            is KeyConfirmationDecryption.PlaintextContractFailure ->
                rejected(
                    snapshot,
                    KeyRecoveryClassification.CORRUPT_KEY_CONFIRMATION,
                    ConfirmationPhase.PLAINTEXT_CONTRACT,
                )
            is KeyConfirmationDecryption.DecryptFailure ->
                decryptFailure(snapshot, row, decrypted.signal)
        }
    }

    private fun decryptFailure(
        snapshot: KeyConfirmationSnapshot,
        row: StoredKeyConfirmationIdentity,
        signal: RecoveryDecryptFailureSignal,
    ): ConfirmationResult =
        when (signal) {
            RecoveryDecryptFailureSignal.OPERATIONAL ->
                diagnostic(snapshot, ConfirmationDiagnostic.DECRYPT_OPERATIONAL_FAILURE)
            RecoveryDecryptFailureSignal.UNKNOWN ->
                diagnostic(snapshot, ConfirmationDiagnostic.DECRYPT_UNKNOWN_FAILURE)
            RecoveryDecryptFailureSignal.AUTHENTICATION_REJECTED ->
                classifyAuthenticationFailure(snapshot, row)
        }

    private fun classifyAuthenticationFailure(
        snapshot: KeyConfirmationSnapshot,
        row: StoredKeyConfirmationIdentity,
    ): ConfirmationResult {
        val receipt = snapshot.controlledReplacement
        val provenanceMatches =
            receipt?.let {
                it.eventId.isNotBlank() &&
                    it.canonicalAlias == snapshot.expected.canonicalAlias &&
                    it.preservedIdentity == row
            } == true
        if (!provenanceMatches) {
            // KCF-04/KCF-05 are not observed KEY-04 events. Their campaign routing is outside this
            // slice.
            return diagnostic(
                snapshot,
                ConfirmationDiagnostic.AUTHENTICATION_WITHOUT_KEY04_PROVENANCE,
            )
        }
        val classification =
            KeyConfirmationRouting.classifyKey04(
                Key04Observation(
                    durableRunRowExists = true,
                    confirmationFinalExists = true,
                    pathTypeLengthAndSha256Match = true,
                    approvedAliasExistsAndIsAccessible = true,
                    exactActiveProtocolAadComputed = true,
                    underlyingAliasKeyReplacedWithCiphertextIdentityPreserved = true,
                    recoveryCreatedOrReplacedKey = false,
                    decryptOutcome = KeyConfirmationDecryptOutcome.AUTHENTICATION_OR_AAD_FAILURE,
                )
            )
        return rejected(snapshot, classification, ConfirmationPhase.KEY04)
    }

    private fun rejected(
        snapshot: KeyConfirmationSnapshot,
        classification: KeyRecoveryClassification,
        phase: ConfirmationPhase,
    ): ConfirmationResult = ConfirmationResult.Rejected(classification, phase, snapshot.evidence)

    private fun diagnostic(
        snapshot: KeyConfirmationSnapshot,
        diagnostic: ConfirmationDiagnostic,
    ): ConfirmationResult = ConfirmationResult.Unclassified(diagnostic, snapshot.evidence)

    private companion object {
        const val FINAL_RELATIVE_NAME = "key-confirmation/run.kc"
    }
}
