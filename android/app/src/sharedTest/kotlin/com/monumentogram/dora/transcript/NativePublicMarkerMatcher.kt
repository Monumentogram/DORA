package com.monumentogram.dora.transcript

import java.util.Locale

/**
 * Scalar evidence only. Approximation permits one insertion/deletion/substitution per whole pair.
 */
internal data class NativeMarkerCoverage(
    val exactPresent: Int,
    val boundedPresent: Int,
    val exactOrdered: Int,
    val boundedOrdered: Int,
)

/** Streaming two-word matcher; retains at most two bounded words and one flag per public marker. */
internal class NativePublicMarkerMatcher(expected: List<String>) {
    private val markers =
        expected.map(::normalize).also { phrases ->
            require(phrases.isNotEmpty())
            require(
                phrases.all {
                    it.split(' ').let { words ->
                        words.size == 2 && words.all { word -> word.length in 1..MAX_WORD_LENGTH }
                    }
                }
            )
        }
    private val exact = BooleanArray(markers.size)
    private val bounded = BooleanArray(markers.size)
    private val word = StringBuilder()
    private var overflow = false
    private var previous = ""
    private var exactNext = 0
    private var boundedNext = 0
    private var tokenIndex = 0
    private var exactEnd = -1
    private var boundedEnd = -1
    private var finished = false

    fun consume(text: String) {
        check(!finished)
        for (character in text.lowercase(Locale.ROOT).replace('ё', 'е')) {
            if (character.isLetterOrDigit()) {
                if (word.length < MAX_WORD_LENGTH) word.append(character) else overflow = true
            } else flushWord()
        }
    }

    fun finish(): NativeMarkerCoverage {
        if (!finished) flushWord()
        finished = true
        return NativeMarkerCoverage(
            exact.count { it },
            bounded.count { it },
            exactNext,
            boundedNext,
        )
    }

    private fun flushWord() {
        if (word.isEmpty() && !overflow) return
        val current = if (overflow) "" else word.toString()
        if (previous.isNotEmpty() && current.isNotEmpty()) match("$previous $current")
        previous = current
        word.setLength(0)
        overflow = false
        tokenIndex++
    }

    private fun match(candidate: String) {
        for (index in markers.indices) {
            val isExact = candidate == markers[index]
            val isBounded = isExact || oneEdit(candidate, markers[index])
            exact[index] = exact[index] || isExact
            bounded[index] = bounded[index] || isBounded
            if (index == exactNext && tokenIndex - 1 > exactEnd && isExact) {
                exactNext++
                exactEnd = tokenIndex
            }
            if (index == boundedNext && tokenIndex - 1 > boundedEnd && isBounded) {
                boundedNext++
                boundedEnd = tokenIndex
            }
        }
    }

    companion object {
        private const val MAX_WORD_LENGTH = 64

        private fun normalize(text: String) =
            text
                .lowercase(Locale.ROOT)
                .replace('ё', 'е')
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                .trim()

        @Suppress(
            "ReturnCount"
        ) // Reject impossible length or second edit immediately; keep the one-edit bound explicit.
        private fun oneEdit(left: String, right: String): Boolean {
            if (kotlin.math.abs(left.length - right.length) > 1) return false
            var a = 0
            var b = 0
            var edits = 0
            while (a < left.length && b < right.length) {
                if (left[a] == right[b]) {
                    a++
                    b++
                    continue
                }
                if (++edits > 1) return false
                if (left.length >= right.length) a++
                if (right.length >= left.length) b++
            }
            return edits + (left.length - a) + (right.length - b) <= 1
        }

        fun analyze(text: String, expected: List<String>): NativeMarkerCoverage =
            NativePublicMarkerMatcher(expected).also { it.consume(text) }.finish()
    }
}
