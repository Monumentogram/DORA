package com.monumentogram.dora.poc.recovery.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryJournalSchemaPlanTest {
    @Test
    fun `only exact one to two upgrade is selected`() {
        assertEquals(
            RecoveryJournalSchema.UpgradePlan.V1_TO_V2,
            RecoveryJournalSchema.upgradePlan(1, 2),
        )
        listOf(0 to 2, 1 to 3, 2 to 3, 2 to 1, 1 to 1).forEach { (old, new) ->
            assertEquals(
                RecoveryJournalSchema.UpgradePlan.REJECT,
                RecoveryJournalSchema.upgradePlan(old, new),
            )
        }
    }

    @Test
    fun `publication and unit DDL retain protocol row constraints`() {
        assertTrue(
            RecoveryJournalSchema.CREATE_PUBLICATION_TABLE.contains(
                "publication_kind TEXT NOT NULL CHECK(publication_kind='MANIFEST')"
            )
        )
        assertTrue(
            RecoveryJournalSchema.CREATE_UNIT_TABLE.contains(
                "plaintext_end - plaintext_start <= cadence_seconds * 32000"
            )
        )
    }
}
