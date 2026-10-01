package com.monumentogram.dora.audio.persistence.keys

import com.google.crypto.tink.Aead
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultEnvelopeTest {
    private val selector = ByteArray(32) { it.toByte() }
    private val secret = ByteArray(32) { (it + 42).toByte() }
    private val owner = "12345678-1234-4234-9234-123456789012"
    private val vault = "23456789-2345-4345-9345-234567890123"
    private val cipher = TestAead()

    @Test
    fun roundTripKeepsIdentityConfidentialAndBorrowsSecretOnlyUntilReturn() {
        val bundle = VaultEnvelope.seal(selector, owner, vault, secret, cipher)
        assertFalse(String(bundle, Charsets.ISO_8859_1).contains(owner))
        assertFalse(String(bundle, Charsets.ISO_8859_1).contains(vault))
        val opened = VaultEnvelope.open(selector, bundle, cipher)
        assertEquals(owner, opened.ownerId)
        assertEquals(vault, opened.vaultId)
        lateinit var borrowed: ByteArray
        opened.borrowDatabaseSecret {
            borrowed = it
            assertArrayEquals(secret, it)
        }
        assertArrayEquals(ByteArray(32), borrowed)
    }

    @Test
    fun secretIsWipedEvenWhenConsumerThrows() {
        val opened =
            VaultEnvelope.open(
                selector,
                VaultEnvelope.seal(selector, owner, vault, secret, cipher),
                cipher,
            )
        lateinit var borrowed: ByteArray
        assertThrows(IllegalStateException::class.java) {
            opened.borrowDatabaseSecret<Unit> {
                borrowed = it
                error("synthetic consumer failure")
            }
        }
        assertArrayEquals(ByteArray(32), borrowed)
    }

    @Test
    fun selectorSubstitutionAndCiphertextTamperAreRejected() {
        val bundle = VaultEnvelope.seal(selector, owner, vault, secret, cipher)
        assertThrows(KeyBoundaryException::class.java) {
            VaultEnvelope.open(ByteArray(32), bundle, cipher)
        }
        bundle[bundle.lastIndex] = (bundle.last().toInt() xor 1).toByte()
        assertThrows(KeyBoundaryException::class.java) {
            VaultEnvelope.open(selector, bundle, cipher)
        }
    }

    @Test
    fun malformedFramingAndTrailingBytesAreRejectedBeforeDecryption() {
        val bundle = VaultEnvelope.seal(selector, owner, vault, secret, cipher)
        for (bad in listOf(byteArrayOf(), bundle.copyOf(10), bundle + 0, ByteArray(4097))) {
            assertThrows(KeyBoundaryException::class.java) {
                VaultEnvelope.open(selector, bad, cipher)
            }
        }
    }

    @Test
    fun swappingAnIndependentlyValidSecretEnvelopeFailsExactPairBinding() {
        val first = VaultEnvelope.seal(selector, owner, vault, secret, cipher)
        val second = VaultEnvelope.seal(selector, owner, vault, ByteArray(32) { 99 }, cipher)
        // Fixed bounded framing: 8-byte header followed by fixed 60-byte DB envelope.
        second.copyInto(first, 8, 8, 68)
        assertThrows(KeyBoundaryException::class.java) {
            VaultEnvelope.open(selector, first, cipher)
        }
    }

    @Test
    fun safeExceptionNeverRetainsProviderMessagesOrCauses() {
        val normalized =
            normalizeKeyFailure(java.security.ProviderException("synthetic secret alias path"))
        assertEquals(KeyFailure.TEMPORARILY_UNAVAILABLE, normalized.failure)
        assertNull(normalized.cause)
        assertEquals("TEMPORARILY_UNAVAILABLE", normalized.message)
        assertEquals(
            KeyFailure.AUTHENTICATION_FAILED,
            normalizeKeyFailure(javax.crypto.AEADBadTagException("canary")).failure,
        )
    }
}

internal class TestAead : Aead {
    private val key = SecretKeySpec(ByteArray(32).also(SecureRandom()::nextBytes), "AES")

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData)
        return cipher.iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, ciphertext, 0, 12))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext, 12, ciphertext.size - 12)
    }
}
