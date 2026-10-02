package com.monumentogram.dora.audio

import com.monumentogram.dora.audio.persistence.keys.KeyFailure
import com.monumentogram.dora.audio.persistence.keys.RunKeyFailureScopes
import com.monumentogram.dora.audio.persistence.keys.RunKeyOperation
import com.monumentogram.dora.poc.recovery.bootstrap.BootstrapResult
import com.monumentogram.dora.poc.recovery.bootstrap.RecoveryKeyBootstrapController
import com.monumentogram.dora.poc.recovery.candidate.MicrofilePublicationInput
import com.monumentogram.dora.poc.recovery.candidate.MicrofilePublicationResult
import com.monumentogram.dora.poc.recovery.candidate.MicrofileReconciliationResult
import com.monumentogram.dora.poc.recovery.candidate.RecoveryArtifactBytes
import com.monumentogram.dora.poc.recovery.candidate.RecoveryArtifactContext
import com.monumentogram.dora.poc.recovery.candidate.RecoveryCandidateSnapshot
import com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureCategory
import com.monumentogram.dora.poc.recovery.candidate.RecoveryInventorySnapshot
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofilePublicationController
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofileReconciliationController
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineController
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineIntentRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryReconciliationCrypto
import com.monumentogram.dora.poc.recovery.candidate.RecoveryReconciliationSource
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.KeyRecoveryClassification
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.controller.ConfirmationDiagnostic
import com.monumentogram.dora.poc.recovery.controller.RecoveryKeyConfirmationController

internal sealed interface AudioIntent {
    data class Append(val identity: AudioStorageUnitIdentity, val frames: Long) : AudioIntent

    data class Finalize(val sources: List<StoredAudioSegment>) : AudioIntent
}

/** Product composition may not inherit the PoC's optional empty quarantine/source defaults. */
internal interface ProductAudioRecoverySource : RecoveryReconciliationSource {
    override fun loadPendingQuarantine(runId: RunId): List<RecoveryQuarantineIntentRow>

    override fun loadInventorySnapshot(
        runId: RunId,
        candidate: RecoveryCandidateSnapshot,
    ): RecoveryInventorySnapshot

    override fun loadRetainedArtifact(
        original: RecoveryQuarantineIntentInput,
        context: RecoveryArtifactContext,
    ): RecoveryArtifactBytes?
}

internal data class StoredAudioSegment(
    val identity: AudioStorageUnitIdentity,
    val frames: Long,
    val manifestDigest: Sha256Value,
)

internal data class StoredAudioAsset(
    val identity: AudioIdentity,
    val segments: List<StoredAudioSegment> = emptyList(),
    val pending: AudioIntent? = null,
    val finalization: List<StoredAudioSegment>? = null,
)

/**
 * Required encrypted, authenticated metadata boundary. No production implementation in 8.1. All
 * operations except atomic unique create require one asset lease; reserve also uniquely reserves
 * the run across assets. A successful reserve durably stores its full typed intent before return.
 * reserve true means durable exact readback; false means definite non-mutation. An exception or
 * unknown commit must keep/reload the durable intent before any subsequent operation; bootstrap
 * never starts on that path. compareAndSet must durably commit and read back, or fail with the
 * old/pending state retained. Callers must reconcile ambiguous outcomes before continuing.
 */
internal interface EncryptedAudioCatalog {
    fun create(identity: AudioIdentity): Boolean

    fun tryAcquire(identity: AudioIdentity): AutoCloseable?

    fun load(identity: AudioIdentity): StoredAudioAsset?

    fun reserve(expected: StoredAudioAsset, intent: AudioIntent): Boolean

    fun compareAndSet(expected: StoredAudioAsset, next: StoredAudioAsset): Boolean
}

/** Only the admitted future encrypted composition may construct this bridge. */
// Ordered fail-closed gates are kept explicit, as in the accepted Recovery controllers.
@Suppress(
    "ReturnCount",
    "TooManyFunctions",
    "CyclomaticComplexMethod",
    "ComplexCondition",
    "LongParameterList",
)
internal class RecoveryAudioBridge(
    private val catalog: EncryptedAudioCatalog,
    private val bootstrap: RecoveryKeyBootstrapController,
    private val publisher: RecoveryMicrofilePublicationController,
    source: ProductAudioRecoverySource,
    crypto: RecoveryReconciliationCrypto,
    confirmation: RecoveryKeyConfirmationController,
    quarantine: RecoveryQuarantineController,
    private val keyFailures: RunKeyFailureScopes = RunKeyFailureScopes(),
) : ProductAudioWriterPort, ProductAudioReaderPort {
    private val reader =
        RecoveryMicrofileReconciliationController(source, crypto, confirmation, quarantine)

    override fun create(identity: AudioIdentity): AudioResult<Unit> = guarded {
        if (!canonical(identity.sessionId)) failed(AudioFailure.INVALID_INPUT)
        else if (catalog.create(identity)) AudioResult.Value(Unit)
        else failed(AudioFailure.COLLISION)
    }

    override fun append(
        segment: AudioStorageUnitIdentity,
        format: AudioFormat,
        pcm: ByteArray,
    ): AudioResult<Unit> {
        val frames =
            try {
                format.requireSupported()
                require(pcm.size <= MAX_SEGMENT_BYTES)
                require(segment.ordinal >= 0 && segment.firstFrame >= 0)
                RunId.fromCanonicalString(segment.unitId)
                require(canonical(segment.physicalSegmentId))
                require(
                    AudioTimeline.nextFrame(
                        segment.physicalFirstFrame,
                        segment.sourceFrameOffset,
                    ) == segment.firstFrame
                )
                AudioTimeline.frames(pcm.size)
            } catch (_: IllegalArgumentException) {
                return failed(AudioFailure.INVALID_INPUT)
            } catch (_: ArithmeticException) {
                return failed(AudioFailure.INVALID_INPUT)
            }
        val bytes = pcm.copyOf()
        return try {
            withAsset(segment.audio) { asset -> appendReserved(asset, segment, bytes, frames) }
        } finally {
            bytes.fill(0)
        }
    }

    @Suppress("LongMethod") // Keep durable reservation and ordered failure gates together.
    private fun appendReserved(
        asset: StoredAudioAsset,
        segment: AudioStorageUnitIdentity,
        bytes: ByteArray,
        frames: Long,
    ): AudioResult<Unit> {
        if (asset.pending != null || asset.finalization != null)
            return failed(AudioFailure.COLLISION)
        val start = validateOrder(asset)
        if (segment.ordinal != asset.segments.size || segment.firstFrame != start) {
            return failed(AudioFailure.INVALID_INPUT)
        }
        if (
            asset.segments.any {
                it.identity.physicalSegmentId == segment.physicalSegmentId &&
                    it.identity.physicalFirstFrame != segment.physicalFirstFrame
            }
        )
            return failed(AudioFailure.INVALID_INPUT)
        val intent = AudioIntent.Append(segment, frames)
        if (!PersistenceLatency.measure("reserve") { catalog.reserve(asset, intent) })
            return failed(AudioFailure.COLLISION)
        // Reservation stays pending after every failure: retries must reconcile, never overwrite.
        val run = RunId.fromCanonicalString(segment.unitId)
        val confirmation = KeyConfirmationValue(RecoveryCandidate.MICROFILE, run)
        val bootstrapAttempt =
            keyFailures.observe(run, RunKeyOperation.BOOTSTRAP) {
                PersistenceLatency.measure("bootstrap") { bootstrap.bootstrap(confirmation) }
            }
        bootstrapAttempt.failure?.let {
            return failed(it.audioFailure())
        }
        val boot = bootstrapAttempt.value
        if (boot !is BootstrapResult.Committed) return failed(AudioFailure.UNCERTAIN)
        val publicationAttempt =
            keyFailures.observe(run, RunKeyOperation.PUBLICATION) {
                PersistenceLatency.measure("publication") {
                    publisher.publish(
                        MicrofilePublicationInput(
                            confirmation,
                            boot.publicationCapability,
                            bytes,
                            5UL,
                        )
                    )
                }
            }
        publicationAttempt.failure?.let {
            return failed(it.audioFailure())
        }
        val publication = publicationAttempt.value
        if (publication !is MicrofilePublicationResult.Committed)
            return failed(AudioFailure.UNCERTAIN)
        if (publication.capability.runId != run || publication.capability.generation != 1UL) {
            return failed(AudioFailure.CORRUPT)
        }
        val authenticated = PersistenceLatency.measure("recovery") { recover(segment) }
        if (authenticated is AudioResult.Failed) return authenticated
        val recovered = (authenticated as AudioResult.Value).value
        try {
            if (
                !recovered.complete ||
                    recovered.bytes.size.toLong() != frames * 2 ||
                    !recovered.bytes.contentEquals(bytes)
            )
                return failed(AudioFailure.CORRUPT)
            val stored = StoredAudioSegment(segment, frames, recovered.manifestDigest)
            val pending = asset.copy(pending = intent)
            return if (
                PersistenceLatency.measure("catalog_commit") {
                    catalog.compareAndSet(pending, asset.copy(segments = asset.segments + stored))
                }
            ) {
                AudioResult.Value(Unit)
            } else failed(AudioFailure.UNCERTAIN)
        } finally {
            recovered.bytes.fill(0)
        }
    }

    override fun finalize(identity: AudioIdentity): AudioResult<Unit> =
        withAsset(identity) { asset ->
            if (asset.pending != null || asset.finalization != null || asset.segments.isEmpty()) {
                return@withAsset failed(AudioFailure.COLLISION)
            }
            val intent = AudioIntent.Finalize(asset.segments.toList())
            if (!catalog.reserve(asset, intent)) return@withAsset failed(AudioFailure.UNCERTAIN)
            reconcileAsset(asset.copy(pending = intent))
        }

    override fun reconcile(identity: AudioIdentity): AudioResult<Unit> =
        withAsset(identity, ::reconcileAsset)

    private fun reconcileAsset(asset: StoredAudioAsset): AudioResult<Unit> {
        validateOrder(asset)
        return when (val intent = asset.pending) {
            null -> AudioResult.Value(Unit)
            is AudioIntent.Append -> {
                if (
                    asset.finalization != null ||
                        intent.identity.audio != asset.identity ||
                        intent.identity.ordinal != asset.segments.size ||
                        intent.identity.firstFrame != validateOrder(asset)
                )
                    return failed(AudioFailure.CORRUPT)
                val result = recover(intent.identity)
                if (result is AudioResult.Failed) return result
                val recovered = (result as AudioResult.Value).value
                try {
                    if (!recovered.complete || recovered.bytes.size.toLong() != intent.frames * 2)
                        return failed(AudioFailure.INCOMPLETE)
                    val stored =
                        StoredAudioSegment(intent.identity, intent.frames, recovered.manifestDigest)
                    commit(asset, asset.copy(pending = null, segments = asset.segments + stored))
                } finally {
                    recovered.bytes.fill(0)
                }
            }
            is AudioIntent.Finalize -> {
                if (
                    asset.finalization != null ||
                        intent.sources != asset.segments ||
                        intent.sources.isEmpty()
                )
                    return failed(AudioFailure.CORRUPT)
                val verification =
                    extractAsset(asset.copy(pending = null), requireComplete = true) { _, _ -> }
                if (verification is AudioResult.Failed) return verification
                commit(asset, asset.copy(pending = null, finalization = intent.sources.toList()))
            }
        }
    }

    private fun commit(expected: StoredAudioAsset, next: StoredAudioAsset): AudioResult<Unit> =
        if (catalog.compareAndSet(expected, next)) AudioResult.Value(Unit)
        else failed(AudioFailure.UNCERTAIN)

    override fun extract(
        identity: AudioIdentity,
        consume: (firstFrame: Long, pcm: ByteArray) -> Unit,
    ): AudioResult<AudioReadSummary> = withAsset(identity) { extractAsset(it, consume = consume) }

    /** Resume requires every committed unit to pass strict recovery, including quarantine state. */
    internal fun verifyContinuation(identity: AudioIdentity): AudioResult<AudioReadSummary> =
        withAsset(identity) {
            if (it.pending != null || it.finalization != null) failed(AudioFailure.INCOMPLETE)
            else extractAsset(it, requireComplete = true) { _, _ -> }
        }

    /** Caller owns the catalog lease; no second acquisition can split reference/read/delete. */
    internal fun extractFinalizedHeld(
        expected: StoredAudioAsset,
        consume: (Long, ByteArray) -> Unit,
    ): AudioResult<AudioReadSummary> {
        check(catalog.load(expected.identity) == expected)
        check(expected.pending == null && expected.finalization != null)
        return extractAsset(expected, requireComplete = true, consume = consume)
    }

    private fun extractAsset(
        asset: StoredAudioAsset,
        requireComplete: Boolean = false,
        consume: (Long, ByteArray) -> Unit,
    ): AudioResult<AudioReadSummary> {
        val frames = validateOrder(asset)
        if (asset.segments.isEmpty()) return failed(AudioFailure.CORRUPT)
        if (asset.finalization != null && asset.finalization != asset.segments) {
            return failed(AudioFailure.CORRUPT)
        }
        var complete = asset.pending == null
        var deliveredFrames = 0L
        for (segment in asset.segments) {
            val result = recover(segment.identity)
            if (result is AudioResult.Failed) {
                if (
                    !requireComplete &&
                        deliveredFrames > 0 &&
                        result.reason in
                            setOf(
                                AudioFailure.INCOMPLETE,
                                AudioFailure.KEY_UNAVAILABLE,
                                AudioFailure.KEY_INVALIDATED,
                            )
                ) {
                    return AudioResult.Value(
                        AudioReadSummary(
                            asset.identity,
                            deliveredFrames,
                            AudioCompletion.PARTIAL_RECOVERED,
                            result.reason,
                        )
                    )
                }
                return result
            }
            val recovered = (result as AudioResult.Value).value
            try {
                if (
                    recovered.manifestDigest != segment.manifestDigest ||
                        recovered.bytes.size.toLong() != segment.frames * 2
                ) {
                    return failed(AudioFailure.CORRUPT)
                }
                complete = complete && recovered.complete
                if (requireComplete && !complete) return failed(AudioFailure.INCOMPLETE)
                consume(segment.identity.firstFrame, recovered.bytes)
                deliveredFrames = AudioTimeline.nextFrame(deliveredFrames, segment.frames)
            } finally {
                recovered.bytes.fill(0)
            }
        }
        val state =
            if (complete && asset.finalization != null) AudioCompletion.FINALIZED
            else AudioCompletion.PARTIAL_RECOVERED
        check(deliveredFrames == frames)
        return AudioResult.Value(AudioReadSummary(asset.identity, frames, state))
    }

    private fun validateOrder(asset: StoredAudioAsset): Long {
        var next = 0L
        val ids = mutableSetOf<String>()
        asset.segments.forEachIndexed { index, segment ->
            check(segment.identity.audio == asset.identity && segment.identity.ordinal == index)
            check(segment.identity.firstFrame == next && ids.add(segment.identity.unitId))
            check(canonical(segment.identity.physicalSegmentId))
            check(
                AudioTimeline.nextFrame(
                    segment.identity.physicalFirstFrame,
                    segment.identity.sourceFrameOffset,
                ) == next
            )
            check(
                asset.segments
                    .take(index)
                    .filter { it.identity.physicalSegmentId == segment.identity.physicalSegmentId }
                    .all { it.identity.physicalFirstFrame == segment.identity.physicalFirstFrame }
            )
            check(segment.frames in 1..MAX_SEGMENT_BYTES / 2)
            next = AudioTimeline.nextFrame(next, segment.frames)
        }
        return next
    }

    private data class Recovered(
        val bytes: ByteArray,
        val manifestDigest: Sha256Value,
        val complete: Boolean,
    )

    @Suppress("LongMethod") // Preserve the explicit authenticated-prefix validation sequence.
    private fun recover(segment: AudioStorageUnitIdentity): AudioResult<Recovered> {
        val run = RunId.fromCanonicalString(segment.unitId)
        val attempt =
            keyFailures.observe(run, RunKeyOperation.RECONCILIATION) { reader.reconcile(run) }
        val result = attempt.value
        refinedKeyFailure(result, attempt.failure)?.let {
            return failed(it)
        }
        if (result is MicrofileReconciliationResult.ConcurrentWriter)
            return failed(AudioFailure.BUSY)
        if (
            result is MicrofileReconciliationResult.PartialPrefix && result.prefix.units.isEmpty()
        ) {
            return failed(
                diagnosticReason(result.failure?.category)
                    ?: when (result.classification) {
                        KeyRecoveryClassification.KEY_UNAVAILABLE -> AudioFailure.KEY_UNAVAILABLE
                        KeyRecoveryClassification.KEY_UNAVAILABLE_KEY_MISMATCH ->
                            AudioFailure.KEY_INVALIDATED
                        else ->
                            if (
                                result.failure?.category == RecoveryFailureCategory.MISSING_ARTIFACT
                            )
                                AudioFailure.INCOMPLETE
                            else AudioFailure.CORRUPT
                    }
            )
        }
        if (result is MicrofileReconciliationResult.NoAuthenticatedPrefix) {
            return failed(rejectionReason(result))
        }
        val prefix =
            when (result) {
                is MicrofileReconciliationResult.AuthenticatedPrefix -> result.prefix
                is MicrofileReconciliationResult.PartialPrefix -> result.prefix
                else -> return failed(AudioFailure.CORRUPT)
            }
        val capability =
            when (result) {
                is MicrofileReconciliationResult.AuthenticatedPrefix -> result.capability
                is MicrofileReconciliationResult.PartialPrefix -> result.capability
                else -> return failed(AudioFailure.CORRUPT)
            }
        if (
            !capability.authorizes(prefix) ||
                prefix.runId != run ||
                prefix.candidate != RecoveryCandidate.MICROFILE ||
                prefix.units.size != 1 ||
                prefix.manifestGenerationUsed != 1UL ||
                prefix.authenticatedEndExclusive !in 2UL..MAX_SEGMENT_BYTES.toULong() ||
                prefix.authenticatedEndExclusive % 2UL != 0UL
        )
            return failed(AudioFailure.CORRUPT)
        return AudioResult.Value(
            Recovered(
                prefix.plaintextSnapshot(),
                prefix.manifestCiphertextSha256,
                result is MicrofileReconciliationResult.AuthenticatedPrefix &&
                    result.manifestRejections.isEmpty() &&
                    result.quarantineOutcomes.isEmpty(),
            )
        )
    }

    /**
     * Typed key detail may refine an unresolved key/operational outcome, never a proven rejection.
     */
    private fun refinedKeyFailure(
        result: MicrofileReconciliationResult,
        observed: KeyFailure?,
    ): AudioFailure? {
        if (observed == null) return null
        val refinable =
            when (result) {
                is MicrofileReconciliationResult.NoAuthenticatedPrefix ->
                    keyOutcomeIsUnresolved(result.classification, result.failure?.category) ||
                        (result.classification == null &&
                            result.failure == null &&
                            when (result.confirmationDiagnostic) {
                                ConfirmationDiagnostic.OPEN_OPERATIONAL_FAILURE,
                                ConfirmationDiagnostic.OPEN_UNEXPECTED_FAILURE,
                                ConfirmationDiagnostic.DECRYPT_UNKNOWN_FAILURE,
                                ConfirmationDiagnostic.DECRYPT_OPERATIONAL_FAILURE,
                                ConfirmationDiagnostic.DECRYPT_UNEXPECTED_FAILURE -> true
                                ConfirmationDiagnostic.AUTHENTICATION_WITHOUT_KEY04_PROVENANCE ->
                                    observed == KeyFailure.AUTHENTICATION_FAILED
                                else -> false
                            })
                is MicrofileReconciliationResult.PartialPrefix ->
                    result.prefix.units.isEmpty() &&
                        keyOutcomeIsUnresolved(result.classification, result.failure?.category)
                else -> false
            }
        return if (refinable) observed.audioFailure() else null
    }

    private fun keyOutcomeIsUnresolved(
        classification: KeyRecoveryClassification?,
        category: RecoveryFailureCategory?,
    ): Boolean {
        val operational =
            category in
                setOf(
                    RecoveryFailureCategory.OPERATIONAL,
                    RecoveryFailureCategory.UNKNOWN,
                    RecoveryFailureCategory.UNKNOWN_OUTCOME,
                )
        if (category != null && !operational) return false
        return classification == KeyRecoveryClassification.KEY_UNAVAILABLE ||
            (classification == null && operational)
    }

    private fun rejectionReason(
        result: MicrofileReconciliationResult.NoAuthenticatedPrefix
    ): AudioFailure =
        diagnosticReason(result.failure?.category)
            ?: when (result.classification) {
                KeyRecoveryClassification.KEY_UNAVAILABLE -> AudioFailure.KEY_UNAVAILABLE
                KeyRecoveryClassification.KEY_UNAVAILABLE_KEY_MISMATCH ->
                    AudioFailure.KEY_INVALIDATED
                KeyRecoveryClassification.KEY_REF_COLLISION -> AudioFailure.COLLISION
                KeyRecoveryClassification.INCOMPLETE_KEY_BOOTSTRAP,
                KeyRecoveryClassification.KEY_CONFIRMATION_MISSING -> AudioFailure.INCOMPLETE
                KeyRecoveryClassification.CORRUPT_KEY_CONFIRMATION,
                KeyRecoveryClassification.CORRUPT_KEY_ENVELOPE,
                KeyRecoveryClassification.KEY_ENVELOPE_AUTH_FAILURE -> AudioFailure.CORRUPT
                null ->
                    if (result.confirmationDiagnostic != null) AudioFailure.UNCERTAIN
                    else if (result.failure?.category == RecoveryFailureCategory.MISSING_ARTIFACT)
                        AudioFailure.INCOMPLETE
                    else AudioFailure.CORRUPT
            }

    private fun diagnosticReason(category: RecoveryFailureCategory?): AudioFailure? =
        when (category) {
            RecoveryFailureCategory.OPERATIONAL,
            RecoveryFailureCategory.UNKNOWN,
            RecoveryFailureCategory.UNKNOWN_OUTCOME -> AudioFailure.UNCERTAIN
            RecoveryFailureCategory.AUTHENTICATION_REJECTED -> AudioFailure.AUTHENTICATION_FAILED
            else -> null
        }

    private fun KeyFailure.audioFailure(): AudioFailure =
        when (this) {
            KeyFailure.TEMPORARILY_UNAVAILABLE -> AudioFailure.KEY_UNAVAILABLE
            KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED -> AudioFailure.KEY_INVALIDATED
            KeyFailure.AUTHENTICATION_FAILED -> AudioFailure.AUTHENTICATION_FAILED
            KeyFailure.CORRUPT_CIPHERTEXT -> AudioFailure.CORRUPT
            KeyFailure.NAMESPACE_OCCUPIED -> AudioFailure.COLLISION
            KeyFailure.INCOMPLETE_BOOTSTRAP -> AudioFailure.INCOMPLETE
            KeyFailure.STORAGE_FAILURE -> AudioFailure.UNCERTAIN
        }

    private fun <T> withAsset(
        identity: AudioIdentity,
        block: (StoredAudioAsset) -> AudioResult<T>,
    ): AudioResult<T> = guarded {
        val lease = catalog.tryAcquire(identity) ?: return@guarded failed(AudioFailure.BUSY)
        lease.use {
            val asset = catalog.load(identity) ?: return@guarded failed(AudioFailure.INVALID_INPUT)
            if (asset.identity != identity) return@guarded failed(AudioFailure.INVALID_INPUT)
            block(asset)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun <T> guarded(block: () -> AudioResult<T>): AudioResult<T> =
        try {
            block()
        } catch (_: Exception) {
            failed(AudioFailure.UNCERTAIN)
        }

    private fun failed(reason: AudioFailure) = AudioResult.Failed(reason)

    private fun canonical(value: String): Boolean =
        value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))

    private companion object {
        const val MAX_SEGMENT_BYTES = 160_000
    }
}
