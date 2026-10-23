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
internal object TranscriptCodec {
    const val MAX_TEXT_BYTES = 262144
    private const val MAX_BYTES = 8388608
    private const val MAX_ENTRIES = 512
    private const val METADATA_RESERVE_BYTES = 1048576
    private const val MAGIC = 0x44545231

    fun encode(doc: TranscriptDocument): ByteArray {
        validate(doc)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(doc.source.version)
            out.text(doc.source.identity.recordingId.value)
            out.text(doc.source.identity.assetId.value)
            out.text(doc.source.identity.sessionId)
            out.text(doc.source.digest)
            out.writeLong(doc.source.frames)
            out.writeLong(doc.revision)
            out.text(doc.activeId ?: "")
            out.writeInt(doc.versions.size)
            doc.versions.forEach {
                out.text(it.id)
                out.text(it.text)
                out.writeLong(it.createdAt)
                out.text(it.model)
                out.text(it.engine)
                out.text(it.timestampAvailability)
                out.text(it.timestampQuality)
            }
            out.writeInt(doc.edits.size)
            doc.edits.forEach {
                out.text(it.id)
                out.text(it.versionId)
                out.text(it.before)
                out.text(it.text)
                out.writeLong(it.createdAt)
            }
            out.writeInt(doc.jobs.size)
            doc.jobs.forEach {
                out.text(it.id)
                out.text(it.status)
                out.text(it.language)
                out.writeLong(it.generation)
                out.writeLong(it.createdAt)
            }
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
            require(input.readInt() == MAGIC)
            val source = input.source()
            val revision = input.readLong()
            val active = input.text().ifEmpty { null }
            val versions =
                List(input.count()) {
                    MachineTranscript(input.text(), input.text(), input.readLong(), input.text())
                        .also {
                            require(
                                input.text() == it.engine &&
                                    input.text() == it.timestampAvailability &&
                                    input.text() == it.timestampQuality
                            )
                        }
                }
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
            val jobs =
                List(input.count()) {
                    TranscriptJob(
                        input.text(),
                        input.text(),
                        input.text(),
                        input.readLong(),
                        input.readLong(),
                    )
                }
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
        doc.jobs.forEachIndexed { index, job ->
            TranscriptReducer.validId(job.id)
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
            require(job.status !in TranscriptReducer.activeStatuses || index == doc.jobs.lastIndex)
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
