package com.monumentogram.dora.recording

import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.vad.VadFailure
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Virtual control owner and deadline; no Android main thread, microphone or JNI. */
class RecordingPreparationTest {
    @Test
    fun pendingInitializationCannotStartMicrophoneOrPublishRecording() {
        val driver = Driver()
        val preparation = RecordingPreparation(driver)
        val owner = Any()
        val initialized = CompletableFuture<VadFailure?>()
        var starts = 0
        preparation.begin(owner, initialized, { true }, { starts++ }, {})
        assertTrue(preparation.pending(owner))
        assertEquals(
            RecordingPhase.PREPARING,
            preparation.visiblePhase(owner, RecordingPhase.RECORDING, false),
        )
        driver.advanceTo(9_999)
        assertEquals(0, starts)

        initialized.complete(null)
        assertEquals(0, starts)
        driver.drain()

        assertEquals(1, starts)
        assertFalse(preparation.pending(owner))
        assertEquals(
            RecordingPhase.RECORDING,
            preparation.visiblePhase(owner, RecordingPhase.RECORDING, true),
        )
        assertEquals(0, driver.deadlines.size)
    }

    @Test
    fun timeoutIsFiniteTypedAndLateReadinessCannotStartAgain() {
        val driver = Driver()
        val preparation = RecordingPreparation(driver)
        val initialized = CompletableFuture<VadFailure?>()
        val outcomes = mutableListOf<VadFailure?>()
        preparation.begin(Any(), initialized, { true }, { outcomes += it }, {})
        driver.advanceTo(10_000)
        assertEquals(listOf(VadFailure.INITIALIZATION_FAILED), outcomes)
        initialized.complete(null)
        driver.drain()
        assertEquals(1, outcomes.size)
        assertEquals(0, driver.deadlines.size)
    }

    @Test
    fun readinessAndTimeoutQueuedTogetherHaveExactlyOneControlOwnerWinner() {
        val driver = Driver()
        val preparation = RecordingPreparation(driver)
        val initialized = CompletableFuture<VadFailure?>()
        val outcomes = mutableListOf<VadFailure?>()
        preparation.begin(Any(), initialized, { true }, { outcomes += it }, {})
        initialized.complete(null) // Ready callback is queued but has not consumed the owner yet.
        driver.advanceTo(10_000)
        assertEquals(listOf(VadFailure.INITIALIZATION_FAILED), outcomes)
        assertEquals(0, driver.deadlines.size)
    }

    @Test
    fun cancelledPreparationRetiresOwnerAndSuppressesLateStart() {
        val driver = Driver()
        val preparation = RecordingPreparation(driver)
        val initialized = CompletableFuture<VadFailure?>()
        var starts = 0
        var retired = 0
        preparation.begin(Any(), initialized, { true }, { starts++ }, { retired++ })
        preparation.cancel()
        initialized.complete(null)
        driver.drain()
        driver.advanceTo(20_000)
        assertEquals(0, starts)
        assertEquals(1, retired)
    }

    @Test
    fun lostAuthorityOrServiceGenerationCannotBeRevivedByReadiness() {
        val driver = Driver()
        val preparation = RecordingPreparation(driver)
        val initialized = CompletableFuture<VadFailure?>()
        var current = true
        var starts = 0
        var retired = 0
        preparation.begin(Any(), initialized, { current }, { starts++ }, { retired++ })
        current = false
        initialized.complete(null)
        driver.drain()
        assertEquals(0, starts)
        assertEquals(1, retired)
    }

    @Test
    fun newSessionOwnsItsPreparationAndTypedFailureStillCompletes() {
        val driver = Driver()
        val preparation = RecordingPreparation(driver)
        val old = CompletableFuture<VadFailure?>()
        val fresh = CompletableFuture<VadFailure?>()
        val owner = Any()
        var oldRetired = 0
        val outcomes = mutableListOf<VadFailure?>()
        preparation.begin(Any(), old, { true }, { error("Stale start") }, { oldRetired++ })
        preparation.begin(owner, fresh, { true }, { outcomes += it }, {})
        old.complete(null)
        fresh.complete(VadFailure.RUNTIME_UNAVAILABLE)
        driver.drain()
        assertEquals(1, oldRetired)
        assertEquals(listOf(VadFailure.RUNTIME_UNAVAILABLE), outcomes)
        assertFalse(preparation.pending(owner))
        assertEquals(
            RecordingPhase.PAUSED,
            preparation.visiblePhase(owner, RecordingPhase.PAUSED, false),
        )
    }

    private class Driver : RecordingPreparation.Driver {
        private var now = 0L
        private val controls = ArrayDeque<() -> Unit>()
        val deadlines = linkedMapOf<Runnable, Long>()

        override fun post(action: () -> Unit) {
            controls.add(action)
        }

        override fun schedule(task: Runnable, delayMillis: Long): () -> Unit {
            assertEquals(10_000L, delayMillis)
            deadlines[task] = now + delayMillis
            return {
                deadlines.remove(task)
                Unit
            }
        }

        fun drain() {
            while (controls.isNotEmpty()) controls.removeFirst().invoke()
        }

        fun advanceTo(target: Long) {
            var next = deadlines.minByOrNull { it.value }
            while (next != null && next.value <= target) {
                now = next.value
                deadlines.remove(next.key)
                next.key.run()
                drain()
                next = deadlines.minByOrNull { it.value }
            }
            now = target
            drain()
        }
    }
}
