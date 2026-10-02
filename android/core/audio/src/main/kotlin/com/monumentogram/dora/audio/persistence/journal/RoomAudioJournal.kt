package com.monumentogram.dora.audio.persistence.journal

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.monumentogram.dora.audio.AudioDeletionCategory
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioIntent
import com.monumentogram.dora.audio.AudioSourceState
import com.monumentogram.dora.audio.EncryptedAudioCatalog
import com.monumentogram.dora.audio.OriginalAudioLifecycle
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioReferenceCodec
import com.monumentogram.dora.audio.StoredAudioAsset
import com.monumentogram.dora.audio.StoredAudioSegment
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
@Suppress("TooManyFunctions", "ReturnCount", "DEPRECATION")
internal class RoomAudioJournal
private constructor(
    private val database: AudioJournalDatabase,
    private val binding: VaultBindingEntity,
    private val lease: VaultJournalLease,
    private val registryKey: String,
) : AutoCloseable {
    private val dao = database.journal()
    private var closed = false
    private var uncertain = false
    private val commits =
        JournalCommitBoundary(
            database::beginTransaction,
            database::setTransactionSuccessful,
            database::endTransaction,
            database::inTransaction,
        ) {
            uncertain = true
        }
    val catalog: EncryptedAudioCatalog = Catalog()

    /** Bounded encrypted discovery; callers authenticate each source before describing audio. */
    fun recordingCandidates(after: String): List<AudioIdentity> {
        checkOpen()
        if (uncertain || database.inTransaction()) throw UncertainAudioSourceState()
        return dao.recordingCandidates(binding.ownerId, binding.vaultId, after).map {
            it.identity()
        }
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
        check(dao.tombstone(claim.assetId) == null)
        return claim
    }

    private inner class Catalog : EncryptedAudioCatalog {
        override fun tryAcquire(identity: AudioIdentity): AutoCloseable? {
            checkOpen()
            canonical(identity.sessionId)
            return lease.tryAcquire(identity.assetId.value)
        }

        override fun create(identity: AudioIdentity): Boolean {
            checkOpen()
            requireMutable()
            canonical(identity.sessionId)
            val operation = lease.tryAcquire(identity.assetId.value) ?: return false
            operation.use {
                if (dao.asset(identity.assetId.value) != null) return false
                val row =
                    AssetEntity(
                        identity.assetId.value,
                        identity.recordingId.value,
                        identity.sessionId,
                        binding.ownerId,
                        binding.vaultId,
                    )
                commits.commit({ dao.insert(row) }) { dao.asset(row.assetId) == row }
                return true
            }
        }

        // Ordered source invariants are deliberately checked together before a snapshot is
        // returned.
        @Suppress("LongMethod", "CyclomaticComplexMethod")
        override fun load(identity: AudioIdentity): StoredAudioAsset? {
            requireOperation(identity)
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
            return StoredAudioAsset(
                identity,
                sources,
                pending,
                if (asset.finalized) sources.toList() else null,
            )
        }

        override fun reserve(expected: StoredAudioAsset, intent: AudioIntent): Boolean {
            requireOperation(expected.identity)
            requireMutable()
            if (
                load(expected.identity) != expected ||
                    expected.pending != null ||
                    expected.finalization != null
            )
                return false
            val assetId = expected.identity.assetId.value
            when (intent) {
                is AudioIntent.Append -> {
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
                    commits.commit({
                        if (existing == null) dao.insert(physical)
                        dao.insert(claim)
                        dao.removeIntent(assetId)
                        dao.insert(IntentEntity(assetId, "APPEND", claim.runId))
                    }) {
                        load(expected.identity) == expected.copy(pending = intent)
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

        @Suppress("ComplexCondition")
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
                        next != expected.copy(segments = expected.segments + source, pending = null)
                    )
                        return false
                    commits.commit({
                        check(dao.update(claim.copy(committed = true)) == 1)
                        check(
                            dao.update(
                                checkNotNull(dao.intent(claim.assetId)).copy(applied = true)
                            ) == 1
                        )
                    }) {
                        load(expected.identity) == next
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
            val end = asset.segments.fold(0L) { start, row -> Math.addExact(start, row.frames) }
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

        init {
            checkOpen()
            requireMutable()
            lease.requireHeld()
            check(!database.inTransaction())
            database.beginTransaction()
        }

        fun write(exactReadback: () -> Boolean, mutation: () -> Unit) {
            lease.requireHeld()
            check(!ended && !successful)
            mutation()
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
                if (successful) check(readbacks.all { it() }) { "Journal readback rejected" }
            } catch (error: Exception) {
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
                    session.write({ dao.bootstrap(row.runId) == row }) { dao.insert(row) }
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
                    session.write({
                        dao.microfile(row.runId) == row && dao.manifest(manifest.runId) == manifest
                    }) {
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
    ): AudioDeletionSnapshot {
        requireOperation(identity)
        requireMutable()
        checkNotNull(exactAsset(identity))
        loadDeletion(identity)?.let {
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
        val targets = buildList {
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
        val byName = targets.groupBy { Triple(it.runId, it.kind, it.relativeName) }
        check(byName.values.all { versions -> versions.map { it.digest }.distinct().size == 1 })
        val rows =
            byName.values
                .map { it.first() }
                .map {
                    DeletionTargetEntity(asset, it.runId, it.kind.name, it.relativeName, it.digest)
                }
        val tombstone = TombstoneEntity(asset, UUID.randomUUID().toString(), "DELETING")
        commits.commit({
            dao.insert(tombstone)
            rows.forEach { dao.insert(it) }
        }) {
            dao.tombstone(asset) == tombstone && dao.deletionTargets(asset).toSet() == rows.toSet()
        }
        return checkNotNull(loadDeletion(identity))
    }

    fun loadDeletion(identity: AudioIdentity): AudioDeletionSnapshot? {
        requireOperation(identity)
        val row = dao.tombstone(identity.assetId.value) ?: return null
        check(row.state in setOf("DELETING", "USER_DELETED"))
        return AudioDeletionSnapshot(
            identity,
            row.operationId,
            row.state,
            dao.deletionTargets(row.assetId).map { AudioDeletionStep(it.target(), it.completed) },
        )
    }

    /** Read-only, under the exact caller's live lease. Null means catalog-present, not readable. */
    fun sourceState(identity: AudioIdentity): AudioSourceState? {
        requireOperation(identity)
        if (uncertain || database.inTransaction()) throw UncertainAudioSourceState()
        val asset = exactAsset(identity) ?: return AudioSourceState.Missing
        val snapshot = loadDeletion(identity) ?: return null
        if (snapshot.state == "USER_DELETED") {
            check(snapshot.steps.all { it.completed })
            check(dao.intent(asset.assetId) == null && dao.finalSources(asset.assetId).isEmpty())
            check(
                dao.claims(asset.assetId).all {
                    dao.bootstrap(it.runId) == null &&
                        dao.microfile(it.runId) == null &&
                        dao.manifest(it.runId) == null &&
                        dao.quarantines(it.runId).isEmpty()
                }
            )
            return AudioSourceState.UserDeleted
        }
        return AudioSourceState.Deleting(
            buildSet {
                add(AudioDeletionCategory.JOURNAL_COMPLETION)
                snapshot.steps
                    .filterNot { it.completed }
                    .forEach {
                        add(
                            if (it.target.kind == AudioDeletionTargetKind.KEY_REFERENCE)
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
        val snapshot = checkNotNull(loadDeletion(identity))
        check(snapshot.state == "DELETING")
        val row =
            checkNotNull(
                dao.deletionTargets(identity.assetId.value).singleOrNull { it.target() == target }
            )
        if (row.completed) return
        if (target.kind != AudioDeletionTargetKind.KEY_REFERENCE) {
            check(
                snapshot.steps
                    .filter { it.target.kind == AudioDeletionTargetKind.KEY_REFERENCE }
                    .all { it.completed }
            )
        }
        check(verifiedAbsent())
        lease.requireHeld(identity.assetId.value)
        val completed = row.copy(completed = true)
        commits.commit({ check(dao.update(completed) == 1) }) {
            completed in dao.deletionTargets(row.assetId)
        }
    }

    fun completeDeletion(identity: AudioIdentity, verifiedComplete: () -> Boolean) {
        requireOperation(identity)
        requireMutable()
        val snapshot = checkNotNull(loadDeletion(identity))
        if (snapshot.state == "USER_DELETED") return
        check(snapshot.steps.all { it.completed })
        check(verifiedComplete())
        lease.requireHeld(identity.assetId.value)
        val old = checkNotNull(dao.tombstone(identity.assetId.value))
        val completed = old.copy(state = "USER_DELETED")
        commits.commit({
            dao.removeIntent(old.assetId)
            dao.removeFinalSources(old.assetId)
            dao.claims(old.assetId).forEach {
                dao.removeQuarantine(it.runId)
                dao.removeMicrofile(it.runId)
                dao.removeManifest(it.runId)
                dao.removeBootstrap(it.runId)
            }
            check(dao.update(completed) == 1)
        }) {
            dao.tombstone(old.assetId) == completed
        }
    }

    override fun close() {
        checkOpen()
        lease.withExclusiveCleanup {
            database.close()
            closed = true
            openPaths.remove(registryKey)
        }
    }

    companion object {
        private const val MAX_UNIT_FRAMES = 80_000L
        private const val MAX_DELETION_TARGETS = 100_000
        private const val MAX_RELATIVE_NAME_LENGTH = 512
        private val openPaths = ConcurrentHashMap.newKeySet<String>()

        // Owner, vault, encrypted helper and live authorization must all be explicit at this seam.
        @Suppress("LongParameterList", "TooGenericExceptionCaught")
        fun open(
            context: Context,
            databaseFile: File,
            helperFactory: SupportSQLiteOpenHelper.Factory,
            ownerId: String,
            vaultId: String,
            operationGate: () -> Unit,
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
                    Room.databaseBuilder(
                            context.applicationContext,
                            AudioJournalDatabase::class.java,
                            file.path,
                        )
                        .openHelperFactory(helperFactory)
                        .addMigrations(OriginalAudioMigration)
                        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                        .build()
                } catch (error: Exception) {
                    openPaths.remove(file.path)
                    throw error
                }
            return try {
                val binding = VaultBindingEntity(ownerId = ownerId, vaultId = vaultId)
                val journal =
                    RoomAudioJournal(database, binding, VaultJournalLease(operationGate), file.path)
                JournalSchemaVerifier.verify(database.openHelper.writableDatabase)
                val rows = journal.dao.bindings()
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

        private fun canonical(value: String) {
            require(
                value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            )
        }
    }
}

internal class UncertainAudioSourceState : IllegalStateException("Uncertain source state")
