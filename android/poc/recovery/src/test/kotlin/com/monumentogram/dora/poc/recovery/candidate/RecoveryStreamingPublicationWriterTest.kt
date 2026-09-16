@file:Suppress("WildcardImport", "LongMethod", "MagicNumber")

package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.bootstrap.*
import com.monumentogram.dora.poc.recovery.contract.*
import com.monumentogram.dora.poc.recovery.crypto.*
import com.monumentogram.dora.poc.recovery.journal.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class RecoveryStreamingPublicationWriterTest {
    @Test
    fun `K12 q2 seed returns only 8136 authenticated bytes before appended byte fails`() {
        val f = Fixture()
        val plaintext = RecoveryCampaignFixture.bytes(17, 0, 8137)
        f.open().use { writer ->
            writer.write(plaintext.copyOfRange(0, 4080))
            writer.write(plaintext.copyOfRange(4080, 8137))
            val checkpoint = writer.checkpoint()
            assertEquals(2UL, checkpoint.durableNonFinalSegmentCount)
            assertEquals(8192UL, checkpoint.streamCiphertextPrefixBytes)
            assertEquals(4056UL, checkpoint.committedEnd)
            assertEquals(8137UL, writer.acceptedEnd)
        }
        val source = f.storage.bytes("stream/stream.ct") + byteArrayOf(0x5a)
        val recovered = ByteArrayOutputStream()
        f.parseStream()
            .newDecryptingStream(
                source.inputStream(),
                StreamingAad(RecoveryCandidate.STREAM, f.run),
            )
            .use { input ->
                val buffer = ByteArray(4080)
                var first = true
                assertThrows(IOException::class.java) {
                    while (true) {
                        val count = input.read(buffer, 0, if (first) 4056 else 4080)
                        first = false
                        if (count == -1) break
                        recovered.write(buffer, 0, count)
                    }
                }
            }
        assertEquals(8136, recovered.size())
        assertArrayEquals(plaintext.copyOfRange(0, 8136), recovered.toByteArray())
    }

    @Test
    fun `real Tink open stream checkpoint authenticates exact geometry and fresh generations`() {
        val f = Fixture()
        val plaintext = ByteArray(12_217) { (it * 31 + 9).toByte() }
        val writer = f.open()
        plaintext.asList().chunked(4_080).forEach { writer.write(it.toByteArray()) }
        val first = writer.checkpoint()
        assertEquals(12_217UL, writer.acceptedEnd)
        assertEquals(3UL, first.durableNonFinalSegmentCount)
        assertEquals(12_288UL, first.streamCiphertextPrefixBytes)
        assertEquals(8_136UL, first.committedEnd)
        assertEquals(
            Sha256Value.calculate(f.storage.bytes("stream/stream.ct")),
            first.streamCiphertextPrefixSha256,
        )
        assertEquals(
            first.checkpointIdentity,
            RecoveryStreamingIdentity.checkpoint(first.identityInput()),
        )
        val decoded = f.decryptCheckpoint(first)
        assertEquals(first.committedEnd, decoded.committedEndExclusive)
        assertEquals(first.streamKeyEnvelopeSha256, decoded.streamKeyEnvelopeSha256)
        assertEquals(
            (1..13).map { "SCHK-%02d".format(it) },
            f.events.filter { it.startsWith("SCHK") },
        )

        writer.write(ByteArray(4_080))
        val second = writer.checkpoint()
        assertEquals(2UL, second.generation)
        assertEquals(first.checkpointSha256, second.previousCheckpointSha256)
        assertFalse(first.checkpointKeyEnvelopeSha256 == second.checkpointKeyEnvelopeSha256)
        writer.close()

        // This closes only the raw destination, never the Tink writer: no final segment is emitted.
        val stream =
            f.parseStream()
                .newDecryptingStream(
                    f.storage.bytes("stream/stream.ct").inputStream(),
                    StreamingAad(RecoveryCandidate.STREAM, f.run),
                )
        val recovered = ByteArray(4_056)
        assertEquals(recovered.size, stream.read(recovered))
        assertArrayEquals(plaintext.copyOfRange(0, recovered.size), recovered)
        stream.close()
        assertTrue(f.storage.handles.all { it.closed })
    }

    @Test
    fun `raw descriptor stays open across initial rename and abandon never finalizes Tink`() {
        val f = Fixture()
        val writer = f.open()
        assertEquals(
            (1..9).map { "SSET-%02d".format(it) },
            f.events.filter { it.startsWith("SSET") },
        )
        assertTrue(f.storage.renameSawOpenStream)
        writer.write(ByteArray(4_080))
        writer.write(ByteArray(4_057))
        assertEquals(8_192, f.storage.bytes("stream/stream.ct").size)
        writer.close()
        assertEquals(8_192, f.storage.bytes("stream/stream.ct").size)
        assertThrows(IllegalStateException::class.java) { writer.write(byteArrayOf(1)) }
    }

    @Test
    fun `fsync failure does not commit and poisons the publication session`() {
        val f = Fixture()
        val writer = f.open()
        writer.write(ByteArray(4_080))
        writer.write(ByteArray(4_057))
        f.storage.failFsync = true
        assertThrows(IOException::class.java) { writer.checkpoint() }
        assertTrue(f.rows.isEmpty())
        assertFalse("SCHK-02" in f.events)
        assertThrows(IllegalStateException::class.java) { writer.write(byteArrayOf(1)) }
        writer.close()
        assertTrue(f.storage.handles.all { it.closed })
    }

    @Test
    fun `bounded writes reject before accepting data and checkpoint does not invent durability`() {
        val f = Fixture()
        val writer = f.open()
        assertThrows(IllegalArgumentException::class.java) { writer.write(ByteArray(4_081)) }
        assertEquals(0UL, writer.acceptedEnd)
        val row = writer.checkpoint()
        assertEquals(0UL, row.durableNonFinalSegmentCount)
        assertEquals(Sha256Value.calculate(byteArrayOf()), row.streamCiphertextPrefixSha256)
        assertEquals(0UL, row.committedEnd)
        writer.close()
    }

    @Test
    fun `unknown commit result never emits commit event or permits another write`() {
        val f = Fixture(commitFailure = true)
        val writer = f.open()
        writer.write(ByteArray(4_080))
        assertThrows(IllegalStateException::class.java) { writer.checkpoint() }
        assertFalse("SCHK-12" in f.events)
        assertFalse("SCHK-13" in f.events)
        assertThrows(IllegalStateException::class.java) { writer.write(byteArrayOf(1)) }
        writer.close()
    }

    @Test
    fun `barrier timeout closes transaction but is not erased by successful exact readback`() {
        val f = Fixture(observerFailureStep = "SCHK-11")
        val writer = f.open()
        writer.write(ByteArray(4_080))
        assertThrows(IOException::class.java) { writer.checkpoint() }
        assertEquals(1, f.rows.size)
        assertFalse("SCHK-12" in f.events)
        assertFalse("SCHK-13" in f.events)
        assertThrows(IllegalStateException::class.java) { writer.write(byteArrayOf(1)) }
        writer.close()
    }

    private class Fixture(
        private val commitFailure: Boolean = false,
        private val observerFailureStep: String? = null,
    ) {
        val run = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")
        val storage = MemoryStorage()
        val events = mutableListOf<String>()
        val database = MemoryJournal(commitFailure)
        val rows
            get() = database.rows

        val provider = RecoveryRunAeadProvider(RecordingRunAeadBackend())
        val confirmation = KeyConfirmationValue(RecoveryCandidate.STREAM, run)
        val capability = authorize(confirmation, provider)

        fun open() =
            RecoveryStreamingPublicationWriter.open(
                confirmation = confirmation,
                capability = capability,
                storage = storage,
                openRunAead = provider::openExisting,
                hashPrefix = { end ->
                    Sha256Value.calculate(storage.bytes("stream/stream.ct").copyOf(end.toInt()))
                },
                commitCheckpoint = RecoveryStreamingCheckpointCommitAdapter(database),
                observer = { event ->
                    events += event
                    if (event == observerFailureStep) throw IOException("external barrier timeout")
                },
            )

        fun parseStream() =
            RecoveryTinkRuntime.parseEncryptedStreamingKeyset(
                storage.bytes("key-envelopes/stream.ks"),
                provider.openExisting(run),
                streamAad(run),
            )

        fun decryptCheckpoint(row: RecoveryStreamingCheckpointRow): RecoveryCheckpoint {
            val aad =
                KeyEnvelopeAad(
                    RecoveryCandidate.STREAM,
                    run,
                    KeyEnvelopeTargetKind.CHECKPOINT,
                    row.generation,
                    KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
                    0UL,
                    row.committedEnd,
                    0UL,
                    row.previousCheckpointSha256,
                )
            val key =
                RecoveryTinkRuntime.parseEncryptedAeadKeyset(
                    storage.bytes(row.checkpointKeyEnvelopeRelativeName),
                    provider.openExisting(run),
                    aad,
                )
            val publication =
                PublicationAad(
                    RecoveryCandidate.STREAM,
                    run,
                    PublicationKind.CHECKPOINT,
                    row.generation,
                    if (row.committedEnd == 0UL) PublicationAad.EMPTY_TERMINAL_UNIT_INDEX
                    else row.durableNonFinalSegmentCount - 1UL,
                    row.committedEnd,
                    row.previousCheckpointSha256,
                )
            return RecoveryCheckpointCodec.decode(
                key.decryptPublication(storage.bytes(row.checkpointRelativeName), publication)
            )
        }
    }

    private class MemoryJournal(private val failEnd: Boolean) : RecoveryStreamingJournalDatabase {
        val rows = mutableListOf<RecoveryStreamingCheckpointRow>()

        override fun checkpoints(runId: RunId) = rows.filter { it.runId == runId }

        override fun outcomeById(id: Sha256Value) = emptyList<RecoveryStreamingOutcomeRow>()

        override fun outcomesByWitness(
            runId: RunId,
            checkpointIdentity: Sha256Value,
            witnessId: Sha256Value,
        ) = emptyList<RecoveryStreamingOutcomeRow>()

        override fun rangesByOutcome(outcomeId: Sha256Value) =
            emptyList<RecoveryStreamingRangeRow>()

        override fun activeRanges(runId: RunId, source: String) =
            emptyList<RecoveryStreamingRangeRow>()

        override fun rangeByIntentId(id: Sha256Value) = emptyList<RecoveryStreamingRangeRow>()

        override fun rangesBySourceTuple(row: RecoveryStreamingRangeRow) =
            emptyList<RecoveryStreamingRangeRow>()

        override fun beginTransactionNonExclusive() =
            object : RecoveryStreamingJournalTransaction {
                private var staged: RecoveryStreamingCheckpointRow? = null
                private var successful = false
                override val provenRolledBack = false

                override fun insertCheckpoint(row: RecoveryStreamingCheckpointRow) {
                    staged = row
                }

                override fun insertOutcome(row: RecoveryStreamingOutcomeRow) =
                    error("unexpected outcome")

                override fun insertRange(row: RecoveryStreamingRangeRow) = error("unexpected range")

                override fun setSuccessful() {
                    successful = true
                }

                override fun end() {
                    if (failEnd) throw IOException("ambiguous transaction end")
                    if (successful) rows += requireNotNull(staged)
                }
            }
    }

    private class MemoryStorage : RecoveryCandidateStorage {
        class Handle(
            val bytes: ByteArrayOutputStream = ByteArrayOutputStream(),
            var closed: Boolean = false,
        ) : CandidateWriteHandle

        val files = mutableMapOf<String, Handle>()
        val handles = mutableListOf<Handle>()
        var failFsync = false
        var renameSawOpenStream = false

        fun bytes(name: String) = files.getValue(name).bytes.toByteArray()

        override fun openExclusiveTemp(
            runId: RunId,
            temporaryRelativeName: String,
        ): CandidateWriteHandle {
            check(
                temporaryRelativeName !in files &&
                    temporaryRelativeName.removeSuffix(".tmp") !in files
            )
            return Handle().also {
                files[temporaryRelativeName] = it
                handles += it
            }
        }

        override fun write(
            handle: CandidateWriteHandle,
            bytes: ByteArray,
            offset: Int,
            count: Int,
        ): Int {
            val h = handle as Handle
            check(!h.closed)
            val written = minOf(count, 137)
            h.bytes.write(bytes, offset, written)
            return written
        }

        override fun fsync(handle: CandidateWriteHandle) {
            check(!(handle as Handle).closed)
            if (failFsync) throw IOException("injected fsync failure")
        }

        override fun close(handle: CandidateWriteHandle) {
            check(!(handle as Handle).closed)
            handle.closed = true
        }

        override fun finalExists(runId: RunId, finalRelativeName: String) =
            finalRelativeName in files

        override fun renameTempToFinal(
            runId: RunId,
            temporaryRelativeName: String,
            finalRelativeName: String,
        ) {
            check(finalRelativeName !in files)
            val handle = files.remove(temporaryRelativeName)!!
            if (finalRelativeName == "stream/stream.ct") renameSawOpenStream = !handle.closed
            files[finalRelativeName] = handle
        }

        override fun fsyncParent(runId: RunId, finalRelativeName: String) = Unit
    }

    private companion object {
        fun streamAad(run: RunId) =
            KeyEnvelopeAad(
                RecoveryCandidate.STREAM,
                run,
                KeyEnvelopeTargetKind.STREAM,
                1UL,
                KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
                0UL,
                RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN,
                0UL,
                Sha256Value.ZERO,
            )

        fun authorize(
            value: KeyConfirmationValue,
            provider: RecoveryRunAeadProvider,
        ): BootstrapPublicationCapability {
            val crypto =
                object : RecoveryBootstrapCrypto {
                    override fun aliasExists(runId: RunId) = false

                    override fun createNewAlias(runId: RunId) =
                        BootstrapAliasCreation.Created(provider.createNew(runId))

                    override fun encryptConfirmation(
                        runAead: RecoveryRunAead,
                        value: KeyConfirmationValue,
                    ) = runAead.encryptKeyConfirmation(value)
                }
            val storage =
                object : RecoveryBootstrapStorage {
                    override fun inspectNamespaces(runId: RunId) = BootstrapNamespaceState()

                    override fun openExclusiveConfirmationTemp(runId: RunId) =
                        object : BootstrapWriteHandle {}

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
            return (RecoveryKeyBootstrapController(
                        crypto,
                        storage,
                        journal,
                        BootstrapEvidenceSink {},
                    )
                    .bootstrap(value) as BootstrapResult.Committed)
                .publicationCapability
        }
    }
}
