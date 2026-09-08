package com.monumentogram.dora.poc.recovery.candidate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalClassification
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalResult
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryStreamingJournal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android SQLite/adapter coverage. Execution remains part of the gated device run. */
@RunWith(AndroidJUnit4::class)
class RecoveryCheckpointForeignKeyInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val journal = AndroidRecoveryStreamingJournal(context)

    @Test
    fun orphanCheckpointIsRejectedByTheRealAdapter() {
        val runId = RunId.fromBytes(ByteArray(16) { (it + 41).toByte() })
        RecoveryCheckpointAndroidTestFixture.cleanupBestEffort(context, runId)
        try {
            val result =
                journal.insertCheckpoint(RecoveryCheckpointAndroidTestFixture.checkpoint(runId))
            assertTrue(result is RecoveryStreamingJournalResult.Retry)
            result as RecoveryStreamingJournalResult.Retry
            assertEquals(
                RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL,
                result.classification,
            )
            assertFalse(RecoveryCheckpointAndroidTestFixture.bootstrapParentExists(context, runId))
            assertTrue(RecoveryCheckpointAndroidTestFixture.checkpoints(context, runId).isEmpty())
        } finally {
            RecoveryCheckpointAndroidTestFixture.cleanupBestEffort(context, runId)
        }
    }

    @Test
    fun productionBootstrapParentAllowsCheckpointInsert() {
        val runId = RunId.fromBytes(ByteArray(16) { (it + 73).toByte() })
        RecoveryCheckpointAndroidTestFixture.cleanupBestEffort(context, runId)
        try {
            val bootstrap = RecoveryCheckpointAndroidTestFixture.bootstrap(context, runId)
            assertTrue(bootstrap.committed.evidenceEmitted)
            assertTrue(RecoveryCheckpointAndroidTestFixture.bootstrapParentExists(context, runId))
            val checkpoint = RecoveryCheckpointAndroidTestFixture.checkpoint(runId)
            val result = journal.insertCheckpoint(checkpoint)
            assertTrue(result is RecoveryStreamingJournalResult.CheckpointReceipt)
            result as RecoveryStreamingJournalResult.CheckpointReceipt
            assertEquals(checkpoint.checkpointIdentity, result.checkpointIdentity)
            assertFalse(result.replayed)
            assertEquals(
                listOf(checkpoint),
                RecoveryCheckpointAndroidTestFixture.checkpoints(context, runId),
            )
        } finally {
            RecoveryCheckpointAndroidTestFixture.cleanupBestEffort(context, runId)
        }
    }
}
