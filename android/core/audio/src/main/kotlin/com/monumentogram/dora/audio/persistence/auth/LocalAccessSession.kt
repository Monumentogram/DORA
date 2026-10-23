package com.monumentogram.dora.audio.persistence.auth

/** Process-local foreground authority; never represents human credential authentication. */
internal class LocalAccessSession(private val deviceReady: () -> Boolean = { true }) {
    private val monitor = Any()
    private var foreground = false
    private var generation = 0L

    fun resume() = synchronized(monitor) { foreground = true }

    fun pause() =
        synchronized(monitor) {
            foreground = false
            generation++
        }

    fun revoke() = pause()

    val isForeground: Boolean
        get() =
            synchronized(monitor) {
                try {
                    requireGeneration(generation)
                    true
                } catch (_: LocalAccessDeniedException) {
                    false
                }
            }

    /** Called for an explicit local action, only while the owning Activity is resumed. */
    fun capture(): Authorization =
        synchronized(monitor) {
            requireGeneration(generation)
            Authorization(generation)
        }

    private fun requireGeneration(expected: Long) {
        val ready =
            try {
                deviceReady()
            } catch (_: Exception) {
                false
            }
        if (!ready) {
            foreground = false
            generation++
        }
        if (!foreground || generation != expected) throw LocalAccessDeniedException()
    }

    internal inner class Authorization internal constructor(private val issuedGeneration: Long) {
        fun requireActive() = synchronized(monitor) { requireGeneration(issuedGeneration) }

        /** Revocation waits for the bounded borrow; callbacks must never wait on the UI thread. */
        fun withPlaintextDelivery(block: () -> Unit) =
            synchronized(monitor) {
                requireGeneration(issuedGeneration)
                block()
            }
    }
}

internal class LocalAccessDeniedException : IllegalStateException("LOCAL_ACCESS_REVOKED")
