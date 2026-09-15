package com.monumentogram.dora.poc.recovery.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RecoveryStreamPrefixSchemaTest {
    @Test
    fun `current schema admits non destructive upgrade from every supported historical version`() {
        assertEquals(5, RecoveryJournalSchema.VERSION)
        (1..4).forEach { old ->
            assertNotEquals(
                RecoveryJournalSchema.UpgradePlan.REJECT,
                RecoveryJournalSchema.upgradePlan(old, 5),
            )
        }
    }
}
