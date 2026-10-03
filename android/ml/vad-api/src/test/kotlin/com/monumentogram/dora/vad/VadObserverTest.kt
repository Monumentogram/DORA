package com.monumentogram.dora.vad

import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VadObserverTest {
    @Test
    fun rejectedWorkerDoesNotThrowOrRetainCanonicalCopy() {
        val rejected =
            VadObserver(
                SegmentationProfile.FROZEN,
                0,
                VadEngineFactory { Engine() },
                Executor { throw java.util.concurrent.RejectedExecutionException() },
                2,
            ) {}
        val pcm = ByteArray(1024) { 1 }
        rejected.offer(pcm, 0)
        assertEquals(VadFailure.RUNTIME_UNAVAILABLE, rejected.stats().failure)
        assertEquals(0, rejected.stats().queueSize)
        assertTrue(pcm.all { it == 1.toByte() })
        rejected.stop(512)
    }

    @Test
    fun queuedDeliveryCannotCrossProducerPauseFence() {
        val tasks = Tasks()
        val deliveries = mutableListOf<VadDelivery>()
        val observer =
            VadObserver(
                SegmentationProfile.FROZEN,
                0,
                VadEngineFactory { Engine() },
                tasks,
                40,
                deliveries::add,
            )
        repeat(10) { observer.offer(ByteArray(1024), it * 512L) }
        tasks.run()
        val opened = deliveries.first { it.event is VadOutput.Classified }
        assertTrue(observer.acceptDelivery(opened))
        observer.discontinuity(5120, VadFailure.PAUSED)
        assertFalse(observer.acceptDelivery(opened))
        tasks.run()
    }

    @Test
    fun coalescedPauseDoesNotEraseBackpressureDiagnostic() {
        repeat(3) { observer.offer(ByteArray(1024), it * 512L) }
        observer.discontinuity(1536, VadFailure.PAUSED)
        tasks.run()
        assertTrue(
            events.any { it is VadOutput.Uncertain && it.failure == VadFailure.BACKPRESSURE }
        )
    }

    private class Tasks : Executor {
        val queue = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            queue.add(command)
        }

        fun run() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }

    private class Engine : VadEngine {
        var calls = 0
        var resets = 0
        var closes = 0
        var fail = false
        var failReset = false

        override fun probability(samples: FloatArray): Float {
            calls++
            if (fail) error("private canary must not escape")
            return .8f
        }

        override fun reset() {
            resets++
            if (failReset) error("Injected synthetic failure")
        }

        override fun close() {
            closes++
        }
    }

    private val tasks = Tasks()
    private val engine = Engine()
    private val events = mutableListOf<VadOutput>()
    private var creations = 0
    private val observer =
        VadObserver(
            SegmentationProfile.FROZEN,
            0,
            VadEngineFactory {
                creations++
                engine
            },
            tasks,
            2,
            { events.add(it.event) },
        )

    @Test
    fun initializationAndInferenceNeverRunInOffer() {
        observer.offer(ByteArray(1024), 0)
        assertEquals(0, creations)
        assertEquals(0, engine.calls)
        tasks.run()
        assertEquals(1, creations)
        assertEquals(1, engine.calls)
    }

    @Test
    fun stallAndQueueFullAreBoundedAndBreakCoverage() {
        repeat(3) { observer.offer(ByteArray(1024), it * 512L) }
        assertEquals(2, observer.stats().queueHighWater)
        assertEquals(VadFailure.BACKPRESSURE, observer.stats().failure)
        tasks.run()
        assertTrue(
            events.any { it is VadOutput.Uncertain && it.failure == VadFailure.BACKPRESSURE }
        )
        assertEquals(0, engine.calls)
        observer.offer(ByteArray(1024), 1536)
        tasks.run()
        assertEquals(1, engine.calls)
    }

    @Test
    fun canonicalBorrowIsNotMutatedOrRetained() {
        val pcm = ByteArray(1024) { 12 }
        observer.offer(pcm, 0)
        assertTrue(pcm.all { it == 12.toByte() })
        pcm.fill(0)
        tasks.run()
        assertEquals(1, engine.calls)
    }

    @Test
    fun stopDrainsAndClosesWithoutBlockingCaller() {
        observer.offer(ByteArray(1024), 0)
        observer.stop(512)
        assertEquals(0, engine.closes)
        tasks.run()
        assertEquals(1, engine.calls)
        assertEquals(1, engine.closes)
        observer.stop(512)
        tasks.run()
        assertEquals(1, engine.closes)
    }

    @Test
    fun pauseDiscardsStaleQueuedWorkAndClearsRecurrentState() {
        observer.offer(ByteArray(1024), 0)
        observer.discontinuity(512, VadFailure.PAUSED)
        tasks.run()
        assertEquals(0, engine.calls)
        observer.discontinuity(512, VadFailure.RESUMED)
        observer.offer(ByteArray(1024), 512)
        tasks.run()
        assertEquals(1, engine.calls)
        assertTrue(engine.resets > 0)
    }

    @Test
    fun initializationFailureIsTypedAndNeverSilence() {
        val o =
            VadObserver(
                SegmentationProfile.FROZEN,
                0,
                VadEngineFactory { throw VadException(VadFailure.MODEL_INVALID) },
                tasks,
                2,
                { events.add(it.event) },
            )
        o.offer(ByteArray(1024), 0)
        tasks.run()
        assertEquals(VadFailure.MODEL_INVALID, o.stats().failure)
        assertTrue(events.none { it is VadOutput.Classified })
    }

    @Test
    fun inferenceFailureCannotEscapeIntoCanonicalCaller() {
        engine.fail = true
        observer.offer(ByteArray(1024), 0)
        tasks.run()
        assertEquals(VadFailure.INFERENCE_FAILED, observer.stats().failure)
        observer.offer(ByteArray(1024), 512)
        tasks.run()
        assertEquals(1, engine.calls)
    }

    @Test
    fun resetFailureDisablesSemanticInference() {
        observer.offer(ByteArray(1024), 0)
        tasks.run()
        engine.failReset = true
        observer.discontinuity(512, VadFailure.PAUSED)
        tasks.run()
        observer.offer(ByteArray(1024), 512)
        tasks.run()
        assertEquals(VadFailure.RESET_FAILED, observer.stats().failure)
        assertEquals(1, engine.calls)
    }

    @Test
    fun partialWindowAtStopIsUnknownNotSilence() {
        observer.offer(ByteArray(30), 0)
        observer.stop(15)
        tasks.run()
        assertEquals(0, engine.calls)
        assertTrue(
            events.any { it is VadOutput.Uncertain && it.failure == VadFailure.COVERAGE_GAP }
        )
    }

    @Test
    fun offerAfterStopCannotResurrectRuntime() {
        observer.stop(0)
        tasks.run()
        observer.offer(ByteArray(1024), 0)
        tasks.run()
        assertEquals(0, engine.calls)
        assertEquals(1, engine.closes)
    }
}
