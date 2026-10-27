@file:Suppress("TooGenericExceptionCaught", "MagicNumber", "LongParameterList")

package com.monumentogram.dora.transcript

import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.transcript.TranscriptDocument
import java.util.concurrent.CancellationException

/** Foreground manual work only. Restart retains the action and requires an explicit retry. */
internal class CloudRecognitionRun(
    private val api: CloudApi,
    private val cancellations: CloudCancellations,
    private val pause: () -> Unit = { Thread.sleep(2000) },
) {
    @Suppress("LongMethod", "CyclomaticComplexMethod", "ThrowsCount")
    // One consent -> transport -> publication boundary and shared fail-closed guard.
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
        require(job.status in setOf("QUEUED", "RUNNING", "FAILED", "INTERRUPTED"))
        val source = stored.source
        fun guard() {
            if (attempt.cancelled() || cancellations.blocked(jobId, api.endpoint))
                throw CancellationException()
            val current = requireNotNull(requireValue(session.transcripts.load(identity)))
            val active = current.jobs.single { it.id == jobId }
            require(current.source == source && active.consent == consent)
            require(active.status in setOf("QUEUED", "RUNNING"))
            if (attempt.cancelled() || cancellations.blocked(jobId, api.endpoint))
                throw CancellationException()
        }
        try {
            if (!attempt.start() || cancellations.blocked(jobId, api.endpoint))
                throw CancellationException()
            requireValue(session.transcripts.resumeCloud(identity, jobId))
            guard()
            reconcileCancellations()
            guard()
            api.register(source)
            guard()
            var remote = api.create(source, jobId, job.language, consent)
            if (remote.status == "FAILED" && remote.retryable) {
                guard()
                remote = api.retry(jobId, attempt::cancelled)
            }
            if (remote.status == "UPLOADING")
                remote = upload(session, source, jobId, remote, attempt, ::guard, state)
            remote = poll(jobId, remote, ::guard, state)
            guard()
            checkCompleted(remote)
            val output = requireNotNull(remote.result)
            if (!attempt.publish()) throw CancellationException()
            state("Сохраняем облачную версию")
            return AudioResult.Value(
                requireValue(session.transcripts.finishCloud(source, jobId, output))
            )
        } catch (_: CancellationException) {
            cancel(session, identity, jobId)
        } catch (error: Exception) {
            if (attempt.cancelled()) cancel(session, identity, jobId)
            session.transcripts.stop(identity, jobId, "FAILED")
            throw (error as? CloudFailure) ?: CloudFailure("SOURCE_OR_STORAGE")
        }
    }

    private fun reconcileCancellations() {
        cancellations.actions(api.endpoint).forEach { id ->
            try {
                val cancelled = api.cancel(id)
                if (cancelled.status != "CANCELLED" || cancelled.cleanupPending)
                    throw CloudFailure("CANCEL_PENDING")
            } catch (error: CloudFailure) {
                if (error.code != "NOT_FOUND") throw error
            }
        }
    }

    private fun cancel(
        session: ProductAudioSession,
        identity: AudioIdentity,
        jobId: String,
    ): Nothing {
        cancellations.record(jobId, api.endpoint)
        session.transcripts.revokeCloud(identity, jobId)
        try {
            api.cancel(jobId)
        } catch (_: Exception) {
            // Marker remains even if the vault lease or network is unavailable.
        }
        throw CloudFailure("CANCELLED")
    }

    private fun upload(
        session: ProductAudioSession,
        source: OriginalAudioReference,
        jobId: String,
        initial: CloudJobState,
        attempt: RecognitionAttempt,
        guard: () -> Unit,
        state: (String) -> Unit,
    ): CloudJobState {
        guard()
        checkSource(session, source)
        return CloudAudio.read(session.originals, source, attempt::cancelled).use { audio ->
            var remote = initial
            var offset = remote.uploadedBytes
            require(offset in 0..audio.bytes.size)
            // Final bytes may be accepted before quota/dispatch/reply failure. Replaying the
            // exact last chunk is idempotent under this action; it cannot create a second job.
            if (offset == audio.bytes.size) offset = ((offset - 1) / CHUNK) * CHUNK
            while (offset < audio.bytes.size) {
                guard()
                checkSource(session, source)
                state("Отправка аудио: ${offset * 100L / audio.bytes.size}%")
                val end = minOf(offset + CHUNK, audio.bytes.size)
                val part = audio.bytes.copyOfRange(offset, end)
                try {
                    remote =
                        api.upload(jobId, offset, part, end == audio.bytes.size, attempt::cancelled)
                } finally {
                    part.fill(0)
                }
                require(remote.uploadedBytes == end)
                offset = end
            }
            remote
        }
    }

    private fun checkSource(session: ProductAudioSession, source: OriginalAudioReference) {
        check(
            requireValue(session.originals.inspect(source)) == OriginalAudioStatus.Available(source)
        )
    }

    private fun poll(
        jobId: String,
        initial: CloudJobState,
        guard: () -> Unit,
        state: (String) -> Unit,
    ): CloudJobState {
        var remote = initial
        var polls = 0
        while (remote.status in setOf("QUEUED", "IN_PROGRESS")) {
            guard()
            state(
                if (remote.status == "QUEUED") "Ожидает обработки в облаке"
                else "Распознавание в облаке"
            )
            if (++polls > 180) throw CloudFailure("WAIT_TIMEOUT")
            pause()
            guard()
            remote = api.status(jobId)
        }
        return remote
    }

    private fun checkCompleted(remote: CloudJobState) {
        if (remote.status == "CANCELLED") throw CancellationException()
        if (remote.status != "COMPLETED") throw CloudFailure("PROCESSING")
    }

    companion object {
        private const val CHUNK = 65536
    }
}
