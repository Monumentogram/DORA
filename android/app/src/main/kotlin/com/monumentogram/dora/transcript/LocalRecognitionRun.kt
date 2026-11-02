package com.monumentogram.dora.transcript

import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.transcript.PagedTranscriptSpec
import com.monumentogram.dora.audio.transcript.ProtectedPcm
import com.monumentogram.dora.audio.transcript.TranscriptDocument
import com.monumentogram.dora.audio.transcript.UnavailablePagedTranscriptPort
import java.util.concurrent.CancellationException

/** Compatible saved-audio recognition boundary; invoked only on the dedicated ASR executor. */
internal class LocalRecognitionRun(
    private val decode: (FloatArray, String, () -> Boolean) -> String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    // Every failure must durably terminate the attempt; cancellation takes precedence over engine
    // exceptions.
    // One attempt owns authentication, checkpoint publication, cancellation and terminal cleanup.
    @Suppress(
        "LongParameterList",
        "TooGenericExceptionCaught",
        "ThrowsCount",
        "LongMethod",
        "CyclomaticComplexMethod",
        "NestedBlockDepth",
    )
    fun run(
        session: ProductAudioSession,
        identity: AudioIdentity,
        jobId: String,
        language: String,
        model: String,
        attempt: RecognitionAttempt,
        state: (String) -> Unit,
    ): AudioResult<TranscriptDocument> {
        var pagedAttempt: Long? = null
        var prepared: com.monumentogram.dora.audio.AuthenticatedAudioSource? = null
        fun stop(status: String) {
            val fence = pagedAttempt
            if (fence == null) session.transcripts.stop(identity, jobId, status)
            else session.transcripts.paged.stop(identity, jobId, fence, status)
        }
        try {
            if (!attempt.start()) throw CancellationException()
            if (session.transcripts.paged !== UnavailablePagedTranscriptPort)
                prepared = requireValue(session.originals.openAuthenticated(identity))
            val acquired = if (prepared == null) session.originals.acquire(identity) else null
            val source =
                prepared?.reference
                    ?: ((acquired as? AudioResult.Value)?.value as? OriginalAudioStatus.Available)
                        ?.reference
                    ?: throw RecognitionFailure("SOURCE_UNAVAILABLE")
            if (
                source.frames > ProtectedPcm.MAX_WINDOW_FRAMES &&
                    session.transcripts.paged !== UnavailablePagedTranscriptPort
            ) {
                val port = session.transcripts.paged
                val retained = requireValue(port.progress(identity, jobId))
                val proposed =
                    PagedTranscriptSpec(
                        source,
                        jobId,
                        "LOCAL",
                        language,
                        model,
                        "local-fixed-300-v1",
                        ProtectedPcm.MAX_WINDOW_FRAMES.toLong(),
                        retained?.spec?.createdAt ?: now(),
                    )
                require(retained == null || retained.spec == proposed)
                return checkNotNull(prepared).use { authenticated ->
                    var progress =
                        if (retained == null) requireValue(port.begin(proposed))
                        else requireValue(port.resume(identity, jobId))
                    pagedAttempt = progress.attempt
                    while (progress.nextIncompleteIndex != null) {
                        if (attempt.cancelled()) throw CancellationException()
                        val index = checkNotNull(progress.nextIncompleteIndex)
                        val range = proposed.range(index)
                        state("Распознавание: ${progress.completedParts}/${progress.totalParts}")
                        val part =
                            ProtectedPcm.readWindow(
                                    authenticated,
                                    range.firstFrame,
                                    range.frameCount.toInt(),
                                    attempt::cancelled,
                                )
                                .use { pcm ->
                                    decode(pcm.samples, language, attempt::cancelled)
                                }
                        if (attempt.cancelled()) throw CancellationException()
                        progress =
                            requireValue(
                                port.commitPart(source, jobId, progress.attempt, index, part)
                            )
                    }
                    if (!attempt.publish()) throw CancellationException()
                    state("Сохраняем расшифровку")
                    AudioResult.Value(
                        requireValue(port.finish(source, jobId, progress.attempt, now()))
                    )
                }
            }
            requireValue(session.transcripts.begin(source, jobId, language, now()))
            state("Ожидает обработки")
            if (attempt.cancelled()) throw CancellationException()
            requireValue(session.transcripts.running(identity, jobId))
            state("Распознавание")
            val text = decodeWindows(session, source, language, attempt)
            if (!attempt.publish()) throw CancellationException()
            state("Сохраняем расшифровку")
            return AudioResult.Value(
                requireValue(
                    session.transcripts.finish(source, jobId, text.toString(), model, now())
                )
            )
        } catch (_: CancellationException) {
            stop("CANCELLED")
            throw RecognitionFailure("CANCELLED")
        } catch (error: RecognitionFailure) {
            stop(if (attempt.cancelled()) "CANCELLED" else "FAILED")
            if (attempt.cancelled()) throw RecognitionFailure("CANCELLED")
            throw error
        } catch (_: Exception) {
            if (attempt.cancelled()) {
                stop("CANCELLED")
                throw RecognitionFailure("CANCELLED")
            }
            stop("FAILED")
            throw RecognitionFailure("ENGINE_OR_STORAGE")
        } finally {
            prepared?.close()
        }
    }

    private fun decodeWindows(
        session: ProductAudioSession,
        source: com.monumentogram.dora.audio.OriginalAudioReference,
        language: String,
        attempt: RecognitionAttempt,
    ): String {
        val text = StringBuilder()
        var next = 0L
        while (next < source.frames) {
            val count = minOf(ProtectedPcm.MAX_WINDOW_FRAMES.toLong(), source.frames - next).toInt()
            ProtectedPcm.readWindow(session.originals, source, next, count, attempt::cancelled)
                .use { pcm ->
                    val part = decode(pcm.samples, language, attempt::cancelled)
                    if (text.isNotEmpty() && part.isNotEmpty()) text.append('\n')
                    text.append(part)
                }
            next += count
        }
        return text.toString()
    }
}

internal fun <T> requireValue(result: AudioResult<T>): T =
    when (result) {
        is AudioResult.Value -> result.value
        is AudioResult.Failed -> throw RecognitionFailure(result.reason.name)
    }

internal class RecognitionFailure(val category: String) :
    IllegalStateException("Local recognition failed")
