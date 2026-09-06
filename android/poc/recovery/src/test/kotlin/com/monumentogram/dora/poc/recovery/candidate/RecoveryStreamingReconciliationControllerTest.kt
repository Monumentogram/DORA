package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingExistingEvidence
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
