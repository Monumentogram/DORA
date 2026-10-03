package com.monumentogram.dora.vad

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class VadStats(
    val queueSize: Int,
    val queueHighWater: Int,
    val inferenceCount: Long,
    val missedDeadlines: Long,
    val minNanos: Long,
    val p50Nanos: Long,
    val p95Nanos: Long,
    val maxNanos: Long,
    val failure: VadFailure?,
    val closed: Boolean,
    val positiveWindows: Long,
    val negativeWindows: Long,
    val lastPositiveEnd: Long,
    val maximumNegativeRunFrames: Long,
)

/** Post to the producer/control owner, then check acceptDelivery before applying metadata. */
data class VadDelivery(val generation: Long, val event: VadOutput)

/** Classification only. The serialized recording owner owns all semantic transitions. */
sealed interface VadOutput {
    data class Classified(val observation: VadObservation) : VadOutput

    data class Uncertain(val atFrame: Long, val failure: VadFailure) : VadOutput
}

/** One producer (recording controls), one dedicated worker. No canonical ownership or I/O. */
@Suppress("TooManyFunctions") // One owner for queue, generation fences and native retirement.
class VadObserver(
    private val profile: SegmentationProfile,
    firstFrame: Long,
    private val factory: VadEngineFactory,
    private val worker: Executor,
    capacity: Int = 40,
    private val emit: (VadDelivery) -> Unit,
) {
    private data class Block(val bytes: ByteArray, val first: Long, val generation: Long)

    private data class Fence(val frame: Long, val generation: Long, val reason: VadFailure)

    init {
        require(capacity in 1..MAX_QUEUE_CAPACITY && firstFrame >= 0)
    }

    private val queue = ArrayBlockingQueue<Block>(capacity)
    private val scheduled = AtomicBoolean(false)
    private val generation = AtomicLong(1)
    private val fence = AtomicReference<Fence?>(null)
    private val stopping = AtomicReference<Long?>(null)
    private val highWater = AtomicInteger(0)
    private val failure = AtomicReference<VadFailure?>(null)
    private var producerEnd = firstFrame // Producer-owned; canonical frame position, not a timer.
    private var workerGeneration = 1L
    private var initialized = false
    private var engine: VadEngine? = null
    private var observedEnd = firstFrame
    private var unavailableReported = false
    @Volatile private var closed = false
    private val durations = LongArray(TIMING_SAMPLES)
    private var calls = 0L
    private var deadlines = 0L
    private var minNanos = Long.MAX_VALUE
    private var maxNanos = 0L
    private var positiveWindows = 0L
    private var negativeWindows = 0L
    private var lastPositiveEnd = -1L
    private var negativeRun = 0L
    private var maximumNegativeRunFrames = 0L
    private val adapter =
        PcmWindowAdapter(profile.windowFrames) { range, samples -> infer(range, samples) }

    /** Bounded copy of already accepted PCM. Returns immediately if the observer cannot keep up. */
    @Suppress(
        "ComplexCondition"
    ) // Reject incomplete, oversized or discontinuous PCM before copying.
    fun offer(pcm: ByteArray, firstFrame: Long) {
        if (stopping.get() != null || closed) return
        if (
            pcm.isEmpty() ||
                pcm.size % 2 != 0 ||
                pcm.size > MAX_BLOCK_BYTES ||
                firstFrame != producerEnd
        ) {
            discontinuity(producerEnd, VadFailure.INVALID_FRAME)
            return
        }
        producerEnd = Math.addExact(firstFrame, pcm.size / 2L)
        val bytes = pcm.copyOf()
        if (!queue.offer(Block(bytes, firstFrame, generation.get()))) {
            bytes.fill(0)
            discontinuity(producerEnd, VadFailure.BACKPRESSURE)
        } else {
            highWater.accumulateAndGet(queue.size, ::maxOf)
            schedule()
        }
    }

    /** Out-of-band bounded fence cannot be lost behind a full PCM queue. */
    fun discontinuity(atFrame: Long, reason: VadFailure) {
        if (stopping.get() != null || closed) return
        require(atFrame >= producerEnd)
        producerEnd = atFrame
        if (reason != VadFailure.PAUSED && reason != VadFailure.RESUMED) failure.set(reason)
        val nextGeneration = generation.incrementAndGet()
        fence.updateAndGet { old ->
            val retained =
                old?.reason?.takeIf { it != VadFailure.PAUSED && it != VadFailure.RESUMED }
            Fence(atFrame, nextGeneration, retained ?: reason)
        }
        schedule()
    }

    /** Called on the same serialized producer as offer/discontinuity/stop. */
    fun acceptDelivery(delivery: VadDelivery): Boolean =
        stopping.get() == null && delivery.generation == generation.get()

    fun currentGeneration(): Long = generation.get()

    /** Drains accepted observer work asynchronously. No caller waits for JNI/close. */
    fun stop(atFrame: Long) {
        require(atFrame >= producerEnd)
        if (stopping.compareAndSet(null, atFrame)) schedule()
    }

    fun stats(): VadStats =
        synchronized(durations) {
            val size = minOf(calls, durations.size.toLong()).toInt()
            val sorted = durations.copyOf(size).sortedArray()
            fun percentile(percent: Int): Long =
                if (size == 0) 0
                else
                    sorted[
                        ((size * percent + PERCENT_SCALE - 1) / PERCENT_SCALE - 1).coerceIn(
                            0,
                            size - 1,
                        )]
            VadStats(
                queue.size,
                highWater.get(),
                calls,
                deadlines,
                if (calls == 0L) 0 else minNanos,
                percentile(MEDIAN_PERCENT),
                percentile(TAIL_PERCENT),
                maxNanos,
                failure.get(),
                closed,
                positiveWindows,
                negativeWindows,
                lastPositiveEnd,
                maximumNegativeRunFrames,
            )
        }

    private fun schedule() {
        if (!scheduled.compareAndSet(false, true)) return
        try {
            worker.execute(::drain)
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            failure.set(VadFailure.RUNTIME_UNAVAILABLE)
            closed = true
            while (true) {
                val block = queue.poll() ?: break
                block.bytes.fill(0)
            }
            // An executor may retire between drains. Native destruction must still avoid producer.
            if (engine != null)
                Thread(
                        {
                            release()
                            adapter.reset()
                        },
                        "Dora VAD retirement",
                    )
                    .start()
            scheduled.set(false)
        }
    }

    @Suppress(
        "CyclomaticComplexMethod",
        "NestedBlockDepth",
        "ComplexCondition",
        "TooGenericExceptionCaught",
    ) // Every worker exit clears borrowed PCM and retires JNI safely.
    private fun drain() {
        try {
            initialize()
            while (true) {
                applyFence()
                val block = queue.poll() ?: break
                try {
                    if (
                        block.generation == generation.get() && block.generation == workerGeneration
                    ) {
                        if (engine != null) adapter.accept(block.bytes, block.first)
                        else if (!unavailableReported) {
                            unavailableReported = true
                            uncertain(
                                block.first + block.bytes.size / 2,
                                failure.get() ?: VadFailure.RUNTIME_UNAVAILABLE,
                            )
                        }
                    }
                } catch (exception: Exception) {
                    disable(
                        (exception as? VadException)?.failure ?: VadFailure.INFERENCE_FAILED,
                        block.first + block.bytes.size / 2,
                    )
                } finally {
                    block.bytes.fill(0)
                }
            }
            applyFence()
            stopping.get()?.let { end ->
                if (queue.isEmpty()) {
                    adapter.reset()
                    if (end > observedEnd) uncertain(end, VadFailure.COVERAGE_GAP)
                    release()
                    closed = true
                }
            }
        } finally {
            scheduled.set(false)
            if (!closed && (queue.isNotEmpty() || fence.get() != null || stopping.get() != null))
                schedule()
        }
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // Provider-neutral initialization failure never escapes into canonical capture.
    private fun initialize() {
        if (initialized) return
        initialized = true
        try {
            engine = factory.create(profile)
        } catch (exception: Exception) {
            failure.set((exception as? VadException)?.failure ?: VadFailure.INITIALIZATION_FAILED)
        } catch (_: LinkageError) {
            failure.set(VadFailure.RUNTIME_UNAVAILABLE)
        }
    }

    private fun applyFence() {
        val latest = fence.getAndSet(null) ?: return
        workerGeneration = latest.generation
        negativeRun = 0
        adapter.reset()
        uncertain(latest.frame, latest.reason)
        try {
            engine?.reset()
        } catch (_: Exception) {
            disable(VadFailure.RESET_FAILED, latest.frame)
        } catch (_: LinkageError) {
            disable(VadFailure.RESET_FAILED, latest.frame)
        }
    }

    private fun infer(range: FrameRange, samples: FloatArray) {
        val token = workerGeneration
        if (token != generation.get()) return
        val current = engine ?: return
        val started = System.nanoTime()
        val probability =
            try {
                current.probability(samples)
            } catch (_: LinkageError) {
                throw VadException(VadFailure.INFERENCE_FAILED)
            }
        val elapsed = System.nanoTime() - started
        synchronized(durations) {
            durations[(calls % durations.size).toInt()] = elapsed
            calls++
            minNanos = minOf(minNanos, elapsed)
            maxNanos = maxOf(maxNanos, elapsed)
            if (elapsed > WINDOW_NANOS) deadlines++
        }
        if (!probability.isFinite() || probability !in 0f..1f)
            throw VadException(VadFailure.INFERENCE_FAILED)
        // Fence may have changed while native inference was running. Never publish a stale result.
        if (token == generation.get()) {
            observedEnd = range.end
            val speech = probability >= profile.probabilityThreshold
            synchronized(durations) {
                if (speech) {
                    positiveWindows++
                    lastPositiveEnd = range.end
                    negativeRun = 0
                } else {
                    negativeWindows++
                    negativeRun += range.end - range.first
                    maximumNegativeRunFrames = maxOf(maximumNegativeRunFrames, negativeRun)
                }
            }
            safeEmit(VadOutput.Classified(VadObservation(range, token, speech)))
        }
    }

    private fun disable(reason: VadFailure, atFrame: Long) {
        failure.set(reason)
        unavailableReported = true
        negativeRun = 0
        adapter.reset()
        uncertain(atFrame, reason)
        release()
    }

    private fun release() {
        val current = engine
        engine = null
        try {
            current?.close()
        } catch (_: Exception) {
            failure.set(VadFailure.INFERENCE_FAILED)
        } catch (_: LinkageError) {
            failure.set(VadFailure.INFERENCE_FAILED)
        }
    }

    private fun uncertain(atFrame: Long, reason: VadFailure) {
        observedEnd = maxOf(observedEnd, atFrame)
        safeEmit(VadOutput.Uncertain(atFrame, reason))
    }

    private fun safeEmit(event: VadOutput) {
        try {
            emit(VadDelivery(workerGeneration, event))
        } catch (_: Exception) {
            failure.set(VadFailure.METADATA_FAILED)
        }
    }

    companion object {
        private const val MAX_QUEUE_CAPACITY = 320
        private const val TIMING_SAMPLES = 4096
        private const val PERCENT_SCALE = 100
        private const val MEDIAN_PERCENT = 50
        private const val TAIL_PERCENT = 95
        private const val MAX_BLOCK_BYTES = 32_000
        private const val WINDOW_NANOS = 32_000_000L
    }
}
