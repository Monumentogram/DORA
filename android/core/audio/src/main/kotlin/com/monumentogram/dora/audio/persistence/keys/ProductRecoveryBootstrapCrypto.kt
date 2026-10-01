package com.monumentogram.dora.audio.persistence.keys

import com.monumentogram.dora.poc.recovery.bootstrap.BootstrapAliasCreation
import com.monumentogram.dora.poc.recovery.bootstrap.RecoveryBootstrapCrypto
import com.monumentogram.dora.poc.recovery.bootstrap.WitnessedRecoveryRunAeadCreator
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAead

internal class ProductRecoveryBootstrapCrypto(private val backend: NoLogRecoveryRunAeadBackend) :
    RecoveryBootstrapCrypto {
    private val creator = WitnessedRecoveryRunAeadCreator(backend)

    override fun aliasExists(runId: RunId): Boolean = backend.aliasExists(runId)

    override fun createNewAlias(runId: RunId): BootstrapAliasCreation = creator.createNew(runId)

    override fun encryptConfirmation(
        runAead: RecoveryRunAead,
        value: KeyConfirmationValue,
    ): ByteArray = runAead.encryptKeyConfirmation(value)
}
