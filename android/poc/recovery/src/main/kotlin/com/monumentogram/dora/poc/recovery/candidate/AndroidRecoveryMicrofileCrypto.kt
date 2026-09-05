package com.monumentogram.dora.poc.recovery.candidate

import com.google.crypto.tink.Aead
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAad
import com.monumentogram.dora.poc.recovery.contract.MicrofileAad
import com.monumentogram.dora.poc.recovery.contract.PublicationAad
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.crypto.RecoveryAeadKeyset
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAead
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadBackend
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import com.monumentogram.dora.poc.recovery.crypto.RecoveryTinkRuntime

/** Typed Tink/Android-Keystore adapter for the sequential candidate controller. */
internal class AndroidRecoveryMicrofileCrypto : RecoveryMicrofileCrypto {
    private val runProvider = RecoveryRunAeadProvider(AndroidExistingRunAeadBackend)

    override fun openRunAead(runId: RunId): RecoveryRunAead = runProvider.openExisting(runId)

    override fun createKeyset(aad: KeyEnvelopeAad, runAead: RecoveryRunAead): PreparedRecoveryAead {
        val keyset = RecoveryTinkRuntime.newAeadKeyset(aad)
        return PreparedRecoveryAead(keyset, keyset.serializeEncrypted(runAead))
    }

    override fun encryptMicrofile(
        keyset: RecoveryAeadKeyset,
        plaintext: ByteArray,
        aad: MicrofileAad,
    ): ByteArray = keyset.encryptMicrofile(plaintext, aad)

    override fun encryptManifest(
        keyset: RecoveryAeadKeyset,
        plaintext: ByteArray,
        aad: PublicationAad,
    ): ByteArray = keyset.encryptPublication(plaintext, aad)

    private object AndroidExistingRunAeadBackend : RecoveryRunAeadBackend {
        override fun generateNew(keyUri: String): Unit =
            error("Candidate publication cannot generate a run alias")

        override fun getAead(keyUri: String): Aead =
            AndroidKeystoreKmsClient.Builder().setKeyUri(keyUri).build().getAead(keyUri)
    }
}
