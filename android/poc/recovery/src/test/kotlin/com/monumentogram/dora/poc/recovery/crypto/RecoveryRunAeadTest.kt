package com.monumentogram.dora.poc.recovery.crypto

import com.google.crypto.tink.Aead
import com.monumentogram.dora.poc.recovery.contract.CanonicalRecoveryAlias
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationAadCodec
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationPlaintextCodec
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryContractException
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryRunAeadTest {
    @Test
    fun `create generates then opens exactly the canonical alias`() {
        val backend = RecordingRunAeadBackend()
        val provider = RecoveryRunAeadProvider(backend)
        val expectedAlias = CanonicalRecoveryAlias.forRun(RecoveryCryptoTestFixtures.RUN_ID)

        val runAead = provider.createNew(RecoveryCryptoTestFixtures.RUN_ID)

        assertEquals(expectedAlias, runAead.keyUri)
        assertEquals(listOf("generate:$expectedAlias", "get:$expectedAlias"), backend.events)
    }

    @Test
    fun `open existing never generates and has no generation fallback`() {
        val backend = RecordingRunAeadBackend()
        val provider = RecoveryRunAeadProvider(backend)
        val expectedAlias = CanonicalRecoveryAlias.forRun(RecoveryCryptoTestFixtures.RUN_ID)

        val runAead = provider.openExisting(RecoveryCryptoTestFixtures.RUN_ID)

        assertEquals(expectedAlias, runAead.keyUri)
        assertEquals(listOf("get:$expectedAlias"), backend.events)

        val failingBackend =
            RecordingRunAeadBackend().apply {
                getFailure = GeneralSecurityException("synthetic unavailable key")
            }
        assertThrows(GeneralSecurityException::class.java) {
            RecoveryRunAeadProvider(failingBackend).openExisting(RecoveryCryptoTestFixtures.RUN_ID)
        }
        assertEquals(listOf("get:$expectedAlias"), failingBackend.events)
    }

    @Test
    fun `create failure does not continue to getAead`() {
        val backend =
            RecordingRunAeadBackend().apply {
                generateFailure = GeneralSecurityException("synthetic existing alias")
            }
        val expectedAlias = CanonicalRecoveryAlias.forRun(RecoveryCryptoTestFixtures.RUN_ID)

        assertThrows(GeneralSecurityException::class.java) {
            RecoveryRunAeadProvider(backend).createNew(RecoveryCryptoTestFixtures.RUN_ID)
        }

        assertEquals(listOf("generate:$expectedAlias"), backend.events)
    }

    @Test
    fun `key confirmation succeeds only for the exact run and candidate`() {
        val backend = RecordingRunAeadBackend()
        val runAead = RecoveryRunAeadProvider(backend).createNew(RecoveryCryptoTestFixtures.RUN_ID)
        val expected =
            KeyConfirmationValue(RecoveryCandidate.STREAM, RecoveryCryptoTestFixtures.RUN_ID)
        val ciphertext = runAead.encryptKeyConfirmation(expected)

        val success = runAead.decryptKeyConfirmation(ciphertext, expected)
        assertEquals(expected, (success as KeyConfirmationDecryption.Success).value)

        val wrongCandidate =
            KeyConfirmationValue(RecoveryCandidate.MICROFILE, RecoveryCryptoTestFixtures.RUN_ID)
        assertTrue(
            runAead.decryptKeyConfirmation(ciphertext, wrongCandidate).let {
                it is KeyConfirmationDecryption.DecryptFailure &&
                    it.signal == RecoveryDecryptFailureSignal.UNKNOWN
            }
        )
        assertThrows(RecoveryContractException::class.java) {
            runAead.encryptKeyConfirmation(
                KeyConfirmationValue(
                    RecoveryCandidate.STREAM,
                    RecoveryCryptoTestFixtures.OTHER_RUN_ID,
                )
            )
        }
    }

    @Test
    fun `authentication failure and authenticated malformed plaintext stay typed apart`() {
        val backend = RecordingRunAeadBackend()
        val runAead = RecoveryRunAeadProvider(backend).createNew(RecoveryCryptoTestFixtures.RUN_ID)
        val expected =
            KeyConfirmationValue(RecoveryCandidate.STREAM, RecoveryCryptoTestFixtures.RUN_ID)

        val otherRunAead =
            RecoveryRunAeadProvider(RecordingRunAeadBackend())
                .createNew(RecoveryCryptoTestFixtures.RUN_ID)
        val validCiphertext = runAead.encryptKeyConfirmation(expected)
        assertTrue(
            otherRunAead.decryptKeyConfirmation(validCiphertext, expected).let {
                it is KeyConfirmationDecryption.DecryptFailure &&
                    it.signal == RecoveryDecryptFailureSignal.UNKNOWN
            }
        )

        val malformedCiphertext =
            backend.primitive.encrypt(
                byteArrayOf(1, 2, 3),
                KeyConfirmationAadCodec.encode(expected),
            )
        assertTrue(
            runAead.decryptKeyConfirmation(malformedCiphertext, expected)
                is KeyConfirmationDecryption.PlaintextContractFailure
        )

        val mismatchedPlaintext =
            KeyConfirmationPlaintextCodec.encode(
                KeyConfirmationValue(RecoveryCandidate.MICROFILE, expected.runId)
            )
        val mismatchedCiphertext =
            backend.primitive.encrypt(mismatchedPlaintext, KeyConfirmationAadCodec.encode(expected))
        assertTrue(
            runAead.decryptKeyConfirmation(mismatchedCiphertext, expected)
                is KeyConfirmationDecryption.PlaintextContractFailure
        )
    }

    @Test
    fun `key confirmation emits only neutral deterministic decrypt failure signals`() {
        val expected =
            KeyConfirmationValue(RecoveryCandidate.STREAM, RecoveryCryptoTestFixtures.RUN_ID)

        assertDecryptSignal(
            GeneralSecurityException("synthetic wrapped tag", AEADBadTagException("synthetic")),
            expected,
            RecoveryDecryptFailureSignal.AUTHENTICATION_REJECTED,
        )
        assertDecryptSignal(
            GeneralSecurityException("synthetic wrapped padding", BadPaddingException("synthetic")),
            expected,
            RecoveryDecryptFailureSignal.AUTHENTICATION_REJECTED,
        )
        assertDecryptSignal(
            GeneralSecurityException(
                "synthetic wrapped provider",
                ProviderException("synthetic"),
            ),
            expected,
            RecoveryDecryptFailureSignal.OPERATIONAL,
        )
        assertDecryptSignal(
            ProviderException("synthetic direct provider"),
            expected,
            RecoveryDecryptFailureSignal.OPERATIONAL,
        )
        assertDecryptSignal(
            GeneralSecurityException("synthetic unknown"),
            expected,
            RecoveryDecryptFailureSignal.UNKNOWN,
        )
    }

    private fun assertDecryptSignal(
        error: Throwable,
        expected: KeyConfirmationValue,
        signal: RecoveryDecryptFailureSignal,
    ) {
        val backend = RecordingRunAeadBackend(ThrowingDecryptAead(error))
        val runAead = RecoveryRunAeadProvider(backend).createNew(expected.runId)
        val result = runAead.decryptKeyConfirmation(byteArrayOf(1), expected)

        assertEquals(signal, (result as KeyConfirmationDecryption.DecryptFailure).signal)
        assertTrue(result.error === error)
    }

    private class ThrowingDecryptAead(private val error: Throwable) : Aead {
        override fun encrypt(
            plaintext: ByteArray,
            associatedData: ByteArray,
        ): ByteArray = throw AssertionError("encrypt must not be called")

        override fun decrypt(
            ciphertext: ByteArray,
            associatedData: ByteArray,
        ): ByteArray = throw error
    }
}
