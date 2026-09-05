package com.monumentogram.dora.poc.recovery.bootstrap

import com.google.crypto.tink.Aead
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient
import com.monumentogram.dora.poc.recovery.contract.CanonicalRecoveryAlias
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAead
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadBackend
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import java.security.KeyStore

/** Android Keystore adapter for the staged KC01–KC04 typed-crypto boundary. */
internal class AndroidRecoveryBootstrapCrypto : RecoveryBootstrapCrypto {
    private val creator = WitnessedRecoveryRunAeadCreator(AndroidBootstrapRunAeadBackend)

    override fun aliasExists(runId: RunId): Boolean {
        val alias = CanonicalRecoveryAlias.forRun(runId).removePrefix(ANDROID_KEYSTORE_URI_PREFIX)
        return KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).run {
            load(null)
            containsAlias(alias)
        }
    }

    override fun createNewAlias(runId: RunId): BootstrapAliasCreation = creator.createNew(runId)

    override fun encryptConfirmation(
        runAead: RecoveryRunAead,
        value: KeyConfirmationValue,
    ): ByteArray = runAead.encryptKeyConfirmation(value)

    private object AndroidBootstrapRunAeadBackend : RecoveryRunAeadBackend {
        override fun generateNew(keyUri: String) {
            AndroidKeystoreKmsClient.generateNewAeadKey(keyUri)
        }

        override fun getAead(keyUri: String): Aead =
            AndroidKeystoreKmsClient.Builder().setKeyUri(keyUri).build().getAead(keyUri)
    }

    private companion object {
        const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val ANDROID_KEYSTORE_URI_PREFIX = "android-keystore://"
    }
}

internal class WitnessedRecoveryRunAeadCreator(private val delegate: RecoveryRunAeadBackend) {
    @Suppress("TooGenericExceptionCaught")
    fun createNew(runId: RunId): BootstrapAliasCreation {
        var generationReturned = false
        val witnessingBackend =
            object : RecoveryRunAeadBackend {
                override fun generateNew(keyUri: String) {
                    delegate.generateNew(keyUri)
                    generationReturned = true
                }

                override fun getAead(keyUri: String): Aead = delegate.getAead(keyUri)
            }
        return try {
            BootstrapAliasCreation.Created(
                RecoveryRunAeadProvider(witnessingBackend).createNew(runId)
            )
        } catch (error: Throwable) {
            if (generationReturned) {
                BootstrapAliasCreation.GeneratedButOpenFailed(error)
            } else {
                throw error
            }
        }
    }
}
