package com.monumentogram.dora.poc.recovery.journal

import org.junit.Assert.assertEquals
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
}
