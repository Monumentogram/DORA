package com.monumentogram.dora.poc.recovery.candidate

import java.security.MessageDigest

/** Canonical serializer for the SQLite compile-options preflight record. */
object SqliteCompileOptionsCanonicalizer {
    fun canonicalize(options: List<String>): SqliteCompileOptionsEvidence {
        val sortedOptions = options.sorted()
        val canonicalUtf8 = sortedOptions.joinToString(separator = "\n", postfix = "\n")
        val sha256 =
            MessageDigest.getInstance("SHA-256")
                .digest(canonicalUtf8.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        return SqliteCompileOptionsEvidence(
            sortedOptions = sortedOptions,
            canonicalUtf8 = canonicalUtf8,
            sha256 = sha256,
        )
    }
}

data class SqliteCompileOptionsEvidence(
    val sortedOptions: List<String>,
    val canonicalUtf8: String,
    val sha256: String,
) {
    val count: Int
        get() = sortedOptions.size
}
