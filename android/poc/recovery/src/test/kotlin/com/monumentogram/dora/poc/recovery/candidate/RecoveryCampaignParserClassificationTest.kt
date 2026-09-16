package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCampaignParserClassificationTest {
    private val run = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")
    private val request =
        RecoveryCampaignParserRequest(
            RecoveryCandidate.MICROFILE,
            run,
            "PAR-01",
            "OVERSIZED",
            480_000UL,
        )
    private val row =
        RecoveryManifestPublicationRow(
            run.toCanonicalString(),
            RecoveryCandidate.MICROFILE.contractId,
            PublicationKind.MANIFEST,
            3UL,
            480_000UL,
            RecoveryRelativeNames.manifestCiphertext(3UL),
            524_322L,
            Sha256Value.calculate(byteArrayOf(1)),
            RecoveryRelativeNames.manifestKeyEnvelope(3UL),
            300L,
            Sha256Value.calculate(byteArrayOf(2)),
            Sha256Value.calculate(byteArrayOf(3)),
        )
    private val fault =
        RecoveryCampaignParserFault(
            "PAR-01",
            "OVERSIZED",
            3L,
            row.publicationRelativeName,
            row.keyEnvelopeRelativeName,
            row.publicationBytes,
            row.publicationSha256.toLowercaseHex(),
            row.keyEnvelopeBytes,
            row.keyEnvelopeSha256.toLowercaseHex(),
            524_289,
            true,
            true,
            true,
            true,
        )
    private val observation =
        RecoveryArtifactSizeLimitObservation(row.publicationRelativeName, 524_322L, 262_144L)
    private val failure =
        RecoveryFailureDiagnostic(
            RecoveryFailureCategory.STRUCTURAL,
            "size",
            "",
            RecoveryFailureStage.ARTIFACT_PATH,
            observation,
        )

    @Test
    fun `exact actual target upper bound plus authenticated oversized fault maps`() {
        assertTrue(matches())
    }

    @Test
    fun `missing and generic structural or operational failures cannot map`() {
        listOf(
                null,
                failure.copy(artifactSizeLimit = null),
                failure.copy(category = RecoveryFailureCategory.OPERATIONAL),
                failure.copy(stage = RecoveryFailureStage.ARTIFACT_IO),
                failure.copy(stage = RecoveryFailureStage.ENVELOPE_BINDING),
            )
            .forEach { assertFalse(matches(failure = it)) }
    }

    @Test
    fun `unrelated artifact lower size changed bound and observed extent cannot map`() {
        listOf(
                observation.copy(relativeName = row.keyEnvelopeRelativeName),
                observation.copy(relativeName = "../bad"),
                observation.copy(observedBytes = -1),
                observation.copy(observedBytes = 0),
                observation.copy(observedBytes = 262_144),
                observation.copy(observedBytes = 524_323),
                observation.copy(maximumBytes = 524_321),
            )
            .forEach { assertFalse(matches(failure = failure.copy(artifactSizeLimit = it))) }
    }

    @Test
    fun `other candidates runs cases variants and committed boundary cannot map`() {
        listOf(
                request.copy(candidate = RecoveryCandidate.STREAM),
                request.copy(caseId = "COR-05"),
                request.copy(variant = "MALFORMED"),
                request.copy(committedEnd = 320_000UL),
                request.copy(
                    runId = RunId.fromCanonicalString("10213243-5465-7687-98a9-bacbdcedfe0f")
                ),
            )
            .forEach { assertFalse(matches(request = it)) }
    }

    @Test
    fun `mutation must bind every authenticated construction and exact updated artifact fact`() {
        listOf(
                fault.copy(caseId = "COR-05"),
                fault.copy(variant = "TRAILING_BYTE"),
                fault.copy(generation = 2),
                fault.copy(generation = 0),
                fault.copy(generation = -1),
                fault.copy(targetName = "manifests/g-00000000000000000002.ct"),
                fault.copy(targetEnvelopeName = "other.bin"),
                fault.copy(ciphertextBytes = 524_323),
                fault.copy(ciphertextSha256 = "0".repeat(64)),
                fault.copy(envelopeBytes = 301),
                fault.copy(envelopeSha256 = "0".repeat(64)),
                fault.copy(plaintextBytes = 524_288),
                fault.copy(originalAuthenticated = false),
                fault.copy(originalParsed = false),
                fault.copy(faultDecryptSucceeded = false),
                fault.copy(outerMetadataUpdated = false),
            )
            .forEach { assertFalse(matches(fault = it)) }
    }

    @Test
    fun `missing duplicate stale noncanonical and foreign journal targets cannot map`() {
        listOf(
                emptyList(),
                listOf(row, row),
                listOf(row, row.copy(generation = 4UL)),
                listOf(row.copy(state = "INVALID")),
                listOf(row.copy(runId = "other-run")),
                listOf(row.copy(candidateId = RecoveryCandidate.STREAM.contractId)),
                listOf(row.copy(publicationKind = PublicationKind.CHECKPOINT)),
                listOf(row.copy(publicationRelativeName = "../bad")),
                listOf(row.copy(keyEnvelopeRelativeName = "key-envelopes/other.kek")),
                listOf(row.copy(publicationBytes = 524_323)),
                listOf(row.copy(publicationSha256 = Sha256Value.ZERO)),
                listOf(row.copy(keyEnvelopeBytes = 301)),
                listOf(row.copy(keyEnvelopeSha256 = Sha256Value.ZERO)),
            )
            .forEach { assertFalse(matches(publications = it)) }
    }

    private fun matches(
        request: RecoveryCampaignParserRequest = this.request,
        publications: List<RecoveryManifestPublicationRow> = listOf(row),
        fault: RecoveryCampaignParserFault = this.fault,
        failure: RecoveryFailureDiagnostic? = this.failure,
    ) =
        RecoveryCampaignParserClassification.isExactOversizedManifest(
            request,
            publications,
            fault,
            failure,
        )
}
