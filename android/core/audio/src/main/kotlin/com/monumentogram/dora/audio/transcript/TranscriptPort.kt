package com.monumentogram.dora.audio.transcript

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioReference

interface TranscriptPort {
    fun load(identity: AudioIdentity): AudioResult<TranscriptDocument?>

    fun begin(
        source: OriginalAudioReference,
        jobId: String,
        language: String,
        createdAt: Long,
    ): AudioResult<TranscriptDocument>

    fun running(identity: AudioIdentity, jobId: String): AudioResult<TranscriptDocument>

    fun finish(
        source: OriginalAudioReference,
        jobId: String,
        text: String,
        model: String,
        createdAt: Long,
    ): AudioResult<TranscriptDocument>

    fun stop(
        identity: AudioIdentity,
        jobId: String,
        status: String,
    ): AudioResult<TranscriptDocument>

    fun edit(
        identity: AudioIdentity,
        versionId: String,
        expectedRevision: Long,
        text: String,
        createdAt: Long,
    ): AudioResult<TranscriptDocument>

    fun select(
        identity: AudioIdentity,
        versionId: String,
        expectedRevision: Long,
        resolvedEditIds: Set<String>,
        createdAt: Long,
    ): AudioResult<TranscriptDocument>

    fun recover(identity: AudioIdentity): AudioResult<TranscriptDocument?>

    fun history(): AudioResult<List<AudioIdentity>>
}

object UnavailableTranscriptPort : TranscriptPort {
    private fun unavailable() = AudioResult.Failed(AudioFailure.UNAVAILABLE)

    override fun load(identity: AudioIdentity) = unavailable()

    override fun begin(
        source: OriginalAudioReference,
        jobId: String,
        language: String,
        createdAt: Long,
    ) = unavailable()

    override fun running(identity: AudioIdentity, jobId: String) = unavailable()

    override fun finish(
        source: OriginalAudioReference,
        jobId: String,
        text: String,
        model: String,
        createdAt: Long,
    ) = unavailable()

    override fun stop(identity: AudioIdentity, jobId: String, status: String) = unavailable()

    override fun edit(
        identity: AudioIdentity,
        versionId: String,
        expectedRevision: Long,
        text: String,
        createdAt: Long,
    ) = unavailable()

    override fun select(
        identity: AudioIdentity,
        versionId: String,
        expectedRevision: Long,
        resolvedEditIds: Set<String>,
        createdAt: Long,
    ) = unavailable()

    override fun recover(identity: AudioIdentity) = unavailable()

    override fun history() = unavailable()
}

data class MachineTranscript(
    val id: String,
    val text: String,
    val createdAt: Long,
    val model: String,
) {
    val engine: String
        get() = "LOCAL"

    val timestampAvailability: String
        get() = "UNAVAILABLE"

    val timestampQuality: String
        get() = "NOT_EVALUABLE"

    override fun toString() = "MachineTranscript(redacted)"
}

/** Exact previous document rendering is the source/version-bound START/END context anchor. */
data class TranscriptEdit(
    val id: String,
    val versionId: String,
    val before: String,
    val text: String,
    val createdAt: Long,
) {
    override fun toString() = "TranscriptEdit(redacted)"
}

data class TranscriptJob(
    val id: String,
    val status: String,
    val language: String,
    val generation: Long,
    val createdAt: Long,
) {
    override fun toString() = "TranscriptJob(redacted)"
}

data class TranscriptSelection(
    val versionId: String,
    val revision: Long,
    val resolvedEditIds: Set<String>,
    val createdAt: Long,
) {
    override fun toString() = "TranscriptSelection(redacted)"
}

data class TranscriptDocument(
    val source: OriginalAudioReference,
    val revision: Long = 0,
    val activeId: String? = null,
    val versions: List<MachineTranscript> = emptyList(),
    val edits: List<TranscriptEdit> = emptyList(),
    val jobs: List<TranscriptJob> = emptyList(),
    val selections: List<TranscriptSelection> = emptyList(),
) {
    // Independent logical counters are derived from immutable ordered histories, not the CAS
    // revision.
    val editRevision: Long
        get() = edits.size.toLong()

    val selectionRevision: Long
        get() = selections.size.toLong()

    fun rendered(versionId: String): String =
        edits.lastOrNull { it.versionId == versionId }?.text
            ?: versions.single { it.id == versionId }.text

    fun conflicts(versionId: String): List<TranscriptEdit> = edits.filter {
        it.versionId != versionId
    }

    override fun toString() = "TranscriptDocument(redacted)"
}
