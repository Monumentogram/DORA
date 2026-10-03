package com.monumentogram.dora.audio.persistence

import com.monumentogram.dora.audio.AudioCompletion
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioSourceState
import com.monumentogram.dora.audio.RecoveryAudioBridge
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.audio.StoredAudioAsset
import com.monumentogram.dora.audio.persistence.journal.RoomAudioJournal
import com.monumentogram.dora.audio.recording.RecordingCompletionState
import com.monumentogram.dora.audio.recording.RecordingRecovery
import com.monumentogram.dora.audio.recording.RecoveryMetadata
import com.monumentogram.dora.audio.recording.RecoveryMetadataState

/** Composition of the existing Recovery path under ONE existing source lease. */
internal class RecordingRecoveryReader(
    private val journal: RoomAudioJournal,
    private val bridge: RecoveryAudioBridge,
) {
    fun read(identity: AudioIdentity): AudioResult<RecordingRecovery> {
        val lease =
            journal.catalog.tryAcquire(identity) ?: return AudioResult.Failed(AudioFailure.BUSY)
        return lease.use {
            val state = journal.sourceState(identity)
            if (state != null) return@use AudioResult.Value(unavailable(identity, state))
            val reconciliation = bridge.reconcileHeld(identity)
            val read = bridge.inspectHeld(identity, false)
            val summary = (read as? AudioResult.Value)?.value
            val continuation = bridge.inspectHeld(identity, true)
            val asset =
                journal.catalog.load(identity)
                    ?: return@use AudioResult.Failed(AudioFailure.INCOMPLETE)
            val canResume =
                reconciliation is AudioResult.Value && cleanPrefix(asset, continuation, summary)
            val metadata =
                metadata(
                    identity,
                    asset,
                    canResume || (asset.segments.isEmpty() && asset.pending == null),
                    summary?.completion == AudioCompletion.FINALIZED,
                )
            AudioResult.Value(
                RecordingRecovery(
                    identity,
                    summary,
                    (reconciliation as? AudioResult.Failed)?.reason
                        ?: (read as? AudioResult.Failed)?.reason
                        ?: if (asset.finalization == null)
                            (continuation as? AudioResult.Failed)?.reason
                        else null,
                    canResume,
                    asset.segments.size,
                    technicalMetadataState = metadata.first,
                    semanticMetadataState = metadata.second,
                )
            )
        }
    }

    private fun unavailable(identity: AudioIdentity, state: AudioSourceState) =
        RecordingRecovery(
            identity,
            null,
            AudioFailure.UNAVAILABLE,
            false,
            0,
            completionState =
                when (state) {
                    AudioSourceState.UserDeleted -> RecordingCompletionState.DELETED
                    is AudioSourceState.Deleting -> RecordingCompletionState.DELETION_PENDING
                    else -> RecordingCompletionState.SOURCE_UNAVAILABLE
                },
        )

    private fun metadata(
        identity: AudioIdentity,
        asset: StoredAudioAsset,
        repair: Boolean,
        finalized: Boolean,
    ): Pair<RecoveryMetadataState, RecoveryMetadataState> =
        try {
            val rows = mutableListOf<SegmentationMetadata>()
            var cursor = ""
            while (true) {
                val page = journal.segmentationPage(identity, cursor)
                if (page.isEmpty()) break
                require(rows.size + page.size <= MAX_METADATA_ROWS)
                require(page.first().key > cursor)
                rows += page
                cursor = page.last().key
            }
            if (rows.isEmpty())
                RecoveryMetadataState.NOT_EVALUATED to RecoveryMetadataState.NOT_EVALUATED
            else if (finalized) finalizedMetadata(rows, asset)
            else if (!repair) RecoveryMetadataState.INCOMPLETE to RecoveryMetadataState.INCOMPLETE
            else {
                val plan =
                    RecoveryMetadata.plan(asset.segments, rows, asset.segments.sumOf { it.frames })
                plan.additions.forEach { journal.retainSegmentation(identity, it) }
                plan.technical to plan.semantic
            }
        } catch (_: IllegalArgumentException) {
            RecoveryMetadataState.MALFORMED to RecoveryMetadataState.MALFORMED
        }

    private fun finalizedMetadata(
        rows: List<SegmentationMetadata>,
        asset: StoredAudioAsset,
    ): Pair<RecoveryMetadataState, RecoveryMetadataState> {
        com.monumentogram.dora.audio.logical.LogicalRecordingProjection.technicalPairs(
            asset.segments.sumOf { it.frames },
            rows,
        )
        val technical = RecoveryMetadataState.COMPLETE
        val semantic =
            if (
                rows.any {
                    it.kind in
                        setOf(SegmentationKind.RECOVERY_INTERRUPTED, SegmentationKind.DEGRADED)
                }
            )
                RecoveryMetadataState.INTERRUPTED
            else if (rows.any { it.kind == SegmentationKind.SEMANTIC_CLOSE })
                RecoveryMetadataState.INCOMPLETE
            else RecoveryMetadataState.NOT_EVALUATED
        return technical to semantic
    }

    internal companion object {
        internal fun cleanPrefix(
            asset: StoredAudioAsset,
            continuation: AudioResult<com.monumentogram.dora.audio.AudioReadSummary>,
            summary: com.monumentogram.dora.audio.AudioReadSummary?,
        ): Boolean =
            continuation is AudioResult.Value &&
                summary != null &&
                summary.tailFailure == null &&
                continuation.value == summary &&
                asset.segments.sumOf { it.frames } == continuation.value.frames &&
                asset.pending == null &&
                asset.finalization == null

        const val MAX_METADATA_ROWS = 4096
    }
}
