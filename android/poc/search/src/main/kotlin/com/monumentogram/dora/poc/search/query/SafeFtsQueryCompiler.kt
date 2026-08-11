@file:Suppress("ReturnCount")

package com.monumentogram.dora.poc.search.query

import java.util.Locale

object SafeFtsQueryCompiler {
    const val MAX_RAW_LENGTH: Int = 512
    const val MAX_TOKENS: Int = 12
    const val MAX_TOKEN_LENGTH: Int = 64
    const val MIN_PREFIX_LENGTH: Int = 2

    fun compile(rawQuery: String, mode: SearchMode, hasFilters: Boolean): CompiledUserQuery {
        if (rawQuery.length > MAX_RAW_LENGTH) {
            return rejected("QUERY_TOO_LONG")
        }

        val tokens = tokenize(rawQuery.trim())
        if (tokens.isEmpty()) {
            return CompiledUserQuery(
                status = if (hasFilters) QueryStatus.FILTER_ONLY else QueryStatus.EMPTY,
                tokens = emptyList(),
                matchExpression = null,
                rejectionCode = null,
            )
        }
        if (tokens.size > MAX_TOKENS) {
            return rejected("TOO_MANY_TOKENS")
        }
        if (tokens.any { it.length > MAX_TOKEN_LENGTH }) {
            return rejected("TOKEN_TOO_LONG")
        }
        if (mode == SearchMode.PREFIX && tokens.any { it.length < MIN_PREFIX_LENGTH }) {
            return rejected("PREFIX_TOO_SHORT")
        }

        val expression =
            when (mode) {
                SearchMode.PHRASE ->
                    tokens.joinToString(separator = " ", prefix = "\"", postfix = "\"")
                SearchMode.PREFIX -> tokens.joinToString(" AND ") { "\"$it\"*" }
                SearchMode.EXACT -> tokens.joinToString(" AND ") { "\"$it\"" }
            }
        return CompiledUserQuery(QueryStatus.READY, tokens, expression, null)
    }

    fun tokenize(value: String): List<String> {
        val normalized = value.lowercase(Locale.ROOT)
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < normalized.length) {
            val codePoint = normalized.codePointAt(index)
            if (isTokenCodePoint(codePoint)) {
                current.appendCodePoint(codePoint)
            } else if (current.isNotEmpty()) {
                tokens += current.toString()
                current.clear()
            }
            index += Character.charCount(codePoint)
        }
        if (current.isNotEmpty()) {
            tokens += current.toString()
        }
        return tokens
    }

    private fun isTokenCodePoint(codePoint: Int): Boolean {
        if (Character.isLetterOrDigit(codePoint)) {
            return true
        }
        return when (Character.getType(codePoint)) {
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt() -> true
            else -> false
        }
    }

    private fun rejected(code: String): CompiledUserQuery =
        CompiledUserQuery(
            status = QueryStatus.REJECTED,
            tokens = emptyList(),
            matchExpression = null,
            rejectionCode = code,
        )
}

/**
 * Renders the frozen, safely tokenized compiler result for Android's portable FTS4 syntax.
 *
 * The frozen normalization contract deliberately records every literal token in quotes and uses an
 * explicit AND. Android SQLite may expose the FTS4 standard query syntax, where explicit AND is
 * parsed as another search term. FTS4 also requires the prefix marker to be part of the quoted
 * phrase instead of following its closing quote. Whitespace is implicit AND in both standard and
 * enhanced FTS4 syntax, so this renderer preserves the frozen token semantics without accepting any
 * raw user syntax.
 */
object Fts4MatchExpressionRenderer {
    fun render(compiled: CompiledUserQuery, mode: SearchMode): String? {
        if (compiled.status != QueryStatus.READY) return null
        check(compiled.tokens.isNotEmpty())
        check(compiled.matchExpression != null)

        return when (mode) {
            SearchMode.EXACT -> compiled.tokens.joinToString(" ") { "\"$it\"" }
            SearchMode.PHRASE ->
                compiled.tokens.joinToString(separator = " ", prefix = "\"", postfix = "\"")
            SearchMode.PREFIX -> compiled.tokens.joinToString(" ") { "\"$it*\"" }
        }
    }
}
