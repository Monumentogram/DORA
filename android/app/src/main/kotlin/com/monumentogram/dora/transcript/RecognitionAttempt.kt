package com.monumentogram.dora.transcript

import java.util.concurrent.atomic.AtomicReference

/** Cancellation wins until the bounded durable-publication phase; inference never holds a lock. */
internal class RecognitionAttempt {
    private enum class Phase {
        QUEUED,
        RUNNING,
        CANCELLED,
        PUBLISHING,
    }

    private val phase = AtomicReference(Phase.QUEUED)

    fun start(): Boolean = phase.compareAndSet(Phase.QUEUED, Phase.RUNNING)

    fun cancelled(): Boolean = phase.get() == Phase.CANCELLED

    @Synchronized fun publish(): Boolean = phase.compareAndSet(Phase.RUNNING, Phase.PUBLISHING)

    @Synchronized
    fun cancel(persist: () -> Unit = {}): Boolean {
        while (true) {
            val current = phase.get()
            if (current == Phase.PUBLISHING) return false
            if (current == Phase.CANCELLED || phase.compareAndSet(current, Phase.CANCELLED)) {
                persist()
                return true
            }
        }
    }
}
