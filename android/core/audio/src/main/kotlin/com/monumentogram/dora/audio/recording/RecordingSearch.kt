package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.transcript.TranscriptDocument
import java.util.Locale
import java.util.concurrent.CancellationException

/** Bounded presentation only. Never persist, log or use as an audio-access capability. */
data class RecordingPreview(
    val title: String,
    val snippet: String,
    val transcriptDate: Long?,
    val processing: Boolean,
) {
    override fun toString() = "RecordingPreview(redacted)"
}

data class RecordingSearchRow(val recording: RecordingRecovery, val preview: RecordingPreview) {
    override fun toString() = "RecordingSearchRow(redacted)"
}

data class RecordingSearchPage(val rows: List<RecordingSearchRow>, val nextCursor: String?) {
    override fun toString() = "RecordingSearchPage(redacted)"
}

/** Runs on the authenticated persistence worker; holds one document and one result page. */
internal class RecordingSearch(
    private val candidates: (String) -> List<Pair<String, AudioIdentity?>>,
    private val document: (AudioIdentity) -> AudioResult<TranscriptDocument?>,
    private val recovery: (AudioIdentity) -> AudioResult<RecordingRecovery>,
) {
    @Suppress(
        "ReturnCount"
    ) // Fail closed on read/cancellation; never present an incomplete search.
    fun page(
        query: String,
        nameFormat: String,
        after: String,
        cancelled: () -> Boolean,
    ): AudioResult<RecordingSearchPage> =
        try {
            scan(query, nameFormat, after, cancelled)
        } catch (_: CancellationException) {
            AudioResult.Failed(AudioFailure.CANCELLED)
        }

    @Suppress("ReturnCount") // Each read/cancellation fails closed without partial results.
    private fun scan(
        query: String,
        nameFormat: String,
        after: String,
        cancelled: () -> Boolean,
    ): AudioResult<RecordingSearchPage> {
        val normalized = query.trim()
        val matcher = LiteralSubstring(normalized, cancelled)
        val rows = mutableListOf<RecordingSearchRow>()
        var cursor = after
        while (!cancelled()) {
            val batch = candidates(cursor)
            if (batch.isEmpty()) return AudioResult.Value(RecordingSearchPage(rows, null))
            for ((key, identity) in batch) {
                if (cancelled()) return AudioResult.Failed(AudioFailure.CANCELLED)
                check(key > cursor)
                cursor = key
                val loaded = identity?.let(document) ?: AudioResult.Value(null)
                if (loaded is AudioResult.Failed) return loaded
                val title =
                    String.format(
                        Locale.ROOT,
                        nameFormat,
                        identity?.recordingId?.value?.take(SHORT_ID_LENGTH).orEmpty(),
                    )
                val preview =
                    preview((loaded as AudioResult.Value).value, title, matcher) ?: continue
                if (cancelled()) return AudioResult.Failed(AudioFailure.CANCELLED)
                val recovered =
                    identity?.let(recovery)
                        ?: AudioResult.Value(
                            RecordingRecovery(
                                null,
                                null,
                                AudioFailure.CORRUPT,
                                false,
                                0,
                                cursor = key,
                            )
                        )
                if (recovered is AudioResult.Failed) return recovered
                rows += RecordingSearchRow((recovered as AudioResult.Value).value, preview)
                if (rows.size == PAGE_SIZE)
                    return AudioResult.Value(RecordingSearchPage(rows, cursor))
            }
        }
        return AudioResult.Failed(AudioFailure.CANCELLED)
    }

    companion object {
        private const val PAGE_SIZE = 20
        private const val SHORT_ID_LENGTH = 8
        private const val DISPLAY_LENGTH = 240
        private const val CONTEXT_LENGTH = 48

        fun preview(
            document: TranscriptDocument?,
            fallback: String,
            query: String,
        ): RecordingPreview? = preview(document, fallback, LiteralSubstring(query.trim()) { false })

        private fun preview(
            document: TranscriptDocument?,
            fallback: String,
            matcher: LiteralSubstring,
        ): RecordingPreview? {
            // No fallback to the latest machine result: only the selected version is authoritative.
            val active = document?.versions?.singleOrNull { it.id == document.activeId }
            val text = active?.let { document.rendered(it.id) }.orEmpty()
            val title = text.lineSequence().firstOrNull { it.isNotBlank() } ?: fallback
            val match = matcher.indexIn(text)
            if (match < 0 && matcher.indexIn(title) < 0) return null
            val start = (match - CONTEXT_LENGTH).coerceAtLeast(0).safeBoundary(text)
            val end = (start + DISPLAY_LENGTH).coerceAtMost(text.length).safeBoundary(text)
            val snippet =
                (if (start > 0) "…" else "") +
                    text.substring(start, end) +
                    (if (end < text.length) "…" else "")
            return RecordingPreview(
                title.take(DISPLAY_LENGTH.safeBoundary(title)),
                snippet,
                active?.createdAt,
                document?.jobs?.lastOrNull()?.status in setOf("QUEUED", "RUNNING"),
            )
        }

        private fun Int.safeBoundary(text: String): Int {
            val boundary = coerceAtMost(text.length)
            if (boundary == 0 || boundary == text.length) return boundary
            return if (text[boundary].isLowSurrogate() && text[boundary - 1].isHighSurrogate())
                boundary - 1
            else boundary
        }
    }
}
