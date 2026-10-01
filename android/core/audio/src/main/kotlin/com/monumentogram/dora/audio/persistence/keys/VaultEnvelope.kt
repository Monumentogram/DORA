// The envelope boundary strips all provider exception details.
@file:Suppress("TooGenericExceptionCaught")

package com.monumentogram.dora.audio.persistence.keys

import com.google.crypto.tink.Aead
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException

internal enum class KeyFailure {
    TEMPORARILY_UNAVAILABLE,
    PERMANENTLY_MISSING_OR_INVALIDATED,
    AUTHENTICATION_FAILED,
    CORRUPT_CIPHERTEXT,
    NAMESPACE_OCCUPIED,
    INCOMPLETE_BOOTSTRAP,
    STORAGE_FAILURE,
}

internal class KeyBoundaryException(val failure: KeyFailure) :
    GeneralSecurityException(failure.name)

internal fun normalizeKeyFailure(error: Exception): KeyBoundaryException =
    when (error) {
        is KeyBoundaryException -> error
        is AEADBadTagException,
        is BadPaddingException -> KeyBoundaryException(KeyFailure.AUTHENTICATION_FAILED)
        else -> KeyBoundaryException(KeyFailure.TEMPORARILY_UNAVAILABLE)
    }

/**
 * No plaintext secret retained here. Borrowers may copy only for the bounded DB lifetime. Clearing
 * arrays is best effort: VM, JCA and native copies cannot be guaranteed erased.
 */
internal class VaultSecrets
internal constructor(
    val ownerId: String,
    val vaultId: String,
    val databaseObjectSelector: String,
    private val cipher: Aead,
    private val encryptedSecret: ByteArray,
    private val aad: ByteArray,
) {
    fun <T> borrowDatabaseSecret(block: (ByteArray) -> T): T {
        val secret =
            try {
                cipher.decrypt(encryptedSecret, aad)
            } catch (error: Exception) {
                throw normalizeKeyFailure(error)
            }
        try {
            if (secret.size != VaultEnvelope.SECRET_BYTES)
                throw KeyBoundaryException(KeyFailure.CORRUPT_CIPHERTEXT)
            return block(secret)
        } finally {
            secret.fill(0)
        }
    }
}

internal object VaultEnvelope {
    const val SECRET_BYTES = 32
    const val SELECTOR_BYTES = 32
    private const val OBJECT_SELECTOR_BYTES = 32
    private const val DIGEST_BYTES = 32
    private const val AEAD_OVERHEAD_BYTES = 28
    private const val IDENTITY_BYTES = 64
    private const val INT_BYTES = 4
    private const val HEX_BYTE_MASK = 0xff
    private const val MAGIC = 0x44564231
    private const val VERSION = 1
    private const val HEADER_BYTES = 8
    private const val SECRET_ENVELOPE_BYTES = 60
    private const val ROOT_PLAINTEXT_BYTES = 96
    const val BUNDLE_BYTES =
        HEADER_BYTES + SECRET_ENVELOPE_BYTES + ROOT_PLAINTEXT_BYTES + AEAD_OVERHEAD_BYTES

    fun seal(
        selector: ByteArray,
        owner: String,
        vault: String,
        secret: ByteArray,
        cipher: Aead,
    ): ByteArray {
        require(selector.size == SELECTOR_BYTES && secret.size == SECRET_BYTES)
        val ownerUuid = canonicalUuid(owner)
        val vaultUuid = canonicalUuid(vault)
        val objectId = ByteArray(OBJECT_SELECTOR_BYTES).also(SecureRandom()::nextBytes)
        val aad = secretAad(selector, ownerUuid, vaultUuid, objectId)
        val root = ByteBuffer.allocate(ROOT_PLAINTEXT_BYTES)
        try {
            val encrypted = cipher.encrypt(secret, aad)
            require(encrypted.size == SECRET_ENVELOPE_BYTES)
            root.putUuid(ownerUuid).putUuid(vaultUuid).put(objectId).put(digest(encrypted))
            val encryptedRoot = cipher.encrypt(root.array(), rootAad(selector))
            require(encryptedRoot.size == ROOT_PLAINTEXT_BYTES + AEAD_OVERHEAD_BYTES)
            return ByteBuffer.allocate(BUNDLE_BYTES)
                .putInt(MAGIC)
                .putInt(VERSION)
                .put(encrypted)
                .put(encryptedRoot)
                .array()
        } catch (error: Exception) {
            throw normalizeKeyFailure(error)
        } finally {
            root.array().fill(0)
            aad.fill(0)
            objectId.fill(0)
        }
    }

    fun open(selector: ByteArray, bundle: ByteArray, cipher: Aead): VaultSecrets {
        if (selector.size != SELECTOR_BYTES || bundle.size != BUNDLE_BYTES) corrupt()
        val frame = ByteBuffer.wrap(bundle)
        if (frame.int != MAGIC || frame.int != VERSION) corrupt()
        val encryptedSecret = ByteArray(SECRET_ENVELOPE_BYTES).also(frame::get)
        val encryptedRoot = ByteArray(ROOT_PLAINTEXT_BYTES + AEAD_OVERHEAD_BYTES).also(frame::get)
        val root =
            try {
                cipher.decrypt(encryptedRoot, rootAad(selector))
            } catch (error: Exception) {
                throw normalizeKeyFailure(error)
            }
        try {
            if (root.size != ROOT_PLAINTEXT_BYTES) corrupt()
            val value = ByteBuffer.wrap(root)
            val owner = value.uuid()
            val vault = value.uuid()
            val objectId = ByteArray(OBJECT_SELECTOR_BYTES).also(value::get)
            val expectedHash = ByteArray(DIGEST_BYTES).also(value::get)
            if (!MessageDigest.isEqual(expectedHash, digest(encryptedSecret))) corrupt()
            val opened =
                VaultSecrets(
                    owner.toString(),
                    vault.toString(),
                    objectId.joinToString("") { "%02x".format(it.toInt() and HEX_BYTE_MASK) },
                    cipher,
                    encryptedSecret,
                    secretAad(selector, owner, vault, objectId),
                )
            opened.borrowDatabaseSecret {
                /* Authenticate the exact inner before releasing identity. */
            }
            return opened
        } finally {
            root.fill(0)
        }
    }

    private fun rootAad(selector: ByteArray) = aad("identity-root", selector, ByteArray(0))

    private fun secretAad(
        selector: ByteArray,
        owner: UUID,
        vault: UUID,
        objectId: ByteArray,
    ): ByteArray {
        val identity =
            ByteBuffer.allocate(IDENTITY_BYTES).putUuid(owner).putUuid(vault).put(objectId).array()
        return try {
            aad("database-secret", selector, identity)
        } finally {
            identity.fill(0)
        }
    }

    private fun aad(role: String, selector: ByteArray, identity: ByteArray): ByteArray {
        val domain =
            "DORA/product-vault-envelope/session:not-applicable/$role"
                .toByteArray(Charsets.US_ASCII)
        return ByteBuffer.allocate(
                INT_BYTES + INT_BYTES + domain.size + SELECTOR_BYTES + identity.size
            )
            .putInt(VERSION)
            .putInt(domain.size)
            .put(domain)
            .put(selector)
            .put(identity)
            .array()
    }

    private fun canonicalUuid(value: String): UUID =
        UUID.fromString(value).also { require(it.toString() == value) }

    private fun ByteBuffer.putUuid(value: UUID): ByteBuffer =
        putLong(value.mostSignificantBits).putLong(value.leastSignificantBits)

    private fun ByteBuffer.uuid() = UUID(long, long)

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun corrupt(): Nothing = throw KeyBoundaryException(KeyFailure.CORRUPT_CIPHERTEXT)
}
