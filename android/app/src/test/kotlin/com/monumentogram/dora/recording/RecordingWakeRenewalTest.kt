package com.monumentogram.dora.recording

import com.monumentogram.dora.audio.recording.RecordingDurability
import com.monumentogram.dora.audio.recording.RecordingPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingWakeRenewalTest {
    @Test
    fun finalizationStaysAwakeWithoutFurtherStateEmissions() {
        val driver = ClockDriver()
        val renewal = RecordingWakeRenewal(driver)
        renewal.update(true, RecordingPhase.FINALIZING, RecordingDurability.PENDING)

        driver.advanceTo(900_000L)

        assertTrue("Finalization outlives the original one-minute wake lease", driver.isHeld())
        assertEquals(31, driver.acquisitions)
        assertEquals(0, driver.expirations)
    }

    @Test
    fun settledPauseCancelsRenewalButPendingPauseRetainsIt() {
        val driver = ClockDriver()
        val renewal = RecordingWakeRenewal(driver)
        renewal.update(true, RecordingPhase.PAUSED, RecordingDurability.PENDING)
        driver.advanceTo(90_000L)
        assertTrue(driver.isHeld())

        renewal.update(true, RecordingPhase.PAUSED, RecordingDurability.CAUGHT_UP)
        driver.advanceTo(180_000L)

        assertFalse(driver.isHeld())
        assertEquals(4, driver.acquisitions)
        assertEquals(0, driver.pendingCallbacks())
    }

    @Test
    fun foregroundLossAndDestroyCannotBeUndoneByLateTimer() {
        val driver = ClockDriver()
        val renewal = RecordingWakeRenewal(driver)
        renewal.update(false, RecordingPhase.FINALIZING, RecordingDurability.PENDING)
        assertEquals(0, driver.acquisitions)
        renewal.update(true, RecordingPhase.RECORDING, RecordingDurability.PENDING)
        renewal.update(false, RecordingPhase.FINALIZING, RecordingDurability.PENDING)
        renewal.run()
        assertFalse(driver.isHeld())
        assertEquals(0, driver.pendingCallbacks())

        renewal.update(true, RecordingPhase.FINALIZING, RecordingDurability.PENDING)
        renewal.close()
        renewal.run()
        renewal.update(true, RecordingPhase.FINALIZING, RecordingDurability.PENDING)
        driver.advanceTo(120_000L)

        assertFalse(driver.isHeld())
        assertEquals(2, driver.acquisitions)
        assertEquals(0, driver.pendingCallbacks())
    }

    @Test
    fun terminalStateReleasesImmediatelyAndFrequentUpdatesDoNotMultiplyTimers() {
        val driver = ClockDriver()
        val renewal = RecordingWakeRenewal(driver)
        repeat(100) {
            renewal.update(true, RecordingPhase.FINALIZING, RecordingDurability.PENDING)
        }
        assertEquals(1, driver.acquisitions)
        assertEquals(1, driver.pendingCallbacks())
        driver.advanceTo(30_000L)
        renewal.update(true, RecordingPhase.SAVED, RecordingDurability.CAUGHT_UP)
        driver.advanceTo(120_000L)

        assertFalse(driver.isHeld())
        assertEquals(2, driver.acquisitions)
        assertEquals(0, driver.pendingCallbacks())
    }

    private class ClockDriver : RecordingWakeRenewal.Driver {
        private var now = 0L
        private var expiresAt: Long? = null
        private val callbacks = mutableMapOf<Runnable, Long>()
        var acquisitions = 0
            private set

        var expirations = 0
            private set

        override fun nowMillis() = now

        override fun isHeld() = expiresAt?.let { now < it } == true

        override fun acquire(timeoutMillis: Long) {
            assertEquals("Every individual lease must remain bounded", 60_000L, timeoutMillis)
            acquisitions++
            expiresAt = now + timeoutMillis
        }

        override fun release() {
            expiresAt = null
        }

        override fun schedule(callback: Runnable, delayMillis: Long) {
            check(callback !in callbacks) { "Duplicate renewal timer" }
            callbacks[callback] = now + delayMillis
        }

        override fun cancel(callback: Runnable) {
            callbacks.remove(callback)
        }

        fun pendingCallbacks() = callbacks.size

        fun advanceTo(target: Long) {
            var next = callbacks.minByOrNull { it.value }
            while (next != null && next.value <= target) {
                advanceClock(next.value)
                callbacks.remove(next.key)
                next.key.run()
                next = callbacks.minByOrNull { it.value }
            }
            advanceClock(target)
        }

        private fun advanceClock(target: Long) {
            if (expiresAt?.let { it <= target } == true) {
                expirations++
                expiresAt = null
            }
            now = target
        }
    }
}
