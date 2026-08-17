package com.monumentogram.dora.poc.recovery.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.Configuration
import com.google.crypto.tink.KeyStatus
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.Parameters
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.aead.AesGcmParameters
import com.google.crypto.tink.config.TinkConfig
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingParameters
import com.monumentogram.dora.poc.recovery.contract.CanonicalRecoveryAlias
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationAadCodec
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationPlaintextCodec
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAad
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAadCodec
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeTargetKind
import com.monumentogram.dora.poc.recovery.contract.MicrofileAad
import com.monumentogram.dora.poc.recovery.contract.MicrofileAadCodec
import com.monumentogram.dora.poc.recovery.contract.PublicationAad
import com.monumentogram.dora.poc.recovery.contract.PublicationAadCodec
import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryContract
import com.monumentogram.dora.poc.recovery.contract.RecoveryContractException
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.StreamingAad
import com.monumentogram.dora.poc.recovery.contract.StreamingAadCodec
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException

private object RegisteredRecoveryTink {
    val configuration: Configuration by
        lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            TinkConfig.register()
            RegistryConfiguration.get()
        }
}

/**
 * The isolated REC-I2B Tink boundary.
 *
 * It owns the exact public Tink construction API and registry configuration. Callers receive only
 * typed Recovery operations; neither a raw primitive, a keyset handle, nor a configurable Tink
 * registry crosses this boundary.
 */
internal object RecoveryTinkRuntime {
    const val STREAMING_INPUT_KEY_BYTES = 16
    const val STREAMING_DERIVED_KEY_BYTES = 16
    const val STREAMING_CIPHERTEXT_SEGMENT_BYTES = 4_096
    const val AEAD_KEY_BYTES = 32
    const val AEAD_IV_BYTES = 12
    const val AEAD_TAG_BYTES = 16

    fun newStreamingKeyset(aad: KeyEnvelopeAad): RecoveryStreamingKeyset {
        requireEnvelopeTarget(aad, KeyEnvelopeTargetKind.STREAM)
        return RecoveryStreamingKeyset(
            handle = newKeyset(streamingParameters()),
            envelopeAad = aad,
            encryptionAllowed = true,
        )
    }

    fun newAeadKeyset(aad: KeyEnvelopeAad): RecoveryAeadKeyset {
        requireAeadEnvelopeTarget(aad)
        return RecoveryAeadKeyset(
            handle = newKeyset(aeadParameters()),
            envelopeAad = aad,
            encryptionAllowed = true,
        )
    }

    fun parseEncryptedStreamingKeyset(
        encryptedKeyset: ByteArray,
        runAead: RecoveryRunAead,
        aad: KeyEnvelopeAad,
    ): RecoveryStreamingKeyset {
        requireEnvelopeTarget(aad, KeyEnvelopeTargetKind.STREAM)
        return RecoveryStreamingKeyset(
            handle = runAead.parseEncryptedKeyset(encryptedKeyset, aad),
            envelopeAad = aad,
            encryptionAllowed = false,
        )
    }

    fun parseEncryptedAeadKeyset(
        encryptedKeyset: ByteArray,
        runAead: RecoveryRunAead,
        aad: KeyEnvelopeAad,
    ): RecoveryAeadKeyset {
        requireAeadEnvelopeTarget(aad)
        return RecoveryAeadKeyset(
            handle = runAead.parseEncryptedKeyset(encryptedKeyset, aad),
            envelopeAad = aad,
            encryptionAllowed = false,
        )
    }

    fun parameterSnapshot(): RecoveryTinkParameterSnapshot {
        val streaming = streamingParameters()
        val aead = aeadParameters()
        return RecoveryTinkParameterSnapshot(
            streamingInputKeyBytes = streaming.keySizeBytes,
            streamingDerivedKeyBytes = streaming.derivedAesGcmKeySizeBytes,
            streamingHkdfHash = streaming.hkdfHashType.toString(),
            streamingCiphertextSegmentBytes = streaming.ciphertextSegmentSizeBytes,
            aeadKeyBytes = aead.keySizeBytes,
            aeadIvBytes = aead.ivSizeBytes,
            aeadTagBytes = aead.tagSizeBytes,
            aeadVariant = aead.variant.toString(),
        )
    }

    private fun streamingParameters(): AesGcmHkdfStreamingParameters =
        AesGcmHkdfStreamingParameters.builder()
            .setKeySizeBytes(STREAMING_INPUT_KEY_BYTES)
            .setDerivedAesGcmKeySizeBytes(STREAMING_DERIVED_KEY_BYTES)
            .setHkdfHashType(AesGcmHkdfStreamingParameters.HashType.SHA256)
            .setCiphertextSegmentSizeBytes(STREAMING_CIPHERTEXT_SEGMENT_BYTES)
            .build()

    private fun aeadParameters(): AesGcmParameters =
        AesGcmParameters.builder()
            .setKeySizeBytes(AEAD_KEY_BYTES)
            .setIvSizeBytes(AEAD_IV_BYTES)
            .setTagSizeBytes(AEAD_TAG_BYTES)
            .setVariant(AesGcmParameters.Variant.TINK)
            .build()

    private fun newKeyset(parameters: Parameters): KeysetHandle {
        ensureRegistered()
        return KeysetHandle.newBuilder()
            .addEntry(
                KeysetHandle.generateEntryFromParameters(parameters).withRandomId().makePrimary()
            )
            .build()
    }

    private fun ensureRegistered() {
        RegisteredRecoveryTink.configuration
    }

    private fun requireEnvelopeTarget(
        aad: KeyEnvelopeAad,
        expected: KeyEnvelopeTargetKind,
    ) {
        if (aad.targetKind != expected) {
            throw RecoveryContractException("Unexpected key-envelope target: ${aad.targetKind}")
        }
        if (expected == KeyEnvelopeTargetKind.STREAM) {
            aad.requireStreamingEnvelopeSemantics()
        }
    }

    private fun requireAeadEnvelopeTarget(aad: KeyEnvelopeAad) {
        if (aad.targetKind == KeyEnvelopeTargetKind.STREAM) {
            throw RecoveryContractException(
                "A streaming key envelope cannot contain an AEAD keyset"
            )
        }
    }
}

internal data class RecoveryTinkParameterSnapshot(
    val streamingInputKeyBytes: Int,
    val streamingDerivedKeyBytes: Int,
    val streamingHkdfHash: String,
    val streamingCiphertextSegmentBytes: Int,
    val aeadKeyBytes: Int,
    val aeadIvBytes: Int,
    val aeadTagBytes: Int,
    val aeadVariant: String,
)

internal data class RecoveryKeysetTopology(
    val entryCount: Int,
    val enabledPrimaryCount: Int,
    val primaryHasNonzeroId: Boolean,
)

internal class RecoveryStreamingKeyset(
    private val handle: KeysetHandle,
    private val envelopeAad: KeyEnvelopeAad,
    private val encryptionAllowed: Boolean,
) {
    private val useGuard = FreshKeysetUseGuard("streaming", encryptionAllowed)

    init {
        handle.requireRecoveryTopology()
        envelopeAad.requireStreamingEnvelopeSemantics()
    }

    private val primitive: StreamingAead =
        handle.getPrimitive(RegisteredRecoveryTink.configuration, StreamingAead::class.java)

    fun newEncryptingStream(
        destination: OutputStream,
        aad: StreamingAad,
    ): OutputStream {
        envelopeAad.requireStreamingBinding(aad)
        claimSingleEncryption()
        return primitive.newEncryptingStream(destination, StreamingAadCodec.encode(aad))
    }

    fun newDecryptingStream(
        source: InputStream,
        aad: StreamingAad,
    ): InputStream {
        envelopeAad.requireStreamingBinding(aad)
        return primitive.newDecryptingStream(source, StreamingAadCodec.encode(aad))
    }

    fun serializeEncrypted(runAead: RecoveryRunAead): ByteArray {
        return useGuard.serializeEnvelope {
            runAead.serializeEncryptedKeyset(handle, envelopeAad)
        }
    }

    fun topology(): RecoveryKeysetTopology = handle.toRecoveryTopology()

    private fun claimSingleEncryption() {
        useGuard.claimEncryption()
    }
}

internal class RecoveryAeadKeyset(
    private val handle: KeysetHandle,
    private val envelopeAad: KeyEnvelopeAad,
    private val encryptionAllowed: Boolean,
) {
    private val useGuard = FreshKeysetUseGuard("AEAD", encryptionAllowed)

    init {
        handle.requireRecoveryTopology()
        envelopeAad.requireAeadTarget()
    }

    private val primitive: Aead =
        handle.getPrimitive(RegisteredRecoveryTink.configuration, Aead::class.java)

    fun encryptMicrofile(
        plaintext: ByteArray,
        aad: MicrofileAad,
    ): ByteArray {
        envelopeAad.requireMicrofileBinding(aad)
        claimSingleEncryption()
        return primitive.encrypt(plaintext, MicrofileAadCodec.encode(aad))
    }

    fun decryptMicrofile(
        ciphertext: ByteArray,
        aad: MicrofileAad,
    ): ByteArray {
        envelopeAad.requireMicrofileBinding(aad)
        return primitive.decrypt(ciphertext, MicrofileAadCodec.encode(aad))
    }

    fun encryptPublication(
        plaintext: ByteArray,
        aad: PublicationAad,
    ): ByteArray {
        envelopeAad.requirePublicationBinding(aad)
        claimSingleEncryption()
        return primitive.encrypt(plaintext, PublicationAadCodec.encode(aad))
    }

    fun decryptPublication(
        ciphertext: ByteArray,
        aad: PublicationAad,
    ): ByteArray {
        envelopeAad.requirePublicationBinding(aad)
        return primitive.decrypt(ciphertext, PublicationAadCodec.encode(aad))
    }

    fun serializeEncrypted(runAead: RecoveryRunAead): ByteArray {
        return useGuard.serializeEnvelope {
            runAead.serializeEncryptedKeyset(handle, envelopeAad)
        }
    }

    fun topology(): RecoveryKeysetTopology = handle.toRecoveryTopology()

    private fun claimSingleEncryption() {
        useGuard.claimEncryption()
    }
}

private class FreshKeysetUseGuard(
    private val kind: String,
    encryptionAllowed: Boolean,
) {
    private var state =
        if (encryptionAllowed) {
            State.NEW
        } else {
            State.RECOVERY_ONLY
        }

    fun <T> serializeEnvelope(block: () -> T): T =
        synchronized(this) {
            if (state != State.NEW) {
                throw RecoveryContractException(
                    "A Recovery $kind keyset permits exactly one new-artifact envelope"
                )
            }
            state = State.UNUSABLE
            val result = block()
            state = State.ENVELOPE_READY
            result
        }

    fun claimEncryption() {
        synchronized(this) {
            if (state != State.ENVELOPE_READY) {
                throw RecoveryContractException(
                    "A Recovery $kind keyset encrypts once, after its envelope is serialized"
                )
            }
            state = State.ENCRYPTION_STARTED
        }
    }

    private enum class State {
        NEW,
        ENVELOPE_READY,
        ENCRYPTION_STARTED,
        RECOVERY_ONLY,
        UNUSABLE,
    }
}

private fun KeysetHandle.toRecoveryTopology(): RecoveryKeysetTopology {
    val entries = (0 until size()).map(::getAt)
    return RecoveryKeysetTopology(
        entryCount = entries.size,
        enabledPrimaryCount = entries.count { it.status == KeyStatus.ENABLED && it.isPrimary },
        primaryHasNonzeroId = entries.singleOrNull { it.isPrimary }?.let { it.id != 0 } ?: false,
    )
}

private fun KeysetHandle.requireRecoveryTopology() {
    val topology = toRecoveryTopology()
    if (
        topology.entryCount != 1 ||
            topology.enabledPrimaryCount != 1 ||
            !topology.primaryHasNonzeroId
    ) {
        throw GeneralSecurityException("Recovery keyset must contain one enabled random-ID primary")
    }
}

private fun KeyEnvelopeAad.requireStreamingEnvelopeSemantics() {
    if (
        targetKind != KeyEnvelopeTargetKind.STREAM ||
            generation != 1UL ||
            unitIndex != KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX ||
            plaintextStartInclusive != 0UL ||
            plaintextEndExclusive != RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN ||
            cadenceSeconds != 0UL ||
            !previousPublicationCiphertextSha256.isZero()
    ) {
        throw RecoveryContractException("Streaming key envelope does not match the exact run span")
    }
}

private fun KeyEnvelopeAad.requireAeadTarget() {
    if (targetKind == KeyEnvelopeTargetKind.STREAM) {
        throw RecoveryContractException("AEAD keyset cannot use a STREAM key envelope")
    }
}

private fun KeyEnvelopeAad.requireStreamingBinding(aad: StreamingAad) {
    if (candidate != aad.candidate || runId != aad.runId) {
        throw RecoveryContractException("Streaming AAD does not match its key envelope")
    }
}

private fun KeyEnvelopeAad.requireMicrofileBinding(aad: MicrofileAad) {
    if (
        targetKind != KeyEnvelopeTargetKind.MICROFILE ||
            candidate != aad.candidate ||
            runId != aad.runId ||
            generation != aad.manifestGeneration ||
            unitIndex != aad.unitIndex ||
            plaintextStartInclusive != aad.plaintextStartInclusive ||
            plaintextEndExclusive != aad.plaintextEndExclusive ||
            cadenceSeconds != aad.cadenceSeconds ||
            previousPublicationCiphertextSha256 != aad.previousManifestCiphertextSha256
    ) {
        throw RecoveryContractException("Microfile AAD does not match its key envelope")
    }
}

private fun KeyEnvelopeAad.requirePublicationBinding(aad: PublicationAad) {
    val expectedTarget =
        when (aad.publicationKind) {
            PublicationKind.MANIFEST -> KeyEnvelopeTargetKind.MANIFEST
            PublicationKind.CHECKPOINT -> KeyEnvelopeTargetKind.CHECKPOINT
        }
    if (
        targetKind != expectedTarget ||
            candidate != aad.candidate ||
            runId != aad.runId ||
            generation != aad.generation ||
            plaintextStartInclusive != 0UL ||
            plaintextEndExclusive != aad.plaintextEndExclusive ||
            previousPublicationCiphertextSha256 != aad.previousPublicationCiphertextSha256
    ) {
        throw RecoveryContractException("Publication AAD does not match its key envelope")
    }
}

/** A run-scoped wrapping primitive whose raw Tink AEAD never leaves the crypto boundary. */
internal class RecoveryRunAead
private constructor(
    private val primitive: Aead,
    val keyUri: String,
) {
    fun encryptKeyConfirmation(value: KeyConfirmationValue): ByteArray {
        requireMatchingAlias(value)
        return primitive.encrypt(
            KeyConfirmationPlaintextCodec.encode(value),
            KeyConfirmationAadCodec.encode(value),
        )
    }

    fun decryptKeyConfirmation(
        ciphertext: ByteArray,
        expected: KeyConfirmationValue,
    ): KeyConfirmationDecryption {
        requireMatchingAlias(expected)
        val plaintext =
            try {
                primitive.decrypt(ciphertext, KeyConfirmationAadCodec.encode(expected))
            } catch (error: GeneralSecurityException) {
                return KeyConfirmationDecryption.AuthenticationFailure(error)
            }

        return try {
            val actual = KeyConfirmationPlaintextCodec.decode(plaintext)
            if (actual != expected) {
                throw RecoveryContractException(
                    "Authenticated key-confirmation plaintext does not match the expected run"
                )
            }
            KeyConfirmationDecryption.Success(actual)
        } catch (error: RecoveryContractException) {
            KeyConfirmationDecryption.PlaintextContractFailure(error)
        }
    }

    internal fun serializeEncryptedKeyset(
        handle: KeysetHandle,
        aad: KeyEnvelopeAad,
    ): ByteArray {
        requireMatchingAlias(aad.runId)
        return TinkProtoKeysetFormat.serializeEncryptedKeyset(
            handle,
            primitive,
            KeyEnvelopeAadCodec.encode(aad),
            RegisteredRecoveryTink.configuration,
        )
    }

    internal fun parseEncryptedKeyset(
        encryptedKeyset: ByteArray,
        aad: KeyEnvelopeAad,
    ): KeysetHandle {
        requireMatchingAlias(aad.runId)
        return TinkProtoKeysetFormat.parseEncryptedKeyset(
            encryptedKeyset,
            primitive,
            KeyEnvelopeAadCodec.encode(aad),
            RegisteredRecoveryTink.configuration,
        )
    }

    private fun requireMatchingAlias(value: KeyConfirmationValue) {
        requireMatchingAlias(value.runId)
    }

    private fun requireMatchingAlias(runId: RunId) {
        if (keyUri != CanonicalRecoveryAlias.forRun(runId)) {
            throw RecoveryContractException(
                "Run AEAD key URI does not match the Recovery run identity"
            )
        }
    }

    companion object {
        internal fun createNew(
            runId: RunId,
            backend: RecoveryRunAeadBackend,
        ): RecoveryRunAead {
            val keyUri = CanonicalRecoveryAlias.forRun(runId)
            backend.generateNew(keyUri)
            return RecoveryRunAead(backend.getAead(keyUri), keyUri)
        }

        internal fun openExisting(
            runId: RunId,
            backend: RecoveryRunAeadBackend,
        ): RecoveryRunAead {
            val keyUri = CanonicalRecoveryAlias.forRun(runId)
            return RecoveryRunAead(backend.getAead(keyUri), keyUri)
        }
    }
}

internal sealed interface KeyConfirmationDecryption {
    data class Success(val value: KeyConfirmationValue) : KeyConfirmationDecryption

    data class AuthenticationFailure(val error: GeneralSecurityException) :
        KeyConfirmationDecryption

    data class PlaintextContractFailure(val error: RecoveryContractException) :
        KeyConfirmationDecryption
}
