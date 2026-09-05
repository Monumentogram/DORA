@file:Suppress("WildcardImport", "LongMethod")

package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.bootstrap.*
import com.monumentogram.dora.poc.recovery.contract.*
import com.monumentogram.dora.poc.recovery.crypto.*
import java.lang.reflect.Modifier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

@Suppress("LargeClass")
class RecoveryMicrofilePublicationControllerTest {
    private val run = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")

    @Test
    fun `publication row model contains every active protocol field`() {
        val protocolFields =
            setOf(
                "runId",
                "candidateId",
                "publicationKind",
                "generation",
                "committedEndExclusive",
                "publicationRelativeName",
                "publicationBytes",
                "publicationSha256",
                "keyEnvelopeRelativeName",
                "keyEnvelopeBytes",
                "keyEnvelopeSha256",
                "previousPublicationCiphertextSha256",
                "state",
            )

        assertEquals(
            protocolFields,
            RecoveryManifestPublicationRow::class
                .java
                .declaredFields
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet(),
        )
    }

    @Test
    fun `exact cadence byte limits publish and one byte oversize rejects before journal`() {
        val auth = authorized(run)
        listOf(5UL to 160_000, 15UL to 480_000, 30UL to 960_000).forEach { (cadence, limit) ->
            val boundary = Fixture(run)
            assertTrue(
                boundary.controller.publish(
                    MicrofilePublicationInput(auth.first, auth.second, ByteArray(limit), cadence)
                ) is MicrofilePublicationResult.Committed
            )

            val oversize = Fixture(run)
            val rejected =
                oversize.controller.publish(
                    MicrofilePublicationInput(
                        auth.first,
                        auth.second,
                        ByteArray(limit + 1),
                        cadence,
                    )
                )
            assertTrue(rejected is MicrofilePublicationResult.Rejected)
            assertEquals(0, oversize.journal.loadCount)
            assertEquals(0, oversize.crypto.openCount)
            assertTrue(oversize.storage.files.isEmpty())
        }
    }

    @Test
    fun `actual Tink genesis and later publication complete P01 through P21 with cumulative manifest`() {
        val auth = authorized(run)
        val fixture = Fixture(run)
        val first =
            fixture.controller.publish(
                MicrofilePublicationInput(auth.first, auth.second, "first".toByteArray(), 5UL)
            )
        assertTrue(first is MicrofilePublicationResult.Committed)
        assertEquals(
            MicrofileStep.entries.toList(),
            (first as MicrofilePublicationResult.Committed).completedSteps,
        )
        val second =
            fixture.controller.publish(
                MicrofilePublicationInput(
                    auth.first,
                    auth.second,
                    "second-unit".toByteArray(),
                    15UL,
                )
            )
        assertTrue(second is MicrofilePublicationResult.Committed)
        assertEquals(
            MicrofileStep.entries.toList(),
            (second as MicrofilePublicationResult.Committed).completedSteps,
        )
        assertEquals(listOf(0UL, 1UL), fixture.journal.units.map { it.unitIndex })
        assertEquals(listOf(1UL, 2UL), fixture.journal.publications.map { it.generation })
        assertTrue(
            fixture.journal.publications.all { it.publicationKind == PublicationKind.MANIFEST }
        )
        assertEquals(
            fixture.journal.publications[0].publicationSha256,
            fixture.journal.publications[1].previousPublicationCiphertextSha256,
        )

        val unit = fixture.journal.units.first()
        val unitEnvelopeAad =
            KeyEnvelopeAad(
                RecoveryCandidate.MICROFILE,
                run,
                KeyEnvelopeTargetKind.MICROFILE,
                1UL,
                0UL,
                0UL,
                unit.plaintextEndExclusive,
                5UL,
                Sha256Value.ZERO,
            )
        val unitKeyset =
            RecoveryTinkRuntime.parseEncryptedAeadKeyset(
                fixture.storage.files.getValue(unit.keyEnvelopeRelativeName),
                fixture.crypto.runAead,
                unitEnvelopeAad,
            )
        val unitAad =
            MicrofileAad(
                RecoveryCandidate.MICROFILE,
                run,
                1UL,
                0UL,
                0UL,
                unit.plaintextEndExclusive,
                5UL,
                Sha256Value.ZERO,
            )
        assertArrayEquals(
            "first".toByteArray(),
            unitKeyset.decryptMicrofile(
                fixture.storage.files.getValue(unit.ciphertextRelativeName),
                unitAad,
            ),
        )
        assertThrows(RecoveryContractException::class.java) {
            unitKeyset.decryptMicrofile(
                fixture.storage.files.getValue(unit.ciphertextRelativeName),
                MicrofileAad(
                    RecoveryCandidate.MICROFILE,
                    run,
                    2UL,
                    1UL,
                    1UL,
                    2UL,
                    5UL,
                    fixture.journal.publications.first().publicationSha256,
                ),
            )
        }

        val publication = fixture.journal.publications.last()
        val envelopeAad =
            KeyEnvelopeAad(
                RecoveryCandidate.MICROFILE,
                run,
                KeyEnvelopeTargetKind.MANIFEST,
                2UL,
                KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
                0UL,
                publication.committedEndExclusive,
                0UL,
                fixture.journal.publications.first().publicationSha256,
            )
        val keyset =
            RecoveryTinkRuntime.parseEncryptedAeadKeyset(
                fixture.storage.files.getValue(publication.keyEnvelopeRelativeName),
                fixture.crypto.runAead,
                envelopeAad,
            )
        val aad =
            PublicationAad(
                RecoveryCandidate.MICROFILE,
                run,
                PublicationKind.MANIFEST,
                2UL,
                1UL,
                publication.committedEndExclusive,
                fixture.journal.publications.first().publicationSha256,
            )
        val manifest =
            RecoveryManifestCodec.decode(
                keyset.decryptPublication(
                    fixture.storage.files.getValue(publication.publicationRelativeName),
                    aad,
                )
            )
        assertEquals(2, manifest.entries.size)
        assertEquals(
            "first".toByteArray().size.toULong(),
            manifest.entries[0].plaintextEndExclusive,
        )
        assertEquals(15UL, manifest.entries[1].cadenceSeconds)
    }

    @Test
    fun `ambiguous state is rejected before alias crypto or filesystem`() {
        val auth = authorized(run)
        val fixture = Fixture(run)
        fixture.journal.bootstrap +=
            CandidateBootstrapRow(
                run.toCanonicalString(),
                RecoveryCandidate.MICROFILE.contractId,
                KeyConfirmationState.VALID,
            )
        val result =
            fixture.controller.publish(
                MicrofilePublicationInput(auth.first, auth.second, byteArrayOf(1), 5UL)
            )
        assertTrue(result is MicrofilePublicationResult.Rejected)
        assertEquals(0, fixture.crypto.openCount)
        assertTrue(fixture.storage.files.isEmpty())
    }

    @Test
    fun `mismatched bootstrap capability is rejected before journal alias and filesystem`() {
        val confirmation = KeyConfirmationValue(RecoveryCandidate.MICROFILE, run)
        val other = authorized(RunId.fromCanonicalString("10213243-5465-7687-98a9-bacbdcedfe0f"))
        val fixture = Fixture(run)

        val result =
            fixture.controller.publish(
                MicrofilePublicationInput(confirmation, other.second, byteArrayOf(1), 5UL)
            )

        assertTrue(result is MicrofilePublicationResult.Rejected)
        assertEquals(0, fixture.journal.loadCount)
        assertEquals(0, fixture.crypto.openCount)
        assertTrue(fixture.storage.files.isEmpty())
    }

    @Test
    fun `every prior-state ambiguity family rejects before the next alias open`() {
        val mutations: List<(MemoryJournal) -> Unit> =
            listOf(
                { j -> j.units[0] = j.units[0].copy(unitIndex = 2UL) },
                { j -> j.units[0] = j.units[0].copy(plaintextStartInclusive = 1UL) },
                { j -> j.units[0] = j.units[0].copy(cadenceSeconds = 6UL) },
                { j ->
                    j.units[0] = j.units[0].copy(ciphertextRelativeName = "units/u-0000000001.ct")
                },
                { j -> j.publications[0] = j.publications[0].copy(committedEndExclusive = 999UL) },
                { j ->
                    j.publications[0] =
                        j.publications[0].copy(publicationKind = PublicationKind.CHECKPOINT)
                },
                { j ->
                    j.publications[0] =
                        j.publications[0].copy(
                            previousPublicationCiphertextSha256 =
                                Sha256Value.fromBytes(ByteArray(32) { 1 })
                        )
                },
                { j -> j.publications += j.publications[0] },
                { j -> j.units[0] = j.units[0].copy(processingIntentId = Sha256Value.ZERO) },
            )
        mutations.forEach { mutation ->
            val auth = authorized(run)
            val fixture = Fixture(run)
            assertTrue(
                fixture.controller.publish(
                    MicrofilePublicationInput(auth.first, auth.second, byteArrayOf(1), 5UL)
                ) is MicrofilePublicationResult.Committed
            )
            mutation(fixture.journal)
            fixture.crypto.openCount = 0
            fixture.storage.files.clear()
            val result =
                fixture.controller.publish(
                    MicrofilePublicationInput(auth.first, auth.second, byteArrayOf(2), 5UL)
                )
            assertTrue(result is MicrofilePublicationResult.Rejected)
            assertEquals(0, fixture.crypto.openCount)
            assertTrue(fixture.storage.files.isEmpty())
        }
    }

    @Test
    fun `stored range wider than cadence limit rejects before alias`() {
        val auth = authorized(run)
        val fixture = Fixture(run)
        assertTrue(
            fixture.controller.publish(
                MicrofilePublicationInput(auth.first, auth.second, byteArrayOf(1), 5UL)
            ) is MicrofilePublicationResult.Committed
        )
        val prior = fixture.journal.units.single()
        val oversizedEnd = 160_001UL
        fixture.journal.units[0] =
            prior.copy(
                plaintextEndExclusive = oversizedEnd,
                processingIntentId =
                    RecoveryProcessingIntent.calculate(
                        RecoveryProcessingIntentInput(
                            RecoveryCandidate.MICROFILE,
                            run,
                            prior.unitIndex,
                            prior.plaintextStartInclusive,
                            oversizedEnd,
                            prior.ciphertextSha256,
                        )
                    ),
            )
        fixture.journal.publications[0] =
            fixture.journal.publications.single().copy(committedEndExclusive = oversizedEnd)
        fixture.crypto.openCount = 0
        fixture.storage.files.clear()

        val result =
            fixture.controller.publish(
                MicrofilePublicationInput(auth.first, auth.second, byteArrayOf(2), 5UL)
            )

        assertTrue(result is MicrofilePublicationResult.Rejected)
        assertEquals(0, fixture.crypto.openCount)
        assertTrue(fixture.storage.files.isEmpty())
    }

    @Test
    fun `arbitrary same-module construction cannot mint publication capability`() {
        CandidatePublicationCapability::class.java.declaredConstructors.forEach { constructor ->
            constructor.isAccessible = true
            val arguments =
                constructor.parameterTypes.map { type ->
                    when {
                        type == RunId::class.java -> run
                        type == java.lang.Long.TYPE -> 1L
                        else -> Any()
                    }
                }
            val forged =
                runCatching { constructor.newInstance(*arguments.toTypedArray()) }.getOrNull()
            assertNull(forged)
        }
    }

    @Test
    fun `returned durable remainder graph exposes only immutable fields`() {
        listOf(CandidateArtifactRemainder::class.java, MicrofileDurableRemainder::class.java)
            .flatMap { type -> type.declaredFields.filterNot { it.isSynthetic } }
            .forEach { field ->
                assertTrue(
                    "Returned remainder field is mutable: ${field.name}",
                    Modifier.isFinal(field.modifiers),
                )
            }
    }

    @Test
    fun `invalid write progress is rejected and acquired descriptor is closed`() {
        val auth = authorized(run)
        listOf("negative", "zero", "over").forEach { progress ->
            val fixture = Fixture(run, invalidProgress = progress)
            val result =
                fixture.controller.publish(
                    MicrofilePublicationInput(auth.first, auth.second, byteArrayOf(1), 5UL)
                )
            assertTrue(result is MicrofilePublicationResult.Failed)
            assertEquals(
                MicrofileStep.P02,
                (result as MicrofilePublicationResult.Failed).failedStep,
            )
            assertTrue(fixture.storage.openHandles.single().closed)
            assertTrue(fixture.journal.units.isEmpty())
        }
    }

    @Test
    fun `artifact failure matrix reports exact step closes descriptors and never commits`() {
        data class Artifact(
            val id: String,
            val write: MicrofileStep,
            val sync: MicrofileStep,
            val rename: MicrofileStep,
            val parent: MicrofileStep,
            val remainder: (MicrofileDurableRemainder) -> CandidateArtifactRemainder,
        )
        val artifacts =
            listOf(
                Artifact(
                    "unit-envelope",
                    MicrofileStep.P02,
                    MicrofileStep.P03,
                    MicrofileStep.P04,
                    MicrofileStep.P05,
                ) {
                    it.unitEnvelope
                },
                Artifact(
                    "unit-ciphertext",
                    MicrofileStep.P06,
                    MicrofileStep.P07,
                    MicrofileStep.P08,
                    MicrofileStep.P09,
                ) {
                    it.unitCiphertext
                },
                Artifact(
                    "manifest-envelope",
                    MicrofileStep.P11,
                    MicrofileStep.P12,
                    MicrofileStep.P13,
                    MicrofileStep.P14,
                ) {
                    it.manifestEnvelope
                },
                Artifact(
                    "manifest-ciphertext",
                    MicrofileStep.P15,
                    MicrofileStep.P16,
                    MicrofileStep.P17,
                    MicrofileStep.P18,
                ) {
                    it.manifestCiphertext
                },
            )
        val auth = authorized(run)
        for (artifact in artifacts) {
            for (operation in
                listOf(
                    "open",
                    "write",
                    "fsync",
                    "close",
                    "collision",
                    "unsafe",
                    "rename",
                    "parent",
                )) {
                val fixture = Fixture(run, storageFail = "${artifact.id}:$operation")
                val result =
                    fixture.controller.publish(
                        MicrofilePublicationInput(auth.first, auth.second, byteArrayOf(1), 5UL)
                    )
                assertTrue("${artifact.id}:$operation", result is MicrofilePublicationResult.Failed)
                result as MicrofilePublicationResult.Failed
                val expectedStep =
                    when (operation) {
                        "open",
                        "write" -> artifact.write
                        "fsync",
                        "close" -> artifact.sync
                        "collision",
                        "unsafe",
                        "rename" -> artifact.rename
                        else -> artifact.parent
                    }
                assertEquals("${artifact.id}:$operation", expectedStep, result.failedStep)
                assertTrue(fixture.storage.openHandles.all { it.closed })
                assertTrue(fixture.journal.units.isEmpty())
                val target = artifact.remainder(result.remainder)
                when (operation) {
                    "open" -> assertFalse(target.tempCreated)
                    "write" -> {
                        assertTrue(target.tempCreated)
                        assertFalse(target.fullyWritten)
                        assertFalse(target.fileSynced)
                    }
                    "fsync" -> {
                        assertTrue(target.tempCreated)
                        assertTrue(target.fullyWritten)
                        assertFalse(target.fileSynced)
                    }
                    "close" -> {
                        assertTrue(target.tempCreated)
                        assertTrue(target.fullyWritten)
                        assertTrue(target.fileSynced)
                        assertEquals(CandidateSideEffectState.NOT_ATTEMPTED, target.rename)
                    }
                    "collision",
                    "unsafe" -> {
                        assertTrue(target.fileSynced)
                        assertEquals(CandidateSideEffectState.NOT_ATTEMPTED, target.rename)
                        assertFalse(artifact.id in fixture.storage.renamedArtifacts)
                    }
                    "rename" -> {
                        assertEquals(CandidateSideEffectState.OUTCOME_UNKNOWN, target.rename)
                        assertFalse(target.parentSynced)
                    }
                    "parent" -> {
                        assertEquals(CandidateSideEffectState.CONFIRMED, target.rename)
                        assertFalse(target.parentSynced)
                    }
                }
            }
        }
    }

    @Test
    fun `shared guard excludes another candidate writer for same run`() {
        val auth = authorized(run)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = Fixture(run, blockOnOpen = entered to release)
        val worker = thread {
            first.controller.publish(
                MicrofilePublicationInput(auth.first, auth.second, byteArrayOf(1), 5UL)
            )
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val second = Fixture(run)
        assertTrue(
            second.controller.publish(
                MicrofilePublicationInput(auth.first, auth.second, byteArrayOf(2), 5UL)
            ) is MicrofilePublicationResult.ConcurrentWriter
        )
        release.countDown()
        worker.join(5_000)
        assertFalse(worker.isAlive)
    }

    @Test
    fun `shared guard allows independent candidate writers for different runs`() {
        val otherRun = RunId.fromCanonicalString("10213243-5465-7687-98a9-bacbdcedfe0f")
        val firstAuth = authorized(run)
        val otherAuth = authorized(otherRun)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = Fixture(run, blockOnOpen = entered to release)
        val worker = thread {
            first.controller.publish(
                MicrofilePublicationInput(
                    firstAuth.first,
                    firstAuth.second,
                    byteArrayOf(1),
                    5UL,
                )
            )
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val other = Fixture(otherRun)
        assertTrue(
            other.controller.publish(
                MicrofilePublicationInput(otherAuth.first, otherAuth.second, byteArrayOf(2), 5UL)
            ) is MicrofilePublicationResult.Committed
        )
        release.countDown()
        worker.join(5_000)
        assertFalse(worker.isAlive)
    }

    @Test
    fun `short writes complete and evidence failure retains committed capability`() {
        val auth = authorized(run)
        val fixture = Fixture(run, maxWrite = 1, evidenceFails = true)
        val result =
            fixture.controller.publish(
                MicrofilePublicationInput(auth.first, auth.second, byteArrayOf(1, 2, 3), 30UL)
            )
        assertTrue(result is MicrofilePublicationResult.Committed)
        result as MicrofilePublicationResult.Committed
        assertFalse(result.evidenceEmitted)
        assertNotNull(result.evidenceFailure)
        assertEquals(1, fixture.journal.units.size)
        assertTrue(fixture.storage.openHandles.all { it.closed })
    }

    @Test
    fun `zero write and endTransaction failure withhold publication capability and close resources`() {
        val auth = authorized(run)
        val zero = Fixture(run, maxWrite = 0)
        val zeroResult =
            zero.controller.publish(
                MicrofilePublicationInput(auth.first, auth.second, byteArrayOf(1), 5UL)
            )
        assertTrue(zeroResult is MicrofilePublicationResult.Failed)
        assertEquals(
            MicrofileStep.P02,
            (zeroResult as MicrofilePublicationResult.Failed).failedStep,
        )
        assertTrue(zero.storage.openHandles.single().closed)

        val end = Fixture(run, failEnd = true)
        val endResult =
            end.controller.publish(
                MicrofilePublicationInput(auth.first, auth.second, byteArrayOf(1), 5UL)
            )
        assertTrue(endResult is MicrofilePublicationResult.Failed)
        assertEquals(MicrofileStep.P20, (endResult as MicrofilePublicationResult.Failed).failedStep)
        assertTrue(end.journal.units.isEmpty())
        assertEquals(
            CandidateSideEffectState.OUTCOME_UNKNOWN,
            (endResult as MicrofilePublicationResult.Failed).remainder.transactionEnd,
        )
    }

    @Test
    fun `side effect failure keeps conservative rename remainder and closes descriptor`() {
        val auth = authorized(run)
        val fixture = Fixture(run, storageFail = "rename")

        val result =
            fixture.controller.publish(
                MicrofilePublicationInput(auth.first, auth.second, byteArrayOf(1), 5UL)
            )

        assertTrue(result is MicrofilePublicationResult.Failed)
        result as MicrofilePublicationResult.Failed
        assertEquals(MicrofileStep.P04, result.failedStep)
        assertEquals(
            CandidateSideEffectState.OUTCOME_UNKNOWN,
            result.remainder.unitEnvelope.rename,
        )
        assertTrue(fixture.storage.openHandles.single().closed)
        assertTrue(fixture.journal.units.isEmpty())
    }

    @Test
    fun `shared guard excludes candidate while bootstrap owns same run`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocking = authorized(run, entered to release, async = true)
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val fixture = Fixture(run)
        val placeholder =
            authorized(RunId.fromCanonicalString("10213243-5465-7687-98a9-bacbdcedfe0f"))
        val result =
            fixture.controller.publish(
                MicrofilePublicationInput(
                    KeyConfirmationValue(RecoveryCandidate.MICROFILE, run),
                    placeholder.second,
                    byteArrayOf(1),
                    5UL,
                )
            )
        assertTrue(result is MicrofilePublicationResult.ConcurrentWriter)
        release.countDown()
        blocking.third?.join(5_000)
    }

    private data class Authorization(
        val first: KeyConfirmationValue,
        val second: BootstrapPublicationCapability,
        val third: Thread? = null,
    )

    private fun authorized(
        runId: RunId,
        block: Pair<CountDownLatch, CountDownLatch>? = null,
        async: Boolean = false,
    ): Authorization {
        val value = KeyConfirmationValue(RecoveryCandidate.MICROFILE, runId)
        val primitive = newTestAead()
        val crypto =
            object : RecoveryBootstrapCrypto {
                override fun aliasExists(runId: RunId) = false

                override fun createNewAlias(runId: RunId) =
                    BootstrapAliasCreation.Created(
                        RecoveryRunAeadProvider(
                                object : RecoveryRunAeadBackend {
                                    override fun generateNew(keyUri: String) = Unit

                                    override fun getAead(keyUri: String) = primitive
                                }
                            )
                            .createNew(runId)
                    )

                override fun encryptConfirmation(
                    runAead: RecoveryRunAead,
                    value: KeyConfirmationValue,
                ) = runAead.encryptKeyConfirmation(value)
            }
        val storage =
            object : RecoveryBootstrapStorage {
                val handle = object : BootstrapWriteHandle {}

                override fun inspectNamespaces(runId: RunId): BootstrapNamespaceState {
                    block?.let {
                        it.first.countDown()
                        check(it.second.await(5, TimeUnit.SECONDS))
                    }
                    return BootstrapNamespaceState()
                }

                override fun openExclusiveConfirmationTemp(runId: RunId) = handle

                override fun write(
                    handle: BootstrapWriteHandle,
                    bytes: ByteArray,
                    offset: Int,
                    count: Int,
                ) = count

                override fun fsyncTemp(handle: BootstrapWriteHandle) = Unit

                override fun closeTemp(handle: BootstrapWriteHandle) = Unit

                override fun finalExists(runId: RunId) = false

                override fun renameTempToFinal(runId: RunId) = Unit

                override fun fsyncConfirmationParent(runId: RunId) = Unit
            }
        val journal =
            object : RecoveryRunBootstrapJournal {
                override fun beginNonExclusive() =
                    object : RecoveryRunBootstrapTransaction {
                        override fun insert(value: RecoveryBootstrapRunRow) = Unit

                        override fun markSuccessful() = Unit

                        override fun end() = Unit
                    }
            }
        val controller =
            RecoveryKeyBootstrapController(crypto, storage, journal, BootstrapEvidenceSink {})
        if (async) {
            var result: BootstrapResult? = null
            val worker = thread { result = controller.bootstrap(value) }
            return Authorization(
                value,
                authorized(RunId.fromCanonicalString("20213243-5465-7687-98a9-bacbdcedfe0f"))
                    .second,
                worker,
            )
        }
        val result = controller.bootstrap(value) as BootstrapResult.Committed
        return Authorization(value, result.publicationCapability)
    }

    @Suppress("LongParameterList")
    private class Fixture(
        run: RunId,
        maxWrite: Int = Int.MAX_VALUE,
        evidenceFails: Boolean = false,
        failEnd: Boolean = false,
        storageFail: String? = null,
        invalidProgress: String? = null,
        blockOnOpen: Pair<CountDownLatch, CountDownLatch>? = null,
    ) {
        val crypto = ActualCrypto(run)
        val storage = MemoryStorage(maxWrite, storageFail, invalidProgress, blockOnOpen)
        val journal = MemoryJournal(run, failEnd)
        val controller =
            RecoveryMicrofilePublicationController(
                crypto,
                storage,
                journal,
                RecoveryMicrofileEvidenceSink { _, _ -> if (evidenceFails) error("evidence") },
            )
    }

    private class ActualCrypto(run: RunId) : RecoveryMicrofileCrypto {
        private val backend = RecordingRunAeadBackend()
        val runAead = RecoveryRunAeadProvider(backend).createNew(run)
        var openCount = 0

        override fun openRunAead(runId: RunId): RecoveryRunAead {
            openCount++
            return runAead
        }

        override fun createKeyset(
            aad: KeyEnvelopeAad,
            runAead: RecoveryRunAead,
        ): PreparedRecoveryAead {
            val keyset = RecoveryTinkRuntime.newAeadKeyset(aad)
            return PreparedRecoveryAead(keyset, keyset.serializeEncrypted(runAead))
        }

        override fun encryptMicrofile(
            keyset: RecoveryAeadKeyset,
            plaintext: ByteArray,
            aad: MicrofileAad,
        ) = keyset.encryptMicrofile(plaintext, aad)

        override fun encryptManifest(
            keyset: RecoveryAeadKeyset,
            plaintext: ByteArray,
            aad: PublicationAad,
        ) = keyset.encryptPublication(plaintext, aad)
    }

    private class MemoryStorage(
        private val maxWrite: Int,
        private val failAt: String? = null,
        private val invalidProgress: String? = null,
        private val blockOnOpen: Pair<CountDownLatch, CountDownLatch>? = null,
    ) : RecoveryCandidateStorage {
        data class H(
            val name: String,
            val bytes: MutableList<Byte> = mutableListOf(),
            var closed: Boolean = false,
        ) : CandidateWriteHandle

        val files = mutableMapOf<String, ByteArray>()
        val openHandles = mutableListOf<H>()
        val renamedArtifacts = mutableSetOf<String>()

        private fun artifact(name: String) =
            when {
                name.startsWith("units/") -> "unit-ciphertext"
                name.startsWith("manifests/") -> "manifest-ciphertext"
                name.startsWith("key-envelopes/manifest-") -> "manifest-envelope"
                else -> "unit-envelope"
            }

        private fun fail(name: String, operation: String) {
            if (failAt == "${artifact(name)}:$operation" || failAt == operation) {
                error("injected ${artifact(name)} $operation")
            }
        }

        override fun openExclusiveTemp(
            runId: RunId,
            temporaryRelativeName: String,
        ): CandidateWriteHandle {
            fail(temporaryRelativeName, "open")
            blockOnOpen?.let {
                it.first.countDown()
                check(it.second.await(5, TimeUnit.SECONDS))
            }
            return H(temporaryRelativeName).also(openHandles::add)
        }

        @Suppress("ReturnCount")
        override fun write(
            handle: CandidateWriteHandle,
            bytes: ByteArray,
            offset: Int,
            count: Int,
        ): Int {
            val exact = handle as H
            fail(exact.name, "write")
            when (invalidProgress) {
                "negative" -> return -1
                "zero" -> return 0
                "over" -> return count + 1
            }
            val amount = minOf(maxWrite, count)
            repeat(amount) { exact.bytes += bytes[offset + it] }
            return amount
        }

        override fun fsync(handle: CandidateWriteHandle) {
            fail((handle as H).name, "fsync")
        }

        override fun close(handle: CandidateWriteHandle) {
            val exact = handle as H
            exact.closed = true
            fail(exact.name, "close")
        }

        override fun finalExists(runId: RunId, finalRelativeName: String): Boolean {
            fail(finalRelativeName, "unsafe")
            if (failAt == "${artifact(finalRelativeName)}:collision") return true
            return files.containsKey(finalRelativeName)
        }

        override fun renameTempToFinal(
            runId: RunId,
            temporaryRelativeName: String,
            finalRelativeName: String,
        ) {
            renamedArtifacts += artifact(finalRelativeName)
            fail(finalRelativeName, "rename")
            val h = openHandles.single { it.name == temporaryRelativeName }
            files[finalRelativeName] = h.bytes.toByteArray()
        }

        override fun fsyncParent(runId: RunId, finalRelativeName: String) {
            fail(finalRelativeName, "parent")
        }
    }

    private class MemoryJournal(run: RunId, private val failEnd: Boolean) :
        RecoveryMicrofileJournal {
        var loadCount = 0
        val bootstrap =
            mutableListOf(
                CandidateBootstrapRow(
                    run.toCanonicalString(),
                    RecoveryCandidate.MICROFILE.contractId,
                    KeyConfirmationState.VALID,
                )
            )
        val units = mutableListOf<RecoveryMicrofileUnitRow>()
        val publications = mutableListOf<RecoveryManifestPublicationRow>()

        override fun loadSnapshot(runId: RunId): RecoveryCandidateSnapshot {
            loadCount++
            return RecoveryCandidateSnapshot(
                bootstrap.toList(),
                units.toList(),
                publications.toList(),
            )
        }

        override fun beginNonExclusive() =
            object : RecoveryMicrofileTransaction {
                var pendingUnit: RecoveryMicrofileUnitRow? = null
                var pendingPublication: RecoveryManifestPublicationRow? = null
                var successful = false

                override fun insert(
                    unit: RecoveryMicrofileUnitRow,
                    publication: RecoveryManifestPublicationRow,
                ) {
                    pendingUnit = unit
                    pendingPublication = publication
                }

                override fun markSuccessful() {
                    successful = true
                }

                override fun end() {
                    if (failEnd) error("end")
                    if (successful) {
                        units += requireNotNull(pendingUnit)
                        publications += requireNotNull(pendingPublication)
                    }
                }
            }
    }
}
