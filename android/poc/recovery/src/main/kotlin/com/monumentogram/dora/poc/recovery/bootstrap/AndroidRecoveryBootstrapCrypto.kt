package com.monumentogram.dora.poc.recovery.bootstrap

import com.monumentogram.dora.poc.recovery.contract.CanonicalRecoveryAlias
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAead
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import java.security.KeyStore

/** Android Keystore adapter for the staged KC01–KC04 typed-crypto boundary. */
internal class AndroidRecoveryBootstrapCrypto : RecoveryBootstrapCrypto {
    private val provider = RecoveryRunAeadProvider()

    override fun aliasExists(runId: RunId): Boolean {
        val alias = CanonicalRecoveryAlias.forRun(runId).removePrefix(ANDROID_KEYSTORE_URI_PREFIX)
        return KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).run {
            load(null)
            containsAlias(alias)
        }
    }

    override fun createNewAlias(runId: RunId): RecoveryRunAead = provider.createNew(runId)

    override fun consumeCreatedAlias(created: RecoveryRunAead): RecoveryRunAead = created

    override fun encryptConfirmation(
        runAead: RecoveryRunAead,
        value: KeyConfirmationValue,
    ): ByteArray = runAead.encryptKeyConfirmation(value)

    private companion object {
        const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val ANDROID_KEYSTORE_URI_PREFIX = "android-keystore://"
    }
}
