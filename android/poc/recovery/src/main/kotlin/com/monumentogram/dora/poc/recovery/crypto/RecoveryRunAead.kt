package com.monumentogram.dora.poc.recovery.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient
import com.monumentogram.dora.poc.recovery.contract.RunId

/**
 * The only route to the Android Keystore run AEAD.
 *
 * Creation and opening are deliberately separate. The open path has no generation fallback.
 */
internal class RecoveryRunAeadProvider(
    private val backend: RecoveryRunAeadBackend = AndroidKeystoreRecoveryRunAeadBackend
) {
    fun createNew(runId: RunId): RecoveryRunAead = RecoveryRunAead.createNew(runId, backend)

    fun openExisting(runId: RunId): RecoveryRunAead = RecoveryRunAead.openExisting(runId, backend)
}

internal interface RecoveryRunAeadBackend {
    fun generateNew(keyUri: String)

    fun getAead(keyUri: String): Aead
}

private object AndroidKeystoreRecoveryRunAeadBackend : RecoveryRunAeadBackend {
    override fun generateNew(keyUri: String) {
        AndroidKeystoreKmsClient.generateNewAeadKey(keyUri)
    }

    override fun getAead(keyUri: String): Aead =
        AndroidKeystoreKmsClient.Builder().setKeyUri(keyUri).build().getAead(keyUri)
}
