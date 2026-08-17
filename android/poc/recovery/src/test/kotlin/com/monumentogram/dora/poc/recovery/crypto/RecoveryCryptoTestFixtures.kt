package com.monumentogram.dora.poc.recovery.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AesGcmParameters
import com.google.crypto.tink.config.TinkConfig
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAad
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeTargetKind
import com.monumentogram.dora.poc.recovery.contract.MicrofileAad
import com.monumentogram.dora.poc.recovery.contract.PublicationAad
import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryContract
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value

internal object RecoveryCryptoTestFixtures {
    val RUN_ID: RunId = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")
    val OTHER_RUN_ID: RunId = RunId.fromCanonicalString("10213243-5465-7687-98a9-bacbdcedfe0f")

    fun streamEnvelopeAad(runId: RunId = RUN_ID): KeyEnvelopeAad =
        KeyEnvelopeAad(
            candidate = RecoveryCandidate.STREAM,
            runId = runId,
            targetKind = KeyEnvelopeTargetKind.STREAM,
            generation = 1UL,
            unitIndex = KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
            plaintextStartInclusive = 0UL,
            plaintextEndExclusive = RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN,
            cadenceSeconds = 0UL,
            previousPublicationCiphertextSha256 = Sha256Value.ZERO,
        )

    fun microfileEnvelopeAad(runId: RunId = RUN_ID): KeyEnvelopeAad =
        KeyEnvelopeAad(
            candidate = RecoveryCandidate.MICROFILE,
            runId = runId,
            targetKind = KeyEnvelopeTargetKind.MICROFILE,
            generation = 1UL,
            unitIndex = 0UL,
            plaintextStartInclusive = 0UL,
            plaintextEndExclusive = 160_000UL,
            cadenceSeconds = 5UL,
            previousPublicationCiphertextSha256 = Sha256Value.ZERO,
        )

    fun manifestEnvelopeAad(runId: RunId = RUN_ID): KeyEnvelopeAad =
        KeyEnvelopeAad(
            candidate = RecoveryCandidate.MICROFILE,
            runId = runId,
            targetKind = KeyEnvelopeTargetKind.MANIFEST,
            generation = 1UL,
            unitIndex = KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
            plaintextStartInclusive = 0UL,
            plaintextEndExclusive = 0UL,
            cadenceSeconds = 0UL,
            previousPublicationCiphertextSha256 = Sha256Value.ZERO,
        )

    fun checkpointEnvelopeAad(runId: RunId = RUN_ID): KeyEnvelopeAad =
        KeyEnvelopeAad(
            candidate = RecoveryCandidate.STREAM,
            runId = runId,
            targetKind = KeyEnvelopeTargetKind.CHECKPOINT,
            generation = 1UL,
            unitIndex = KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
            plaintextStartInclusive = 0UL,
            plaintextEndExclusive = 8_136UL,
            cadenceSeconds = 0UL,
            previousPublicationCiphertextSha256 = Sha256Value.ZERO,
        )

    fun microfileAad(runId: RunId = RUN_ID): MicrofileAad =
        MicrofileAad(
            candidate = RecoveryCandidate.MICROFILE,
            runId = runId,
            manifestGeneration = 1UL,
            unitIndex = 0UL,
            plaintextStartInclusive = 0UL,
            plaintextEndExclusive = 160_000UL,
            cadenceSeconds = 5UL,
            previousManifestCiphertextSha256 = Sha256Value.ZERO,
        )

    fun emptyManifestPublicationAad(runId: RunId = RUN_ID): PublicationAad =
        PublicationAad(
            candidate = RecoveryCandidate.MICROFILE,
            runId = runId,
            publicationKind = PublicationKind.MANIFEST,
            generation = 1UL,
            terminalUnitIndex = PublicationAad.EMPTY_TERMINAL_UNIT_INDEX,
            plaintextEndExclusive = 0UL,
            previousPublicationCiphertextSha256 = Sha256Value.ZERO,
        )

    fun checkpointPublicationAad(runId: RunId = RUN_ID): PublicationAad =
        PublicationAad(
            candidate = RecoveryCandidate.STREAM,
            runId = runId,
            publicationKind = PublicationKind.CHECKPOINT,
            generation = 1UL,
            terminalUnitIndex = 2UL,
            plaintextEndExclusive = 8_136UL,
            previousPublicationCiphertextSha256 = Sha256Value.ZERO,
        )

    fun preparedStreamingKeyset(
        envelopeAad: KeyEnvelopeAad = streamEnvelopeAad()
    ): RecoveryStreamingKeyset {
        val keyset = RecoveryTinkRuntime.newStreamingKeyset(envelopeAad)
        val runAead =
            RecoveryRunAeadProvider(RecordingRunAeadBackend()).createNew(envelopeAad.runId)
        keyset.serializeEncrypted(runAead)
        return keyset
    }

    fun preparedAeadKeyset(envelopeAad: KeyEnvelopeAad): RecoveryAeadKeyset {
        val keyset = RecoveryTinkRuntime.newAeadKeyset(envelopeAad)
        val runAead =
            RecoveryRunAeadProvider(RecordingRunAeadBackend()).createNew(envelopeAad.runId)
        keyset.serializeEncrypted(runAead)
        return keyset
    }
}

internal class RecordingRunAeadBackend : RecoveryRunAeadBackend {
    val events = mutableListOf<String>()
    val primitive: Aead = newTestAead()
    var generateFailure: Exception? = null
    var getFailure: Exception? = null

    override fun generateNew(keyUri: String) {
        events += "generate:$keyUri"
        generateFailure?.let { throw it }
    }

    override fun getAead(keyUri: String): Aead {
        events += "get:$keyUri"
        getFailure?.let { throw it }
        return primitive
    }
}

private fun newTestAead(): Aead {
    TinkConfig.register()
    val parameters =
        AesGcmParameters.builder()
            .setKeySizeBytes(RecoveryTinkRuntime.AEAD_KEY_BYTES)
            .setIvSizeBytes(RecoveryTinkRuntime.AEAD_IV_BYTES)
            .setTagSizeBytes(RecoveryTinkRuntime.AEAD_TAG_BYTES)
            .setVariant(AesGcmParameters.Variant.TINK)
            .build()
    val handle =
        KeysetHandle.newBuilder()
            .addEntry(
                KeysetHandle.generateEntryFromParameters(parameters).withRandomId().makePrimary()
            )
            .build()
    return handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
}
