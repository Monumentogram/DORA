@file:Suppress("LongMethod", "LargeClass")

package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAad
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeTargetKind
import com.monumentogram.dora.poc.recovery.contract.PublicationAad
import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryCheckpoint
import com.monumentogram.dora.poc.recovery.contract.RecoveryCheckpointCodec
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
import com.monumentogram.dora.poc.recovery.contract.StreamDecision
import com.monumentogram.dora.poc.recovery.contract.StreamSourceMatch
import com.monumentogram.dora.poc.recovery.contract.StreamingAad
import com.monumentogram.dora.poc.recovery.coordination.RecoveryRunSingleWriterGuard
import com.monumentogram.dora.poc.recovery.coordination.RecoveryRunWriterLease
import com.monumentogram.dora.poc.recovery.crypto.RecordingRunAeadBackend
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import com.monumentogram.dora.poc.recovery.crypto.RecoveryTinkRuntime
import com.monumentogram.dora.poc.recovery.storage.RecoveryOpenedStreamingSource
import com.monumentogram.dora.poc.recovery.storage.RecoveryReplayHashOnlyResult
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamOpenRequest
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamReplayRequest
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamingReplayAccess
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamingSource
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamingSourceLeaseAccess
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryStreamPrefixControllerCryptoTest {
    @Test
    fun `historical sealed truncated fatal is replayed without reclassification or decrypt`() {
        val f = tinkFixture()
        val after = f.streamCiphertext.copyOf(f.streamCiphertext.size - 2_000)
        val old =
            RecoveryStreamingIntentBuilder.buildOutcome(
                RecoveryStreamingValidatedIntentFacts(
                    f.witness,
                    after.size.toULong(),
                    Sha256Value.calculate(after),
                    false,
                    false,
                    null,
                )
            )
        val range = RecoveryStreamingRangeRow.exact(old, Sha256Value.calculate(after))
        val events = mutableListOf<String>()
        val journal =
            ControllerJournal(events).apply {
                checkpoints = listOf(f.row)
                existingOutcome = old
                existingRange = range
                active = listOf(range)
            }
        val source = ReplayControllerSource(events, old, range.rangeSha256)
        val controller =
            RecoveryStreamingReconciliationController(
                journal,
                source,
                RecoveryRunSingleWriterGuard { RecoveryRunWriterLease {} },
                RecoveryStreamingCheckpointAuthenticator { _, _ ->
                    error("Historical sealed outcome must not decrypt")
                },
                RecoveryStreamingEvidenceSink {},
            )
        val result =
            controller.recover(
                RecoveryStreamingControllerRequest(
                    f.witness,
                    RecoveryStreamingIntentBuilder.RecoveryStreamingOracle.from(
                        f.witness,
                        f.plaintext,
                    ),
                )
            )
        assertTrue(result is RecoveryStreamingReconciliationResult.Fatal)
        assertEquals(old, journal.existingOutcome)
        assertEquals(StreamDecision.FATAL, journal.existingOutcome!!.decision)
        assertEquals(0, journal.persistCalls)
        assertEquals(0, source.normalOpens)
        assertEquals(1, source.replayOpens)
    }

    @Test
    fun `real authenticated checkpoint and public Tink controller retain surviving prefix and exact replay`() {
        val f = tinkFixture()
        assertEquals(479_232, f.streamCiphertext.size)
        val after = f.streamCiphertext.copyOf(f.streamCiphertext.size - 2_000)
        val events = mutableListOf<String>()
        val journal =
            ControllerJournal(events).apply {
                checkpoints = listOf(f.row)
                persistBehavior = { attempt ->
                    existingOutcome = attempt.outcome
                    existingRange = attempt.range
                    RecoveryStreamingJournalResult.Receipt(
                        attempt.outcome.outcomeId,
                        attempt.range?.rangeIntentId,
                        false,
                    )
                }
            }
        val source = FreshControllerSource(events, after)
        val artifacts =
            mapOf(
                f.row.checkpointRelativeName to f.checkpointCiphertext,
                f.row.checkpointKeyEnvelopeRelativeName to f.checkpointEnvelope,
                f.row.streamKeyEnvelopeRelativeName to f.streamEnvelope,
            )
        val authenticator =
            RecoveryStreamingCheckpointAuthenticatorAdapter(
                RecoveryStreamingPrerequisiteSource { _, name, _ ->
                    artifacts[name]?.let { RecoveryArtifactBytes(name, it) }
                },
                RecoveryStreamingTinkPrerequisiteCrypto(f.runProvider::openExisting),
            )
        val request =
            RecoveryStreamingControllerRequest(
                f.witness,
                RecoveryStreamingIntentBuilder.RecoveryStreamingOracle.from(f.witness, f.plaintext),
            )
        val controller =
            RecoveryStreamingReconciliationController(
                journal,
                source,
                RecoveryRunSingleWriterGuard { RecoveryRunWriterLease {} },
                authenticator,
                RecoveryStreamingEvidenceSink {},
            )
        val result = controller.recover(request)
        assertTrue(
            result.toString(),
            result is RecoveryStreamingReconciliationResult.PersistedValid,
        )
        val outcome = requireNotNull(journal.existingOutcome)
        assertEquals(473_256UL, outcome.recoveredEnd)
        assertEquals(6_744UL, outcome.tailLossBytes)
        assertEquals(479_232UL, outcome.preFaultSourceBytes)
        assertEquals(Sha256Value.calculate(f.streamCiphertext), outcome.preFaultSourceSha256)
        assertEquals(
            StreamSourceMatch.VERIFIED_SURVIVING_CHECKPOINT_PREFIX,
            outcome.preFaultSourceMatch,
        )
        assertEquals(475_136UL, journal.existingRange!!.rangeStart)
        assertEquals(477_232UL, journal.existingRange!!.rangeEnd)
        assertFalse(events.contains("hash-479232"))
        val replaySource =
            ReplayControllerSource(events, outcome, journal.existingRange!!.rangeSha256)
        val replayController =
            RecoveryStreamingReconciliationController(
                journal,
                replaySource,
                RecoveryRunSingleWriterGuard { RecoveryRunWriterLease {} },
                RecoveryStreamingCheckpointAuthenticator { _, _ ->
                    error("Sealed replay cannot decrypt")
                },
                RecoveryStreamingEvidenceSink {},
            )
        assertTrue(
            replayController.recover(request)
                is RecoveryStreamingReconciliationResult.PersistedValid
        )
        assertEquals(1, journal.persistCalls)
        assertEquals(0, replaySource.normalOpens)
        assertEquals(1, replaySource.replayOpens)
    }

    private fun tinkFixture(
        checkpointEnvelopeBytesDelta: ULong = 0UL,
        generation: ULong = 1UL,
        previous: Sha256Value = Sha256Value.ZERO,
        sameStream: TinkFixture? = null,
        plaintextBytes: Int = 480_000,
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
                .also { writer ->
                    plaintext.asList().chunked(4_080).forEach { writer.write(it.toByteArray()) }
                }
        val streamCiphertext = sameStream?.streamCiphertext ?: streamDestination.toByteArray()
        val checkpoint =
            RecoveryCheckpoint(
                RecoveryCandidate.STREAM,
                runId,
                generation,
                previous,
                116UL,
                475_136UL,
                469_176UL,
                streamEnvelope.size.toULong() + checkpointEnvelopeBytesDelta,
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
                469_176UL,
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
                115UL,
                469_176UL,
                previous,
            )
        val checkpointCiphertext =
            checkpointKeyset.encryptPublication(
                RecoveryCheckpointCodec.encode(checkpoint),
                checkpointPublicationAad,
            )
        val input =
            RecoveryStreamingCheckpointIdentityInput(
                runId,
                generation,
                116UL,
                475_136UL,
                Sha256Value.calculate(streamCiphertext.copyOfRange(0, 475_136)),
                469_176UL,
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
            controllerFixtureFor(row, plaintext, streamCiphertext),
        )
    }

    private fun controllerFixtureFor(
        row: RecoveryStreamingCheckpointRow,
        plaintext: ByteArray,
        fullCiphertext: ByteArray,
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
                fullCiphertext.size.toULong(),
                Sha256Value.calculate(fullCiphertext),
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

    private class ControllerJournal(private val events: MutableList<String>) :
        RecoveryStreamingJournal {
        var checkpoints = emptyList<RecoveryStreamingCheckpointRow>()
        var existingOutcome: RecoveryStreamingOutcomeRow? = null
        var parentOutcome: RecoveryStreamingOutcomeRow? = null
        var existingRange: RecoveryStreamingRangeRow? = null
        var active = emptyList<RecoveryStreamingRangeRow>()
        var persistCalls = 0
        var lastAttempt: RecoveryStreamingOutcomeAttempt? = null
        var persistBehavior: (RecoveryStreamingOutcomeAttempt) -> RecoveryStreamingJournalResult = {
            error("persistence must not run")
        }

        override fun checkpointChain(runId: RunId) =
            RecoveryStreamingJournalReadResult.Value(checkpoints).also {
                events += "checkpoint-chain"
            }

        override fun outcomeById(outcomeId: Sha256Value) =
            RecoveryStreamingJournalReadResult.Value(
                    (parentOutcome ?: existingOutcome)?.takeIf { it.outcomeId == outcomeId }
                )
                .also { events += "outcome-id" }

        override fun outcomeByWitness(
            runId: RunId,
            checkpointIdentity: Sha256Value,
            witnessId: Sha256Value,
        ) =
            RecoveryStreamingJournalReadResult.Value(existingOutcome).also {
                events += "outcome-witness"
            }

        override fun rangeByOutcome(outcomeId: Sha256Value) =
            RecoveryStreamingJournalReadResult.Value(existingRange).also {
                events += "range-outcome"
            }

        override fun activeRanges(runId: RunId, sourceRelativeName: String) =
            RecoveryStreamingJournalReadResult.Value(active).also { events += "active-ranges" }

        override fun insertCheckpoint(row: RecoveryStreamingCheckpointRow) =
            error("checkpoint insertion is forbidden")

        override fun persistOutcome(
            attempt: RecoveryStreamingOutcomeAttempt
        ): RecoveryStreamingJournalResult {
            persistCalls += 1
            lastAttempt = attempt
            events += "persist"
            return persistBehavior(attempt)
        }
    }

    private class ReplayControllerSource(
        private val events: MutableList<String>,
        private val outcome: RecoveryStreamingOutcomeRow,
        private val retainedRangeSha256: Sha256Value? = null,
    ) : RecoveryStreamingSource {
        var normalOpens = 0
        var replayOpens = 0

        override fun <T> withSource(
            access: RecoveryStreamingSourceLeaseAccess,
            request: RecoveryStreamOpenRequest,
            block: (RecoveryOpenedStreamingSource) -> T,
        ): T {
            normalOpens += 1
            events += "normal-source-open"
            error("replay must not use normal source")
        }

        override fun verifyReplayHashOnly(
            access: RecoveryStreamingReplayAccess,
            request: RecoveryStreamReplayRequest,
        ): RecoveryReplayHashOnlyResult =
            access.withBoundTo(request.runId) {
                replayOpens += 1
                events += "replay-source-open"
                RecoveryReplayHashOnlyResult.ExactStoredSourceMetadata(
                    outcome.observedSourceBytes,
                    outcome.observedSourceSha256,
                    retainedRangeSha256,
                )
            }
    }

    private class FreshControllerSource(
        private val events: MutableList<String>,
        sourceBytes: ByteArray,
    ) : RecoveryStreamingSource {
        private val bytes = sourceBytes.copyOf()
        var normalOpens = 0

        override fun <T> withSource(
            access: RecoveryStreamingSourceLeaseAccess,
            request: RecoveryStreamOpenRequest,
            block: (RecoveryOpenedStreamingSource) -> T,
        ): T =
            access.withBoundTo(request.runId) {
                normalOpens += 1
                events += "source-open"
                try {
                    block(
                        object : RecoveryOpenedStreamingSource {
                            override val observedBytes = bytes.size.toULong()

                            override fun sha256Prefix(endExclusive: ULong): Sha256Value {
                                events += "hash-$endExclusive"
                                return Sha256Value.calculate(
                                    bytes.copyOfRange(0, endExclusive.toInt())
                                )
                            }

                            override fun sha256Range(
                                startInclusive: ULong,
                                endExclusive: ULong,
                            ): Sha256Value {
                                events += "range-$startInclusive-$endExclusive"
                                return Sha256Value.calculate(
                                    bytes.copyOfRange(
                                        startInclusive.toInt(),
                                        endExclusive.toInt(),
                                    )
                                )
                            }

                            override fun boundedInputStream() = bytes.inputStream()
                        }
                    )
                } finally {
                    events += "source-close"
                }
            }

        override fun verifyReplayHashOnly(
            access: RecoveryStreamingReplayAccess,
            request: RecoveryStreamReplayRequest,
        ): RecoveryReplayHashOnlyResult = error("fresh path must not replay")
    }
}
