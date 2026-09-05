package com.monumentogram.dora.poc.recovery.candidate

import android.content.Context
import com.monumentogram.dora.poc.recovery.controller.RecoveryKeyConfirmationController
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryQuarantineJournal
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryReconciliationStorage

/**
 * Minimal Android composition boundary; snapshot acquisition remains caller-owned PoC
 * orchestration.
 */
internal object AndroidRecoveryMicrofileReconciliation {
    fun create(
        context: Context,
        source: RecoveryReconciliationSource,
        evidence: RecoveryQuarantineEvidenceSink,
    ): RecoveryMicrofileReconciliationController {
        val quarantine =
            RecoveryQuarantineController(
                AndroidOsRecoveryReconciliationStorage(context),
                AndroidRecoveryQuarantineJournal(context),
                evidence,
            )
        return RecoveryMicrofileReconciliationController(
            source = source,
            crypto = AndroidRecoveryMicrofileCrypto(),
            confirmationController = RecoveryKeyConfirmationController(),
            quarantineController = quarantine,
        )
    }
}
