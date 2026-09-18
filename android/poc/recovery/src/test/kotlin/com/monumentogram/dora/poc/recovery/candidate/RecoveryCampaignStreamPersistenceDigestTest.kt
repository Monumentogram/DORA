package com.monumentogram.dora.poc.recovery.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCampaignStreamPersistenceDigestTest {
    private val rows =
        listOf(
            RecoveryPersistedRow(
                "recovery_stream_checkpoint_v4",
                listOf(
                    RecoveryPersistedCell("generation", RecoveryPersistedSqlType.INTEGER, "1"),
                    RecoveryPersistedCell(
                        "checkpoint_identity",
                        RecoveryPersistedSqlType.BLOB,
                        "aa",
                    ),
                ),
            ),
            RecoveryPersistedRow(
                "recovery_stream_outcome_v4",
                listOf(
                    RecoveryPersistedCell("decision", RecoveryPersistedSqlType.TEXT, "VALID"),
                    RecoveryPersistedCell("state", RecoveryPersistedSqlType.TEXT, "SEALED"),
                ),
            ),
            RecoveryPersistedRow(
                "recovery_stream_range_quarantine_v4",
                listOf(
                    RecoveryPersistedCell("range_start", RecoveryPersistedSqlType.INTEGER, "8192"),
                    RecoveryPersistedCell("range_end", RecoveryPersistedSqlType.INTEGER, "8193"),
                ),
            ),
        )

    @Test
    fun `digest is stable across query row and column ordering`() {
        val expected = RecoveryCampaignStreamPersistenceDigest.sha256(rows)
        val reordered = rows.reversed().map { it.copy(cells = it.cells.reversed()) }
        assertEquals(expected, RecoveryCampaignStreamPersistenceDigest.sha256(reordered))
        assertTrue(expected.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `digest changes for every persisted row and a changed SQLite type`() {
        val baseline = RecoveryCampaignStreamPersistenceDigest.sha256(rows)
        for (index in rows.indices) {
            val changed = rows.toMutableList()
            val original = changed[index]
            changed[index] =
                original.copy(
                    cells =
                        original.cells.toMutableList().also { cells ->
                            cells[0] = cells[0].copy(value = cells[0].value + "x")
                        }
                )
            assertNotEquals(baseline, RecoveryCampaignStreamPersistenceDigest.sha256(changed))
        }
        val changedType = rows.toMutableList()
        changedType[2] =
            rows[2].copy(
                cells =
                    rows[2].cells.toMutableList().also { cells ->
                        cells[0] = cells[0].copy(type = RecoveryPersistedSqlType.TEXT)
                    }
            )
        assertNotEquals(baseline, RecoveryCampaignStreamPersistenceDigest.sha256(changedType))
    }
}
