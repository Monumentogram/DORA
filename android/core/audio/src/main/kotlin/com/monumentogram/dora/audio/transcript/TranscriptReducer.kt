package com.monumentogram.dora.audio.transcript

import com.monumentogram.dora.audio.OriginalAudioReference
import java.util.UUID

/** Pure append-only transition rules. Persistence serializes these under an exact-source lease. */
@Suppress("TooManyFunctions") // Related transitions share one source/history invariant checker.
internal object TranscriptReducer {
    const val MAX_MODEL_NAME_LENGTH = 256
    val activeStatuses = setOf("QUEUED", "RUNNING")
    val terminalStatuses = setOf("CANCELLED", "FAILED", "INTERRUPTED")

    @Suppress(
        "LongParameterList"
    ) // Original source, action, language, consent and time are independent bindings.
    fun beginCloud(
        old: TranscriptDocument?,
        source: OriginalAudioReference,
        id: String,
        language: String,
        consent: CloudConsent,
        time: Long,
    ): TranscriptDocument {
        consent.validate()
        require(!consent.revoked && consent.grantedAt == time)
        old?.jobs
            ?.find { it.id == id }
            ?.let {
                require(
                    old.source == source &&
                        it.consent == consent &&
                        it.language == language &&
                        it.createdAt == time
                )
                return old
            }
        val begun = begin(old, source, id, language, time)
        return begun
            .copy(jobs = begun.jobs.map { if (it.id == id) it.copy(consent = consent) else it })
            .also(TranscriptCodec::validate)
    }

    fun resumeCloud(doc: TranscriptDocument, id: String): TranscriptDocument {
        val job = doc.jobs.single { it.id == id }
        require(job.consent != null && !job.consent.revoked)
        require(doc.jobs.none { it.id != id && it.status in activeStatuses })
        require(job.status in activeStatuses + setOf("FAILED", "INTERRUPTED"))
        return if (job.status in activeStatuses) doc
        else
            advance(
                doc.copy(
                    jobs = doc.jobs.map { if (it.id == id) it.copy(status = "QUEUED") else it }
                )
            )
    }

    fun revokeCloud(doc: TranscriptDocument, id: String): TranscriptDocument {
        val job = doc.jobs.single { it.id == id }
        require(job.consent != null)
        if (job.consent.revoked) return doc
        return advance(
            doc.copy(
                jobs =
                    doc.jobs.map {
                        if (it.id == id)
                            it.copy(
                                status = if (it.status == "SUCCEEDED") it.status else "CANCELLED",
                                consent = job.consent.copy(revoked = true),
                            )
                        else it
                    }
            )
        )
    }

    fun finishCloud(
        doc: TranscriptDocument,
        id: String,
        result: CloudTranscriptResult,
    ): TranscriptDocument {
        val job = doc.jobs.single { it.id == id }
        require(job.consent != null && !job.consent.revoked)
        result.validate(doc, job.language)
        val output = MachineTranscript(id, result.text, result.completedAt, result.model, result)
        doc.versions
            .find { it.id == id }
            ?.let {
                require(it == output)
                return doc
            }
        require(job.status in activeStatuses)
        require(doc.jobs.none { it.id != id && it.status in activeStatuses })
        return advance(
            doc.copy(
                versions = doc.versions + output,
                jobs = doc.jobs.map { if (it.id == id) it.copy(status = "SUCCEEDED") else it },
                activeId =
                    if (doc.activeId == null && doc.edits.isEmpty() && doc.selections.isEmpty()) id
                    else doc.activeId,
            )
        )
    }

    fun begin(
        old: TranscriptDocument?,
        source: OriginalAudioReference,
        id: String,
        language: String,
        time: Long,
    ): TranscriptDocument {
        validId(id)
        require(language in setOf("ru", "en") && time >= 0)
        val doc = old ?: TranscriptDocument(source)
        require(doc.source == source)
        doc.jobs
            .find { it.id == id }
            ?.let {
                require(it.language == language && it.createdAt == time)
                return doc
            }
        require(doc.jobs.none { it.status in activeStatuses })
        return advance(
            doc.copy(
                jobs =
                    doc.jobs +
                        TranscriptJob(
                            id,
                            "QUEUED",
                            language,
                            Math.addExact(doc.jobs.lastOrNull()?.generation ?: 0, 1),
                            time,
                        )
            )
        )
    }

    fun running(doc: TranscriptDocument, id: String): TranscriptDocument {
        val job = doc.jobs.single { it.id == id }
        require(job.status in activeStatuses)
        return if (job.status == "RUNNING") doc
        else
            advance(
                doc.copy(
                    jobs = doc.jobs.map { if (it.id == id) it.copy(status = "RUNNING") else it }
                )
            )
    }

    fun finish(
        doc: TranscriptDocument,
        id: String,
        text: String,
        model: String,
        time: Long,
    ): TranscriptDocument {
        validText(text)
        require(model.isNotBlank() && model.length <= MAX_MODEL_NAME_LENGTH && time >= 0)
        val output = MachineTranscript(id, text, time, model)
        doc.versions
            .find { it.id == id }
            ?.let {
                require(it == output)
                return doc
            }
        val job = doc.jobs.single { it.id == id }
        require(job.consent == null)
        require(job.status in activeStatuses && job.generation == doc.jobs.last().generation)
        return advance(
            doc.copy(
                versions = doc.versions + output,
                jobs = doc.jobs.map { if (it.id == id) it.copy(status = "SUCCEEDED") else it },
                activeId =
                    if (doc.activeId == null && doc.edits.isEmpty() && doc.selections.isEmpty()) id
                    else doc.activeId,
            )
        )
    }

    fun stop(doc: TranscriptDocument, id: String, status: String): TranscriptDocument {
        require(status in terminalStatuses)
        val job = doc.jobs.single { it.id == id }
        if (job.status == status) return doc
        require(job.status in activeStatuses)
        return advance(
            doc.copy(jobs = doc.jobs.map { if (it.id == id) it.copy(status = status) else it })
        )
    }

    fun resumePaged(doc: TranscriptDocument, id: String): TranscriptDocument {
        val job = doc.jobs.single { it.id == id }
        require(job.consent?.revoked != true && job.status != "SUCCEEDED")
        require(doc.jobs.none { it.id != id && it.status in activeStatuses })
        require(job.consent != null || job == doc.jobs.last())
        return advance(
            doc.copy(jobs = doc.jobs.map { if (it.id == id) it.copy(status = "RUNNING") else it })
        )
    }

    fun finishPaged(
        doc: TranscriptDocument,
        id: String,
        reference: PagedTranscriptReference,
        model: String,
        time: Long,
    ): TranscriptDocument {
        reference.validate()
        require(reference.publicationRevision == doc.revision + 1 && time >= 0)
        val job = doc.jobs.single { it.id == id }
        require(job.status in activeStatuses && job.consent?.revoked != true)
        require((job.consent != null) == (reference.engine == "CLOUD"))
        require(doc.versions.none { it.id == id })
        return advance(
            doc.copy(
                versions = doc.versions + MachineTranscript(id, "", time, model, paged = reference),
                jobs = doc.jobs.map { if (it.id == id) it.copy(status = "SUCCEEDED") else it },
                activeId =
                    if (doc.activeId == null && doc.edits.isEmpty() && doc.selections.isEmpty()) id
                    else doc.activeId,
            )
        )
    }

    fun edit(
        doc: TranscriptDocument,
        id: String,
        expected: Long,
        text: String,
        time: Long,
    ): TranscriptDocument {
        require(doc.revision == expected && time >= 0)
        validText(text)
        val before = doc.rendered(id)
        require(before != text)
        return advance(
            doc.copy(
                edits =
                    doc.edits + TranscriptEdit(UUID.randomUUID().toString(), id, before, text, time)
            )
        )
    }

    fun select(
        doc: TranscriptDocument,
        id: String,
        expected: Long,
        resolutions: Set<String>,
        time: Long,
    ): TranscriptDocument {
        require(doc.revision == expected && time >= 0 && doc.versions.any { it.id == id })
        require(resolutions == doc.conflicts(id).map { it.id }.toSet())
        return advance(
            doc.copy(
                activeId = id,
                selections =
                    doc.selections + TranscriptSelection(id, expected, resolutions.toSet(), time),
            )
        )
    }

    fun recover(doc: TranscriptDocument): TranscriptDocument =
        if (doc.jobs.none { it.status in activeStatuses && it.consent == null }) doc
        else
            advance(
                doc.copy(
                    jobs =
                        doc.jobs.map {
                            if (it.status in activeStatuses && it.consent == null)
                                it.copy(status = "INTERRUPTED")
                            else it
                        }
                )
            )

    private fun advance(doc: TranscriptDocument): TranscriptDocument =
        doc.copy(revision = Math.addExact(doc.revision, 1)).also { TranscriptCodec.validate(it) }

    fun validText(text: String) {
        val encoded =
            Charsets.UTF_8.newEncoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .encode(java.nio.CharBuffer.wrap(text))
        require(encoded.remaining() <= TranscriptCodec.MAX_TEXT_BYTES)
    }

    fun validId(id: String) {
        require(UUID.fromString(id).toString() == id)
    }
}
