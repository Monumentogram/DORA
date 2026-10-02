// Provider failures are intentionally normalized without retaining sensitive causes.
@file:Suppress("TooGenericExceptionCaught")

package com.monumentogram.dora.audio.persistence.keys

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.security.keystore.UserNotAuthenticatedException
import com.google.crypto.tink.Aead
import com.monumentogram.dora.audio.PersistenceLatency
import com.monumentogram.dora.poc.recovery.contract.CanonicalRecoveryAlias
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.crypto.RecoveryDecryptFailureSignal
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadBackend
import com.monumentogram.dora.poc.recovery.crypto.toRecoveryDecryptFailureSignal
import java.security.KeyStore
import java.security.MessageDigest
import java.security.ProviderException
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

internal object VaultKeyAlias {
    fun forSelector(selector: ByteArray): String {
        if (selector.size != VaultEnvelope.SELECTOR_BYTES)
            throw KeyBoundaryException(KeyFailure.CORRUPT_CIPHERTEXT)
        return "dora.vault.v1." +
            selector.joinToString("") { "%02x".format(it.toInt() and HEX_MASK) }
    }
}

internal class AndroidVaultKeyBackend(
    context: Context,
    operations: VaultKeystoreIo = AndroidVaultKeystoreIo,
) : VaultKeyBackend {
    private val keystore = NonExportableKeystore(context, operations)

    override fun createNew(selector: ByteArray): ConfirmedVaultKey {
        val alias = VaultKeyAlias.forSelector(selector)
        keystore.generate(alias)
        return keystore.open(alias)
    }

    override fun openExisting(selector: ByteArray): ConfirmedVaultKey =
        keystore.open(VaultKeyAlias.forSelector(selector))
}

internal class NoLogRecoveryRunAeadBackend(
    private val context: Context,
    authenticatedVaultId: String,
    private val operations: VaultKeystoreIo = AndroidVaultKeystoreIo,
) : RecoveryRunAeadBackend {
    // Root-envelope authentication is the caller's responsibility; reject noncanonical selectors.
    private val vaultId = RunId.fromCanonicalString(authenticatedVaultId).toCanonicalString()
    val failureScopes = RunKeyFailureScopes()

    override fun generateNew(keyUri: String) {
        val run = run(keyUri)
        PersistenceLatency.measure("key_generation") { keystore(run).generate(alias(run)) }
    }

    override fun getAead(keyUri: String): Aead {
        val run = run(keyUri)
        return PersistenceLatency.measure("key_open") { keystore(run).open(alias(run)).aead }
    }

    fun aliasExists(runId: RunId): Boolean =
        keystore(runId).exists(alias(runId)).also {
            if (!it) failureScopes.missing(runId)
        }

    /** Internal only: caller must first prove the durable scoped deletion fence. */
    fun removeAlias(runId: RunId) = keystore(runId).remove(alias(runId))

    private fun keystore(run: RunId) =
        NonExportableKeystore(
            context,
            operations,
            { error ->
                failureScopes.record(run, androidKeyFailure(error).failure)
                recoveryKeyFailure(error)
            },
        )

    private fun alias(run: RunId): String {
        val binding = "DORA/recovery-physical-alias/v1/$vaultId/${run.toCanonicalString()}"
        val digest =
            MessageDigest.getInstance("SHA-256").digest(binding.toByteArray(Charsets.US_ASCII))
        return "dora.vault.run.v1." +
            digest.joinToString("") { "%02x".format(it.toInt() and HEX_MASK) }
    }

    private fun run(uri: String): RunId {
        try {
            require(uri.startsWith(CanonicalRecoveryAlias.PREFIX)) { "INVALID_KEY_REFERENCE" }
            val run = RunId.fromCanonicalString(uri.removePrefix(CanonicalRecoveryAlias.PREFIX))
            require(uri == CanonicalRecoveryAlias.forRun(run)) { "INVALID_KEY_REFERENCE" }
            return run
        } catch (_: Exception) {
            throw KeyBoundaryException(KeyFailure.CORRUPT_CIPHERTEXT)
        }
    }
}

internal interface VaultKeystoreIo {
    fun cipher(): Cipher = Cipher.getInstance("AES/GCM/NoPadding")

    fun exists(alias: String): Boolean

    fun generate(alias: String, strongBox: Boolean)

    fun open(alias: String): SecretKey?

    fun information(key: SecretKey): KeyInfo

    fun remove(alias: String)
}

internal object AndroidVaultKeystoreIo : VaultKeystoreIo {
    override fun exists(alias: String) = store().containsAlias(alias)

    override fun generate(alias: String, strongBox: Boolean) {
        val spec =
            KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setKeySize(AES_KEY_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .setIsStrongBoxBacked(strongBox)
                .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(spec)
            generateKey()
        }
    }

    override fun open(alias: String) = store().getKey(alias, null) as? SecretKey

    override fun information(key: SecretKey) =
        SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
            .getKeySpec(key, KeyInfo::class.java) as KeyInfo

    override fun remove(alias: String) = store().deleteEntry(alias)

    private fun store(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}

/** No Tink AndroidKeystoreAead: its failure logger can expose aliases and provider details. */
private class NonExportableKeystore(
    private val context: Context,
    private val operations: VaultKeystoreIo,
    private val sanitizeFailure: (Exception) -> Exception = ::androidKeyFailure,
) {
    fun generate(alias: String) = guarded {
        synchronized(generationLock) {
            if (operations.exists(alias)) throw KeyBoundaryException(KeyFailure.NAMESPACE_OCCUPIED)
            if (
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            ) {
                try {
                    operations.generate(alias, true)
                } catch (_: StrongBoxUnavailableException) {
                    // An uncertain generation must never overwrite a possibly created alias.
                    if (operations.exists(alias))
                        throw KeyBoundaryException(KeyFailure.NAMESPACE_OCCUPIED)
                    operations.generate(alias, false)
                }
            } else {
                operations.generate(alias, false)
            }
        }
    }

    @Suppress("ComplexCondition") // Validate every persisted key property before confirmation.
    fun open(alias: String): ConfirmedVaultKey = guarded {
        if (!operations.exists(alias))
            throw KeyBoundaryException(KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED)
        val key =
            operations.open(alias)
                ?: throw KeyBoundaryException(KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED)
        val info = operations.information(key)
        key.encoded?.let { unexpectedExport ->
            unexpectedExport.fill(0)
            throw KeyBoundaryException(KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED)
        }
        if (
            info.keySize != AES_KEY_BITS ||
                info.isUserAuthenticationRequired ||
                info.blockModes.toSet() != setOf(KeyProperties.BLOCK_MODE_GCM) ||
                info.encryptionPaddings.toSet() != setOf(KeyProperties.ENCRYPTION_PADDING_NONE) ||
                info.purposes != (KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        ) {
            throw KeyBoundaryException(KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED)
        }
        val aead =
            NoLogKeystoreAead(key, operations::cipher) { error ->
                val sanitized = sanitizeFailure(error)
                if (error is AEADBadTagException || error is BadPaddingException)
                    AEADBadTagException("AUTHENTICATION_FAILED")
                else sanitized
            }
        confirm(aead)
        ConfirmedVaultKey(aead, protection(info))
    }

    fun exists(alias: String): Boolean = guarded { operations.exists(alias) }

    fun remove(alias: String) = guarded {
        synchronized(generationLock) {
            if (operations.exists(alias)) operations.remove(alias)
            if (operations.exists(alias))
                throw KeyBoundaryException(KeyFailure.TEMPORARILY_UNAVAILABLE)
        }
    }

    private fun confirm(aead: Aead) {
        val challenge = ByteArray(CONFIRMATION_BYTES).also(SecureRandom()::nextBytes)
        val aad = "DORA/keystore-roundtrip/v1".toByteArray(Charsets.US_ASCII)
        try {
            val encrypted = aead.encrypt(challenge, aad)
            val actual = aead.decrypt(encrypted, aad)
            try {
                if (!MessageDigest.isEqual(challenge, actual))
                    throw KeyBoundaryException(KeyFailure.AUTHENTICATION_FAILED)
            } finally {
                actual.fill(0)
            }
        } finally {
            challenge.fill(0)
            aad.fill(0)
        }
    }

    @Suppress("DEPRECATION")
    private fun protection(info: KeyInfo): KeyProtection =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeyProtection.STRONGBOX
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ->
                    KeyProtection.TRUSTED_ENVIRONMENT
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeyProtection.SOFTWARE
                else -> throw KeyBoundaryException(KeyFailure.TEMPORARILY_UNAVAILABLE)
            }
        } else if (info.isInsideSecureHardware) KeyProtection.HARDWARE_BACKED_UNSPECIFIED
        else KeyProtection.SOFTWARE

    private fun <T> guarded(block: () -> T): T =
        try {
            block()
        } catch (error: Exception) {
            throw sanitizeFailure(error)
        }

    private companion object {
        val generationLock = Any()
    }
}

private class NoLogKeystoreAead(
    private val key: SecretKey,
    private val newCipher: () -> Cipher,
    private val sanitizeFailure: (Exception) -> Exception,
) : Aead {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray = crypto {
        val cipher = newCipher()
        cipher.init(
            Cipher.ENCRYPT_MODE,
            key,
        ) // Provider owns nonce generation; never accept a caller IV.
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        if (
            cipher.iv.size != GCM_NONCE_BYTES || ciphertext.size != plaintext.size + GCM_TAG_BYTES
        ) {
            throw KeyBoundaryException(KeyFailure.TEMPORARILY_UNAVAILABLE)
        }
        cipher.iv + ciphertext
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray = crypto {
        if (ciphertext.size < GCM_OVERHEAD_BYTES)
            throw KeyBoundaryException(KeyFailure.CORRUPT_CIPHERTEXT)
        val cipher = newCipher()
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_BITS, ciphertext, 0, GCM_NONCE_BYTES),
        )
        cipher.updateAAD(associatedData)
        cipher.doFinal(ciphertext, GCM_NONCE_BYTES, ciphertext.size - GCM_NONCE_BYTES)
    }

    private fun <T> crypto(block: () -> T): T =
        try {
            block()
        } catch (error: Exception) {
            throw sanitizeFailure(error)
        }
}

/** Preserve the accepted classifier's signal before destroying provider details. */
private fun recoveryKeyFailure(error: Exception): Exception =
    when (error.toRecoveryDecryptFailureSignal()) {
        RecoveryDecryptFailureSignal.AUTHENTICATION_REJECTED ->
            AEADBadTagException("AUTHENTICATION_FAILED")
        RecoveryDecryptFailureSignal.OPERATIONAL -> ProviderException("TEMPORARILY_UNAVAILABLE")
        RecoveryDecryptFailureSignal.UNKNOWN -> {
            val normalized = androidKeyFailure(error)
            when (normalized.failure) {
                KeyFailure.TEMPORARILY_UNAVAILABLE,
                KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED,
                KeyFailure.AUTHENTICATION_FAILED -> ProviderException(normalized.failure.name)
                else -> normalized
            }
        }
    }

@Suppress("ReturnCount") // Bounded cause traversal returns only sanitized categories.
private fun androidKeyFailure(error: Exception): KeyBoundaryException {
    var current: Throwable? = error
    repeat(MAX_CAUSE_DEPTH) {
        val observed = current
        when (observed) {
            is KeyBoundaryException -> return KeyBoundaryException(observed.failure)
            is KeyPermanentlyInvalidatedException,
            is UnrecoverableKeyException ->
                return KeyBoundaryException(KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED)
            is UserNotAuthenticatedException,
            is AEADBadTagException,
            is BadPaddingException -> return KeyBoundaryException(KeyFailure.AUTHENTICATION_FAILED)
        }
        current = observed?.cause
    }
    return normalizeKeyFailure(error)
}

private const val AES_KEY_BITS = 256
private const val GCM_NONCE_BYTES = 12
private const val GCM_TAG_BYTES = 16
private const val GCM_TAG_BITS = 128
private const val GCM_OVERHEAD_BYTES = GCM_NONCE_BYTES + GCM_TAG_BYTES
private const val CONFIRMATION_BYTES = 32
private const val MAX_CAUSE_DEPTH = 16
private const val HEX_MASK = 0xff
