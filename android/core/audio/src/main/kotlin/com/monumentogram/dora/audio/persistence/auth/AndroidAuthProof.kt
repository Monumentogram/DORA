package com.monumentogram.dora.audio.persistence.auth

import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal object AppLockAuthenticatorPolicy {
    fun combinedPrompt(api: Int): Boolean = api >= Build.VERSION_CODES.R

    // Constants are inlined on API28/29; only API30+ passes this mask to the platform prompt.
    @get:android.annotation.SuppressLint("InlinedApi")
    val combinedAuthenticators: Int
        get() =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
}

/** Separate disposable authentication keys. Never accesses any vault or recording alias. */
internal class AndroidAuthProof private constructor(private val id: String) : AutoCloseable {
    internal val recentAlias = "$PREFIX$id.recent"
    private val biometricAlias = "$PREFIX$id.biometric"
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private var legacyCipher: Cipher? = null

    /** A newly executed operation proves recent authentication, not prompt/nonce binding. */
    fun verifyRecentAuthentication(): Boolean =
        try {
            val cipher = cipher(recentAlias)
            cipher.doFinal(ByteArray(PROOF_BYTES)).fill(0)
            true
        } catch (_: Exception) {
            false
        }

    @Suppress("DEPRECATION")
    fun legacyBiometricObject(): BiometricPrompt.CryptoObject {
        generate(biometricAlias, -1)
        val operation = cipher(biometricAlias)
        legacyCipher = operation
        return BiometricPrompt.CryptoObject(operation)
    }

    fun finishLegacyBiometric(result: BiometricPrompt.AuthenticationResult): Boolean =
        try {
            val operation = legacyCipher
            legacyCipher = null
            if (operation == null || result.cryptoObject?.cipher !== operation) false
            else {
                operation.doFinal(ByteArray(PROOF_BYTES)).fill(0)
                true
            }
        } catch (_: Exception) {
            false
        }

    override fun close() {
        legacyCipher = null
        for (alias in listOf(recentAlias, biometricAlias)) {
            try {
                store.deleteEntry(alias)
            } catch (_: Exception) {
                /* Auth remains revoked. */
            }
        }
    }

    private fun cipher(alias: String): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, store.getKey(alias, null) as SecretKey)
        }

    @Suppress("DEPRECATION")
    private fun generate(alias: String, validity: Int) {
        val builder =
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT)
                .setKeySize(KEY_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && validity > 0) {
            builder.setUserAuthenticationParameters(
                validity,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        } else {
            builder.setUserAuthenticationValidityDurationSeconds(validity)
        }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(builder.build())
            generateKey()
        }
    }

    companion object {
        private const val PREFIX = "dora.applock.auth-only."
        private const val PROOF_BYTES = 32
        private const val KEY_BITS = 256

        @Suppress(
            "TooGenericExceptionCaught"
        ) // Clean the disposable alias for every provider failure.
        fun create(): AndroidAuthProof {
            val proof = AndroidAuthProof(UUID.randomUUID().toString())
            try {
                proof.generate(proof.recentAlias, 1)
                return proof
            } catch (failure: Exception) {
                proof.close()
                throw failure
            }
        }
    }
}
