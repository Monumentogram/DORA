package com.monumentogram.dora.poc.recovery.journal

import android.database.Cursor
import com.monumentogram.dora.poc.recovery.candidate.AndroidRecoveryMicrofileReconciliation
import com.monumentogram.dora.poc.recovery.candidate.QuarantineBootstrapBinding
import com.monumentogram.dora.poc.recovery.candidate.QuarantineIntentState
import com.monumentogram.dora.poc.recovery.candidate.QuarantinePathState
import com.monumentogram.dora.poc.recovery.candidate.RecoveryCandidateSnapshot
import com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureCategory
import com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureStage
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofileUnitRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineEvidenceSink
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineIntentRow
import com.monumentogram.dora.poc.recovery.candidate.RecoverySourceAccessException
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.controller.StoredKeyConfirmationIdentity
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryReconciliationStorage
import com.monumentogram.dora.poc.recovery.storage.BootstrapPathType
import com.monumentogram.dora.poc.recovery.storage.RecoveryReconciliationDescriptor
import com.monumentogram.dora.poc.recovery.storage.RecoveryReconciliationOs
import com.monumentogram.dora.poc.recovery.storage.RecoveryReconciliationStat
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRecoveryReconciliationSourceTest {
    @Test
    fun `bootstrap cursor decoder returns null for zero rows`() {
        val probe = CursorProbe(emptyList())

        assertEquals(
            null,
            AndroidRecoveryReconciliationSource.decodeBootstrapIdentity(probe.cursor, RUN),
        )
        assertEquals(0, probe.getterPositions.size)
    }

    @Test
    fun `bootstrap cursor decoder decodes the one stored identity at position zero`() {
        val row = bootstrapRow()
        val probe = CursorProbe(listOf(row))

        val identity =
            AndroidRecoveryReconciliationSource.decodeBootstrapIdentity(probe.cursor, RUN)

        assertEquals(expectedBootstrapIdentity(row), identity)
        assertEquals(listOf(0, 0, 0, 0, 0), probe.getterPositions)
        assertEquals(0, probe.advanceCount)
    }

    @Test
    fun `bootstrap cursor decoder rejects two rows as a structural journal failure`() {
        val probe = CursorProbe(listOf(bootstrapRow(), bootstrapRow()))

        val failure = assertThrowsSource {
            AndroidRecoveryReconciliationSource.decodeBootstrapIdentity(probe.cursor, RUN)
        }

        assertEquals(RecoveryFailureCategory.STRUCTURAL, failure.diagnostic.category)
        assertEquals(RecoveryFailureStage.JOURNAL, failure.diagnostic.stage)
        assertEquals(0, probe.getterPositions.size)
    }

    @Test
    fun `actual source treats never-created quarantine namespace as empty`() {
        val os =
            InventoryOs().apply {
                seed(emptyMap(), emptyMap())
                removeQuarantineNamespace()
            }
        val inventory =
            source(os)
                .loadInventorySnapshot(
                    RUN,
                    RecoveryCandidateSnapshot(emptyList(), emptyList(), emptyList()),
                )
        assertTrue(inventory.quarantine.isEmpty())
    }

    @Test
    @Suppress("LongMethod")
    fun `actual source reports active taxonomy and pending completed and unreferenced quarantine`() {
        val os = InventoryOs()
        val pending = intent("units/u-0.bin", byteArrayOf(1), QuarantineIntentState.PENDING)
        val completed = intent("units/u-1.bin", byteArrayOf(2), QuarantineIntentState.COMPLETED)
        os.seed(
            mapOf(
                "units/u-0.bin.tmp" to byteArrayOf(3),
                "units/u-0.bin" to byteArrayOf(4),
                "units/u-1.bin.tmp" to byteArrayOf(7),
                "units/u-2.bin" to byteArrayOf(8),
                "unknown.bin" to byteArrayOf(5),
            ),
            mapOf(
                pending.destinationRelativeName to byteArrayOf(1),
                completed.destinationRelativeName to byteArrayOf(2),
                "objects/q-${"f".repeat(64)}.bin" to byteArrayOf(6),
            ),
        )
        os.addUnsafeQuarantine("objects/nested")
        val source =
            AndroidRecoveryReconciliationSource(
                loadBootstrap = { null },
                loadSnapshot = {
                    RecoveryCandidateSnapshot(emptyList(), listOf(tempReferenceRow()), emptyList())
                },
                loadPending = { listOf(pending) },
                loadAllIntents = { listOf(pending, completed) },
                storage = AndroidOsRecoveryReconciliationStorage(ROOT, os),
                aliasExists = { false },
            )
        val inventory =
            source.loadInventorySnapshot(
                RUN,
                RecoveryCandidateSnapshot(emptyList(), listOf(tempReferenceRow()), emptyList()),
            )
        assertEquals(5, inventory.active.size)
        assertEquals(
            RecoveryQuarantineObservedState.TEMP_AND_FINAL,
            inventory.active
                .single { it.input.sourceRelativeName == "units/u-0.bin.tmp" }
                .observedState,
        )
        assertEquals(
            RecoveryQuarantineObservedState.UNKNOWN_OR_NON_ALLOWLISTED_NAME,
            inventory.active.single { it.input.sourceRelativeName == "unknown.bin" }.observedState,
        )
        assertEquals(
            RecoveryQuarantineObservedState.SQLITE_POINTS_TO_TEMP,
            inventory.active
                .single { it.input.sourceRelativeName == "units/u-1.bin.tmp" }
                .observedState,
        )
        assertEquals(
            RecoveryQuarantineObservedState.FINAL_ORPHAN,
            inventory.active
                .single { it.input.sourceRelativeName == "units/u-2.bin" }
                .observedState,
        )
        assertEquals(4, inventory.quarantine.size)
        assertEquals(2, inventory.quarantine.count { it.knownIntentDestination })
        assertTrue(
            inventory.quarantine.any {
                !it.knownIntentDestination && it.pathState == QuarantinePathState.OCCUPIED
            }
        )
        assertTrue(inventory.quarantine.any { it.pathState == QuarantinePathState.UNSAFE })
        assertFalse(os.listed.any { it.endsWith("nested") })
    }

    @Test
    fun `inventory names receive all exact active artifact roles`() {
        val cases =
            mapOf(
                "key-confirmation/run.kc" to RecoveryQuarantineArtifactRole.KEY_CONFIRMATION,
                "units/u-0.bin" to RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT,
                "manifests/m-1.bin" to RecoveryQuarantineArtifactRole.MANIFEST_CIPHERTEXT,
                "key-envelopes/manifest-1.bin" to
                    RecoveryQuarantineArtifactRole.MANIFEST_KEY_ENVELOPE,
                "key-envelopes/unit-0.bin" to RecoveryQuarantineArtifactRole.MICROFILE_KEY_ENVELOPE,
                "unknown.bin" to RecoveryQuarantineArtifactRole.UNKNOWN_REGULAR,
            )
        cases.forEach { (name, expected) ->
            assertEquals(expected, RecoveryInventoryClassifier.role(name))
        }
    }

    @Test
    fun `production composition exposes no caller supplied snapshot or source parameter`() {
        val create =
            AndroidRecoveryMicrofileReconciliation::class.java.declaredMethods.single {
                it.name == "create"
            }
        assertEquals(2, create.parameterCount)
        assertFalse(
            create.parameterTypes.any {
                it.name.contains("Snapshot") || it.name.contains("ReconciliationSource")
            }
        )
        assertEquals(RecoveryQuarantineEvidenceSink::class.java, create.parameterTypes.last())
    }

    @Test
    fun `actual source keeps journal alias path and descriptor failures distinct`() {
        val os = InventoryOs().apply { seed(emptyMap(), emptyMap()) }
        val journalFailure = source(os, loadSnapshot = { error("sqlite") })
        val journal = assertThrowsSource { journalFailure.loadCandidate(RUN) }
        assertEquals(RecoveryFailureStage.JOURNAL, journal.diagnostic.stage)
        val structural =
            source(
                os,
                loadSnapshot = {
                    throw RecoverySourceAccessException(
                        com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureDiagnostic(
                            RecoveryFailureCategory.STRUCTURAL,
                            "MalformedRow",
                            "decoded row identity mismatch",
                            RecoveryFailureStage.JOURNAL,
                        ),
                        IllegalStateException("malformed"),
                    )
                },
            )
        assertEquals(
            RecoveryFailureCategory.STRUCTURAL,
            assertThrowsSource { structural.loadCandidate(RUN) }.diagnostic.category,
        )

        val absentAlias = source(os, alias = { false }).loadConfirmation(RUN)
        assertEquals(
            com.monumentogram.dora.poc.recovery.controller.AliasObservation.ABSENT,
            absentAlias.alias,
        )
        val alias = assertThrowsSource {
            source(os, alias = { error("provider") }).loadConfirmation(RUN)
        }
        assertEquals(RecoveryFailureStage.ALIAS_OBSERVATION, alias.diagnostic.stage)
        assertEquals(RecoveryFailureCategory.OPERATIONAL, alias.diagnostic.category)

        os.addActive("units/u-0.bin", byteArrayOf(1))
        os.unsafe("units")
        val unsafe = assertThrowsSource { source(os).loadArtifact(RUN, "units/u-0.bin") }
        assertEquals(RecoveryFailureStage.ARTIFACT_PATH, unsafe.diagnostic.stage)
        os.safeDirectory("units")
        os.failRead = true
        val io = assertThrowsSource { source(os).loadArtifact(RUN, "units/u-0.bin") }
        assertEquals(RecoveryFailureStage.ARTIFACT_IO, io.diagnostic.stage)
        assertEquals(RecoveryFailureCategory.OPERATIONAL, io.diagnostic.category)
    }

    private fun source(
        os: InventoryOs,
        loadSnapshot: (RunId) -> RecoveryCandidateSnapshot = {
            RecoveryCandidateSnapshot(emptyList(), emptyList(), emptyList())
        },
        alias: (RunId) -> Boolean = { false },
    ) =
        AndroidRecoveryReconciliationSource(
            loadBootstrap = { null },
            loadSnapshot = loadSnapshot,
            loadPending = { emptyList() },
            loadAllIntents = { emptyList() },
            storage = AndroidOsRecoveryReconciliationStorage(ROOT, os),
            aliasExists = alias,
        )

    private fun assertThrowsSource(block: () -> Unit): RecoverySourceAccessException =
        try {
            block()
            error("expected RecoverySourceAccessException")
        } catch (error: RecoverySourceAccessException) {
            error
        }

    private fun bootstrapRow() =
        mapOf(
            "candidate_id" to RecoveryCandidate.MICROFILE.contractId,
            "key_confirmation_relative_name" to "key-confirmation/run.kc",
            "key_confirmation_bytes" to 17L,
            "key_confirmation_sha256" to ByteArray(32) { 0x11 },
            "canonical_alias_sha256" to ByteArray(32) { 0x22 },
        )

    private fun expectedBootstrapIdentity(row: Map<String, Any>) =
        StoredKeyConfirmationIdentity(
            KeyConfirmationValue(RecoveryCandidate.MICROFILE, RUN),
            row.getValue("key_confirmation_relative_name") as String,
            row.getValue("key_confirmation_bytes") as Long,
            Sha256Value.fromBytes(row.getValue("key_confirmation_sha256") as ByteArray),
            Sha256Value.fromBytes(row.getValue("canonical_alias_sha256") as ByteArray),
        )

    private class CursorProbe(private val rows: List<Map<String, Any>>) : InvocationHandler {
        val getterPositions = mutableListOf<Int>()
        var advanceCount = 0
        private var position = -1
        private val columns =
            listOf(
                "candidate_id",
                "key_confirmation_relative_name",
                "key_confirmation_bytes",
                "key_confirmation_sha256",
                "canonical_alias_sha256",
            )

        val cursor: Cursor =
            Proxy.newProxyInstance(
                Cursor::class.java.classLoader,
                arrayOf(Cursor::class.java),
                this,
            ) as Cursor

        @Suppress("CyclomaticComplexMethod")
        override fun invoke(
            proxy: Any,
            method: java.lang.reflect.Method,
            args: Array<Any?>?,
        ): Any? =
            when (method.name) {
                "getCount" -> rows.size
                "getPosition" -> position
                "moveToFirst" -> moveTo(0)
                "moveToNext" -> {
                    advanceCount += 1
                    moveTo(position + 1)
                }
                "getColumnIndexOrThrow" -> {
                    val column = args!![0] as String
                    columns.indexOf(column).takeIf { it >= 0 }
                        ?: throw IllegalArgumentException("Unknown column: $column")
                }
                "getString" -> value(args).also { getterPositions += position } as String
                "getLong" -> value(args).also { getterPositions += position } as Long
                "getBlob" ->
                    (value(args).also { getterPositions += position } as ByteArray).copyOf()
                "close" -> Unit
                "isClosed" -> false
                "toString" -> "CursorProbe(position=$position)"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args!![0]
                else ->
                    throw UnsupportedOperationException("Unexpected cursor call: ${method.name}")
            }

        private fun moveTo(requested: Int): Boolean {
            position =
                when {
                    requested < 0 -> -1
                    requested >= rows.size -> rows.size
                    else -> requested
                }
            return position in rows.indices
        }

        private fun value(args: Array<Any?>?): Any {
            check(position in rows.indices) {
                "Cursor position $position is outside ${rows.size} rows"
            }
            return rows[position].getValue(columns[args!![0] as Int])
        }
    }

    private class Descriptor(val path: String) : RecoveryReconciliationDescriptor

    private class InventoryOs : RecoveryReconciliationOs {
        private val stats = mutableMapOf<String, RecoveryReconciliationStat>()
        private val data = mutableMapOf<String, ByteArray>()
        private val children = mutableMapOf<String, MutableList<String>>()
        private val offsets = mutableMapOf<String, Int>()
        val listed = mutableListOf<String>()
        var failRead = false

        fun seed(active: Map<String, ByteArray>, quarantine: Map<String, ByteArray>) {
            val base = File(ROOT, "poc-recovery")
            val v1 = File(base, "v1")
            val activeRoot = File(v1, "runs/${RUN.toCanonicalString()}")
            val quarantineRoot = File(v1, "quarantine/${RUN.toCanonicalString()}")
            val objects = File(quarantineRoot, "objects")
            listOf(
                    ROOT,
                    base,
                    v1,
                    File(v1, "runs"),
                    activeRoot,
                    File(activeRoot, "units"),
                    File(activeRoot, "key-confirmation"),
                    File(v1, "quarantine"),
                    quarantineRoot,
                    objects,
                )
                .forEach {
                    stats[it.path] = RecoveryReconciliationStat(BootstrapPathType.DIRECTORY)
                }
            active.forEach { (relative, bytes) -> add(activeRoot, relative, bytes) }
            quarantine.forEach { (relative, bytes) -> add(quarantineRoot, relative, bytes) }
        }

        fun addActive(relative: String, bytes: ByteArray) {
            add(File(ROOT, "poc-recovery/v1/runs/${RUN.toCanonicalString()}"), relative, bytes)
        }

        fun removeQuarantineNamespace() {
            val prefix = File(ROOT, "poc-recovery/v1/quarantine").path
            stats.keys.filter { it.startsWith(prefix) }.forEach(stats::remove)
            children.keys.filter { it.startsWith(prefix) }.forEach(children::remove)
        }

        fun unsafe(relativeDirectory: String) {
            stats[
                File(ROOT, "poc-recovery/v1/runs/${RUN.toCanonicalString()}/$relativeDirectory")
                    .path] = RecoveryReconciliationStat(BootstrapPathType.SYMLINK)
        }

        fun safeDirectory(relativeDirectory: String) {
            stats[
                File(ROOT, "poc-recovery/v1/runs/${RUN.toCanonicalString()}/$relativeDirectory")
                    .path] = RecoveryReconciliationStat(BootstrapPathType.DIRECTORY)
        }

        fun addUnsafeQuarantine(relative: String) {
            val root = File(ROOT, "poc-recovery/v1/quarantine/${RUN.toCanonicalString()}")
            val parent = File(root, relative).parentFile!!
            children.getOrPut(parent.path) { mutableListOf() } += File(relative).name
            stats[File(root, relative).path] =
                RecoveryReconciliationStat(BootstrapPathType.DIRECTORY)
        }

        private fun add(root: File, relative: String, bytes: ByteArray) {
            var parent = root
            val parts = relative.split('/')
            parts.dropLast(1).forEach { name ->
                children
                    .getOrPut(parent.path) { mutableListOf() }
                    .let { if (name !in it) it += name }
                parent = File(parent, name)
                stats[parent.path] = RecoveryReconciliationStat(BootstrapPathType.DIRECTORY)
            }
            val leaf = File(parent, parts.last())
            children.getOrPut(parent.path) { mutableListOf() } += leaf.name
            stats[leaf.path] =
                RecoveryReconciliationStat(BootstrapPathType.REGULAR, bytes.size.toLong())
            data[leaf.path] = bytes
        }

        override fun lstat(path: String) = stats[path]

        override fun list(path: String): List<String> {
            listed += path
            return children[path]?.toList() ?: emptyList()
        }

        override fun mkdir(path: String, mode: Int) = error("not used")

        override fun open(path: String, flags: Int): RecoveryReconciliationDescriptor {
            offsets[path] = 0
            return Descriptor(path)
        }

        override fun fstat(descriptor: RecoveryReconciliationDescriptor) =
            requireNotNull(stats[(descriptor as Descriptor).path])

        override fun read(
            descriptor: RecoveryReconciliationDescriptor,
            buffer: ByteArray,
            offset: Int,
            count: Int,
        ): Int {
            if (failRead) error("read")
            val path = (descriptor as Descriptor).path
            val bytes = data[path] ?: ByteArray(0)
            val position = offsets[path] ?: 0
            if (position == bytes.size) return 0
            val actual = minOf(count, bytes.size - position)
            bytes.copyInto(buffer, offset, position, position + actual)
            offsets[path] = position + actual
            return actual
        }

        override fun rename(source: String, destination: String) = error("not used")

        override fun fsync(descriptor: RecoveryReconciliationDescriptor) = error("not used")

        override fun close(descriptor: RecoveryReconciliationDescriptor) = Unit
    }

    private companion object {
        val ROOT = File("inventory-root").absoluteFile
        val RUN = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")

        fun intent(
            source: String,
            bytes: ByteArray,
            state: QuarantineIntentState,
        ): RecoveryQuarantineIntentRow {
            val input =
                RecoveryQuarantineIntentInput(
                    RecoveryCandidate.MICROFILE,
                    RUN,
                    source,
                    RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT,
                    bytes.size.toULong(),
                    Sha256Value.calculate(bytes),
                )
            return RecoveryQuarantineIntentRow(
                RecoveryQuarantineIntent.calculate(input),
                input,
                RecoveryQuarantineObservedState.FINAL_ORPHAN,
                QuarantineBootstrapBinding.PRESENT,
                RecoveryQuarantineIntent.destination(input),
                state,
            )
        }

        fun tempReferenceRow() =
            RecoveryMicrofileUnitRow(
                RUN.toCanonicalString(),
                RecoveryCandidate.MICROFILE.contractId,
                1UL,
                0UL,
                1UL,
                5UL,
                "units/u-1.bin.tmp",
                1L,
                Sha256Value.calculate(byteArrayOf(7)),
                "key-envelopes/unit-1.bin",
                1L,
                Sha256Value.calculate(byteArrayOf(8)),
                2UL,
                Sha256Value.calculate(byteArrayOf(9)),
            )
    }
}
