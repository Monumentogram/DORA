package com.monumentogram.dora.audio.persistence.keys

import com.google.crypto.tink.Aead
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
    ) {
        var primitive: ScopedAead? = null
        var reuseAllowed = true
        var closed = false
    }

    private val active = ThreadLocal<Attempt>()

    fun <T> observe(run: RunId, operation: RunKeyOperation, block: () -> T): RunKeyAttempt<T> {
        check(active.get() == null) { "NESTED_KEY_ATTEMPT" }
        val attempt = Attempt(run, operation)
        active.set(attempt)
        return try {
            RunKeyAttempt(block(), attempt.failure)
        } finally {
            expire(attempt)
            attempt.closed = true
            active.remove()
        }
    }

    /** Reuse only a successfully validated, nonexportable primitive in this run's read attempt. */
    @Suppress("TooGenericExceptionCaught") // Every validation failure must retire borrowed keys.
    fun validatedAead(run: RunId, requireAvailable: () -> Unit, open: () -> Aead): Aead {
        val attempt = active.get()
        if (attempt?.run != run || attempt.operation != RunKeyOperation.RECONCILIATION)
            return open()
        requireUsable(attempt)
        return try {
            // Recovery may authenticate an older manifest or dependent quarantine members after
            // a failure. Preserve that path with fresh validation, never reuse the failed scope.
            if (!attempt.reuseAllowed) expire(attempt)
            attempt.primitive?.also { requireAvailable() }
                ?: ScopedAead(open(), { requireUsable(attempt) }, requireAvailable) {
                        expire(attempt)
                    }
                    .also {
                        requireUsable(attempt)
                        attempt.primitive = it
                    }
        } catch (error: Exception) {
            expire(attempt)
            throw error
        }
    }

    /** Deletion never leaves a primitive reusable, including a previously borrowed wrapper. */
    fun invalidate(run: RunId) {
        active.get()?.takeIf { it.run == run }?.let(::expire)
    }

    fun record(run: RunId, failure: KeyFailure) {
        val attempt = active.get() ?: return
        // The first original failure survives subsequent sanitizing wrappers.
        if (attempt.run == run) {
            if (attempt.failure == null) attempt.failure = failure
            expire(attempt)
        }
    }

    fun missing(run: RunId) {
        if (active.get()?.operation == RunKeyOperation.RECONCILIATION)
            record(run, KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED)
    }

    private fun requireUsable(attempt: Attempt) {
        if (active.get() !== attempt || attempt.closed)
            throw KeyBoundaryException(attempt.failure ?: KeyFailure.TEMPORARILY_UNAVAILABLE)
    }

    private fun expire(attempt: Attempt) {
        attempt.reuseAllowed = false
        attempt.primitive?.close()
        attempt.primitive = null
    }

    private class ScopedAead(
        private var delegate: Aead?,
        private val requireUsable: () -> Unit,
        private val requireAvailable: () -> Unit,
        private val invalidate: () -> Unit,
    ) : Aead {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray) = use {
            it.encrypt(plaintext, associatedData)
        }

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray) = use {
            it.decrypt(ciphertext, associatedData)
        }

        fun close() {
            delegate = null
        }

        @Suppress("TooGenericExceptionCaught") // Every provider failure must retire this wrapper.
        private fun use(operation: (Aead) -> ByteArray): ByteArray {
            // Reject an escaped wrapper before reading its thread-confined primitive.
            requireUsable()
            val current = delegate ?: throw KeyBoundaryException(KeyFailure.TEMPORARILY_UNAVAILABLE)
            return try {
                requireAvailable()
                operation(current)
            } catch (error: Exception) {
                invalidate()
                throw error
            }
        }
    }
}
