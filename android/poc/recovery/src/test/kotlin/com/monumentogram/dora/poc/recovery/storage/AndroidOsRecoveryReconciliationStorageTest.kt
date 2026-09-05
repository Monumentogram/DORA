package com.monumentogram.dora.poc.recovery.storage

import com.monumentogram.dora.poc.recovery.candidate.QuarantineBootstrapBinding
import com.monumentogram.dora.poc.recovery.candidate.QuarantineIntentState
import com.monumentogram.dora.poc.recovery.candidate.QuarantinePathState
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineIntentRow
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
        assertThrows(RecoveryUnsafePathException::class.java) { storage.inspect(unsafeSource) }
        assertTrue(os.lstats.none { it.contains("..") })

        val unsafeDestination = row(byteArrayOf(1)).copy(destinationRelativeName = "objects/bad")
        assertThrows(RecoveryUnsafePathException::class.java) {
            storage.inspect(unsafeDestination)
        }
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
            return requireNotNull(stats[(descriptor as Descriptor).path])
        }

        @Suppress("ReturnCount")
        override fun read(
            descriptor: RecoveryReconciliationDescriptor,
            buffer: ByteArray,
            offset: Int,
            count: Int,
        ): Int {
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
