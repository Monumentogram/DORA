package com.monumentogram.dora.audio.transcript

import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.recording.AlphaRecordingLimit
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Versioned bounded binary payload, stored only inside the SQLCipher journal. */
@Suppress(
    "TooManyFunctions"
) // Bounded format readers/writers share the same versioned wire contract.
internal object TranscriptCodec {
    const val MAX_TEXT_BYTES = TranscriptLimits.MAX_TEXT_BYTES
    private const val MAX_BYTES = 8388608
    private const val MAX_ENTRIES = 512
    private const val METADATA_RESERVE_BYTES = 1048576
    private const val MAGIC = 0x44545231
    private const val CLOUD_MAGIC = 0x44545232
    private const val PAGED_MAGIC = 0x44545233

    fun encode(doc: TranscriptDocument): ByteArray {
        validate(doc)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            val format = if (doc.versions.any { it.paged != null }) PAGED_MAGIC else CLOUD_MAGIC
            out.writeInt(format)
            out.writeInt(doc.source.version)
            out.text(doc.source.identity.recordingId.value)
            out.text(doc.source.identity.assetId.value)
            out.text(doc.source.identity.sessionId)
            out.text(doc.source.digest)
            out.writeLong(doc.source.frames)
            out.writeLong(doc.revision)
            out.text(doc.activeId ?: "")
            out.writeInt(doc.versions.size)
            doc.versions.forEach { out.version(it, format) }
            out.writeInt(doc.edits.size)
            doc.edits.forEach {
                out.text(it.id)
                out.text(it.versionId)
                out.text(it.before)
                out.text(it.text)
                out.writeLong(it.createdAt)
            }
            out.writeInt(doc.jobs.size)
            doc.jobs.forEach { out.job(it) }
            out.writeInt(doc.selections.size)
            doc.selections.forEach {
                out.text(it.versionId)
                out.writeLong(it.revision)
                out.writeLong(it.createdAt)
                out.writeInt(it.resolvedEditIds.size)
                it.resolvedEditIds.sorted().forEach { id -> out.text(id) }
            }
        }
        return bytes.toByteArray().also { require(it.size <= MAX_BYTES) }
    }

    fun decode(bytes: ByteArray): TranscriptDocument {
        require(bytes.size <= MAX_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val format = input.readInt()
            require(format == MAGIC || format == CLOUD_MAGIC || format == PAGED_MAGIC)
            val source = input.source()
            val revision = input.readLong()
            val active = input.text().ifEmpty { null }
            val versions = List(input.count()) { input.version(format) }
            val edits =
                List(input.count()) {
                    TranscriptEdit(
                        input.text(),
                        input.text(),
                        input.text(),
                        input.text(),
                        input.readLong(),
                    )
                }
            val jobs = List(input.count()) { input.job(format) }
            val selections =
                List(input.count()) {
                    val id = input.text()
                    val rev = input.readLong()
                    val time = input.readLong()
                    val resolutions = List(input.count()) { input.text() }
                    require(resolutions.distinct().size == resolutions.size)
                    TranscriptSelection(id, rev, resolutions.toSet(), time)
                }
            require(input.available() == 0)
            TranscriptDocument(source, revision, active, versions, edits, jobs, selections)
                .also(::validate)
        }
    }

    private fun DataInputStream.source() =
        OriginalAudioReference(
            readInt(),
            AudioIdentity(RecordingId(text()), AudioAssetId(text()), text()),
            text(),
            readLong(),
        )

    @Suppress("LongMethod") // Legacy and paged versions share one bounded document invariant pass.
    fun validate(doc: TranscriptDocument) {
        require(
            doc.revision >= 0 &&
                doc.source.version == 1 &&
                doc.source.frames in 1..AlphaRecordingLimit.MAX_ALPHA_FRAMES
        )
        require(doc.source.digest.matches(Regex("[a-f0-9]{64}")))
        TranscriptReducer.validId(doc.source.identity.sessionId)
        require(
            listOf(doc.versions.size, doc.edits.size, doc.jobs.size, doc.selections.size).all {
                it <= MAX_ENTRIES
            }
        )
        require(
            doc.versions.sumOf { it.text.toByteArray(Charsets.UTF_8).size.toLong() } +
                doc.versions.sumOf { version ->
                    version.cloud?.segments?.sumOf { segment ->
                        segment.text.toByteArray(Charsets.UTF_8).size.toLong()
                    } ?: 0L
                } +
                doc.edits.sumOf {
                    it.before.toByteArray(Charsets.UTF_8).size.toLong() +
                        it.text.toByteArray(Charsets.UTF_8).size
                } < MAX_BYTES - METADATA_RESERVE_BYTES
        )
        validateJobs(doc)
        require(doc.versions.map { it.id }.distinct().size == doc.versions.size)
        doc.versions.forEach {
            TranscriptReducer.validText(it.text)
            require(
                it.createdAt >= 0 &&
                    it.model.isNotBlank() &&
                    it.model.length <= TranscriptReducer.MAX_MODEL_NAME_LENGTH
            )
            require(doc.jobs.single { job -> job.id == it.id }.status == "SUCCEEDED")
            val job = doc.jobs.single { job -> job.id == it.id }
            require((it.engine == "CLOUD") == (job.consent != null))
            it.paged?.let { reference ->
                reference.validate()
                require(
                    it.text.isEmpty() &&
                        it.cloud == null &&
                        reference.publicationRevision <= doc.revision
                )
            }
            it.cloud?.let { cloud ->
                require(
                    cloud.text == it.text &&
                        cloud.model == it.model &&
                        cloud.completedAt == it.createdAt
                )
                cloud.validate(doc, job.language)
            }
        }
        require(
            doc.jobs.filter { it.status == "SUCCEEDED" }.map { it.id }.toSet() ==
                doc.versions.map { it.id }.toSet()
        )
        require(doc.activeId == null || doc.versions.any { it.id == doc.activeId })
        validateEditsAndSelections(doc)
    }

    private fun validateJobs(doc: TranscriptDocument) {
        require(doc.jobs.map { it.id }.distinct().size == doc.jobs.size)
        require(doc.jobs.count { it.status in TranscriptReducer.activeStatuses } <= 1)
        doc.jobs.forEachIndexed { index, job ->
            TranscriptReducer.validId(job.id)
            job.consent?.validate()
            require(job.consent?.revoked != true || job.status in setOf("CANCELLED", "SUCCEEDED"))
            require(
                job.generation == index + 1L &&
                    job.createdAt >= 0 &&
                    job.language in setOf("ru", "en")
            )
            require(
                job.status in
                    TranscriptReducer.activeStatuses +
                        TranscriptReducer.terminalStatuses +
                        "SUCCEEDED"
            )
            require(
                job.status !in TranscriptReducer.activeStatuses ||
                    job.consent != null ||
                    index == doc.jobs.lastIndex
            )
        }
    }

    private fun validateEditsAndSelections(doc: TranscriptDocument) {
        require(doc.edits.map { it.id }.distinct().size == doc.edits.size)
        val rendered = doc.versions.associate { it.id to it.text }.toMutableMap()
        doc.edits.forEach {
            TranscriptReducer.validId(it.id)
            TranscriptReducer.validText(it.text)
            require(
                it.createdAt >= 0 && rendered[it.versionId] == it.before && it.text != it.before
            )
            rendered[it.versionId] = it.text
        }
        var selectionRevision = -1L
        doc.selections.forEach {
            require(it.revision in (selectionRevision + 1) until doc.revision && it.createdAt >= 0)
            require(doc.versions.any { version -> version.id == it.versionId })
            require(
                it.resolvedEditIds.all { id ->
                    doc.edits.any { edit -> edit.id == id && edit.versionId != it.versionId }
                }
            )
            selectionRevision = it.revision
        }
    }

    private fun DataOutputStream.text(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.size <= MAX_TEXT_BYTES)
        writeInt(encoded.size)
        write(encoded)
    }

    private fun DataOutputStream.version(value: MachineTranscript, format: Int) {
        text(value.id)
        text(value.text)
        writeLong(value.createdAt)
        text(value.model)
        text(value.engine)
        text(value.timestampAvailability)
        text(value.timestampQuality)
        writeBoolean(value.cloud != null)
        value.cloud?.let { cloud(it) }
        if (format == PAGED_MAGIC) {
            writeBoolean(value.paged != null)
            value.paged?.let {
                text(it.engine)
                text(it.configDigest)
                text(it.windowPolicy)
                writeInt(it.partCount)
                writeLong(it.pageCount)
                writeLong(it.textBytes)
                text(it.rootDigest)
                writeLong(it.publicationRevision)
            }
        }
    }

    private fun DataInputStream.version(format: Int): MachineTranscript {
        val raw = MachineTranscript(text(), text(), readLong(), text())
        val engine = text()
        val availability = text()
        val quality = text()
        val cloud = if (format != MAGIC && readBoolean()) cloud(raw) else null
        val paged =
            if (format == PAGED_MAGIC && readBoolean())
                PagedTranscriptReference(
                    text(),
                    text(),
                    text(),
                    readInt(),
                    readLong(),
                    readLong(),
                    text(),
                    readLong(),
                )
            else null
        return raw.copy(cloud = cloud, paged = paged).also {
            require(
                engine == it.engine &&
                    availability == it.timestampAvailability &&
                    quality == it.timestampQuality
            )
        }
    }

    private fun DataOutputStream.job(value: TranscriptJob) {
        text(value.id)
        text(value.status)
        text(value.language)
        writeLong(value.generation)
        writeLong(value.createdAt)
        writeBoolean(value.consent != null)
        value.consent?.let {
            text(it.version)
            text(it.source)
            writeLong(it.grantedAt)
            writeBoolean(it.revoked)
            text(it.destination)
        }
    }

    private fun DataInputStream.job(format: Int) =
        TranscriptJob(
            text(),
            text(),
            text(),
            readLong(),
            readLong(),
            if (format != MAGIC && readBoolean())
                CloudConsent(text(), text(), readLong(), readBoolean(), text())
            else null,
        )

    private fun DataOutputStream.cloud(value: CloudTranscriptResult) {
        text(value.providerId)
        text(value.providerJobId)
        text(value.sourceDigest)
        writeInt(value.sourceVersion)
        writeInt(value.segments.size)
        value.segments.forEach {
            text(it.text)
            writeLong(it.startMs ?: -1)
            writeLong(it.endMs ?: -1)
            writeBoolean(it.confidence != null)
            it.confidence?.let(::writeDouble)
        }
    }

    private fun DataInputStream.cloud(raw: MachineTranscript): CloudTranscriptResult {
        val provider = text()
        val job = text()
        val digest = text()
        val version = readInt()
        val count = readInt().also { require(it in 0..CloudTranscriptResult.MAX_SEGMENTS) }
        val segments =
            List(count) {
                CloudSegment(
                    text(),
                    readLong().let { if (it == -1L) null else it },
                    readLong().let { if (it == -1L) null else it },
                    if (readBoolean()) readDouble() else null,
                )
            }
        return CloudTranscriptResult(
            raw.text,
            provider,
            raw.model,
            job,
            digest,
            version,
            raw.createdAt,
            segments,
        )
    }

    private fun DataInputStream.count(): Int = readInt().also { require(it in 0..MAX_ENTRIES) }

    private fun DataInputStream.text(): String {
        val size = readInt()
        require(size in 0..MAX_TEXT_BYTES && size <= available())
        val bytes = ByteArray(size)
        readFully(bytes)
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }
}
