package com.monumentogram.dora.poc.recovery.candidate

import android.content.Context
import com.monumentogram.dora.poc.recovery.bootstrap.AndroidRecoveryKeyBootstrap
import com.monumentogram.dora.poc.recovery.bootstrap.BootstrapEvidenceSink
import com.monumentogram.dora.poc.recovery.bootstrap.BootstrapResult
import com.monumentogram.dora.poc.recovery.bootstrap.BootstrapStep
import com.monumentogram.dora.poc.recovery.bootstrap.RecoveryBootstrapRunRow
import com.monumentogram.dora.poc.recovery.contract.CanonicalRecoveryAlias
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointIdentityInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalReadResult
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryJournalDatabase
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryStreamingJournal
import java.io.File
import java.security.KeyStore

/** Shared real-adapter setup and child-before-parent teardown for checkpoint instrumentation. */
internal object RecoveryCheckpointAndroidTestFixture {
    data class BootstrapObservation(
        val committed: BootstrapResult.Committed,
        val evidence: RecoveryBootstrapRunRow,
    )

    data class CleanupObservation(
        val rangeDeletes: Int,
        val outcomeDeletes: Int,
        val checkpointDeletes: Int,
        val bootstrapDeletes: Int,
        val aliasExistedBefore: Boolean,
        val aliasAbsentAfter: Boolean,
        val runDirectoryAbsentAfter: Boolean,
    )

    fun bootstrap(context: Context, runId: RunId): BootstrapObservation {
        val evidence = mutableListOf<RecoveryBootstrapRunRow>()
        val result =
            AndroidRecoveryKeyBootstrap.controller(
                    context,
                    BootstrapEvidenceSink { evidence += it },
                )
                .bootstrap(KeyConfirmationValue(RecoveryCandidate.STREAM, runId))
        check(result is BootstrapResult.Committed) {
            "bootstrap-result:${result.javaClass.simpleName}"
        }
        check(result.completedSteps == BootstrapStep.entries)
        check(result.evidenceEmitted && result.evidenceFailure == null)
        check(evidence.size == 1)
        check(evidence.single().runId == runId.toCanonicalString())
        check(evidence.single().candidateId == RecoveryCandidate.STREAM.contractId)
        check(evidence.single().keyConfirmationRelativeName == "key-confirmation/run.kc")
        return BootstrapObservation(result, evidence.single())
    }

    fun checkpoint(
        runId: RunId,
        source: ByteArray = ByteArray(8_192) { ((it * 31 + 9) and 0xff).toByte() },
    ): RecoveryStreamingCheckpointRow {
        val checkpointBytes = ByteArray(128) { (it * 3 + 1).toByte() }
        val checkpointEnvelope = ByteArray(96) { (it * 5 + 2).toByte() }
        val streamEnvelope = ByteArray(96) { (it * 11 + 4).toByte() }
        val input =
            RecoveryStreamingCheckpointIdentityInput(
                runId,
                1UL,
                2UL,
                8_192UL,
                Sha256Value.calculate(source),
                4_056UL,
                "checkpoints/g-00000000000000000001.ct",
                128UL,
                Sha256Value.calculate(checkpointBytes),
                "key-envelopes/checkpoint-g-00000000000000000001.ks",
                96UL,
                Sha256Value.calculate(checkpointEnvelope),
                "stream/stream.ct",
                "key-envelopes/stream.ks",
                96UL,
                Sha256Value.calculate(streamEnvelope),
                Sha256Value.ZERO,
            )
        return RecoveryStreamingCheckpointRow(
            runId,
            1UL,
            2UL,
            8_192UL,
            input.streamCiphertextPrefixSha256,
            4_056UL,
            input.checkpointRelativeName,
            input.checkpointBytes,
            input.checkpointSha256,
            input.checkpointEnvelopeRelativeName,
            input.checkpointEnvelopeBytes,
            input.checkpointEnvelopeSha256,
            input.streamRelativeName,
            input.streamEnvelopeRelativeName,
            input.streamEnvelopeBytes,
            input.streamEnvelopeSha256,
            input.previousCheckpointSha256,
            RecoveryStreamingIdentity.checkpoint(input),
        )
    }

    fun checkpoints(context: Context, runId: RunId): List<RecoveryStreamingCheckpointRow> =
        when (val result = AndroidRecoveryStreamingJournal(context).checkpointChain(runId)) {
            is RecoveryStreamingJournalReadResult.Value -> result.value
            is RecoveryStreamingJournalReadResult.Retry ->
                error("journal-retry:${result.classification.name}")
            is RecoveryStreamingJournalReadResult.Fatal ->
                error("journal-fatal:${result.classification.name}")
        }

    fun bootstrapParentExists(context: Context, runId: RunId): Boolean =
        AndroidRecoveryJournalDatabase.writable(context)
            .rawQuery(
                "SELECT COUNT(*) FROM recovery_run_bootstrap_v1 WHERE run_id=? AND candidate_id=?",
                arrayOf(runId.toCanonicalString(), RecoveryCandidate.STREAM.contractId),
            )
            .use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0) == 1
            }

    fun cleanup(context: Context, runId: RunId): CleanupObservation {
        val database = AndroidRecoveryJournalDatabase.writable(context)
        val run = arrayOf(runId.toCanonicalString())
        val rangeDeletes = database.delete("recovery_stream_range_quarantine_v4", "run_id=?", run)
        val outcomeDeletes = database.delete("recovery_stream_outcome_v4", "run_id=?", run)
        val checkpointDeletes = database.delete("recovery_stream_checkpoint_v4", "run_id=?", run)
        val bootstrapDeletes = database.delete("recovery_run_bootstrap_v1", "run_id=?", run)
        val keyStore = androidKeyStore()
        val alias = androidAlias(runId)
        val aliasExistedBefore = keyStore.containsAlias(alias)
        if (aliasExistedBefore) keyStore.deleteEntry(alias)
        val runDirectory = runDirectory(context, runId)
        if (runDirectory.exists()) check(runDirectory.deleteRecursively())
        return CleanupObservation(
            rangeDeletes,
            outcomeDeletes,
            checkpointDeletes,
            bootstrapDeletes,
            aliasExistedBefore,
            !keyStore.containsAlias(alias),
            !runDirectory.exists(),
        )
    }

    @Suppress("TooGenericExceptionCaught")
    fun cleanupBestEffort(context: Context, runId: RunId) {
        val run = arrayOf(runId.toCanonicalString())
        val database =
            try {
                AndroidRecoveryJournalDatabase.writable(context)
            } catch (_: Throwable) {
                null
            }
        if (database != null) {
            attemptCleanup {
                database.delete("recovery_stream_range_quarantine_v4", "run_id=?", run)
            }
            attemptCleanup { database.delete("recovery_stream_outcome_v4", "run_id=?", run) }
            attemptCleanup { database.delete("recovery_stream_checkpoint_v4", "run_id=?", run) }
            attemptCleanup { database.delete("recovery_run_bootstrap_v1", "run_id=?", run) }
        }
        attemptCleanup {
            val keyStore = androidKeyStore()
            val alias = androidAlias(runId)
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        }
        attemptCleanup { runDirectory(context, runId).deleteRecursively() }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun attemptCleanup(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // Continue so every later child, parent, alias, and file cleanup is still attempted.
        }
    }

    fun runDirectory(context: Context, runId: RunId) =
        File(context.noBackupFilesDir, "poc-recovery/v1/runs/${runId.toCanonicalString()}")

    private fun androidKeyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun androidAlias(runId: RunId) =
        CanonicalRecoveryAlias.forRun(runId).removePrefix("android-keystore://")
}
