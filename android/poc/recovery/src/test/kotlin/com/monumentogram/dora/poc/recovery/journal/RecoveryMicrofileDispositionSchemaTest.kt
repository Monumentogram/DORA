package com.monumentogram.dora.poc.recovery.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RecoveryMicrofileDispositionSchemaTest {
    @Test
    fun `every supported historical version has an explicit route to six`() {
        for (old in 1..5) {
            assertNotEquals(
                "Missing migration from version $old",
                RecoveryJournalSchema.UpgradePlan.REJECT,
                RecoveryJournalSchema.upgradePlan(old, 6),
            )
        }
        assertEquals(6, RecoveryJournalSchema.VERSION)
    }

    @Test
    fun `unknown version and downgrade cannot select the schema six path`() {
        for ((old, new) in listOf(0 to 6, 6 to 6, 7 to 6, 6 to 5, 6 to 4, 5 to 7)) {
            assertEquals(
                "$old to $new must be rejected",
                RecoveryJournalSchema.UpgradePlan.REJECT,
                RecoveryJournalSchema.upgradePlan(old, new),
            )
        }
    }

    @Test
    fun `historical migration selectors keep their accepted paths`() {
        for (old in 1..3) {
            assertNotEquals(
                RecoveryJournalSchema.UpgradePlan.REJECT,
                RecoveryJournalSchema.upgradePlan(old, 4),
            )
        }
        for (old in 1..4) {
            assertNotEquals(
                RecoveryJournalSchema.UpgradePlan.REJECT,
                RecoveryJournalSchema.upgradePlan(old, 5),
            )
        }
    }
}
