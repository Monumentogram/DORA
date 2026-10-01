// All accepted Recovery row conversions are kept together for exact binding review.
@file:Suppress("TooManyFunctions")

package com.monumentogram.dora.audio.persistence.journal

import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.poc.recovery.bootstrap.KeyConfirmationState
import com.monumentogram.dora.poc.recovery.bootstrap.RecoveryBootstrapRunRow
import com.monumentogram.dora.poc.recovery.candidate.QuarantineBootstrapBinding
import com.monumentogram.dora.poc.recovery.candidate.QuarantineIntentState
import com.monumentogram.dora.poc.recovery.candidate.RecoveryManifestPublicationRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofileUnitRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineIntentRow
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.controller.StoredKeyConfirmationIdentity

internal fun String.sha() = Sha256Value.fromLowercaseHex(this)

internal fun Sha256Value.hex() = toLowercaseHex()

internal fun ULong.signed(): Long = also { require(it <= Long.MAX_VALUE.toULong()) }.toLong()

internal fun AssetEntity.identity() =
    AudioIdentity(RecordingId(recordingId), AudioAssetId(assetId), sessionId)

internal fun UnitClaimEntity.identity(audio: AudioIdentity) =
    AudioStorageUnitIdentity(
        audio,
        runId,
        ordinal,
        firstFrame,
        physicalId,
        physicalFirstFrame,
        sourceFrameOffset,
    )

internal fun BootstrapEntity.row() =
    RecoveryBootstrapRunRow(
        runId,
        candidateId,
        relativeName,
        bytes,
        digest.sha(),
        aliasHash.sha(),
        KeyConfirmationState.valueOf(state),
    )

internal fun RecoveryBootstrapRunRow.entity() =
    BootstrapEntity(
        runId,
        candidateId,
        keyConfirmationRelativeName,
        keyConfirmationBytes,
        keyConfirmationSha256.hex(),
        canonicalAliasSha256.hex(),
        keyConfirmationState.name,
    )

internal fun BootstrapEntity.confirmation() =
    StoredKeyConfirmationIdentity(
        KeyConfirmationValue(
            RecoveryCandidate.fromContractId(candidateId),
            RunId.fromCanonicalString(runId),
        ),
        relativeName,
        bytes,
        digest.sha(),
        aliasHash.sha(),
    )

internal fun RecoveryManifestPublicationRow.entity() =
    ManifestEntity(
        runId,
        candidateId,
        publicationKind.contractId,
        generation.signed(),
        committedEndExclusive.signed(),
        publicationRelativeName,
        publicationBytes,
        publicationSha256.hex(),
        keyEnvelopeRelativeName,
        keyEnvelopeBytes,
        keyEnvelopeSha256.hex(),
        previousPublicationCiphertextSha256.hex(),
        state,
    )

internal fun ManifestEntity.row() =
    RecoveryManifestPublicationRow(
        runId,
        candidateId,
        PublicationKind.fromContractId(kind),
        generation.toULong(),
        endExclusive.toULong(),
        relativeName,
        bytes,
        digest.sha(),
        keyName,
        keyBytes,
        keyDigest.sha(),
        previousDigest.sha(),
        state,
    )

internal fun RecoveryMicrofileUnitRow.entity(manifest: RecoveryManifestPublicationRow) =
    MicrofileEntity(
        runId,
        candidateId,
        unitIndex.signed(),
        plaintextStartInclusive.signed(),
        plaintextEndExclusive.signed(),
        cadenceSeconds.signed(),
        ciphertextRelativeName,
        ciphertextBytes,
        ciphertextSha256.hex(),
        keyEnvelopeRelativeName,
        keyEnvelopeBytes,
        keyEnvelopeSha256.hex(),
        manifestGeneration.signed(),
        manifest.publicationSha256.hex(),
        processingIntentId.hex(),
        state,
    )

internal fun MicrofileEntity.row() =
    RecoveryMicrofileUnitRow(
        runId,
        candidateId,
        unitIndex.toULong(),
        startInclusive.toULong(),
        endExclusive.toULong(),
        cadence.toULong(),
        relativeName,
        bytes,
        digest.sha(),
        keyName,
        keyBytes,
        keyDigest.sha(),
        generation.toULong(),
        processingIntent.sha(),
        state,
    )

internal fun RecoveryQuarantineIntentRow.entity() =
    QuarantineEntity(
        intentId.hex(),
        input.runId.toCanonicalString(),
        input.candidate.contractId,
        if (bootstrapBinding == QuarantineBootstrapBinding.PRESENT) input.runId.toCanonicalString()
        else null,
        bootstrapBinding.name,
        input.artifactRole.name,
        recordedObservedState.name,
        input.sourceRelativeName,
        destinationRelativeName,
        input.sourceBytes.signed(),
        input.sourceSha256.hex(),
        state.name,
    )

internal fun QuarantineEntity.row(): RecoveryQuarantineIntentRow {
    val input =
        RecoveryQuarantineIntentInput(
            RecoveryCandidate.fromContractId(candidateId),
            RunId.fromCanonicalString(runId),
            sourceName,
            RecoveryQuarantineArtifactRole.valueOf(role),
            sourceBytes.also { check(it >= 0) }.toULong(),
            sourceDigest.sha(),
        )
    val binding = QuarantineBootstrapBinding.valueOf(bootstrapBinding)
    check(
        (binding == QuarantineBootstrapBinding.PRESENT && bootstrapRunId == runId) ||
            (binding == QuarantineBootstrapBinding.ABSENT && bootstrapRunId == null)
    )
    check(input.candidate == RecoveryCandidate.MICROFILE)
    check(intentId.sha() == RecoveryQuarantineIntent.calculate(input))
    check(destinationName == RecoveryQuarantineIntent.destination(input))
    return RecoveryQuarantineIntentRow(
        intentId.sha(),
        input,
        RecoveryQuarantineObservedState.valueOf(observed),
        binding,
        destinationName,
        QuarantineIntentState.valueOf(state),
    )
}
