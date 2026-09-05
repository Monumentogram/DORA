package com.monumentogram.dora.poc.recovery.bootstrap

import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.KeyRecoveryClassification
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAead
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadBackend
import com.monumentogram.dora.poc.recovery.crypto.newTestAead
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryKeyBootstrapControllerTest {
    @Test
    fun `both candidates execute KC01 through KC13 and store decryptable typed crypto`() {
        RecoveryCandidate.entries.forEach { candidate ->
            val fixture = Fixture(candidate = candidate, writes = listOf(1, 2, 3, Int.MAX_VALUE))

            val result = fixture.controller.bootstrap(fixture.value)

            assertTrue(result is BootstrapResult.Committed)
            result as BootstrapResult.Committed
            assertTrue(result.evidenceEmitted)
            assertTrue(result.publicationCapability.authorizes(fixture.value))
            assertEquals(BootstrapStep.entries.toList(), result.completedSteps)
            assertEquals(
                listOf(
                    "alias-exists",
                    "inspect-namespaces",
                    "generate-alias",
                    "open-created-alias",
                    "encrypt",
                    "open-temp",
                    "write",
                    "write",
                    "write",
                    "write",
                    "fsync-temp",
                    "close-temp",
                    "final-exists",
                    "rename",
                    "fsync-parent",
                    "begin-transaction",
                    "insert-row",
                    "mark-successful",
                    "end-transaction",
                    "emit-evidence",
                ),
                fixture.events,
            )
            val row = requireNotNull(fixture.journal.row)
            assertEquals(fixture.value.runId.toCanonicalString(), row.runId)
            assertEquals(candidate.contractId, row.candidateId)
            assertEquals("key-confirmation/run.kc", row.keyConfirmationRelativeName)
            assertEquals(fixture.storage.bytes.size.toLong(), row.keyConfirmationBytes)
            assertEquals(Sha256Value.calculate(fixture.storage.bytes), row.keyConfirmationSha256)
            assertEquals(fixture.value.canonicalAliasSha256, row.canonicalAliasSha256)
            assertEquals(KeyConfirmationState.VALID, row.keyConfirmationState)
            val opened = fixture.crypto.openForVerification(fixture.value.runId)
            val decrypted = opened.decryptKeyConfirmation(fixture.storage.bytes, fixture.value)
            assertTrue(
                decrypted
                    is com.monumentogram.dora.poc.recovery.crypto.KeyConfirmationDecryption.Success
            )
        }
    }

    @Test
    fun `KC01 rejects every occupied namespace before mutation`() {
        val occupied =
            listOf(
                BootstrapNamespaceState(aliasOccupied = true),
                BootstrapNamespaceState(keyReferenceNamespaceOccupied = true),
                BootstrapNamespaceState(temporaryOccupied = true),
                BootstrapNamespaceState(finalOccupied = true),
            )

        occupied.forEach { state ->
            val fixture = Fixture(namespace = state)

            val result = fixture.controller.bootstrap(fixture.value)

            assertTrue(result is BootstrapResult.Rejected)
            result as BootstrapResult.Rejected
            assertEquals(KeyRecoveryClassification.KEY_REF_COLLISION, result.classification)
            assertEquals(listOf("alias-exists", "inspect-namespaces"), fixture.events)
            assertFalse(result.remainder.aliasCreated)
            assertFalse(result.remainder.transactionCommitted)
        }
    }

    @Test
    fun `every precommit boundary failure withholds capability and closes acquired resources`() {
        val failingOperations =
            listOf(
                "alias-exists" to BootstrapStep.KC01,
                "inspect-namespaces" to BootstrapStep.KC01,
                "generate-alias" to BootstrapStep.KC02,
                "open-created-alias" to BootstrapStep.KC03,
                "encrypt" to BootstrapStep.KC04,
                "open-temp" to BootstrapStep.KC05,
                "write" to BootstrapStep.KC06,
                "fsync-temp" to BootstrapStep.KC07,
                "close-temp" to BootstrapStep.KC07,
                "final-exists" to BootstrapStep.KC08,
                "rename" to BootstrapStep.KC08,
                "fsync-parent" to BootstrapStep.KC09,
                "begin-transaction" to BootstrapStep.KC10,
                "insert-row" to BootstrapStep.KC10,
                "mark-successful" to BootstrapStep.KC11,
                "end-transaction" to BootstrapStep.KC12,
            )
        failingOperations.forEach { (operation, step) ->
            val fixture = Fixture(failAt = operation)

            val result = fixture.controller.bootstrap(fixture.value)

            assertTrue("$operation must fail closed", result is BootstrapResult.Failed)
            result as BootstrapResult.Failed
            assertEquals(step, result.failedStep)
            assertFalse(result.remainder.transactionCommitted)
            assertFalse(
                "evidence before commit for $operation",
                fixture.events.contains("emit-evidence"),
            )
            if (operation != "open-temp" && fixture.events.contains("open-temp")) {
                assertTrue(
                    "descriptor not closed for $operation",
                    fixture.events.contains("close-temp"),
                )
            }
            if (operation != "begin-transaction" && fixture.events.contains("begin-transaction")) {
                assertTrue(
                    "transaction not ended for $operation",
                    fixture.events.contains("end-transaction"),
                )
            }
        }
    }

    @Test
    fun `zero-byte write fails KC06 without spinning and closes descriptor`() {
        val fixture = Fixture(writes = listOf(0))

        val result = fixture.controller.bootstrap(fixture.value)

        assertTrue(result is BootstrapResult.Failed)
        assertEquals(BootstrapStep.KC06, (result as BootstrapResult.Failed).failedStep)
        assertEquals(1, fixture.events.count { it == "write" })
        assertTrue(fixture.events.contains("close-temp"))
    }

    @Test
    fun `final collision immediately before rename never overwrites`() {
        val fixture = Fixture(finalCollisionAtRename = true)

        val result = fixture.controller.bootstrap(fixture.value)

        assertTrue(result is BootstrapResult.Rejected)
        assertEquals(
            KeyRecoveryClassification.KEY_REF_COLLISION,
            (result as BootstrapResult.Rejected).classification,
        )
        assertFalse(fixture.events.contains("rename"))
        assertFalse(fixture.events.contains("begin-transaction"))
    }

    @Test
    fun `KC13 evidence failure retains committed capability and never rolls back`() {
        val fixture = Fixture(failAt = "emit-evidence")

        val result = fixture.controller.bootstrap(fixture.value)

        assertTrue(result is BootstrapResult.Committed)
        result as BootstrapResult.Committed
        assertFalse(result.evidenceEmitted)
        assertNotNull(result.evidenceFailure)
        assertTrue(result.publicationCapability.authorizes(fixture.value))
        assertTrue(result.remainder.transactionCommitted)
        assertEquals(1, fixture.events.count { it == "begin-transaction" })
        assertFalse(fixture.events.any { it.startsWith("delete") || it.startsWith("rollback") })
    }

    @Test
    fun `marked transaction whose end throws is not a commit capability`() {
        val fixture = Fixture(failAt = "end-transaction")

        val result = fixture.controller.bootstrap(fixture.value)

        assertTrue(result is BootstrapResult.Failed)
        result as BootstrapResult.Failed
        assertEquals(BootstrapStep.KC12, result.failedStep)
        assertTrue(result.remainder.runRowInserted)
        assertTrue(result.remainder.transactionMarkedSuccessful)
        assertFalse(result.remainder.transactionCommitted)
    }

    @Test
    fun `caller supplied proof cannot forge publication capability`() {
        val value =
            KeyConfirmationValue(
                RecoveryCandidate.STREAM,
                RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff"),
            )

        assertThrows(IllegalStateException::class.java) {
            BootstrapPublicationCapability(value, Any())
        }
    }

    @Test
    fun `process guard excludes another controller for the same run across instances`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = Fixture(blockOnNamespace = entered to release)
        val second = Fixture(runId = first.value.runId)
        var firstResult: BootstrapResult? = null
        val worker = thread(start = true) { firstResult = first.controller.bootstrap(first.value) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))

        val competing = second.controller.bootstrap(second.value)

        assertTrue(competing is BootstrapResult.ConcurrentWriter)
        assertTrue(second.events.isEmpty())
        release.countDown()
        worker.join(5_000)
        assertTrue(firstResult is BootstrapResult.Committed)
    }

    @Suppress("LongParameterList")
    private class Fixture(
        candidate: RecoveryCandidate = RecoveryCandidate.STREAM,
        runId: RunId = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff"),
        namespace: BootstrapNamespaceState = BootstrapNamespaceState(),
        writes: List<Int> = listOf(Int.MAX_VALUE),
        failAt: String? = null,
        finalCollisionAtRename: Boolean = false,
        blockOnNamespace: Pair<CountDownLatch, CountDownLatch>? = null,
    ) {
        val events = mutableListOf<String>()
        val value = KeyConfirmationValue(candidate, runId)
        val crypto = RecordingCrypto(events, failAt)
        val storage =
            RecordingStorage(
                events,
                namespace,
                writes,
                failAt,
                finalCollisionAtRename,
                blockOnNamespace,
            )
        val journal = RecordingJournal(events, failAt)
        val controller =
            RecoveryKeyBootstrapController(
                crypto = crypto,
                storage = storage,
                journal = journal,
                evidenceSink =
                    BootstrapEvidenceSink {
                        events += "emit-evidence"
                        if (failAt == "emit-evidence") error("injected emit-evidence")
                    },
            )
    }

    private class RecordingCrypto(
        private val events: MutableList<String>,
        private val failAt: String?,
    ) : RecoveryBootstrapCrypto {
        private val primitive = newTestAead()
        private val backend =
            object : RecoveryRunAeadBackend {
                override fun generateNew(keyUri: String) = Unit

                override fun getAead(keyUri: String) = primitive
            }

        override fun aliasExists(runId: RunId): Boolean {
            event("alias-exists")
            return false
        }

        override fun generateNewAlias(runId: RunId) {
            event("generate-alias")
        }

        override fun openCreatedAlias(runId: RunId): RecoveryRunAead {
            event("open-created-alias")
            return RecoveryRunAead.openExisting(runId, backend)
        }

        override fun encryptConfirmation(
            runAead: RecoveryRunAead,
            value: KeyConfirmationValue,
        ): ByteArray {
            event("encrypt")
            return runAead.encryptKeyConfirmation(value)
        }

        fun openForVerification(runId: RunId): RecoveryRunAead =
            RecoveryRunAead.openExisting(runId, backend)

        private fun event(name: String) {
            events += name
            if (failAt == name) error("injected $name")
        }
    }

    private class RecordingStorage(
        private val events: MutableList<String>,
        private val namespace: BootstrapNamespaceState,
        writes: List<Int>,
        private val failAt: String?,
        private val finalCollisionAtRename: Boolean,
        private val blockOnNamespace: Pair<CountDownLatch, CountDownLatch>?,
    ) : RecoveryBootstrapStorage {
        private val writeSizes = ArrayDeque(writes)
        private val sink = ArrayList<Byte>()
        val bytes: ByteArray
            get() = sink.toByteArray()

        override fun inspectNamespaces(runId: RunId): BootstrapNamespaceState {
            event("inspect-namespaces")
            blockOnNamespace?.let { (entered, release) ->
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            return namespace
        }

        override fun openExclusiveConfirmationTemp(runId: RunId): BootstrapWriteHandle {
            event("open-temp")
            return object : BootstrapWriteHandle {}
        }

        override fun write(
            handle: BootstrapWriteHandle,
            bytes: ByteArray,
            offset: Int,
            count: Int,
        ): Int {
            event("write")
            val requested = writeSizes.removeFirstOrNull() ?: Int.MAX_VALUE
            val written = minOf(requested, count)
            repeat(written) { sink += bytes[offset + it] }
            return written
        }

        override fun fsyncTemp(handle: BootstrapWriteHandle) = event("fsync-temp")

        override fun closeTemp(handle: BootstrapWriteHandle) = event("close-temp")

        override fun finalExists(runId: RunId): Boolean {
            event("final-exists")
            return finalCollisionAtRename
        }

        override fun renameTempToFinal(runId: RunId) = event("rename")

        override fun fsyncConfirmationParent(runId: RunId) = event("fsync-parent")

        private fun event(name: String) {
            events += name
            if (failAt == name) error("injected $name")
        }
    }

    private class RecordingJournal(
        private val events: MutableList<String>,
        private val failAt: String?,
    ) : RecoveryRunBootstrapJournal {
        var row: RecoveryBootstrapRunRow? = null

        override fun beginNonExclusive(): RecoveryRunBootstrapTransaction {
            event("begin-transaction")
            return object : RecoveryRunBootstrapTransaction {
                override fun insert(value: RecoveryBootstrapRunRow) {
                    event("insert-row")
                    row = value
                }

                override fun markSuccessful() = event("mark-successful")

                override fun end() = event("end-transaction")
            }
        }

        private fun event(name: String) {
            events += name
            if (failAt == name) error("injected $name")
        }
    }
}
