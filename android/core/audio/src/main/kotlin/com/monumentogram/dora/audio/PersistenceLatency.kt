package com.monumentogram.dora.audio

/**
 * Optional thread-scoped diagnostics: fixed stage names and aggregate nanoseconds, never values.
 */
internal object PersistenceLatency {
    private val current = ThreadLocal<MutableMap<String, Long>>()

    fun <T> collect(report: (Map<String, Long>) -> Unit, block: () -> T): T {
        check(current.get() == null)
        val totals = linkedMapOf<String, Long>()
        current.set(totals)
        try {
            return block()
        } finally {
            current.remove()
            report(totals.toMap())
        }
    }

    fun <T> measure(stage: String, block: () -> T): T {
        val totals = current.get() ?: return block()
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            totals[stage] = (totals[stage] ?: 0L) + System.nanoTime() - start
            totals[stage + "_count"] = (totals[stage + "_count"] ?: 0L) + 1L
        }
    }
}
