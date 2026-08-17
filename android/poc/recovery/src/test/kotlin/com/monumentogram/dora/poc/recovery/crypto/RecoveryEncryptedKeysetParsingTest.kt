package com.monumentogram.dora.poc.recovery.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.Parameters
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.aead.AesGcmParameters
import com.google.crypto.tink.config.TinkConfig
import com.google.crypto.tink.proto.EncryptedKeyset
import com.google.crypto.tink.shaded.protobuf.ByteString
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingParameters
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAad
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAadCodec
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryEncryptedKeysetParsingTest {
    @Test
    fun `exact streaming and AEAD suites parse successfully`() {
        val backend = RecordingRunAeadBackend()
        val runAead = RecoveryRunAeadProvider(backend).createNew(Fixtures.RUN_ID)
        val streamAad = Fixtures.streamEnvelopeAad()
        val aeadAad = Fixtures.microfileEnvelopeAad()
        val streamKeyset = RecoveryTinkRuntime.newStreamingKeyset(streamAad)
        val aeadKeyset = RecoveryTinkRuntime.newAeadKeyset(aeadAad)

        val parsedStream =
            RecoveryTinkRuntime.parseEncryptedStreamingKeyset(
                streamKeyset.serializeEncrypted(runAead),
                runAead,
                streamAad,
            )
        val parsedAead =
            RecoveryTinkRuntime.parseEncryptedAeadKeyset(
                aeadKeyset.serializeEncrypted(runAead),
                runAead,
                aeadAad,
            )

        assertEquals(1, parsedStream.topology().enabledPrimaryCount)
        assertEquals(1, parsedAead.topology().enabledPrimaryCount)
    }

    @Test
    fun `parsed alternate streaming parameters are rejected by the frozen suite`() {
        val backend = RecordingRunAeadBackend()
        val runAead = RecoveryRunAeadProvider(backend).createNew(Fixtures.RUN_ID)
        val aad = Fixtures.streamEnvelopeAad()
        val parameters =
            AesGcmHkdfStreamingParameters.builder()
                .setKeySizeBytes(32)
                .setDerivedAesGcmKeySizeBytes(32)
                .setHkdfHashType(AesGcmHkdfStreamingParameters.HashType.SHA256)
                .setCiphertextSegmentSizeBytes(8_192)
                .build()
        val envelope = serializeAlternateKeyset(parameters, backend.primitive, aad)

        assertParseFailure(
            RecoveryEncryptedKeysetParseFailure.PARSED_BUT_UNSUPPORTED_PARAMETER_SUITE
        ) {
            RecoveryTinkRuntime.parseEncryptedStreamingKeyset(envelope, runAead, aad)
        }
    }

    @Test
    fun `parsed AES-128-GCM parameters are rejected by the frozen suite`() {
        val backend = RecordingRunAeadBackend()
        val runAead = RecoveryRunAeadProvider(backend).createNew(Fixtures.RUN_ID)
        val aad = Fixtures.microfileEnvelopeAad()
        val parameters =
            AesGcmParameters.builder()
                .setKeySizeBytes(16)
                .setIvSizeBytes(RecoveryTinkRuntime.AEAD_IV_BYTES)
                .setTagSizeBytes(RecoveryTinkRuntime.AEAD_TAG_BYTES)
                .setVariant(AesGcmParameters.Variant.TINK)
                .build()
        val envelope = serializeAlternateKeyset(parameters, backend.primitive, aad)

        assertParseFailure(
            RecoveryEncryptedKeysetParseFailure.PARSED_BUT_UNSUPPORTED_PARAMETER_SUITE
        ) {
            RecoveryTinkRuntime.parseEncryptedAeadKeyset(envelope, runAead, aad)
        }
    }

    @Test
    fun `outer empty truncated and malformed encodings never reach decrypt`() {
        val runAead =
            RecoveryRunAeadProvider(RecordingRunAeadBackend(FailIfDecryptInvokedAead()))
                .createNew(Fixtures.RUN_ID)
        val invalidOuterEncodings =
            listOf(
                byteArrayOf(),
                byteArrayOf(0x0a),
                byteArrayOf(0x0a, 0x02, 0x01),
            )

        invalidOuterEncodings.forEach { bytes ->
            assertParseFailure(
                RecoveryEncryptedKeysetParseFailure.OUTER_STRUCTURAL_OR_ENCODING_INVALID
            ) {
                RecoveryTinkRuntime.parseEncryptedStreamingKeyset(
                    bytes,
                    runAead,
                    Fixtures.streamEnvelopeAad(),
                )
            }
        }
    }

    @Test
    fun `wrong AAD and modified tag stay unknown when Tink supplies no positive cause`() {
        val backend = RecordingRunAeadBackend()
        val runAead = RecoveryRunAeadProvider(backend).createNew(Fixtures.RUN_ID)
        val aad = Fixtures.microfileEnvelopeAad()
        val keyset = RecoveryTinkRuntime.newAeadKeyset(aad)
        val envelope = keyset.serializeEncrypted(runAead)
        val wrongAad =
            aad.copy(
                generation = 2UL,
                previousPublicationCiphertextSha256 =
                    Sha256Value.fromBytes(ByteArray(Sha256Value.SIZE_BYTES) { 1 }),
            )

        assertParseFailure(RecoveryEncryptedKeysetParseFailure.UNKNOWN) {
            RecoveryTinkRuntime.parseEncryptedAeadKeyset(envelope, runAead, wrongAad)
        }
        assertParseFailure(RecoveryEncryptedKeysetParseFailure.UNKNOWN) {
            RecoveryTinkRuntime.parseEncryptedAeadKeyset(
                mutateEncryptedPayload(envelope),
                runAead,
                aad,
            )
        }
    }

    @Test
    fun `provider and generic decrypt failures retain neutral parse signals`() {
        val aad = Fixtures.streamEnvelopeAad()
        val outer = outerEncryptedKeyset(byteArrayOf(1, 2, 3))

        assertParseFailure(RecoveryEncryptedKeysetParseFailure.OPERATIONAL) {
            RecoveryTinkRuntime.parseEncryptedStreamingKeyset(
                outer,
                runAeadWith(ProviderException("synthetic provider")),
                aad,
            )
        }
        assertParseFailure(RecoveryEncryptedKeysetParseFailure.UNKNOWN) {
            RecoveryTinkRuntime.parseEncryptedStreamingKeyset(
                outer,
                runAeadWith(GeneralSecurityException("synthetic unknown")),
                aad,
            )
        }
        assertParseFailure(RecoveryEncryptedKeysetParseFailure.AUTHENTICATION_REJECTED) {
            RecoveryTinkRuntime.parseEncryptedStreamingKeyset(
                outer,
                runAeadWith(
                    GeneralSecurityException(
                        "synthetic wrapped authentication",
                        AEADBadTagException("synthetic"),
                    )
                ),
                aad,
            )
        }
    }

    @Test
    fun `authenticated malformed inner keyset is distinct from decrypt failure`() {
        val backend = RecordingRunAeadBackend()
        val runAead = RecoveryRunAeadProvider(backend).createNew(Fixtures.RUN_ID)
        val aad = Fixtures.streamEnvelopeAad()
        val encryptedMalformedInner =
            backend.primitive.encrypt(
                byteArrayOf(0x0a, 0x02, 0x01),
                KeyEnvelopeAadCodec.encode(aad),
            )
        val outer = outerEncryptedKeyset(encryptedMalformedInner)

        assertParseFailure(RecoveryEncryptedKeysetParseFailure.AUTHENTICATED_INNER_KEYSET_INVALID) {
            RecoveryTinkRuntime.parseEncryptedStreamingKeyset(outer, runAead, aad)
        }
    }

    private fun serializeAlternateKeyset(
        parameters: Parameters,
        wrappingAead: Aead,
        aad: KeyEnvelopeAad,
    ): ByteArray {
        TinkConfig.register()
        val handle =
            KeysetHandle.newBuilder()
                .addEntry(
                    KeysetHandle.generateEntryFromParameters(parameters)
                        .withRandomId()
                        .makePrimary()
                )
                .build()
        return TinkProtoKeysetFormat.serializeEncryptedKeyset(
            handle,
            wrappingAead,
            KeyEnvelopeAadCodec.encode(aad),
            RegistryConfiguration.get(),
        )
    }

    private fun mutateEncryptedPayload(envelope: ByteArray): ByteArray {
        val outer = EncryptedKeyset.parseFrom(envelope)
        val payload = outer.encryptedKeyset.toByteArray()
        payload[payload.lastIndex] = (payload.last().toInt() xor 1).toByte()
        return outer
            .toBuilder()
            .setEncryptedKeyset(ByteString.copyFrom(payload))
            .build()
            .toByteArray()
    }

    private fun outerEncryptedKeyset(payload: ByteArray): ByteArray =
        EncryptedKeyset.newBuilder()
            .setEncryptedKeyset(ByteString.copyFrom(payload))
            .build()
            .toByteArray()

    private fun runAeadWith(error: Throwable): RecoveryRunAead =
        RecoveryRunAeadProvider(RecordingRunAeadBackend(ThrowingDecryptAead(error)))
            .createNew(Fixtures.RUN_ID)

    private fun assertParseFailure(
        expected: RecoveryEncryptedKeysetParseFailure,
        block: () -> Unit,
    ) {
        val error = assertThrows(RecoveryEncryptedKeysetParseException::class.java, block)
        assertEquals(expected, error.failure)
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

    private class FailIfDecryptInvokedAead : Aead {
        override fun encrypt(
            plaintext: ByteArray,
            associatedData: ByteArray,
        ): ByteArray = throw AssertionError("encrypt must not be called")

        override fun decrypt(
            ciphertext: ByteArray,
            associatedData: ByteArray,
        ): ByteArray = throw AssertionError("outer parse must not invoke decrypt")
    }

    private object Fixtures {
        val RUN_ID = RecoveryCryptoTestFixtures.RUN_ID

        fun streamEnvelopeAad() = RecoveryCryptoTestFixtures.streamEnvelopeAad()

        fun microfileEnvelopeAad() = RecoveryCryptoTestFixtures.microfileEnvelopeAad()
    }
}
