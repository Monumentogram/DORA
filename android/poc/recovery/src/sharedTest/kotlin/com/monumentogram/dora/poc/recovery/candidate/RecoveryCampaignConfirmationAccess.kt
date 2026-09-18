package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.KeyRecoveryClassification
import com.monumentogram.dora.poc.recovery.controller.KeyConfirmationSnapshot

/** A failed source load does not provide an alias observation or a confirmation snapshot. */
internal sealed interface RecoveryCampaignConfirmationLoad {
    data class Loaded(val snapshot: KeyConfirmationSnapshot) : RecoveryCampaignConfirmationLoad

    data class MissingFinal(val sourceFailure: RecoverySourceAccessException) :
        RecoveryCampaignConfirmationLoad {
        val classification: KeyRecoveryClassification =
            KeyRecoveryClassification.KEY_CONFIRMATION_MISSING
    }
}

internal object RecoveryCampaignConfirmationAccess {
    fun load(source: () -> KeyConfirmationSnapshot): RecoveryCampaignConfirmationLoad =
        try {
            RecoveryCampaignConfirmationLoad.Loaded(source())
        } catch (failure: RecoverySourceAccessException) {
            if (!isMissingFinal(failure)) throw failure
            RecoveryCampaignConfirmationLoad.MissingFinal(failure)
        }

    @Suppress("ComplexCondition")
    private fun isMissingFinal(failure: RecoverySourceAccessException): Boolean {
        val context = failure.context ?: return false
        return failure.diagnostic.category == RecoveryFailureCategory.MISSING_ARTIFACT &&
            failure.diagnostic.stage == RecoveryFailureStage.ARTIFACT_PATH &&
            failure.diagnostic.artifactSizeLimit == null &&
            context.bootstrapRowState == RecoveryBootstrapRowState.PRESENT &&
            context.artifactContext == RecoveryArtifactContext.CONFIRMATION_FINAL &&
            context.relativeName == "key-confirmation/run.kc" &&
            context.artifactPresence == RecoveryArtifactPresence.ABSENT &&
            context.confirmationFinalPresence != RecoveryArtifactPresence.PRESENT
    }
}
