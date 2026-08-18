package com.monumentogram.dora.poc.recovery.crypto

import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryContractException
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingMath
import com.monumentogram.dora.poc.recovery.contract.StreamingAad
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryStreamingCryptoTest {
    @Test
    fun `streaming AEAD roundtrip has the exact two-segment geometry`() {
        val keyset = RecoveryCryptoTestFixtures.preparedStreamingKeyset()
        val plaintext =
            ByteArray(
                RecoveryStreamingMath.FIRST_REQUESTED_PLAINTEXT_READ_BYTES +
                    RecoveryStreamingMath.LATER_REQUESTED_PLAINTEXT_READ_BYTES
            ) {
                (it % 251).toByte()
            }

        val ciphertext = encrypt(keyset, plaintext, aad())

        assertEquals(
            RecoveryStreamingMath.ciphertextPrefixBytes(2UL).toInt(),
            ciphertext.size,
        )
        assertArrayEquals(plaintext, decrypt(keyset, ciphertext, aad()))
    }

    @Test
    fun `streaming ciphertext is randomized and exact AAD bound`() {
        val keyset = RecoveryCryptoTestFixtures.preparedStreamingKeyset()
        val secondKeyset = RecoveryCryptoTestFixtures.preparedStreamingKeyset()
        val plaintext = ByteArray(8_136) { (it % 199).toByte() }
        val first = encrypt(keyset, plaintext, aad())
        val second = encrypt(secondKeyset, plaintext, aad())

        assertFalse(first.contentEquals(second))
        assertThrows(RecoveryContractException::class.java) {
            encrypt(keyset, plaintext, aad())
        }
        assertThrows(RecoveryContractException::class.java) {
            decrypt(
                keyset,
                first,
                StreamingAad(RecoveryCandidate.STREAM, RecoveryCryptoTestFixtures.OTHER_RUN_ID),
            )
        }
    }

    @Test
    fun `streaming tamper and truncation are rejected`() {
        val keyset = RecoveryCryptoTestFixtures.preparedStreamingKeyset()
        val ciphertext = encrypt(keyset, ByteArray(8_137) { (it % 181).toByte() }, aad())
        val tampered =
            ciphertext.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        val truncated = ciphertext.copyOf(ciphertext.size - 1)

        assertThrows(Exception::class.java) { decrypt(keyset, tampered, aad()) }
        assertThrows(Exception::class.java) { decrypt(keyset, truncated, aad()) }
    }

    private fun aad(): StreamingAad =
        StreamingAad(RecoveryCandidate.STREAM, RecoveryCryptoTestFixtures.RUN_ID)

    private fun encrypt(
        keyset: RecoveryStreamingKeyset,
        plaintext: ByteArray,
        aad: StreamingAad,
    ): ByteArray {
        val destination = ByteArrayOutputStream()
        keyset.newEncryptingStream(destination, aad).use { it.write(plaintext) }
        return destination.toByteArray()
    }

    private fun decrypt(
        keyset: RecoveryStreamingKeyset,
        ciphertext: ByteArray,
        aad: StreamingAad,
    ): ByteArray =
        keyset.newDecryptingStream(ByteArrayInputStream(ciphertext), aad).use { it.readBytes() }
}
