package com.monumentogram.dora.poc.recovery.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecoveryCampaignStreamPersistenceSummaryTest {
    private val run =
        RecoveryPersistedRow(
            "recovery_run_bootstrap_v1",
            listOf(RecoveryPersistedCell("run_id", RecoveryPersistedSqlType.TEXT, "synthetic-run")),
        )
    private val checkpoint =
        RecoveryPersistedRow(
            "recovery_stream_checkpoint_v4",
            listOf(
                RecoveryPersistedCell("committed_end", RecoveryPersistedSqlType.INTEGER, "4056")
            ),
        )
    private val outcome =
        RecoveryPersistedRow(
            "recovery_stream_outcome_v4",
            listOf(
                RecoveryPersistedCell("decision", RecoveryPersistedSqlType.TEXT, "VALID"),
                RecoveryPersistedCell("state", RecoveryPersistedSqlType.TEXT, "SEALED"),
            ),
        )
    private val range =
        RecoveryPersistedRow(
            "recovery_stream_range_quarantine_v4",
            listOf(
                RecoveryPersistedCell("state", RecoveryPersistedSqlType.TEXT, "ACTIVE"),
                RecoveryPersistedCell("range_start", RecoveryPersistedSqlType.INTEGER, "8192"),
                RecoveryPersistedCell("range_end", RecoveryPersistedSqlType.INTEGER, "8193"),
                RecoveryPersistedCell(
                    "boundary_certainty",
                    RecoveryPersistedSqlType.TEXT,
                    "EXACT_FORMAT_BOUNDARY",
                ),
            ),
        )

    @Test
    fun `derives active range and sealed valid count from rows`() {
        val summary =
            RecoveryCampaignStreamPersistenceSummary.observe(
                listOf(run),
                listOf(checkpoint),
                listOf(outcome),
                listOf(range),
            )
        assertEquals(1, summary.sealedValidOutcomes)
        assertEquals(1, summary.activeRanges)
        assertEquals(8192L, summary.activeRangeStart)
        assertEquals(8193L, summary.activeRangeEnd)
        assertEquals("EXACT_FORMAT_BOUNDARY", summary.activeRangeCertainty)
        assertEquals(1, summary.totalOutcomeRows)
        assertEquals(1, summary.rangeRows)
        assertEquals(1, summary.checkpointRows)
        assertEquals(1, summary.runRows)
    }

    @Test
    fun `missing range cannot produce a fabricated active boundary`() {
        val summary =
            RecoveryCampaignStreamPersistenceSummary.observe(
                listOf(run),
                listOf(checkpoint),
                listOf(outcome),
                emptyList(),
            )
        assertEquals(0, summary.activeRanges)
        assertNull(summary.activeRangeStart)
        assertNull(summary.activeRangeEnd)
        assertNull(summary.activeRangeCertainty)
    }

    @Test
    fun `digest includes bootstrap checkpoint outcome and range rows`() {
        val baseline =
            RecoveryCampaignStreamPersistenceSummary.observe(
                listOf(run),
                listOf(checkpoint),
                listOf(outcome),
                listOf(range),
            )
        for (missing in 0..3) {
            val groups = listOf(listOf(run), listOf(checkpoint), listOf(outcome), listOf(range))
            val changed = groups.mapIndexed { index, rows ->
                if (index == missing) emptyList() else rows
            }
            val observed =
                RecoveryCampaignStreamPersistenceSummary.observe(
                    changed[0],
                    changed[1],
                    changed[2],
                    changed[3],
                )
            assertNotEquals(baseline.persistedStateDigest, observed.persistedStateDigest)
        }
    }
}
