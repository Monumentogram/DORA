package com.monumentogram.dora.audio.persistence.auth

internal class AppLockedException : IllegalStateException("APP_LOCKED")

/** Process-local authority. No instance, attempt or generation is persisted. */
@Suppress("TooManyFunctions") // Keep every authority transition under this single monitor.
internal class AppLockSession(
    private val elapsedMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
    private val deviceReady: () -> Boolean,
) {
    private val monitor = Any()
    private var foreground = false
    private var securityEpoch = 0L
    private var generation = 0L
    private var unlockedGeneration: Long? = null
    private var pending: Attempt? = null

    internal class Attempt internal constructor(internal val securityEpoch: Long) {
        internal var resultReceivedAt: Long? = null
    }

    fun resume() = synchronized(monitor) { foreground = true }

    fun pause() =
        synchronized(monitor) {
            foreground = false
            revoke()
        }

    fun lock() =
        synchronized(monitor) {
            securityEpoch++
            pending = null
            revoke()
        }

    fun begin(): Attempt =
        synchronized(monitor) {
            revoke()
            Attempt(securityEpoch).also { pending = it }
        }

    fun cancel(attempt: Attempt) =
        synchronized(monitor) {
            if (pending === attempt) pending = null
        }

    fun recordResult(attempt: Attempt): Boolean =
        synchronized(monitor) {
            if (pending !== attempt || attempt.resultReceivedAt != null) return false
            attempt.resultReceivedAt = elapsedMillis()
            true
        }

    fun complete(attempt: Attempt, recentProof: () -> Boolean): Boolean =
        synchronized(monitor) {
            if (!foreground || pending !== attempt || attempt.securityEpoch != securityEpoch)
                return false
            pending = null // Every returned result gets exactly one proof attempt.
            val beforeProof = generation
            if (!resultIsFresh(attempt)) return false
            if (!deviceReady()) {
                lock()
                return false
            }
            val verified =
                try {
                    recentProof()
                } catch (_: Exception) {
                    false
                }
            if (!verified || !foreground || generation != beforeProof) return false
            if (securityEpoch != attempt.securityEpoch || !deviceReady() || !resultIsFresh(attempt))
                return false
            generation++
            unlockedGeneration = generation
            true
        }

    private fun resultIsFresh(attempt: Attempt): Boolean {
        val receivedAt = attempt.resultReceivedAt ?: return false
        return elapsedMillis() - receivedAt in 0 until RESULT_DEADLINE_MILLIS
    }

    fun capture(): Authorization =
        synchronized(monitor) {
            val current = unlockedGeneration ?: throw AppLockedException()
            requireGeneration(current)
            Authorization(current)
        }

    val isUnlocked: Boolean
        get() =
            synchronized(monitor) {
                try {
                    capture()
                    true
                } catch (_: AppLockedException) {
                    false
                }
            }

    private fun revoke() {
        generation++
        unlockedGeneration = null
    }

    private fun requireGeneration(expected: Long) {
        if (!deviceReady()) {
            lock()
            throw AppLockedException()
        }
        if (!foreground || unlockedGeneration != expected) throw AppLockedException()
    }

    internal inner class Authorization internal constructor(private val issuedGeneration: Long) {
        fun requireActive() = synchronized(monitor) { requireGeneration(issuedGeneration) }

        /** The callback borrows bounded plaintext and must finish before revocation returns. */
        fun withPlaintextDelivery(block: () -> Unit) =
            synchronized(monitor) {
                requireGeneration(issuedGeneration)
                block()
            }
    }

    companion object {
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private const val RESULT_DEADLINE_MILLIS = 1_000L
    }
}
