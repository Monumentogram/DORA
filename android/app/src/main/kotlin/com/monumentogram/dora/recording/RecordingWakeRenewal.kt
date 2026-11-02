package com.monumentogram.dora.recording

import com.monumentogram.dora.audio.recording.RecordingDurability
import com.monumentogram.dora.audio.recording.RecordingPhase

/** Main-thread-owned, bounded wake lease for the foreground recording service. */
internal class RecordingWakeRenewal(private val driver: Driver) : Runnable {
    interface Driver {
        fun nowMillis(): Long

        fun isHeld(): Boolean

        fun acquire(timeoutMillis: Long)

        fun release()

        fun schedule(callback: Runnable, delayMillis: Long)

        fun cancel(callback: Runnable)
    }

    private var renewedAt = 0L
    private var required = false
    private var closed = false
    private var scheduled = false

    fun update(foreground: Boolean, phase: RecordingPhase, durability: RecordingDurability) {
        if (closed) return
        required =
            foreground &&
                (phase == RecordingPhase.PREPARING ||
                    phase == RecordingPhase.RECORDING ||
                    phase == RecordingPhase.FINALIZING ||
                    durability == RecordingDurability.PENDING)
        if (required) {
            val now = driver.nowMillis()
            if (!driver.isHeld() || now - renewedAt >= RENEW_MILLIS) {
                driver.acquire(TIMEOUT_MILLIS)
                renewedAt = now
            }
            schedule()
        } else {
            driver.cancel(this)
            scheduled = false
            if (driver.isHeld()) driver.release()
        }
    }

    override fun run() {
        scheduled = false
        if (closed || !required) return
        // Finalization may publish no new state for many minutes. Its lease must not
        // depend on state emissions or the recording-only presentation ticker.
        driver.acquire(TIMEOUT_MILLIS)
        renewedAt = driver.nowMillis()
        schedule()
    }

    private fun schedule() {
        if (scheduled) return
        scheduled = true
        driver.schedule(this, RENEW_MILLIS)
    }

    fun close() {
        closed = true
        required = false
        driver.cancel(this)
        scheduled = false
        if (driver.isHeld()) driver.release()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 60_000L
        const val RENEW_MILLIS = 30_000L
    }
}
