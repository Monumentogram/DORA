package com.monumentogram.dora.poc.recovery.candidate

import android.content.Context
import com.monumentogram.dora.poc.recovery.controller.RecoveryKeyConfirmationController
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryQuarantineJournal
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryReconciliationSource
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryReconciliationStorage

/**
 * Minimal Android composition boundary. Production snapshot acquisition is owned internally by the
 * journal and descriptor-backed source.
 */
internal object AndroidRecoveryMicrofileReconciliation {
    fun create(
        context: Context,
        evidence: RecoveryQuarantineEvidenceSink,
    ): RecoveryMicrofileReconciliationController {
        val storage = AndroidOsRecoveryReconciliationStorage(context)
        val quarantine =
            RecoveryQuarantineController(
                storage,
                AndroidRecoveryQuarantineJournal(context),
                evidence,
            )
        return RecoveryMicrofileReconciliationController(
            source = AndroidRecoveryReconciliationSource.withStorage(context, storage),
            crypto = AndroidRecoveryMicrofileCrypto(),
            confirmationController = RecoveryKeyConfirmationController(),
            quarantineController = quarantine,
        )
    }
}
