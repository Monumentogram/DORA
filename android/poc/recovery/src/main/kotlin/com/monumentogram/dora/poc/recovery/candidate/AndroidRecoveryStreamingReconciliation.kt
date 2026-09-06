package com.monumentogram.dora.poc.recovery.candidate

import android.content.Context
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.coordination.ProcessRecoveryRunSingleWriterGuard
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryStreamingJournal
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryReconciliationStorage
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryStreamingSource
import com.monumentogram.dora.poc.recovery.storage.RecoveryArtifactAccessException
import com.monumentogram.dora.poc.recovery.storage.RecoveryUnsafePathException

/** Android composition for the admitted streaming recovery controller. */
internal object AndroidRecoveryStreamingReconciliation {
    fun create(
        context: Context,
        evidence: RecoveryStreamingEvidenceSink,
    ): RecoveryStreamingReconciliationController {
        val applicationContext = context.applicationContext
        val journal = AndroidRecoveryStreamingJournal(applicationContext)
        val storage = AndroidOsRecoveryReconciliationStorage(applicationContext)
        return RecoveryStreamingReconciliationController(
            journal,
            AndroidOsRecoveryStreamingSource(applicationContext.noBackupFilesDir, journal),
            ProcessRecoveryRunSingleWriterGuard,
            RecoveryStreamingCheckpointAuthenticatorAdapter(
                AndroidRecoveryStreamingPrerequisiteSource(storage::loadActiveArtifact),
                RecoveryStreamingTinkPrerequisiteCrypto(),
            ),
            evidence,
        )
    }
}

@Suppress("SwallowedException")
internal class AndroidRecoveryStreamingPrerequisiteSource(
    private val loadActiveArtifact: (RunId, String, Long) -> RecoveryArtifactBytes?
) : RecoveryStreamingPrerequisiteSource {
    override fun load(
        runId: RunId,
        relativeName: String,
        kind: RecoveryStreamingPrerequisiteArtifactKind,
    ): RecoveryArtifactBytes? =
        try {
            loadActiveArtifact(runId, relativeName, MAX_PREREQUISITE_ARTIFACT_BYTES)
        } catch (_: RecoveryUnsafePathException) {
            throw RecoveryStreamingPrerequisiteSourceException(
                RecoveryStreamingPrerequisiteSourceFailure.UNSAFE_PATH
            )
        } catch (failure: RecoveryArtifactAccessException) {
            throw RecoveryStreamingPrerequisiteSourceException(
                if (failure.structural) {
                    RecoveryStreamingPrerequisiteSourceFailure.STRUCTURAL
                } else {
                    RecoveryStreamingPrerequisiteSourceFailure.OPERATIONAL
                }
            )
        } catch (_: Throwable) {
            throw RecoveryStreamingPrerequisiteSourceException(
                RecoveryStreamingPrerequisiteSourceFailure.OPERATIONAL
            )
        }

    private companion object {
        const val MAX_PREREQUISITE_ARTIFACT_BYTES = 16L * 1_024L * 1_024L
    }
}
