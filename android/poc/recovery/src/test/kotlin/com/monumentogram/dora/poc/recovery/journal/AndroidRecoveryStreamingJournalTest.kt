@file:Suppress(
    "CyclomaticComplexMethod",
    "LargeClass",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "TooManyFunctions",
    "UseCheckOrError",
)

package com.monumentogram.dora.poc.recovery.journal

import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointIdentityInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingExistingEvidence
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalClassification
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalReadResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeAttempt
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeIdentityInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRangeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRejectedObservationInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingWitnessInput
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.contract.StreamBoundaryResult
import com.monumentogram.dora.poc.recovery.contract.StreamCheckpointIntersection
import com.monumentogram.dora.poc.recovery.contract.StreamDecision
import com.monumentogram.dora.poc.recovery.contract.StreamDiagnosticBranch
import com.monumentogram.dora.poc.recovery.contract.StreamDiagnosticClassification
import com.monumentogram.dora.poc.recovery.contract.StreamDiagnosticStage
import com.monumentogram.dora.poc.recovery.contract.StreamRangeCertainty
import com.monumentogram.dora.poc.recovery.contract.StreamSemanticOutcome
import com.monumentogram.dora.poc.recovery.contract.StreamSourceMatch
import com.monumentogram.dora.poc.recovery.contract.StreamTerminal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AndroidRecoveryStreamingJournalTest {
    @Test
    fun `SQL codec pins exact 21 51 17 mappings and round trips all columns`() {
        val checkpoint = checkpoint()
        val attempt = validAuthenticationFailure()
        val checkpointCells = RecoveryStreamingSqlCodec.encodeCheckpoint(checkpoint)
        val outcomeCells = RecoveryStreamingSqlCodec.encodeOutcome(attempt.outcome)
        val rangeCells = RecoveryStreamingSqlCodec.encodeRange(requireNotNull(attempt.range))

        assertEquals(21, RecoveryStreamingSqlCodec.CHECKPOINT_COLUMNS.size)
        assertEquals(51, RecoveryStreamingSqlCodec.OUTCOME_COLUMNS.size)
        assertEquals(17, RecoveryStreamingSqlCodec.RANGE_COLUMNS.size)
        assertEquals(
            "run_id,candidate_id,publication_kind,generation,durable_non_final_segment_count," +
                "stream_ciphertext_prefix_bytes,stream_ciphertext_prefix_sha256,committed_end," +
                "checkpoint_relative_name,checkpoint_bytes,checkpoint_sha256," +
                "checkpoint_key_envelope_relative_name,checkpoint_key_envelope_bytes," +
                "checkpoint_key_envelope_sha256,stream_ciphertext_relative_name," +
                "stream_key_envelope_relative_name,stream_key_envelope_bytes," +
                "stream_key_envelope_sha256,previous_checkpoint_sha256,checkpoint_identity,state",
            RecoveryStreamingSqlCodec.CHECKPOINT_COLUMNS.joinToString(","),
        )
        assertEquals(
            "outcome_id,run_id,candidate_id,checkpoint_generation,checkpoint_identity," +
                "checkpoint_context_end,checkpoint_prefix_bytes,checkpoint_artifact_state," +
                "source_witness_id,witness_capability_state,controller_snapshot_sha256," +
                "oracle_identity_sha256,oracle_plaintext_sha256,accepted_end,source_relative_name," +
                "pre_fault_source_bytes,pre_fault_source_sha256,observed_source_bytes," +
                "observed_source_sha256,pre_fault_source_match_state,checkpoint_intersection_state," +
                "decision,diagnostic_branch,terminal_outcome,recovered_end," +
                "recovered_beyond_checkpoint_bytes,tail_loss_bytes,returned_plaintext_sha256," +
                "remainder_boundary_bytes,remainder_certainty,rejected_candidate_end," +
                "rejected_completed_plaintext_sha256,rejected_oracle_prefix_sha256," +
                "rejected_oracle_prefix_equal,rejected_compared_end,rejected_first_mismatch_offset," +
                "rejected_equal_prefix_sha256,rejected_expected_oracle_byte," +
                "rejected_observed_plaintext_byte,rejected_observed_tail_loss_bytes," +
                "rejected_boundary_result,rejected_boundary_bytes,rejected_observation_sha256," +
                "required_range_start,required_range_certainty,diagnostic_stage," +
                "diagnostic_classification,metadata_adopted,semantic_commit_adopted," +
                "processing_intent_adopted,state",
            RecoveryStreamingSqlCodec.OUTCOME_COLUMNS.joinToString(","),
        )
        assertEquals(
            "range_intent_id,outcome_id,run_id,candidate_id,outcome_decision,outcome_branch," +
                "outcome_terminal,outcome_classification,source_relative_name,source_bytes," +
                "source_sha256,range_start,range_end,range_sha256,boundary_certainty,disposition,state",
            RecoveryStreamingSqlCodec.RANGE_COLUMNS.joinToString(","),
        )
        assertCheckpointEqual(
            checkpoint,
            RecoveryStreamingSqlCodec.decodeCheckpoint(checkpointCells),
        )
        assertOutcomeEqual(attempt.outcome, RecoveryStreamingSqlCodec.decodeOutcome(outcomeCells))
        assertRangeEqual(
            requireNotNull(attempt.range),
            RecoveryStreamingSqlCodec.decodeRange(rangeCells),
        )
        assertTrue(outcomeCells[24] is StreamingSqliteCell.Integer)
        assertTrue(outcomeCells[30] is StreamingSqliteCell.Null)
    }

    @Test
    fun `strict SQL decoding rejects wrong types null substitutions constants enums paths arithmetic and identities`() {
        val checkpointCells = RecoveryStreamingSqlCodec.encodeCheckpoint(checkpoint())
        val outcomeCells =
            RecoveryStreamingSqlCodec.encodeOutcome(validAuthenticationFailure().outcome)
        val rangeCells =
            RecoveryStreamingSqlCodec.encodeRange(
                requireNotNull(validAuthenticationFailure().range)
            )

        val mutations =
            listOf<() -> Unit>(
                {
                    RecoveryStreamingSqlCodec.decodeCheckpoint(
                        checkpointCells.replaced(3, StreamingSqliteCell.Real(1.0))
                    )
                },
                {
                    RecoveryStreamingSqlCodec.decodeCheckpoint(
                        checkpointCells.replaced(3, StreamingSqliteCell.Integer(-1))
                    )
                },
                {
                    RecoveryStreamingSqlCodec.decodeCheckpoint(
                        checkpointCells.replaced(1, StreamingSqliteCell.Text("OTHER"))
                    )
                },
                {
                    RecoveryStreamingSqlCodec.decodeCheckpoint(
                        checkpointCells.replaced(8, StreamingSqliteCell.Text("checkpoints/g-1.ct"))
                    )
                },
                {
                    RecoveryStreamingSqlCodec.decodeCheckpoint(
                        checkpointCells.replaced(5, StreamingSqliteCell.Integer(4097))
                    )
                },
                {
                    RecoveryStreamingSqlCodec.decodeCheckpoint(
                        checkpointCells.replaced(19, StreamingSqliteCell.Blob(ByteArray(32)))
                    )
                },
                {
                    RecoveryStreamingSqlCodec.decodeOutcome(
                        outcomeCells.replaced(
                            1,
                            StreamingSqliteCell.Text("00000000-0000-0000-0000-00000000000A"),
                        )
                    )
                },
                {
                    RecoveryStreamingSqlCodec.decodeOutcome(
                        outcomeCells.replaced(21, StreamingSqliteCell.Text("UNKNOWN"))
                    )
                },
                {
                    RecoveryStreamingSqlCodec.decodeOutcome(
                        outcomeCells.replaced(27, StreamingSqliteCell.Null)
                    )
                },
                {
                    RecoveryStreamingSqlCodec.decodeOutcome(
                        outcomeCells.replaced(47, StreamingSqliteCell.Integer(1))
                    )
                },
                {
                    RecoveryStreamingSqlCodec.decodeOutcome(
                        outcomeCells.replaced(0, StreamingSqliteCell.Blob(ByteArray(31)))
                    )
                },
                {
                    RecoveryStreamingSqlCodec.decodeRange(
                        rangeCells.replaced(9, StreamingSqliteCell.Null)
                    )
                },
                {
                    RecoveryStreamingSqlCodec.decodeRange(
                        rangeCells.replaced(12, StreamingSqliteCell.Integer(8192))
                    )
                },
                {
                    RecoveryStreamingSqlCodec.decodeRange(
                        rangeCells.replaced(16, StreamingSqliteCell.Text("UNKNOWN"))
                    )
                },
            )

        mutations.forEach(::assertIllegalArgument)
    }

    @Test
    fun `adapter strict decode failure stays structural through public witness lookup`() {
        val malformed =
            RecoveryStreamingSqlCodec.encodeOutcome(validAuthenticationFailure().outcome)
                .replaced(21, StreamingSqliteCell.Text("UNKNOWN"))
        val decodeFailure =
            try {
                decodeStreamingJournalRow {
                    RecoveryStreamingSqlCodec.decodeOutcome(malformed)
                }
                fail("Expected strict decoding to fail")
                error("unreachable")
            } catch (failure: Throwable) {
                failure
            }
        val attempt = validAuthenticationFailure()

        val result =
            AndroidRecoveryStreamingJournal(RecordingDatabase(witnessFailure = decodeFailure))
                .outcomeByWitness(
                    attempt.outcome.runId,
                    attempt.outcome.checkpointIdentity,
                    attempt.outcome.sourceWitnessId,
                )

        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
            readFatal(result).classification,
        )
    }

    @Test
    fun `adapter strict decode failures stay structural through every keyed lookup and reconciliation`() {
        val attempt = validAuthenticationFailure()
        val malformedOutcome =
            RecoveryStreamingSqlCodec.encodeOutcome(attempt.outcome)
                .replaced(21, StreamingSqliteCell.Text("UNKNOWN"))
        val malformedRange =
            RecoveryStreamingSqlCodec.encodeRange(requireNotNull(attempt.range))
                .replaced(16, StreamingSqliteCell.Text("UNKNOWN"))
        val outcomeDecodeFailure = strictDecodeFailure {
            RecoveryStreamingSqlCodec.decodeOutcome(malformedOutcome)
        }
        val rangeDecodeFailure = strictDecodeFailure {
            RecoveryStreamingSqlCodec.decodeRange(malformedRange)
        }

        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
            readFatal(
                    AndroidRecoveryStreamingJournal(
                            RecordingDatabase(outcomeIdFailure = outcomeDecodeFailure)
                        )
                        .outcomeById(attempt.outcome.outcomeId)
                )
                .classification,
        )
        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
            readFatal(
                    AndroidRecoveryStreamingJournal(
                            RecordingDatabase(witnessFailure = outcomeDecodeFailure)
                        )
                        .outcomeByWitness(
                            attempt.outcome.runId,
                            attempt.outcome.checkpointIdentity,
                            attempt.outcome.sourceWitnessId,
                        )
                )
                .classification,
        )
        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
            readFatal(
                    AndroidRecoveryStreamingJournal(
                            RecordingDatabase(rangeOutcomeFailure = rangeDecodeFailure)
                        )
                        .rangeByOutcome(attempt.outcome.outcomeId)
                )
                .classification,
        )
        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
            fatal(
                    AndroidRecoveryStreamingJournal(
                            RecordingDatabase(rangeIdFailure = rangeDecodeFailure)
                        )
                        .persistOutcome(attempt)
                )
                .classification,
        )
        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
            fatal(
                    AndroidRecoveryStreamingJournal(
                            RecordingDatabase(rangeSourceFailure = rangeDecodeFailure)
                        )
                        .persistOutcome(attempt)
                )
                .classification,
        )
    }

    @Test
    fun `checkpoint decoder accepts q boundaries and rejects one over`() {
        listOf(0UL, 1UL, 2UL, 3UL, 28_236UL).forEachIndexed { index, segments ->
            val checkpoint = checkpointForSegments(index.toULong() + 1UL, segments)
            assertCheckpointEqual(
                checkpoint,
                RecoveryStreamingSqlCodec.decodeCheckpoint(
                    RecoveryStreamingSqlCodec.encodeCheckpoint(checkpoint)
                ),
            )
        }
        val maximum = checkpointForSegments(1UL, 28_236UL)
        val cells = RecoveryStreamingSqlCodec.encodeCheckpoint(maximum).toMutableList()
        cells[4] = StreamingSqliteCell.Integer(28_237)
        cells[5] = StreamingSqliteCell.Integer(28_237L * 4_096L)
        assertIllegalArgument { RecoveryStreamingSqlCodec.decodeCheckpoint(cells) }
    }

    @Test
    fun `all persistable decision branches enforce their exact child matrix`() {
        val attempts =
            listOf(
                validAuthenticationFailure(),
                validAuthenticationFailure(observedEnd = 8192UL),
                validEof(),
                preIntersection(
                    StreamDiagnosticClassification.STREAM_CHECKPOINT_PREFIX_OUTSIDE_WITNESS
                ),
                preIntersection(StreamDiagnosticClassification.STREAM_SOURCE_TRUNCATED),
                preIntersection(
                    StreamDiagnosticClassification.STREAM_SOURCE_PREFIX_IDENTITY_MISMATCH
                ),
                postIntersection(
                    StreamDiagnosticClassification.STREAM_RETURNED_BYTE_ORACLE_MISMATCH
                ),
                postIntersection(StreamDiagnosticClassification.STREAM_RECOVERED_BELOW_CHECKPOINT),
                postIntersection(
                    StreamDiagnosticClassification.STREAM_RECOVERED_BELOW_CHECKPOINT,
                    StreamTerminal.AUTHENTICATED_EOF,
                ),
                postIntersection(StreamDiagnosticClassification.STREAM_REMAINDER_BOUNDARY_UNPROVEN),
                postIntersection(StreamDiagnosticClassification.STREAM_TAIL_BOUND_EXCEEDED),
                postIntersection(
                    StreamDiagnosticClassification.STREAM_TAIL_BOUND_EXCEEDED,
                    StreamTerminal.AUTHENTICATED_EOF,
                ),
                postIntersection(
                    StreamDiagnosticClassification.STREAM_TAIL_BOUND_EXCEEDED,
                    equalityEnd = true,
                ),
            )

        assertEquals(13, attempts.size)
        attempts.forEach { attempt ->
            RecoveryStreamingSqlCodec.decodeOutcome(
                RecoveryStreamingSqlCodec.encodeOutcome(attempt.outcome)
            )
            attempt.range?.let {
                RecoveryStreamingSqlCodec.validateParentChild(attempt.outcome, it)
                RecoveryStreamingSqlCodec.decodeRange(RecoveryStreamingSqlCodec.encodeRange(it))
            }
        }
        assertTrue(attempts[0].range != null)
        assertTrue(attempts[1].range == null)
        assertTrue(attempts[2].range == null)
        assertTrue(attempts[3].range != null)
        assertTrue(attempts[8].range == null)
        assertTrue(attempts[11].range == null)
        assertTrue(attempts[12].range == null)
    }

    @Test
    fun `invalid parent and child matrices are rejected before any lookup or transaction`() {
        val valid = validAuthenticationFailure()
        assertIllegalArgument {
            RecoveryStreamingOutcomeAttempt(
                valid.outcome,
                null,
                StreamSemanticOutcome.PERSISTED_VALID,
            )
        }
        assertIllegalArgument {
            RecoveryStreamingOutcomeAttempt(
                valid.outcome,
                requireNotNull(valid.range).copy(sourceBytes = 8194UL),
                StreamSemanticOutcome.PERSISTED_VALID,
            )
        }
        assertIllegalArgument {
            val input = valid.outcome.identityInput().copy(tailLossBytes = 2UL)
            RecoveryStreamingOutcomeRow.from(input)
        }
        assertIllegalArgument {
            val invalidBase =
                valid.outcome
                    .witness()
                    .copy(
                        checkpointPrefixBytes = 4_096UL,
                        controllerSnapshotSha256 = null,
                    )
            val invalidWitness =
                invalidBase.copy(
                    controllerSnapshotSha256 =
                        RecoveryStreamingIdentity.controllerSnapshot(invalidBase)
                )
            RecoveryStreamingOutcomeRow.from(
                valid.outcome.identityInput().copy(witness = invalidWitness)
            )
        }
        assertIllegalArgument {
            val original =
                postIntersection(StreamDiagnosticClassification.STREAM_TAIL_BOUND_EXCEEDED).outcome
            val observation = requireNotNull(original.rejectedObservation)
            RecoveryStreamingOutcomeRow.from(
                original
                    .identityInput()
                    .copy(
                        rejectedObservation =
                            observation.copy(comparedEnd = observation.candidateEnd + 1UL)
                    )
            )
        }
    }

    @Test
    fun `atomic persistence ends exactly once at every transaction boundary`() {
        val attempt = validAuthenticationFailure()
        val success = RecordingDatabase()
        assertTrue(
            AndroidRecoveryStreamingJournal(success).persistOutcome(attempt)
                is RecoveryStreamingJournalResult.Receipt
        )
        assertEquals(listOf("outcome", "range", "successful", "end"), success.transactionEvents)
        assertEquals(1, success.endCount)

        listOf(FailurePoint.OUTCOME, FailurePoint.RANGE, FailurePoint.SUCCESSFUL).forEach { point ->
            val database = RecordingDatabase(failurePoint = point)
            val result = AndroidRecoveryStreamingJournal(database).persistOutcome(attempt)
            assertEquals(
                RecoveryStreamingJournalResult.Original(StreamSemanticOutcome.PERSISTED_VALID),
                result,
            )
            assertEquals(1, database.endCount)
            assertTrue(database.outcomes.isEmpty())
            assertTrue(database.ranges.isEmpty())
        }
    }

    @Test
    fun `exact replay with and without range performs every mandatory query and no mutation`() {
        listOf(validAuthenticationFailure(), validEof()).forEach { attempt ->
            val database = RecordingDatabase()
            val journal = AndroidRecoveryStreamingJournal(database)
            journal.persistOutcome(attempt)
            database.queryEvents.clear()
            database.transactionEvents.clear()

            val result = journal.persistOutcome(attempt)

            assertEquals(
                RecoveryStreamingJournalResult.Receipt(
                    attempt.outcome.outcomeId,
                    attempt.range?.rangeIntentId,
                    true,
                ),
                result,
            )
            assertTrue("outcome-id" in database.queryEvents)
            assertTrue("outcome-witness" in database.queryEvents)
            assertTrue("range-outcome" in database.queryEvents)
            if (attempt.range != null) {
                assertTrue("range-id" in database.queryEvents)
                assertTrue("range-source" in database.queryEvents)
            }
            assertTrue(database.transactionEvents.isEmpty())
        }
    }

    @Test
    fun `outcome ID witness and disagreeing lookup collisions classify without mutation`() {
        val attempt = validAuthenticationFailure()
        val other = validAuthenticationFailure(returnedDigest = "returned-other")
        val outcomeIdCollision = RecordingDatabase(outcomeIdOverride = listOf(other.outcome))
        val witnessCollision = RecordingDatabase(witnessOverride = listOf(other.outcome))
        val disagreement =
            RecordingDatabase(
                outcomeIdOverride = listOf(attempt.outcome),
                witnessOverride = listOf(other.outcome),
            )

        val results =
            listOf(outcomeIdCollision, witnessCollision, disagreement).map {
                AndroidRecoveryStreamingJournal(it).persistOutcome(attempt)
            }

        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_ATTEMPT_CONFLICT,
            fatal(results[0]).classification,
        )
        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_ATTEMPT_CONFLICT,
            fatal(results[1]).classification,
        )
        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
            fatal(results[2]).classification,
        )
        assertEquals(
            listOf(RecoveryStreamingExistingEvidence.Outcome(other.outcome.outcomeId)),
            fatal(results[0]).existingEvidence,
        )
        assertEquals(
            listOf(RecoveryStreamingExistingEvidence.Outcome(other.outcome.outcomeId)),
            fatal(results[1]).existingEvidence,
        )
        assertEquals(
            listOf(
                RecoveryStreamingExistingEvidence.Outcome(attempt.outcome.outcomeId),
                RecoveryStreamingExistingEvidence.Outcome(other.outcome.outcomeId),
            ),
            fatal(results[2]).existingEvidence,
        )
        assertTrue(
            listOf(outcomeIdCollision, witnessCollision, disagreement).all {
                it.transactionEvents.isEmpty()
            }
        )
    }

    @Test
    fun `range identity source tuple missing unexpected and parent mismatch are distinguished`() {
        val attempt = validAuthenticationFailure()
        val range = requireNotNull(attempt.range)
        val otherRange = requireNotNull(validAuthenticationFailure(returnedDigest = "other").range)
        val idCollision = RecordingDatabase(rangeIdOverride = listOf(otherRange))
        val sourceTupleCollision = RecordingDatabase(rangeSourceOverride = listOf(otherRange))
        val missing =
            RecordingDatabase(
                outcomeIdOverride = listOf(attempt.outcome),
                witnessOverride = listOf(attempt.outcome),
            )
        val noRange = validEof()
        val unexpected =
            RecordingDatabase(
                outcomeIdOverride = listOf(noRange.outcome),
                witnessOverride = listOf(noRange.outcome),
                rangeOutcomeOverride = listOf(range),
            )
        val mismatch =
            RecordingDatabase(
                outcomeIdOverride = listOf(attempt.outcome),
                witnessOverride = listOf(attempt.outcome),
                rangeOutcomeOverride = listOf(otherRange),
            )

        val idCollisionResult = AndroidRecoveryStreamingJournal(idCollision).persistOutcome(attempt)
        val sourceTupleCollisionResult =
            AndroidRecoveryStreamingJournal(sourceTupleCollision).persistOutcome(attempt)
        assertEquals(
            RecoveryStreamingJournalClassification.STREAM_RANGE_QUARANTINE_COLLISION,
            fatal(idCollisionResult).classification,
        )
        assertEquals(
            RecoveryStreamingJournalClassification.STREAM_RANGE_QUARANTINE_COLLISION,
            fatal(sourceTupleCollisionResult).classification,
        )
        assertEquals(
            listOf(RecoveryStreamingExistingEvidence.Range(otherRange.rangeIntentId)),
            fatal(idCollisionResult).existingEvidence,
        )
        assertEquals(
            listOf(RecoveryStreamingExistingEvidence.Range(otherRange.rangeIntentId)),
            fatal(sourceTupleCollisionResult).existingEvidence,
        )
        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
            fatal(AndroidRecoveryStreamingJournal(missing).persistOutcome(attempt)).classification,
        )
        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
            fatal(AndroidRecoveryStreamingJournal(unexpected).persistOutcome(noRange))
                .classification,
        )
        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
            fatal(AndroidRecoveryStreamingJournal(mismatch).persistOutcome(attempt)).classification,
        )
        assertTrue(
            listOf(idCollision, sourceTupleCollision, missing, unexpected, mismatch).all {
                it.transactionEvents.isEmpty()
            }
        )
    }

    @Test
    fun `ambiguous end reconciles exact conflict and absent states after one end`() {
        val attempt = validAuthenticationFailure()
        val exact = RecordingDatabase(failurePoint = FailurePoint.END_EXACT)
        val conflict =
            RecordingDatabase(
                failurePoint = FailurePoint.END_CONFLICT,
                conflictOutcome = validAuthenticationFailure(returnedDigest = "conflict").outcome,
            )
        val absent = RecordingDatabase(failurePoint = FailurePoint.END_ABSENT)

        val exactResult = AndroidRecoveryStreamingJournal(exact).persistOutcome(attempt)
        val conflictResult = AndroidRecoveryStreamingJournal(conflict).persistOutcome(attempt)
        val absentResult = AndroidRecoveryStreamingJournal(absent).persistOutcome(attempt)

        assertEquals(
            RecoveryStreamingJournalResult.Receipt(
                attempt.outcome.outcomeId,
                attempt.range!!.rangeIntentId,
                true,
            ),
            exactResult,
        )
        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_ATTEMPT_CONFLICT,
            fatal(conflictResult).classification,
        )
        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_COMMIT_STATE_UNRESOLVED,
            retry(absentResult).classification,
        )
        assertEquals(listOf(1, 1, 1), listOf(exact.endCount, conflict.endCount, absent.endCount))
    }

    @Test
    fun `primary transaction failure survives a simultaneous end failure`() {
        val database = RecordingDatabase(failurePoint = FailurePoint.OUTCOME_AND_END)

        val result =
            AndroidRecoveryStreamingJournal(database).persistOutcome(validAuthenticationFailure())

        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_COMMIT_STATE_UNRESOLVED,
            retry(result).classification,
        )
        assertEquals(1, database.endCount)
        assertEquals("outcome", database.primaryFailure?.message)
        assertEquals("end", database.primaryFailure?.suppressed?.singleOrNull()?.message)
    }

    @Test
    fun `operational lookup readback and begin failures are classified`() {
        val attempt = validAuthenticationFailure()
        val lookup = RecordingDatabase(failurePoint = FailurePoint.LOOKUP)
        val begin = RecordingDatabase(failurePoint = FailurePoint.BEGIN)
        val readback = RecordingDatabase(failurePoint = FailurePoint.READBACK)

        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL,
            retry(AndroidRecoveryStreamingJournal(lookup).persistOutcome(attempt)).classification,
        )
        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL,
            retry(AndroidRecoveryStreamingJournal(begin).persistOutcome(attempt)).classification,
        )
        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL,
            retry(AndroidRecoveryStreamingJournal(readback).persistOutcome(attempt)).classification,
        )
        assertEquals(1, readback.endCount)
    }

    @Test
    fun `checkpoint reads classify invalid genesis gap predecessor and split brain without throwing`() {
        val genesis = checkpoint()
        val gap = checkpoint(3UL, genesis.checkpointSha256)
        val badPredecessor = checkpoint(2UL, sha("wrong"))
        val split = checkpoint(1UL, previous = Sha256Value.ZERO, checkpointDigest = "other")
        val cases =
            listOf(
                emptyList(),
                listOf(genesis),
                listOf(genesis, gap),
                listOf(genesis, badPredecessor),
                listOf(genesis, split),
            )

        val results = cases.map {
            AndroidRecoveryStreamingJournal(RecordingDatabase(initialCheckpoints = it))
                .checkpointChain(runId())
        }

        assertTrue(results[0] is RecoveryStreamingJournalReadResult.Value)
        assertTrue(results[1] is RecoveryStreamingJournalReadResult.Value)
        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
            readFatal(results[2]).classification,
        )
        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
            readFatal(results[3]).classification,
        )
        assertEquals(
            RecoveryStreamingJournalClassification.STREAM_CHECKPOINT_SPLIT_BRAIN,
            readFatal(results[4]).classification,
        )
        assertFalse(results.any { it is RecoveryStreamingJournalReadResult.Retry })
    }

    @Test
    fun `checkpoint insert validates proposed chain and reconciles exact replay and split brain`() {
        val first = checkpoint()
        val second = checkpoint(2UL, first.checkpointSha256)
        val database = RecordingDatabase()
        val journal = AndroidRecoveryStreamingJournal(database)

        assertEquals(
            RecoveryStreamingJournalResult.CheckpointReceipt(first.checkpointIdentity, false),
            journal.insertCheckpoint(first),
        )
        assertEquals(
            RecoveryStreamingJournalResult.CheckpointReceipt(first.checkpointIdentity, true),
            journal.insertCheckpoint(first),
        )
        assertEquals(
            RecoveryStreamingJournalResult.CheckpointReceipt(second.checkpointIdentity, false),
            journal.insertCheckpoint(second),
        )
        assertEquals(2, database.transactionEvents.count { it == "checkpoint" })

        val invalidGap =
            AndroidRecoveryStreamingJournal(RecordingDatabase(initialCheckpoints = listOf(first)))
                .insertCheckpoint(checkpoint(3UL, first.checkpointSha256))
        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
            fatal(invalidGap).classification,
        )

        val split = checkpoint(2UL, first.checkpointSha256, checkpointDigest = "split")
        val splitResult =
            AndroidRecoveryStreamingJournal(
                    RecordingDatabase(initialCheckpoints = listOf(first, split))
                )
                .insertCheckpoint(second)
        assertEquals(
            RecoveryStreamingJournalClassification.STREAM_CHECKPOINT_SPLIT_BRAIN,
            fatal(splitResult).classification,
        )
        assertEquals(
            listOf(RecoveryStreamingExistingEvidence.Checkpoint(split.checkpointIdentity)),
            fatal(splitResult).existingEvidence,
        )
    }

    @Test
    fun `operational reads and active range ordering fail closed`() {
        val first = validAuthenticationFailure()
        val second = validAuthenticationFailure(returnedDigest = "tied-range")
        val ranges = listOf(requireNotNull(first.range), requireNotNull(second.range))
        val expected =
            ranges.sortedWith(
                compareBy<RecoveryStreamingRangeRow> { it.rangeStart }
                    .thenBy { it.rangeEnd }
                    .thenBy { it.rangeIntentId.toLowercaseHex() }
            )
        val insertionOrder = expected.asReversed()
        assertTrue(expected != insertionOrder)
        val database = RecordingDatabase()
        database.ranges += insertionOrder
        val journal = AndroidRecoveryStreamingJournal(database)

        val read = journal.activeRanges(first.outcome.runId, "stream/stream.ct")
        val values = (read as RecoveryStreamingJournalReadResult.Value).value
        assertEquals(2, values.size)
        assertEquals(
            requireNotNull(first.range).rangeStart,
            requireNotNull(second.range).rangeStart,
        )
        assertEquals(requireNotNull(first.range).rangeEnd, requireNotNull(second.range).rangeEnd)
        assertFalse(
            requireNotNull(first.range).rangeIntentId == requireNotNull(second.range).rangeIntentId
        )
        assertEquals(expected, values)

        val failure =
            AndroidRecoveryStreamingJournal(
                    RecordingDatabase(failurePoint = FailurePoint.ACTIVE_READ)
                )
                .activeRanges(first.outcome.runId, "stream/stream.ct")
        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL,
            (failure as RecoveryStreamingJournalReadResult.Retry).classification,
        )
    }

    private fun validAuthenticationFailure(
        runSeed: Int = 1,
        observedEnd: ULong = 8_193UL,
        recoveredEnd: ULong = 8_136UL,
        acceptedEnd: ULong = 8_137UL,
        preFaultEnd: ULong = 8_192UL,
        returnedDigest: String = "returned",
    ): RecoveryStreamingOutcomeAttempt {
        val witness = witness(runSeed, acceptedEnd = acceptedEnd, preFaultEnd = preFaultEnd)
        val boundary =
            if (recoveredEnd == 0UL) 0UL else (1UL + (recoveredEnd - 4_056UL) / 4_080UL) * 4_096UL
        return attempt(
            RecoveryStreamingOutcomeIdentityInput.validAuthenticationFailure(
                witness,
                observedEnd,
                sha("source-$observedEnd"),
                recoveredEnd,
                sha(returnedDigest),
                boundary,
            )
        )
    }

    private fun validEof(): RecoveryStreamingOutcomeAttempt {
        val witness = witness()
        return attempt(
            RecoveryStreamingOutcomeIdentityInput(
                witness,
                8_192UL,
                sha("source-eof"),
                StreamSourceMatch.VERIFIED_SAME_DESCRIPTOR,
                StreamCheckpointIntersection.PROVEN,
                StreamDecision.VALID,
                StreamDiagnosticBranch.NONE,
                StreamTerminal.AUTHENTICATED_EOF,
                8_137UL,
                4_081UL,
                0UL,
                sha("returned-eof"),
                null,
                null,
                null,
                null,
                null,
                StreamDiagnosticStage.NONE,
                StreamDiagnosticClassification.NONE,
            )
        )
    }

    private fun preIntersection(
        classification: StreamDiagnosticClassification
    ): RecoveryStreamingOutcomeAttempt {
        val witness =
            if (
                classification ==
                    StreamDiagnosticClassification.STREAM_CHECKPOINT_PREFIX_OUTSIDE_WITNESS
            )
                witness(preFaultEnd = 4_096UL)
            else witness()
        val observed =
            if (classification == StreamDiagnosticClassification.STREAM_SOURCE_TRUNCATED) 8_191UL
            else witness.preFaultSourceBytes
        val stage =
            if (
                classification ==
                    StreamDiagnosticClassification.STREAM_CHECKPOINT_PREFIX_OUTSIDE_WITNESS
            )
                StreamDiagnosticStage.STREAM_CHECKPOINT
            else StreamDiagnosticStage.STREAM_SOURCE_EXTENT
        return attempt(
            RecoveryStreamingOutcomeIdentityInput(
                witness,
                observed,
                sha("pre-$classification"),
                StreamSourceMatch.UNPROVEN_OR_MISMATCH,
                StreamCheckpointIntersection.CONTEXT_ONLY,
                StreamDecision.FATAL,
                StreamDiagnosticBranch.PRE_INTERSECTION,
                StreamTerminal.NOT_REACHED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                observed.takeIf { it > 0UL }?.let { 0UL },
                observed
                    .takeIf { it > 0UL }
                    ?.let { StreamRangeCertainty.CONSERVATIVE_WHOLE_SOURCE },
                stage,
                classification,
            )
        )
    }

    private fun postIntersection(
        classification: StreamDiagnosticClassification,
        terminal: StreamTerminal = StreamTerminal.AUTHENTICATION_FAILURE,
        equalityEnd: Boolean = false,
    ): RecoveryStreamingOutcomeAttempt {
        val tail = classification == StreamDiagnosticClassification.STREAM_TAIL_BOUND_EXCEEDED
        val accepted = if (tail) 12_217UL else 8_137UL
        val preFault = if (tail && equalityEnd) 4_096UL else 8_192UL
        val witness =
            witness(
                acceptedEnd = accepted,
                preFaultEnd = preFault,
                prefix = if (preFault == 4_096UL) 4_096UL else 8_192UL,
            )
        val candidate =
            when (classification) {
                StreamDiagnosticClassification.STREAM_RETURNED_BYTE_ORACLE_MISMATCH -> 4_056UL
                StreamDiagnosticClassification.STREAM_RECOVERED_BELOW_CHECKPOINT -> 0UL
                StreamDiagnosticClassification.STREAM_REMAINDER_BOUNDARY_UNPROVEN -> 4_057UL
                StreamDiagnosticClassification.STREAM_TAIL_BOUND_EXCEEDED -> 4_056UL
                else -> error("unsupported")
            }
        val observed = if (tail && equalityEnd) 4_096UL else 8_193UL
        val equal =
            classification != StreamDiagnosticClassification.STREAM_RETURNED_BYTE_ORACLE_MISMATCH
        val boundary =
            when {
                classification ==
                    StreamDiagnosticClassification.STREAM_RETURNED_BYTE_ORACLE_MISMATCH ->
                    StreamBoundaryResult.NOT_EVALUATED_ORACLE_MISMATCH to null
                terminal == StreamTerminal.AUTHENTICATED_EOF ->
                    StreamBoundaryResult.NOT_APPLICABLE_AUTHENTICATED_EOF to null
                classification ==
                    StreamDiagnosticClassification.STREAM_REMAINDER_BOUNDARY_UNPROVEN ->
                    StreamBoundaryResult.NON_CANONICAL_CANDIDATE_END to null
                else ->
                    StreamBoundaryResult.EXACT_FORMAT_BOUNDARY to
                        if (candidate == 0UL) 0UL else 4_096UL
            }
        val observation = rejected(candidate, accepted, equal, boundary.first, boundary.second)
        val required =
            when {
                terminal == StreamTerminal.AUTHENTICATED_EOF || equalityEnd -> null
                classification ==
                    StreamDiagnosticClassification.STREAM_RETURNED_BYTE_ORACLE_MISMATCH ->
                    0UL to StreamRangeCertainty.CONSERVATIVE_WHOLE_SOURCE
                classification ==
                    StreamDiagnosticClassification.STREAM_RECOVERED_BELOW_CHECKPOINT ->
                    0UL to StreamRangeCertainty.CONSERVATIVE_WHOLE_SOURCE
                classification ==
                    StreamDiagnosticClassification.STREAM_REMAINDER_BOUNDARY_UNPROVEN ->
                    4_096UL to StreamRangeCertainty.CONSERVATIVE_PROVEN_CHECKPOINT_SUPERSET
                else -> 4_096UL to StreamRangeCertainty.EXACT_FORMAT_BOUNDARY
            }
        return attempt(
            RecoveryStreamingOutcomeIdentityInput(
                witness,
                observed,
                sha("post-$classification-$terminal-$equalityEnd"),
                StreamSourceMatch.VERIFIED_SAME_DESCRIPTOR,
                StreamCheckpointIntersection.PROVEN,
                if (tail) StreamDecision.REJECTED else StreamDecision.FATAL,
                StreamDiagnosticBranch.POST_INTERSECTION,
                if (
                    classification ==
                        StreamDiagnosticClassification.STREAM_RETURNED_BYTE_ORACLE_MISMATCH
                )
                    StreamTerminal.COMPLETED_READ_REJECTED
                else terminal,
                null,
                null,
                null,
                null,
                null,
                null,
                observation,
                required?.first,
                required?.second,
                StreamDiagnosticStage.STREAM_PAYLOAD_DECRYPT,
                classification,
            )
        )
    }

    private fun rejected(
        candidate: ULong,
        accepted: ULong,
        equal: Boolean,
        boundary: StreamBoundaryResult,
        boundaryBytes: ULong?,
    ) =
        RecoveryStreamingRejectedObservationInput(
            candidate,
            sha(if (equal) "equal-prefix" else "returned-mismatch"),
            sha(if (equal) "equal-prefix" else "oracle-mismatch"),
            equal,
            candidate,
            if (equal) null else 1UL,
            if (equal) null else sha("equal-before-mismatch"),
            if (equal) null else 1U,
            if (equal) null else 2U,
            accepted - candidate,
            boundary,
            boundaryBytes,
        )

    private fun attempt(
        input: RecoveryStreamingOutcomeIdentityInput
    ): RecoveryStreamingOutcomeAttempt {
        val outcome = RecoveryStreamingOutcomeRow.from(input)
        val range =
            outcome.requiredRangeStart?.let {
                RecoveryStreamingRangeRow.exact(outcome, sha("range-${outcome.outcomeId}"))
            }
        val semantic =
            when (outcome.decision) {
                StreamDecision.VALID -> StreamSemanticOutcome.PERSISTED_VALID
                StreamDecision.REJECTED -> StreamSemanticOutcome.PERSISTED_REJECTED
                StreamDecision.FATAL -> StreamSemanticOutcome.PERSISTED_FATAL
            }
        return RecoveryStreamingOutcomeAttempt(outcome, range, semantic)
    }

    private fun witness(
        runSeed: Int = 1,
        acceptedEnd: ULong = 8_137UL,
        preFaultEnd: ULong = 8_192UL,
        prefix: ULong = 8_192UL,
    ): RecoveryStreamingWitnessInput {
        val runId = runId(runSeed)
        val oracle = sha("oracle-$runSeed-$acceptedEnd")
        val checkpointContext =
            if (prefix < 8_192UL) 0UL else 4_056UL + (prefix / 4_096UL - 2UL) * 4_080UL
        val base =
            RecoveryStreamingWitnessInput(
                runId,
                1UL,
                sha("checkpoint-$runSeed-$prefix"),
                prefix,
                checkpointContext,
                RecoveryStreamingIdentity.oracle(acceptedEnd, oracle, runId),
                acceptedEnd,
                oracle,
                preFaultEnd,
                sha("source-before-$runSeed-$preFaultEnd"),
                null,
            )
        return base.copy(
            controllerSnapshotSha256 = RecoveryStreamingIdentity.controllerSnapshot(base)
        )
    }

    private fun checkpoint(
        generation: ULong = 1UL,
        previous: Sha256Value = Sha256Value.ZERO,
        checkpointDigest: String = "checkpoint",
    ): RecoveryStreamingCheckpointRow {
        val input =
            RecoveryStreamingCheckpointIdentityInput(
                runId(),
                generation,
                2UL,
                8_192UL,
                sha("prefix-$generation"),
                4_056UL,
                "checkpoints/g-${generation.toString().padStart(20, '0')}.ct",
                1UL,
                sha(checkpointDigest),
                "key-envelopes/checkpoint-g-${generation.toString().padStart(20, '0')}.ks",
                1UL,
                sha("checkpoint-key-$generation"),
                "stream/stream.ct",
                "key-envelopes/stream.ks",
                1UL,
                sha("stream-key"),
                previous,
            )
        return RecoveryStreamingCheckpointRow(
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
    }

    private fun checkpointForSegments(
        generation: ULong,
        segments: ULong,
    ): RecoveryStreamingCheckpointRow {
        val prefix = segments * 4_096UL
        val committed = if (segments < 2UL) 0UL else 4_056UL + (segments - 2UL) * 4_080UL
        val input =
            RecoveryStreamingCheckpointIdentityInput(
                runId(),
                generation,
                segments,
                prefix,
                sha("prefix-$segments"),
                committed,
                "checkpoints/g-${generation.toString().padStart(20, '0')}.ct",
                1UL,
                sha("checkpoint-$segments"),
                "key-envelopes/checkpoint-g-${generation.toString().padStart(20, '0')}.ks",
                1UL,
                sha("checkpoint-key-$segments"),
                "stream/stream.ct",
                "key-envelopes/stream.ks",
                1UL,
                sha("stream-key"),
                Sha256Value.ZERO,
            )
        return RecoveryStreamingCheckpointRow(
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
    }

    private fun runId(seed: Int = 1) = RunId.fromBytes(ByteArray(16) { (it + seed).toByte() })

    private fun sha(value: String) = Sha256Value.calculate(value.toByteArray())

    private fun assertCheckpointEqual(
        expected: RecoveryStreamingCheckpointRow,
        actual: RecoveryStreamingCheckpointRow,
    ) =
        assertSqlCellsEqual(
            RecoveryStreamingSqlCodec.encodeCheckpoint(expected),
            RecoveryStreamingSqlCodec.encodeCheckpoint(actual),
        )

    private fun assertOutcomeEqual(
        expected: RecoveryStreamingOutcomeRow,
        actual: RecoveryStreamingOutcomeRow,
    ) =
        assertSqlCellsEqual(
            RecoveryStreamingSqlCodec.encodeOutcome(expected),
            RecoveryStreamingSqlCodec.encodeOutcome(actual),
        )

    private fun assertRangeEqual(
        expected: RecoveryStreamingRangeRow,
        actual: RecoveryStreamingRangeRow,
    ) =
        assertSqlCellsEqual(
            RecoveryStreamingSqlCodec.encodeRange(expected),
            RecoveryStreamingSqlCodec.encodeRange(actual),
        )

    private fun assertSqlCellsEqual(
        expected: List<StreamingSqliteCell>,
        actual: List<StreamingSqliteCell>,
    ) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { index ->
            val left = expected[index]
            val right = actual[index]
            if (left is StreamingSqliteCell.Blob && right is StreamingSqliteCell.Blob)
                assertTrue("blob column $index", left.value.contentEquals(right.value))
            else assertEquals("column $index", left, right)
        }
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {}
    }

    private fun strictDecodeFailure(block: () -> Unit): Throwable {
        var failure: Throwable? = null
        try {
            decodeStreamingJournalRow(block)
        } catch (thrown: Throwable) {
            failure = thrown
        }
        assertTrue("Expected adapter strict decoding to fail", failure is IllegalStateException)
        return requireNotNull(failure)
    }

    private fun fatal(result: RecoveryStreamingJournalResult) =
        result as RecoveryStreamingJournalResult.Fatal

    private fun retry(result: RecoveryStreamingJournalResult) =
        result as RecoveryStreamingJournalResult.Retry

    private fun readFatal(result: RecoveryStreamingJournalReadResult<*>) =
        result as RecoveryStreamingJournalReadResult.Fatal

    private fun List<StreamingSqliteCell>.replaced(index: Int, value: StreamingSqliteCell) =
        toMutableList().also { it[index] = value }

    private enum class FailurePoint {
        NONE,
        LOOKUP,
        BEGIN,
        OUTCOME,
        RANGE,
        SUCCESSFUL,
        END_EXACT,
        END_CONFLICT,
        END_ABSENT,
        OUTCOME_AND_END,
        READBACK,
        ACTIVE_READ,
    }

    private class RecordingDatabase(
        initialCheckpoints: List<RecoveryStreamingCheckpointRow> = emptyList(),
        private val failurePoint: FailurePoint = FailurePoint.NONE,
        private val outcomeIdOverride: List<RecoveryStreamingOutcomeRow>? = null,
        private val outcomeIdFailure: Throwable? = null,
        private val witnessOverride: List<RecoveryStreamingOutcomeRow>? = null,
        private val rangeOutcomeOverride: List<RecoveryStreamingRangeRow>? = null,
        private val rangeOutcomeFailure: Throwable? = null,
        private val rangeIdOverride: List<RecoveryStreamingRangeRow>? = null,
        private val rangeIdFailure: Throwable? = null,
        private val rangeSourceOverride: List<RecoveryStreamingRangeRow>? = null,
        private val rangeSourceFailure: Throwable? = null,
        private val conflictOutcome: RecoveryStreamingOutcomeRow? = null,
        private val witnessFailure: Throwable? = null,
    ) : RecoveryStreamingJournalDatabase {
        val queryEvents = mutableListOf<String>()
        val transactionEvents = mutableListOf<String>()
        val checkpoints = initialCheckpoints.toMutableList()
        val outcomes = mutableListOf<RecoveryStreamingOutcomeRow>()
        val ranges = mutableListOf<RecoveryStreamingRangeRow>()
        var endCount = 0
        var primaryFailure: Throwable? = null
        private var transactionEnded = false

        override fun checkpoints(runId: RunId): List<RecoveryStreamingCheckpointRow> {
            queryEvents += "checkpoints"
            if (failurePoint == FailurePoint.LOOKUP) error("lookup")
            return checkpoints.filter { it.runId == runId }
        }

        override fun outcomeById(id: Sha256Value): List<RecoveryStreamingOutcomeRow> {
            queryEvents += "outcome-id"
            if (failurePoint == FailurePoint.LOOKUP) error("lookup")
            if (failurePoint == FailurePoint.READBACK && transactionEnded) error("readback")
            outcomeIdFailure?.let { throw it }
            return outcomeIdOverride ?: outcomes.filter { it.outcomeId == id }
        }

        override fun outcomesByWitness(
            runId: RunId,
            checkpointIdentity: Sha256Value,
            witnessId: Sha256Value,
        ): List<RecoveryStreamingOutcomeRow> {
            queryEvents += "outcome-witness"
            witnessFailure?.let { throw it }
            return witnessOverride
                ?: outcomes.filter {
                    it.runId == runId &&
                        it.checkpointIdentity == checkpointIdentity &&
                        it.sourceWitnessId == witnessId
                }
        }

        override fun rangesByOutcome(outcomeId: Sha256Value): List<RecoveryStreamingRangeRow> {
            queryEvents += "range-outcome"
            rangeOutcomeFailure?.let { throw it }
            return rangeOutcomeOverride ?: ranges.filter { it.outcomeId == outcomeId }
        }

        override fun activeRanges(runId: RunId, source: String): List<RecoveryStreamingRangeRow> {
            queryEvents += "active"
            if (failurePoint == FailurePoint.ACTIVE_READ) error("active")
            return ranges.filter { it.runId == runId && it.sourceRelativeName == source }
        }

        override fun rangeByIntentId(id: Sha256Value): List<RecoveryStreamingRangeRow> {
            queryEvents += "range-id"
            rangeIdFailure?.let { throw it }
            return rangeIdOverride ?: ranges.filter { it.rangeIntentId == id }
        }

        override fun rangesBySourceTuple(
            row: RecoveryStreamingRangeRow
        ): List<RecoveryStreamingRangeRow> {
            queryEvents += "range-source"
            rangeSourceFailure?.let { throw it }
            return rangeSourceOverride
                ?: ranges.filter {
                    it.runId == row.runId &&
                        it.sourceRelativeName == row.sourceRelativeName &&
                        it.sourceBytes == row.sourceBytes &&
                        it.sourceSha256 == row.sourceSha256 &&
                        it.rangeStart == row.rangeStart &&
                        it.rangeEnd == row.rangeEnd &&
                        it.rangeSha256 == row.rangeSha256
                }
        }

        override fun beginTransactionNonExclusive(): RecoveryStreamingJournalTransaction {
            if (failurePoint == FailurePoint.BEGIN) error("begin")
            val stagedCheckpoints = mutableListOf<RecoveryStreamingCheckpointRow>()
            val stagedOutcomes = mutableListOf<RecoveryStreamingOutcomeRow>()
            val stagedRanges = mutableListOf<RecoveryStreamingRangeRow>()
            var successful = false
            var ended = false
            return object : RecoveryStreamingJournalTransaction {
                override val provenRolledBack: Boolean
                    get() = ended && !successful && failurePoint != FailurePoint.OUTCOME_AND_END

                override fun insertCheckpoint(row: RecoveryStreamingCheckpointRow) {
                    transactionEvents += "checkpoint"
                    stagedCheckpoints += row
                }

                override fun insertOutcome(row: RecoveryStreamingOutcomeRow) {
                    transactionEvents += "outcome"
                    if (
                        failurePoint == FailurePoint.OUTCOME ||
                            failurePoint == FailurePoint.OUTCOME_AND_END
                    ) {
                        primaryFailure = IllegalStateException("outcome")
                        throw requireNotNull(primaryFailure)
                    }
                    stagedOutcomes += row
                }

                override fun insertRange(row: RecoveryStreamingRangeRow) {
                    transactionEvents += "range"
                    if (failurePoint == FailurePoint.RANGE) error("range")
                    stagedRanges += row
                }

                override fun setSuccessful() {
                    transactionEvents += "successful"
                    if (failurePoint == FailurePoint.SUCCESSFUL) error("successful")
                    successful = true
                }

                override fun end() {
                    check(!ended) { "ended twice" }
                    ended = true
                    transactionEnded = true
                    endCount++
                    transactionEvents += "end"
                    when (failurePoint) {
                        FailurePoint.END_EXACT -> {
                            outcomes += stagedOutcomes
                            ranges += stagedRanges
                            checkpoints += stagedCheckpoints
                            throw IllegalStateException("end")
                        }
                        FailurePoint.END_CONFLICT -> {
                            outcomes += requireNotNull(conflictOutcome)
                            throw IllegalStateException("end")
                        }
                        FailurePoint.END_ABSENT -> throw IllegalStateException("end")
                        FailurePoint.OUTCOME_AND_END -> {
                            val end = IllegalStateException("end")
                            throw end
                        }
                        else ->
                            if (successful) {
                                outcomes += stagedOutcomes
                                ranges += stagedRanges
                                checkpoints += stagedCheckpoints
                            }
                    }
                }
            }
        }
    }
}
