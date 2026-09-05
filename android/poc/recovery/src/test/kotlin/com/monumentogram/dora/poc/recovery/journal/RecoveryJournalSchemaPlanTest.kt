package com.monumentogram.dora.poc.recovery.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryJournalSchemaPlanTest {
    @Test
    fun `only exact one or two to three upgrades are selected`() {
        assertEquals(
            RecoveryJournalSchema.UpgradePlan.V1_TO_V3,
            RecoveryJournalSchema.upgradePlan(1, 3),
        )
        assertEquals(
            RecoveryJournalSchema.UpgradePlan.V2_TO_V3,
            RecoveryJournalSchema.upgradePlan(2, 3),
        )
        listOf(0 to 3, 1 to 2, 2 to 1, 1 to 1, 3 to 2).forEach { (old, new) ->
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
        assertTrue(
            RecoveryJournalSchema.CREATE_QUARANTINE_TABLE.contains(
                "state TEXT NOT NULL CHECK(state IN ('PENDING','COMPLETED'))"
            )
        )
    }
}
