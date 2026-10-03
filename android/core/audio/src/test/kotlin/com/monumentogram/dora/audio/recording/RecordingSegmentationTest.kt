package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.vad.VadEngine
import com.monumentogram.dora.vad.VadEngineFactory
import com.monumentogram.dora.vad.VadFailure
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingSegmentationTest {
    private class Tasks : AbstractExecutorService() {
        val queued = ArrayDeque<Runnable>()
        var closed = false
        var reject = false

        override fun execute(command: Runnable) {
            if (reject || closed) throw RejectedExecutionException()
            queued.add(command)
        }

        override fun shutdown() {
            closed = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            closed = true
            return mutableListOf()
        }

        override fun isShutdown() = closed

        override fun isTerminated() = closed && queued.isEmpty()

        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated

        fun run() {
            while (queued.isNotEmpty()) queued.removeFirst().run()
        }
    }

    private class Writer : ProductAudioWriterPort {
        var frames = 0L
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val rows = mutableListOf<SegmentationMetadata>()

        override fun create(identity: AudioIdentity) = AudioResult.Value(Unit)

        override fun append(
            segment: AudioStorageUnitIdentity,
            format: AudioFormat,
            pcm: ByteArray,
        ): AudioResult<Unit> {
            assertEquals(frames, segment.firstFrame)
            frames += pcm.size / 2
            digest.update(pcm)
            return AudioResult.Value(Unit)
        }

        override fun segmentation(
            identity: AudioIdentity,
            metadata: SegmentationMetadata,
        ): AudioResult<Unit> {
            metadata.validate(frames)
            rows.add(metadata)
            return AudioResult.Value(Unit)
        }

        override fun finalize(identity: AudioIdentity) = AudioResult.Value(Unit)

        override fun reconcile(identity: AudioIdentity) = AudioResult.Value(Unit)
    }

    private val worker = Tasks()
    private val writer = Writer()
    private val session =
        RecordingSession(
                AudioIdentity(
                    RecordingId(UUID.randomUUID().toString()),
                    AudioAssetId(UUID.randomUUID().toString()),
                    UUID.randomUUID().toString(),
                ),
                writer,
            )
            .also { it.start() }
    private var speech = true
    private val segmentation =
        RecordingSegmentation(
            session,
            VadEngineFactory {
                object : VadEngine {
                    override fun probability(samples: FloatArray) = if (speech) 1f else 0f

                    override fun reset() = Unit

                    override fun close() = Unit
                }
            },
            worker,
        )

    private fun accept(frames: Int, process: Boolean = true) {
        val first = session.state.frames
        val pcm = ByteArray(frames * 2)
        session.accept(pcm)
        segmentation.accept(pcm, first)
        if (process) {
            worker.run()
            segmentation.drain()
        }
    }

    private fun stop() {
        segmentation.stop()
        session.requestStop()
        session.confirmStop()
        assertEquals(RecordingPhase.SAVED, session.state.phase)
        assertEquals(session.state.frames, writer.frames)
    }

    @Test
    fun stopWithUndeliveredOnsetPersistsUnknownWithoutWaitingForWorker() {
        accept(5120, false)
        stop()
        assertTrue(writer.rows.none { it.kind == SegmentationKind.SEMANTIC_CLOSE })
        val gap = writer.rows.single { it.kind == SegmentationKind.DEGRADED }
        assertEquals(0L, gap.firstFrame)
        assertEquals(5120L, gap.endFrame)
        assertEquals("COVERAGE_GAP", gap.reason)
        worker.run()
        assertTrue(writer.rows.none { it.kind == SegmentationKind.SEMANTIC_CLOSE })
    }

    @Test
    fun rejectedInferenceExecutorCannotPreventSavedOrHideUnknownTail() {
        worker.reject = true
        accept(5120, false)
        stop()
        assertTrue(writer.rows.any { it.kind == SegmentationKind.DEGRADED && it.endFrame == 5120L })
        assertTrue(segmentation.diagnostics().contains("RUNTIME_UNAVAILABLE"))
    }

    @Test
    fun pauseDropsPendingOnsetAndResumeCanOpenFreshSpeech() {
        accept(5120, false)
        segmentation.discontinuity(VadFailure.PAUSED)
        segmentation.discontinuity(VadFailure.RESUMED)
        worker.run()
        accept(5120)
        stop()
        val closed = writer.rows.single { it.kind == SegmentationKind.SEMANTIC_CLOSE }
        assertEquals(5120L, closed.firstFrame)
        assertEquals(10240L, closed.endFrame)
        assertTrue(writer.rows.any { it.kind == SegmentationKind.DEGRADED && it.endFrame == 5120L })
    }

    @Test
    fun acceptedOpenSurvivesPauseAndClosesExactlyOnce() {
        accept(5120)
        segmentation.discontinuity(VadFailure.PAUSED)
        segmentation.discontinuity(VadFailure.RESUMED)
        worker.run()
        accept(5120)
        stop()
        assertEquals(1, writer.rows.count { it.kind == SegmentationKind.SEMANTIC_CLOSE })
        assertEquals(
            0L,
            writer.rows.single { it.kind == SegmentationKind.SEMANTIC_CLOSE }.firstFrame,
        )
    }

    @Test
    fun queuedSilenceCannotCloseSegmentAfterPauseFence() {
        accept(5120)
        speech = false
        repeat(89) { repeat(2) { accept(8000) } }
        accept(16000, false)
        segmentation.discontinuity(VadFailure.PAUSED)
        worker.run()
        segmentation.discontinuity(VadFailure.RESUMED)
        worker.run()
        speech = true
        accept(5120)
        stop()
        val semantic = writer.rows.filter { it.kind == SegmentationKind.SEMANTIC_CLOSE }
        assertEquals(1, semantic.size)
        assertEquals("STOP", semantic.single().reason)
        assertTrue(semantic.single().degraded)
    }

    @Test
    fun partialWindowAtStopIsDurableUnknownRange() {
        accept(15)
        stop()
        val gap = writer.rows.single { it.kind == SegmentationKind.DEGRADED }
        assertEquals(0L, gap.firstFrame)
        assertEquals(15L, gap.endFrame)
    }

    @Test
    fun inputQueueOverflowCannotFabricateSilenceOrLoseCanonicalFrames() {
        repeat(41) { accept(512, false) }
        worker.run()
        segmentation.drain()
        accept(5120)
        stop()
        assertTrue(writer.rows.any { it.reason == "BACKPRESSURE" })
        assertTrue(writer.rows.none { it.reason == "SILENCE_90_SECONDS" })
        assertEquals(41 * 512L + 5120, writer.frames)
    }

    @Test
    fun classificationQueueOverflowInvalidatesWholeUndeliveredInterval() {
        repeat(40) { accept(16000, false) }
        worker.run()
        segmentation.drain()
        worker.run()
        accept(5120)
        stop()
        val gaps = writer.rows.filter { it.kind == SegmentationKind.DEGRADED }
        assertTrue(
            gaps.any {
                it.reason == "BACKPRESSURE" && it.firstFrame == 0L && it.endFrame == 640000L
            }
        )
        assertTrue(writer.rows.none { it.reason == "SILENCE_90_SECONDS" })
        assertEquals(645120L, writer.frames)
    }

    @Test
    fun delayedUncertaintyAcrossRepeatedPauseResumeCannotAdvanceSilence() {
        accept(5120)
        accept(100, false)
        repeat(3) {
            segmentation.discontinuity(VadFailure.PAUSED)
            segmentation.discontinuity(VadFailure.RESUMED)
        }
        worker.run()
        accept(5120)
        stop()
        assertEquals(1, writer.rows.count { it.kind == SegmentationKind.SEMANTIC_CLOSE })
        assertTrue(writer.rows.none { it.reason == "SILENCE_90_SECONDS" })
    }

    @Test
    fun vadOnOffAndOverlapProduceIdenticalCanonicalHashAcrossTwoCaps() {
        val baselineWriter = Writer()
        val baseline =
            RecordingSession(
                    AudioIdentity(
                        RecordingId(UUID.randomUUID().toString()),
                        AudioAssetId(UUID.randomUUID().toString()),
                        UUID.randomUUID().toString(),
                    ),
                    baselineWriter,
                )
                .also { it.start() }
        val pcm = ByteArray(32000) { (it * 17 + 3).toByte() }
        repeat(1201) {
            val first = session.state.frames
            baseline.accept(pcm)
            session.accept(pcm)
            segmentation.accept(pcm, first)
            worker.run()
            segmentation.drain()
        }
        pcm.fill(0)
        stop()
        baseline.requestStop()
        baseline.confirmStop()
        assertEquals(RecordingPhase.SAVED, baseline.state.phase)
        assertEquals(baselineWriter.frames, writer.frames)
        assertArrayEquals(baselineWriter.digest.digest(), writer.digest.digest())
        assertEquals(
            2,
            writer.rows.count { it.kind == SegmentationKind.TECHNICAL_CLOSE && it.reason == "CAP" },
        )
        assertEquals(1, writer.rows.count { it.kind == SegmentationKind.SEMANTIC_CLOSE })
    }
}
