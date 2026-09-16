package com.monumentogram.dora.poc.recovery.storage

import com.monumentogram.dora.poc.recovery.candidate.AndroidRecoveryStreamingOrphanArtifacts
import com.monumentogram.dora.poc.recovery.candidate.QuarantineBootstrapBinding
import com.monumentogram.dora.poc.recovery.candidate.QuarantineIntentState
import com.monumentogram.dora.poc.recovery.candidate.QuarantinePathState
import com.monumentogram.dora.poc.recovery.candidate.QuarantineResult
import com.monumentogram.dora.poc.recovery.candidate.QuarantineStep
import com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureCategory
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineController
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineEvidenceSink
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineIntentRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineJournal
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineTransaction
import com.monumentogram.dora.poc.recovery.candidate.RecoveryStreamingOrphanAccessException
import com.monumentogram.dora.poc.recovery.candidate.RecoveryStreamingOrphanFailure
import com.monumentogram.dora.poc.recovery.candidate.RecoveryStreamingReconciliationResult
import com.monumentogram.dora.poc.recovery.candidate.RecoveryStreamingResultClassification
import com.monumentogram.dora.poc.recovery.candidate.RecoveryStreamingResultStage
import com.monumentogram.dora.poc.recovery.candidate.RecoveryStreamingSafeExceptionType
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidOsRecoveryReconciliationStorageTest {
    @Test
    fun `PAR01 oversized manifest has a distinct upper bound failure before any read`() {
        for (inventory in listOf(false, true)) {
            val os = oversizedManifest()
            val storage = AndroidOsRecoveryReconciliationStorage(ROOT, os)
            val failure =
                assertThrows(RecoveryArtifactAccessException::class.java) {
                    if (inventory) storage.listActiveInventory(RUN)
                    else storage.loadActiveArtifact(RUN, MANIFEST_NAME, 1_048_576L)
                }
            assertTrue(failure.structural)
            assertEquals("RecoveryArtifactSizeLimitException", failure.cause?.javaClass?.simpleName)
            assertEquals(0, os.actualReadCalls)
            assertEquals(1, os.closeCalls)
        }
    }

    @Test
    fun `PAR01 oversized inventory keeps its typed failure when descriptor close fails`() {
        val os = oversizedManifest().apply { fail = "close" }
        val failure =
            assertThrows(RecoveryArtifactAccessException::class.java) {
                AndroidOsRecoveryReconciliationStorage(ROOT, os).listActiveInventory(RUN)
            }
        assertEquals("RecoveryArtifactSizeLimitException", failure.cause?.javaClass?.simpleName)
        assertEquals("close", failure.suppressed.single().message)
        assertEquals(0, os.actualReadCalls)
        assertEquals(1, os.closeCalls)
    }

    @Test
    fun `PAR01 exact manifest bound remains readable and one byte above fails before reading`() {
        for (size in listOf(262_144, 262_145)) {
            val os = oversizedManifest()
            val path = File(fixedDirectories()[4], MANIFEST_NAME).path
            os.stats[path] = RecoveryReconciliationStat(BootstrapPathType.REGULAR, size.toLong())
            os.bytes[path] = ByteArray(size) { 7 }
            val storage = AndroidOsRecoveryReconciliationStorage(ROOT, os)
            if (size == 262_144) {
                val artifact =
                    requireNotNull(storage.loadActiveArtifact(RUN, MANIFEST_NAME, 1_048_576L))
                assertEquals(size.toLong(), artifact.size)
                assertEquals(Sha256Value.calculate(os.bytes.getValue(path)), artifact.sha256)
                assertEquals(2, os.actualReadCalls)
            } else {
                val failure =
                    assertThrows(RecoveryArtifactAccessException::class.java) {
                            storage.loadActiveArtifact(RUN, MANIFEST_NAME, 1_048_576L)
                        }
                        .cause as RecoveryArtifactSizeLimitException
                assertEquals(MANIFEST_NAME, failure.relativeName)
                assertEquals(262_145L, failure.observedBytes)
                assertEquals(262_144L, failure.maximumBytes)
                assertEquals(0, os.actualReadCalls)
            }
            assertEquals(1, os.closeCalls)
        }
    }

    @Test
    fun `PAR01 STREAM checkpoint ciphertext of oversized plaintext retains its separate read cap`() {
        val name = "checkpoints/g-00000000000000000003.ct"
        val os =
            FakeOs().apply {
                seed()
                stats[File(fixedDirectories()[4], "checkpoints").path] =
                    RecoveryReconciliationStat(BootstrapPathType.DIRECTORY)
                val path = File(fixedDirectories()[4], name).path
                bytes[path] = ByteArray(524_322) { 9 }
                stats[path] = RecoveryReconciliationStat(BootstrapPathType.REGULAR, 524_322L)
            }
        val artifact =
            requireNotNull(
                AndroidOsRecoveryReconciliationStorage(ROOT, os)
                    .loadActiveArtifact(RUN, name, 16_777_216L)
            )
        assertEquals(524_322L, artifact.size)
        assertEquals(Sha256Value.calculate(ByteArray(524_322) { 9 }), artifact.sha256)
        assertEquals(2, os.actualReadCalls)
        assertEquals(1, os.closeCalls)
    }

    private fun oversizedManifest(): FakeOs =
        FakeOs().apply {
            seed()
            val runRoot = fixedDirectories()[4]
            val directory = File(runRoot, "manifests").path
            directoryChildren[runRoot] = mutableListOf("manifests")
            directoryChildren[directory] = mutableListOf(MANIFEST_NAME.substringAfter('/'))
            stats[directory] = RecoveryReconciliationStat(BootstrapPathType.DIRECTORY)
            stats[File(runRoot, MANIFEST_NAME).path] =
                RecoveryReconciliationStat(BootstrapPathType.REGULAR, 524_322L)
        }

    @Test
    fun `SPL01 actual storage classifies zero short and long completed destinations as structural`() {
        val actualFailures = mutableListOf<RecoveryStreamingOrphanFailure>()
        for (size in listOf(0, 2, 4)) {
            val (os, row) = orphanDestination(ByteArray(size))
            val failure =
                assertThrows(RecoveryStreamingOrphanAccessException::class.java) {
                    orphanLoad(os, row)
                }
            actualFailures += failure.failure
            assertEquals(1, os.closeCalls)
            assertEquals(0, os.actualReadCalls)
            assertTrue(os.lists.isEmpty())
        }
        assertEquals(List(3) { RecoveryStreamingOrphanFailure.ARTIFACT_STRUCTURAL }, actualFailures)
        val result = actualFailures.first().result() as RecoveryStreamingReconciliationResult.Fatal
        assertEquals(RecoveryStreamingResultStage.PREREQUISITE, result.stage)
        assertEquals(
            RecoveryStreamingResultClassification.STREAM_CHECKPOINT_STRUCTURAL,
            result.classification,
        )
    }

    @Test
    fun `SPL01 actual fstat leaf change is unsafe before content read`() {
        for (type in
            listOf(
                BootstrapPathType.DIRECTORY,
                BootstrapPathType.SYMLINK,
                BootstrapPathType.OTHER,
            )) {
            val (os, row) = orphanDestination(byteArrayOf(1, 2, 3))
            os.fstatType = type
            val failure =
                assertThrows(RecoveryStreamingOrphanAccessException::class.java) {
                    orphanLoad(os, row)
                }
            assertEquals(RecoveryStreamingOrphanFailure.UNSAFE_PATH, failure.failure)
            assertEquals(1, os.closeCalls)
            assertEquals(0, os.actualReadCalls)
            assertTrue(os.lists.isEmpty())
        }
    }

    @Test
    fun `SPL01 actual framework open fstat read and close exceptions remain operational`() {
        for (operation in listOf("open", "fstat", "read", "close")) {
            val (os, row) = orphanDestination(byteArrayOf(1, 2, 3))
            os.fail = operation
            val failure =
                assertThrows(RecoveryStreamingOrphanAccessException::class.java) {
                    orphanLoad(os, row)
                }
            assertEquals(
                operation,
                RecoveryStreamingOrphanFailure.ARTIFACT_OPERATIONAL,
                failure.failure,
            )
            val result = failure.failure.result() as RecoveryStreamingReconciliationResult.Retry
            assertEquals(
                RecoveryStreamingResultClassification.ARTIFACT_IO_BEFORE_EXACT_SOURCE_HASH,
                result.classification,
            )
            assertEquals(RecoveryStreamingSafeExceptionType.IO, result.safeExceptionType)
            assertEquals(if (operation == "open") 0 else 1, os.closeCalls)
        }
    }

    @Test
    fun `shared MICROFILE exact reads type size conflicts and preserve primary over close`() {
        for (size in listOf(0, 2, 4)) {
            val os =
                FakeOs().apply {
                    seed(source = ByteArray(size))
                    fail = "close"
                }
            val failure =
                assertThrows(RecoveryArtifactAccessException::class.java) {
                    AndroidOsRecoveryReconciliationStorage(ROOT, os).inspect(row())
                }
            assertTrue(failure.structural)
            assertEquals("close", failure.suppressed.single().message)
            assertEquals(1, os.closeCalls)
            assertEquals(0, os.actualReadCalls)
        }
    }

    @Test
    fun `shared MICROFILE Q02 retains unsafe leaf and operational framework classifications`() {
        val row = row().copy(state = QuarantineIntentState.COMPLETED)
        val unsafe =
            FakeOs().apply {
                seed(destination = byteArrayOf(1, 2, 3))
                fstatType = BootstrapPathType.OTHER
            }
        val result = quarantineExisting(unsafe, row) as QuarantineResult.UnsafePath
        assertEquals(QuarantineStep.Q02, result.failedStep)
        assertEquals(RecoveryFailureCategory.CORRUPT_LEAF, result.diagnostic.category)
        assertEquals(1, unsafe.closeCalls)
        assertEquals(0, unsafe.actualReadCalls)
        for (operation in listOf("open", "fstat", "read", "close")) {
            val os =
                FakeOs().apply {
                    seed(destination = byteArrayOf(1, 2, 3))
                    fail = operation
                }
            val failed = quarantineExisting(os, row) as QuarantineResult.RetryRequired
            assertEquals(QuarantineStep.Q02, failed.failedStep)
            assertEquals(RecoveryFailureCategory.OPERATIONAL, failed.diagnostic?.category)
            assertEquals(if (operation == "open") 0 else 1, os.closeCalls)
        }
    }

    private fun quarantineExisting(os: FakeOs, row: RecoveryQuarantineIntentRow): QuarantineResult {
        val journal =
            object : RecoveryQuarantineJournal {
                override fun load(intentId: Sha256Value) = row

                override fun loadBySource(input: RecoveryQuarantineIntentInput) = row

                override fun beginNonExclusive(): RecoveryQuarantineTransaction =
                    error("No new transaction")
            }
        return RecoveryQuarantineController(
                AndroidOsRecoveryReconciliationStorage(ROOT, os),
                journal,
                RecoveryQuarantineEvidenceSink { error("No completion evidence") },
            )
            .quarantine(row.input, row.recordedObservedState, row.bootstrapBinding)
    }

    private fun orphanDestination(bytes: ByteArray): Pair<FakeOs, RecoveryQuarantineIntentRow> {
        val input =
            RecoveryQuarantineIntentInput(
                RecoveryCandidate.STREAM,
                RUN,
                "checkpoints/g-00000000000000000001.ct",
                RecoveryQuarantineArtifactRole.CHECKPOINT_CIPHERTEXT,
                3UL,
                Sha256Value.calculate(byteArrayOf(1, 2, 3)),
            )
        val row =
            RecoveryQuarantineIntentRow(
                RecoveryQuarantineIntent.calculate(input),
                input,
                RecoveryQuarantineObservedState.FINAL_ORPHAN,
                QuarantineBootstrapBinding.PRESENT,
                RecoveryQuarantineIntent.destination(input),
                QuarantineIntentState.COMPLETED,
            )
        val os =
            FakeOs().apply {
                seed()
                stats[File(fixedDirectories()[4], "checkpoints").path] =
                    RecoveryReconciliationStat(BootstrapPathType.DIRECTORY)
                addQuarantine(row.destinationRelativeName.removePrefix("objects/"), bytes)
            }
        return os to row
    }

    private fun orphanLoad(os: FakeOs, row: RecoveryQuarantineIntentRow) {
        val storage = AndroidOsRecoveryReconciliationStorage(ROOT, os)
        AndroidRecoveryStreamingOrphanArtifacts(
                storage::loadActiveArtifact,
                { _, _ -> row },
                storage::inspect,
                storage::loadQuarantinedCheckpoint,
            )
            .load(RUN, row.input.sourceRelativeName, row.input.artifactRole)
    }

    @Test
    fun `SPL01 exact checkpoint destination is byte bound and never inventoried`() {
        val expected = byteArrayOf(1, 2, 3)
        val input =
            RecoveryQuarantineIntentInput(
                RecoveryCandidate.STREAM,
                RUN,
                "checkpoints/g-00000000000000000001.ct",
                RecoveryQuarantineArtifactRole.CHECKPOINT_CIPHERTEXT,
                3UL,
                Sha256Value.calculate(expected),
            )
        val row =
            RecoveryQuarantineIntentRow(
                RecoveryQuarantineIntent.calculate(input),
                input,
                RecoveryQuarantineObservedState.FINAL_ORPHAN,
                QuarantineBootstrapBinding.PRESENT,
                RecoveryQuarantineIntent.destination(input),
                QuarantineIntentState.COMPLETED,
            )
        val os =
            FakeOs().apply {
                seed()
                stats[File(fixedDirectories()[4], "checkpoints").path] =
                    RecoveryReconciliationStat(BootstrapPathType.DIRECTORY)
                addQuarantine(row.destinationRelativeName.removePrefix("objects/"), expected)
            }
        val storage = AndroidOsRecoveryReconciliationStorage(ROOT, os)
        val artifact = storage.loadQuarantinedCheckpoint(row)
        assertEquals(input.sourceRelativeName, artifact.relativeName)
        assertEquals(input.sourceSha256, artifact.sha256)
        assertEquals(3L, artifact.size)
        assertEquals(1, os.closeCalls)
        assertTrue(os.lists.isEmpty())
        os.bytes[
                File(fixedDirectories()[7], row.destinationRelativeName.removePrefix("objects/"))
                    .path] = byteArrayOf(3, 2, 1)
        val changed =
            assertThrows(RecoveryArtifactAccessException::class.java) {
                storage.loadQuarantinedCheckpoint(row)
            }
        assertTrue(changed.structural)
        assertEquals(2, os.closeCalls)
    }

    @Test
    fun `SPL01 destination loader rejects wrong role identity path and unsafe ancestor`() {
        val os = FakeOs().apply { seed() }
        val storage = AndroidOsRecoveryReconciliationStorage(ROOT, os)
        assertThrows(IllegalArgumentException::class.java) {
            storage.loadQuarantinedCheckpoint(row())
        }
        val input =
            RecoveryQuarantineIntentInput(
                RecoveryCandidate.STREAM,
                RUN,
                "checkpoints/g-00000000000000000001.ct",
                RecoveryQuarantineArtifactRole.CHECKPOINT_CIPHERTEXT,
                3UL,
                Sha256Value.calculate(byteArrayOf(1, 2, 3)),
            )
        val row =
            RecoveryQuarantineIntentRow(
                RecoveryQuarantineIntent.calculate(input),
                input,
                RecoveryQuarantineObservedState.FINAL_ORPHAN,
                QuarantineBootstrapBinding.PRESENT,
                RecoveryQuarantineIntent.destination(input),
                QuarantineIntentState.COMPLETED,
            )
        assertThrows(IllegalArgumentException::class.java) {
            storage.loadQuarantinedCheckpoint(
                row.copy(destinationRelativeName = "objects/../escape")
            )
        }
        assertTrue(os.lstats.isEmpty())
        os.stats[fixedDirectories()[6]] = RecoveryReconciliationStat(BootstrapPathType.SYMLINK)
        assertThrows(RecoveryUnsafePathException::class.java) {
            storage.loadQuarantinedCheckpoint(row)
        }
        assertEquals(0, os.closeCalls)
        assertTrue(os.lists.isEmpty())
    }

    @Test
    fun `optional quarantine namespace absence is empty and unsafe ancestors are rejected`() {
        val absent =
            FakeOs().apply {
                seed()
                fixedDirectories().drop(5).forEach(stats::remove)
            }
        assertTrue(
            AndroidOsRecoveryReconciliationStorage(ROOT, absent)
                .listQuarantineInventory(RUN)
                .isEmpty()
        )
        fixedDirectories().drop(5).forEach { unsafe ->
            val os =
                FakeOs().apply {
                    seed()
                    stats[unsafe] = RecoveryReconciliationStat(BootstrapPathType.SYMLINK)
                }
            assertThrows(RecoveryUnsafePathException::class.java) {
                AndroidOsRecoveryReconciliationStorage(ROOT, os).listQuarantineInventory(RUN)
            }
        }
    }

    @Test
    fun `unsafe inventory child name is a typed unsafe path`() {
        val os =
            FakeOs().apply {
                seed()
                directoryChildren[fixedDirectories().last()] = mutableListOf("../escape")
            }
        assertThrows(RecoveryUnsafePathException::class.java) {
            AndroidOsRecoveryReconciliationStorage(ROOT, os).listQuarantineInventory(RUN)
        }
    }

    @Test
    fun `actual inspect rejects lexical source and destination before artifact syscall`() {
        val os = FakeOs().apply { seed(source = byteArrayOf(1)) }
        val storage = AndroidOsRecoveryReconciliationStorage(ROOT, os)
        val unsafeSource =
            row(byteArrayOf(1)).let { original ->
                val input = original.input.copy(sourceRelativeName = "../bad")
                original.copy(
                    intentId = RecoveryQuarantineIntent.calculate(input),
                    input = input,
                    destinationRelativeName = RecoveryQuarantineIntent.destination(input),
                )
            }
        assertEquals(
            com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureCategory.UNSAFE_PARENT,
            assertThrows(RecoveryUnsafePathException::class.java) { storage.inspect(unsafeSource) }
                .category,
        )
        assertTrue(os.lstats.none { it.contains("..") })

        val unsafeDestination = row(byteArrayOf(1)).copy(destinationRelativeName = "objects/bad")
        assertEquals(
            com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureCategory.UNSAFE_PARENT,
            assertThrows(RecoveryUnsafePathException::class.java) {
                    storage.inspect(unsafeDestination)
                }
                .category,
        )
        assertTrue(os.lstats.none { it.endsWith("objects${File.separator}bad") })
    }

    @Test
    fun `actual inspect validates every fixed ancestor and rejects each symlink`() {
        fixedDirectories().forEach { unsafe ->
            val os = FakeOs().apply { seed(unsafe = unsafe) }
            val storage = AndroidOsRecoveryReconciliationStorage(ROOT, os)
            assertThrows(IllegalStateException::class.java) { storage.inspect(row()) }
            assertTrue(os.lstats.contains(unsafe))
        }
    }

    @Test
    fun `actual inspect uses bounded raw POSIX reads and exactly once close`() {
        val expected = byteArrayOf(1, 2, 3)
        val cases =
            listOf("premature" to listOf(0), "negative" to listOf(-1), "growth" to listOf(3, 1))
        cases.forEach { (name, reads) ->
            val os =
                FakeOs().apply {
                    seed(source = expected)
                    scriptedReads = reads
                }
            assertThrows(name, IllegalStateException::class.java) {
                AndroidOsRecoveryReconciliationStorage(ROOT, os).inspect(row(expected))
            }
            assertEquals(1, os.closeCalls)
        }
        val clean = FakeOs().apply { seed(source = expected) }
        val observation = AndroidOsRecoveryReconciliationStorage(ROOT, clean).inspect(row(expected))
        assertEquals(QuarantinePathState.EXACT, observation.source)
        assertEquals(QuarantinePathState.ABSENT, observation.destination)
        assertEquals(1, clean.closeCalls)
    }

    @Test
    @Suppress("LongMethod")
    fun `actual adapter closes after fstat read and fsync failures and retains primary`() {
        listOf("fstat", "read").forEach { fault ->
            val os =
                FakeOs().apply {
                    seed(source = byteArrayOf(1))
                    fail = fault
                }
            assertThrows(IllegalStateException::class.java) {
                AndroidOsRecoveryReconciliationStorage(ROOT, os).inspect(row(byteArrayOf(1)))
            }
            assertEquals(1, os.closeCalls)
        }
        val open =
            FakeOs().apply {
                seed(source = byteArrayOf(1))
                fail = "open"
            }
        assertThrows(IllegalStateException::class.java) {
            AndroidOsRecoveryReconciliationStorage(ROOT, open).inspect(row(byteArrayOf(1)))
        }
        assertEquals(0, open.closeCalls)

        val both =
            FakeOs().apply {
                seed(source = byteArrayOf(1))
                fail = "read+close"
            }
        val failure =
            assertThrows(IllegalStateException::class.java) {
                AndroidOsRecoveryReconciliationStorage(ROOT, both).inspect(row(byteArrayOf(1)))
            }
        assertEquals("read", failure.message)
        assertEquals("close", failure.suppressed.single().message)
        assertEquals(1, both.closeCalls)

        val closeOnly =
            FakeOs().apply {
                seed(source = byteArrayOf(1))
                fail = "close"
            }
        assertEquals(
            "close",
            assertThrows(IllegalStateException::class.java) {
                    AndroidOsRecoveryReconciliationStorage(ROOT, closeOnly)
                        .inspect(row(byteArrayOf(1)))
                }
                .message,
        )
        assertEquals(1, closeOnly.closeCalls)

        val oversized =
            FakeOs().apply {
                seed(source = byteArrayOf(1))
                stats[sourcePath()] =
                    RecoveryReconciliationStat(BootstrapPathType.REGULAR, 960_257L)
            }
        assertThrows(IllegalStateException::class.java) {
            AndroidOsRecoveryReconciliationStorage(ROOT, oversized).inspect(row(byteArrayOf(1)))
        }
        assertEquals(1, oversized.closeCalls)

        val sync =
            FakeOs().apply {
                seed(source = byteArrayOf(1), destination = byteArrayOf(1))
                fail = "fsync"
            }
        assertThrows(IllegalStateException::class.java) {
            AndroidOsRecoveryReconciliationStorage(ROOT, sync)
                .fsyncDestinationParent(row(byteArrayOf(1)))
        }
        assertEquals(1, sync.closeCalls)
    }

    @Test
    fun `actual inventory is bounded and quarantine walk is never recursive`() {
        val os =
            FakeOs().apply {
                seed(source = byteArrayOf(7))
                addQuarantine("q-${"1".repeat(64)}.bin", byteArrayOf(9))
                addQuarantine("nested", null, BootstrapPathType.DIRECTORY)
            }
        val storage = AndroidOsRecoveryReconciliationStorage(ROOT, os)
        val quarantine = storage.listQuarantineInventory(RUN)
        assertEquals(2, quarantine.size)
        assertTrue(
            quarantine.any { it.pathType == BootstrapPathType.DIRECTORY && it.artifact == null }
        )
        assertTrue(os.lists.none { it.endsWith("/nested") || it.endsWith("\\nested") })
    }

    private class Descriptor(val path: String) : RecoveryReconciliationDescriptor

    private class FakeOs : RecoveryReconciliationOs {
        val stats = mutableMapOf<String, RecoveryReconciliationStat>()
        val bytes = mutableMapOf<String, ByteArray>()
        val directoryChildren = mutableMapOf<String, MutableList<String>>()
        val offsets = mutableMapOf<String, Int>()
        val lstats = mutableListOf<String>()
        val lists = mutableListOf<String>()
        var scriptedReads: List<Int>? = null
        var readCall = 0
        var fail: String? = null
        var closeCalls = 0
        var actualReadCalls = 0
        var fstatType: BootstrapPathType? = null

        fun seed(
            unsafe: String? = null,
            source: ByteArray? = null,
            destination: ByteArray? = null,
        ) {
            fixedDirectories().forEach {
                stats[it] = RecoveryReconciliationStat(BootstrapPathType.DIRECTORY)
            }
            stats[File(fixedDirectories()[4], "units").path] =
                RecoveryReconciliationStat(BootstrapPathType.DIRECTORY)
            unsafe?.let { stats[it] = RecoveryReconciliationStat(BootstrapPathType.SYMLINK) }
            source?.let { put(sourcePath(), it) }
            destination?.let { put(destinationPath(), it) }
        }

        fun addQuarantine(
            name: String,
            value: ByteArray?,
            type: BootstrapPathType = BootstrapPathType.REGULAR,
        ) {
            val objects = fixedDirectories().last()
            directoryChildren.getOrPut(objects) { mutableListOf() } += name
            val path = File(objects, name).path
            stats[path] = RecoveryReconciliationStat(type, value?.size?.toLong() ?: 0)
            value?.let { bytes[path] = it }
        }

        private fun put(path: String, value: ByteArray) {
            stats[path] = RecoveryReconciliationStat(BootstrapPathType.REGULAR, value.size.toLong())
            bytes[path] = value
        }

        override fun lstat(path: String): RecoveryReconciliationStat? {
            lstats += path
            return stats[path]
        }

        override fun list(path: String): List<String> {
            lists += path
            return directoryChildren[path]?.toList() ?: emptyList()
        }

        override fun mkdir(path: String, mode: Int) {
            stats[path] = RecoveryReconciliationStat(BootstrapPathType.DIRECTORY)
        }

        override fun open(path: String, flags: Int): RecoveryReconciliationDescriptor {
            if (fail == "open") error("open")
            offsets[path] = 0
            return Descriptor(path)
        }

        override fun fstat(
            descriptor: RecoveryReconciliationDescriptor
        ): RecoveryReconciliationStat {
            if (fail == "fstat") error("fstat")
            return requireNotNull(stats[(descriptor as Descriptor).path]).let {
                it.copy(type = fstatType ?: it.type)
            }
        }

        @Suppress("ReturnCount")
        override fun read(
            descriptor: RecoveryReconciliationDescriptor,
            buffer: ByteArray,
            offset: Int,
            count: Int,
        ): Int {
            actualReadCalls++
            if (fail == "read" || fail == "read+close") error("read")
            scriptedReads?.getOrNull(readCall++)?.let { scripted ->
                if (scripted > 0) repeat(scripted.coerceAtMost(count)) { buffer[offset + it] = 1 }
                return scripted
            }
            val path = (descriptor as Descriptor).path
            val value = bytes[path] ?: ByteArray(0)
            val position = offsets[path] ?: 0
            if (position == value.size) return 0
            val actual = minOf(count, value.size - position)
            value.copyInto(buffer, offset, position, position + actual)
            offsets[path] = position + actual
            return actual
        }

        override fun rename(source: String, destination: String) = Unit

        override fun fsync(descriptor: RecoveryReconciliationDescriptor) {
            if (fail == "fsync") error("fsync")
        }

        override fun close(descriptor: RecoveryReconciliationDescriptor) {
            closeCalls++
            if (fail == "close" || fail == "read+close") error("close")
        }
    }

    private companion object {
        const val MANIFEST_NAME = "manifests/g-00000000000000000003.ct"
        val ROOT = File("root").absoluteFile
        val RUN = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")

        fun row(value: ByteArray = byteArrayOf(1, 2, 3)): RecoveryQuarantineIntentRow {
            val input =
                RecoveryQuarantineIntentInput(
                    RecoveryCandidate.MICROFILE,
                    RUN,
                    "units/u-0.bin",
                    RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT,
                    value.size.toULong(),
                    Sha256Value.calculate(value),
                )
            return RecoveryQuarantineIntentRow(
                RecoveryQuarantineIntent.calculate(input),
                input,
                RecoveryQuarantineObservedState.FINAL_ORPHAN,
                QuarantineBootstrapBinding.PRESENT,
                RecoveryQuarantineIntent.destination(input),
                QuarantineIntentState.PENDING,
            )
        }

        fun fixedDirectories(): List<String> {
            val base = File(ROOT, "poc-recovery")
            val v1 = File(base, "v1")
            return listOf(
                ROOT.path,
                base.path,
                v1.path,
                File(v1, "runs").path,
                File(v1, "runs/${RUN.toCanonicalString()}").path,
                File(v1, "quarantine").path,
                File(v1, "quarantine/${RUN.toCanonicalString()}").path,
                File(v1, "quarantine/${RUN.toCanonicalString()}/objects").path,
            )
        }

        fun sourcePath() = File(fixedDirectories()[4], "units/u-0.bin").path

        fun destinationPath() =
            File(
                    fixedDirectories()[7],
                    "q-${RecoveryQuarantineIntent.calculate(row().input).toLowercaseHex()}.bin",
                )
                .path
    }
}
