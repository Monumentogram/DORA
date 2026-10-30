package com.monumentogram.dora.audio.persistence.journal

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.monumentogram.dora.audio.AudioDeletionCategory
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioIntent
import com.monumentogram.dora.audio.AudioSegmentIndex
import com.monumentogram.dora.audio.AudioSourceState
import com.monumentogram.dora.audio.EncryptedAudioCatalog
import com.monumentogram.dora.audio.OriginalAudioLifecycle
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioReferenceCodec
import com.monumentogram.dora.audio.PersistenceLatency
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.audio.StoredAudioAsset
import com.monumentogram.dora.audio.StoredAudioSegment
import com.monumentogram.dora.audio.persistence.DiagnosticJournalFactory
import com.monumentogram.dora.audio.persistence.DiagnosticSourcePolicy
import com.monumentogram.dora.audio.recording.AlphaRecordingLimit
import com.monumentogram.dora.audio.recording.RecordingLibraryMetadata
import com.monumentogram.dora.audio.recording.RecordingLibraryState
import com.monumentogram.dora.poc.recovery.bootstrap.KeyConfirmationState
import com.monumentogram.dora.poc.recovery.bootstrap.RecoveryBootstrapRunRow
import com.monumentogram.dora.poc.recovery.bootstrap.RecoveryRunBootstrapJournal
import com.monumentogram.dora.poc.recovery.bootstrap.RecoveryRunBootstrapTransaction
import com.monumentogram.dora.poc.recovery.candidate.CandidateBootstrapRow
import com.monumentogram.dora.poc.recovery.candidate.QuarantineBootstrapBinding
import com.monumentogram.dora.poc.recovery.candidate.QuarantineIntentState
import com.monumentogram.dora.poc.recovery.candidate.RecoveryCandidateSnapshot
import com.monumentogram.dora.poc.recovery.candidate.RecoveryManifestPublicationRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofileJournal
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofileTransaction
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofileUnitRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineIntentRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineJournal
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineTransaction
import com.monumentogram.dora.poc.recovery.contract.CanonicalRecoveryAlias
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryProcessingIntent
import com.monumentogram.dora.poc.recovery.contract.RecoveryProcessingIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.controller.StoredKeyConfirmationIdentity
import com.monumentogram.dora.poc.recovery.journal.RecoveryMicrofileQuarantineReadbackException
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the sole Room handle. The caller must inject the admitted encrypted helper and live auth
 * gate.
 */
// Explicit fail-closed returns and the accepted begin/mark/end ports remain visible for review.
@Suppress("TooManyFunctions", "ReturnCount", "DEPRECATION", "LargeClass")
internal class RoomAudioJournal
private constructor(
    private val database: AudioJournalDatabase,
    private val binding: VaultBindingEntity,
    private val lease: VaultJournalLease,
    private val registryKey: String,
    private val diagnostic: DiagnosticSourcePolicy,
) : AutoCloseable {
    private val dao = database.journal()
    private var closed = false
    private var uncertain = false
    // One immutable observation for the next append reservation, owned by the current lease.
    // Never used by load, CAS, finalization, or a post-commit readback.
    private var reservationSnapshot: StoredAudioAsset? = null

    private data class CatalogSnapshot(val revision: Long, val asset: StoredAudioAsset)

    private var verifiedCatalog: CatalogSnapshot? = null
    private val schemaVersionQuery =
        database.openHelper.writableDatabase.compileStatement("PRAGMA schema_version")
    private val expectedSchemaVersion = schemaVersionQuery.simpleQueryForLong()
    private var maintainingWal = false

    private fun maintainWalAfterReadback() {
        if (File("$registryKey-wal").length() < JournalWalCheckpoint.TRIGGER_BYTES) return
        lease.requireHeld()
        check(!maintainingWal && !database.inTransaction())
        maintainingWal = true
        try {
            PersistenceLatency.measure("wal_checkpoint") {
                JournalWalCheckpoint.perform(database.openHelper.writableDatabase)
            }
        } finally {
            maintainingWal = false
        }
    }

    private fun catalogRevision(): Long {
        if (schemaVersionQuery.simpleQueryForLong() != expectedSchemaVersion)
            throw UncertainAudioSourceState()
        return database.openHelper.writableDatabase
            .query("SELECT revision FROM audio_catalog_revision WHERE singleton=1")
            .use {
                check(it.moveToFirst())
                it.getLong(0).also { value -> check(value >= 0 && !it.moveToNext()) }
            }
    }

    /** Readback may advance the proof only by the exact known transaction's row mutations. */
    private fun retainCatalogDelta(
        previous: Long,
        mutations: Long,
        next: StoredAudioAsset,
    ): Boolean {
        val revision = Math.addExact(previous, mutations)
        if (catalogRevision() != revision) {
            verifiedCatalog = null
            return false
        }
        verifiedCatalog = CatalogSnapshot(revision, next)
        return true
    }

    private val commits =
        JournalCommitBoundary(
            {
                reservationSnapshot = null
                database.beginTransaction()
            },
            database::setTransactionSuccessful,
            database::endTransaction,
            database::inTransaction,
            afterReadback = ::maintainWalAfterReadback,
        ) {
            reservationSnapshot = null
            verifiedCatalog = null
            uncertain = true
        }
    val catalog: EncryptedAudioCatalog = Catalog()

    val pagedTranscripts =
        PagedTranscriptStore(
            database.paged(),
            ::transcript,
            ::mutatePagedTranscript,
            commits::commit,
        )

    /** A bounded header mutation and its page/progress rows share one durability boundary. */
    private fun mutatePagedTranscript(
        identity: AudioIdentity,
        transform:
            (
                com.monumentogram.dora.audio.transcript.TranscriptDocument?
            ) -> com.monumentogram.dora.audio.transcript.TranscriptDocument,
        write: () -> Unit,
        readback: () -> Boolean,
    ): com.monumentogram.dora.audio.transcript.TranscriptDocument {
        requireOperation(identity)
        requireMutableSource(identity)
        requireMutable()
        val old = transcript(identity)
        val next = transform(old)
        check(next.source.identity == identity)
        val source = checkNotNull(dao.originalReference(identity.assetId.value))
        check(
            source.digest == next.source.digest &&
                source.version == next.source.version &&
                source.frames == next.source.frames
        )
        check(next == old || next.revision == Math.addExact(old?.revision ?: 0, 1))
        val payload = com.monumentogram.dora.audio.transcript.TranscriptCodec.encode(next)
        try {
            commits.commit({
                if (old == null)
                    dao.insert(TranscriptEntity(identity.assetId.value, next.revision, payload))
                else
                    check(
                        dao.replaceTranscript(
                            identity.assetId.value,
                            old.revision,
                            next.revision,
                            payload,
                        ) == 1
                    )
                write()
            }) {
                val stored = dao.transcript(identity.assetId.value)
                stored != null &&
                    stored.revision == next.revision &&
                    stored.payload.contentEquals(payload) &&
                    readback()
            }
        } finally {
            payload.fill(0)
        }
        return next
    }

    private fun removePagedTranscript(asset: String) {
        database.paged().run {
            removeEdits(asset)
            removePages(asset)
            removeParts(asset)
            removeJobs(asset)
        }
    }

    fun transcript(
        identity: AudioIdentity
    ): com.monumentogram.dora.audio.transcript.TranscriptDocument? {
        requireOperation(identity)
        requireMutableSource(identity)
        if (uncertain || database.inTransaction()) throw UncertainAudioSourceState()
        val row = dao.transcript(identity.assetId.value) ?: return null
        return com.monumentogram.dora.audio.transcript.TranscriptCodec.decode(row.payload).also {
            doc ->
            check(doc.source.identity == identity && doc.revision == row.revision)
            val source = checkNotNull(dao.originalReference(identity.assetId.value))
            check(
                source.digest == doc.source.digest &&
                    source.version == doc.source.version &&
                    source.frames == doc.source.frames
            )
        }
    }

    fun updateTranscript(
        identity: AudioIdentity,
        transform:
            (
                com.monumentogram.dora.audio.transcript.TranscriptDocument?
            ) -> com.monumentogram.dora.audio.transcript.TranscriptDocument,
    ): com.monumentogram.dora.audio.transcript.TranscriptDocument {
        requireOperation(identity)
        requireMutableSource(identity)
        requireMutable()
        val old = transcript(identity)
        val next = transform(old)
        check(next.source.identity == identity)
        val source = checkNotNull(dao.originalReference(identity.assetId.value))
        check(
            source.digest == next.source.digest &&
                source.version == next.source.version &&
                source.frames == next.source.frames
        )
        if (next == old) return next
        check(next.revision == Math.addExact(old?.revision ?: 0, 1))
        val payload = com.monumentogram.dora.audio.transcript.TranscriptCodec.encode(next)
        try {
            commits.commit({
                if (old == null)
                    dao.insert(TranscriptEntity(identity.assetId.value, next.revision, payload))
                else
                    check(
                        dao.replaceTranscript(
                            identity.assetId.value,
                            old.revision,
                            next.revision,
                            payload,
                        ) == 1
                    )
            }) {
                val retained = dao.transcript(identity.assetId.value)
                retained != null &&
                    retained.revision == next.revision &&
                    retained.payload.contentEquals(payload)
            }
        } finally {
            payload.fill(0)
        }
        return next
    }

    fun transcriptHistory(): List<AudioIdentity> {
        checkOpen()
        if (uncertain || database.inTransaction()) throw UncertainAudioSourceState()
        return dao.transcriptHistory(binding.ownerId, binding.vaultId)
            .map { it.identity() }
            .filter { !isProtected(it) }
            .filter { identity ->
                try {
                    diagnostic.requireNewSource(identity)
                    true
                } catch (_: IllegalArgumentException) {
                    false
                }
            }
    }

    @Suppress(
        "LongMethod"
    ) // Validate source provenance then atomically insert the exact immutable row.
    fun retainSegmentation(identity: AudioIdentity, metadata: SegmentationMetadata) {
        requireOperation(identity)
        requireMutable()
        diagnostic.requireComponent(metadata.segmentId)
        metadata.captureEpochId?.let(diagnostic::requireComponent)
        require(metadata.kind != SegmentationKind.RECORDING_ORIGIN)
        check(sourceState(identity) == null)
        val source = checkNotNull(catalog.load(identity))
        val frames = source.segments.sumOf { it.frames }
        metadata.validate(frames)
        val row = metadata.entity(identity)
        val old = dao.segmentation(row.assetId, row.recordKey)
        if (old != null) {
            check(old == row)
            return
        }
        if (metadata.kind == SegmentationKind.TECHNICAL_ABORT) {
            validateAbort(identity, metadata, source, frames)
        } else if (
            metadata.kind == SegmentationKind.TECHNICAL_OPEN ||
                metadata.kind == SegmentationKind.TECHNICAL_CLOSE
        ) {
            val units =
                source.segments.filter { it.identity.physicalSegmentId == metadata.segmentId }
            validateTechnicalRange(metadata, source, units, frames)
            val epochStart =
                if (metadata.captureEpochId == metadata.segmentId) metadata.firstFrame
                else
                    checkNotNull(
                            dao.segmentation(
                                identity.assetId.value,
                                "TECHNICAL_OPEN:${metadata.captureEpochId}",
                            )
                        )
                        .firstFrame
            check(
                (metadata.reason == "CAP" || metadata.kind == SegmentationKind.TECHNICAL_CLOSE) ||
                    metadata.captureEpochId == metadata.segmentId
            )
            check(
                dao.segmentation(identity.assetId.value, "TECHNICAL_ABORT:${metadata.segmentId}") ==
                    null
            )
            check(
                metadata.firstFrame >= epochStart &&
                    (metadata.firstFrame - epochStart) %
                        com.monumentogram.dora.vad.SegmentationProfile.FROZEN.technicalCapFrames ==
                        0L
            )
            check(
                metadata.overlapFirstFrame ==
                    if (metadata.firstFrame == epochStart) null
                    else
                        maxOf(
                            epochStart,
                            metadata.firstFrame -
                                com.monumentogram.dora.vad.SegmentationProfile.FROZEN.overlapFrames,
                        )
            )
            if (metadata.kind == SegmentationKind.TECHNICAL_CLOSE) {
                check(metadata.endFrame == units.last().identity.firstFrame + units.last().frames)
                val opened =
                    checkNotNull(
                        dao.segmentation(
                            identity.assetId.value,
                            "TECHNICAL_OPEN:${metadata.segmentId}",
                        )
                    )
                check(
                    opened.captureEpochId == metadata.captureEpochId &&
                        opened.overlapFirstFrame == metadata.overlapFirstFrame
                )
            }
        }
        commits.commit({ dao.insert(row) }) { dao.segmentation(row.assetId, row.recordKey) == row }
    }

    private fun validateTechnicalRange(
        metadata: SegmentationMetadata,
        source: StoredAudioAsset,
        units: List<StoredAudioSegment>,
        frames: Long,
    ) {
        if (units.isEmpty()) {
            check(metadata.kind == SegmentationKind.TECHNICAL_OPEN)
            check(
                source.pending == null &&
                    source.finalization == null &&
                    metadata.firstFrame == frames
            )
            check(metadata.reason != "START" || frames == 0L)
            check(metadata.reason != "RESUME" || frames > 0L)
        } else {
            check(units.first().identity.physicalFirstFrame == metadata.firstFrame)
            check(metadata.endFrame <= units.last().identity.firstFrame + units.last().frames)
        }
    }

    private fun validateAbort(
        identity: AudioIdentity,
        metadata: SegmentationMetadata,
        source: StoredAudioAsset,
        frames: Long,
    ) {
        check(source.pending == null && source.finalization == null)
        check(source.segments.none { it.identity.physicalSegmentId == metadata.segmentId })
        check(metadata.firstFrame == frames)
        val opened =
            checkNotNull(
                dao.segmentation(identity.assetId.value, "TECHNICAL_OPEN:${metadata.segmentId}")
            )
        check(opened.firstFrame == metadata.firstFrame && opened.endFrame == metadata.endFrame)
        check(
            opened.captureEpochId == metadata.captureEpochId &&
                opened.overlapFirstFrame == metadata.overlapFirstFrame
        )
        check(
            dao.segmentation(identity.assetId.value, "TECHNICAL_CLOSE:${metadata.segmentId}") ==
                null
        )
    }

    fun segmentationPage(identity: AudioIdentity, afterKey: String): List<SegmentationMetadata> {
        requireOperation(identity)
        check(sourceState(identity) == null)
        val source = checkNotNull(catalog.load(identity))
        val frames = source.segments.sumOf { it.frames }
        return dao.segmentationPage(identity.assetId.value, afterKey).map { row ->
            try {
                SegmentationMetadata(
                        SegmentationKind.valueOf(row.kind),
                        row.segmentId,
                        row.firstFrame,
                        row.endFrame,
                        row.captureEpochId,
                        row.overlapFirstFrame,
                        row.reason,
                        row.degraded,
                        row.profileId,
                        row.profileSha256,
                    )
                    .also {
                        it.validate(frames)
                        require(it.key == row.recordKey)
                        if (it.kind == SegmentationKind.RECORDING_ORIGIN)
                            require(it == SegmentationMetadata.origin(identity))
                    }
            } catch (_: IllegalArgumentException) {
                throw com.monumentogram.dora.audio.InvalidSegmentationMetadata()
            }
        }
    }

    /** Bounded encrypted discovery; callers authenticate each source before describing audio. */
    fun recordingCandidates(after: String): List<AudioIdentity> =
        recordingCandidatePage(after).mapNotNull { it.second }

    fun recordingCandidatePage(after: String): List<Pair<String, AudioIdentity?>> {
        checkOpen()
        if (uncertain || database.inTransaction()) throw UncertainAudioSourceState()
        return dao.recordingCandidates(binding.ownerId, binding.vaultId, after).map {
            it.assetId to
                try {
                    it.identity()
                } catch (_: IllegalArgumentException) {
                    null
                }
        }
    }

    fun findRecording(recordingId: com.monumentogram.dora.model.alpha.RecordingId): AudioIdentity? {
        checkOpen()
        if (uncertain || database.inTransaction()) throw UncertainAudioSourceState()
        val selected = dao.recordingAssets(recordingId.value).singleOrNull() ?: return null
        val row = checkNotNull(dao.asset(selected))
        check(row.ownerId == binding.ownerId && row.vaultId == binding.vaultId)
        return row.identity().also { diagnostic.requireNewSource(it) }
    }

    /**
     * Journal-only display projection. No PCM reconciliation, source repair or resume authority.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod", "ComplexCondition")
    fun recordingLibraryMetadata(identity: AudioIdentity): RecordingLibraryMetadata {
        requireOperation(identity)
        if (uncertain || database.inTransaction()) throw UncertainAudioSourceState()
        val revision = catalogRevision()
        fun valid(condition: Boolean) {
            if (!condition) throw CorruptRecordingLibraryMetadata()
        }
        fun observed(metadata: RecordingLibraryMetadata): RecordingLibraryMetadata {
            if (catalogRevision() != revision) throw UncertainAudioSourceState()
            return metadata
        }
        val asset = exactAsset(identity) ?: throw CorruptRecordingLibraryMetadata()
        dao.tombstone(asset.assetId)?.let {
            valid(it.state in setOf("DELETING", "USER_DELETED"))
            return observed(
                RecordingLibraryMetadata(
                    identity,
                    0,
                    if (it.state == "DELETING") RecordingLibraryState.DELETION_PENDING
                    else RecordingLibraryState.DELETED,
                )
            )
        }
        valid(dao.invalidLibraryClaim(asset.assetId, AlphaRecordingLimit.MAX_ALPHA_FRAMES) == null)
        val stats = dao.libraryClaimStats(asset.assetId)
        val tail = dao.lastLibraryClaim(asset.assetId)
        valid(stats.unitCount in 0..AlphaRecordingLimit.MAX_ALPHA_FRAMES)
        valid(
            stats.committedCount in 0..stats.unitCount &&
                stats.unitCount - stats.committedCount <= 1
        )
        valid(
            stats.committedFrames in 0..AlphaRecordingLimit.MAX_ALPHA_FRAMES &&
                stats.reservedFrames in stats.committedFrames..AlphaRecordingLimit.MAX_ALPHA_FRAMES
        )
        valid(
            if (stats.unitCount == 0L)
                tail == null && stats.firstOrdinal == null && stats.lastOrdinal == null
            else
                tail != null &&
                    stats.firstOrdinal == 0L &&
                    stats.lastOrdinal == stats.unitCount - 1 &&
                    tail.ordinal.toLong() == stats.lastOrdinal &&
                    Math.addExact(tail.firstFrame, tail.frames) == stats.reservedFrames
        )
        tail?.let {
            canonical(it.runId)
            canonical(it.physicalId)
        }
        val receipt = dao.intent(asset.assetId)
        val pending = receipt?.takeUnless { it.applied }
        val finals = dao.libraryFinalizationStats(asset.assetId)
        valid(finals.invalid == 0L)
        if (asset.finalized || pending?.kind == "FINALIZE") {
            valid(
                stats.unitCount > 0 &&
                    stats.unitCount == stats.committedCount &&
                    finals.unitCount == stats.unitCount &&
                    finals.frames == stats.committedFrames
            )
        } else valid(finals.unitCount == 0L)
        when (receipt?.kind) {
            null -> valid(stats.unitCount == stats.committedCount)
            "APPEND" -> {
                valid(!asset.finalized && tail?.runId == receipt.appendRunId)
                valid(
                    if (receipt.applied)
                        stats.unitCount == stats.committedCount && tail?.committed == true
                    else stats.unitCount == stats.committedCount + 1 && tail?.committed == false
                )
            }
            "FINALIZE" ->
                valid(
                    receipt.appendRunId == null &&
                        stats.unitCount == stats.committedCount &&
                        asset.finalized == receipt.applied
                )
            else -> valid(false)
        }
        var loss: AudioFailure? = null
        dao.originalReference(asset.assetId)?.let {
            valid(
                asset.finalized &&
                    it.version == 1 &&
                    it.frames == stats.committedFrames &&
                    it.digest.matches(Regex("[0-9a-f]{64}"))
            )
            loss =
                it.unavailableReason?.let { reason ->
                    val failure = AudioFailure.entries.firstOrNull { entry -> entry.name == reason }
                    valid(failure in OriginalAudioLifecycle.permanentFailures)
                    failure
                }
        }
        val collision = dao.recordingAssets(identity.recordingId.value) != listOf(asset.assetId)
        return observed(
            RecordingLibraryMetadata(
                identity,
                stats.committedFrames,
                when {
                    loss != null || collision -> RecordingLibraryState.UNAVAILABLE
                    asset.finalized -> RecordingLibraryState.FINALIZED
                    else -> RecordingLibraryState.NEEDS_RECOVERY
                },
                loss ?: if (collision) AudioFailure.COLLISION else null,
            )
        )
    }

    val bootstrapJournal: RecoveryRunBootstrapJournal = BootstrapJournal()
    val microfileJournal: RecoveryMicrofileJournal = MicrofileJournal()
    val quarantineJournal: RecoveryQuarantineJournal = QuarantineJournal()
    val sourceOwner: String
        get() = binding.ownerId

    val sourceVault: String
        get() = binding.vaultId

    /** Multiple immutable assets remain preserved, but cannot select their own current version. */
    fun originalSourceState(identity: AudioIdentity): AudioSourceState? {
        sourceState(identity)?.let {
            return it
        }
        if (dao.recordingAssets(identity.recordingId.value) != listOf(identity.assetId.value))
            return AudioSourceState.Unavailable(AudioFailure.COLLISION)
        return null
    }

    fun originalSourceLoss(reference: OriginalAudioReference): AudioFailure? {
        requireOperation(reference.identity)
        val row = dao.originalReference(reference.identity.assetId.value) ?: return null
        check(
            row.version == reference.version &&
                row.digest == reference.digest &&
                row.frames == reference.frames
        )
        return row.unavailableReason?.let {
            AudioFailure.valueOf(it).also { failure ->
                check(failure in OriginalAudioLifecycle.permanentFailures)
            }
        }
    }

    /** Derived only after complete authentication, or as explicit permanent-loss evidence. */
    fun retainOriginalReference(reference: OriginalAudioReference, failure: AudioFailure?) {
        requireOperation(reference.identity)
        requireMutable()
        check(sourceState(reference.identity) == null)
        val source = checkNotNull(catalog.load(reference.identity))
        check(
            OriginalAudioReferenceCodec.derive(binding.ownerId, binding.vaultId, source) ==
                reference
        )
        check(failure == null || failure in OriginalAudioLifecycle.permanentFailures)
        val old = dao.originalReference(reference.identity.assetId.value)
        originalSourceLoss(reference)?.let {
            check(failure == it)
            return
        }
        val row =
            OriginalAudioReferenceEntity(
                reference.identity.assetId.value,
                reference.version,
                reference.digest,
                reference.frames,
                failure?.name,
            )
        if (row == old) return
        commits.commit({ if (old == null) dao.insert(row) else check(dao.update(row) == 1) }) {
            dao.originalReference(row.assetId) == row
        }
    }

    /** Borrow the caller's existing vault operation; never silently acquire a nested operation. */
    fun requireOperation(identity: AudioIdentity) {
        checkOpen()
        lease.requireHeld(identity.assetId.value)
        exactAsset(identity)
    }

    private fun checkOpen() {
        check(!closed)
    }

    private fun requireMutable() {
        check(!uncertain) { "Journal requires reopen and exact readback" }
        if (diagnostic.active) diagnostic.requireAsset(lease.ownedAsset())
    }

    fun isProtected(identity: AudioIdentity): Boolean = diagnostic.isProtected(identity)

    fun requireMutableSource(identity: AudioIdentity) {
        diagnostic.requireNewSource(identity)
    }

    fun requireMutableRun(run: RunId) {
        if (!diagnostic.active) return
        checkOpen()
        val claim = checkNotNull(dao.claim(run.toCanonicalString()))
        lease.requireHeld(claim.assetId)
        val owner = checkNotNull(dao.asset(claim.assetId))
        check(owner.ownerId == binding.ownerId && owner.vaultId == binding.vaultId)
        diagnostic.requireRun(claim.runId, owner.identity())
    }

    private fun exactAsset(identity: AudioIdentity): AssetEntity? =
        dao.asset(identity.assetId.value)?.also {
            check(
                it.identity() == identity &&
                    it.ownerId == binding.ownerId &&
                    it.vaultId == binding.vaultId
            )
        }

    private fun activeRun(runId: String): UnitClaimEntity {
        checkOpen()
        val claim = checkNotNull(dao.claim(runId))
        lease.requireHeld(claim.assetId)
        val asset = checkNotNull(dao.asset(claim.assetId))
        check(asset.ownerId == binding.ownerId && asset.vaultId == binding.vaultId)
        diagnostic.requireRun(runId, asset.identity())
        check(dao.tombstone(claim.assetId) == null)
        return claim
    }

    private fun SegmentationMetadata.entity(identity: AudioIdentity) =
        SegmentationEntity(
            identity.assetId.value,
            key,
            kind.name,
            segmentId,
            firstFrame,
            endFrame,
            captureEpochId,
            overlapFirstFrame,
            reason,
            degraded,
            profileId,
            profileSha256,
        )

    private inner class Catalog : EncryptedAudioCatalog {
        override fun isVerifiedAppendSnapshot(snapshot: StoredAudioAsset): Boolean {
            requireOperation(snapshot.identity)
            val cached = verifiedCatalog ?: return false
            return !uncertain &&
                !database.inTransaction() &&
                cached.asset === snapshot &&
                cached.revision == catalogRevision()
        }

        override fun tryAcquire(identity: AudioIdentity): AutoCloseable? {
            checkOpen()
            canonical(identity.sessionId)
            val operation = lease.tryAcquire(identity.assetId.value) ?: return null
            reservationSnapshot = null
            return AutoCloseable {
                lease.requireOwner()
                reservationSnapshot = null
                operation.close()
            }
        }

        override fun create(identity: AudioIdentity): Boolean = createAsset(identity, false)

        override fun createLogicalRecording(identity: AudioIdentity): Boolean =
            createAsset(identity, true)

        private fun createAsset(identity: AudioIdentity, logical: Boolean): Boolean {
            checkOpen()
            diagnostic.requireNewSource(identity)
            canonical(identity.sessionId)
            val operation = lease.tryAcquire(identity.assetId.value) ?: return false
            operation.use {
                requireMutable()
                if (dao.asset(identity.assetId.value) != null) return false
                val row =
                    AssetEntity(
                        identity.assetId.value,
                        identity.recordingId.value,
                        identity.sessionId,
                        binding.ownerId,
                        binding.vaultId,
                    )
                val origin =
                    if (logical) SegmentationMetadata.origin(identity).entity(identity) else null
                commits.commit({
                    dao.insert(row)
                    if (origin != null) dao.insert(origin)
                }) {
                    dao.asset(row.assetId) == row &&
                        (origin == null ||
                            dao.segmentation(row.assetId, origin.recordKey) == origin)
                }
                return true
            }
        }

        // Ordered source invariants are deliberately checked together before a snapshot is
        // returned.
        @Suppress("LongMethod", "CyclomaticComplexMethod")
        override fun load(identity: AudioIdentity): StoredAudioAsset? {
            // Do not let a failed read leave a prior observation reusable. Only the owner
            // may touch lease-local state, including when authorization has been revoked.
            lease.requireOwner()
            reservationSnapshot = null
            requireOperation(identity)
            if (uncertain || database.inTransaction()) throw UncertainAudioSourceState()
            val revision = catalogRevision()
            verifiedCatalog
                ?.takeIf { it.revision == revision && it.asset.identity == identity }
                ?.let {
                    val snapshot = it.asset
                    if (snapshot.pending == null && snapshot.finalization == null)
                        reservationSnapshot = snapshot
                    return snapshot
                }
            verifiedCatalog = null
            val asset = exactAsset(identity) ?: return null
            if (dao.tombstone(asset.assetId) != null) return null
            val claims = dao.claims(asset.assetId)
            // One lease still fences the complete snapshot. Bulk reads preserve every row
            // invariant below while avoiding three SQLCipher round trips per historic unit.
            val physical = dao.physicalSources(asset.assetId).associateBy { it.physicalId }
            val manifests = dao.assetManifests(asset.assetId).associateBy { it.runId }
            val microfiles = dao.assetMicrofiles(asset.assetId).associateBy { it.runId }
            var nextFrame = 0L
            claims.forEachIndexed { ordinal, row ->
                check(
                    row.ordinal == ordinal &&
                        row.firstFrame == nextFrame &&
                        row.frames in 1..MAX_UNIT_FRAMES
                )
                check(row.physicalFirstFrame >= 0 && row.sourceFrameOffset >= 0)
                check(
                    Math.addExact(row.physicalFirstFrame, row.sourceFrameOffset) == row.firstFrame
                )
                check(physical[row.physicalId]?.physicalFirstFrame == row.physicalFirstFrame)
                canonical(row.runId)
                canonical(row.physicalId)
                nextFrame = Math.addExact(nextFrame, row.frames)
            }
            val committed = claims.takeWhile { it.committed }
            check(claims.drop(committed.size).none { it.committed })
            val sources = committed.map { row ->
                val manifest = checkNotNull(manifests[row.runId])
                val unit = checkNotNull(microfiles[row.runId])
                validatePublication(row, unit.row(), manifest.row())
                check(unit.manifestDigest == manifest.digest)
                StoredAudioSegment(row.identity(identity), row.frames, manifest.digest.sha())
            }
            val receipt = dao.intent(asset.assetId)
            if (receipt?.applied == true) {
                when (receipt.kind) {
                    "APPEND" ->
                        check(
                            committed.lastOrNull()?.runId == receipt.appendRunId && !asset.finalized
                        )
                    "FINALIZE" -> check(asset.finalized && receipt.appendRunId == null)
                    else -> error("Journal receipt rejected")
                }
            }
            val storedIntent = receipt?.takeUnless { it.applied }
            val pending =
                when (storedIntent?.kind) {
                    null -> {
                        check(claims.size == committed.size)
                        null
                    }
                    "APPEND" -> {
                        check(!asset.finalized && claims.size == committed.size + 1)
                        val row = claims.last()
                        check(row.runId == storedIntent.appendRunId && !row.committed)
                        AudioIntent.Append(row.identity(identity), row.frames)
                    }
                    "FINALIZE" -> {
                        check(
                            !asset.finalized &&
                                storedIntent.appendRunId == null &&
                                claims.size == committed.size
                        )
                        check(sources.isNotEmpty())
                        verifyFinalSources(asset.assetId, sources)
                        AudioIntent.Finalize(sources.toList())
                    }
                    else -> error("Journal intent rejected")
                }
            if (asset.finalized) {
                check(pending == null && sources.isNotEmpty())
                verifyFinalSources(asset.assetId, sources)
            } else if (pending !is AudioIntent.Finalize)
                check(dao.finalSources(asset.assetId).isEmpty())
            val indexed = AudioSegmentIndex.from(sources)
            check(catalogRevision() == revision) { "Catalog changed during snapshot" }
            return StoredAudioAsset(
                    identity,
                    indexed,
                    pending,
                    if (asset.finalized) indexed else null,
                )
                .also { snapshot ->
                    verifiedCatalog = CatalogSnapshot(revision, snapshot)
                    val hasNoPendingOrFinalization =
                        snapshot.pending == null && snapshot.finalization == null
                    if (hasNoPendingOrFinalization && !uncertain && !database.inTransaction()) {
                        // Elements/identities/digests are immutable; only the exposed list needs
                        // copying. Ineligible pending/finalization lists are never retained.
                        reservationSnapshot = snapshot
                    }
                }
        }

        private fun reservationMatches(expected: StoredAudioAsset, intent: AudioIntent): Boolean {
            val observed = reservationSnapshot
            reservationSnapshot = null
            if (intent is AudioIntent.Append && observed != null && observed == expected)
                return true
            return try {
                load(expected.identity) == expected
            } finally {
                // The fallback precondition is consumed too, even if the intent is rejected.
                reservationSnapshot = null
            }
        }

        // Keep precondition, mutation and exact durable readback together for lease review.
        @Suppress("LongMethod", "CyclomaticComplexMethod")
        override fun reserve(expected: StoredAudioAsset, intent: AudioIntent): Boolean {
            requireOperation(expected.identity)
            requireMutable()
            if (
                !reservationMatches(expected, intent) ||
                    expected.pending != null ||
                    expected.finalization != null
            )
                return false
            val assetId = expected.identity.assetId.value
            when (intent) {
                is AudioIntent.Append -> {
                    diagnostic.requireRun(intent.identity.unitId, expected.identity)
                    diagnostic.requireComponent(intent.identity.physicalSegmentId)
                    if (!validAppend(expected, intent) || dao.claim(intent.identity.unitId) != null)
                        return false
                    val physical =
                        PhysicalEntity(
                            assetId,
                            intent.identity.physicalSegmentId,
                            intent.identity.physicalFirstFrame,
                        )
                    val existing = dao.physical(assetId, physical.physicalId)
                    if (existing != null && existing != physical) return false
                    val unit = intent.identity
                    val claim =
                        UnitClaimEntity(
                            unit.unitId,
                            assetId,
                            unit.ordinal,
                            unit.firstFrame,
                            intent.frames,
                            unit.physicalSegmentId,
                            unit.physicalFirstFrame,
                            unit.sourceFrameOffset,
                        )
                    val revision = catalogRevision()
                    val oldReceipt = dao.intent(assetId)
                    commits.commit({
                        check(catalogRevision() == revision)
                        if (existing == null) dao.insert(physical)
                        dao.insert(claim)
                        dao.removeIntent(assetId)
                        dao.insert(IntentEntity(assetId, "APPEND", claim.runId))
                    }) {
                        dao.claim(claim.runId) == claim &&
                            dao.physical(assetId, physical.physicalId) == physical &&
                            dao.intent(assetId) == IntentEntity(assetId, "APPEND", claim.runId) &&
                            retainCatalogDelta(
                                revision,
                                2L +
                                    (if (existing == null) 1 else 0) +
                                    (if (oldReceipt == null) 0 else 1),
                                expected.copy(pending = intent),
                            )
                    }
                }
                is AudioIntent.Finalize -> {
                    if (intent.sources.isEmpty() || intent.sources != expected.segments)
                        return false
                    commits.commit({
                        dao.removeIntent(assetId)
                        dao.insert(IntentEntity(assetId, "FINALIZE", null))
                        intent.sources.forEach { dao.insert(it.finalSource()) }
                    }) {
                        load(expected.identity) == expected.copy(pending = intent)
                    }
                }
            }
            return true
        }

        // Append/finalize retain their explicit transaction and post-commit failure gates.
        @Suppress("ComplexCondition", "LongMethod", "CyclomaticComplexMethod")
        override fun compareAndSet(expected: StoredAudioAsset, next: StoredAudioAsset): Boolean {
            requireOperation(expected.identity)
            requireMutable()
            if (
                next.identity != expected.identity ||
                    load(expected.identity) != expected ||
                    next.pending != null ||
                    expected.finalization != null
            )
                return false
            when (val pending = expected.pending) {
                is AudioIntent.Append -> {
                    val manifest = dao.manifest(pending.identity.unitId) ?: return false
                    val unit = dao.microfile(pending.identity.unitId) ?: return false
                    val claim = checkNotNull(dao.claim(pending.identity.unitId))
                    validatePublication(claim, unit.row(), manifest.row())
                    val source =
                        StoredAudioSegment(pending.identity, pending.frames, manifest.digest.sha())
                    if (
                        next !=
                            expected.copy(
                                segments = AudioSegmentIndex.append(expected.segments, source),
                                pending = null,
                            )
                    )
                        return false
                    val revision = catalogRevision()
                    val receipt = checkNotNull(dao.intent(claim.assetId))
                    val committed = claim.copy(committed = true)
                    val applied = receipt.copy(applied = true)
                    commits.commit({
                        check(catalogRevision() == revision)
                        check(dao.update(committed) == 1)
                        check(dao.update(applied) == 1)
                    }) {
                        dao.claim(claim.runId) == committed &&
                            dao.intent(claim.assetId) == applied &&
                            dao.microfile(claim.runId) == unit &&
                            dao.manifest(claim.runId) == manifest &&
                            retainCatalogDelta(revision, 2, next)
                    }
                }
                is AudioIntent.Finalize -> {
                    if (
                        pending.sources != expected.segments ||
                            pending.sources.isEmpty() ||
                            next != expected.copy(pending = null, finalization = pending.sources)
                    )
                        return false
                    val asset = checkNotNull(exactAsset(expected.identity))
                    commits.commit({
                        check(dao.update(asset.copy(finalized = true)) == 1)
                        check(
                            dao.update(
                                checkNotNull(dao.intent(asset.assetId)).copy(applied = true)
                            ) == 1
                        )
                    }) {
                        load(expected.identity) == next
                    }
                }
                null -> return false
            }
            return true
        }
    }

    private fun validAppend(asset: StoredAudioAsset, intent: AudioIntent.Append): Boolean =
        try {
            val unit = intent.identity
            canonical(unit.unitId)
            canonical(unit.physicalSegmentId)
            val end =
                asset.segments.lastOrNull()?.let {
                    Math.addExact(it.identity.firstFrame, it.frames)
                } ?: 0L
            unit.audio == asset.identity &&
                unit.ordinal == asset.segments.size &&
                unit.firstFrame == end &&
                intent.frames in 1..MAX_UNIT_FRAMES &&
                unit.physicalFirstFrame >= 0 &&
                unit.sourceFrameOffset >= 0 &&
                Math.addExact(unit.physicalFirstFrame, unit.sourceFrameOffset) == end &&
                Math.addExact(end, intent.frames) > end
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: ArithmeticException) {
            false
        }

    private fun StoredAudioSegment.finalSource() =
        FinalSourceEntity(
            identity.audio.assetId.value,
            identity.ordinal,
            identity.unitId,
            1,
            manifestDigest.hex(),
            frames,
        )

    private fun verifyFinalSources(asset: String, sources: List<StoredAudioSegment>) {
        check(dao.finalSources(asset) == sources.map { it.finalSource() })
    }

    fun loadBootstrapIdentity(runId: RunId): StoredKeyConfirmationIdentity? {
        val id = runId.toCanonicalString()
        activeRun(id)
        return dao.bootstrap(id)?.also { validateBootstrap(it.row()) }?.confirmation()
    }

    /** Only a committed bootstrap belonging to this fenced asset establishes key ownership. */
    fun hasCommittedDeletionBootstrap(identity: AudioIdentity, runId: RunId): Boolean {
        requireOperation(identity)
        requireMutable()
        check(!database.inTransaction())
        val asset = checkNotNull(exactAsset(identity))
        check(checkNotNull(dao.tombstone(asset.assetId)).state == "DELETING")
        val id = runId.toCanonicalString()
        val claim = checkNotNull(dao.claim(id))
        check(claim.runId == id && claim.assetId == asset.assetId)
        val bootstrap = dao.bootstrap(id) ?: return false
        check(bootstrap.runId == claim.runId)
        validateBootstrap(bootstrap.row())
        return true
    }

    fun loadCandidateSnapshot(runId: RunId): RecoveryCandidateSnapshot {
        val id = runId.toCanonicalString()
        val claim = activeRun(id)
        val bootstrap = dao.bootstrap(id)?.also { validateBootstrap(it.row()) }
        val unit = dao.microfile(id)
        val publication = dao.manifest(id)
        check((unit == null) == (publication == null))
        if (unit != null && publication != null) {
            validatePublication(claim, unit.row(), publication.row())
            check(unit.manifestDigest == publication.digest)
        }
        return RecoveryCandidateSnapshot(
            bootstrap?.let {
                listOf(
                    CandidateBootstrapRow(
                        it.runId,
                        it.candidateId,
                        KeyConfirmationState.valueOf(it.state),
                    )
                )
            } ?: emptyList(),
            unit?.let { listOf(it.row()) } ?: emptyList(),
            publication?.let { listOf(it.row()) } ?: emptyList(),
        )
    }

    fun loadAllQuarantine(runId: RunId): List<RecoveryQuarantineIntentRow> {
        val id = runId.toCanonicalString()
        activeRun(id)
        return dao.quarantines(id).map { it.row() }
    }

    fun loadPendingQuarantine(runId: RunId) =
        loadAllQuarantine(runId).filter { it.state == QuarantineIntentState.PENDING }

    fun loadRetainedMicrofileSource(
        runId: RunId,
        relativeName: String,
    ): RecoveryQuarantineIntentRow? {
        val id = runId.toCanonicalString()
        activeRun(id)
        // Keep database access outside structural validation: storage/read failures remain
        // operational.
        val rows = dao.quarantines(id).filter { it.sourceName == relativeName }
        return try {
            check(rows.size <= 1)
            rows.singleOrNull()?.row()?.also {
                check(
                    it.bootstrapBinding == QuarantineBootstrapBinding.PRESENT &&
                        it.state == QuarantineIntentState.COMPLETED &&
                        it.recordedObservedState in
                            setOf(
                                RecoveryQuarantineObservedState.REFERENCED_REJECTED,
                                RecoveryQuarantineObservedState.REFERENCED_DEPENDENT,
                            )
                )
            }
        } catch (_: IllegalArgumentException) {
            throw RecoveryMicrofileQuarantineReadbackException()
        } catch (_: IllegalStateException) {
            throw RecoveryMicrofileQuarantineReadbackException()
        }
    }

    private inner class WriteSession {
        private var ended = false
        private var successful = false
        private val readbacks = mutableListOf<() -> Boolean>()
        private var catalogBefore: CatalogSnapshot? = null
        private var catalogMutations = 0L
        private var knownCatalogWrites = true

        init {
            checkOpen()
            requireMutable()
            lease.requireHeld()
            check(!database.inTransaction())
            reservationSnapshot = null
            database.beginTransaction()
            catalogBefore = verifiedCatalog?.takeIf { it.revision == catalogRevision() }
        }

        fun write(
            exactReadback: () -> Boolean,
            catalogChanges: Long? = null,
            mutation: () -> Unit,
        ) {
            lease.requireHeld()
            check(!ended && !successful)
            mutation()
            if (catalogChanges == null) knownCatalogWrites = false
            else catalogMutations = Math.addExact(catalogMutations, catalogChanges)
            readbacks += exactReadback
        }

        fun markSuccessful() {
            lease.requireHeld()
            check(!ended && !successful && readbacks.isNotEmpty())
            database.setTransactionSuccessful()
            successful = true
        }

        // Every driver failure must fence the journal and propagate; cleanup does not swallow it.
        @Suppress("TooGenericExceptionCaught")
        fun end() {
            lease.requireOwner()
            check(!ended)
            try {
                database.endTransaction()
                if (successful) {
                    check(readbacks.all { it() }) { "Journal readback rejected" }
                    val before = catalogBefore
                    if (before != null && knownCatalogWrites) {
                        check(retainCatalogDelta(before.revision, catalogMutations, before.asset))
                    } else verifiedCatalog = null
                    maintainWalAfterReadback()
                }
            } catch (error: Exception) {
                reservationSnapshot = null
                verifiedCatalog = null
                uncertain = true
                throw error
            } finally {
                ended = true
            }
        }
    }

    private inner class BootstrapJournal : RecoveryRunBootstrapJournal {
        override fun beginNonExclusive(): RecoveryRunBootstrapTransaction {
            val session = WriteSession()
            return object : RecoveryRunBootstrapTransaction {
                override fun insert(value: RecoveryBootstrapRunRow) {
                    val claim = activeRun(value.runId)
                    check(
                        !claim.committed &&
                            dao.intent(claim.assetId) ==
                                IntentEntity(claim.assetId, "APPEND", claim.runId)
                    )
                    validateBootstrap(value)
                    val row = value.entity()
                    session.write({ dao.bootstrap(row.runId) == row }, catalogChanges = 1) {
                        dao.insert(row)
                    }
                }

                override fun markSuccessful() = session.markSuccessful()

                override fun end() = session.end()
            }
        }
    }

    private inner class MicrofileJournal : RecoveryMicrofileJournal {
        override fun loadSnapshot(runId: RunId) = loadCandidateSnapshot(runId)

        override fun beginNonExclusive(): RecoveryMicrofileTransaction {
            val session = WriteSession()
            return object : RecoveryMicrofileTransaction {
                override fun insert(
                    unit: RecoveryMicrofileUnitRow,
                    publication: RecoveryManifestPublicationRow,
                ) {
                    val claim = activeRun(unit.runId)
                    check(
                        !claim.committed &&
                            dao.intent(claim.assetId) ==
                                IntentEntity(claim.assetId, "APPEND", claim.runId)
                    )
                    validateBootstrap(checkNotNull(dao.bootstrap(unit.runId)).row())
                    validatePublication(claim, unit, publication)
                    val manifest = publication.entity()
                    val row = unit.entity(publication)
                    session.write(
                        {
                            dao.microfile(row.runId) == row &&
                                dao.manifest(manifest.runId) == manifest
                        },
                        catalogChanges = 2,
                    ) {
                        dao.insert(manifest)
                        dao.insert(row)
                    }
                }

                override fun markSuccessful() = session.markSuccessful()

                override fun end() = session.end()
            }
        }
    }

    private inner class QuarantineJournal : RecoveryQuarantineJournal {
        override fun load(intentId: Sha256Value): RecoveryQuarantineIntentRow? {
            checkOpen()
            lease.requireHeld()
            return dao.quarantine(intentId.hex())?.also { activeRun(it.runId) }?.row()
        }

        override fun loadBySource(
            input: RecoveryQuarantineIntentInput
        ): RecoveryQuarantineIntentRow? =
            loadAllQuarantine(input.runId)
                .filter {
                    it.input.candidate == input.candidate &&
                        it.input.sourceRelativeName == input.sourceRelativeName &&
                        it.input.sourceSha256 == input.sourceSha256
                }
                .also { check(it.size <= 1) }
                .singleOrNull()

        override fun loadPending(runId: RunId) = loadPendingQuarantine(runId)

        override fun beginNonExclusive(): RecoveryQuarantineTransaction {
            val session = WriteSession()
            return object : RecoveryQuarantineTransaction {
                override fun insert(row: RecoveryQuarantineIntentRow) {
                    activeRun(row.input.runId.toCanonicalString())
                    check(row.state == QuarantineIntentState.PENDING)
                    val entity = row.entity()
                    check(entity.row() == row)
                    check(
                        (dao.bootstrap(entity.runId) != null) ==
                            (row.bootstrapBinding == QuarantineBootstrapBinding.PRESENT)
                    )
                    session.write({ dao.quarantine(entity.intentId) == entity }) {
                        dao.insert(entity)
                    }
                }

                override fun complete(intentId: Sha256Value) {
                    val old = checkNotNull(load(intentId))
                    check(old.state == QuarantineIntentState.PENDING)
                    session.write({
                        load(intentId) == old.copy(state = QuarantineIntentState.COMPLETED)
                    }) {
                        check(dao.completeQuarantine(intentId.hex()) == 1)
                    }
                }

                override fun markSuccessful() = session.markSuccessful()

                override fun end() = session.end()
            }
        }
    }

    private fun validateBootstrap(row: RecoveryBootstrapRunRow) {
        val value =
            KeyConfirmationValue(RecoveryCandidate.MICROFILE, RunId.fromCanonicalString(row.runId))
        check(
            row.candidateId == value.candidate.contractId &&
                row.keyConfirmationState == KeyConfirmationState.VALID
        )
        check(
            row.keyConfirmationRelativeName == "key-confirmation/run.kc" &&
                row.keyConfirmationBytes > 0
        )
        check(row.canonicalAliasSha256 == value.canonicalAliasSha256)
    }

    private fun validatePublication(
        claim: UnitClaimEntity,
        unit: RecoveryMicrofileUnitRow,
        manifest: RecoveryManifestPublicationRow,
    ) {
        check(unit.runId == claim.runId && manifest.runId == claim.runId)
        check(
            unit.candidateId == RecoveryCandidate.MICROFILE.contractId &&
                manifest.candidateId == unit.candidateId
        )
        check(
            unit.unitIndex == 0UL &&
                unit.plaintextStartInclusive == 0UL &&
                unit.plaintextEndExclusive == (claim.frames * 2).toULong()
        )
        check(
            unit.manifestGeneration == 1UL &&
                manifest.generation == 1UL &&
                manifest.publicationKind == PublicationKind.MANIFEST
        )
        check(
            manifest.committedEndExclusive == unit.plaintextEndExclusive &&
                manifest.previousPublicationCiphertextSha256 == Sha256Value.ZERO
        )
        check(unit.state == "VALID" && manifest.state == "VALID" && unit.cadenceSeconds == 5UL)
        check(
            unit.ciphertextBytes > 0 &&
                unit.keyEnvelopeBytes > 0 &&
                manifest.publicationBytes > 0 &&
                manifest.keyEnvelopeBytes > 0
        )
        RecoveryRelativeNames.validateMicrofileCiphertext(unit.ciphertextRelativeName, 0UL)
        RecoveryRelativeNames.validateMicrofileKeyEnvelope(unit.keyEnvelopeRelativeName, 0UL)
        RecoveryRelativeNames.validateManifestCiphertext(manifest.publicationRelativeName, 1UL)
        RecoveryRelativeNames.validateManifestKeyEnvelope(manifest.keyEnvelopeRelativeName, 1UL)
        check(
            unit.processingIntentId ==
                RecoveryProcessingIntent.calculate(
                    RecoveryProcessingIntentInput(
                        RecoveryCandidate.MICROFILE,
                        RunId.fromCanonicalString(claim.runId),
                        0UL,
                        0UL,
                        unit.plaintextEndExclusive,
                        unit.ciphertextSha256,
                    )
                )
        )
    }

    /** Durable tombstone and a frozen inventory precede every destructive caller action. */
    // Freeze the complete inventory in one explicit phase before any caller can delete artifacts.
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun beginDeletion(
        identity: AudioIdentity,
        discovered: List<AudioDeletionTarget>,
        eraseTranscript: Boolean = false,
        includeSteps: Boolean = true,
    ): AudioDeletionSnapshot {
        requireOperation(identity)
        requireMutable()
        checkNotNull(exactAsset(identity))
        loadDeletion(identity, includeSteps)?.let {
            if (eraseTranscript) eraseRecordingText(identity)
            return it
        }
        val asset = identity.assetId.value
        val claims = dao.claims(asset)
        val runs = claims.map { it.runId }.toSet()
        check(discovered.size <= MAX_DELETION_TARGETS)
        discovered.forEach { target ->
            check(target.runId in runs)
            check(
                target.kind in
                    setOf(
                        AudioDeletionTargetKind.ARTIFACT,
                        AudioDeletionTargetKind.QUARANTINE_ARTIFACT,
                    )
            )
            check(
                target.relativeName.isNotEmpty() &&
                    target.relativeName.length <= MAX_RELATIVE_NAME_LENGTH
            )
            check(
                !target.relativeName.startsWith('/') &&
                    '\\' !in target.relativeName &&
                    ':' !in target.relativeName
            )
            check(target.relativeName.split('/').none { it.isEmpty() || it == "." || it == ".." })
            target.digest?.sha()
        }
        val targets =
            mutableListOf<AudioDeletionTarget>().apply {
                addAll(discovered)
                claims.forEach { claim ->
                    val run = claim.runId
                    add(AudioDeletionTarget(run, AudioDeletionTargetKind.RUN_DIRECTORY, ""))
                    add(
                        AudioDeletionTarget(
                            run,
                            AudioDeletionTargetKind.KEY_REFERENCE,
                            "",
                            CanonicalRecoveryAlias.sha256(RunId.fromCanonicalString(run)).hex(),
                        )
                    )
                    dao.bootstrap(run)?.let {
                        add(
                            AudioDeletionTarget(
                                run,
                                AudioDeletionTargetKind.ARTIFACT,
                                it.relativeName,
                                it.digest,
                            )
                        )
                    }
                    dao.microfile(run)?.let {
                        add(
                            AudioDeletionTarget(
                                run,
                                AudioDeletionTargetKind.ARTIFACT,
                                it.relativeName,
                                it.digest,
                            )
                        )
                        add(
                            AudioDeletionTarget(
                                run,
                                AudioDeletionTargetKind.ARTIFACT,
                                it.keyName,
                                it.keyDigest,
                            )
                        )
                    }
                    dao.manifest(run)?.let {
                        add(
                            AudioDeletionTarget(
                                run,
                                AudioDeletionTargetKind.ARTIFACT,
                                it.relativeName,
                                it.digest,
                            )
                        )
                        add(
                            AudioDeletionTarget(
                                run,
                                AudioDeletionTargetKind.ARTIFACT,
                                it.keyName,
                                it.keyDigest,
                            )
                        )
                    }
                    dao.quarantines(run).forEach {
                        add(
                            AudioDeletionTarget(
                                run,
                                AudioDeletionTargetKind.QUARANTINE_ARTIFACT,
                                it.destinationName,
                                it.sourceDigest,
                            )
                        )
                    }
                }
            }
        val byName =
            linkedMapOf<Triple<String, AudioDeletionTargetKind, String>, AudioDeletionTarget>()
        targets.forEach {
            val previous = byName.putIfAbsent(Triple(it.runId, it.kind, it.relativeName), it)
            check(previous == null || previous.digest == it.digest)
        }
        val rows =
            byName.values
                .map {
                    DeletionTargetEntity(asset, it.runId, it.kind.name, it.relativeName, it.digest)
                }
                .sortedWith(compareBy({ it.runId }, { it.kind }, { it.relativeName }))
        targets.clear()
        byName.clear()
        val tombstone = TombstoneEntity(asset, UUID.randomUUID().toString(), "DELETING")
        commits.commit({
            dao.insert(tombstone)
            dao.insertDeletionTargets(rows)
            if (eraseTranscript) {
                removePagedTranscript(asset)
                dao.removeTranscript(asset)
                dao.removeSegmentation(asset)
            }
        }) {
            var ordinal = 0
            var exact = true
            deletionRows(identity) { row ->
                if (ordinal >= rows.size || row != rows[ordinal]) exact = false
                ordinal++
            }
            dao.tombstone(asset) == tombstone &&
                exact &&
                ordinal == rows.size &&
                (!eraseTranscript ||
                    dao.transcript(asset) == null && database.paged().jobCount(asset) == 0)
        }
        return AudioDeletionSnapshot(
            identity,
            tombstone.operationId,
            tombstone.state,
            if (includeSteps) rows.map { AudioDeletionStep(it.target(), it.completed) }
            else emptyList(),
        )
    }

    private fun eraseRecordingText(identity: AudioIdentity) {
        lease.requireHeld(identity.assetId.value)
        val asset = identity.assetId.value
        commits.commit({
            removePagedTranscript(asset)
            dao.removeTranscript(asset)
            dao.removeSegmentation(asset)
        }) {
            dao.transcript(asset) == null &&
                database.paged().jobCount(asset) == 0 &&
                dao.segmentationPage(asset, "").isEmpty()
        }
    }

    fun loadDeletion(
        identity: AudioIdentity,
        includeSteps: Boolean = true,
    ): AudioDeletionSnapshot? {
        requireOperation(identity)
        val row = dao.tombstone(identity.assetId.value) ?: return null
        check(row.state in setOf("DELETING", "USER_DELETED"))
        return AudioDeletionSnapshot(
            identity,
            row.operationId,
            row.state,
            if (includeSteps)
                buildList {
                    deletionRows(identity) { add(AudioDeletionStep(it.target(), it.completed)) }
                }
            else emptyList(),
        )
    }

    /** Bounded ordered readback; never retain a second whole inventory while comparing receipts. */
    private fun deletionRows(identity: AudioIdentity, action: (DeletionTargetEntity) -> Unit) {
        val revision = catalogRevision()
        var after: DeletionTargetEntity? = null
        while (true) {
            val cursor = after
            val page =
                if (cursor == null) dao.firstDeletionTargetPage(identity.assetId.value)
                else
                    dao.deletionTargetPage(
                        identity.assetId.value,
                        cursor.runId,
                        cursor.kind,
                        cursor.relativeName,
                    )
            if (page.isEmpty()) break
            page.forEach(action)
            after = page.last()
        }
        check(catalogRevision() == revision) { "Deletion inventory changed during readback" }
    }

    fun forEachDeletionTarget(identity: AudioIdentity, action: (AudioDeletionStep) -> Unit) {
        requireOperation(identity)
        if (uncertain || database.inTransaction()) throw UncertainAudioSourceState()
        checkNotNull(dao.tombstone(identity.assetId.value))
        deletionRows(identity) { action(AudioDeletionStep(it.target(), it.completed)) }
    }

    fun pendingDeletionPage(
        identity: AudioIdentity,
        kind: AudioDeletionTargetKind,
    ): List<AudioDeletionStep> {
        requireOperation(identity)
        if (uncertain || database.inTransaction()) throw UncertainAudioSourceState()
        check(checkNotNull(dao.tombstone(identity.assetId.value)).state == "DELETING")
        return dao.pendingDeletionTargets(identity.assetId.value, kind.name).map {
            AudioDeletionStep(it.target(), it.completed)
        }
    }

    fun requireDeletionKeysCompleted(identity: AudioIdentity) {
        check(pendingDeletionPage(identity, AudioDeletionTargetKind.KEY_REFERENCE).isEmpty())
    }

    /** Read-only, under the exact caller's live lease. Null means catalog-present, not readable. */
    fun sourceState(identity: AudioIdentity): AudioSourceState? {
        requireOperation(identity)
        if (uncertain || database.inTransaction()) throw UncertainAudioSourceState()
        val asset = exactAsset(identity) ?: return AudioSourceState.Missing
        val tombstone = dao.tombstone(asset.assetId) ?: return null
        check(tombstone.state in setOf("DELETING", "USER_DELETED"))
        val pending = mutableSetOf<AudioDeletionTargetKind>()
        deletionRows(identity) { row ->
            val target = row.target()
            if (!row.completed) pending.add(target.kind)
        }
        if (tombstone.state == "USER_DELETED") {
            check(pending.isEmpty())
            check(dao.intent(asset.assetId) == null && dao.finalSources(asset.assetId).isEmpty())
            check(dao.remainingDeletionMetadata(asset.assetId) == null)
            return AudioSourceState.UserDeleted
        }
        return AudioSourceState.Deleting(
            buildSet {
                add(AudioDeletionCategory.JOURNAL_COMPLETION)
                pending.forEach {
                    add(
                        if (it == AudioDeletionTargetKind.KEY_REFERENCE)
                            AudioDeletionCategory.KEY_MATERIAL
                        else AudioDeletionCategory.AUDIO_FILES
                    )
                }
            }
        )
    }

    /** The caller verifies exact absence after its idempotent file/key operation. */
    fun completeDeletionTarget(
        identity: AudioIdentity,
        target: AudioDeletionTarget,
        verifiedAbsent: () -> Boolean,
    ) {
        requireOperation(identity)
        requireMutable()
        check(checkNotNull(dao.tombstone(identity.assetId.value)).state == "DELETING")
        val row =
            checkNotNull(
                dao.deletionTarget(
                    identity.assetId.value,
                    target.runId,
                    target.kind.name,
                    target.relativeName,
                )
            )
        check(row.target() == target)
        if (row.completed) return
        if (target.kind != AudioDeletionTargetKind.KEY_REFERENCE) {
            requireDeletionKeysCompleted(identity)
        }
        val revision = catalogRevision()
        check(verifiedAbsent())
        lease.requireHeld(identity.assetId.value)
        val completed = row.copy(completed = true)
        commits.commit({
            check(catalogRevision() == revision)
            check(dao.update(completed) == 1)
        }) {
            dao.deletionTarget(row.assetId, row.runId, row.kind, row.relativeName) == completed &&
                catalogRevision() == Math.addExact(revision, 1)
        }
    }

    /**
     * One same-phase page, after idempotent effects; a crash before commit replays absence safely.
     */
    fun completeDeletionTargets(
        identity: AudioIdentity,
        targets: List<AudioDeletionTarget>,
        verifiedAbsent: (AudioDeletionTarget) -> Boolean,
    ) {
        requireOperation(identity)
        requireMutable()
        require(targets.isNotEmpty() && targets.size <= DELETION_RECEIPT_BATCH_SIZE)
        val kind = targets.first().kind
        require(targets.all { it.kind == kind })
        check(checkNotNull(dao.tombstone(identity.assetId.value)).state == "DELETING")
        val rows = dao.pendingDeletionTargets(identity.assetId.value, kind.name)
        check(rows.map { it.target() } == targets)
        if (kind != AudioDeletionTargetKind.KEY_REFERENCE) requireDeletionKeysCompleted(identity)
        val revision = catalogRevision()
        targets.forEach {
            lease.requireHeld(identity.assetId.value)
            check(verifiedAbsent(it))
        }
        lease.requireHeld(identity.assetId.value)
        val completed = rows.map { it.copy(completed = true) }
        commits.commit({
            check(catalogRevision() == revision)
            check(dao.updateDeletionTargets(completed) == completed.size)
        }) {
            completed.all {
                dao.deletionTarget(it.assetId, it.runId, it.kind, it.relativeName) == it
            } && catalogRevision() == Math.addExact(revision, completed.size.toLong())
        }
    }

    fun completeDeletion(identity: AudioIdentity, verifiedComplete: () -> Boolean) {
        requireOperation(identity)
        requireMutable()
        val tombstone = checkNotNull(dao.tombstone(identity.assetId.value))
        if (tombstone.state == "USER_DELETED") return
        check(tombstone.state == "DELETING")
        forEachDeletionTarget(identity) { check(it.completed) }
        check(verifiedComplete())
        lease.requireHeld(identity.assetId.value)
        val old = checkNotNull(dao.tombstone(identity.assetId.value))
        val completed = old.copy(state = "USER_DELETED")
        commits.commit({
            dao.removeIntent(old.assetId)
            dao.removeFinalSources(old.assetId)
            dao.removeAssetQuarantines(old.assetId)
            dao.removeAssetMicrofiles(old.assetId)
            dao.removeAssetManifests(old.assetId)
            dao.removeAssetBootstraps(old.assetId)
            check(dao.update(completed) == 1)
        }) {
            dao.tombstone(old.assetId) == completed &&
                dao.remainingDeletionMetadata(old.assetId) == null &&
                dao.intent(old.assetId) == null &&
                dao.finalSources(old.assetId).isEmpty()
        }
    }

    override fun close() {
        checkOpen()
        lease.withExclusiveCleanup {
            reservationSnapshot = null
            verifiedCatalog = null
            try {
                schemaVersionQuery.close()
            } finally {
                database.close()
            }
            closed = true
            openPaths.remove(registryKey)
        }
    }

    companion object {
        private const val DELETION_RECEIPT_BATCH_SIZE = 256
        private const val MAX_UNIT_FRAMES = 80_000L
        private const val MAX_DELETION_TARGETS = 100_000
        private const val MAX_RELATIVE_NAME_LENGTH = 512
        private val openPaths = ConcurrentHashMap.newKeySet<String>()

        // Owner, vault, encrypted helper and live authorization must all be explicit at this seam.
        @Suppress("LongParameterList")
        fun open(
            context: Context,
            databaseFile: File,
            helperFactory: SupportSQLiteOpenHelper.Factory,
            ownerId: String,
            vaultId: String,
            operationGate: () -> Unit,
        ): RoomAudioJournal =
            open(
                context,
                databaseFile,
                helperFactory,
                ownerId,
                vaultId,
                operationGate,
                DiagnosticSourcePolicy.ordinary(),
            )

        @Suppress("LongParameterList", "TooGenericExceptionCaught", "LongMethod")
        fun open(
            context: Context,
            databaseFile: File,
            helperFactory: SupportSQLiteOpenHelper.Factory,
            ownerId: String,
            vaultId: String,
            operationGate: () -> Unit,
            diagnostic: DiagnosticSourcePolicy,
        ): RoomAudioJournal {
            canonical(ownerId)
            canonical(vaultId)
            operationGate()
            val file = databaseFile.canonicalFile
            require(file.toPath().startsWith(context.noBackupFilesDir.canonicalFile.toPath()))
            require(file != context.noBackupFilesDir.canonicalFile && file.name != ":memory:")
            check(openPaths.add(file.path)) { "Vault already open" }
            val database =
                try {
                    val factory =
                        if (diagnostic.active)
                            DiagnosticJournalFactory(helperFactory, diagnostic, ownerId, vaultId)
                        else helperFactory
                    openDatabase(context, file, factory)
                } catch (error: Exception) {
                    openPaths.remove(file.path)
                    throw error
                }
            return try {
                val binding = VaultBindingEntity(ownerId = ownerId, vaultId = vaultId)
                val journal =
                    RoomAudioJournal(
                        database,
                        binding,
                        VaultJournalLease(operationGate),
                        file.path,
                        diagnostic,
                    )
                JournalSchemaVerifier.verify(database.openHelper.writableDatabase)
                val rows = journal.dao.bindings()
                if (diagnostic.active) {
                    check(rows == listOf(binding)) { "Diagnostic vault binding rejected" }
                    diagnostic.protectedSources().forEach { identity ->
                        val row = checkNotNull(journal.exactAsset(identity))
                        check(journal.dao.tombstone(row.assetId) == null)
                    }
                }
                if (rows.isEmpty())
                    journal.commits.commit({ journal.dao.insert(binding) }) {
                        journal.dao.bindings() == listOf(binding)
                    }
                else check(rows == listOf(binding)) { "Vault binding rejected" }
                journal
            } catch (error: Exception) {
                database.close()
                openPaths.remove(file.path)
                throw error
            }
        }

        private fun openDatabase(
            context: Context,
            file: File,
            helperFactory: SupportSQLiteOpenHelper.Factory,
        ): AudioJournalDatabase =
            Room.databaseBuilder(
                    context.applicationContext,
                    AudioJournalDatabase::class.java,
                    file.path,
                )
                .openHelperFactory(helperFactory)
                // All journal DAOs are synchronous and operations own the sole encrypted
                // connection. Finish Room invalidation on that same worker: an async
                // refresh
                // can otherwise hold Room's close barrier while waiting for a connection
                // retained by a failed endTransaction on the thread trying to close it.
                // Async DAO/observer admission requires revisiting this ownership contract.
                .setQueryExecutor { command ->
                    check(Looper.myLooper() != Looper.getMainLooper()) {
                        "Journal work requires a worker thread"
                    }
                    command.run()
                }
                .addMigrations(
                    OriginalAudioMigration,
                    SegmentationMigration,
                    TranscriptMigration,
                    PagedTranscriptMigration,
                    AudioDeletionIndexMigration,
                )
                .addCallback(
                    object : RoomDatabase.Callback() {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            AudioCatalogRevisionSchema.install(db)
                            AudioDeletionIndexMigration.install(db)
                        }
                    }
                )
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()

        private fun canonical(value: String) {
            require(
                value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            )
        }
    }
}

internal class UncertainAudioSourceState : IllegalStateException("Uncertain source state")
