package com.monumentogram.dora.audio.persistence.journal

import com.monumentogram.dora.audio.AudioIdentity

internal enum class AudioDeletionTargetKind {
    ARTIFACT,
    QUARANTINE_ARTIFACT,
    RUN_DIRECTORY,
    KEY_REFERENCE,
}

/** Internal deletion inventory only; never returned by the public audio product ports. */
internal data class AudioDeletionTarget(
    val runId: String,
    val kind: AudioDeletionTargetKind,
    val relativeName: String,
    val digest: String? = null,
)

internal data class AudioDeletionStep(val target: AudioDeletionTarget, val completed: Boolean)

internal data class AudioDeletionSnapshot(
    val identity: AudioIdentity,
    val operationId: String,
    val state: String,
    val steps: List<AudioDeletionStep>,
)

internal fun DeletionTargetEntity.target() =
    AudioDeletionTarget(runId, AudioDeletionTargetKind.valueOf(kind), relativeName, digest)
