package com.monumentogram.dora.poc.recovery.bootstrap

import android.content.Context
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryRunBootstrapJournal
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryBootstrapStorage

/** Wires the PoC-only Android adapters without creating a production application edge. */
internal object AndroidRecoveryKeyBootstrap {
    fun controller(
        context: Context,
        evidenceSink: BootstrapEvidenceSink,
    ): RecoveryKeyBootstrapController =
        RecoveryKeyBootstrapController(
            crypto = AndroidRecoveryBootstrapCrypto(),
            storage = AndroidOsRecoveryBootstrapStorage(context.applicationContext),
            journal = AndroidRecoveryRunBootstrapJournal(context.applicationContext),
            evidenceSink = evidenceSink,
        )
}
