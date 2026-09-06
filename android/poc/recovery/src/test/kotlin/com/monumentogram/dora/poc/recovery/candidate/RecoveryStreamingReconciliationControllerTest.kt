package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointIdentityInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingExistingEvidence
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournal
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalClassification
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalReadResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeAttempt
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRangeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingWitnessInput
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.contract.StreamDecision
import com.monumentogram.dora.poc.recovery.contract.StreamDiagnosticClassification
import com.monumentogram.dora.poc.recovery.contract.StreamSemanticOutcome
import com.monumentogram.dora.poc.recovery.contract.StreamTerminal
import com.monumentogram.dora.poc.recovery.coordination.RecoveryRunSingleWriterGuard
import com.monumentogram.dora.poc.recovery.coordination.RecoveryRunWriterLease
import com.monumentogram.dora.poc.recovery.storage.RecoveryOpenedStreamingSource
import com.monumentogram.dora.poc.recovery.storage.RecoveryReplayHashOnlyResult
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamOpenRequest
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamReplayRequest
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamingReplayAccess
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamingSource
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamingSourceException
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamingSourceFailure
import com.monumentogram.dora.poc.recovery.storage.RecoveryStreamingSourceLeaseAccess
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryStreamingReconciliationControllerTest {
    @Test
    fun `v08 result vocabulary and all twenty outward mappings are closed`() {
        assertEquals(
            listOf(
                "LEASE",
                "PREREQUISITE",
                "SOURCE_PROOF",
                "RANGE_ADMISSION",
                "STREAM_READ",
                "JOURNAL",
            ),
            RecoveryStreamingResultStage.entries.map { it.name },
        )
        assertEquals(
            listOf("NONE", "IO", "CRYPTO", "SQLITE"),
            RecoveryStreamingSafeExceptionType.entries.map { it.name },
        )

        val expected =
            listOf(
                fatal("PREREQUISITE", "STREAM_CHECKPOINT_MISSING"),
                fatal("PREREQUISITE", "STREAM_CHECKPOINT_STRUCTURAL"),
                fatal("PREREQUISITE", "STREAM_CHECKPOINT_AUTHENTICATION_REJECTED"),
                retry("PREREQUISITE", "STREAM_CHECKPOINT_AUTHENTICATION_OPERATIONAL", "CRYPTO"),
                fatal("PREREQUISITE", "STREAM_CHECKPOINT_SPLIT_BRAIN"),
                fatal("PREREQUISITE", "STREAM_SOURCE_WITNESS_MISSING"),
                fatal("PREREQUISITE", "UNSAFE_PATH"),
                fatal("SOURCE_PROOF", "STREAM_SOURCE_IDENTITY_CHANGED"),
                rejected("SOURCE_PROOF", "STREAM_SOURCE_EXTENT_LIMIT_EXCEEDED"),
                retry("LEASE", "RUN_LEASE_CONTENDED", "NONE"),
                fatal("RANGE_ADMISSION", "STREAM_ACTIVE_RANGE_DENIED"),
                retry("STREAM_READ", "STREAM_ZERO_PROGRESS", "NONE"),
                fatal("STREAM_READ", "STREAM_READ_CROSSES_ACCEPTED_END"),
                "RETRY|STREAM_READ|STREAM_PUBLIC_READ_OPERATIONAL|CRYPTO,IO",
                fatal("JOURNAL", "JOURNAL_STRUCTURAL"),
                fatal("JOURNAL", "JOURNAL_ATTEMPT_CONFLICT"),
                fatal("JOURNAL", "STREAM_RANGE_QUARANTINE_COLLISION"),
                retry("SOURCE_PROOF", "ARTIFACT_IO_BEFORE_EXACT_SOURCE_HASH", "IO"),
                retry("JOURNAL", "JOURNAL_OPERATIONAL", "SQLITE"),
                retry("JOURNAL", "JOURNAL_COMMIT_STATE_UNRESOLVED", "SQLITE"),
            )

        assertEquals(20, RecoveryStreamingResultClassification.entries.size)
        assertEquals(expected, RecoveryStreamingResultMapping.entries.map(::render))
        assertEquals(
            RecoveryStreamingResultClassification.entries.toSet(),
            RecoveryStreamingResultMapping.entries.map { it.classification }.toSet(),
        )
    }

    @Test
    fun `mapping constructor rejects every unlisted stage exception and unknown spelling`() {
        assertEquals(
            RecoveryStreamingResultDisposition.RETRY,
            RecoveryStreamingResultMapping.require(
                    RecoveryStreamingResultStage.STREAM_READ,
                    RecoveryStreamingResultClassification.STREAM_PUBLIC_READ_OPERATIONAL,
                    RecoveryStreamingSafeExceptionType.IO,
                )
                .disposition,
        )
        assertEquals(
            RecoveryStreamingResultDisposition.FATAL,
            RecoveryStreamingResultMapping.require(
                    RecoveryStreamingResultStage.PREREQUISITE,
                    RecoveryStreamingResultClassification.STREAM_CHECKPOINT_MISSING,
                    null,
                )
                .disposition,
        )

        assertThrows(IllegalArgumentException::class.java) {
            RecoveryStreamingResultMapping.require(
                RecoveryStreamingResultStage.JOURNAL,
                RecoveryStreamingResultClassification.JOURNAL_OPERATIONAL,
                RecoveryStreamingSafeExceptionType.NONE,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryStreamingResultMapping.require(
                RecoveryStreamingResultStage.SOURCE_PROOF,
                RecoveryStreamingResultClassification.STREAM_SOURCE_EXTENT_LIMIT_EXCEEDED,
                RecoveryStreamingSafeExceptionType.IO,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryStreamingResultMapping.require(
                RecoveryStreamingResultStage.STREAM_READ,
                RecoveryStreamingResultClassification.STREAM_ZERO_PROGRESS,
                null,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryStreamingResultStage.valueOf("STREAM_PAYLOAD_DECRYPT")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryStreamingResultClassification.valueOf(
                "JOURNAL_AMBIGUOUS_COMMIT_WITH_NO_EXACT_INTENDED_STATE"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryStreamingResultClassification.valueOf("UNKNOWN")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryStreamingSafeExceptionType.valueOf("RUNTIME")
        }
    }

    @Test
    fun `sealed result and receipt support values remain exact`() {
        assertEquals(
            setOf("PersistedValid", "Retry", "Rejected", "Fatal"),
            RecoveryStreamingReconciliationResult::class
                .java
                .declaredClasses
                .filter(RecoveryStreamingReconciliationResult::class.java::isAssignableFrom)
                .map { it.simpleName }
                .toSet(),
        )
        assertEquals(
            listOf(
                "NONE",
                "PUBLIC_STREAM_CLOSE_FAILED",
                "SOURCE_DESCRIPTOR_CLOSE_FAILED",
                "PUBLIC_STREAM_AND_SOURCE_DESCRIPTOR_CLOSE_FAILED",
            ),
            RecoveryStreamingPostReceiptCleanup.entries.map { it.name },
        )
        assertEquals(
            listOf("DELIVERED", "PENDING"),
            RecoveryStreamingEvidenceDelivery.entries.map { it.name },
        )
        assertEquals(
            listOf("STREAM_CHECKPOINT", "STREAM_OUTCOME", "STREAM_RANGE"),
            RecoveryStreamingExistingRecordKind.entries.map { it.name },
        )

        val receipt =
            RecoveryStreamingPersistenceReceipt(
                outcomeId = sha(1),
                optionalRangeIntentId = sha(2),
                replayed = true,
                postReceiptCleanup = RecoveryStreamingPostReceiptCleanup.PUBLIC_STREAM_CLOSE_FAILED,
                evidenceDelivery = RecoveryStreamingEvidenceDelivery.PENDING,
            )
        assertEquals(sha(1), receipt.outcomeId)
        assertEquals(sha(2), receipt.optionalRangeIntentId)
        assertTrue(receipt.replayed)
    }

    @Test
    fun `non persistable result constructors enforce exact mapping and attempted id rules`() {
        val retry =
            RecoveryStreamingReconciliationResult.Retry.of(
                RecoveryStreamingResultStage.JOURNAL,
                RecoveryStreamingResultClassification.JOURNAL_COMMIT_STATE_UNRESOLVED,
                RecoveryStreamingSafeExceptionType.SQLITE,
                attemptedOutcomeId = sha(3),
                attemptedRangeId = sha(4),
            )
        assertEquals(sha(3), retry.attemptedOutcomeId)
        assertEquals(sha(4), retry.attemptedRangeId)
        assertTrue(retry.existingEvidenceReferences.isEmpty())

        assertThrows(IllegalArgumentException::class.java) {
            RecoveryStreamingReconciliationResult.Retry.of(
                RecoveryStreamingResultStage.JOURNAL,
                RecoveryStreamingResultClassification.JOURNAL_OPERATIONAL,
                RecoveryStreamingSafeExceptionType.SQLITE,
                attemptedOutcomeId = sha(3),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryStreamingReconciliationResult.Fatal.nonPersistable(
                RecoveryStreamingResultStage.JOURNAL,
                RecoveryStreamingResultClassification.JOURNAL_OPERATIONAL,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryStreamingReconciliationResult.Rejected.nonPersistable(
                RecoveryStreamingResultStage.SOURCE_PROOF,
                RecoveryStreamingResultClassification.STREAM_SOURCE_IDENTITY_CHANGED,
            )
        }
    }

    @Test
    fun `strict decoded references are deduplicated ordered and immutable`() {
        val high = sha(0xff)
        val low = sha(0)
        val references =
            RecoveryStreamingExistingEvidenceReferences.forClassification(
                RecoveryStreamingResultClassification.STREAM_RANGE_QUARANTINE_COLLISION,
                listOf(
                    RecoveryStreamingExistingEvidence.Range(low),
                    RecoveryStreamingExistingEvidence.Outcome(high),
                    RecoveryStreamingExistingEvidence.Range(low),
                    RecoveryStreamingExistingEvidence.Outcome(low),
                ),
            )

        assertEquals(
            listOf(
                RecoveryStreamingExistingRecordKind.STREAM_OUTCOME to low,
                RecoveryStreamingExistingRecordKind.STREAM_OUTCOME to high,
                RecoveryStreamingExistingRecordKind.STREAM_RANGE to low,
            ),
            references.map { it.recordKind to it.existingId },
        )
        assertTrue(references.all { it.existingId == it.existingIdentitySha256 })
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (references as java.util.List<RecoveryStreamingExistingEvidenceReference>).add(
                references.first()
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryStreamingExistingEvidenceReferences.forClassification(
                RecoveryStreamingResultClassification.STREAM_CHECKPOINT_SPLIT_BRAIN,
                listOf(RecoveryStreamingExistingEvidence.Outcome(low)),
            )
        }
        assertFalse(
            RecoveryStreamingExistingEvidenceReferences.forClassification(
                    RecoveryStreamingResultClassification.JOURNAL_STRUCTURAL,
                    emptyList(),
                )
                .isNotEmpty()
        )
    }

    @Test
    fun `intent core constructs the exact eight durable cells`() {
        val cases =
            listOf(
                intentFacts(observedEnd = 8_191UL) to
                    (StreamDecision.FATAL to
                        StreamDiagnosticClassification.STREAM_SOURCE_TRUNCATED),
                intentFacts(preFaultEnd = 4_096UL) to
                    (StreamDecision.FATAL to
                        StreamDiagnosticClassification.STREAM_CHECKPOINT_PREFIX_OUTSIDE_WITNESS),
                intentFacts(checkpointPrefixMatches = false) to
                    (StreamDecision.FATAL to
                        StreamDiagnosticClassification.STREAM_SOURCE_PREFIX_IDENTITY_MISMATCH),
                intentFacts(completed = mismatchRead(4_056UL)) to
                    (StreamDecision.FATAL to
                        StreamDiagnosticClassification.STREAM_RETURNED_BYTE_ORACLE_MISMATCH),
                intentFacts(completed = equalRead(0UL, StreamTerminal.AUTHENTICATED_EOF)) to
                    (StreamDecision.FATAL to
                        StreamDiagnosticClassification.STREAM_RECOVERED_BELOW_CHECKPOINT),
                intentFacts(completed = equalRead(4_057UL)) to
                    (StreamDecision.FATAL to
                        StreamDiagnosticClassification.STREAM_REMAINDER_BOUNDARY_UNPROVEN),
                intentFacts(
                    prefixBytes = 4_096UL,
                    committedEnd = 0UL,
                    acceptedEnd = 8_161UL,
                    preFaultEnd = 4_096UL,
                    observedEnd = 4_097UL,
                    completed = equalRead(0UL),
                ) to
                    (StreamDecision.REJECTED to
                        StreamDiagnosticClassification.STREAM_TAIL_BOUND_EXCEEDED),
                intentFacts(completed = equalRead(8_136UL)) to
                    (StreamDecision.VALID to StreamDiagnosticClassification.NONE),
            )

        cases.forEach { (facts, expected) ->
            val outcome = RecoveryStreamingIntentBuilder.buildOutcome(facts)
            assertEquals(expected.first, outcome.decision)
            assertEquals(expected.second, outcome.diagnosticClassification)
            assertEquals(
                expected.first == StreamDecision.VALID,
                outcome.recoveredEnd != null,
            )
        }
    }

    @Test
    fun `intent core preserves precedence range boundaries and K12`() {
        val preOverlap =
            intentFacts(
                preFaultEnd = 9_000UL,
                observedEnd = 8_000UL,
                checkpointPrefixMatches = false,
                preFaultPrefixMatches = false,
            )
        assertEquals(
            StreamDiagnosticClassification.STREAM_SOURCE_TRUNCATED,
            RecoveryStreamingIntentBuilder.buildOutcome(preOverlap).diagnosticClassification,
        )

        val postOverlap =
            intentFacts(
                acceptedEnd = 20_000UL,
                completed = mismatchRead(1UL),
            )
        assertEquals(
            StreamDiagnosticClassification.STREAM_RETURNED_BYTE_ORACLE_MISMATCH,
            RecoveryStreamingIntentBuilder.buildOutcome(postOverlap).diagnosticClassification,
        )

        val atBound =
            RecoveryStreamingIntentBuilder.buildOutcome(
                intentFacts(
                    prefixBytes = 4_096UL,
                    committedEnd = 0UL,
                    acceptedEnd = 8_160UL,
                    preFaultEnd = 4_096UL,
                    observedEnd = 4_097UL,
                    completed = equalRead(0UL),
                )
            )
        val overBound =
            RecoveryStreamingIntentBuilder.buildOutcome(
                intentFacts(
                    prefixBytes = 4_096UL,
                    committedEnd = 0UL,
                    acceptedEnd = 8_161UL,
                    preFaultEnd = 4_096UL,
                    observedEnd = 4_097UL,
                    completed = equalRead(0UL),
                )
            )
        assertEquals(StreamDecision.VALID, atBound.decision)
        assertEquals(StreamDecision.REJECTED, overBound.decision)

        val boundaryEqualsObserved =
            RecoveryStreamingIntentBuilder.buildOutcome(
                intentFacts(
                    prefixBytes = 4_096UL,
                    committedEnd = 0UL,
                    acceptedEnd = 4_056UL,
                    preFaultEnd = 4_096UL,
                    observedEnd = 4_096UL,
                    completed = equalRead(4_056UL),
                )
            )
        assertEquals(null, boundaryEqualsObserved.requiredRangeStart)

        val eof =
            RecoveryStreamingIntentBuilder.buildOutcome(
                intentFacts(completed = equalRead(8_136UL, StreamTerminal.AUTHENTICATED_EOF))
            )
        assertEquals(null, eof.requiredRangeStart)

        val k12 =
            RecoveryStreamingIntentBuilder.buildOutcome(intentFacts(completed = equalRead(8_136UL)))
        assertEquals(StreamDecision.VALID, k12.decision)
        assertEquals(8_136UL, k12.recoveredEnd)
        assertEquals(8_192UL, k12.remainderBoundaryBytes)
        assertEquals(8_192UL, k12.requiredRangeStart)
        assertEquals(8_193UL, k12.observedSourceBytes)
    }

    @Test
    fun `intent facts reject inconsistent witness and incomplete mismatch tuple`() {
        val valid = intentFacts(completed = equalRead(8_136UL))
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(witness = valid.witness.copy(controllerSnapshotSha256 = sha(99)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            mismatchRead(4_056UL).copy(expectedOracleByte = null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryStreamingIntentBuilder.buildOutcome(valid.copy(completed = equalRead(8_138UL)))
        }
    }

    @Test
    fun `public read loop keeps exact requests and counts only completed positive returns`() {
        val oracleBytes = ByteArray(8_136) { ((it * 17 + 3) and 0xff).toByte() }
        val stream =
            ScriptedPublicRead(
                ReadStep.Bytes(oracleBytes.copyOfRange(0, 4_056)),
                ReadStep.Bytes(oracleBytes.copyOfRange(4_056, 8_136)),
                ReadStep.Eof,
            )

        val result =
            RecoveryStreamingIntentBuilder.RecoveryStreamingReadLoop.read(
                stream,
                RecoveryStreamingIntentBuilder.RecoveryStreamingOracle.from(
                    readWitness(oracleBytes),
                    oracleBytes,
                ),
            )

        assertEquals(listOf(4_056, 4_080, 4_080), stream.requests)
        val completed =
            result as RecoveryStreamingIntentBuilder.RecoveryStreamingReadExecution.Completed
        assertEquals(8_136UL, completed.facts.candidateEnd)
        assertEquals(StreamTerminal.AUTHENTICATED_EOF, completed.facts.terminal)
        assertEquals(Sha256Value.calculate(oracleBytes), completed.facts.completedPlaintextSha256)
    }

    @Test
    fun `zero progress and accepted end crossing stop without a later read`() {
        val zero = ScriptedPublicRead(ReadStep.Zero, ReadStep.Eof)
        val zeroResult =
            RecoveryStreamingIntentBuilder.RecoveryStreamingReadLoop.read(
                zero,
                RecoveryStreamingIntentBuilder.RecoveryStreamingOracle.from(
                    readWitness(ByteArray(10)),
                    ByteArray(10),
                ),
            )
        val zeroFailure =
            zeroResult as RecoveryStreamingIntentBuilder.RecoveryStreamingReadExecution.Failed
        assertEquals(
            RecoveryStreamingResultClassification.STREAM_ZERO_PROGRESS,
            (zeroFailure.result as RecoveryStreamingReconciliationResult.Retry).classification,
        )
        assertEquals(listOf(4_056), zero.requests)

        val crossingBytes = ByteArray(101) { 7 }
        val crossing = ScriptedPublicRead(ReadStep.Bytes(crossingBytes), ReadStep.Eof)
        val crossingResult =
            RecoveryStreamingIntentBuilder.RecoveryStreamingReadLoop.read(
                crossing,
                RecoveryStreamingIntentBuilder.RecoveryStreamingOracle.from(
                    readWitness(ByteArray(100)),
                    ByteArray(100),
                ),
            )
        val crossingFailure =
            crossingResult as RecoveryStreamingIntentBuilder.RecoveryStreamingReadExecution.Failed
        assertEquals(
            RecoveryStreamingResultClassification.STREAM_READ_CROSSES_ACCEPTED_END,
            (crossingFailure.result as RecoveryStreamingReconciliationResult.Fatal).classification,
        )
        assertEquals(listOf(4_056), crossing.requests)
    }

    @Test
    fun `throwing public read discards its buffer and maps only safe operational type`() {
        val oracleBytes = ByteArray(8_136) { 1 }
        val stream =
            ScriptedPublicRead(
                ReadStep.Bytes(oracleBytes.copyOfRange(0, 4_056)),
                ReadStep.ThrowAfterWrite(
                    ByteArray(4_080) { 99 },
                    RecoveryStreamingSafeExceptionType.CRYPTO,
                ),
                ReadStep.Eof,
            )
        val result =
            RecoveryStreamingIntentBuilder.RecoveryStreamingReadLoop.read(
                stream,
                RecoveryStreamingIntentBuilder.RecoveryStreamingOracle.from(
                    readWitness(oracleBytes),
                    oracleBytes,
                ),
            )
        val retry =
            (result as RecoveryStreamingIntentBuilder.RecoveryStreamingReadExecution.Failed).result
                as RecoveryStreamingReconciliationResult.Retry
        assertEquals(
            RecoveryStreamingResultClassification.STREAM_PUBLIC_READ_OPERATIONAL,
            retry.classification,
        )
        assertEquals(RecoveryStreamingSafeExceptionType.CRYPTO, retry.safeExceptionType)
        assertEquals(listOf(4_056, 4_080), stream.requests)
    }

    @Test
    fun `mismatch retains the whole completed return and deterministic first mismatch`() {
        val oracleBytes = ByteArray(4_056) { it.toByte() }
        val returned = oracleBytes.copyOf().also { it[17] = (it[17] + 1).toByte() }
        val result =
            RecoveryStreamingIntentBuilder.RecoveryStreamingReadLoop.read(
                ScriptedPublicRead(ReadStep.Bytes(returned), ReadStep.Eof),
                RecoveryStreamingIntentBuilder.RecoveryStreamingOracle.from(
                    readWitness(oracleBytes),
                    oracleBytes,
                ),
            )
        val completed =
            (result as RecoveryStreamingIntentBuilder.RecoveryStreamingReadExecution.Completed)
                .facts
        assertEquals(4_056UL, completed.candidateEnd)
        assertFalse(completed.oraclePrefixEqual)
        assertEquals(17UL, completed.firstMismatchOffset)
        assertEquals(Sha256Value.calculate(returned), completed.completedPlaintextSha256)
        assertNotEquals(completed.completedPlaintextSha256, completed.oraclePrefixSha256)
    }

    @Test
    fun `exact receipt readback closes resources before one sanitized evidence attempt`() {
        val outcome =
            RecoveryStreamingIntentBuilder.buildOutcome(intentFacts(completed = equalRead(8_136UL)))
        val range = RecoveryStreamingRangeRow.exact(outcome, sha(41))
        val order = mutableListOf<String>()
        val pending =
            RecoveryStreamingPendingPersistedResult.exactReadback(
                outcome,
                range,
                RecoveryStreamingJournalResult.Receipt(
                    outcome.outcomeId,
                    range.rangeIntentId,
                    replayed = false,
                ),
            ) {
                order += "public-close"
                error("public close")
            }
        order += "descriptor-close"
        val result =
            pending.complete(
                sourceDescriptorCloseFailed = true,
                evidenceSink =
                    RecoveryStreamingEvidenceSink { event ->
                        order += "evidence"
                        assertEquals(outcome.outcomeId, event.outcomeId)
                        assertEquals(range.rangeIntentId, event.rangeIntentId)
                        assertEquals(StreamDecision.VALID, event.persistedDecision)
                        assertEquals(
                            RecoveryStreamingPostReceiptCleanup
                                .PUBLIC_STREAM_AND_SOURCE_DESCRIPTOR_CLOSE_FAILED,
                            event.postReceiptCleanup,
                        )
                        assertTrue(event.existingEvidenceReferences.isEmpty())
                        error("sink unavailable")
                    },
            ) as RecoveryStreamingReconciliationResult.PersistedValid

        assertEquals(listOf("public-close", "descriptor-close", "evidence"), order)
        assertEquals(
            RecoveryStreamingPostReceiptCleanup.PUBLIC_STREAM_AND_SOURCE_DESCRIPTOR_CLOSE_FAILED,
            result.receipt.postReceiptCleanup,
        )
        assertEquals(RecoveryStreamingEvidenceDelivery.PENDING, result.receipt.evidenceDelivery)
    }

    @Test
    fun `receipt core rejects anything except exact journal readback`() {
        val outcome =
            RecoveryStreamingIntentBuilder.buildOutcome(intentFacts(completed = equalRead(8_136UL)))
        val range = RecoveryStreamingRangeRow.exact(outcome, sha(42))

        assertThrows(IllegalArgumentException::class.java) {
            RecoveryStreamingPendingPersistedResult.exactReadback(
                outcome,
                range,
                RecoveryStreamingJournalResult.Receipt(
                    sha(43),
                    range.rangeIntentId,
                    replayed = false,
                ),
            ) {}
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryStreamingPendingPersistedResult.exactReadback(
                outcome,
                null,
                RecoveryStreamingJournalResult.Receipt(
                    outcome.outcomeId,
                    range.rangeIntentId,
                    replayed = false,
                ),
            ) {}
        }
    }

    @Test
    fun `nonpersistable evidence is attempted once without throwable or source fields`() {
        val result =
            RecoveryStreamingReconciliationResult.Retry.of(
                RecoveryStreamingResultStage.JOURNAL,
                RecoveryStreamingResultClassification.JOURNAL_COMMIT_STATE_UNRESOLVED,
                RecoveryStreamingSafeExceptionType.SQLITE,
                attemptedOutcomeId = sha(44),
                attemptedRangeId = sha(45),
            )
        var attempts = 0
        val returned =
            RecoveryStreamingEvidenceFinalizer.nonPersistable(
                result,
                RecoveryStreamingEvidenceSink { event ->
                    attempts += 1
                    assertEquals(
                        RecoveryStreamingResultClassification.JOURNAL_COMMIT_STATE_UNRESOLVED,
                        event.classification,
                    )
                    assertEquals(sha(44), event.attemptedOutcomeId)
                    error("sink unavailable")
                },
            )

        assertTrue(returned === result)
        assertEquals(1, attempts)
        val names = RecoveryStreamingEvidenceEvent::class.java.declaredFields.map { it.name }
        assertFalse(names.any { it.contains("throwable", ignoreCase = true) })
        assertFalse(names.any { it.contains("path", ignoreCase = true) })
        assertFalse(names.any { it.contains("sha256", ignoreCase = true) })
        assertFalse(names.any { it.contains("byte", ignoreCase = true) })
    }

    @Test
    fun `journal mapper preserves exact receipt rollback retry collision and ambiguity`() {
        val outcome =
            RecoveryStreamingIntentBuilder.buildOutcome(intentFacts(completed = equalRead(8_136UL)))
        val range = RecoveryStreamingRangeRow.exact(outcome, sha(46))
        val attempt =
            RecoveryStreamingOutcomeAttempt(outcome, range, StreamSemanticOutcome.PERSISTED_VALID)

        val exact =
            RecoveryStreamingJournalMapper.resolve(
                attempt,
                RecoveryStreamingJournalResult.Receipt(
                    outcome.outcomeId,
                    range.rangeIntentId,
                    replayed = false,
                ),
            ) as RecoveryStreamingPersistenceResolution.ExactReceipt
        assertEquals(outcome.outcomeId, exact.receipt.outcomeId)

        val rollback =
            RecoveryStreamingJournalMapper.resolve(
                attempt,
                RecoveryStreamingJournalResult.Original(StreamSemanticOutcome.PERSISTED_VALID),
            ) as RecoveryStreamingPersistenceResolution.ProvenRollback
        assertEquals(StreamSemanticOutcome.PERSISTED_VALID, rollback.semanticOutcome)

        val unresolved =
            RecoveryStreamingJournalMapper.resolve(
                attempt,
                RecoveryStreamingJournalResult.Retry(
                    RecoveryStreamingJournalClassification.JOURNAL_COMMIT_STATE_UNRESOLVED,
                    attemptedOutcomeId = outcome.outcomeId,
                    attemptedRangeId = range.rangeIntentId,
                ),
            ) as RecoveryStreamingPersistenceResolution.Failed
        val retry = unresolved.result as RecoveryStreamingReconciliationResult.Retry
        assertEquals(outcome.outcomeId, retry.attemptedOutcomeId)
        assertEquals(range.rangeIntentId, retry.attemptedRangeId)

        val collision =
            RecoveryStreamingJournalMapper.resolve(
                attempt,
                RecoveryStreamingJournalResult.Fatal(
                    RecoveryStreamingJournalClassification.STREAM_RANGE_QUARANTINE_COLLISION,
                    listOf(
                        RecoveryStreamingExistingEvidence.Range(sha(49)),
                        RecoveryStreamingExistingEvidence.Outcome(sha(48)),
                    ),
                ),
            ) as RecoveryStreamingPersistenceResolution.Failed
        assertEquals(
            listOf(
                RecoveryStreamingExistingRecordKind.STREAM_OUTCOME,
                RecoveryStreamingExistingRecordKind.STREAM_RANGE,
            ),
            (collision.result as RecoveryStreamingReconciliationResult.Fatal)
                .existingEvidenceReferences
                .map { it.recordKind },
        )

        val ambiguous =
            RecoveryStreamingJournalMapper.readFailure(
                RecoveryStreamingJournalReadResult.Fatal(
                    RecoveryStreamingJournalClassification.JOURNAL_ATTEMPT_CONFLICT,
                    listOf(
                        RecoveryStreamingExistingEvidence.Outcome(sha(51)),
                        RecoveryStreamingExistingEvidence.Outcome(sha(50)),
                    ),
                )
            ) as RecoveryStreamingReconciliationResult.Fatal
        assertEquals(
            listOf(sha(50), sha(51)),
            ambiguous.existingEvidenceReferences.map { it.existingId },
        )
    }

    @Test
    fun `journal mapper rejects inexact readback and maps every allowed journal failure`() {
        val outcome =
            RecoveryStreamingIntentBuilder.buildOutcome(intentFacts(completed = equalRead(8_136UL)))
        val range = RecoveryStreamingRangeRow.exact(outcome, sha(52))
        val attempt =
            RecoveryStreamingOutcomeAttempt(outcome, range, StreamSemanticOutcome.PERSISTED_VALID)
        val inexact =
            RecoveryStreamingJournalMapper.resolve(
                attempt,
                RecoveryStreamingJournalResult.Receipt(
                    sha(53),
                    range.rangeIntentId,
                    replayed = false,
                ),
            ) as RecoveryStreamingPersistenceResolution.Failed
        assertEquals(
            RecoveryStreamingResultClassification.JOURNAL_STRUCTURAL,
            (inexact.result as RecoveryStreamingReconciliationResult.Fatal).classification,
        )

        val expected =
            mapOf(
                RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL to
                    RecoveryStreamingResultClassification.JOURNAL_OPERATIONAL,
                RecoveryStreamingJournalClassification.JOURNAL_COMMIT_STATE_UNRESOLVED to
                    RecoveryStreamingResultClassification.JOURNAL_COMMIT_STATE_UNRESOLVED,
                RecoveryStreamingJournalClassification.JOURNAL_ATTEMPT_CONFLICT to
                    RecoveryStreamingResultClassification.JOURNAL_ATTEMPT_CONFLICT,
                RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL to
                    RecoveryStreamingResultClassification.JOURNAL_STRUCTURAL,
                RecoveryStreamingJournalClassification.STREAM_RANGE_QUARANTINE_COLLISION to
                    RecoveryStreamingResultClassification.STREAM_RANGE_QUARANTINE_COLLISION,
                RecoveryStreamingJournalClassification.STREAM_CHECKPOINT_SPLIT_BRAIN to
                    RecoveryStreamingResultClassification.STREAM_CHECKPOINT_SPLIT_BRAIN,
                RecoveryStreamingJournalClassification.STREAM_SOURCE_IDENTITY_CHANGED to
                    RecoveryStreamingResultClassification.STREAM_SOURCE_IDENTITY_CHANGED,
            )
        expected.forEach { (journal, outward) ->
            val result =
                when (journal) {
                    RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL,
                    RecoveryStreamingJournalClassification.JOURNAL_COMMIT_STATE_UNRESOLVED ->
                        RecoveryStreamingJournalMapper.readFailure(
                            RecoveryStreamingJournalReadResult.Retry(journal)
                        )
                    else ->
                        RecoveryStreamingJournalMapper.readFailure(
                            RecoveryStreamingJournalReadResult.Fatal(journal, emptyList())
                        )
                }
            val actual =
                when (result) {
                    is RecoveryStreamingReconciliationResult.Retry -> result.classification
                    is RecoveryStreamingReconciliationResult.Fatal -> result.classification
                    else -> error("unexpected")
                }
            assertEquals(outward, actual)
        }
    }

    @Test
    fun `controller checkpoint missing stops before auth source write and emits before lease release`() {
        val events = mutableListOf<String>()
        val journal = ControllerJournal(events)
        val controller =
            RecoveryStreamingReconciliationController(
                journal,
                NeverControllerSource(events),
                RecoveryRunSingleWriterGuard {
                    events += "lease-acquire"
                    RecoveryRunWriterLease { events += "lease-release" }
                },
                RecoveryStreamingCheckpointAuthenticator { _, _ ->
                    events += "authenticate"
                    error("authentication must not run")
                },
                RecoveryStreamingEvidenceSink { events += "evidence" },
            )

        val result = controller.recover(controllerFixture().request)

        assertEquals(
            RecoveryStreamingResultClassification.STREAM_CHECKPOINT_MISSING,
            (result as RecoveryStreamingReconciliationResult.Fatal).classification,
        )
        assertEquals(
            listOf("lease-acquire", "checkpoint-chain", "evidence", "lease-release"),
            events,
        )
        assertEquals(0, journal.persistCalls)
    }

    @Test
    fun `controller authenticates the exact checkpoint and stops on rejected authentication`() {
        val fixture = controllerFixture()
        val events = mutableListOf<String>()
        val journal = ControllerJournal(events).apply { checkpoints = listOf(fixture.checkpoint) }
        val controller =
            RecoveryStreamingReconciliationController(
                journal,
                NeverControllerSource(events),
                RecoveryRunSingleWriterGuard {
                    events += "lease-acquire"
                    RecoveryRunWriterLease { events += "lease-release" }
                },
                RecoveryStreamingCheckpointAuthenticator { checkpoint, witness ->
                    events += "authenticate"
                    assertTrue(checkpoint === fixture.checkpoint)
                    assertEquals(fixture.request.witness, witness)
                    RecoveryStreamingCheckpointAuthentication.Rejected
                },
                RecoveryStreamingEvidenceSink { events += "evidence" },
            )

        val result = controller.recover(fixture.request)

        assertEquals(
            RecoveryStreamingResultClassification.STREAM_CHECKPOINT_AUTHENTICATION_REJECTED,
            (result as RecoveryStreamingReconciliationResult.Fatal).classification,
        )
        assertEquals(
            listOf(
                "lease-acquire",
                "checkpoint-chain",
                "authenticate",
                "evidence",
                "lease-release",
            ),
            events,
        )
        assertEquals(0, journal.persistCalls)
    }

    @Test
    fun `controller exact replay rehashes once without public stream write or second source open`() {
        val fixture = controllerFixture()
        val events = mutableListOf<String>()
        val outcome = controllerValidOutcome(fixture)
        val journal =
            ControllerJournal(events).apply {
                checkpoints = listOf(fixture.checkpoint)
                existingOutcome = outcome
            }
        val source = ReplayControllerSource(events, outcome)
        val controller =
            RecoveryStreamingReconciliationController(
                journal,
                source,
                RecoveryRunSingleWriterGuard {
                    events += "lease-acquire"
                    RecoveryRunWriterLease { events += "lease-release" }
                },
                RecoveryStreamingCheckpointAuthenticator { _, _ ->
                    events += "authenticate"
                    RecoveryStreamingCheckpointAuthentication.Ready(
                        RecoveryStreamingPublicStreamOpener { _, _ ->
                            events += "public-open"
                            error("replay must not open public Tink")
                        }
                    )
                },
                RecoveryStreamingEvidenceSink { events += "evidence" },
            )

        val result =
            controller.recover(fixture.request)
                as RecoveryStreamingReconciliationResult.PersistedValid

        assertTrue(result.receipt.replayed)
        assertEquals(outcome.outcomeId, result.receipt.outcomeId)
        assertEquals(
            listOf(
                "lease-acquire",
                "checkpoint-chain",
                "authenticate",
                "outcome-witness",
                "range-outcome",
                "replay-source-open",
                "evidence",
                "lease-release",
            ),
            events,
        )
        assertEquals(1, source.replayOpens)
        assertEquals(0, source.normalOpens)
        assertEquals(0, journal.persistCalls)
    }

    @Test
    fun `controller active range denial stops before source public stream and durable write`() {
        val fixture = controllerFixture()
        val events = mutableListOf<String>()
        val parent = controllerPreFatalOutcome(fixture)
        val range = RecoveryStreamingRangeRow.exact(parent, Sha256Value.calculate(fixture.source))
        val journal =
            ControllerJournal(events).apply {
                checkpoints = listOf(fixture.checkpoint)
                parentOutcome = parent
                active = listOf(range)
            }
        val controller =
            RecoveryStreamingReconciliationController(
                journal,
                NeverControllerSource(events),
                RecoveryRunSingleWriterGuard {
                    events += "lease-acquire"
                    RecoveryRunWriterLease { events += "lease-release" }
                },
                RecoveryStreamingCheckpointAuthenticator { _, _ ->
                    events += "authenticate"
                    RecoveryStreamingCheckpointAuthentication.Ready(
                        RecoveryStreamingPublicStreamOpener { _, _ ->
                            events += "public-open"
                            error("active denial must not open public Tink")
                        }
                    )
                },
                RecoveryStreamingEvidenceSink { events += "evidence" },
            )

        val result = controller.recover(fixture.request)

        val fatal = result as RecoveryStreamingReconciliationResult.Fatal
        assertEquals(
            RecoveryStreamingResultClassification.STREAM_ACTIVE_RANGE_DENIED,
            fatal.classification,
        )
        assertEquals(
            listOf(
                RecoveryStreamingExistingRecordKind.STREAM_OUTCOME,
                RecoveryStreamingExistingRecordKind.STREAM_RANGE,
            ),
            fatal.existingEvidenceReferences.map { it.recordKind },
        )
        assertEquals(
            listOf(
                "lease-acquire",
                "checkpoint-chain",
                "authenticate",
                "outcome-witness",
                "active-ranges",
                "outcome-id",
                "evidence",
                "lease-release",
            ),
            events,
        )
        assertEquals(0, journal.persistCalls)
    }

    @Test
    fun `controller fresh valid persists exact readback then closes emits and releases`() {
        val fixture = controllerFixture()
        val events = mutableListOf<String>()
        val journal =
            ControllerJournal(events).apply {
                checkpoints = listOf(fixture.checkpoint)
                persistBehavior = { attempt ->
                    RecoveryStreamingJournalResult.Receipt(
                        attempt.outcome.outcomeId,
                        attempt.range?.rangeIntentId,
                        replayed = false,
                    )
                }
            }
        val source = FreshControllerSource(events, fixture.source)
        val controller =
            RecoveryStreamingReconciliationController(
                journal,
                source,
                RecoveryRunSingleWriterGuard {
                    events += "lease-acquire"
                    RecoveryRunWriterLease { events += "lease-release" }
                },
                RecoveryStreamingCheckpointAuthenticator { _, _ ->
                    events += "authenticate"
                    RecoveryStreamingCheckpointAuthentication.Ready(
                        RecoveryStreamingPublicStreamOpener { _, _ ->
                            events += "public-open"
                            EventPublicRead(events, fixture.oracle)
                        }
                    )
                },
                RecoveryStreamingEvidenceSink { events += "evidence" },
            )

        val result =
            controller.recover(fixture.request)
                as RecoveryStreamingReconciliationResult.PersistedValid

        assertFalse(result.receipt.replayed)
        assertEquals(RecoveryStreamingPostReceiptCleanup.NONE, result.receipt.postReceiptCleanup)
        assertEquals(RecoveryStreamingEvidenceDelivery.DELIVERED, result.receipt.evidenceDelivery)
        assertEquals(1, source.normalOpens)
        assertEquals(1, journal.persistCalls)
        assertEquals(StreamDecision.VALID, requireNotNull(journal.lastAttempt).outcome.decision)
        assertEquals(null, requireNotNull(journal.lastAttempt).range)
        assertEquals(
            listOf(
                "lease-acquire",
                "checkpoint-chain",
                "authenticate",
                "outcome-witness",
                "active-ranges",
                "source-open",
                "hash-8192",
                "hash-8192",
                "hash-8192",
                "public-open",
                "persist",
                "public-close",
                "source-close",
                "evidence",
                "lease-release",
            ),
            events,
        )
    }

    @Test
    fun `controller PRE truncation persists without public stream construction`() {
        val fixture = controllerFixture()
        val events = mutableListOf<String>()
        val journal =
            ControllerJournal(events).apply {
                checkpoints = listOf(fixture.checkpoint)
                persistBehavior = { attempt ->
                    RecoveryStreamingJournalResult.Receipt(
                        attempt.outcome.outcomeId,
                        attempt.range?.rangeIntentId,
                        replayed = false,
                    )
                }
            }
        val controller =
            RecoveryStreamingReconciliationController(
                journal,
                FreshControllerSource(events, fixture.source.copyOf(8_191)),
                RecoveryRunSingleWriterGuard {
                    events += "lease-acquire"
                    RecoveryRunWriterLease { events += "lease-release" }
                },
                RecoveryStreamingCheckpointAuthenticator { _, _ ->
                    events += "authenticate"
                    RecoveryStreamingCheckpointAuthentication.Ready(
                        RecoveryStreamingPublicStreamOpener { _, _ ->
                            events += "public-open"
                            error("PRE selection must not construct public Tink")
                        }
                    )
                },
                RecoveryStreamingEvidenceSink { events += "evidence" },
            )

        val result =
            controller.recover(fixture.request) as RecoveryStreamingReconciliationResult.Fatal

        assertEquals(null, result.classification)
        assertEquals(
            StreamDiagnosticClassification.STREAM_SOURCE_TRUNCATED,
            requireNotNull(result.persistedDiagnostic).diagnosticClassification,
        )
        assertEquals(1, journal.persistCalls)
        assertEquals(0UL, requireNotNull(journal.lastAttempt).range?.rangeStart)
        assertFalse(events.contains("public-open"))
        assertEquals(
            listOf(
                "lease-acquire",
                "checkpoint-chain",
                "authenticate",
                "outcome-witness",
                "active-ranges",
                "source-open",
                "hash-8191",
                "range-0-8191",
                "persist",
                "source-close",
                "evidence",
                "lease-release",
            ),
            events,
        )
    }

    @Test
    fun `controller lease contention returns retry without journal source or write`() {
        val events = mutableListOf<String>()
        val journal = ControllerJournal(events)
        val controller =
            RecoveryStreamingReconciliationController(
                journal,
                NeverControllerSource(events),
                RecoveryRunSingleWriterGuard {
                    events += "lease-contended"
                    null
                },
                RecoveryStreamingCheckpointAuthenticator { _, _ ->
                    error("auth must not run")
                },
                RecoveryStreamingEvidenceSink { events += "evidence" },
            )

        val result =
            controller.recover(controllerFixture().request)
                as RecoveryStreamingReconciliationResult.Retry

        assertEquals(RecoveryStreamingResultStage.LEASE, result.stage)
        assertEquals(
            RecoveryStreamingResultClassification.RUN_LEASE_CONTENDED,
            result.classification,
        )
        assertEquals(RecoveryStreamingSafeExceptionType.NONE, result.safeExceptionType)
        assertEquals(listOf("lease-contended", "evidence"), events)
        assertEquals(0, journal.persistCalls)
    }

    @Test
    fun `controller maps unsafe source denial without public stream write or receipt`() {
        val fixture = controllerFixture()
        val events = mutableListOf<String>()
        val journal = ControllerJournal(events).apply { checkpoints = listOf(fixture.checkpoint) }
        val source =
            object : RecoveryStreamingSource {
                override fun <T> withSource(
                    access: RecoveryStreamingSourceLeaseAccess,
                    request: RecoveryStreamOpenRequest,
                    block: (RecoveryOpenedStreamingSource) -> T,
                ): T =
                    access.withBoundTo(request.runId) {
                        events += "source-deny"
                        throw RecoveryStreamingSourceException(
                            RecoveryStreamingSourceFailure.UNSAFE_PATH
                        )
                    }

                override fun verifyReplayHashOnly(
                    access: RecoveryStreamingReplayAccess,
                    request: RecoveryStreamReplayRequest,
                ): RecoveryReplayHashOnlyResult = error("replay must not run")
            }
        val controller =
            RecoveryStreamingReconciliationController(
                journal,
                source,
                RecoveryRunSingleWriterGuard {
                    events += "lease-acquire"
                    RecoveryRunWriterLease { events += "lease-release" }
                },
                RecoveryStreamingCheckpointAuthenticator { _, _ ->
                    events += "authenticate"
                    RecoveryStreamingCheckpointAuthentication.Ready(
                        RecoveryStreamingPublicStreamOpener { _, _ ->
                            events += "public-open"
                            error("unsafe source must not open public Tink")
                        }
                    )
                },
                RecoveryStreamingEvidenceSink { events += "evidence" },
            )

        val result =
            controller.recover(fixture.request) as RecoveryStreamingReconciliationResult.Fatal

        assertEquals(RecoveryStreamingResultClassification.UNSAFE_PATH, result.classification)
        assertEquals(
            listOf(
                "lease-acquire",
                "checkpoint-chain",
                "authenticate",
                "outcome-witness",
                "active-ranges",
                "source-deny",
                "evidence",
                "lease-release",
            ),
            events,
        )
        assertEquals(0, journal.persistCalls)
    }

    @Test
    fun `proven rollback and reconciled absence for semantic valid returns operational retry`() {
        val fixture = controllerFixture()
        val events = mutableListOf<String>()
        val journal =
            ControllerJournal(events).apply {
                checkpoints = listOf(fixture.checkpoint)
                persistBehavior = {
                    RecoveryStreamingJournalResult.Original(StreamSemanticOutcome.PERSISTED_VALID)
                }
            }
        val controller =
            RecoveryStreamingReconciliationController(
                journal,
                FreshControllerSource(events, fixture.source),
                RecoveryRunSingleWriterGuard {
                    events += "lease-acquire"
                    RecoveryRunWriterLease { events += "lease-release" }
                },
                RecoveryStreamingCheckpointAuthenticator { _, _ ->
                    events += "authenticate"
                    RecoveryStreamingCheckpointAuthentication.Ready(
                        RecoveryStreamingPublicStreamOpener { _, _ ->
                            events += "public-open"
                            EventPublicRead(events, fixture.oracle)
                        }
                    )
                },
                RecoveryStreamingEvidenceSink { event ->
                    events += "evidence"
                    assertEquals(null, event.outcomeId)
                    assertEquals(null, event.rangeIntentId)
                    assertEquals(null, event.attemptedOutcomeId)
                    assertTrue(event.existingEvidenceReferences.isEmpty())
                },
            )

        val result =
            controller.recover(fixture.request) as RecoveryStreamingReconciliationResult.Retry

        assertEquals(RecoveryStreamingResultStage.JOURNAL, result.stage)
        assertEquals(
            RecoveryStreamingResultClassification.JOURNAL_OPERATIONAL,
            result.classification,
        )
        assertEquals(RecoveryStreamingSafeExceptionType.SQLITE, result.safeExceptionType)
        assertEquals(null, result.attemptedOutcomeId)
        assertEquals(null, result.attemptedRangeId)
        assertTrue(result.existingEvidenceReferences.isEmpty())
        assertTrue(events.indexOf("public-close") < events.indexOf("source-close"))
        assertTrue(events.indexOf("source-close") < events.indexOf("evidence"))
        assertTrue(events.indexOf("evidence") < events.indexOf("lease-release"))
        assertEquals(1, journal.persistCalls)
    }

    @Test
    fun `proven rollback returns original rejected semantic without receipt`() {
        val fixture = controllerFixture(acceptedEnd = 12_217)
        val events = mutableListOf<String>()
        val journal =
            ControllerJournal(events).apply {
                checkpoints = listOf(fixture.checkpoint)
                persistBehavior = {
                    RecoveryStreamingJournalResult.Original(
                        StreamSemanticOutcome.PERSISTED_REJECTED
                    )
                }
            }
        val controller =
            RecoveryStreamingReconciliationController(
                journal,
                FreshControllerSource(events, fixture.source),
                RecoveryRunSingleWriterGuard { RecoveryRunWriterLease {} },
                RecoveryStreamingCheckpointAuthenticator { _, _ ->
                    RecoveryStreamingCheckpointAuthentication.Ready(
                        RecoveryStreamingPublicStreamOpener { _, _ ->
                            ScriptedPublicRead(
                                ReadStep.Bytes(fixture.oracle.copyOfRange(0, 4_056)),
                                ReadStep.AuthenticationFailure,
                            )
                        }
                    )
                },
                RecoveryStreamingEvidenceSink { event ->
                    assertEquals(null, event.outcomeId)
                    assertEquals(null, event.rangeIntentId)
                    assertEquals(null, event.persistedDecision)
                    assertEquals(
                        StreamDiagnosticClassification.STREAM_TAIL_BOUND_EXCEEDED,
                        event.diagnosticClassification,
                    )
                },
            )

        val result =
            controller.recover(fixture.request) as RecoveryStreamingReconciliationResult.Rejected

        assertEquals(null, result.stage)
        assertEquals(null, result.classification)
        assertEquals(null, result.persistedDiagnostic)
        assertEquals(
            StreamDiagnosticClassification.STREAM_TAIL_BOUND_EXCEEDED,
            requireNotNull(result.originalDiagnostic).diagnosticClassification,
        )
        assertEquals(1, journal.persistCalls)
    }

    @Test
    fun `proven rollback returns original fatal semantic without receipt`() {
        val fixture = controllerFixture()
        val events = mutableListOf<String>()
        val journal =
            ControllerJournal(events).apply {
                checkpoints = listOf(fixture.checkpoint)
                persistBehavior = {
                    RecoveryStreamingJournalResult.Original(StreamSemanticOutcome.PERSISTED_FATAL)
                }
            }
        val changedSource = fixture.source.copyOf().also { it[0] = (it[0] + 1).toByte() }
        val controller =
            RecoveryStreamingReconciliationController(
                journal,
                FreshControllerSource(events, changedSource),
                RecoveryRunSingleWriterGuard { RecoveryRunWriterLease {} },
                RecoveryStreamingCheckpointAuthenticator { _, _ ->
                    RecoveryStreamingCheckpointAuthentication.Ready(
                        RecoveryStreamingPublicStreamOpener { _, _ ->
                            error("PRE result must not open public stream")
                        }
                    )
                },
                RecoveryStreamingEvidenceSink { event ->
                    assertEquals(null, event.outcomeId)
                    assertEquals(null, event.rangeIntentId)
                    assertEquals(null, event.persistedDecision)
                    assertEquals(
                        StreamDiagnosticClassification.STREAM_SOURCE_PREFIX_IDENTITY_MISMATCH,
                        event.diagnosticClassification,
                    )
                },
            )

        val result =
            controller.recover(fixture.request) as RecoveryStreamingReconciliationResult.Fatal

        assertEquals(null, result.stage)
        assertEquals(null, result.classification)
        assertEquals(null, result.persistedDiagnostic)
        assertEquals(
            StreamDiagnosticClassification.STREAM_SOURCE_PREFIX_IDENTITY_MISMATCH,
            requireNotNull(result.originalDiagnostic).diagnosticClassification,
        )
        assertEquals(1, journal.persistCalls)
    }

    @Test
    fun `prerequisite adapter requires all exact artifacts before crypto`() {
        val fixture = controllerFixture()
        val artifacts =
            mapOf(
                RecoveryStreamingPrerequisiteArtifactKind.CHECKPOINT_KEY_ENVELOPE to
                    RecoveryArtifactBytes(
                        fixture.checkpoint.checkpointKeyEnvelopeRelativeName,
                        fixture.checkpointEnvelope,
                    ),
                RecoveryStreamingPrerequisiteArtifactKind.CHECKPOINT_CIPHERTEXT to
                    RecoveryArtifactBytes(
                        fixture.checkpoint.checkpointRelativeName,
                        fixture.checkpointArtifact,
                    ),
                RecoveryStreamingPrerequisiteArtifactKind.STREAM_KEY_ENVELOPE to
                    RecoveryArtifactBytes(
                        fixture.checkpoint.streamKeyEnvelopeRelativeName,
                        fixture.streamEnvelope,
                    ),
            )
        var cryptoCalls = 0
        val opener = RecoveryStreamingPublicStreamOpener { _, _ -> error("unused") }
        val crypto =
            RecoveryStreamingPrerequisiteCrypto { checkpoint, checkpointEnvelope, ciphertext, streamEnvelope ->
                cryptoCalls += 1
                assertTrue(checkpoint === fixture.checkpoint)
                assertArrayEquals(fixture.checkpointEnvelope, checkpointEnvelope)
                assertArrayEquals(fixture.checkpointArtifact, ciphertext)
                assertArrayEquals(fixture.streamEnvelope, streamEnvelope)
                RecoveryStreamingCheckpointAuthentication.Ready(opener)
            }
        fun adapter(values: Map<RecoveryStreamingPrerequisiteArtifactKind, RecoveryArtifactBytes>) =
            RecoveryStreamingCheckpointAuthenticatorAdapter(
                RecoveryStreamingPrerequisiteSource { _, _, kind -> values[kind] },
                crypto,
            )

        val ready = adapter(artifacts).authenticate(fixture.checkpoint, fixture.request.witness)
        assertTrue(ready is RecoveryStreamingCheckpointAuthentication.Ready)
        assertEquals(1, cryptoCalls)

        val missing = adapter(artifacts - RecoveryStreamingPrerequisiteArtifactKind.STREAM_KEY_ENVELOPE)
        assertTrue(
            missing.authenticate(fixture.checkpoint, fixture.request.witness) ===
                RecoveryStreamingCheckpointAuthentication.Missing
        )
        val corrupt =
            adapter(
                artifacts +
                    (RecoveryStreamingPrerequisiteArtifactKind.CHECKPOINT_CIPHERTEXT to
                        RecoveryArtifactBytes(
                            fixture.checkpoint.checkpointRelativeName,
                            fixture.checkpointArtifact.copyOf(1),
                        ))
            )
        assertTrue(
            corrupt.authenticate(fixture.checkpoint, fixture.request.witness) ===
                RecoveryStreamingCheckpointAuthentication.Structural
        )
        assertEquals(1, cryptoCalls)
    }

    private fun render(mapping: RecoveryStreamingResultMapping): String =
        listOf(
                mapping.disposition.name,
                mapping.stage.name,
                mapping.classification.name,
                mapping.allowedSafeExceptionTypes.map { it.name }.sorted().joinToString(","),
            )
            .joinToString("|")

    private fun fatal(stage: String, classification: String) = "FATAL|$stage|$classification|"

    private fun rejected(stage: String, classification: String) = "REJECTED|$stage|$classification|"

    private fun retry(stage: String, classification: String, exception: String) =
        "RETRY|$stage|$classification|$exception"

    private fun sha(firstByte: Int): Sha256Value =
        Sha256Value.fromBytes(byteArrayOf(firstByte.toByte()) + ByteArray(31))

    private fun equalRead(
        end: ULong,
        terminal: StreamTerminal = StreamTerminal.AUTHENTICATION_FAILURE,
    ) =
        RecoveryStreamingCompletedReadFacts(
            candidateEnd = end,
            completedPlaintextSha256 = sha((end % 251UL).toInt()),
            oraclePrefixSha256 = sha((end % 251UL).toInt()),
            oraclePrefixEqual = true,
            terminal = terminal,
        )

    private fun mismatchRead(end: ULong) =
        RecoveryStreamingCompletedReadFacts(
            candidateEnd = end,
            completedPlaintextSha256 = sha(10),
            oraclePrefixSha256 = sha(11),
            oraclePrefixEqual = false,
            terminal = StreamTerminal.COMPLETED_READ_REJECTED,
            firstMismatchOffset = 0UL,
            equalPrefixSha256 = sha(0),
            expectedOracleByte = 1U,
            observedPlaintextByte = 2U,
        )

    @Suppress("LongParameterList")
    private fun intentFacts(
        prefixBytes: ULong = 8_192UL,
        committedEnd: ULong = 4_056UL,
        acceptedEnd: ULong = 8_137UL,
        preFaultEnd: ULong = 8_192UL,
        observedEnd: ULong = 8_193UL,
        checkpointPrefixMatches: Boolean = true,
        preFaultPrefixMatches: Boolean = true,
        completed: RecoveryStreamingCompletedReadFacts? = null,
    ): RecoveryStreamingValidatedIntentFacts {
        val runId = RunId.fromBytes(ByteArray(16) { it.toByte() })
        val oracleSha = sha(20)
        val baseWitness =
            RecoveryStreamingWitnessInput(
                runId = runId,
                checkpointGeneration = 1UL,
                checkpointIdentity = sha(21),
                checkpointPrefixBytes = prefixBytes,
                checkpointContextEnd = committedEnd,
                oracleIdentitySha256 =
                    RecoveryStreamingIdentity.oracle(acceptedEnd, oracleSha, runId),
                acceptedEnd = acceptedEnd,
                oraclePlaintextSha256 = oracleSha,
                preFaultSourceBytes = preFaultEnd,
                preFaultSourceSha256 = sha(22),
                controllerSnapshotSha256 = null,
            )
        val witness =
            baseWitness.copy(
                controllerSnapshotSha256 = RecoveryStreamingIdentity.controllerSnapshot(baseWitness)
            )
        return RecoveryStreamingValidatedIntentFacts(
            witness = witness,
            observedSourceBytes = observedEnd,
            observedSourceSha256 = sha(23),
            checkpointPrefixMatches = checkpointPrefixMatches,
            preFaultPrefixMatches = preFaultPrefixMatches,
            completed = completed,
        )
    }

    private fun readWitness(oracleBytes: ByteArray): RecoveryStreamingWitnessInput {
        val runId = RunId.fromBytes(ByteArray(16) { it.toByte() })
        val oracleSha = Sha256Value.calculate(oracleBytes)
        val base =
            RecoveryStreamingWitnessInput(
                runId = runId,
                checkpointGeneration = 1UL,
                checkpointIdentity = sha(31),
                checkpointPrefixBytes = 0UL,
                checkpointContextEnd = 0UL,
                oracleIdentitySha256 =
                    RecoveryStreamingIdentity.oracle(oracleBytes.size.toULong(), oracleSha, runId),
                acceptedEnd = oracleBytes.size.toULong(),
                oraclePlaintextSha256 = oracleSha,
                preFaultSourceBytes = 0UL,
                preFaultSourceSha256 = Sha256Value.calculate(byteArrayOf()),
                controllerSnapshotSha256 = null,
            )
        return base.copy(
            controllerSnapshotSha256 = RecoveryStreamingIdentity.controllerSnapshot(base)
        )
    }

    private data class ControllerFixture(
        val request: RecoveryStreamingControllerRequest,
        val checkpoint: RecoveryStreamingCheckpointRow,
        val source: ByteArray,
        val oracle: ByteArray,
        val checkpointArtifact: ByteArray,
        val checkpointEnvelope: ByteArray,
        val streamEnvelope: ByteArray,
    )

    private fun controllerFixture(acceptedEnd: Int = 8_136): ControllerFixture {
        val runId = RunId.fromBytes(ByteArray(16) { (it + 1).toByte() })
        val source = ByteArray(8_192) { ((it * 31 + 9) and 0xff).toByte() }
        val checkpointArtifact = ByteArray(128) { ((it * 3 + 1) and 0xff).toByte() }
        val checkpointEnvelope = ByteArray(96) { ((it * 5 + 2) and 0xff).toByte() }
        val streamEnvelope = ByteArray(96) { ((it * 11 + 4) and 0xff).toByte() }
        val prefix = Sha256Value.calculate(source)
        val checkpointInput =
            RecoveryStreamingCheckpointIdentityInput(
                runId,
                generation = 1UL,
                durableNonFinalSegmentCount = 2UL,
                streamCiphertextPrefixBytes = 8_192UL,
                streamCiphertextPrefixSha256 = prefix,
                committedEnd = 4_056UL,
                checkpointRelativeName = "checkpoints/g-00000000000000000001.ct",
                checkpointBytes = 128UL,
                checkpointSha256 = Sha256Value.calculate(checkpointArtifact),
                checkpointEnvelopeRelativeName =
                    "key-envelopes/checkpoint-g-00000000000000000001.ks",
                checkpointEnvelopeBytes = 96UL,
                checkpointEnvelopeSha256 = Sha256Value.calculate(checkpointEnvelope),
                streamRelativeName = "stream/stream.ct",
                streamEnvelopeRelativeName = "key-envelopes/stream.ks",
                streamEnvelopeBytes = 96UL,
                streamEnvelopeSha256 = Sha256Value.calculate(streamEnvelope),
                previousCheckpointSha256 = sha(0),
            )
        val checkpointIdentity = RecoveryStreamingIdentity.checkpoint(checkpointInput)
        val checkpoint =
            RecoveryStreamingCheckpointRow(
                runId,
                1UL,
                2UL,
                8_192UL,
                prefix,
                4_056UL,
                checkpointInput.checkpointRelativeName,
                checkpointInput.checkpointBytes,
                checkpointInput.checkpointSha256,
                checkpointInput.checkpointEnvelopeRelativeName,
                checkpointInput.checkpointEnvelopeBytes,
                checkpointInput.checkpointEnvelopeSha256,
                checkpointInput.streamRelativeName,
                checkpointInput.streamEnvelopeRelativeName,
                checkpointInput.streamEnvelopeBytes,
                checkpointInput.streamEnvelopeSha256,
                checkpointInput.previousCheckpointSha256,
                checkpointIdentity,
            )
        val oracleBytes = ByteArray(acceptedEnd) { ((it * 7 + 5) and 0xff).toByte() }
        val oracleSha = Sha256Value.calculate(oracleBytes)
        val witnessBase =
            RecoveryStreamingWitnessInput(
                runId,
                1UL,
                checkpointIdentity,
                8_192UL,
                4_056UL,
                RecoveryStreamingIdentity.oracle(acceptedEnd.toULong(), oracleSha, runId),
                acceptedEnd.toULong(),
                oracleSha,
                8_192UL,
                prefix,
                null,
            )
        val witness =
            witnessBase.copy(
                controllerSnapshotSha256 = RecoveryStreamingIdentity.controllerSnapshot(witnessBase)
            )
        return ControllerFixture(
            RecoveryStreamingControllerRequest(
                witness,
                RecoveryStreamingIntentBuilder.RecoveryStreamingOracle.from(witness, oracleBytes),
            ),
            checkpoint,
            source,
            oracleBytes,
            checkpointArtifact,
            checkpointEnvelope,
            streamEnvelope,
        )
    }

    private fun controllerValidOutcome(fixture: ControllerFixture): RecoveryStreamingOutcomeRow {
        val witness = fixture.request.witness
        return RecoveryStreamingIntentBuilder.buildOutcome(
            RecoveryStreamingValidatedIntentFacts(
                witness,
                fixture.source.size.toULong(),
                Sha256Value.calculate(fixture.source),
                checkpointPrefixMatches = true,
                preFaultPrefixMatches = true,
                completed =
                    RecoveryStreamingCompletedReadFacts(
                        candidateEnd = witness.acceptedEnd,
                        completedPlaintextSha256 = witness.oraclePlaintextSha256,
                        oraclePrefixSha256 = witness.oraclePlaintextSha256,
                        oraclePrefixEqual = true,
                        terminal = StreamTerminal.AUTHENTICATED_EOF,
                    ),
            )
        )
    }

    private fun controllerPreFatalOutcome(fixture: ControllerFixture) =
        RecoveryStreamingIntentBuilder.buildOutcome(
            RecoveryStreamingValidatedIntentFacts(
                fixture.request.witness,
                fixture.source.size.toULong(),
                Sha256Value.calculate(fixture.source),
                checkpointPrefixMatches = false,
                preFaultPrefixMatches = true,
                completed = null,
            )
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

    private class NeverControllerSource(private val events: MutableList<String>) :
        RecoveryStreamingSource {
        override fun <T> withSource(
            access: RecoveryStreamingSourceLeaseAccess,
            request: RecoveryStreamOpenRequest,
            block: (RecoveryOpenedStreamingSource) -> T,
        ): T {
            events += "source-open"
            error("source must not open")
        }

        override fun verifyReplayHashOnly(
            access: RecoveryStreamingReplayAccess,
            request: RecoveryStreamReplayRequest,
        ): RecoveryReplayHashOnlyResult {
            events += "replay-open"
            error("replay source must not open")
        }
    }

    private class ReplayControllerSource(
        private val events: MutableList<String>,
        private val outcome: RecoveryStreamingOutcomeRow,
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

    private class EventPublicRead(
        private val events: MutableList<String>,
        bytes: ByteArray,
    ) : RecoveryStreamingIntentBuilder.RecoveryStreamingPublicRead {
        private val value = bytes.copyOf()
        private var sourceOffset = 0

        override fun read(destination: ByteArray, offset: Int, count: Int): Int {
            if (sourceOffset == value.size) return -1
            val returned = minOf(count, value.size - sourceOffset)
            value.copyInto(destination, offset, sourceOffset, sourceOffset + returned)
            sourceOffset += returned
            return returned
        }

        override fun close() {
            events += "public-close"
        }
    }

    private sealed interface ReadStep {
        data class Bytes(val value: ByteArray) : ReadStep

        data class ThrowAfterWrite(
            val value: ByteArray,
            val type: RecoveryStreamingSafeExceptionType,
        ) : ReadStep

        data object Zero : ReadStep

        data object Eof : ReadStep

        data object AuthenticationFailure : ReadStep
    }

    private class ScriptedPublicRead(vararg steps: ReadStep) :
        RecoveryStreamingIntentBuilder.RecoveryStreamingPublicRead {
        private val pending = ArrayDeque(steps.toList())
        val requests = mutableListOf<Int>()

        override fun read(destination: ByteArray, offset: Int, count: Int): Int {
            requests += count
            return when (val step = pending.removeFirst()) {
                is ReadStep.Bytes -> {
                    step.value.copyInto(destination, offset)
                    step.value.size
                }
                is ReadStep.ThrowAfterWrite -> {
                    step.value.copyInto(destination, offset)
                    throw RecoveryStreamingIntentBuilder.RecoveryStreamingPublicReadException(
                        step.type
                    )
                }
                ReadStep.Zero -> 0
                ReadStep.Eof -> -1
                ReadStep.AuthenticationFailure ->
                    throw RecoveryStreamingIntentBuilder
                        .RecoveryStreamingAuthenticationFailureException()
            }
        }

        override fun close() = Unit
    }
}
