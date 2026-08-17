package com.monumentogram.dora.poc.recovery.crypto

import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeTargetKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryContractException
import com.monumentogram.dora.poc.recovery.contract.StreamingAad
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryTinkRuntimeTest {
    @Test
    fun `parameter snapshot is the exact protocol suite`() {
        val snapshot = RecoveryTinkRuntime.parameterSnapshot()

        assertEquals(16, snapshot.streamingInputKeyBytes)
        assertEquals(16, snapshot.streamingDerivedKeyBytes)
        assertEquals("SHA256", snapshot.streamingHkdfHash)
        assertEquals(4_096, snapshot.streamingCiphertextSegmentBytes)
        assertEquals(32, snapshot.aeadKeyBytes)
        assertEquals(12, snapshot.aeadIvBytes)
        assertEquals(16, snapshot.aeadTagBytes)
        assertEquals("TINK", snapshot.aeadVariant)
    }

    @Test
    fun `generated keysets contain exactly one enabled random-ID primary`() {
        val streamingTopology =
            RecoveryTinkRuntime.newStreamingKeyset(Fixtures.streamEnvelopeAad()).topology()
        val aeadTopology =
            RecoveryTinkRuntime.newAeadKeyset(Fixtures.microfileEnvelopeAad()).topology()

        listOf(streamingTopology, aeadTopology).forEach { topology ->
            assertEquals(1, topology.entryCount)
            assertEquals(1, topology.enabledPrimaryCount)
            assertTrue(topology.primaryHasNonzeroId)
        }
    }

    @Test
    fun `encrypted streaming keyset roundtrips only with exact envelope AAD and run key`() {
        val backend = RecordingRunAeadBackend()
        val runAead = RecoveryRunAeadProvider(backend).createNew(Fixtures.RUN_ID)
        val aad = Fixtures.streamEnvelopeAad()
        val original = RecoveryTinkRuntime.newStreamingKeyset(aad)
        val envelope = original.serializeEncrypted(runAead)

        val parsed = RecoveryTinkRuntime.parseEncryptedStreamingKeyset(envelope, runAead, aad)
        val plaintext = ByteArray(8_136) { (it % 251).toByte() }
        assertArrayEquals(plaintext, decrypt(parsed, encrypt(original, plaintext)))

        val otherRunAead =
            RecoveryRunAeadProvider(RecordingRunAeadBackend()).createNew(Fixtures.RUN_ID)
        assertThrows(GeneralSecurityException::class.java) {
            RecoveryTinkRuntime.parseEncryptedStreamingKeyset(envelope, otherRunAead, aad)
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryTinkRuntime.parseEncryptedStreamingKeyset(
                envelope,
                runAead,
                Fixtures.streamEnvelopeAad(Fixtures.OTHER_RUN_ID),
            )
        }
        assertThrows(RecoveryContractException::class.java) { parsed.serializeEncrypted(runAead) }
    }

    @Test
    fun `fresh streaming keyset enforces exact span envelope-first and one-shot use`() {
        val aad = Fixtures.streamEnvelopeAad()
        val keyset = RecoveryTinkRuntime.newStreamingKeyset(aad)
        val runAead = RecoveryRunAeadProvider(RecordingRunAeadBackend()).createNew(Fixtures.RUN_ID)
        val plaintext = ByteArray(8_136) { (it % 211).toByte() }

        assertThrows(RecoveryContractException::class.java) { encrypt(keyset, plaintext) }
        keyset.serializeEncrypted(runAead)
        assertThrows(RecoveryContractException::class.java) { keyset.serializeEncrypted(runAead) }
        encrypt(keyset, plaintext)
        assertThrows(RecoveryContractException::class.java) { encrypt(keyset, plaintext) }

        assertThrows(RecoveryContractException::class.java) {
            RecoveryTinkRuntime.newStreamingKeyset(aad.copy(plaintextEndExclusive = 0UL))
        }
    }

    @Test
    fun `failed envelope serialization leaves a fresh keyset unusable`() {
        val aad = Fixtures.streamEnvelopeAad()
        val keyset = RecoveryTinkRuntime.newStreamingKeyset(aad)
        val wrongRunAead =
            RecoveryRunAeadProvider(RecordingRunAeadBackend()).createNew(Fixtures.OTHER_RUN_ID)
        val correctRunAead =
            RecoveryRunAeadProvider(RecordingRunAeadBackend()).createNew(Fixtures.RUN_ID)

        assertThrows(RecoveryContractException::class.java) {
            keyset.serializeEncrypted(wrongRunAead)
        }
        assertThrows(RecoveryContractException::class.java) {
            keyset.serializeEncrypted(correctRunAead)
        }
    }

    @Test
    fun `encrypted AEAD keyset rejects stream target and preserves fresh-key separation`() {
        val runAead = RecoveryRunAeadProvider(RecordingRunAeadBackend()).createNew(Fixtures.RUN_ID)
        val aad = Fixtures.microfileEnvelopeAad()
        val original = RecoveryTinkRuntime.newAeadKeyset(aad)
        val other = RecoveryTinkRuntime.newAeadKeyset(aad)
        val envelope = original.serializeEncrypted(runAead)
        val parsed = RecoveryTinkRuntime.parseEncryptedAeadKeyset(envelope, runAead, aad)
        val plaintext = ByteArray(160_000) { (it % 239).toByte() }
        val ciphertext = original.encryptMicrofile(plaintext, Fixtures.microfileAad())

        assertArrayEquals(plaintext, parsed.decryptMicrofile(ciphertext, Fixtures.microfileAad()))
        assertThrows(GeneralSecurityException::class.java) {
            other.decryptMicrofile(ciphertext, Fixtures.microfileAad())
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryTinkRuntime.newAeadKeyset(Fixtures.streamEnvelopeAad())
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryTinkRuntime.parseEncryptedAeadKeyset(
                envelope,
                runAead,
                Fixtures.streamEnvelopeAad(),
            )
        }
        assertThrows(GeneralSecurityException::class.java) {
            RecoveryTinkRuntime.parseEncryptedAeadKeyset(
                envelope,
                runAead,
                RecoveryCryptoTestFixtures.manifestEnvelopeAad(),
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            parsed.encryptMicrofile(plaintext, Fixtures.microfileAad())
        }
        assertThrows(RecoveryContractException::class.java) { parsed.serializeEncrypted(runAead) }
        assertNotEquals(KeyEnvelopeTargetKind.STREAM, aad.targetKind)
    }

    private fun encrypt(
        keyset: RecoveryStreamingKeyset,
        plaintext: ByteArray,
    ): ByteArray {
        val destination = ByteArrayOutputStream()
        keyset
            .newEncryptingStream(
                destination,
                StreamingAad(RecoveryCandidate.STREAM, Fixtures.RUN_ID),
            )
            .use { it.write(plaintext) }
        return destination.toByteArray()
    }

    private fun decrypt(
        keyset: RecoveryStreamingKeyset,
        ciphertext: ByteArray,
    ): ByteArray =
        keyset
            .newDecryptingStream(
                ByteArrayInputStream(ciphertext),
                StreamingAad(RecoveryCandidate.STREAM, Fixtures.RUN_ID),
            )
            .use { it.readBytes() }

    private object Fixtures {
        val RUN_ID = RecoveryCryptoTestFixtures.RUN_ID
        val OTHER_RUN_ID = RecoveryCryptoTestFixtures.OTHER_RUN_ID

        fun streamEnvelopeAad(runId: com.monumentogram.dora.poc.recovery.contract.RunId = RUN_ID) =
            RecoveryCryptoTestFixtures.streamEnvelopeAad(runId)

        fun microfileEnvelopeAad() = RecoveryCryptoTestFixtures.microfileEnvelopeAad()

        fun microfileAad() = RecoveryCryptoTestFixtures.microfileAad()
    }
}
