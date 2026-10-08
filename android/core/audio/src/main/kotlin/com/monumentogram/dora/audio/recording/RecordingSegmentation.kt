package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.vad.BoundaryReason
import com.monumentogram.dora.vad.FrameRange
import com.monumentogram.dora.vad.SegmentationProfile
import com.monumentogram.dora.vad.SegmentationReducer
import com.monumentogram.dora.vad.SemanticEvent
import com.monumentogram.dora.vad.VadDelivery
import com.monumentogram.dora.vad.VadEngineFactory
import com.monumentogram.dora.vad.VadFailure
import com.monumentogram.dora.vad.VadObserver
import com.monumentogram.dora.vad.VadOutput
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Control-owned metadata delivery. Worker outputs are bounded and fenced before persistence. */
class RecordingSegmentation(
    private val session: RecordingSession,
    factory: VadEngineFactory,
    private val worker: ExecutorService = Executors.newSingleThreadExecutor {
        Thread(it, "Dora VAD inference")
    },
) {
    private companion object {
        const val MAX_DELIVERIES = 256
    }

    private val deliveries = ArrayBlockingQueue<VadDelivery>(MAX_DELIVERIES)
    private val overflow = AtomicBoolean(false)
    private val recordingGeneration = UUID.randomUUID().toString()
    private var stopped = false
    private val reducer =
        SegmentationReducer(SegmentationProfile.FROZEN, session.state.frames, 1, ::retainEvent)
    private val observer =
        VadObserver(SegmentationProfile.FROZEN, session.state.frames, factory, worker) {
            if (!deliveries.offer(it)) overflow.set(true)
        }
    private var opened = 0L
    private var closed = 0L
    private var silenceBoundaries = 0L
    private var lastBoundaryFrame = -1L

    /** Called only after canonical acceptance, with the original borrowed block still valid. */
    fun accept(pcm: ByteArray, firstFrame: Long) {
        if (stopped) return
        drain()
        observer.offer(pcm, firstFrame)
    }

    fun drain() {
        if (stopped) return
        if (overflow.getAndSet(false)) {
            deliveries.clear()
            observer.discontinuity(session.state.frames, VadFailure.BACKPRESSURE)
            reducer.discontinuity(
                session.state.frames,
                observer.currentGeneration(),
                VadFailure.BACKPRESSURE,
            )
        }
        repeat(MAX_DELIVERIES) {
            val delivery = deliveries.poll() ?: return
            if (!observer.acceptDelivery(delivery)) return@repeat
            when (val event = delivery.event) {
                is VadOutput.Classified -> reducer.observe(event.observation)
                is VadOutput.Uncertain ->
                    reducer.discontinuity(event.atFrame, delivery.generation, event.failure)
            }
        }
    }

    private fun retainEvent(event: SemanticEvent) {
        when (event) {
            is SemanticEvent.Opened -> opened++
            is SemanticEvent.Closed -> {
                retainClosed(
                    event.ordinal,
                    event.range,
                    event.reason.name,
                    event.degraded,
                )
                closed++
                if (event.reason == BoundaryReason.SILENCE_90_SECONDS) silenceBoundaries++
                lastBoundaryFrame = event.range.end
            }
            is SemanticEvent.Degraded -> {
                session.retainMetadata(
                    SegmentationMetadata(
                        SegmentationKind.DEGRADED,
                        UUID.randomUUID().toString(),
                        event.firstFrame,
                        event.atFrame,
                        reason = event.failure.name,
                        degraded = true,
                    )
                )
            }
        }
    }

    fun discontinuity(reason: VadFailure) {
        drain()
        observer.discontinuity(session.state.frames, reason)
        reducer.discontinuity(session.state.frames, observer.currentGeneration(), reason)
    }

    /** Stop never waits for inference. Undelivered coverage is explicitly incomplete. */
    fun stop() {
        if (stopped) return
        drain()
        stopped = true
        observer.stop(session.state.frames)
        reducer.stop(session.state.frames)
        deliveries.clear()
        try {
            worker.execute { worker.shutdown() } // Serialized after drain; no caller waits for JNI.
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            worker.shutdown()
        }
    }

    private fun retainClosed(ordinal: Long, range: FrameRange, reason: String, degraded: Boolean) {
        val id =
            UUID.nameUUIDFromBytes("$recordingGeneration:$ordinal".toByteArray(Charsets.UTF_8))
                .toString()
        session.retainMetadata(
            SegmentationMetadata(
                SegmentationKind.SEMANTIC_CLOSE,
                id,
                range.first,
                range.end,
                reason = reason,
                degraded = degraded,
            )
        )
    }

    val queueHighWater: Int
        get() = observer.stats().queueHighWater

    fun diagnostics(): String {
        val s = observer.stats()
        return "vadProfile=${SegmentationProfile.FROZEN.id} vadFailure=${s.failure ?: "NONE"} " +
            "vadQueue=${s.queueSize} vadHighWater=${s.queueHighWater} vadInference=${s.inferenceCount} " +
            "vadDeadlineMisses=${s.missedDeadlines} vadMinNanos=${s.minNanos} " +
            "vadP50Nanos=${s.p50Nanos} vadP95Nanos=${s.p95Nanos} vadMaxNanos=${s.maxNanos} " +
            "vadTimingWindow=4096 vadClosed=${s.closed} semanticOpened=$opened semanticClosed=$closed " +
            "vadPositiveWindows=${s.positiveWindows} vadNegativeWindows=${s.negativeWindows} " +
            "vadLastPositiveEnd=${s.lastPositiveEnd} vadMaxNegativeFrames=${s.maximumNegativeRunFrames} " +
            "silenceBoundaries=$silenceBoundaries lastBoundaryFrame=$lastBoundaryFrame"
    }
}
