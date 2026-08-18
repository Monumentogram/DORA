package com.monumentogram.dora.poc.recovery.crypto

import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryCheckpoint
import com.monumentogram.dora.poc.recovery.contract.RecoveryCheckpointCodec
import com.monumentogram.dora.poc.recovery.contract.RecoveryContractException
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifest
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifestCodec
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingMath
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import java.security.GeneralSecurityException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryAeadCryptoTest {
    @Test
    fun `microfile AEAD has exact 160033 byte geometry and authenticates typed AAD`() {
        val keyset =
            RecoveryCryptoTestFixtures.preparedAeadKeyset(
                RecoveryCryptoTestFixtures.microfileEnvelopeAad()
            )
        val plaintext = ByteArray(160_000) { (it % 251).toByte() }
        val aad = RecoveryCryptoTestFixtures.microfileAad()

        val ciphertext = keyset.encryptMicrofile(plaintext, aad)

        assertEquals(160_033, ciphertext.size)
        assertArrayEquals(plaintext, keyset.decryptMicrofile(ciphertext, aad))
        assertThrows(RecoveryContractException::class.java) {
            keyset.decryptMicrofile(
                ciphertext,
                RecoveryCryptoTestFixtures.microfileAad(RecoveryCryptoTestFixtures.OTHER_RUN_ID),
            )
        }
    }

    @Test
    fun `microfile AEAD is randomized and rejects tamper`() {
        val keyset =
            RecoveryCryptoTestFixtures.preparedAeadKeyset(
                RecoveryCryptoTestFixtures.microfileEnvelopeAad()
            )
        val secondKeyset =
            RecoveryCryptoTestFixtures.preparedAeadKeyset(
                RecoveryCryptoTestFixtures.microfileEnvelopeAad()
            )
        val plaintext = ByteArray(160_000) { (it % 223).toByte() }
        val aad = RecoveryCryptoTestFixtures.microfileAad()
        val first = keyset.encryptMicrofile(plaintext, aad)
        val second = secondKeyset.encryptMicrofile(plaintext, aad)
        val tampered = first.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }

        assertFalse(first.contentEquals(second))
        assertThrows(RecoveryContractException::class.java) {
            keyset.encryptMicrofile(plaintext, aad)
        }
        assertThrows(GeneralSecurityException::class.java) {
            keyset.decryptMicrofile(tampered, aad)
        }
    }

    @Test
    fun `publication AEAD roundtrips the strict manifest codec and rejects wrong AAD`() {
        val keyset =
            RecoveryCryptoTestFixtures.preparedAeadKeyset(
                RecoveryCryptoTestFixtures.manifestEnvelopeAad()
            )
        val manifest =
            RecoveryManifest.create(
                candidate = RecoveryCandidate.MICROFILE,
                runId = RecoveryCryptoTestFixtures.RUN_ID,
                generation = 1UL,
                previousManifestCiphertextSha256 = Sha256Value.ZERO,
                committedEndExclusive = 0UL,
                entries = emptyList(),
            )
        val plaintext = RecoveryManifestCodec.encode(manifest)
        val aad = RecoveryCryptoTestFixtures.emptyManifestPublicationAad()
        val ciphertext = keyset.encryptPublication(plaintext, aad)

        val recovered = RecoveryManifestCodec.decode(keyset.decryptPublication(ciphertext, aad))

        assertEquals(manifest, recovered)
        assertThrows(RecoveryContractException::class.java) {
            keyset.decryptPublication(
                ciphertext,
                RecoveryCryptoTestFixtures.emptyManifestPublicationAad(
                    RecoveryCryptoTestFixtures.OTHER_RUN_ID
                ),
            )
        }
        assertArrayEquals(plaintext, RecoveryManifestCodec.encode(recovered))
    }

    @Test
    fun `publication AEAD roundtrips the strict checkpoint codec`() {
        val keyset =
            RecoveryCryptoTestFixtures.preparedAeadKeyset(
                RecoveryCryptoTestFixtures.checkpointEnvelopeAad()
            )
        val checkpoint =
            RecoveryCheckpoint(
                candidate = RecoveryCandidate.STREAM,
                runId = RecoveryCryptoTestFixtures.RUN_ID,
                generation = 1UL,
                previousCheckpointCiphertextSha256 = Sha256Value.ZERO,
                durableNonFinalSegmentCount = 3UL,
                ciphertextPrefixBytes = RecoveryStreamingMath.ciphertextPrefixBytes(3UL),
                committedEndExclusive = RecoveryStreamingMath.recoveredEndExclusive(3UL),
                streamKeyEnvelopeBytes = 123UL,
                streamKeyEnvelopeSha256 = Sha256Value.fromBytes(ByteArray(32) { it.toByte() }),
                streamCiphertextRelativeName = "stream/stream.ct",
                streamKeyEnvelopeRelativeName = "key-envelopes/stream.ks",
            )
        val plaintext = RecoveryCheckpointCodec.encode(checkpoint)
        val aad = RecoveryCryptoTestFixtures.checkpointPublicationAad()
        val ciphertext = keyset.encryptPublication(plaintext, aad)

        val recovered = RecoveryCheckpointCodec.decode(keyset.decryptPublication(ciphertext, aad))

        assertEquals(checkpoint, recovered)
        assertThrows(GeneralSecurityException::class.java) {
            keyset.decryptPublication(ciphertext, aad.copy(terminalUnitIndex = 1UL))
        }
        assertArrayEquals(plaintext, RecoveryCheckpointCodec.encode(recovered))
    }
}
