package com.monumentogram.dora.poc.recovery.candidate

import android.content.Context
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.controller.RecoveryKeyConfirmationController
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryQuarantineJournal
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryReconciliationSource
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryReconciliationStorage

/** Real reconciliation with observers around the quarantine persistence and filesystem returns. */
internal object RecoveryCampaignMicrofileRecovery {
    fun reconcile(
        context: Context,
        runId: RunId,
        observer: (String) -> Unit = {},
    ): MicrofileReconciliationResult {
        val storage = AndroidOsRecoveryReconciliationStorage(context)
        return RecoveryMicrofileReconciliationController(
                AndroidRecoveryReconciliationSource.withStorage(context, storage),
                AndroidRecoveryMicrofileCrypto(),
                RecoveryKeyConfirmationController(),
                quarantineController(context, observer),
            )
            .reconcile(runId)
    }

    fun quarantineController(
        context: Context,
        observer: (String) -> Unit = {},
    ): RecoveryQuarantineController {
        val storage = AndroidOsRecoveryReconciliationStorage(context)
        val journal = AndroidRecoveryQuarantineJournal(context)
        val observingJournal =
            object : RecoveryQuarantineJournal by journal {
                override fun beginNonExclusive(): RecoveryQuarantineTransaction {
                    val transaction = journal.beginNonExclusive()
                    var boundary: String? = null
                    var successful = false
                    return object : RecoveryQuarantineTransaction by transaction {
                        override fun insert(row: RecoveryQuarantineIntentRow) {
                            transaction.insert(row)
                            boundary = "Q01"
                        }

                        override fun complete(intentId: Sha256Value) {
                            transaction.complete(intentId)
                            boundary = "Q05"
                        }

                        override fun markSuccessful() {
                            transaction.markSuccessful()
                            successful = true
                        }

                        override fun end() {
                            transaction.end()
                            if (successful) observer(requireNotNull(boundary))
                        }
                    }
                }
            }
        val observingStorage =
            object : RecoveryQuarantineStorage by storage {
                override fun renameNoOverwrite(row: RecoveryQuarantineIntentRow) {
                    storage.renameNoOverwrite(row)
                    observer("Q02")
                }

                override fun fsyncSourceParent(row: RecoveryQuarantineIntentRow) {
                    storage.fsyncSourceParent(row)
                    observer("Q03")
                }

                override fun fsyncDestinationParent(row: RecoveryQuarantineIntentRow) {
                    storage.fsyncDestinationParent(row)
                    observer("Q04")
                }
            }
        return RecoveryQuarantineController(
            observingStorage,
            observingJournal,
            RecoveryQuarantineEvidenceSink { observer("EVIDENCE") },
        )
    }
}
