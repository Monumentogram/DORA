package com.monumentogram.dora.audio.persistence.journal

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioIntent
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.StoredAudioAsset
import com.monumentogram.dora.audio.StoredAudioSegment
import com.monumentogram.dora.audio.persistence.database.SqlCipherJournalHelperFactory
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.poc.recovery.bootstrap.KeyConfirmationState
import com.monumentogram.dora.poc.recovery.bootstrap.RecoveryBootstrapRunRow
import com.monumentogram.dora.poc.recovery.candidate.QuarantineBootstrapBinding
import com.monumentogram.dora.poc.recovery.candidate.QuarantineIntentState
import com.monumentogram.dora.poc.recovery.candidate.RecoveryArtifactContext
import com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureCategory
import com.monumentogram.dora.poc.recovery.candidate.RecoveryManifestPublicationRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofileUnitRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineIntentRow
import com.monumentogram.dora.poc.recovery.candidate.RecoverySourceAccessException
import com.monumentogram.dora.poc.recovery.contract.CanonicalRecoveryAlias
import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryProcessingIntent
import com.monumentogram.dora.poc.recovery.contract.RecoveryProcessingIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryReconciliationSource
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryReconciliationStorage
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Real Room and admitted-helper candidate tests with synthetic keys; no physical-device claim. */
// Durability scenarios keep setup, interruption, reopen and assertions visible in one fixture.
@Suppress("LargeClass", "LongMethod")
class RoomAudioJournalTest {
    @Test
    fun sourceStateRequiresExactLeaseAndCommittedCertainState() {
        open(file()).use { journal ->
            val audio = identity()
            assertThrows(IllegalStateException::class.java) { journal.sourceState(audio) }
            assertTrue(journal.catalog.create(audio))
            journal.catalog.tryAcquire(audio)!!.use {
                assertNull(journal.sourceState(audio))
                assertThrows(IllegalStateException::class.java) {
                    journal.sourceState(audio.copy(sessionId = id(999)))
                }
                val transaction = journal.bootstrapJournal.beginNonExclusive()
                try {
                    assertThrows(IllegalStateException::class.java) { journal.sourceState(audio) }
                } finally {
                    transaction.end()
                }
                assertNull(journal.sourceState(audio))
            }
        }
    }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun id(n: Int) = "00000000-0000-0000-0000-%012d".format(n)

    private fun identity(n: Int = 1) =
        AudioIdentity(RecordingId(id(n)), AudioAssetId(id(n + 10)), id(n + 20))

    private fun file() = File(context.noBackupFilesDir, "journal-test-${UUID.randomUUID()}.db")

    private val secrets = mutableMapOf<String, ByteArray>()

    private fun encryptedFactory(file: File) =
        SqlCipherJournalHelperFactory(
            context,
            file.canonicalFile,
            secrets.getOrPut(file.path) { ByteArray(32).also { SecureRandom().nextBytes(it) } },
        )

    private fun open(file: File, owner: String = id(100)) =
        RoomAudioJournal.open(
            context,
            file,
            encryptedFactory(file),
            owner,
            id(101),
            {},
        )

    @Test
    fun durableIntentSurvivesReopenAndCannotClaimRunForAnotherAsset() {
        val file = file()
        val audio = identity()
        val unit = AudioStorageUnitIdentity(audio, id(80), 0, 0)
        open(file).use { journal ->
            assertTrue(journal.catalog.create(audio))
            journal.catalog.tryAcquire(audio)!!.use {
                assertTrue(
                    journal.catalog.reserve(StoredAudioAsset(audio), AudioIntent.Append(unit, 2))
                )
                assertFalse(
                    journal.catalog.reserve(StoredAudioAsset(audio), AudioIntent.Append(unit, 2))
                )
            }
        }
        open(file).use { journal ->
            journal.catalog.tryAcquire(audio)!!.use {
                assertEquals(AudioIntent.Append(unit, 2), journal.catalog.load(audio)!!.pending)
            }
            val other = identity(2)
            assertTrue(journal.catalog.create(other))
            journal.catalog.tryAcquire(other)!!.use {
                assertFalse(
                    journal.catalog.reserve(
                        StoredAudioAsset(other),
                        AudioIntent.Append(unit.copy(audio = other), 2),
                    )
                )
            }
        }
    }

    @Test
    fun ownerSubstitutionCannotReopenExistingCatalog() {
        val file = file()
        open(file).close()
        assertThrows(IllegalStateException::class.java) { open(file, id(102)) }
    }

    @Test
    fun holesAndForeignIdentityAreRejectedBeforeDurableReservation() {
        open(file()).use { journal ->
            val audio = identity()
            assertTrue(journal.catalog.create(audio))
            journal.catalog.tryAcquire(audio)!!.use {
                val initial = journal.catalog.load(audio)!!
                assertFalse(
                    journal.catalog.reserve(
                        initial,
                        AudioIntent.Append(AudioStorageUnitIdentity(audio, id(80), 1, 0), 2),
                    )
                )
                assertFalse(
                    journal.catalog.reserve(
                        initial,
                        AudioIntent.Append(AudioStorageUnitIdentity(identity(2), id(80), 0, 0), 2),
                    )
                )
                assertEquals(initial, journal.catalog.load(audio))
            }
        }
    }

    @Test
    fun deletionTombstoneBlocksReadWriteAndRequiresVerifiedCompletion() {
        val file = file()
        val audio = identity()
        open(file).use { journal ->
            assertTrue(journal.catalog.create(audio))
            journal.catalog.tryAcquire(audio)!!.use {
                val deletion = journal.beginDeletion(audio, emptyList())
                assertEquals("DELETING", deletion.state)
                assertNull(journal.catalog.load(audio))
                assertThrows(IllegalStateException::class.java) {
                    journal.completeDeletion(audio) { false }
                }
                assertEquals("DELETING", journal.loadDeletion(audio)!!.state)
                journal.completeDeletion(audio) { true }
                assertEquals("USER_DELETED", journal.loadDeletion(audio)!!.state)
            }
            assertFalse(journal.catalog.create(audio))
        }
        open(file).use { journal ->
            journal.catalog.tryAcquire(audio)!!.use { assertNull(journal.catalog.load(audio)) }
            assertFalse(journal.catalog.create(audio))
        }
    }

    private fun reserve(journal: RoomAudioJournal, audio: AudioIdentity): AudioIntent.Append {
        val intent = AudioIntent.Append(AudioStorageUnitIdentity(audio, id(80), 0, 0), 2)
        assertTrue(journal.catalog.reserve(StoredAudioAsset(audio), intent))
        return intent
    }

    private fun bootstrap(journal: RoomAudioJournal, run: String) {
        val row =
            RecoveryBootstrapRunRow(
                run,
                RecoveryCandidate.MICROFILE.contractId,
                "key-confirmation/run.kc",
                48,
                Sha256Value.calculate(byteArrayOf(1)),
                CanonicalRecoveryAlias.sha256(RunId.fromCanonicalString(run)),
                KeyConfirmationState.VALID,
            )
        journal.bootstrapJournal.beginNonExclusive().let {
            it.insert(row)
            it.markSuccessful()
            it.end()
        }
        assertEquals(
            row.canonicalAliasSha256,
            journal.loadBootstrapIdentity(RunId.fromCanonicalString(run))!!.canonicalAliasSha256,
        )
    }

    private fun publication(
        run: String
    ): Pair<RecoveryMicrofileUnitRow, RecoveryManifestPublicationRow> {
        val digest = Sha256Value.calculate(byteArrayOf(2))
        val unit =
            RecoveryMicrofileUnitRow(
                run,
                RecoveryCandidate.MICROFILE.contractId,
                0UL,
                0UL,
                4UL,
                5UL,
                RecoveryRelativeNames.microfileCiphertext(0UL),
                44,
                digest,
                RecoveryRelativeNames.microfileKeyEnvelope(0UL),
                80,
                digest,
                1UL,
                RecoveryProcessingIntent.calculate(
                    RecoveryProcessingIntentInput(
                        RecoveryCandidate.MICROFILE,
                        RunId.fromCanonicalString(run),
                        0UL,
                        0UL,
                        4UL,
                        digest,
                    )
                ),
            )
        val manifest =
            RecoveryManifestPublicationRow(
                run,
                RecoveryCandidate.MICROFILE.contractId,
                PublicationKind.MANIFEST,
                1UL,
                4UL,
                RecoveryRelativeNames.manifestCiphertext(1UL),
                64,
                digest,
                RecoveryRelativeNames.manifestKeyEnvelope(1UL),
                80,
                digest,
                Sha256Value.ZERO,
            )
        return unit to manifest
    }

    @Test
    fun atomicPublicationRollbackAndExactFinalizationSurviveReopen() {
        val file = file()
        val audio = identity()
        var finalized: StoredAudioAsset? = null
        open(file).use { journal ->
            assertTrue(journal.catalog.create(audio))
            journal.catalog.tryAcquire(audio)!!.use {
                val intent = reserve(journal, audio)
                bootstrap(journal, intent.identity.unitId)
                val (unit, manifest) = publication(intent.identity.unitId)
                journal.microfileJournal.beginNonExclusive().let {
                    it.insert(unit, manifest)
                    it.end()
                }
                val run = RunId.fromCanonicalString(intent.identity.unitId)
                assertTrue(journal.loadCandidateSnapshot(run).units.isEmpty())
                assertTrue(journal.loadCandidateSnapshot(run).publications.isEmpty())
                journal.microfileJournal.beginNonExclusive().let {
                    it.insert(unit, manifest)
                    it.markSuccessful()
                    it.end()
                }
                assertEquals(listOf(unit), journal.loadCandidateSnapshot(run).units)
                assertEquals(listOf(manifest), journal.loadCandidateSnapshot(run).publications)
                val pending = journal.catalog.load(audio)!!
                val committed =
                    StoredAudioAsset(
                        audio,
                        listOf(StoredAudioSegment(intent.identity, 2, manifest.publicationSha256)),
                    )
                assertFalse(
                    journal.catalog.compareAndSet(
                        pending,
                        committed.copy(
                            segments = listOf(committed.segments.single().copy(frames = 3))
                        ),
                    )
                )
                assertTrue(journal.catalog.compareAndSet(pending, committed))
                assertFalse(journal.catalog.compareAndSet(pending, committed))
                val finalIntent = AudioIntent.Finalize(committed.segments)
                assertTrue(journal.catalog.reserve(committed, finalIntent))
                finalized = committed.copy(finalization = committed.segments)
                assertTrue(
                    journal.catalog.compareAndSet(
                        committed.copy(pending = finalIntent),
                        finalized!!,
                    )
                )
                assertFalse(
                    journal.catalog.reserve(
                        finalized!!,
                        AudioIntent.Append(
                            intent.identity.copy(unitId = id(81), ordinal = 1, firstFrame = 2),
                            2,
                        ),
                    )
                )
            }
        }
        open(file).use { journal ->
            journal.catalog.tryAcquire(audio)!!.use {
                assertEquals(finalized, journal.catalog.load(audio))
            }
        }
    }

    @Test
    fun quarantineInventoryAndRetainedRowsAreDurableAndExact() {
        val file = file()
        val audio = identity()
        val run = RunId.fromCanonicalString(id(80))
        val input =
            RecoveryQuarantineIntentInput(
                RecoveryCandidate.MICROFILE,
                run,
                RecoveryRelativeNames.microfileCiphertext(0UL),
                RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT,
                44UL,
                Sha256Value.calculate(byteArrayOf(9)),
            )
        val row =
            RecoveryQuarantineIntentRow(
                RecoveryQuarantineIntent.calculate(input),
                input,
                RecoveryQuarantineObservedState.REFERENCED_REJECTED,
                QuarantineBootstrapBinding.PRESENT,
                RecoveryQuarantineIntent.destination(input),
                QuarantineIntentState.PENDING,
            )
        open(file).use { journal ->
            assertTrue(journal.catalog.create(audio))
            journal.catalog.tryAcquire(audio)!!.use {
                reserve(journal, audio)
                bootstrap(journal, run.toCanonicalString())
                journal.quarantineJournal.beginNonExclusive().let {
                    it.insert(row)
                    it.markSuccessful()
                    it.end()
                }
                assertEquals(listOf(row), journal.loadPendingQuarantine(run))
                assertEquals(row, journal.quarantineJournal.loadBySource(input))
                assertThrows(
                    com.monumentogram.dora.poc.recovery.journal
                            .RecoveryMicrofileQuarantineReadbackException::class
                        .java
                ) {
                    journal.loadRetainedMicrofileSource(run, input.sourceRelativeName)
                }
                journal.quarantineJournal.beginNonExclusive().let {
                    it.complete(row.intentId)
                    it.markSuccessful()
                    it.end()
                }
            }
        }
        open(file).use { journal ->
            journal.catalog.tryAcquire(audio)!!.use {
                assertTrue(journal.loadPendingQuarantine(run).isEmpty())
                val complete = row.copy(state = QuarantineIntentState.COMPLETED)
                assertEquals(listOf(complete), journal.loadAllQuarantine(run))
                assertEquals(
                    complete,
                    journal.loadRetainedMicrofileSource(run, input.sourceRelativeName),
                )
            }
        }
    }

    @Test
    fun deletionResumesWithVerifiedKeyCompleteAndFilesPending() {
        val file = file()
        val audio = identity()
        open(file).use { journal ->
            assertTrue(journal.catalog.create(audio))
            journal.catalog.tryAcquire(audio)!!.use {
                reserve(journal, audio)
                bootstrap(journal, id(80))
                val frozen = journal.beginDeletion(audio, emptyList())
                assertEquals(3, frozen.steps.size)
                val key =
                    frozen.steps
                        .single { it.target.kind == AudioDeletionTargetKind.KEY_REFERENCE }
                        .target
                journal.completeDeletionTarget(audio, key) { true }
                val artifact =
                    frozen.steps
                        .single { it.target.kind == AudioDeletionTargetKind.ARTIFACT }
                        .target
                assertThrows(IllegalStateException::class.java) {
                    journal.completeDeletionTarget(audio, artifact) { false }
                }
                assertFalse(
                    journal.loadDeletion(audio)!!.steps.single { it.target == artifact }.completed
                )
            }
        }
        open(file).use { journal ->
            journal.catalog.tryAcquire(audio)!!.use {
                val resumed = journal.loadDeletion(audio)!!
                assertEquals(1, resumed.steps.count { it.completed })
                assertTrue(
                    resumed.steps
                        .single { it.target.kind == AudioDeletionTargetKind.KEY_REFERENCE }
                        .completed
                )
                assertTrue(
                    resumed.steps
                        .filter { it.target.kind != AudioDeletionTargetKind.KEY_REFERENCE }
                        .none { it.completed }
                )
                assertEquals(resumed, journal.beginDeletion(audio, emptyList()))
                resumed.steps
                    .filter {
                        !it.completed && it.target.kind != AudioDeletionTargetKind.KEY_REFERENCE
                    }
                    .forEach { step ->
                        journal.completeDeletionTarget(audio, step.target) { true }
                    }
                val key =
                    resumed.steps
                        .single { it.target.kind == AudioDeletionTargetKind.KEY_REFERENCE }
                        .target
                journal.completeDeletionTarget(audio, key) {
                    error("Verified key checkpoint must not repeat")
                }
                journal.completeDeletion(audio) { true }
                assertEquals("USER_DELETED", journal.loadDeletion(audio)!!.state)
            }
        }
        open(file).use { journal ->
            val other = identity(2)
            assertTrue(journal.catalog.create(other))
            journal.catalog.tryAcquire(other)!!.use {
                assertFalse(
                    journal.catalog.reserve(
                        StoredAudioAsset(other),
                        AudioIntent.Append(AudioStorageUnitIdentity(other, id(80), 0, 0), 2),
                    )
                )
            }
        }
    }

    @Test
    fun uncertainTransactionEndRetainsReceiptAndFencesFurtherWritesUntilReopen() {
        val file = file()
        val audio = identity()
        val fail = AtomicBoolean(false)
        val factory = EndFailureFactory(encryptedFactory(file), fail)
        var committed: StoredAudioAsset? = null
        RoomAudioJournal.open(context, file, factory, id(100), id(101), {}).use { journal ->
            assertTrue(journal.catalog.create(audio))
            journal.catalog.tryAcquire(audio)!!.use {
                val intent = reserve(journal, audio)
                bootstrap(journal, id(80))
                val (unit, manifest) = publication(id(80))
                journal.microfileJournal.beginNonExclusive().let {
                    it.insert(unit, manifest)
                    it.markSuccessful()
                    it.end()
                }
                val pending = journal.catalog.load(audio)!!
                committed =
                    StoredAudioAsset(
                        audio,
                        listOf(StoredAudioSegment(intent.identity, 2, manifest.publicationSha256)),
                    )
                fail.set(true)
                assertThrows(IllegalStateException::class.java) {
                    journal.catalog.compareAndSet(pending, committed!!)
                }
                assertThrows(IllegalStateException::class.java) {
                    journal.catalog.reserve(committed!!, AudioIntent.Finalize(committed!!.segments))
                }
                assertThrows(IllegalStateException::class.java) { journal.sourceState(audio) }
            }
        }
        open(file).use { journal ->
            journal.catalog.tryAcquire(audio)!!.use {
                assertEquals(committed, journal.catalog.load(audio))
                assertTrue(
                    journal.catalog.reserve(committed!!, AudioIntent.Finalize(committed!!.segments))
                )
            }
        }
    }

    @Test
    fun ddlTamperingWithUnchangedRoomIdentityIsRejected() {
        val file = file()
        fun platformOpen() =
            RoomAudioJournal.open(
                context,
                file,
                FrameworkSQLiteOpenHelperFactory(),
                id(100),
                id(101),
                {},
            )
        platformOpen().close()
        android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, 0).use {
            it.execSQL("DROP INDEX index_unit_claim_assetId_ordinal")
        }
        assertThrows(IllegalStateException::class.java) { platformOpen() }
    }

    private class EndFailureFactory(
        private val delegate: SupportSQLiteOpenHelper.Factory,
        private val fail: AtomicBoolean,
    ) : SupportSQLiteOpenHelper.Factory {
        override fun create(
            configuration: SupportSQLiteOpenHelper.Configuration
        ): SupportSQLiteOpenHelper {
            val helper = delegate.create(configuration)
            fun wrapped(database: SupportSQLiteDatabase) =
                Proxy.newProxyInstance(
                    SupportSQLiteDatabase::class.java.classLoader,
                    arrayOf(SupportSQLiteDatabase::class.java),
                ) { _, method, args ->
                    val result =
                        try {
                            method.invoke(database, *(args ?: emptyArray()))
                        } catch (error: InvocationTargetException) {
                            throw error.targetException
                        }
                    if (method.name == "endTransaction" && fail.compareAndSet(true, false))
                        error("Synthetic transaction-end failure")
                    result
                } as SupportSQLiteDatabase
            return object : SupportSQLiteOpenHelper by helper {
                override val writableDatabase: SupportSQLiteDatabase
                    get() = wrapped(helper.writableDatabase)

                override val readableDatabase: SupportSQLiteDatabase
                    get() = wrapped(helper.readableDatabase)
            }
        }
    }

    @Test
    fun openRecoveryTransactionCannotAuthorizeDeletion() {
        open(file()).use { journal ->
            val audio = identity()
            assertTrue(journal.catalog.create(audio))
            journal.catalog.tryAcquire(audio)!!.use {
                val outer = journal.bootstrapJournal.beginNonExclusive()
                try {
                    assertThrows(IllegalStateException::class.java) {
                        journal.beginDeletion(audio, emptyList())
                    }
                } finally {
                    outer.end()
                }
                assertNull(journal.loadDeletion(audio))
                assertEquals(StoredAudioAsset(audio), journal.catalog.load(audio))
            }
        }
    }

    @Test
    fun openRecoveryTransactionCannotAuthorizeReservation() {
        open(file()).use { journal ->
            val audio = identity()
            assertTrue(journal.catalog.create(audio))
            journal.catalog.tryAcquire(audio)!!.use {
                val outer = journal.bootstrapJournal.beginNonExclusive()
                try {
                    assertThrows(IllegalStateException::class.java) { reserve(journal, audio) }
                } finally {
                    outer.end()
                }
                assertEquals(StoredAudioAsset(audio), journal.catalog.load(audio))
            }
        }
    }

    @Test
    fun openRecoveryTransactionCannotAuthorizeCompareAndSet() {
        open(file()).use { journal ->
            val audio = identity()
            assertTrue(journal.catalog.create(audio))
            journal.catalog.tryAcquire(audio)!!.use {
                val intent = reserve(journal, audio)
                bootstrap(journal, id(80))
                val (unit, manifest) = publication(id(80))
                journal.microfileJournal.beginNonExclusive().let {
                    it.insert(unit, manifest)
                    it.markSuccessful()
                    it.end()
                }
                val pending = journal.catalog.load(audio)!!
                val committed =
                    StoredAudioAsset(
                        audio,
                        listOf(StoredAudioSegment(intent.identity, 2, manifest.publicationSha256)),
                    )
                val outer = journal.quarantineJournal.beginNonExclusive()
                try {
                    assertThrows(IllegalStateException::class.java) {
                        journal.catalog.compareAndSet(pending, committed)
                    }
                } finally {
                    outer.end()
                }
                assertEquals(pending, journal.catalog.load(audio))
            }
        }
    }

    private fun source(
        journal: RoomAudioJournal,
        retained: (RunId, String) -> RecoveryQuarantineIntentRow? =
            journal::loadRetainedMicrofileSource,
    ) =
        AndroidRecoveryReconciliationSource(
            loadBootstrap = journal::loadBootstrapIdentity,
            loadSnapshot = journal::loadCandidateSnapshot,
            loadPending = journal::loadPendingQuarantine,
            loadAllIntents = journal::loadAllQuarantine,
            storage = AndroidOsRecoveryReconciliationStorage(context),
            aliasExists = { true },
            loadRetained = retained,
        )

    private fun retainedInput(digestSeed: Int) =
        RecoveryQuarantineIntentInput(
            RecoveryCandidate.MICROFILE,
            RunId.fromCanonicalString(id(80)),
            RecoveryRelativeNames.microfileCiphertext(0UL),
            RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT,
            44UL,
            Sha256Value.calculate(byteArrayOf(digestSeed.toByte())),
        )

    private fun quarantine(
        journal: RoomAudioJournal,
        input: RecoveryQuarantineIntentInput,
        complete: Boolean,
    ) {
        val row =
            RecoveryQuarantineIntentRow(
                RecoveryQuarantineIntent.calculate(input),
                input,
                RecoveryQuarantineObservedState.REFERENCED_REJECTED,
                QuarantineBootstrapBinding.PRESENT,
                RecoveryQuarantineIntent.destination(input),
                QuarantineIntentState.PENDING,
            )
        journal.quarantineJournal.beginNonExclusive().let {
            it.insert(row)
            it.markSuccessful()
            it.end()
        }
        if (complete)
            journal.quarantineJournal.beginNonExclusive().let {
                it.complete(row.intentId)
                it.markSuccessful()
                it.end()
            }
    }

    @Test
    fun ambiguousRetainedFilenameIsStructuralThroughAcceptedCallbackSource() {
        open(file()).use { journal ->
            val audio = identity()
            assertTrue(journal.catalog.create(audio))
            journal.catalog.tryAcquire(audio)!!.use {
                reserve(journal, audio)
                bootstrap(journal, id(80))
                val original = retainedInput(1)
                quarantine(journal, original, complete = true)
                quarantine(journal, retainedInput(2), complete = true)
                val error =
                    assertThrows(RecoverySourceAccessException::class.java) {
                        source(journal)
                            .loadRetainedArtifact(original, RecoveryArtifactContext.UNIT_CIPHERTEXT)
                    }
                assertEquals(RecoveryFailureCategory.STRUCTURAL, error.diagnostic.category)
            }
        }
    }

    @Test
    fun pendingRetainedRowIsStructuralThroughAcceptedCallbackSource() {
        open(file()).use { journal ->
            val audio = identity()
            assertTrue(journal.catalog.create(audio))
            journal.catalog.tryAcquire(audio)!!.use {
                reserve(journal, audio)
                bootstrap(journal, id(80))
                val original = retainedInput(1)
                quarantine(journal, original, complete = false)
                val error =
                    assertThrows(RecoverySourceAccessException::class.java) {
                        source(journal)
                            .loadRetainedArtifact(original, RecoveryArtifactContext.UNIT_CIPHERTEXT)
                    }
                assertEquals(RecoveryFailureCategory.STRUCTURAL, error.diagnostic.category)
            }
        }
    }

    @Test
    fun retainedDatabaseFailureRemainsOperationalThroughAcceptedCallbackSource() {
        open(file()).use { journal ->
            val audio = identity()
            assertTrue(journal.catalog.create(audio))
            journal.catalog.tryAcquire(audio)!!.use {
                reserve(journal, audio)
                bootstrap(journal, id(80))
                val error =
                    assertThrows(RecoverySourceAccessException::class.java) {
                        source(journal) { _, _ ->
                                throw android.database.sqlite.SQLiteException(
                                    "Synthetic database unavailable"
                                )
                            }
                            .loadRetainedArtifact(
                                retainedInput(1),
                                RecoveryArtifactContext.UNIT_CIPHERTEXT,
                            )
                    }
                assertEquals(RecoveryFailureCategory.OPERATIONAL, error.diagnostic.category)
            }
        }
    }

    @Test
    fun deletionBootstrapRequiresExactIdentityRunLeaseAndFence() {
        val audio = identity()
        val run = RunId.fromCanonicalString(id(80))
        open(file()).use { journal ->
            assertTrue(journal.catalog.create(audio))
            journal.catalog.tryAcquire(audio)!!.use {
                reserve(journal, audio)
                bootstrap(journal, id(80))
                assertThrows(IllegalStateException::class.java) {
                    journal.hasCommittedDeletionBootstrap(audio, run)
                }
                journal.beginDeletion(audio, emptyList())
                assertTrue(journal.hasCommittedDeletionBootstrap(audio, run))
                assertThrows(IllegalStateException::class.java) {
                    journal.hasCommittedDeletionBootstrap(audio.copy(sessionId = id(99)), run)
                }
                assertThrows(IllegalStateException::class.java) {
                    journal.hasCommittedDeletionBootstrap(identity(2), run)
                }
                assertThrows(IllegalStateException::class.java) {
                    journal.hasCommittedDeletionBootstrap(audio, RunId.fromCanonicalString(id(81)))
                }
                val outer = journal.bootstrapJournal.beginNonExclusive()
                try {
                    assertThrows(IllegalStateException::class.java) {
                        journal.hasCommittedDeletionBootstrap(audio, run)
                    }
                } finally {
                    outer.end()
                }
            }
            assertThrows(IllegalStateException::class.java) {
                journal.hasCommittedDeletionBootstrap(audio, run)
            }
        }
    }

    @Test
    fun deletionBootstrapRejectsRevocationDuringHeldOperation() {
        val databaseFile = file()
        val authorized = AtomicBoolean(true)
        RoomAudioJournal.open(
                context,
                databaseFile,
                encryptedFactory(databaseFile),
                id(100),
                id(101),
                { check(authorized.get()) },
            )
            .use { journal ->
                val audio = identity()
                val run = RunId.fromCanonicalString(id(80))
                assertTrue(journal.catalog.create(audio))
                journal.catalog.tryAcquire(audio)!!.use {
                    reserve(journal, audio)
                    bootstrap(journal, id(80))
                    journal.beginDeletion(audio, emptyList())
                    assertTrue(journal.hasCommittedDeletionBootstrap(audio, run))
                    authorized.set(false)
                    assertThrows(IllegalStateException::class.java) {
                        journal.hasCommittedDeletionBootstrap(audio, run)
                    }
                }
            }
    }

    @Test
    fun deletionBootstrapDistinguishesAbsentAndCommittedRowsAfterReopen() {
        assertDeletionBootstrapAfterReopen(false)
        assertDeletionBootstrapAfterReopen(true)
    }

    private fun assertDeletionBootstrapAfterReopen(committed: Boolean) {
        val audio = identity()
        val run = RunId.fromCanonicalString(id(80))
        val databaseFile = file()
        open(databaseFile).use { journal ->
            assertTrue(journal.catalog.create(audio))
            journal.catalog.tryAcquire(audio)!!.use {
                reserve(journal, audio)
                if (committed) bootstrap(journal, id(80))
                journal.beginDeletion(audio, emptyList())
            }
        }
        open(databaseFile).use { journal ->
            journal.catalog.tryAcquire(audio)!!.use {
                assertEquals(committed, journal.hasCommittedDeletionBootstrap(audio, run))
                // Checkpoint keys first; this fixture verifies journal readback only.
                journal
                    .loadDeletion(audio)!!
                    .steps
                    .sortedBy { it.target.kind != AudioDeletionTargetKind.KEY_REFERENCE }
                    .forEach { step ->
                        journal.completeDeletionTarget(audio, step.target) { true }
                    }
                journal.completeDeletion(audio) { true }
                assertThrows(IllegalStateException::class.java) {
                    journal.hasCommittedDeletionBootstrap(audio, run)
                }
            }
        }
    }

    @Test
    fun unverifiedKeyAbsenceBlocksFileCheckpointAndItsVerification() {
        open(file()).use { journal ->
            val audio = identity()
            assertTrue(journal.catalog.create(audio))
            journal.catalog.tryAcquire(audio)!!.use {
                reserve(journal, audio)
                bootstrap(journal, id(80))
                val deletion = journal.beginDeletion(audio, emptyList())
                val key =
                    deletion.steps
                        .single { it.target.kind == AudioDeletionTargetKind.KEY_REFERENCE }
                        .target
                val artifact =
                    deletion.steps
                        .single { it.target.kind == AudioDeletionTargetKind.ARTIFACT }
                        .target
                var keyVerified = false
                assertThrows(IllegalStateException::class.java) {
                    journal.completeDeletionTarget(audio, key) {
                        keyVerified = true
                        false
                    }
                }
                assertTrue(keyVerified)
                var fileVerified = false
                assertThrows(IllegalStateException::class.java) {
                    journal.completeDeletionTarget(audio, artifact) {
                        fileVerified = true
                        true
                    }
                }
                assertFalse(fileVerified)
                assertTrue(journal.loadDeletion(audio)!!.steps.none { it.completed })
            }
        }
    }
}
