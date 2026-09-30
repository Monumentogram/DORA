package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryContract
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.storage.RecoveryArtifactRoleBounds

internal data class RecoveryCampaignParserRequest(
    val candidate: RecoveryCandidate,
    val runId: RunId,
    val caseId: String,
    val variant: String,
    val committedEnd: ULong,
)

@Suppress("LongParameterList")
internal data class RecoveryCampaignParserFault(
    val caseId: String,
    val variant: String,
    val generation: Long,
    val targetName: String,
    val targetEnvelopeName: String,
    val ciphertextBytes: Long,
    val ciphertextSha256: String,
    val envelopeBytes: Long,
    val envelopeSha256: String,
    val plaintextBytes: Int,
    val originalAuthenticated: Boolean,
    val originalParsed: Boolean,
    val faultDecryptSucceeded: Boolean,
    val outerMetadataUpdated: Boolean,
)

/** Caller has already validated the immutable request/state fixture and attempt binding. */
internal object RecoveryCampaignParserClassification {
    @Suppress("ReturnCount", "ComplexCondition", "CyclomaticComplexMethod", "LongMethod")
    fun isExactOversizedManifest(
        request: RecoveryCampaignParserRequest,
        publications: List<RecoveryManifestPublicationRow>,
        fault: RecoveryCampaignParserFault,
        failure: RecoveryFailureDiagnostic?,
    ): Boolean {
        if (
            request.candidate != RecoveryCandidate.MICROFILE ||
                request.caseId != "PAR-01" ||
                request.variant != "OVERSIZED" ||
                fault.caseId != request.caseId ||
                fault.variant != request.variant ||
                fault.generation <= 0L
        )
            return false
        if (
            !fault.originalAuthenticated ||
                !fault.originalParsed ||
                !fault.faultDecryptSucceeded ||
                !fault.outerMetadataUpdated ||
                fault.plaintextBytes <= RecoveryContract.MAX_MANIFEST_PLAINTEXT_BYTES
        )
            return false
        val target =
            publications.singleOrNull { it.generation == fault.generation.toULong() }
                ?: return false
        if (
            target.generation != publications.maxOfOrNull { it.generation } ||
                target.state != "VALID" ||
                target.candidateId != request.candidate.contractId ||
                target.runId != request.runId.toCanonicalString() ||
                target.publicationKind != PublicationKind.MANIFEST ||
                target.committedEndExclusive != request.committedEnd ||
                target.publicationRelativeName !=
                    RecoveryRelativeNames.manifestCiphertext(target.generation) ||
                target.keyEnvelopeRelativeName !=
                    RecoveryRelativeNames.manifestKeyEnvelope(target.generation)
        )
            return false
        if (
            target.publicationRelativeName != fault.targetName ||
                target.keyEnvelopeRelativeName != fault.targetEnvelopeName ||
                target.publicationBytes != fault.ciphertextBytes ||
                target.publicationSha256.toLowercaseHex() != fault.ciphertextSha256 ||
                target.keyEnvelopeBytes != fault.envelopeBytes ||
                target.keyEnvelopeSha256.toLowercaseHex() != fault.envelopeSha256
        )
            return false
        if (
            failure?.category != RecoveryFailureCategory.STRUCTURAL ||
                failure.stage != RecoveryFailureStage.ARTIFACT_PATH
        )
            return false
        val size = failure.artifactSizeLimit ?: return false
        return size.relativeName == target.publicationRelativeName &&
            size.observedBytes == target.publicationBytes &&
            size.maximumBytes ==
                RecoveryArtifactRoleBounds.maximumFor(target.publicationRelativeName) &&
            size.observedBytes > size.maximumBytes
    }
}
