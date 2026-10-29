package com.monumentogram.dora.audio.recording

import java.util.concurrent.CancellationException

/** Linear-time, Unicode simple-case matching. Offsets address the original UTF-16 text. */
internal class LiteralSubstring(private val query: String, private val cancelled: () -> Boolean) {
    private var work = 0
    private val pattern = IntArray(query.codePointCount(0, query.length))
    private val prefix = IntArray(pattern.size)

    init {
        var offset = 0
        for (index in pattern.indices) {
            checkpoint()
            val point = query.codePointAt(offset)
            pattern[index] = fold(point)
            offset += Character.charCount(point)
        }
        var matched = 0
        for (index in 1 until pattern.size) {
            checkpoint()
            while (matched > 0 && pattern[index] != pattern[matched]) {
                checkpoint()
                matched = prefix[matched - 1]
            }
            if (pattern[index] == pattern[matched]) matched++
            prefix[index] = matched
        }
    }

    @Suppress("ReturnCount") // Empty pattern, found match, exhausted input.
    fun indexIn(text: String): Int {
        if (pattern.isEmpty()) return 0
        var matched = 0
        var offset = 0
        while (offset < text.length) {
            checkpoint()
            val point = text.codePointAt(offset)
            val folded = fold(point)
            while (matched > 0 && folded != pattern[matched]) {
                checkpoint()
                matched = prefix[matched - 1]
            }
            if (folded == pattern[matched]) matched++
            offset += Character.charCount(point)
            if (matched == pattern.size) return offset - query.length
        }
        return -1
    }

    private fun fold(point: Int) = Character.toLowerCase(Character.toUpperCase(point))

    private fun checkpoint() {
        if (work++ % CHECK_INTERVAL == 0 && cancelled()) throw CancellationException()
    }

    private companion object {
        const val CHECK_INTERVAL = 1024
    }
}
