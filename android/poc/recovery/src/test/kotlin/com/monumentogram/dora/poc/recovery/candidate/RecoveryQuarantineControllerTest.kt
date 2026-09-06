package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryReconciliationStorage
import com.monumentogram.dora.poc.recovery.storage.BootstrapPathType
import com.monumentogram.dora.poc.recovery.storage.RecoveryReconciliationDescriptor
import com.monumentogram.dora.poc.recovery.storage.RecoveryReconciliationOs
import com.monumentogram.dora.poc.recovery.storage.RecoveryReconciliationStat
import com.monumentogram.dora.poc.recovery.storage.RecoveryUnsafePathException
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryQuarantineControllerTest {
    @Test
    fun `Q01 through Q05 completes in exact order and evidence follows commit`() {
        val fixture = Fixture()
        val result = fixture.run() as QuarantineResult.Completed
        assertEquals(
            listOf(
                "prepare",
                "begin",
                "insert",
                "mark",
                "end",
                "inspect",
                "rename",
                "inspect",
                "source-fsync",
                "destination-fsync",
                "begin",
                "complete",
                "mark",
                "end",
                "load",
                "evidence",
            ),
            fixture.events,
        )
        assertTrue(result.evidenceEmitted)
        assertEquals(QuarantineOperationState.CONFIRMED, result.remainder.completionCommit)
    }

    @Test
    fun `completed exact replay is filesystem no-op but retries logical evidence`() {
        val fixture = Fixture()
        fixture.run()
        fixture.events.clear()
        val replay = fixture.run() as QuarantineResult.Completed
        assertEquals(listOf("prepare", "load", "inspect", "evidence"), fixture.events)
        assertTrue(replay.evidenceEmitted)
    }

    @Test
    fun `both present collides even when destination identity is exact`() {
        val fixture = Fixture()
        fixture.storage.observation =
            QuarantinePathObservation(QuarantinePathState.EXACT, QuarantinePathState.EXACT)
        assertTrue(fixture.run() is QuarantineResult.Collision)
        assertFalse("rename" in fixture.events)
    }

    @Test
    fun `post completion evidence failure preserves completed state`() {
        val fixture = Fixture(evidenceFails = true)
        val result = fixture.run() as QuarantineResult.Completed
        assertFalse(result.evidenceEmitted)
        assertEquals(QuarantineIntentState.COMPLETED, fixture.journal.row?.state)
        assertEquals(QuarantineOperationState.CONFIRMED, result.remainder.completionCommit)
    }

    @Test
    fun `filesystem faults report exact step and conservative remainder`() {
        listOf(
                "rename" to QuarantineStep.Q02,
                "source-fsync" to QuarantineStep.Q03,
                "destination-fsync" to QuarantineStep.Q04,
            )
            .forEach { (fault, expectedStep) ->
                val fixture = Fixture(storageFault = fault)
                val result = fixture.run() as QuarantineResult.RetryRequired
                assertEquals(expectedStep, result.failedStep)
                if (fault == "rename") {
                    assertEquals(QuarantineOperationState.OUTCOME_UNKNOWN, result.remainder.rename)
                } else {
                    assertEquals(QuarantineOperationState.CONFIRMED, result.remainder.rename)
                }
                assertEquals(
                    QuarantineOperationState.NOT_ATTEMPTED,
                    result.remainder.completionCommit,
                )
            }
    }

    @Test
    fun `actual quarantine entry distinguishes unsafe paths from ordinary IO`() {
        val prepareUnsafe =
            Fixture(storageFault = "prepare-unsafe").run() as QuarantineResult.UnsafePath
        assertEquals(QuarantineStep.PREPARE, prepareUnsafe.failedStep)
        assertEquals(RecoveryFailureCategory.UNSAFE_PARENT, prepareUnsafe.diagnostic.category)
        assertEquals(RecoveryFailureStage.ARTIFACT_PATH, prepareUnsafe.diagnostic.stage)
        assertEquals(QuarantineRemainder(), prepareUnsafe.remainder)
        val inspectUnsafe =
            Fixture(storageFault = "inspect-unsafe").run() as QuarantineResult.UnsafePath
        assertEquals(QuarantineStep.Q02, inspectUnsafe.failedStep)
        assertEquals(RecoveryFailureCategory.UNSAFE_PARENT, inspectUnsafe.diagnostic.category)
        assertEquals(RecoveryFailureStage.ARTIFACT_PATH, inspectUnsafe.diagnostic.stage)
        assertEquals(
            QuarantineOperationState.CONFIRMED,
            inspectUnsafe.remainder.intentCommit,
        )
        listOf("prepare-io" to QuarantineStep.PREPARE, "inspect-io" to QuarantineStep.Q02)
            .forEach { (fault, step) ->
                val result = Fixture(storageFault = fault).run() as QuarantineResult.RetryRequired
                assertEquals(step, result.failedStep)
                assertEquals(RecoveryFailureCategory.OPERATIONAL, result.diagnostic?.category)
                assertEquals(RecoveryFailureStage.ARTIFACT_IO, result.diagnostic?.stage)
            }
    }

    @Test
    fun `typed rename and parent sync failures retain exact known remainder`() {
        val rename = Fixture(storageFault = "rename-unsafe").run() as QuarantineResult.UnsafePath
        assertEquals(QuarantineStep.Q02, rename.failedStep)
        assertEquals(QuarantineOperationState.OUTCOME_UNKNOWN, rename.remainder.rename)
        assertEquals(RecoveryFailureStage.ARTIFACT_PATH, rename.diagnostic.stage)

        listOf(
                "source-fsync-unsafe" to QuarantineStep.Q03,
                "destination-fsync-unsafe" to QuarantineStep.Q04,
            )
            .forEach { (fault, step) ->
                val result = Fixture(storageFault = fault).run() as QuarantineResult.UnsafePath
                assertEquals(step, result.failedStep)
                assertEquals(QuarantineOperationState.CONFIRMED, result.remainder.rename)
                assertEquals(RecoveryFailureCategory.UNSAFE_PARENT, result.diagnostic.category)
                assertEquals(RecoveryFailureStage.ARTIFACT_PATH, result.diagnostic.stage)
            }
    }

    @Test
    fun `all replay path states are fail closed except exact completed destination`() {
        val fixture = Fixture()
        fixture.run()
        listOf(
                QuarantinePathObservation(QuarantinePathState.EXACT, QuarantinePathState.ABSENT),
                QuarantinePathObservation(QuarantinePathState.ABSENT, QuarantinePathState.ABSENT),
            )
            .forEach { observation ->
                fixture.storage.observation = observation
                assertTrue(fixture.run() is QuarantineResult.RetryRequired)
            }
        fixture.storage.observation =
            QuarantinePathObservation(QuarantinePathState.UNSAFE, QuarantinePathState.ABSENT)
        assertTrue(fixture.run() is QuarantineResult.UnsafePath)
        fixture.storage.observation =
            QuarantinePathObservation(QuarantinePathState.OCCUPIED, QuarantinePathState.EXACT)
        assertTrue(fixture.run() is QuarantineResult.Collision)
    }

    @Test
    fun `persisted identity mismatch is rejected while new observation does not fork intent`() {
        val fixture = Fixture()
        fixture.journal.row =
            RecoveryQuarantineIntentRow(
                com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent.calculate(
                    fixture.input
                ),
                fixture.input.copy(artifactRole = RecoveryQuarantineArtifactRole.UNKNOWN_REGULAR),
                RecoveryQuarantineObservedState.FINAL_ORPHAN,
                QuarantineBootstrapBinding.PRESENT,
                com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent.destination(
                    fixture.input
                ),
                QuarantineIntentState.PENDING,
            )
        assertTrue(fixture.run() is QuarantineResult.RetryRequired)
    }

    @Test
    fun `Q01 and Q05 end exceptions resolve only through exact durable readback`() {
        for (ordinal in listOf(1, 2)) {
            val fixture = Fixture(transactionEndFault = ordinal)
            val result = fixture.run()
            assertTrue(result is QuarantineResult.Completed)
            assertEquals(QuarantineIntentState.COMPLETED, fixture.journal.row?.state)
            assertEquals(
                QuarantineOperationState.CONFIRMED,
                (result as QuarantineResult.Completed).remainder.completionCommit,
            )
        }
    }

    @Test
    fun `load and begin failures are immutable typed retry diagnostics`() {
        val load = Fixture(journalFault = "load").run() as QuarantineResult.RetryRequired
        assertEquals(QuarantineStep.Q01, load.failedStep)
        assertEquals(RecoveryFailureCategory.UNKNOWN_OUTCOME, load.diagnostic?.category)
        assertEquals(RecoveryFailureStage.JOURNAL, load.diagnostic?.stage)
        val begin = Fixture(journalFault = "begin").run() as QuarantineResult.RetryRequired
        assertEquals(QuarantineStep.Q01, begin.failedStep)
        assertEquals(RecoveryFailureCategory.OPERATIONAL, begin.diagnostic?.category)
    }

    @Test
    fun `one readback lookup failure recovers and both failures retain exact prior remainder`() {
        listOf(
                Fixture(transactionEndFault = 1, journalFault = "load-2") to QuarantineStep.Q01,
                Fixture(transactionEndFault = 2, journalFault = "load-2") to QuarantineStep.Q05,
                Fixture(journalFault = "load-2") to QuarantineStep.Q05,
            )
            .forEach { (fixture, _) ->
                assertTrue(fixture.run() is QuarantineResult.Completed)
                assertTrue(fixture.journal.exactLoadCalls > 0)
                assertEquals(fixture.journal.exactLoadCalls, fixture.journal.sourceLoadCalls)
            }
        val ambiguous =
            Fixture(journalFault = "both-load-2").run() as QuarantineResult.RetryRequired
        assertEquals(QuarantineStep.Q05, ambiguous.failedStep)
        assertEquals(RecoveryFailureStage.JOURNAL, ambiguous.diagnostic?.stage)
        assertEquals(QuarantineOperationState.CONFIRMED, ambiguous.remainder.completionCommit)
        val q01 =
            Fixture(transactionEndFault = 1, journalFault = "both-load-2").run()
                as QuarantineResult.RetryRequired
        assertEquals(QuarantineStep.Q01, q01.failedStep)
        assertEquals(QuarantineOperationState.OUTCOME_UNKNOWN, q01.remainder.intentCommit)
        assertEquals(QuarantineOperationState.NOT_ATTEMPTED, q01.remainder.completionCommit)
        val q05 =
            Fixture(transactionEndFault = 2, journalFault = "both-load-2").run()
                as QuarantineResult.RetryRequired
        assertEquals(QuarantineStep.Q05, q05.failedStep)
        assertEquals(QuarantineOperationState.OUTCOME_UNKNOWN, q05.remainder.completionCommit)
    }

    @Test
    fun `unique source race with different derived identity is retained and rejected`() {
        val fixture = Fixture(journalFault = "insert-race")
        val result = fixture.run() as QuarantineResult.RetryRequired
        assertEquals(QuarantineStep.Q01, result.failedStep)
        assertTrue(result.row != null)
        assertTrue(
            result.row!!.intentId !=
                com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent.calculate(
                    fixture.input
                )
        )
        assertFalse("rename" in fixture.events)
    }

    @Test
    fun `actual lexical and initial inspect failures retain typed diagnostics`() {
        val lexical = ActualStorageFixture(sourceName = "../bad")
        val lexicalResult = lexical.run() as QuarantineResult.UnsafePath
        assertEquals(QuarantineStep.Q02, lexicalResult.failedStep)
        assertEquals(RecoveryFailureCategory.UNSAFE_PARENT, lexicalResult.diagnostic?.category)
        assertEquals(RecoveryFailureStage.ARTIFACT_PATH, lexicalResult.diagnostic?.stage)
        assertEquals(QuarantineOperationState.CONFIRMED, lexicalResult.remainder.intentCommit)
        assertStopsAfterInitialInspect(lexical)

        val unsafe = ActualStorageFixture(initialFault = "unsafe-leaf")
        val unsafeResult = unsafe.run() as QuarantineResult.UnsafePath
        assertEquals(RecoveryFailureCategory.CORRUPT_LEAF, unsafeResult.diagnostic?.category)
        assertEquals(RecoveryFailureStage.ARTIFACT_PATH, unsafeResult.diagnostic?.stage)
        assertStopsAfterInitialInspect(unsafe)

        val io = ActualStorageFixture(initialFault = "lstat-io")
        val ioResult = io.run() as QuarantineResult.RetryRequired
        assertEquals(RecoveryFailureCategory.OPERATIONAL, ioResult.diagnostic?.category)
        assertEquals(RecoveryFailureStage.ARTIFACT_IO, ioResult.diagnostic?.stage)
        assertStopsAfterInitialInspect(io)
    }

    @Test
    fun `actual second inspect preserves confirmed rename for unsafe and IO transitions`() {
        val unsafe = ActualStorageFixture(afterRenameFault = "unsafe-parent")
        val unsafeResult = unsafe.run() as QuarantineResult.UnsafePath
        assertEquals(QuarantineStep.Q02, unsafeResult.failedStep)
        assertEquals(RecoveryFailureCategory.UNSAFE_PARENT, unsafeResult.diagnostic?.category)
        assertEquals(RecoveryFailureStage.ARTIFACT_PATH, unsafeResult.diagnostic?.stage)
        assertEquals(QuarantineOperationState.CONFIRMED, unsafeResult.remainder.rename)
        assertStopsAfterConfirmedRename(unsafe)

        val io = ActualStorageFixture(afterRenameFault = "lstat-io")
        val ioResult = io.run() as QuarantineResult.RetryRequired
        assertEquals(RecoveryFailureCategory.OPERATIONAL, ioResult.diagnostic?.category)
        assertEquals(RecoveryFailureStage.ARTIFACT_IO, ioResult.diagnostic?.stage)
        assertEquals(QuarantineOperationState.CONFIRMED, ioResult.remainder.rename)
        assertStopsAfterConfirmedRename(io)
    }

    @Test
    fun `persisted invalid destination is rejected before actual storage inspection`() {
        val fixture = ActualStorageFixture()
        fixture.journal.row =
            fixture.proposed().copy(destinationRelativeName = "objects/not-canonical")
        val result = fixture.run() as QuarantineResult.RetryRequired
        assertEquals(QuarantineStep.Q01, result.failedStep)
        assertEquals(RecoveryFailureCategory.STRUCTURAL, result.diagnostic?.category)
        assertEquals(RecoveryFailureStage.JOURNAL, result.diagnostic?.stage)
        assertFalse(fixture.os.events.any { it.contains("units/u-0000000000.ct.tmp") })
        assertFalse(fixture.os.events.any { it.startsWith("rename:") })
        assertFalse("evidence" in fixture.events)
    }

    private fun assertStopsAfterInitialInspect(fixture: ActualStorageFixture) {
        assertEquals(0, fixture.os.events.count { it.startsWith("rename:") })
        assertEquals(0, fixture.os.events.count { it == "fsync:${fixture.os.sourceParent}" })
        assertEquals(0, fixture.os.events.count { it == "fsync:${fixture.os.objectsRoot}" })
        assertEquals(1, fixture.events.count { it == "begin" })
        assertFalse("complete" in fixture.events)
        assertFalse("evidence" in fixture.events)
    }

    private fun assertStopsAfterConfirmedRename(fixture: ActualStorageFixture) {
        assertEquals(1, fixture.os.events.count { it.startsWith("rename:") })
        assertEquals(0, fixture.os.events.count { it == "fsync:${fixture.os.sourceParent}" })
        assertEquals(0, fixture.os.events.count { it == "fsync:${fixture.os.objectsRoot}" })
        assertEquals(1, fixture.events.count { it == "begin" })
        assertFalse("complete" in fixture.events)
        assertFalse("evidence" in fixture.events)
    }

    private class ActualStorageFixture(
        sourceName: String = "units/u-0000000000.ct.tmp",
        initialFault: String? = null,
        afterRenameFault: String? = null,
    ) {
        val events = mutableListOf<String>()
        val root = File("build/round4-quarantine-${System.nanoTime()}").absoluteFile
        val input =
            RecoveryQuarantineIntentInput(
                RecoveryCandidate.MICROFILE,
                RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff"),
                sourceName,
                RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT,
                1UL,
                Sha256Value.calculate(byteArrayOf(1)),
            )
        val os = ActualOs(root, input, initialFault, afterRenameFault)
        val journal = Journal(events)
        private val controller =
            RecoveryQuarantineController(
                AndroidOsRecoveryReconciliationStorage(root, os),
                journal,
            ) {
                events += "evidence"
            }

        fun proposed() =
            RecoveryQuarantineIntentRow(
                com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent.calculate(
                    input
                ),
                input,
                RecoveryQuarantineObservedState.TEMP_ONLY,
                QuarantineBootstrapBinding.PRESENT,
                com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent.destination(
                    input
                ),
                QuarantineIntentState.PENDING,
            )

        fun run() =
            controller.quarantine(
                input,
                RecoveryQuarantineObservedState.TEMP_ONLY,
                QuarantineBootstrapBinding.PRESENT,
            )
    }

    private class ActualDescriptor(val path: String) : RecoveryReconciliationDescriptor

    private class ActualOs(
        root: File,
        input: RecoveryQuarantineIntentInput,
        private val initialFault: String?,
        private val afterRenameFault: String?,
    ) : RecoveryReconciliationOs {
        val events = mutableListOf<String>()
        private val stats = mutableMapOf<String, RecoveryReconciliationStat>()
        private val content = mutableMapOf<String, ByteArray>()
        private val offsets = mutableMapOf<String, Int>()
        private val runRoot = File(root, "poc-recovery/v1/runs/${input.runId.toCanonicalString()}")
        private val source = File(runRoot, input.sourceRelativeName)
        private val quarantineRoot =
            File(root, "poc-recovery/v1/quarantine/${input.runId.toCanonicalString()}")
        val sourceParent: String = File(runRoot, "units").path
        val objectsRoot: String = File(quarantineRoot, "objects").path
        private val destination =
            File(
                quarantineRoot,
                com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent.destination(
                    input
                ),
            )
        private var renamed = false

        init {
            listOf(
                    root,
                    File(root, "poc-recovery"),
                    File(root, "poc-recovery/v1"),
                    File(root, "poc-recovery/v1/runs"),
                    runRoot,
                    File(runRoot, "units"),
                )
                .forEach {
                    stats[it.path] = RecoveryReconciliationStat(BootstrapPathType.DIRECTORY)
                }
            if (input.sourceRelativeName == "units/u-0000000000.ct.tmp") {
                stats[source.path] =
                    RecoveryReconciliationStat(
                        if (initialFault == "unsafe-leaf") BootstrapPathType.SYMLINK
                        else BootstrapPathType.REGULAR,
                        1,
                    )
                content[source.path] = byteArrayOf(1)
            }
        }

        override fun lstat(path: String): RecoveryReconciliationStat? {
            events += "lstat:$path"
            val initialIo = initialFault == "lstat-io" && path == source.path
            val postRenameIo =
                renamed && afterRenameFault == "lstat-io" && path == quarantineRoot.path
            if (initialIo || postRenameIo) error("lstat io")
            if (renamed && afterRenameFault == "unsafe-parent" && path == quarantineRoot.path) {
                return RecoveryReconciliationStat(BootstrapPathType.SYMLINK)
            }
            return stats[path]
        }

        override fun list(path: String) = emptyList<String>()

        override fun mkdir(path: String, mode: Int) {
            events += "mkdir:$path"
            stats[path] = RecoveryReconciliationStat(BootstrapPathType.DIRECTORY)
        }

        override fun open(path: String, flags: Int): RecoveryReconciliationDescriptor {
            events += "open:$path"
            offsets[path] = 0
            return ActualDescriptor(path)
        }

        override fun fstat(descriptor: RecoveryReconciliationDescriptor) =
            requireNotNull(stats[(descriptor as ActualDescriptor).path])

        override fun read(
            descriptor: RecoveryReconciliationDescriptor,
            buffer: ByteArray,
            offset: Int,
            count: Int,
        ): Int {
            val path = (descriptor as ActualDescriptor).path
            val bytes = content[path] ?: ByteArray(0)
            val position = offsets[path] ?: 0
            if (position == bytes.size) return 0
            val actual = minOf(count, bytes.size - position)
            bytes.copyInto(buffer, offset, position, position + actual)
            offsets[path] = position + actual
            return actual
        }

        override fun rename(source: String, destination: String) {
            events += "rename:$source->$destination"
            val bytes = requireNotNull(content.remove(source))
            content[destination] = bytes
            stats.remove(source)
            stats[destination] =
                RecoveryReconciliationStat(BootstrapPathType.REGULAR, bytes.size.toLong())
            renamed = true
        }

        override fun fsync(descriptor: RecoveryReconciliationDescriptor) {
            events += "fsync:${(descriptor as ActualDescriptor).path}"
        }

        override fun close(descriptor: RecoveryReconciliationDescriptor) = Unit
    }

    private class Fixture(
        evidenceFails: Boolean = false,
        storageFault: String? = null,
        transactionEndFault: Int? = null,
        journalFault: String? = null,
    ) {
        val events = mutableListOf<String>()
        val input =
            RecoveryQuarantineIntentInput(
                RecoveryCandidate.MICROFILE,
                RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff"),
                "units/u-0000000000.ct.tmp",
                RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT,
                1UL,
                Sha256Value.calculate(byteArrayOf(1)),
            )
        val storage = Storage(events, storageFault)
        val journal = Journal(events, transactionEndFault, journalFault)
        private val controller =
            RecoveryQuarantineController(storage, journal) {
                events += "evidence"
                if (evidenceFails) error("evidence")
            }

        fun run() =
            controller.quarantine(
                input,
                RecoveryQuarantineObservedState.TEMP_ONLY,
                QuarantineBootstrapBinding.PRESENT,
            )
    }

    private class Storage(
        private val events: MutableList<String>,
        private val fault: String? = null,
    ) : RecoveryQuarantineStorage {
        var observation =
            QuarantinePathObservation(QuarantinePathState.EXACT, QuarantinePathState.ABSENT)

        override fun prepare(runId: RunId) {
            events += "prepare"
            if (fault == "prepare-unsafe") throw RecoveryUnsafePathException("unsafe")
            if (fault == "prepare-io") error("prepare io")
        }

        override fun inspect(row: RecoveryQuarantineIntentRow): QuarantinePathObservation {
            events += "inspect"
            if (fault == "inspect-unsafe") throw RecoveryUnsafePathException("unsafe")
            if (fault == "inspect-io") error("inspect io")
            return observation
        }

        override fun renameNoOverwrite(row: RecoveryQuarantineIntentRow) {
            events += "rename"
            if (fault == "rename-unsafe") throw RecoveryUnsafePathException("unsafe")
            if (fault == "rename") error("rename")
            observation =
                QuarantinePathObservation(QuarantinePathState.ABSENT, QuarantinePathState.EXACT)
        }

        override fun fsyncSourceParent(row: RecoveryQuarantineIntentRow) {
            events += "source-fsync"
            if (fault == "source-fsync-unsafe") throw RecoveryUnsafePathException("unsafe")
            if (fault == "source-fsync") error("source-fsync")
        }

        override fun fsyncDestinationParent(row: RecoveryQuarantineIntentRow) {
            events += "destination-fsync"
            if (fault == "destination-fsync-unsafe") throw RecoveryUnsafePathException("unsafe")
            if (fault == "destination-fsync") error("destination-fsync")
        }
    }

    private class Journal(
        private val events: MutableList<String>,
        private val endFaultOrdinal: Int? = null,
        private val fault: String? = null,
    ) : RecoveryQuarantineJournal {
        var row: RecoveryQuarantineIntentRow? = null
        private var transactionOrdinal = 0
        var exactLoadCalls = 0
        var sourceLoadCalls = 0

        override fun load(intentId: Sha256Value): RecoveryQuarantineIntentRow? {
            exactLoadCalls++
            if (
                fault == "load" ||
                    fault == "load-$exactLoadCalls" ||
                    fault == "both-load-$exactLoadCalls"
            )
                error("load")
            if (row != null) events += "load"
            return row?.takeIf { it.intentId == intentId }
        }

        override fun loadBySource(
            input: RecoveryQuarantineIntentInput
        ): RecoveryQuarantineIntentRow? {
            sourceLoadCalls++
            if (fault == "both-load-$exactLoadCalls") error("load unique")
            return row?.takeIf {
                it.input.runId == input.runId &&
                    it.input.candidate == input.candidate &&
                    it.input.sourceRelativeName == input.sourceRelativeName &&
                    it.input.sourceSha256 == input.sourceSha256
            }
        }

        override fun beginNonExclusive(): RecoveryQuarantineTransaction {
            if (fault == "begin") error("begin")
            events += "begin"
            transactionOrdinal++
            val ordinal = transactionOrdinal
            return object : RecoveryQuarantineTransaction {
                private var inserted: RecoveryQuarantineIntentRow? = null
                private var complete = false

                override fun insert(row: RecoveryQuarantineIntentRow) {
                    events += "insert"
                    if (fault == "insert-race") {
                        val racedInput = row.input.copy(sourceBytes = row.input.sourceBytes + 1UL)
                        this@Journal.row =
                            row.copy(
                                intentId =
                                    com.monumentogram.dora.poc.recovery.contract
                                        .RecoveryQuarantineIntent
                                        .calculate(racedInput),
                                input = racedInput,
                                destinationRelativeName =
                                    com.monumentogram.dora.poc.recovery.contract
                                        .RecoveryQuarantineIntent
                                        .destination(racedInput),
                            )
                        error("unique source race")
                    }
                    inserted = row
                }

                override fun complete(intentId: Sha256Value) {
                    events += "complete"
                    complete = true
                }

                override fun markSuccessful() {
                    events += "mark"
                }

                override fun end() {
                    events += "end"
                    inserted?.let { row = it }
                    if (complete) row = row?.copy(state = QuarantineIntentState.COMPLETED)
                    if (endFaultOrdinal == ordinal) error("end outcome unknown")
                }
            }
        }
    }
}
