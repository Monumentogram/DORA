package com.monumentogram.dora.poc.recovery.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryJournalSchemaPlanTest {
    @Test
    fun `only exact one two or three to four upgrades are selected`() {
        assertEquals(
            RecoveryJournalSchema.UpgradePlan.V1_TO_V4,
            RecoveryJournalSchema.upgradePlan(1, 4),
        )
        assertEquals(
            RecoveryJournalSchema.UpgradePlan.V2_TO_V4,
            RecoveryJournalSchema.upgradePlan(2, 4),
        )
        assertEquals(
            RecoveryJournalSchema.UpgradePlan.V3_TO_V4,
            RecoveryJournalSchema.upgradePlan(3, 4),
        )
        listOf(0 to 4, 1 to 3, 2 to 3, 4 to 3, 1 to 1, 3 to 2).forEach { (old, new) ->
            assertEquals(
                RecoveryJournalSchema.UpgradePlan.REJECT,
                RecoveryJournalSchema.upgradePlan(old, new),
            )
        }
    }

    @Test
    fun `schema four defines exact persistent object families`() {
        assertEquals(4, RecoveryJournalSchema.VERSION)
        assertEquals("recovery_quarantine_intent_v3", RecoveryJournalSchema.QUARANTINE_V3_TABLE)
        assertEquals("recovery_quarantine_intent_v4", RecoveryJournalSchema.QUARANTINE_TABLE)
        assertEquals("recovery_stream_checkpoint_v4", RecoveryJournalSchema.STREAM_CHECKPOINT_TABLE)
        assertEquals("recovery_stream_outcome_v4", RecoveryJournalSchema.STREAM_OUTCOME_TABLE)
        assertEquals(
            "recovery_stream_range_quarantine_v4",
            RecoveryJournalSchema.STREAM_RANGE_TABLE,
        )
        assertTrue(
            RecoveryJournalSchema.CREATE_STREAM_ACTIVE_RANGE_INDEX.replace(Regex("\\s+"), "")
                .contains("source_relative_name,state,range_start,range_end")
        )
        assertEquals(24, RecoveryJournalSchema.EXACT_V4_OBJECTS.size)
        assertTrue(
            RecoveryJournalSchema.EXACT_V4_OBJECTS.contains(
                RecoveryJournalSchema.SqliteObject(
                    "index",
                    "sqlite_autoindex_recovery_stream_range_quarantine_v4_3",
                    "recovery_stream_range_quarantine_v4",
                )
            )
        )
        assertTrue(
            RecoveryJournalSchema.EXACT_V4_OBJECTS.contains(
                RecoveryJournalSchema.SqliteObject(
                    "index",
                    "recovery_stream_active_range_v4",
                    "recovery_stream_range_quarantine_v4",
                )
            )
        )
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
            RecoveryJournalSchema.CREATE_QUARANTINE_V3_TABLE.contains(
                "state TEXT NOT NULL CHECK(state IN ('PENDING','COMPLETED'))"
            )
        )
        assertTrue(
            RecoveryJournalSchema.CREATE_QUARANTINE_TABLE.replace(Regex("\\s+"), " ")
                .contains(
                    "candidate_id TEXT NOT NULL CHECK(candidate_id IN " +
                        "('REC-STREAM-TINK','REC-MICROFILE-TINK'))"
                )
        )
        assertTrue(
            RecoveryJournalSchema.CREATE_STREAM_OUTCOME_TABLE.contains("recovered_end IS NULL")
        )
        assertTrue(
            RecoveryJournalSchema.CREATE_STREAM_RANGE_TABLE.replace(Regex("\\s+"), "")
                .contains(
                    "FOREIGNKEY(outcome_id,run_id,candidate_id,source_bytes," +
                        "source_sha256,outcome_decision"
                )
        )
    }

    @Test
    fun `v3 to v4 migration exposes every rollback failpoint in mutation order`() {
        assertEquals(
            listOf(
                RecoveryJournalSchema.V3ToV4Step.AFTER_PREFLIGHT,
                RecoveryJournalSchema.V3ToV4Step.AFTER_CREATE_QUARANTINE,
                RecoveryJournalSchema.V3ToV4Step.AFTER_COPY,
                RecoveryJournalSchema.V3ToV4Step.AFTER_DESTINATION_VERIFY,
                RecoveryJournalSchema.V3ToV4Step.AFTER_CREATE_CHECKPOINT,
                RecoveryJournalSchema.V3ToV4Step.AFTER_CREATE_OUTCOME,
                RecoveryJournalSchema.V3ToV4Step.AFTER_CREATE_RANGE,
                RecoveryJournalSchema.V3ToV4Step.AFTER_CREATE_RANGE_INDEX,
                RecoveryJournalSchema.V3ToV4Step.AFTER_DROP_V3,
            ),
            RecoveryJournalSchema.V3_TO_V4_STEPS,
        )
    }
}
