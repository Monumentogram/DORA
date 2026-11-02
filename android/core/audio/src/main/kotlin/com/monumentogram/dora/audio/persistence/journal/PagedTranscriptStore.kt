package com.monumentogram.dora.audio.persistence.journal

import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.transcript.CloudConsent
import com.monumentogram.dora.audio.transcript.CloudSegment
import com.monumentogram.dora.audio.transcript.PagedTranscriptLimits
import com.monumentogram.dora.audio.transcript.PagedTranscriptPart
import com.monumentogram.dora.audio.transcript.PagedTranscriptProgress
import com.monumentogram.dora.audio.transcript.PagedTranscriptReference
import com.monumentogram.dora.audio.transcript.PagedTranscriptSpec
import com.monumentogram.dora.audio.transcript.PagedTranscriptText
import com.monumentogram.dora.audio.transcript.TranscriptDocument
import com.monumentogram.dora.audio.transcript.TranscriptPageCursor
import com.monumentogram.dora.audio.transcript.TranscriptPartProvenance
import com.monumentogram.dora.audio.transcript.TranscriptReducer
import com.monumentogram.dora.audio.transcript.TranscriptTextPage
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

private const val FRAMES_PER_MILLISECOND = 16

/** Called only under the journal's authenticated source lease, never directly from UI/native. */
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal class PagedTranscriptStore(
    private val dao: PagedTranscriptDao,
    private val document: (AudioIdentity) -> TranscriptDocument?,
    private val mutate:
        (
            AudioIdentity,
            (TranscriptDocument?) -> TranscriptDocument,
            () -> Unit,
            () -> Boolean,
        ) -> TranscriptDocument,
    private val commit: (() -> Unit, () -> Boolean) -> Unit,
) {
    fun begin(spec: PagedTranscriptSpec): PagedTranscriptProgress {
        val identity = spec.source.identity
        dao.job(identity.assetId.value, spec.jobId)?.let {
            require(PagedTranscriptWire.spec(it.payload) == spec)
            return checkNotNull(progress(identity, spec.jobId))
        }
        val row =
            PagedJobEntity(identity.assetId.value, spec.jobId, 1, PagedTranscriptWire.spec(spec))
        mutate(
            identity,
            { old ->
                val queued =
                    if (spec.consent == null)
                        TranscriptReducer.begin(
                            old,
                            spec.source,
                            spec.jobId,
                            spec.language,
                            spec.createdAt,
                        )
                    else
                        TranscriptReducer.beginCloud(
                            old,
                            spec.source,
                            spec.jobId,
                            spec.language,
                            spec.consent,
                            spec.createdAt,
                        )
                // One durable transition publishes the initial RUNNING job and its configuration.
                queued.copy(
                    jobs =
                        queued.jobs.map {
                            if (it.id == spec.jobId) it.copy(status = "RUNNING") else it
                        }
                )
            },
            { dao.insert(row) },
            { dao.job(row.assetId, row.jobId)?.attempt == 1L },
        )
        return checkNotNull(progress(identity, spec.jobId))
    }

    fun resume(identity: AudioIdentity, job: String): PagedTranscriptProgress {
        val old = requireNotNull(dao.job(identity.assetId.value, job))
        val next = old.copy(attempt = Math.addExact(old.attempt, 1))
        mutate(
            identity,
            { TranscriptReducer.resumePaged(requireNotNull(it), job) },
            { check(dao.update(next) == 1) },
            { dao.job(old.assetId, job)?.attempt == next.attempt },
        )
        return checkNotNull(progress(identity, job))
    }

    fun progress(identity: AudioIdentity, job: String): PagedTranscriptProgress? {
        val row = dao.job(identity.assetId.value, job) ?: return null
        val spec = PagedTranscriptWire.spec(row.payload)
        val doc = requireNotNull(document(identity))
        require(doc.source == spec.source && spec.source.identity == identity)
        val state = doc.jobs.single { it.id == job }
        require(state.language == spec.language)
        val indexes = dao.parts(row.assetId, job)
        return PagedTranscriptProgress(
            spec,
            state.status,
            row.attempt,
            indexes.size,
            spec.partCount,
            (0 until spec.partCount).firstOrNull { it !in indexes },
            dao.textBytes(row.assetId, job),
            state.status == "SUCCEEDED",
        )
    }

    fun part(identity: AudioIdentity, job: String, index: Int): PagedTranscriptPart? {
        val spec = requireNotNull(progress(identity, job)).spec
        val range = spec.range(index)
        val row = dao.part(identity.assetId.value, job, index) ?: return null
        return PagedTranscriptPart(
            index,
            range.firstFrame,
            range.frameCount,
            row.pageCount,
            row.textBytes,
            row.digest,
            PagedTranscriptWire.provenance(row.payload),
        )
    }

    private fun running(
        source: OriginalAudioReference,
        job: String,
        attempt: Long,
    ): PagedTranscriptProgress {
        val value = requireNotNull(progress(source.identity, job))
        require(
            value.spec.source == source && value.attempt == attempt && value.status == "RUNNING"
        )
        require(document(source.identity)!!.jobs.single { it.id == job }.consent?.revoked != true)
        return value
    }

    // Keep validation and the exact manifest/page commit in one explicit transaction flow.
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun commitPart(
        source: OriginalAudioReference,
        job: String,
        attempt: Long,
        index: Int,
        text: String,
        provenance: TranscriptPartProvenance?,
    ): PagedTranscriptProgress {
        val progress = running(source, job, attempt)
        val range = progress.spec.range(index)
        require((progress.spec.engine == "CLOUD") == (provenance != null))
        provenance?.let {
            require(
                it.providerId == "amazon-transcribe" &&
                    it.providerJobId.matches(Regex("[A-Za-z0-9._-]{1,200}")) &&
                    it.completedAt >= 0
            )
            require(it.segments.size <= PagedTranscriptLimits.MAX_CLOUD_SEGMENTS)
            require(
                it.segments.sumOf { segment ->
                    segment.text.toByteArray(Charsets.UTF_8).size.toLong()
                } <= PagedTranscriptText.MAX_PART_BYTES
            )
            val first = range.firstFrame / FRAMES_PER_MILLISECOND
            val last = (range.firstFrame + range.frameCount) / FRAMES_PER_MILLISECOND
            it.segments.forEach { segment ->
                require(segment.startMs == null || segment.startMs in first..last)
                require(segment.endMs == null || segment.endMs in first..last)
                require(
                    segment.startMs == null ||
                        segment.endMs == null ||
                        segment.startMs <= segment.endMs
                )
                require(
                    segment.confidence == null ||
                        segment.confidence.isFinite() && segment.confidence in 0.0..1.0
                )
            }
        }
        val pages = PagedTranscriptText.pages(text)
        val bytes = text.toByteArray(Charsets.UTF_8)
        val digest =
            try {
                PagedTranscriptText.digest(bytes)
            } finally {
                bytes.fill(0)
            }
        val payload = PagedTranscriptWire.provenance(provenance)
        val row =
            PagedPartEntity(
                source.identity.assetId.value,
                job,
                index,
                pages.size,
                pages.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() },
                digest,
                payload,
            )
        dao.part(row.assetId, job, index)?.let { old ->
            require(
                old.digest == row.digest &&
                    old.pageCount == row.pageCount &&
                    old.textBytes == row.textBytes &&
                    old.payload.contentEquals(payload)
            )
            return checkNotNull(progress(source.identity, job))
        }
        val rows = pages.mapIndexed { number, page ->
            PagedPageEntity(row.assetId, job, index, number, page.toByteArray(Charsets.UTF_8))
        }
        try {
            commit(
                {
                    dao.insert(row)
                    rows.forEach { dao.insert(it) }
                },
                {
                    dao.part(row.assetId, job, index)?.let {
                        it.digest == digest && it.payload.contentEquals(payload)
                    } == true &&
                        rows.all { expected ->
                            dao.page(row.assetId, job, index, expected.pageIndex)
                                ?.payload
                                ?.contentEquals(expected.payload) == true
                        }
                },
            )
        } finally {
            payload.fill(0)
            rows.forEach { it.payload.fill(0) }
        }
        return checkNotNull(progress(source.identity, job))
    }

    fun reusePart(
        source: OriginalAudioReference,
        job: String,
        attempt: Long,
        fromJob: String,
        index: Int,
    ): PagedTranscriptProgress {
        val target = running(source, job, attempt)
        val previous = requireNotNull(progress(source.identity, fromJob))
        require(
            target.spec.source == previous.spec.source &&
                target.spec.configDigest == previous.spec.configDigest
        )
        val old = requireNotNull(dao.part(source.identity.assetId.value, fromJob, index))
        dao.part(old.assetId, job, index)?.let { retained ->
            require(retained.digest == old.digest && retained.payload.contentEquals(old.payload))
            return checkNotNull(progress(source.identity, job))
        }
        val replacement = old.copy(jobId = job)
        commit(
            {
                dao.insert(replacement)
                for (number in 0 until old.pageCount) {
                    val page = requireNotNull(dao.page(old.assetId, fromJob, index, number))
                    try {
                        dao.insert(page.copy(jobId = job))
                    } finally {
                        page.payload.fill(0)
                    }
                }
            },
            { dao.part(old.assetId, job, index)?.digest == old.digest },
        )
        return checkNotNull(progress(source.identity, job))
    }

    fun stop(
        identity: AudioIdentity,
        job: String,
        attempt: Long,
        status: String,
    ): PagedTranscriptProgress {
        val current = requireNotNull(progress(identity, job))
        require(current.attempt == attempt)
        mutate(identity, { TranscriptReducer.stop(requireNotNull(it), job, status) }, {}, { true })
        return checkNotNull(progress(identity, job))
    }

    fun finish(
        source: OriginalAudioReference,
        job: String,
        attempt: Long,
        time: Long,
    ): TranscriptDocument {
        val value = running(source, job, attempt)
        require(value.completedParts == value.totalParts && value.nextIncompleteIndex == null)
        val root = MessageDigest.getInstance("SHA-256")
        root.update(
            ("dora-paged-root-v1\u0000" + source.digest + value.spec.configDigest).toByteArray(
                Charsets.UTF_8
            )
        )
        var pageCount = 0L
        for (index in 0 until value.totalParts) {
            val part = requireNotNull(dao.part(source.identity.assetId.value, job, index))
            require(
                part.pageCount in 1..PagedTranscriptLimits.MAX_PAGES_PER_PART &&
                    part.textBytes in 0..PagedTranscriptText.MAX_PART_BYTES.toLong()
            )
            root.update(PagedTranscriptWire.partRoot(value.spec, part))
            pageCount += part.pageCount
            part.payload.fill(0)
        }
        val digest = root.digest().joinToString("") { "%02x".format(it) }
        return mutate(
            source.identity,
            { old ->
                val doc = requireNotNull(old)
                val reference =
                    PagedTranscriptReference(
                        value.spec.engine,
                        value.spec.configDigest,
                        value.spec.windowPolicy,
                        value.totalParts,
                        pageCount,
                        value.textBytes,
                        digest,
                        doc.revision + 1,
                    )
                TranscriptReducer.finishPaged(doc, job, reference, value.spec.model, time)
            },
            {},
            { true },
        )
    }

    fun page(
        identity: AudioIdentity,
        job: String,
        after: TranscriptPageCursor?,
        revision: Long?,
        allowPartial: Boolean,
    ): TranscriptTextPage? {
        val doc = requireNotNull(document(identity))
        val version = doc.versions.singleOrNull { it.id == job }
        require(version?.paged != null || allowPartial && progress(identity, job) != null)
        val at = revision ?: doc.revision
        require(at in 0..doc.revision)
        version?.paged?.let { require(at >= it.publicationRevision) }
        val row =
            dao.nextPage(
                identity.assetId.value,
                job,
                after?.partIndex ?: -1,
                after?.pageIndex ?: -1,
            ) ?: return null
        val edited = dao.edit(row.assetId, job, row.partIndex, row.pageIndex, at)
        val bytes = edited?.payload ?: row.payload
        return try {
            TranscriptTextPage(
                TranscriptPageCursor(row.partIndex, row.pageIndex),
                PagedTranscriptText.decode(bytes),
                PagedTranscriptText.digest(bytes),
                version != null,
            )
        } finally {
            row.payload.fill(0)
            edited?.payload?.fill(0)
        }
    }

    fun editPage(
        identity: AudioIdentity,
        job: String,
        cursor: TranscriptPageCursor,
        expected: Long,
        before: String,
        text: String,
        time: Long,
    ): TranscriptDocument {
        val old = requireNotNull(document(identity))
        require(
            old.revision == expected &&
                time >= 0 &&
                old.versions.single { it.id == job }.paged != null
        )
        val row =
            requireNotNull(
                dao.page(identity.assetId.value, job, cursor.partIndex, cursor.pageIndex)
            )
        val edit = dao.edit(row.assetId, job, cursor.partIndex, cursor.pageIndex, old.revision)
        require(PagedTranscriptText.digest(edit?.payload ?: row.payload) == before)
        val pages = PagedTranscriptText.pages(text)
        require(pages.size == 1)
        val bytes = pages.single().toByteArray(Charsets.UTF_8)
        require(!bytes.contentEquals(edit?.payload ?: row.payload))
        row.payload.fill(0)
        edit?.payload?.fill(0)
        val replacement =
            PagedEditEntity(
                identity.assetId.value,
                job,
                cursor.partIndex,
                cursor.pageIndex,
                expected + 1,
                before,
                time,
                bytes,
            )
        try {
            return mutate(
                identity,
                { requireNotNull(it).copy(revision = expected + 1) },
                { dao.insert(replacement) },
                {
                    dao.edit(row.assetId, job, cursor.partIndex, cursor.pageIndex, expected + 1)
                        ?.payload
                        ?.contentEquals(bytes) == true
                },
            )
        } finally {
            bytes.fill(0)
        }
    }
}

/** Small bounded headers; recognized text pages are separate SQLCipher rows. */
@Suppress("TooManyFunctions", "MagicNumber")
internal object PagedTranscriptWire {
    private fun encode(body: (DataOutputStream) -> Unit): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use(body)
            bytes.toByteArray().also {
                require(it.size <= PagedTranscriptLimits.MAX_PROVENANCE_BYTES)
            }
        }

    private fun <T> decode(bytes: ByteArray, body: (DataInputStream) -> T): T {
        require(bytes.size <= PagedTranscriptLimits.MAX_PROVENANCE_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            body(input).also { require(input.available() == 0) }
        }
    }

    private fun DataOutputStream.text(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(
            bytes.size <= 262_144 &&
                size().toLong() + 4 + bytes.size <= PagedTranscriptLimits.MAX_PROVENANCE_BYTES
        )
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.text(): String {
        val size = readInt()
        require(size in 0..262_144 && size <= available())
        return PagedTranscriptText.decode(ByteArray(size).also(::readFully))
    }

    fun spec(value: PagedTranscriptSpec) = encode { out ->
        out.writeInt(1)
        out.writeInt(value.source.version)
        out.text(value.source.identity.recordingId.value)
        out.text(value.source.identity.assetId.value)
        out.text(value.source.identity.sessionId)
        out.text(value.source.digest)
        out.writeLong(value.source.frames)
        out.text(value.jobId)
        out.text(value.engine)
        out.text(value.language)
        out.text(value.model)
        out.text(value.windowPolicy)
        out.writeLong(value.windowFrames)
        out.writeLong(value.createdAt)
        out.writeBoolean(value.consent != null)
        value.consent?.let {
            out.text(it.version)
            out.text(it.source)
            out.writeLong(it.grantedAt)
            out.writeBoolean(it.revoked)
            out.text(it.destination)
        }
    }

    fun spec(bytes: ByteArray) =
        decode(bytes) { input ->
            require(input.readInt() == 1)
            val version = input.readInt()
            val identity =
                AudioIdentity(RecordingId(input.text()), AudioAssetId(input.text()), input.text())
            val source = OriginalAudioReference(version, identity, input.text(), input.readLong())
            PagedTranscriptSpec(
                source,
                input.text(),
                input.text(),
                input.text(),
                input.text(),
                input.text(),
                input.readLong(),
                input.readLong(),
                if (input.readBoolean())
                    CloudConsent(
                        input.text(),
                        input.text(),
                        input.readLong(),
                        input.readBoolean(),
                        input.text(),
                    )
                else null,
            )
        }

    fun provenance(value: TranscriptPartProvenance?) = encode { out ->
        out.writeBoolean(value != null)
        value?.let {
            out.text(it.providerId)
            out.text(it.providerJobId)
            out.writeLong(it.completedAt)
            out.writeInt(it.segments.size)
            it.segments.forEach { segment ->
                out.text(segment.text)
                out.writeLong(segment.startMs ?: -1)
                out.writeLong(segment.endMs ?: -1)
                out.writeBoolean(segment.confidence != null)
                segment.confidence?.let(out::writeDouble)
            }
        }
    }

    fun provenance(bytes: ByteArray): TranscriptPartProvenance? =
        decode(bytes) { input ->
            if (!input.readBoolean()) null
            else {
                val provider = input.text()
                val job = input.text()
                val time = input.readLong()
                val count = input.readInt()
                require(count in 0..PagedTranscriptLimits.MAX_CLOUD_SEGMENTS)
                TranscriptPartProvenance(
                    provider,
                    job,
                    time,
                    List(count) {
                        CloudSegment(
                            input.text(),
                            input.readLong().takeIf { it >= 0 },
                            input.readLong().takeIf { it >= 0 },
                            if (input.readBoolean()) input.readDouble() else null,
                        )
                    },
                )
            }
        }

    fun partRoot(spec: PagedTranscriptSpec, part: PagedPartEntity) = encode { out ->
        val range = spec.range(part.partIndex)
        out.writeInt(part.partIndex)
        out.writeLong(range.firstFrame)
        out.writeLong(range.frameCount)
        out.writeInt(part.pageCount)
        out.writeLong(part.textBytes)
        out.text(part.digest)
        out.text(PagedTranscriptText.digest(part.payload))
    }
}
