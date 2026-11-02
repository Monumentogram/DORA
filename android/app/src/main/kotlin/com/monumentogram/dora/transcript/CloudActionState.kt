package com.monumentogram.dora.transcript

import com.monumentogram.dora.audio.transcript.TranscriptJob

internal data class CloudActionState(val pending: TranscriptJob?, val blocksNew: Boolean)

/** Keep the existing action reachable until the user explicitly resumes or revokes it. */
internal fun cloudActions(jobs: List<TranscriptJob>): CloudActionState {
    val resumable = jobs.lastOrNull {
        it.consent?.revoked == false && it.status !in setOf("SUCCEEDED", "CANCELLED")
    }
    val pending = resumable ?: jobs.lastOrNull { it.consent != null && it.status != "SUCCEEDED" }
    return CloudActionState(pending, resumable != null)
}
