package com.monumentogram.dora.poc.recovery.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
}
