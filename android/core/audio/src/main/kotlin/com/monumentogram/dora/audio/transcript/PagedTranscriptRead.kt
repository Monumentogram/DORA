package com.monumentogram.dora.audio.transcript

import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.recording.LiteralSubstring
import com.monumentogram.dora.audio.recording.RecordingPreview
import java.util.concurrent.CancellationException

/** Bounded rendering for export/search; never materializes a complete long version. */
object PagedTranscriptRead {
    private const val MAX_QUERY_CHARACTERS = 4096
    private const val DISPLAY_CHARACTERS = 240
    private const val CONTEXT_CHARACTERS = 48
    private const val CARRY_CONTEXT_CHARACTERS = 96

    // Exact version/revision, live cancellation and output sink are independent bindings.
    @Suppress("LongParameterList")
    fun stream(
        port: PagedTranscriptPort,
        identity: AudioIdentity,
        versionId: String,
        revision: Long,
        cancelled: () -> Boolean,
        consume: (String) -> Unit,
    ) {
        var after: TranscriptPageCursor? = null
        var previousPart = -1
        var wrote = false
        while (true) {
            if (cancelled()) throw CancellationException()
            val page = value(port.page(identity, versionId, after, revision)) ?: return
            check(page.completeVersion)
            check(
                after == null ||
                    page.cursor.partIndex > after.partIndex ||
                    page.cursor.partIndex == after.partIndex &&
                        page.cursor.pageIndex > after.pageIndex
            )
            if (page.text.isNotEmpty()) {
                if (wrote && page.cursor.partIndex != previousPart) consume("\n")
                consume(page.text)
                wrote = true
                previousPart = page.cursor.partIndex
            }
            after = page.cursor
        }
    }

    // Cross-page matching retains bounded context and Unicode boundaries in one bounded scan.
    @Suppress("LongParameterList", "CyclomaticComplexMethod")
    fun preview(
        port: PagedTranscriptPort,
        identity: AudioIdentity,
        document: TranscriptDocument,
        query: String,
        fallback: String,
        cancelled: () -> Boolean,
    ): RecordingPreview? {
        require(query.length <= MAX_QUERY_CHARACTERS)
        val version = document.versions.single { it.id == document.activeId }
        val matcher = LiteralSubstring(query, cancelled)
        var after: TranscriptPageCursor? = null
        var carry = ""
        var title: String? = null
        var previousPart = -1
        while (true) {
            if (cancelled()) throw CancellationException()
            val page =
                value(port.page(identity, version.id, after, document.revision)) ?: return null
            val separator = if (after != null && previousPart != page.cursor.partIndex) "\n" else ""
            val text = carry + separator + page.text
            if (title == null)
                title =
                    text.lineSequence().firstOrNull { it.isNotBlank() }?.take(DISPLAY_CHARACTERS)
            val match = matcher.indexIn(text)
            if (match >= 0) {
                var start = (match - CONTEXT_CHARACTERS).coerceAtLeast(0)
                var end = minOf(text.length, start + DISPLAY_CHARACTERS)
                if (start > 0 && text[start].isLowSurrogate()) start--
                if (end < text.length && end > 0 && text[end - 1].isHighSurrogate()) end--
                return RecordingPreview(
                    title ?: fallback,
                    text.substring(start, end),
                    version.createdAt,
                    document.jobs.lastOrNull()?.status in setOf("QUEUED", "RUNNING"),
                )
            }
            carry = text.takeLast(query.length + CARRY_CONTEXT_CHARACTERS)
            if (carry.firstOrNull()?.isLowSurrogate() == true) carry = carry.drop(1)
            after = page.cursor
            previousPart = page.cursor.partIndex
        }
    }

    private fun <T> value(result: AudioResult<T>): T =
        when (result) {
            is AudioResult.Value -> result.value
            is AudioResult.Failed -> error("Protected transcript read failed")
        }
}
