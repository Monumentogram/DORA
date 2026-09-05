package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.bootstrap.BootstrapPublicationCapability
import com.monumentogram.dora.poc.recovery.bootstrap.KeyConfirmationState
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAad
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeTargetKind
import com.monumentogram.dora.poc.recovery.contract.MicrofileAad
import com.monumentogram.dora.poc.recovery.contract.PublicationAad
import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryContract
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifest
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifestCodec
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifestEntry
import com.monumentogram.dora.poc.recovery.contract.RecoveryProcessingIntent
import com.monumentogram.dora.poc.recovery.contract.RecoveryProcessingIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNameState
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.coordination.ProcessRecoveryRunSingleWriterGuard
import com.monumentogram.dora.poc.recovery.coordination.RecoveryRunSingleWriterGuard
import com.monumentogram.dora.poc.recovery.crypto.RecoveryAeadKeyset
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAead

internal enum class MicrofileStep {
    P01,
    P02,
    P03,
    P04,
    P05,
    P06,
    P07,
    P08,
    P09,
    P10,
    P11,
    P12,
    P13,
    P14,
    P15,
    P16,
    P17,
    P18,
    P19,
    P20,
    P21,
}

internal enum class CandidateSideEffectState {
    NOT_ATTEMPTED,
    OUTCOME_UNKNOWN,
    CONFIRMED,
}

internal data class CandidateArtifactRemainder(
    var tempCreated: Boolean = false,
    var fullyWritten: Boolean = false,
    var fileSynced: Boolean = false,
    var rename: CandidateSideEffectState = CandidateSideEffectState.NOT_ATTEMPTED,
    var parentSynced: Boolean = false,
)

internal data class MicrofileDurableRemainder(
    val unitEnvelope: CandidateArtifactRemainder = CandidateArtifactRemainder(),
    val unitCiphertext: CandidateArtifactRemainder = CandidateArtifactRemainder(),
    val manifestEnvelope: CandidateArtifactRemainder = CandidateArtifactRemainder(),
    val manifestCiphertext: CandidateArtifactRemainder = CandidateArtifactRemainder(),
    var rowsInserted: Boolean = false,
    var transactionMarkedSuccessful: Boolean = false,
    var transactionEnd: CandidateSideEffectState = CandidateSideEffectState.NOT_ATTEMPTED,
    var evidenceEmitted: Boolean = false,
)

internal data class PreparedRecoveryAead(val keyset: RecoveryAeadKeyset, val envelope: ByteArray)

internal interface RecoveryMicrofileCrypto {
    fun openRunAead(runId: RunId): RecoveryRunAead

    fun createKeyset(aad: KeyEnvelopeAad, runAead: RecoveryRunAead): PreparedRecoveryAead

    fun encryptMicrofile(
        keyset: RecoveryAeadKeyset,
        plaintext: ByteArray,
        aad: MicrofileAad,
    ): ByteArray

    fun encryptManifest(
        keyset: RecoveryAeadKeyset,
        plaintext: ByteArray,
        aad: PublicationAad,
    ): ByteArray
}

internal interface CandidateWriteHandle

internal interface RecoveryCandidateStorage {
    fun openExclusiveTemp(runId: RunId, temporaryRelativeName: String): CandidateWriteHandle

    fun write(handle: CandidateWriteHandle, bytes: ByteArray, offset: Int, count: Int): Int

    fun fsync(handle: CandidateWriteHandle)

    fun close(handle: CandidateWriteHandle)

    fun finalExists(runId: RunId, finalRelativeName: String): Boolean

    fun renameTempToFinal(runId: RunId, temporaryRelativeName: String, finalRelativeName: String)

    fun fsyncParent(runId: RunId, finalRelativeName: String)
}

internal data class CandidateBootstrapRow(
    val runId: String,
    val candidateId: String,
    val state: KeyConfirmationState,
)

internal data class RecoveryMicrofileUnitRow(
    val runId: String,
    val candidateId: String,
    val unitIndex: ULong,
    val plaintextStartInclusive: ULong,
    val plaintextEndExclusive: ULong,
    val cadenceSeconds: ULong,
    val ciphertextRelativeName: String,
    val ciphertextBytes: Long,
    val ciphertextSha256: Sha256Value,
    val keyEnvelopeRelativeName: String,
    val keyEnvelopeBytes: Long,
    val keyEnvelopeSha256: Sha256Value,
    val manifestGeneration: ULong,
    val processingIntentId: Sha256Value,
    val state: String = "VALID",
)

internal data class RecoveryManifestPublicationRow(
    val runId: String,
    val candidateId: String,
    val generation: ULong,
    val committedEndExclusive: ULong,
    val publicationRelativeName: String,
    val publicationBytes: Long,
    val publicationSha256: Sha256Value,
    val keyEnvelopeRelativeName: String,
    val keyEnvelopeBytes: Long,
    val keyEnvelopeSha256: Sha256Value,
    val previousPublicationCiphertextSha256: Sha256Value,
    val state: String = "VALID",
)

internal data class RecoveryCandidateSnapshot(
    val bootstrapRows: List<CandidateBootstrapRow>,
    val units: List<RecoveryMicrofileUnitRow>,
    val publications: List<RecoveryManifestPublicationRow>,
)

internal interface RecoveryMicrofileJournal {
    fun loadSnapshot(runId: RunId): RecoveryCandidateSnapshot

    fun beginNonExclusive(): RecoveryMicrofileTransaction
}

internal interface RecoveryMicrofileTransaction {
    fun insert(unit: RecoveryMicrofileUnitRow, publication: RecoveryManifestPublicationRow)

    fun markSuccessful()

    fun end()
}

internal fun interface RecoveryMicrofileEvidenceSink {
    fun emit(unit: RecoveryMicrofileUnitRow, publication: RecoveryManifestPublicationRow)
}

internal data class MicrofilePublicationInput(
    val confirmation: KeyConfirmationValue,
    val capability: BootstrapPublicationCapability,
    val plaintext: ByteArray,
    val cadenceSeconds: ULong,
)

internal class CandidatePublicationCapability
private constructor(val runId: RunId, val generation: ULong) {
    companion object {
        internal fun afterCommit(runId: RunId, generation: ULong) =
            CandidatePublicationCapability(runId, generation)
    }
}

internal sealed interface MicrofilePublicationResult {
    data class Rejected(
        val cause: Throwable,
        val completedSteps: List<MicrofileStep>,
        val remainder: MicrofileDurableRemainder,
    ) : MicrofilePublicationResult

    data class Failed(
        val failedStep: MicrofileStep,
        val cause: Throwable,
        val completedSteps: List<MicrofileStep>,
        val remainder: MicrofileDurableRemainder,
    ) : MicrofilePublicationResult

    data class ConcurrentWriter(val runId: RunId) : MicrofilePublicationResult

    data class Committed(
        val capability: CandidatePublicationCapability,
        val evidenceEmitted: Boolean,
        val evidenceFailure: Throwable?,
        val completedSteps: List<MicrofileStep>,
        val remainder: MicrofileDurableRemainder,
    ) : MicrofilePublicationResult
}

internal class RecoveryMicrofilePublicationController(
    private val crypto: RecoveryMicrofileCrypto,
    private val storage: RecoveryCandidateStorage,
    private val journal: RecoveryMicrofileJournal,
    private val evidenceSink: RecoveryMicrofileEvidenceSink,
    private val writerGuard: RecoveryRunSingleWriterGuard = ProcessRecoveryRunSingleWriterGuard,
) {
    fun publish(input: MicrofilePublicationInput): MicrofilePublicationResult {
        val lease =
            writerGuard.tryAcquire(input.confirmation.runId)
                ?: return MicrofilePublicationResult.ConcurrentWriter(input.confirmation.runId)
        return lease.use { publishExclusive(input) }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "TooGenericExceptionCaught", "ReturnCount")
    private fun publishExclusive(input: MicrofilePublicationInput): MicrofilePublicationResult {
        val steps = mutableListOf<MicrofileStep>()
        val remainder = MicrofileDurableRemainder()
        fun fail(step: MicrofileStep, error: Throwable) =
            MicrofilePublicationResult.Failed(step, error, steps.toList(), remainder)
        if (!input.capability.authorizes(input.confirmation)) {
            return MicrofilePublicationResult.Rejected(
                IllegalArgumentException("Bootstrap capability mismatch"),
                steps,
                remainder,
            )
        }
        val continuation =
            try {
                validateSnapshot(input, journal.loadSnapshot(input.confirmation.runId))
            } catch (error: Throwable) {
                return MicrofilePublicationResult.Rejected(error, steps, remainder)
            }
        val runAead =
            try {
                crypto.openRunAead(input.confirmation.runId)
            } catch (error: Throwable) {
                return fail(MicrofileStep.P01, error)
            }
        val names = Names(continuation.unitIndex, continuation.generation)
        val microAad = continuation.microfileAad(input)
        val microEnvelopeAad = continuation.envelopeAad(input, KeyEnvelopeTargetKind.MICROFILE)
        val microPrepared =
            try {
                crypto.createKeyset(microEnvelopeAad, runAead)
            } catch (error: Throwable) {
                return fail(MicrofileStep.P01, error)
            }
        steps += MicrofileStep.P01
        publishFile(
                input.confirmation.runId,
                names.unitEnvelopeTemp,
                names.unitEnvelope,
                microPrepared.envelope,
                MicrofileStep.P02,
                MicrofileStep.P03,
                MicrofileStep.P04,
                MicrofileStep.P05,
                steps,
                remainder,
                remainder.unitEnvelope,
            )
            ?.let {
                return it
            }

        val unitCiphertext =
            try {
                crypto.encryptMicrofile(microPrepared.keyset, input.plaintext.copyOf(), microAad)
            } catch (error: Throwable) {
                return fail(MicrofileStep.P06, error)
            }
        publishFile(
                input.confirmation.runId,
                names.unitTemp,
                names.unit,
                unitCiphertext,
                MicrofileStep.P06,
                MicrofileStep.P07,
                MicrofileStep.P08,
                MicrofileStep.P09,
                steps,
                remainder,
                remainder.unitCiphertext,
            )
            ?.let {
                return it
            }

        val newEntry =
            RecoveryManifestEntry(
                continuation.unitIndex,
                continuation.start,
                continuation.end,
                input.cadenceSeconds,
                unitCiphertext.size.toULong(),
                Sha256Value.calculate(unitCiphertext),
                microPrepared.envelope.size.toULong(),
                Sha256Value.calculate(microPrepared.envelope),
                names.unit,
                names.unitEnvelope,
            )
        val manifest =
            RecoveryManifest.create(
                RecoveryCandidate.MICROFILE,
                input.confirmation.runId,
                continuation.generation,
                continuation.previousDigest,
                continuation.end,
                continuation.entries + newEntry,
            )
        val publicationAad =
            PublicationAad(
                RecoveryCandidate.MICROFILE,
                input.confirmation.runId,
                PublicationKind.MANIFEST,
                continuation.generation,
                continuation.unitIndex,
                continuation.end,
                continuation.previousDigest,
            )
        val manifestEnvelopeAad =
            KeyEnvelopeAad(
                RecoveryCandidate.MICROFILE,
                input.confirmation.runId,
                KeyEnvelopeTargetKind.MANIFEST,
                continuation.generation,
                KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
                0UL,
                continuation.end,
                0UL,
                continuation.previousDigest,
            )
        val manifestPrepared =
            try {
                crypto.createKeyset(manifestEnvelopeAad, runAead)
            } catch (error: Throwable) {
                return fail(MicrofileStep.P10, error)
            }
        steps += MicrofileStep.P10
        publishFile(
                input.confirmation.runId,
                names.manifestEnvelopeTemp,
                names.manifestEnvelope,
                manifestPrepared.envelope,
                MicrofileStep.P11,
                MicrofileStep.P12,
                MicrofileStep.P13,
                MicrofileStep.P14,
                steps,
                remainder,
                remainder.manifestEnvelope,
            )
            ?.let {
                return it
            }
        val manifestCiphertext =
            try {
                crypto.encryptManifest(
                    manifestPrepared.keyset,
                    RecoveryManifestCodec.encode(manifest),
                    publicationAad,
                )
            } catch (error: Throwable) {
                return fail(MicrofileStep.P15, error)
            }
        publishFile(
                input.confirmation.runId,
                names.manifestTemp,
                names.manifest,
                manifestCiphertext,
                MicrofileStep.P15,
                MicrofileStep.P16,
                MicrofileStep.P17,
                MicrofileStep.P18,
                steps,
                remainder,
                remainder.manifestCiphertext,
            )
            ?.let {
                return it
            }

        val unitRow =
            RecoveryMicrofileUnitRow(
                input.confirmation.runId.toCanonicalString(),
                RecoveryCandidate.MICROFILE.contractId,
                continuation.unitIndex,
                continuation.start,
                continuation.end,
                input.cadenceSeconds,
                names.unit,
                unitCiphertext.size.toLong(),
                Sha256Value.calculate(unitCiphertext),
                names.unitEnvelope,
                microPrepared.envelope.size.toLong(),
                Sha256Value.calculate(microPrepared.envelope),
                continuation.generation,
                RecoveryProcessingIntent.calculate(
                    RecoveryProcessingIntentInput(
                        RecoveryCandidate.MICROFILE,
                        input.confirmation.runId,
                        continuation.unitIndex,
                        continuation.start,
                        continuation.end,
                        Sha256Value.calculate(unitCiphertext),
                    )
                ),
            )
        val publicationRow =
            RecoveryManifestPublicationRow(
                input.confirmation.runId.toCanonicalString(),
                RecoveryCandidate.MICROFILE.contractId,
                continuation.generation,
                continuation.end,
                names.manifest,
                manifestCiphertext.size.toLong(),
                Sha256Value.calculate(manifestCiphertext),
                names.manifestEnvelope,
                manifestPrepared.envelope.size.toLong(),
                Sha256Value.calculate(manifestPrepared.envelope),
                continuation.previousDigest,
            )
        val transaction =
            try {
                journal.beginNonExclusive()
            } catch (error: Throwable) {
                return fail(MicrofileStep.P19, error)
            }
        var transactionFailure: Throwable? = null
        try {
            transaction.insert(unitRow, publicationRow)
            remainder.rowsInserted = true
            transaction.markSuccessful()
            remainder.transactionMarkedSuccessful = true
            steps += MicrofileStep.P19
        } catch (error: Throwable) {
            transactionFailure = error
        }
        try {
            remainder.transactionEnd = CandidateSideEffectState.OUTCOME_UNKNOWN
            transaction.end()
            remainder.transactionEnd = CandidateSideEffectState.CONFIRMED
            if (transactionFailure == null) {
                steps += MicrofileStep.P20
            }
        } catch (error: Throwable) {
            if (transactionFailure == null) transactionFailure = error
            else transactionFailure.addSuppressed(error)
        }
        if (transactionFailure != null)
            return fail(
                if (MicrofileStep.P19 in steps) MicrofileStep.P20 else MicrofileStep.P19,
                transactionFailure,
            )
        val capability =
            CandidatePublicationCapability.afterCommit(
                input.confirmation.runId,
                continuation.generation,
            )
        return try {
            evidenceSink.emit(unitRow, publicationRow)
            remainder.evidenceEmitted = true
            steps += MicrofileStep.P21
            MicrofilePublicationResult.Committed(capability, true, null, steps.toList(), remainder)
        } catch (error: Throwable) {
            MicrofilePublicationResult.Committed(
                capability,
                false,
                error,
                steps.toList(),
                remainder,
            )
        }
    }

    @Suppress("TooGenericExceptionCaught", "LongParameterList", "ReturnCount")
    private fun publishFile(
        runId: RunId,
        temporary: String,
        final: String,
        bytes: ByteArray,
        writeStep: MicrofileStep,
        syncStep: MicrofileStep,
        renameStep: MicrofileStep,
        parentStep: MicrofileStep,
        steps: MutableList<MicrofileStep>,
        whole: MicrofileDurableRemainder,
        artifact: CandidateArtifactRemainder,
    ): MicrofilePublicationResult.Failed? {
        val handle =
            try {
                storage.openExclusiveTemp(runId, temporary)
            } catch (error: Throwable) {
                return MicrofilePublicationResult.Failed(writeStep, error, steps.toList(), whole)
            }
        artifact.tempCreated = true
        var failure: Throwable? = null
        try {
            var offset = 0
            while (offset < bytes.size) {
                val written = storage.write(handle, bytes, offset, bytes.size - offset)
                check(written in 1..(bytes.size - offset)) {
                    "Candidate write made invalid progress"
                }
                offset += written
            }
            artifact.fullyWritten = true
            steps += writeStep
            storage.fsync(handle)
            artifact.fileSynced = true
            steps += syncStep
        } catch (error: Throwable) {
            failure = error
        }
        try {
            storage.close(handle)
        } catch (close: Throwable) {
            if (failure == null) failure = close else failure.addSuppressed(close)
        }
        if (failure != null)
            return MicrofilePublicationResult.Failed(
                if (writeStep in steps) syncStep else writeStep,
                failure,
                steps.toList(),
                whole,
            )
        try {
            check(!storage.finalExists(runId, final)) { "Candidate final already exists: $final" }
            artifact.rename = CandidateSideEffectState.OUTCOME_UNKNOWN
            storage.renameTempToFinal(runId, temporary, final)
            artifact.rename = CandidateSideEffectState.CONFIRMED
            steps += renameStep
            storage.fsyncParent(runId, final)
            artifact.parentSynced = true
            steps += parentStep
        } catch (error: Throwable) {
            return MicrofilePublicationResult.Failed(
                if (renameStep in steps) parentStep else renameStep,
                error,
                steps.toList(),
                whole,
            )
        }
        return null
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun validateSnapshot(
        input: MicrofilePublicationInput,
        snapshot: RecoveryCandidateSnapshot,
    ): Continuation {
        val run = input.confirmation.runId.toCanonicalString()
        check(
            snapshot.bootstrapRows.singleOrNull() ==
                CandidateBootstrapRow(
                    run,
                    RecoveryCandidate.MICROFILE.contractId,
                    KeyConfirmationState.VALID,
                )
        ) {
            "Exactly one matching VALID bootstrap row is required"
        }
        check(input.plaintext.isNotEmpty()) { "Microfile plaintext must be non-empty" }
        check(
            input.cadenceSeconds == 5UL ||
                input.cadenceSeconds == 15UL ||
                input.cadenceSeconds == 30UL
        ) {
            "Invalid cadence"
        }
        check(snapshot.units.size == snapshot.publications.size) {
            "Unit/publication state is ambiguous"
        }
        var end = 0UL
        var previous = Sha256Value.ZERO
        val entries = ArrayList<RecoveryManifestEntry>()
        snapshot.units.forEachIndexed { index, unit ->
            val publication = snapshot.publications.getOrNull(index) ?: error("Missing publication")
            val expectedIndex = index.toULong()
            val generation = expectedIndex + 1UL
            check(
                unit.runId == run &&
                    unit.candidateId == RecoveryCandidate.MICROFILE.contractId &&
                    unit.state == "VALID"
            ) {
                "Unit identity mismatch"
            }
            check(unit.unitIndex == expectedIndex && unit.manifestGeneration == generation) {
                "Unit sequence is ambiguous"
            }
            check(unit.plaintextStartInclusive == end && unit.plaintextEndExclusive > end) {
                "Unit range is not contiguous"
            }
            check(
                unit.cadenceSeconds == 5UL ||
                    unit.cadenceSeconds == 15UL ||
                    unit.cadenceSeconds == 30UL
            ) {
                "Stored cadence invalid"
            }
            check(unit.ciphertextBytes > 0 && unit.keyEnvelopeBytes > 0) {
                "Stored artifact size invalid"
            }
            check(
                unit.processingIntentId ==
                    RecoveryProcessingIntent.calculate(
                        RecoveryProcessingIntentInput(
                            RecoveryCandidate.MICROFILE,
                            input.confirmation.runId,
                            unit.unitIndex,
                            unit.plaintextStartInclusive,
                            unit.plaintextEndExclusive,
                            unit.ciphertextSha256,
                        )
                    )
            ) {
                "Processing intent mismatch"
            }
            RecoveryRelativeNames.validateMicrofileCiphertext(
                unit.ciphertextRelativeName,
                unit.unitIndex,
            )
            RecoveryRelativeNames.validateMicrofileKeyEnvelope(
                unit.keyEnvelopeRelativeName,
                unit.unitIndex,
            )
            check(
                publication.runId == run &&
                    publication.candidateId == RecoveryCandidate.MICROFILE.contractId &&
                    publication.state == "VALID"
            ) {
                "Publication identity mismatch"
            }
            check(
                publication.generation == generation &&
                    publication.committedEndExclusive == unit.plaintextEndExclusive
            ) {
                "Publication terminal range mismatch"
            }
            check(publication.previousPublicationCiphertextSha256 == previous) {
                "Publication chain mismatch"
            }
            check(publication.publicationBytes > 0 && publication.keyEnvelopeBytes > 0) {
                "Publication artifact size invalid"
            }
            RecoveryRelativeNames.validateManifestCiphertext(
                publication.publicationRelativeName,
                generation,
            )
            RecoveryRelativeNames.validateManifestKeyEnvelope(
                publication.keyEnvelopeRelativeName,
                generation,
            )
            entries +=
                RecoveryManifestEntry(
                    unit.unitIndex,
                    unit.plaintextStartInclusive,
                    unit.plaintextEndExclusive,
                    unit.cadenceSeconds,
                    unit.ciphertextBytes.toULong(),
                    unit.ciphertextSha256,
                    unit.keyEnvelopeBytes.toULong(),
                    unit.keyEnvelopeSha256,
                    unit.ciphertextRelativeName,
                    unit.keyEnvelopeRelativeName,
                )
            end = unit.plaintextEndExclusive
            previous = publication.publicationSha256
        }
        check(entries.size < RecoveryContract.MAX_MANIFEST_ENTRIES) {
            "Manifest entry limit reached"
        }
        check(end <= ULong.MAX_VALUE - input.plaintext.size.toULong()) {
            "Plaintext range overflow"
        }
        val nextEnd = end + input.plaintext.size.toULong()
        check(nextEnd <= RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN) {
            "Run plaintext bound exceeded"
        }
        val nextIndex = entries.size.toULong()
        check(nextIndex <= RecoveryContract.U32_MAX && nextIndex < ULong.MAX_VALUE) {
            "Unit index overflow"
        }
        return Continuation(nextIndex, nextIndex + 1UL, end, nextEnd, previous, entries)
    }

    private data class Continuation(
        val unitIndex: ULong,
        val generation: ULong,
        val start: ULong,
        val end: ULong,
        val previousDigest: Sha256Value,
        val entries: List<RecoveryManifestEntry>,
    ) {
        fun microfileAad(input: MicrofilePublicationInput) =
            MicrofileAad(
                RecoveryCandidate.MICROFILE,
                input.confirmation.runId,
                generation,
                unitIndex,
                start,
                end,
                input.cadenceSeconds,
                previousDigest,
            )

        fun envelopeAad(input: MicrofilePublicationInput, kind: KeyEnvelopeTargetKind) =
            KeyEnvelopeAad(
                RecoveryCandidate.MICROFILE,
                input.confirmation.runId,
                kind,
                generation,
                unitIndex,
                start,
                end,
                input.cadenceSeconds,
                previousDigest,
            )
    }

    private data class Names(val unitIndex: ULong, val generation: ULong) {
        val unit = RecoveryRelativeNames.microfileCiphertext(unitIndex)
        val unitTemp =
            RecoveryRelativeNames.microfileCiphertext(
                unitIndex,
                RecoveryRelativeNameState.TEMPORARY,
            )
        val unitEnvelope = RecoveryRelativeNames.microfileKeyEnvelope(unitIndex)
        val unitEnvelopeTemp =
            RecoveryRelativeNames.microfileKeyEnvelope(
                unitIndex,
                RecoveryRelativeNameState.TEMPORARY,
            )
        val manifest = RecoveryRelativeNames.manifestCiphertext(generation)
        val manifestTemp =
            RecoveryRelativeNames.manifestCiphertext(
                generation,
                RecoveryRelativeNameState.TEMPORARY,
            )
        val manifestEnvelope = RecoveryRelativeNames.manifestKeyEnvelope(generation)
        val manifestEnvelopeTemp =
            RecoveryRelativeNames.manifestKeyEnvelope(
                generation,
                RecoveryRelativeNameState.TEMPORARY,
            )
    }
}
