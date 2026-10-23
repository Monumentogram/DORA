package com.monumentogram.dora.transcript

import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.transcript.ProtectedPcm
import com.monumentogram.dora.audio.transcript.TranscriptDocument
import java.util.concurrent.CancellationException

/** Compatible saved-audio recognition boundary; invoked only on the dedicated ASR executor. */
internal class LocalRecognitionRun(
    private val decode: (FloatArray, String, () -> Boolean) -> String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    // Every failure must durably terminate the attempt; cancellation takes precedence over engine
    // exceptions.
    @Suppress("LongParameterList", "TooGenericExceptionCaught", "ThrowsCount")
    fun run(
        session: ProductAudioSession,
        identity: AudioIdentity,
        jobId: String,
        language: String,
        model: String,
        attempt: RecognitionAttempt,
        state: (String) -> Unit,
    ): AudioResult<TranscriptDocument> {
        try {
            if (!attempt.start()) throw CancellationException()
            val acquired = session.originals.acquire(identity)
            val source =
                ((acquired as? AudioResult.Value)?.value as? OriginalAudioStatus.Available)
                    ?.reference ?: throw RecognitionFailure("SOURCE_UNAVAILABLE")
            requireValue(session.transcripts.begin(source, jobId, language, now()))
            state("Ожидает обработки")
            if (attempt.cancelled()) throw CancellationException()
            requireValue(session.transcripts.running(identity, jobId))
            state("Распознавание")
            val text =
                ProtectedPcm.read(session.originals, source, attempt::cancelled).use { pcm ->
                    decode(pcm.samples, language, attempt::cancelled)
                }
            if (!attempt.publish()) throw CancellationException()
            state("Сохраняем расшифровку")
            return AudioResult.Value(
                requireValue(session.transcripts.finish(source, jobId, text, model, now()))
            )
        } catch (_: CancellationException) {
            session.transcripts.stop(identity, jobId, "CANCELLED")
            throw RecognitionFailure("CANCELLED")
        } catch (error: RecognitionFailure) {
            session.transcripts.stop(identity, jobId, "FAILED")
            throw error
        } catch (_: Exception) {
            if (attempt.cancelled()) {
                session.transcripts.stop(identity, jobId, "CANCELLED")
                throw RecognitionFailure("CANCELLED")
            }
            session.transcripts.stop(identity, jobId, "FAILED")
            throw RecognitionFailure("ENGINE_OR_STORAGE")
        }
    }
}

internal fun <T> requireValue(result: AudioResult<T>): T =
    when (result) {
        is AudioResult.Value -> result.value
        is AudioResult.Failed -> throw RecognitionFailure(result.reason.name)
    }

internal class RecognitionFailure(val category: String) :
    IllegalStateException("Local recognition failed")
