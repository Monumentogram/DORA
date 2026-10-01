package com.monumentogram.dora.audio.persistence.keys

import com.monumentogram.dora.poc.recovery.contract.RunId

internal enum class RunKeyOperation {
    BOOTSTRAP,
    PUBLICATION,
    RECONCILIATION,
}

internal data class RunKeyAttempt<T>(val value: T, val failure: KeyFailure?)

/** One synchronous controller attempt, isolated by backend instance and calling thread. */
internal class RunKeyFailureScopes {
    private class Attempt(
        val run: RunId,
        val operation: RunKeyOperation,
        var failure: KeyFailure? = null,
    )

    private val active = ThreadLocal<Attempt>()

    fun <T> observe(run: RunId, operation: RunKeyOperation, block: () -> T): RunKeyAttempt<T> {
        check(active.get() == null) { "NESTED_KEY_ATTEMPT" }
        val attempt = Attempt(run, operation)
        active.set(attempt)
        return try {
            RunKeyAttempt(block(), attempt.failure)
        } finally {
            active.remove()
        }
    }

    fun record(run: RunId, failure: KeyFailure) {
        val attempt = active.get() ?: return
        // The first original failure survives subsequent sanitizing wrappers.
        if (attempt.run == run && attempt.failure == null) attempt.failure = failure
    }

    fun missing(run: RunId) {
        if (active.get()?.operation == RunKeyOperation.RECONCILIATION)
            record(run, KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED)
    }
}
