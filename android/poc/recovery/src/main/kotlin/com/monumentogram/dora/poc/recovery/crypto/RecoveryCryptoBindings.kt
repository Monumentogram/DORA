package com.monumentogram.dora.poc.recovery.crypto

import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAad
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeTargetKind
import com.monumentogram.dora.poc.recovery.contract.MicrofileAad
import com.monumentogram.dora.poc.recovery.contract.PublicationAad
import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryContract
import com.monumentogram.dora.poc.recovery.contract.RecoveryContractException
import com.monumentogram.dora.poc.recovery.contract.StreamingAad

internal fun KeyEnvelopeAad.requireStreamingEnvelopeSemantics() {
    val message = "Streaming key envelope does not match the exact run span"
    requireCryptoBinding(targetKind == KeyEnvelopeTargetKind.STREAM, message)
    requireCryptoBinding(generation == 1UL, message)
    requireCryptoBinding(unitIndex == KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX, message)
    requireCryptoBinding(plaintextStartInclusive == 0UL, message)
    requireCryptoBinding(
        plaintextEndExclusive == RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN,
        message,
    )
    requireCryptoBinding(cadenceSeconds == 0UL, message)
    requireCryptoBinding(previousPublicationCiphertextSha256.isZero(), message)
}

internal fun KeyEnvelopeAad.requireAeadTarget() {
    if (targetKind == KeyEnvelopeTargetKind.STREAM) {
        throw RecoveryContractException("AEAD keyset cannot use a STREAM key envelope")
    }
}

internal fun KeyEnvelopeAad.requireStreamingBinding(aad: StreamingAad) {
    if (candidate != aad.candidate || runId != aad.runId) {
        throw RecoveryContractException("Streaming AAD does not match its key envelope")
    }
}

internal fun KeyEnvelopeAad.requireMicrofileBinding(aad: MicrofileAad) {
    val message = "Microfile AAD does not match its key envelope"
    requireCryptoBinding(targetKind == KeyEnvelopeTargetKind.MICROFILE, message)
    requireCryptoBinding(candidate == aad.candidate, message)
    requireCryptoBinding(runId == aad.runId, message)
    requireCryptoBinding(generation == aad.manifestGeneration, message)
    requireCryptoBinding(unitIndex == aad.unitIndex, message)
    requireCryptoBinding(plaintextStartInclusive == aad.plaintextStartInclusive, message)
    requireCryptoBinding(plaintextEndExclusive == aad.plaintextEndExclusive, message)
    requireCryptoBinding(cadenceSeconds == aad.cadenceSeconds, message)
    requireCryptoBinding(
        previousPublicationCiphertextSha256 == aad.previousManifestCiphertextSha256,
        message,
    )
}

internal fun KeyEnvelopeAad.requirePublicationBinding(aad: PublicationAad) {
    val expectedTarget =
        when (aad.publicationKind) {
            PublicationKind.MANIFEST -> KeyEnvelopeTargetKind.MANIFEST
            PublicationKind.CHECKPOINT -> KeyEnvelopeTargetKind.CHECKPOINT
        }
    val message = "Publication AAD does not match its key envelope"
    requireCryptoBinding(targetKind == expectedTarget, message)
    requireCryptoBinding(candidate == aad.candidate, message)
    requireCryptoBinding(runId == aad.runId, message)
    requireCryptoBinding(generation == aad.generation, message)
    requireCryptoBinding(plaintextStartInclusive == 0UL, message)
    requireCryptoBinding(plaintextEndExclusive == aad.plaintextEndExclusive, message)
    requireCryptoBinding(
        previousPublicationCiphertextSha256 == aad.previousPublicationCiphertextSha256,
        message,
    )
}

private fun requireCryptoBinding(
    condition: Boolean,
    message: String,
) {
    if (!condition) {
        throw RecoveryContractException(message)
    }
}
