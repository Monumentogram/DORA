@file:Suppress("LongMethod")

package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAad
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeTargetKind
import com.monumentogram.dora.poc.recovery.contract.PublicationAad
import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryCheckpoint
import com.monumentogram.dora.poc.recovery.contract.RecoveryCheckpointCodec
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointIdentityInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournal
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalReadResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeAttempt
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRangeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingWitnessInput
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.contract.StreamingAad
import com.monumentogram.dora.poc.recovery.coordination.RecoveryRunSingleWriterGuard
import com.monumentogram.dora.poc.recovery.coordination.RecoveryRunWriterLease
import com.monumentogram.dora.poc.recovery.crypto.RecordingRunAeadBackend
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import com.monumentogram.dora.poc.recovery.crypto.RecoveryTinkRuntime
import com.monumentogram.dora.poc.recovery.storage.RecoveryArtifactAccessException
import com.monumentogram.dora.poc.recovery.storage.RecoveryOpenedStreamingSource
import com.monumentogram.dora.poc.recovery.storage.RecoveryReplayHashOnlyResult
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamOpenRequest
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamReplayRequest
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamingReplayAccess
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamingSource
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamingSourceLeaseAccess
import com.monumentogram.dora.poc.recovery.storage.RecoveryUnsafePathException
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// Shared real-Tink fixtures cover committed prerequisites and orphan reconciliation together.
@Suppress("LargeClass")
class RecoveryStreamingTinkPrerequisiteCryptoTest {
    @Test
    fun `SPL01 final duplicate checkpoint deletion preserves surviving committed chain`() {
        val previous = tinkFixture()
        val current = tinkFixture(generation = 2UL, previous = previous.row.checkpointSha256)
        val f = OrphanFixture(current)
        f.chain = listOf(previous.row)
        f.recover()
        assertEquals(listOf(previous.row), f.chain)
        assertEquals(2, f.rows.size)
        assertTrue(f.rows.values.all { it.state == QuarantineIntentState.COMPLETED })
        assertEquals(1, f.authenticated)
        f.recover()
        assertEquals(listOf(previous.row), f.chain)
        assertEquals(2, f.rows.size)
        assertEquals(2, f.authenticated)
    }

    @Test
    fun `SPL01 missing predecessor or unavailable prefix digest does not authenticate or quarantine`() {
        val previous = tinkFixture()
        val f =
            OrphanFixture(tinkFixture(generation = 2UL, previous = previous.row.checkpointSha256))
        f.recover()
        assertEquals(0, f.authenticated)
        assertTrue(f.rows.isEmpty())
        val g = OrphanFixture(tinkFixture())
        g.witness =
            g.witness.copy(preFaultSourceBytes = 8_193UL, controllerSnapshotSha256 = null).let {
                it.copy(controllerSnapshotSha256 = RecoveryStreamingIdentity.controllerSnapshot(it))
            }
        g.recover()
        assertEquals(0, g.authenticated)
        assertTrue(g.rows.isEmpty())
    }

    @Test
    fun `SPL01 real Tink orphan is quarantined with stable replay and zero stream opens`() {
        val f = OrphanFixture(tinkFixture())
        val first = f.recover() as RecoveryStreamingReconciliationResult.Fatal
        assertEquals(
            RecoveryStreamingResultClassification.STREAM_CHECKPOINT_MISSING,
            first.classification,
        )
        assertEquals(null, first.persistedDiagnostic)
        assertEquals(null, first.originalDiagnostic)
        assertEquals(2, f.rows.size)
        assertTrue(f.rows.values.all { it.state == QuarantineIntentState.COMPLETED })
        assertEquals(2, f.destinations.size)
        assertEquals(setOf("key-envelopes/stream.ks"), f.active.keys)
        assertEquals(1, f.authenticated)
        val rows = f.rows.toMap()
        val destinations = f.destinations.mapValues { Sha256Value.calculate(it.value) }
        val commits = f.commits
        f.recover()
        assertEquals(rows, f.rows)
        assertEquals(destinations, f.destinations.mapValues { Sha256Value.calculate(it.value) })
        assertEquals(commits, f.commits)
        assertEquals(2, f.authenticated)
        assertEquals(4, f.completedEvidence.size)
        assertEquals(f.completedEvidence.take(2), f.completedEvidence.drop(2))
    }

    @Test
    fun `SPL01 interruption after rename resumes pending transaction without new identity`() {
        val f = OrphanFixture(tinkFixture())
        f.failAfterRename = true
        assertTrue(f.recover() is RecoveryStreamingReconciliationResult.Retry)
        assertEquals(1, f.rows.size)
        assertEquals(QuarantineIntentState.PENDING, f.rows.values.single().state)
        val pendingId = f.rows.keys.single()
        assertEquals(1, f.destinations.size)
        f.recover()
        assertTrue(f.rows.containsKey(pendingId))
        assertEquals(2, f.rows.size)
        assertTrue(f.rows.values.all { it.state == QuarantineIntentState.COMPLETED })
        assertEquals(2, f.destinations.size)
    }

    @Test
    fun `SPL01 tampered orphan and wrong witness retain artifacts without quarantine`() {
        val f = OrphanFixture(tinkFixture())
        val name = f.fixture.row.checkpointRelativeName
        f.active[name] = f.active.getValue(name).copyOf().also { it[0] = (it[0] + 1).toByte() }
        val result = f.recover() as RecoveryStreamingReconciliationResult.Fatal
        assertEquals(
            RecoveryStreamingResultClassification.STREAM_CHECKPOINT_STRUCTURAL,
            result.classification,
        )
        assertEquals(0, f.authenticated)
        assertTrue(f.rows.isEmpty())
        assertEquals(3, f.active.size)
        val g = OrphanFixture(tinkFixture())
        g.witness = g.witness.copy(controllerSnapshotSha256 = Sha256Value.ZERO)
        assertTrue(g.recover() is RecoveryStreamingReconciliationResult.Fatal)
        assertTrue(g.rows.isEmpty())
    }

    @Test
    fun `SPL01 valid identity hash cannot authorize cryptographically wrong checkpoint AAD`() {
        val f = OrphanFixture(tinkFixture())
        val name = f.fixture.row.checkpointRelativeName
        val damaged =
            f.active.getValue(name).copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        f.active[name] = damaged
        // A valid unkeyed identity is not an authentication proof.
        val input =
            f.fixture.row.identityInput().copy(checkpointSha256 = Sha256Value.calculate(damaged))
        f.witness =
            f.witness
                .copy(
                    checkpointIdentity = RecoveryStreamingIdentity.checkpoint(input),
                    controllerSnapshotSha256 = null,
                )
                .let {
                    it.copy(
                        controllerSnapshotSha256 = RecoveryStreamingIdentity.controllerSnapshot(it)
                    )
                }
        val result = f.recover() as RecoveryStreamingReconciliationResult.Fatal
        assertEquals(
            RecoveryStreamingResultClassification.STREAM_CHECKPOINT_AUTHENTICATION_REJECTED,
            result.classification,
        )
        assertEquals(0, f.authenticated)
        assertTrue(f.rows.isEmpty())
        assertEquals(3, f.active.size)
    }

    @Test
    fun `SPL01 quarantine destination collision cannot complete or overwrite`() {
        val f = OrphanFixture(tinkFixture())
        val input =
            RecoveryQuarantineIntentInput(
                RecoveryCandidate.STREAM,
                f.witness.runId,
                f.fixture.row.checkpointRelativeName,
                RecoveryQuarantineArtifactRole.CHECKPOINT_CIPHERTEXT,
                f.fixture.row.checkpointBytes,
                f.fixture.row.checkpointSha256,
            )
        val destination = RecoveryQuarantineIntent.destination(input)
        f.destinations[destination] = byteArrayOf(9)
        assertTrue(f.recover() is RecoveryStreamingReconciliationResult.Fatal)
        assertArrayEquals(byteArrayOf(9), f.destinations[destination])
        assertEquals(3, f.active.size)
        assertTrue(f.rows.values.none { it.state == QuarantineIntentState.COMPLETED })
    }

    @Test
    fun `SPL01 production loader rejects completed row with source still active`() {
        val f = OrphanFixture(tinkFixture())
        f.recover()
        val row = f.rows.values.first()
        f.active[row.input.sourceRelativeName] =
            f.destinations.remove(row.destinationRelativeName)!!
        val result = f.recover() as RecoveryStreamingReconciliationResult.Fatal
        assertEquals(
            RecoveryStreamingResultClassification.JOURNAL_STRUCTURAL,
            result.classification,
        )
        assertEquals(RecoveryStreamingResultStage.JOURNAL, result.stage)
        assertEquals(1, f.authenticated)
    }

    @Test
    fun `SPL01 production loader preserves SQLite operational failure provenance`() {
        val f = OrphanFixture(tinkFixture())
        f.journalReadFailure = true
        val result = f.recover() as RecoveryStreamingReconciliationResult.Retry
        assertEquals(
            RecoveryStreamingResultClassification.JOURNAL_OPERATIONAL,
            result.classification,
        )
        assertEquals(RecoveryStreamingResultStage.JOURNAL, result.stage)
        assertEquals(RecoveryStreamingSafeExceptionType.SQLITE, result.safeExceptionType)
        assertEquals(0, f.authenticated)
        assertTrue(f.rows.isEmpty())
    }

    @Test
    fun `SPL01 production replay preserves unsafe destination classification`() {
        val f = OrphanFixture(tinkFixture())
        f.recover()
        f.unsafeDestination = true
        val result = f.recover() as RecoveryStreamingReconciliationResult.Fatal
        assertEquals(RecoveryStreamingResultClassification.UNSAFE_PATH, result.classification)
        assertEquals(RecoveryStreamingResultStage.PREREQUISITE, result.stage)
        assertEquals(1, f.authenticated)
    }

    @Test
    fun `SPL01 Q01 end uncertainty preserves unresolved journal classification`() =
        assertUnresolved(QuarantineStep.Q01)

    @Test
    fun `SPL01 Q05 end uncertainty preserves unresolved journal classification`() =
        assertUnresolved(QuarantineStep.Q05)

    private fun assertUnresolved(step: QuarantineStep) {
        val f = OrphanFixture(tinkFixture())
        f.failedEnd = step
        val result = f.recover() as RecoveryStreamingReconciliationResult.Retry
        assertEquals(
            RecoveryStreamingResultClassification.JOURNAL_COMMIT_STATE_UNRESOLVED,
            result.classification,
        )
        assertEquals(RecoveryStreamingResultStage.JOURNAL, result.stage)
        assertEquals(RecoveryStreamingSafeExceptionType.SQLITE, result.safeExceptionType)
        assertEquals(null, result.attemptedOutcomeId)
        assertEquals(null, result.attemptedRangeId)
        assertEquals(1, f.authenticated)
        assertTrue(f.rows.values.none { it.state == QuarantineIntentState.COMPLETED })
    }

    @Test
    fun `SPL01 known begin failure is operational and exact post commit readback resolves uncertainty`() {
        for (step in listOf(QuarantineStep.Q01, QuarantineStep.Q05)) {
            val f = OrphanFixture(tinkFixture())
            f.failedBegin = step
            val result = f.recover() as RecoveryStreamingReconciliationResult.Retry
            assertEquals(
                RecoveryStreamingResultClassification.JOURNAL_OPERATIONAL,
                result.classification,
            )
            val g = OrphanFixture(tinkFixture())
            g.failedEnd = step
            g.commitBeforeThrow = true
            val recovered = g.recover() as RecoveryStreamingReconciliationResult.Fatal
            assertEquals(
                RecoveryStreamingResultClassification.STREAM_CHECKPOINT_MISSING,
                recovered.classification,
            )
            assertEquals(2, g.rows.size)
            assertTrue(g.rows.values.all { it.state == QuarantineIntentState.COMPLETED })
        }
    }

    @Test
    fun `SPL01 Q01 structural readback disagreement is fatal`() {
        val f = OrphanFixture(tinkFixture())
        f.failAfterRename = true
        f.recover()
        f.mismatchedReadback = true
        val result = f.recover() as RecoveryStreamingReconciliationResult.Fatal
        assertEquals(
            RecoveryStreamingResultClassification.JOURNAL_STRUCTURAL,
            result.classification,
        )
        assertEquals(RecoveryStreamingResultStage.JOURNAL, result.stage)
        assertEquals(1, f.destinations.size)
    }

    @Test
    fun `SPL01 P below S authenticates using same prefix predecessor and replays exactly`() {
        val previous = tinkFixture(plaintextBytes = 9_000)
        val current =
            tinkFixture(
                generation = 2UL,
                previous = previous.row.checkpointSha256,
                sameStream = previous,
            )
        val f = OrphanFixture(current)
        f.chain = listOf(previous.row)
        f.witness =
            f.witness
                .copy(
                    preFaultSourceBytes = current.streamCiphertext.size.toULong(),
                    preFaultSourceSha256 = Sha256Value.calculate(current.streamCiphertext),
                    controllerSnapshotSha256 = null,
                )
                .let {
                    it.copy(
                        controllerSnapshotSha256 = RecoveryStreamingIdentity.controllerSnapshot(it)
                    )
                }
        assertTrue(f.witness.checkpointPrefixBytes < f.witness.preFaultSourceBytes)
        assertEquals(
            previous.row.streamCiphertextPrefixSha256,
            current.row.streamCiphertextPrefixSha256,
        )
        repeat(2) {
            val result = f.recover() as RecoveryStreamingReconciliationResult.Fatal
            assertEquals(
                RecoveryStreamingResultClassification.STREAM_CHECKPOINT_MISSING,
                result.classification,
            )
            assertEquals(listOf(previous.row), f.chain)
            assertEquals(2, f.rows.size)
            assertTrue(f.rows.values.all { row -> row.state == QuarantineIntentState.COMPLETED })
        }
        assertEquals(2, f.authenticated)
        assertEquals(4, f.commits)
    }

    @Test
    fun `SPL01 production cursor rejects ambiguous valid source versions and malformed metadata`() {
        val seed = OrphanFixture(tinkFixture())
        seed.recover()
        val row = seed.rows.values.first()
        val otherInput = row.input.copy(sourceSha256 = Sha256Value.calculate(byteArrayOf(9)))
        val other =
            row.copy(
                input = otherInput,
                intentId = RecoveryQuarantineIntent.calculate(otherInput),
                destinationRelativeName = RecoveryQuarantineIntent.destination(otherInput),
            )
        val valid = quarantineColumns(row)
        val cases =
            listOf(
                listOf(valid, quarantineColumns(other)),
                listOf(valid.toMutableList().also { it[0] = Sha256Value.ZERO.toByteArray() }),
                listOf(
                    valid.toMutableList().also {
                        it[4] = RunId.fromBytes(ByteArray(16)).toCanonicalString()
                    }
                ),
                listOf(valid.toMutableList().also { it[9] = "objects/wrong" }),
                listOf(valid.toMutableList().also { it[6] = "INVALID_ROLE" }),
            )
        for (columns in cases) {
            val f = OrphanFixture(seed.fixture)
            f.journalCursor = quarantineCursor(columns)
            val result = f.recover() as RecoveryStreamingReconciliationResult.Fatal
            assertEquals(
                RecoveryStreamingResultClassification.JOURNAL_STRUCTURAL,
                result.classification,
            )
            assertEquals(RecoveryStreamingResultStage.JOURNAL, result.stage)
            assertEquals(0, f.authenticated)
            assertTrue(f.rows.isEmpty())
            assertTrue(f.destinations.isEmpty())
        }
        val decoded =
            com.monumentogram.dora.poc.recovery.journal.RecoveryStreamingQuarantineReadback.read(
                quarantineCursor(listOf(valid))
            )
        assertEquals(row, decoded)
    }

    private fun quarantineColumns(row: RecoveryQuarantineIntentRow): List<Any?> =
        listOf(
            row.intentId.toByteArray(),
            row.input.runId.toCanonicalString(),
            row.input.candidate.contractId,
            row.bootstrapBinding.name,
            row.input.runId.toCanonicalString(),
            row.input.candidate.contractId,
            row.input.artifactRole.name,
            row.recordedObservedState.name,
            row.input.sourceRelativeName,
            row.destinationRelativeName,
            row.input.sourceBytes.toLong(),
            row.input.sourceSha256.toByteArray(),
            row.state.name,
        )

    /**
     * A cursor port fixture exercises the actual strict decoder; no Android SQLite runtime claim.
     */
    private fun quarantineCursor(rows: List<List<Any?>>): android.database.Cursor {
        var position = -1
        return java.lang.reflect.Proxy.newProxyInstance(
            android.database.Cursor::class.java.classLoader,
            arrayOf(android.database.Cursor::class.java),
        ) { _, method, args ->
            when (method.name) {
                "moveToFirst" -> {
                    position = 0
                    rows.isNotEmpty()
                }
                "moveToNext" -> {
                    position++
                    position < rows.size
                }
                "getString",
                "getBlob",
                "getLong" -> rows[position][args!![0] as Int]
                "isNull" -> rows[position][args!![0] as Int] == null
                else -> error("Unexpected cursor operation: " + method.name)
            }
        } as android.database.Cursor
    }

    @Test
    fun `SPL01 quarantine adapter retains typed diagnostics and only relevant commit ambiguity`() {
        val f = OrphanFixture(tinkFixture())
        val adapter =
            RecoveryStreamingOrphanReconciler(
                RecoveryStreamingOrphanArtifacts { _, _, _ -> error("No artifact read") },
                RecoveryStreamingTinkPrerequisiteCrypto(f.fixture.runProvider::openExisting),
                RecoveryQuarantineController(
                    f.storage,
                    f.journal,
                    RecoveryQuarantineEvidenceSink {},
                ),
            )
        fun mapped(category: RecoveryFailureCategory, remainder: QuarantineRemainder) =
            adapter.quarantineRetry(
                QuarantineResult.RetryRequired(
                    QuarantineStep.Q05,
                    RecoveryFailureDiagnostic(
                        category,
                        "synthetic",
                        "synthetic",
                        RecoveryFailureStage.JOURNAL,
                    ),
                    null,
                    remainder,
                )
            )
        val unrelated = QuarantineRemainder(intentCommit = QuarantineOperationState.OUTCOME_UNKNOWN)
        val operational =
            mapped(RecoveryFailureCategory.UNKNOWN_OUTCOME, unrelated)
                as RecoveryStreamingReconciliationResult.Retry
        assertEquals(
            RecoveryStreamingResultClassification.JOURNAL_OPERATIONAL,
            operational.classification,
        )
        val unknown =
            QuarantineRemainder(completionCommit = QuarantineOperationState.OUTCOME_UNKNOWN)
        val structural =
            mapped(RecoveryFailureCategory.STRUCTURAL, unknown)
                as RecoveryStreamingReconciliationResult.Fatal
        assertEquals(
            RecoveryStreamingResultClassification.JOURNAL_STRUCTURAL,
            structural.classification,
        )
        for (category in
            listOf(RecoveryFailureCategory.UNSAFE_PARENT, RecoveryFailureCategory.CORRUPT_LEAF)) {
            val unsafe = mapped(category, unknown) as RecoveryStreamingReconciliationResult.Fatal
            assertEquals(RecoveryStreamingResultClassification.UNSAFE_PATH, unsafe.classification)
        }
    }

    private class OrphanFixture(val fixture: TinkFixture) {
        var witness = fixture.witness
        var chain = emptyList<RecoveryStreamingCheckpointRow>()
        val active =
            mutableMapOf(
                fixture.row.checkpointRelativeName to fixture.checkpointCiphertext,
                fixture.row.checkpointKeyEnvelopeRelativeName to fixture.checkpointEnvelope,
                "key-envelopes/stream.ks" to fixture.streamEnvelope,
            )
        val destinations = mutableMapOf<String, ByteArray>()
        val rows = mutableMapOf<Sha256Value, RecoveryQuarantineIntentRow>()
        val completedEvidence = mutableListOf<Sha256Value>()
        var commits = 0
        var authenticated = 0
        var failAfterRename = false
        var journalReadFailure = false
        var journalCursor: android.database.Cursor? = null
        var unsafeDestination = false
        var failedBegin: QuarantineStep? = null
        var failedEnd: QuarantineStep? = null
        var commitBeforeThrow = false
        var mismatchedReadback = false
        val journal =
            object : RecoveryQuarantineJournal {
                override fun load(intentId: Sha256Value) = rows[intentId]

                override fun loadBySource(input: RecoveryQuarantineIntentInput) =
                    rows.values
                        .singleOrNull { it.input == input }
                        ?.let {
                            if (mismatchedReadback) it.copy(state = QuarantineIntentState.COMPLETED)
                            else it
                        }

                override fun beginNonExclusive(): RecoveryQuarantineTransaction {
                    val step = if (rows.isEmpty()) QuarantineStep.Q01 else QuarantineStep.Q05
                    if (failedBegin == step) error("synthetic begin failure")
                    return object : RecoveryQuarantineTransaction {
                        var proposed: RecoveryQuarantineIntentRow? = null
                        var completeId: Sha256Value? = null
                        var success = false

                        override fun insert(row: RecoveryQuarantineIntentRow) {
                            check(!rows.containsKey(row.intentId))
                            proposed = row
                        }

                        override fun complete(intentId: Sha256Value) {
                            check(rows[intentId]?.state == QuarantineIntentState.PENDING)
                            completeId = intentId
                        }

                        override fun markSuccessful() {
                            success = true
                        }

                        override fun end() {
                            val step =
                                if (proposed != null) QuarantineStep.Q01 else QuarantineStep.Q05
                            if (success && failedEnd == step && !commitBeforeThrow)
                                error("synthetic end outcome unknown")
                            if (success) {
                                proposed?.let { rows[it.intentId] = it }
                                completeId?.let {
                                    rows[it] =
                                        rows
                                            .getValue(it)
                                            .copy(state = QuarantineIntentState.COMPLETED)
                                }
                                commits++
                            }
                            if (success && failedEnd == step) error("synthetic end after commit")
                        }
                    }
                }
            }
        val storage =
            object : RecoveryQuarantineStorage {
                override fun prepare(runId: RunId) {
                    assertEquals(witness.runId, runId)
                }

                override fun inspect(row: RecoveryQuarantineIntentRow): QuarantinePathObservation {
                    fun state(bytes: ByteArray?) =
                        when {
                            bytes == null -> QuarantinePathState.ABSENT
                            bytes.size.toULong() == row.input.sourceBytes &&
                                Sha256Value.calculate(bytes) == row.input.sourceSha256 ->
                                QuarantinePathState.EXACT
                            else -> QuarantinePathState.OCCUPIED
                        }
                    return QuarantinePathObservation(
                        state(active[row.input.sourceRelativeName]),
                        if (unsafeDestination) QuarantinePathState.UNSAFE
                        else state(destinations[row.destinationRelativeName]),
                    )
                }

                override fun renameNoOverwrite(row: RecoveryQuarantineIntentRow) {
                    assertEquals(
                        QuarantinePathObservation(
                            QuarantinePathState.EXACT,
                            QuarantinePathState.ABSENT,
                        ),
                        inspect(row),
                    )
                    destinations[row.destinationRelativeName] =
                        requireNotNull(active.remove(row.input.sourceRelativeName))
                    if (failAfterRename) {
                        failAfterRename = false
                        throw java.io.IOException("synthetic interruption")
                    }
                }

                override fun fsyncSourceParent(row: RecoveryQuarantineIntentRow) = Unit

                override fun fsyncDestinationParent(row: RecoveryQuarantineIntentRow) = Unit
            }

        fun recover(): RecoveryStreamingReconciliationResult {
            val handler =
                RecoveryStreamingOrphanReconciler(
                    AndroidRecoveryStreamingOrphanArtifacts(
                        { run, name, _ ->
                            assertEquals(witness.runId, run)
                            assertTrue(
                                name == fixture.row.checkpointRelativeName ||
                                    name == fixture.row.checkpointKeyEnvelopeRelativeName ||
                                    name == "key-envelopes/stream.ks"
                            )
                            active[name]?.let { RecoveryArtifactBytes(name, it) }
                        },
                        { _, name ->
                            if (journalReadFailure) error("synthetic closed SQLite connection")
                            journalCursor?.let {
                                com.monumentogram.dora.poc.recovery.journal
                                    .RecoveryStreamingQuarantineReadback
                                    .read(it)
                            } ?: rows.values.singleOrNull { it.input.sourceRelativeName == name }
                        },
                        storage::inspect,
                        { row ->
                            destinations[row.destinationRelativeName]?.let {
                                RecoveryArtifactBytes(row.input.sourceRelativeName, it)
                            }
                        },
                    ),
                    RecoveryStreamingTinkPrerequisiteCrypto(fixture.runProvider::openExisting),
                    RecoveryQuarantineController(
                        storage,
                        journal,
                        RecoveryQuarantineEvidenceSink { completedEvidence += it.intentId },
                    ),
                    { if (it is RecoveryStreamingCheckpointAuthentication.Ready) authenticated++ },
                )
            val streamJournal =
                object : RecoveryStreamingJournal {
                    override fun checkpointChain(runId: RunId) =
                        RecoveryStreamingJournalReadResult.Value(chain)

                    override fun outcomeById(
                        outcomeId: Sha256Value
                    ): RecoveryStreamingJournalReadResult<RecoveryStreamingOutcomeRow?> =
                        error("No orphan outcome")

                    override fun outcomeByWitness(
                        runId: RunId,
                        checkpointIdentity: Sha256Value,
                        witnessId: Sha256Value,
                    ): RecoveryStreamingJournalReadResult<RecoveryStreamingOutcomeRow?> =
                        error("No orphan outcome")

                    override fun rangeByOutcome(
                        outcomeId: Sha256Value
                    ): RecoveryStreamingJournalReadResult<RecoveryStreamingRangeRow?> =
                        error("No orphan range")

                    override fun activeRanges(
                        runId: RunId,
                        sourceRelativeName: String,
                    ): RecoveryStreamingJournalReadResult<List<RecoveryStreamingRangeRow>> =
                        error("No stream open")

                    override fun insertCheckpoint(
                        row: RecoveryStreamingCheckpointRow
                    ): RecoveryStreamingJournalResult = error("No implicit commit")

                    override fun persistOutcome(
                        attempt: RecoveryStreamingOutcomeAttempt
                    ): RecoveryStreamingJournalResult = error("No orphan outcome")
                }
            val streamSource =
                object : RecoveryStreamingSource {
                    override fun <T> withSource(
                        access: RecoveryStreamingSourceLeaseAccess,
                        request: RecoveryStreamOpenRequest,
                        block: (RecoveryOpenedStreamingSource) -> T,
                    ): T = error("Orphan must not open stream")

                    override fun verifyReplayHashOnly(
                        access: RecoveryStreamingReplayAccess,
                        request: RecoveryStreamReplayRequest,
                    ): RecoveryReplayHashOnlyResult = error("Orphan must not open stream")
                }
            return RecoveryStreamingReconciliationController(
                    streamJournal,
                    streamSource,
                    RecoveryRunSingleWriterGuard { RecoveryRunWriterLease {} },
                    RecoveryStreamingCheckpointAuthenticator { _, _ ->
                        error("No committed row authentication")
                    },
                    RecoveryStreamingEvidenceSink {},
                    handler,
                )
                .recover(
                    RecoveryStreamingControllerRequest(
                        witness,
                        RecoveryStreamingIntentBuilder.RecoveryStreamingOracle.from(
                            witness,
                            fixture.plaintext,
                        ),
                    )
                )
        }
    }

    @Test
    fun `Android prerequisite source maps storage failures without artifact data`() {
        val runId = RunId.fromBytes(ByteArray(16))
        val expected =
            RecoveryArtifactBytes("checkpoints/g-00000000000000000001.ct", byteArrayOf(1))
        val successful = AndroidRecoveryStreamingPrerequisiteSource { _, _, _ -> expected }
        assertTrue(
            successful.load(
                runId,
                expected.relativeName,
                RecoveryStreamingPrerequisiteArtifactKind.CHECKPOINT_CIPHERTEXT,
            ) === expected
        )

        val unsafe =
            assertThrows(RecoveryStreamingPrerequisiteSourceException::class.java) {
                AndroidRecoveryStreamingPrerequisiteSource { _, _, _ ->
                        throw RecoveryUnsafePathException("private")
                    }
                    .load(
                        runId,
                        expected.relativeName,
                        RecoveryStreamingPrerequisiteArtifactKind.CHECKPOINT_CIPHERTEXT,
                    )
            }
        assertEquals(RecoveryStreamingPrerequisiteSourceFailure.UNSAFE_PATH, unsafe.failure)

        val operational =
            assertThrows(RecoveryStreamingPrerequisiteSourceException::class.java) {
                AndroidRecoveryStreamingPrerequisiteSource { _, _, _ ->
                        throw RecoveryArtifactAccessException(
                            RecoveryArtifactPresence.PRESENT,
                            structural = false,
                            IllegalStateException("private"),
                        )
                    }
                    .load(
                        runId,
                        expected.relativeName,
                        RecoveryStreamingPrerequisiteArtifactKind.CHECKPOINT_CIPHERTEXT,
                    )
            }
        assertEquals(RecoveryStreamingPrerequisiteSourceFailure.OPERATIONAL, operational.failure)
    }

    @Test
    fun `authenticated unsafe checkpoint reaches controller without stream access or writes`() {
        for (name in listOf("stream/stream.ct", "key-envelopes/stream.ks")) {
            for (replacement in listOf("/x/", "../")) {
                val fixture =
                    tinkFixture(
                        checkpointPlaintextTransform = {
                            mutateCheckpointName(it, name, replacement)
                        }
                    )
                val loaded = mutableListOf<String>()
                val artifacts =
                    mapOf(
                        fixture.row.checkpointKeyEnvelopeRelativeName to fixture.checkpointEnvelope,
                        fixture.row.checkpointRelativeName to fixture.checkpointCiphertext,
                        fixture.row.streamKeyEnvelopeRelativeName to fixture.streamEnvelope,
                    )
                val kinds =
                    mapOf(
                        fixture.row.checkpointKeyEnvelopeRelativeName to
                            RecoveryStreamingPrerequisiteArtifactKind.CHECKPOINT_KEY_ENVELOPE,
                        fixture.row.checkpointRelativeName to
                            RecoveryStreamingPrerequisiteArtifactKind.CHECKPOINT_CIPHERTEXT,
                        fixture.row.streamKeyEnvelopeRelativeName to
                            RecoveryStreamingPrerequisiteArtifactKind.STREAM_KEY_ENVELOPE,
                    )
                var sourceOpens = 0
                var replayReads = 0
                var checkpointWrites = 0
                var outcomeWrites = 0
                val chain = listOf(fixture.row)
                val journal =
                    object : RecoveryStreamingJournal {
                        override fun checkpointChain(runId: RunId) =
                            RecoveryStreamingJournalReadResult.Value(chain).also {
                                assertEquals(fixture.row.runId, runId)
                            }

                        override fun outcomeById(
                            outcomeId: Sha256Value
                        ): RecoveryStreamingJournalReadResult<RecoveryStreamingOutcomeRow?> =
                            error("No unsafe checkpoint outcome")

                        override fun outcomeByWitness(
                            runId: RunId,
                            checkpointIdentity: Sha256Value,
                            witnessId: Sha256Value,
                        ): RecoveryStreamingJournalReadResult<RecoveryStreamingOutcomeRow?> {
                            assertEquals(fixture.row.runId, runId)
                            assertEquals(fixture.row.checkpointIdentity, checkpointIdentity)
                            assertEquals(
                                RecoveryStreamingIdentity.witness(fixture.witness),
                                witnessId,
                            )
                            return RecoveryStreamingJournalReadResult.Value(null)
                        }

                        override fun rangeByOutcome(
                            outcomeId: Sha256Value
                        ): RecoveryStreamingJournalReadResult<RecoveryStreamingRangeRow?> =
                            error("No unsafe checkpoint range")

                        override fun activeRanges(
                            runId: RunId,
                            sourceRelativeName: String,
                        ): RecoveryStreamingJournalReadResult<List<RecoveryStreamingRangeRow>> =
                            error("No source or range access before prerequisite acceptance")

                        override fun insertCheckpoint(
                            row: RecoveryStreamingCheckpointRow
                        ): RecoveryStreamingJournalResult {
                            checkpointWrites++
                            error("No implicit checkpoint write")
                        }

                        override fun persistOutcome(
                            attempt: RecoveryStreamingOutcomeAttempt
                        ): RecoveryStreamingJournalResult {
                            outcomeWrites++
                            error("No outcome or range write")
                        }
                    }
                val source =
                    object : RecoveryStreamingSource {
                        override fun <T> withSource(
                            access: RecoveryStreamingSourceLeaseAccess,
                            request: RecoveryStreamOpenRequest,
                            block: (RecoveryOpenedStreamingSource) -> T,
                        ): T {
                            sourceOpens++
                            error("Unsafe checkpoint must not open or read stream")
                        }

                        override fun verifyReplayHashOnly(
                            access: RecoveryStreamingReplayAccess,
                            request: RecoveryStreamReplayRequest,
                        ): RecoveryReplayHashOnlyResult {
                            replayReads++
                            error("Unsafe checkpoint must not read replay source")
                        }
                    }
                val androidSource =
                    AndroidRecoveryStreamingPrerequisiteSource { run, relativeName, maxBytes ->
                        assertEquals(fixture.row.runId, run)
                        assertEquals(16L * 1_024L * 1_024L, maxBytes)
                        loaded += relativeName
                        RecoveryArtifactBytes(relativeName, artifacts.getValue(relativeName))
                    }
                val authenticator =
                    RecoveryStreamingCheckpointAuthenticatorAdapter(
                        RecoveryStreamingPrerequisiteSource { run, relativeName, kind ->
                            assertEquals(kinds.getValue(relativeName), kind)
                            androidSource.load(run, relativeName, kind)
                        },
                        RecoveryStreamingTinkPrerequisiteCrypto(fixture.runProvider::openExisting),
                    )
                val controller =
                    RecoveryStreamingReconciliationController(
                        journal,
                        source,
                        RecoveryRunSingleWriterGuard { RecoveryRunWriterLease {} },
                        authenticator,
                        RecoveryStreamingEvidenceSink {},
                    )
                val request =
                    RecoveryStreamingControllerRequest(
                        fixture.witness,
                        RecoveryStreamingIntentBuilder.RecoveryStreamingOracle.from(
                            fixture.witness,
                            fixture.plaintext,
                        ),
                    )
                val results = (1..2).map { controller.recover(request) }
                for (result in results) {
                    assertTrue(result is RecoveryStreamingReconciliationResult.Fatal)
                    val fatal = result as RecoveryStreamingReconciliationResult.Fatal
                    assertEquals(RecoveryStreamingResultStage.PREREQUISITE, fatal.stage)
                    assertEquals(
                        RecoveryStreamingResultClassification.UNSAFE_PATH,
                        fatal.classification,
                    )
                    assertEquals(null, fatal.persistedDiagnostic)
                    assertEquals(null, fatal.originalDiagnostic)
                    assertTrue(fatal.existingEvidenceReferences.isEmpty())
                }
                assertEquals(artifacts.keys.toList() + artifacts.keys.toList(), loaded)
                assertEquals(listOf(fixture.row), chain)
                assertEquals(0, sourceOpens)
                assertEquals(0, replayReads)
                assertEquals(0, checkpointWrites)
                assertEquals(0, outcomeWrites)
            }
        }
    }

    @Test
    fun `authenticated checkpoint invalid relative names preserve unsafe path classification`() {
        for (name in listOf("stream/stream.ct", "key-envelopes/stream.ks")) {
            for (replacement in listOf("/x/", "../")) {
                val fixture =
                    tinkFixture(
                        checkpointPlaintextTransform = {
                            mutateCheckpointName(it, name, replacement)
                        }
                    )
                val crypto =
                    RecoveryStreamingTinkPrerequisiteCrypto(fixture.runProvider::openExisting)
                assertEquals(
                    RecoveryStreamingCheckpointAuthentication.UnsafePath,
                    crypto.authenticate(
                        fixture.row,
                        fixture.checkpointEnvelope,
                        fixture.checkpointCiphertext,
                        fixture.streamEnvelope,
                    ),
                )
            }
        }
    }

    @Test
    fun `authenticated malformed checkpoint remains structural`() {
        val transforms: List<(ByteArray) -> ByteArray> =
            listOf(
                { bytes -> bytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() } },
                { bytes -> bytes + byteArrayOf(0) },
                { bytes -> bytes.copyOf(bytes.size - 1) },
                { _ -> ByteArray(524_289) },
                { bytes ->
                    mutateCheckpointName(bytes, "stream/stream.ct", "../").also {
                        it[0] = (it[0].toInt() xor 1).toByte()
                    }
                },
            )
        for (transform in transforms) {
            val fixture = tinkFixture(checkpointPlaintextTransform = transform)
            assertEquals(
                RecoveryStreamingCheckpointAuthentication.Structural,
                RecoveryStreamingTinkPrerequisiteCrypto(fixture.runProvider::openExisting)
                    .authenticate(
                        fixture.row,
                        fixture.checkpointEnvelope,
                        fixture.checkpointCiphertext,
                        fixture.streamEnvelope,
                    ),
            )
        }
    }

    @Test
    fun `authenticated path failure precedes stream envelope parsing`() {
        for (unsafe in listOf(false, true)) {
            val fixture =
                tinkFixture(
                    checkpointPlaintextTransform = {
                        if (unsafe) mutateCheckpointName(it, "stream/stream.ct", "../") else it
                    }
                )
            assertEquals(
                if (unsafe) RecoveryStreamingCheckpointAuthentication.UnsafePath
                else RecoveryStreamingCheckpointAuthentication.Structural,
                RecoveryStreamingTinkPrerequisiteCrypto(fixture.runProvider::openExisting)
                    .authenticate(
                        fixture.row,
                        fixture.checkpointEnvelope,
                        fixture.checkpointCiphertext,
                        byteArrayOf(0),
                    ),
            )
        }
    }

    @Test
    fun `unauthenticated checkpoint cannot claim an embedded path failure`() {
        val fixture =
            tinkFixture(
                checkpointPlaintextTransform = {
                    mutateCheckpointName(it, "stream/stream.ct", "../")
                }
            )
        val damaged = fixture.checkpointCiphertext.copyOf()
        damaged[damaged.lastIndex] = (damaged.last().toInt() xor 1).toByte()
        assertEquals(
            RecoveryStreamingCheckpointAuthentication.Rejected,
            RecoveryStreamingTinkPrerequisiteCrypto(fixture.runProvider::openExisting)
                .authenticate(
                    fixture.row,
                    fixture.checkpointEnvelope,
                    damaged,
                    fixture.streamEnvelope,
                ),
        )
    }

    @Test
    fun `tink prerequisite authenticates exact checkpoint and exposes descriptor stream`() {
        val fixture = tinkFixture()
        val crypto = RecoveryStreamingTinkPrerequisiteCrypto(fixture.runProvider::openExisting)

        val authentication =
            crypto.authenticate(
                fixture.row,
                fixture.checkpointEnvelope,
                fixture.checkpointCiphertext,
                fixture.streamEnvelope,
            ) as RecoveryStreamingCheckpointAuthentication.Ready
        val read =
            authentication.publicStreamOpener.open(
                opened(fixture.streamCiphertext),
                fixture.witness,
            )

        assertArrayEquals(fixture.plaintext, read.use { it.readAll() })
    }

    @Test
    fun `tink prerequisite rejects ciphertext auth and structural plaintext mismatch`() {
        val fixture = tinkFixture()
        val crypto = RecoveryStreamingTinkPrerequisiteCrypto(fixture.runProvider::openExisting)

        val tampered =
            fixture.checkpointCiphertext.copyOf().also {
                it[it.lastIndex] = (it.last() + 1).toByte()
            }
        assertTrue(
            crypto.authenticate(
                fixture.row,
                fixture.checkpointEnvelope,
                tampered,
                fixture.streamEnvelope,
            ) === RecoveryStreamingCheckpointAuthentication.Rejected
        )

        val mismatchFixture =
            tinkFixture(
                checkpointPlaintextTransform = { bytes ->
                    val checkpoint = RecoveryCheckpointCodec.decode(bytes)
                    RecoveryCheckpointCodec.encode(
                        checkpoint.copy(
                            streamKeyEnvelopeBytes = checkpoint.streamKeyEnvelopeBytes + 1UL
                        )
                    )
                }
            )
        val mismatchCrypto =
            RecoveryStreamingTinkPrerequisiteCrypto(mismatchFixture.runProvider::openExisting)
        assertTrue(
            mismatchCrypto.authenticate(
                mismatchFixture.row,
                mismatchFixture.checkpointEnvelope,
                mismatchFixture.checkpointCiphertext,
                mismatchFixture.streamEnvelope,
            ) === RecoveryStreamingCheckpointAuthentication.Structural
        )
    }

    private fun RecoveryStreamingIntentBuilder.RecoveryStreamingPublicRead.readAll(): ByteArray {
        val output = ByteArrayOutputStream()
        var request = 4_056
        while (true) {
            val buffer = ByteArray(request)
            val count = read(buffer, 0, buffer.size)
            if (count == -1) return output.toByteArray()
            output.write(buffer, 0, count)
            request = 4_080
        }
    }

    private fun opened(bytes: ByteArray) =
        object : RecoveryOpenedStreamingSource {
            override val observedBytes = bytes.size.toULong()

            override fun sha256Prefix(endExclusive: ULong) =
                Sha256Value.calculate(bytes.copyOfRange(0, endExclusive.toInt()))

            override fun sha256Range(startInclusive: ULong, endExclusive: ULong) =
                Sha256Value.calculate(
                    bytes.copyOfRange(startInclusive.toInt(), endExclusive.toInt())
                )

            override fun boundedInputStream() = bytes.inputStream()
        }

    private fun mutateCheckpointName(
        bytes: ByteArray,
        name: String,
        replacement: String,
    ): ByteArray {
        val encoded = name.toByteArray(Charsets.US_ASCII)
        val offset =
            bytes.indices
                .filter { start ->
                    start + encoded.size <= bytes.size &&
                        encoded.indices.all { bytes[start + it] == encoded[it] }
                }
                .single()
        return bytes.copyOf().also {
            replacement.toByteArray(Charsets.US_ASCII).copyInto(it, offset)
        }
    }

    private fun tinkFixture(
        generation: ULong = 1UL,
        previous: Sha256Value = Sha256Value.ZERO,
        sameStream: TinkFixture? = null,
        plaintextBytes: Int = 8_136,
        checkpointPlaintextTransform: (ByteArray) -> ByteArray = { it },
    ): TinkFixture {
        val runId = RunId.fromBytes(ByteArray(16) { (it + 7).toByte() })
        val backend = RecordingRunAeadBackend()
        val provider = sameStream?.runProvider ?: RecoveryRunAeadProvider(backend)
        val runAead =
            if (sameStream == null) provider.createNew(runId) else provider.openExisting(runId)
        val streamAad =
            KeyEnvelopeAad(
                RecoveryCandidate.STREAM,
                runId,
                KeyEnvelopeTargetKind.STREAM,
                1UL,
                KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
                0UL,
                115_200_000UL,
                0UL,
                Sha256Value.ZERO,
            )
        val streamKeyset = RecoveryTinkRuntime.newStreamingKeyset(streamAad)
        val streamEnvelope = sameStream?.streamEnvelope ?: streamKeyset.serializeEncrypted(runAead)
        val plaintext =
            sameStream?.plaintext ?: ByteArray(plaintextBytes) { ((it * 13 + 3) and 0xff).toByte() }
        val streamDestination = ByteArrayOutputStream()
        if (sameStream == null)
            streamKeyset
                .newEncryptingStream(
                    streamDestination,
                    StreamingAad(RecoveryCandidate.STREAM, runId),
                )
                .use { it.write(plaintext) }
        val streamCiphertext = sameStream?.streamCiphertext ?: streamDestination.toByteArray()
        val checkpoint =
            RecoveryCheckpoint(
                RecoveryCandidate.STREAM,
                runId,
                generation,
                previous,
                2UL,
                8_192UL,
                4_056UL,
                streamEnvelope.size.toULong(),
                Sha256Value.calculate(streamEnvelope),
                "stream/stream.ct",
                "key-envelopes/stream.ks",
            )
        val checkpointEnvelopeAad =
            KeyEnvelopeAad(
                RecoveryCandidate.STREAM,
                runId,
                KeyEnvelopeTargetKind.CHECKPOINT,
                generation,
                KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
                0UL,
                4_056UL,
                0UL,
                previous,
            )
        val checkpointKeyset = RecoveryTinkRuntime.newAeadKeyset(checkpointEnvelopeAad)
        val checkpointEnvelope = checkpointKeyset.serializeEncrypted(runAead)
        val checkpointPublicationAad =
            PublicationAad(
                RecoveryCandidate.STREAM,
                runId,
                PublicationKind.CHECKPOINT,
                generation,
                1UL,
                4_056UL,
                previous,
            )
        val checkpointPlaintext =
            checkpointPlaintextTransform(RecoveryCheckpointCodec.encode(checkpoint))
        val checkpointCiphertext =
            checkpointKeyset.encryptPublication(checkpointPlaintext, checkpointPublicationAad)
        assertArrayEquals(
            checkpointPlaintext,
            checkpointKeyset.decryptPublication(checkpointCiphertext, checkpointPublicationAad),
        )
        val input =
            RecoveryStreamingCheckpointIdentityInput(
                runId,
                generation,
                2UL,
                8_192UL,
                Sha256Value.calculate(streamCiphertext.copyOfRange(0, 8_192)),
                4_056UL,
                RecoveryRelativeNames.checkpointCiphertext(generation),
                checkpointCiphertext.size.toULong(),
                Sha256Value.calculate(checkpointCiphertext),
                RecoveryRelativeNames.checkpointKeyEnvelope(generation),
                checkpointEnvelope.size.toULong(),
                Sha256Value.calculate(checkpointEnvelope),
                "stream/stream.ct",
                "key-envelopes/stream.ks",
                streamEnvelope.size.toULong(),
                Sha256Value.calculate(streamEnvelope),
                previous,
            )
        val row =
            RecoveryStreamingCheckpointRow(
                input.runId,
                input.generation,
                input.durableNonFinalSegmentCount,
                input.streamCiphertextPrefixBytes,
                input.streamCiphertextPrefixSha256,
                input.committedEnd,
                input.checkpointRelativeName,
                input.checkpointBytes,
                input.checkpointSha256,
                input.checkpointEnvelopeRelativeName,
                input.checkpointEnvelopeBytes,
                input.checkpointEnvelopeSha256,
                input.streamRelativeName,
                input.streamEnvelopeRelativeName,
                input.streamEnvelopeBytes,
                input.streamEnvelopeSha256,
                input.previousCheckpointSha256,
                RecoveryStreamingIdentity.checkpoint(input),
            )
        return TinkFixture(
            provider,
            row,
            checkpointEnvelope,
            checkpointCiphertext,
            streamEnvelope,
            streamCiphertext,
            plaintext,
            controllerFixtureFor(row, plaintext),
        )
    }

    private fun controllerFixtureFor(
        row: RecoveryStreamingCheckpointRow,
        plaintext: ByteArray,
    ) =
        com.monumentogram.dora.poc.recovery.contract
            .RecoveryStreamingWitnessInput(
                row.runId,
                row.generation,
                row.checkpointIdentity,
                row.streamCiphertextPrefixBytes,
                row.committedEnd,
                RecoveryStreamingIdentity.oracle(
                    plaintext.size.toULong(),
                    Sha256Value.calculate(plaintext),
                    row.runId,
                ),
                plaintext.size.toULong(),
                Sha256Value.calculate(plaintext),
                row.streamCiphertextPrefixBytes,
                row.streamCiphertextPrefixSha256,
                null,
            )
            .let {
                it.copy(controllerSnapshotSha256 = RecoveryStreamingIdentity.controllerSnapshot(it))
            }

    private data class TinkFixture(
        val runProvider: RecoveryRunAeadProvider,
        val row: RecoveryStreamingCheckpointRow,
        val checkpointEnvelope: ByteArray,
        val checkpointCiphertext: ByteArray,
        val streamEnvelope: ByteArray,
        val streamCiphertext: ByteArray,
        val plaintext: ByteArray,
        val witness: com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingWitnessInput,
    )
}
