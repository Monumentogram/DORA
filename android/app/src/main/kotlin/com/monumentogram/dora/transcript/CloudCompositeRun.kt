@file:Suppress(
    "MagicNumber",
    "LongMethod",
    "LongParameterList",
    "TooGenericExceptionCaught",
    "ThrowsCount",
    "TooManyFunctions",
)

package com.monumentogram.dora.transcript

import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AuthenticatedAudioSource
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.transcript.CloudTranscriptResult
import com.monumentogram.dora.audio.transcript.PagedTranscriptLimits
import com.monumentogram.dora.audio.transcript.PagedTranscriptSpec
import com.monumentogram.dora.audio.transcript.PagedTranscriptText
import com.monumentogram.dora.audio.transcript.TranscriptDocument
import com.monumentogram.dora.audio.transcript.TranscriptPartProvenance
import java.util.concurrent.CancellationException

/** One foreground attempt; encrypted committed parts survive interruptions and process restart. */
internal class CloudCompositeRun(
    private val api: CloudApi,
    private val cancellations: CloudCancellations,
    private val pause: () -> Unit = { Thread.sleep(2000) },
    private val now: () -> Long = System::currentTimeMillis,
    private val authenticated: AuthenticatedAudioSource? = null,
) {
    @Suppress("CyclomaticComplexMethod")
    fun run(
        session: ProductAudioSession,
        identity: AudioIdentity,
        jobId: String,
        attempt: RecognitionAttempt,
        state: (String) -> Unit,
    ): AudioResult<TranscriptDocument> {
        val stored = requireNotNull(requireValue(session.transcripts.load(identity)))
        val job = stored.jobs.single { it.id == jobId }
        val consent = requireNotNull(job.consent)
        consent.validate()
        require(!consent.revoked && consent.destination == api.endpoint)
        val source = stored.source
        require(source.frames in 8000..CloudAudio.MAX_COMPOSITE_FRAMES)
        val progress =
            requireNotNull(requireValue(session.transcripts.paged.progress(identity, jobId)))
        val spec = progress.spec
        require(spec.source == source && spec.consent == consent && spec.engine == "CLOUD")
        require(
            spec.model ==
                "standard-batch:eu-central-1:" + if (job.language == "ru") "ru-RU" else "en-US"
        )
        var fence = progress.attempt
        var audio: AuthenticatedAudioSource? = authenticated
        var running = false
        fun guard() {
            if (attempt.cancelled() || cancellations.blocked(jobId, api.endpoint))
                throw CancellationException()
            val current = requireNotNull(requireValue(session.transcripts.load(identity)))
            val active = current.jobs.single { it.id == jobId }
            require(current.source == source && active.consent == consent)
            require(
                active.status in
                    if (running) setOf("QUEUED", "RUNNING")
                    else setOf("QUEUED", "RUNNING", "FAILED", "INTERRUPTED")
            )
            audio?.let {
                check(requireValue(it.checkAvailable()) == OriginalAudioStatus.Available(source))
            }
            if (attempt.cancelled() || cancellations.blocked(jobId, api.endpoint))
                throw CancellationException()
        }
        fun interrupted(): Nothing {
            if (cancellations.blocked(jobId, api.endpoint)) {
                session.transcripts.paged.stop(identity, jobId, fence, "CANCELLED")
                session.transcripts.revokeCloud(identity, jobId)
                try {
                    api.cancel(jobId)
                } catch (_: Exception) {
                    /* Durable marker fences later sends. */
                }
                throw CloudFailure("CANCELLED")
            }
            session.transcripts.paged.stop(identity, jobId, fence, "INTERRUPTED")
            throw CloudFailure("INTERRUPTED")
        }
        try {
            if (!attempt.start() || cancellations.blocked(jobId, api.endpoint))
                throw CancellationException()
            if (progress.published) return AudioResult.Value(stored)
            guard()
            reconcileCancellations()
            guard()
            val quote =
                try {
                    api.quoteRun(spec)
                } catch (error: CloudFailure) {
                    if (error.code == "NOT_FOUND") throw CloudFailure("LONG_NOT_READY")
                    throw error
                }
            guard()
            require(quote.partCount == spec.partCount && quote.windowPolicy == spec.windowPolicy)
            if (
                !quote.enabled ||
                    quote.maxFrames < source.frames ||
                    quote.maxPartFrames < spec.windowFrames
            )
                throw CloudFailure("LONG_NOT_READY")
            if (!quote.affordable)
                throw CloudBudgetFailure(quote.requiredMicroUsd, quote.availableMicroUsd)
            if (audio == null) audio = requireValue(session.originals.openAuthenticated(source))
            require(audio.reference == source)
            fence = requireValue(session.transcripts.paged.resume(identity, jobId)).attempt
            running = true
            guard()
            val remote = api.createRun(spec)
            guard()
            validateRun(remote, spec)
            for (index in 0 until spec.partCount) {
                guard()
                if (requireValue(session.transcripts.paged.part(identity, jobId, index)) != null)
                    continue
                state("Облачная часть ${index + 1} из ${spec.partCount}")
                var child = api.runPart(jobId, index)
                validatePart(child, spec, index, remote.parts[index].id)
                if (child.status == "FAILED" && child.retryable) {
                    guard()
                    child = api.retry(child.id, attempt::cancelled)
                }
                validatePart(child, spec, index, remote.parts[index].id)
                if (child.status == "UPLOADING") {
                    guard()
                    child = upload(audio, child, spec, index, attempt, ::guard, state)
                }
                child = poll(child, ::guard, state)
                guard()
                validatePart(child, spec, index, remote.parts[index].id)
                if (child.status == "CANCELLED") throw CancellationException()
                if (child.status != "COMPLETED") throw CloudFailure("PROCESSING")
                val output = requireNotNull(child.result)
                val provenance = provenance(output, spec, index)
                requireValue(
                    session.transcripts.paged.commitPart(
                        source,
                        jobId,
                        fence,
                        index,
                        output.text,
                        provenance,
                    )
                )
            }
            guard()
            if (!attempt.publish()) throw CancellationException()
            state("Сохраняем полную облачную версию")
            return session.transcripts.paged.finish(source, jobId, fence, now())
        } catch (_: CancellationException) {
            interrupted()
        } catch (error: Exception) {
            if (attempt.cancelled()) interrupted()
            session.transcripts.paged.stop(identity, jobId, fence, "FAILED")
            throw (error as? CloudFailure) ?: CloudFailure("SOURCE_OR_STORAGE")
        } finally {
            if (authenticated == null) audio?.close()
        }
    }

    private fun validateRun(run: CloudRunState, spec: PagedTranscriptSpec) {
        require(
            run.id == spec.jobId &&
                run.sourceDigest == spec.source.digest &&
                run.sourceVersion == spec.source.version
        )
        require(
            run.frames == spec.source.frames &&
                run.windowPolicy == spec.windowPolicy &&
                run.parts.size == spec.partCount
        )
        require(run.status in setOf("UPLOADING", "PARTIAL", "COMPLETED"))
        require(run.parts.map { it.id }.toSet().size == run.parts.size)
        run.parts.forEachIndexed { index, part ->
            val range = spec.range(index)
            require(
                part.index == index &&
                    part.firstFrame == range.firstFrame &&
                    part.frames == range.frameCount
            )
        }
    }

    private fun validatePart(
        child: CloudJobState,
        spec: PagedTranscriptSpec,
        index: Int,
        expectedId: String,
    ) {
        val range = spec.range(index)
        require(child.id == expectedId && child.parentId == spec.jobId && child.partIndex == index)
        require(child.firstFrame == range.firstFrame && child.frames == range.frameCount)
        require(child.uploadedBytes in 0..range.frameCount * 2 + 44)
        if (child.status == "COMPLETED") require(child.uploadedBytes == range.frameCount * 2 + 44)
    }

    // Keep authenticated window ownership and per-chunk wiping visible in the upload sequence.
    @Suppress("NestedBlockDepth")
    private fun upload(
        audio: AuthenticatedAudioSource,
        initial: CloudJobState,
        spec: PagedTranscriptSpec,
        index: Int,
        attempt: RecognitionAttempt,
        guard: () -> Unit,
        state: (String) -> Unit,
    ): CloudJobState {
        val range = spec.range(index)
        val total = range.frameCount * 2 + 44
        var remote = initial
        var offset = remote.uploadedBytes
        require(offset in 0..total)
        if (offset == total) offset = ((total - 1) / CHUNK) * CHUNK
        require(offset % CHUNK == 0L)
        while (offset < total) {
            guard()
            val count = minOf(CloudAudio.MAX_WINDOW_BYTES.toLong(), total - offset).toInt()
            CloudAudio.readWindow(
                    audio,
                    range.firstFrame,
                    range.frameCount,
                    offset,
                    count,
                    attempt::cancelled,
                )
                .use { window ->
                    var first = 0
                    while (first < window.bytes.size) {
                        guard()
                        state(
                            "Облачная часть ${index + 1}/${spec.partCount}: ${offset * 100L / total}%"
                        )
                        val end = minOf(first + CHUNK, window.bytes.size)
                        val bytes = window.bytes.copyOfRange(first, end)
                        val next = offset + bytes.size
                        try {
                            remote =
                                api.upload(
                                    initial.id,
                                    offset,
                                    bytes,
                                    next == total,
                                    attempt::cancelled,
                                )
                        } finally {
                            bytes.fill(0)
                        }
                        validatePart(remote, spec, index, initial.id)
                        require(remote.uploadedBytes == next)
                        offset = next
                        first = end
                    }
                }
        }
        return remote
    }

    private fun poll(
        initial: CloudJobState,
        guard: () -> Unit,
        state: (String) -> Unit,
    ): CloudJobState {
        var remote = initial
        var polls = 0
        while (remote.status in setOf("QUEUED", "IN_PROGRESS")) {
            guard()
            state("Распознавание облачной части; готовые части сохранены")
            if (++polls > 180) throw CloudFailure("WAIT_TIMEOUT")
            pause()
            guard()
            remote = api.status(initial.id)
        }
        return remote
    }

    private fun provenance(
        output: CloudTranscriptResult,
        spec: PagedTranscriptSpec,
        index: Int,
    ): TranscriptPartProvenance {
        require(
            output.sourceDigest == spec.source.digest && output.sourceVersion == spec.source.version
        )
        require(
            output.providerId == "amazon-transcribe" &&
                output.model == spec.model &&
                output.completedAt >= 0
        )
        require(output.providerJobId.matches(Regex("[A-Za-z0-9._-]{1,200}")))
        require(output.text.toByteArray(Charsets.UTF_8).size <= PagedTranscriptText.MAX_PART_BYTES)
        require(output.segments.size <= PagedTranscriptLimits.MAX_CLOUD_SEGMENTS)
        val range = spec.range(index)
        val duration = range.frameCount / 16
        val base = range.firstFrame / 16
        val segments =
            output.segments.map {
                val start = it.startMs
                val end = it.endMs
                val confidence = it.confidence
                require(start == null || start in 0..duration)
                require(end == null || end in 0..duration)
                require(start == null || end == null || start <= end)
                require(confidence == null || confidence.isFinite() && confidence in 0.0..1.0)
                it.copy(
                    startMs = start?.let { value -> Math.addExact(base, value) },
                    endMs = end?.let { value -> Math.addExact(base, value) },
                )
            }
        return TranscriptPartProvenance(
            output.providerId,
            output.providerJobId,
            output.completedAt,
            segments,
        )
    }

    private fun reconcileCancellations() {
        cancellations.actions(api.endpoint).forEach { id ->
            try {
                val response = api.cancel(id)
                if (response.status != "CANCELLED" || response.cleanupPending)
                    throw CloudFailure("CANCEL_PENDING")
            } catch (error: CloudFailure) {
                if (error.code != "NOT_FOUND") throw error
            }
        }
    }

    private companion object {
        const val CHUNK = 65536
    }
}
