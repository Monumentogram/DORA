package com.monumentogram.dora.audio.persistence.keys

import com.google.crypto.tink.Aead
import java.security.SecureRandom
import java.util.UUID

internal sealed interface KeyAccess<out T> {
    class Available<T>(val value: T, val protection: KeyProtection) : KeyAccess<T>

    class Unavailable(val failure: KeyFailure) : KeyAccess<Nothing>
}

internal enum class KeyProtection {
    STRONGBOX,
    TRUSTED_ENVIRONMENT,
    HARDWARE_BACKED_UNSPECIFIED,
    SOFTWARE,
}

internal class ConfirmedVaultKey(val aead: Aead, val protection: KeyProtection)

internal interface VaultKeyBackend {
    fun createNew(selector: ByteArray): ConfirmedVaultKey

    fun openExisting(selector: ByteArray): ConfirmedVaultKey
}

internal interface VaultBundleStorage {
    fun reserve(selector: ByteArray)

    fun readSelector(): ByteArray

    fun persist(bundle: ByteArray)

    fun readBundle(): ByteArray
}

internal class VaultSecretStore(
    private val storage: VaultBundleStorage,
    private val keys: VaultKeyBackend,
) {
    fun createNew(): KeyAccess<VaultSecrets> = access {
        val selector = ByteArray(VaultEnvelope.SELECTOR_BYTES).also(SecureRandom()::nextBytes)
        storage.reserve(selector) // Durable namespace fence precedes any possible key generation.
        val key = keys.createNew(selector)
        val secret = ByteArray(VaultEnvelope.SECRET_BYTES).also(SecureRandom()::nextBytes)
        try {
            val bundle =
                VaultEnvelope.seal(
                    selector,
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    secret,
                    key.aead,
                )
            storage.persist(bundle)
            val readback = storage.readBundle()
            if (!bundle.contentEquals(readback) || !selector.contentEquals(storage.readSelector()))
                throw KeyBoundaryException(KeyFailure.CORRUPT_CIPHERTEXT)
            KeyAccess.Available(VaultEnvelope.open(selector, readback, key.aead), key.protection)
        } finally {
            secret.fill(0)
        }
    }

    fun openExisting(): KeyAccess<VaultSecrets> = access {
        val selector = storage.readSelector()
        val bundle = storage.readBundle()
        val key = keys.openExisting(selector) // This path has no generation operation.
        KeyAccess.Available(VaultEnvelope.open(selector, bundle, key.aead), key.protection)
    }

    @Suppress("TooGenericExceptionCaught") // No provider/storage detail crosses this boundary.
    private fun access(block: () -> KeyAccess<VaultSecrets>): KeyAccess<VaultSecrets> =
        try {
            block()
        } catch (error: Exception) {
            KeyAccess.Unavailable(normalizeKeyFailure(error).failure)
        }
}
