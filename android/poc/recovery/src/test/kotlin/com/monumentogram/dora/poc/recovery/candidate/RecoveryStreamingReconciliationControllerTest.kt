package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingExistingEvidence
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRangeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingWitnessInput
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.contract.StreamDecision
import com.monumentogram.dora.poc.recovery.contract.StreamDiagnosticClassification
import com.monumentogram.dora.poc.recovery.contract.StreamTerminal
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

    private sealed interface ReadStep {
        data class Bytes(val value: ByteArray) : ReadStep

        data class ThrowAfterWrite(
            val value: ByteArray,
            val type: RecoveryStreamingSafeExceptionType,
        ) : ReadStep

        data object Zero : ReadStep

        data object Eof : ReadStep
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
            }
        }

        override fun close() = Unit
    }
}
