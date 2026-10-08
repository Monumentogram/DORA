package com.monumentogram.dora.recording

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.audio.recording.RecordingSession
import com.monumentogram.dora.audio.recording.RecordingWriterOperation
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Virtual microphone cadence and separately scheduled commit/callback; no microphone or disk. */
class CapturePersistenceBackpressureTest {
    @Test
    fun failingAppendRetainsExactOperationWithoutClaimingDurability() {
        val pipeline = Pipeline()
        repeat(100) { pipeline.frame() }
        pipeline.writer.failure = AudioFailure.UNCERTAIN
        pipeline.io.all()
        pipeline.callbacks.all()
        assertEquals(RecordingWriterOperation.APPEND, pipeline.session.failureOperation)
        assertEquals(0L, pipeline.session.state.durableFrames)
        assertEquals(RecordingPhase.INTERRUPTED, pipeline.session.state.phase)
    }

    @Test
    fun failingFinalizeIsDistinguishedFromAppendAndPreservesDurablePrefix() {
        val pipeline = Pipeline()
        repeat(100) { pipeline.frame() }
        pipeline.io.all()
        pipeline.callbacks.all()
        pipeline.writer.failure = AudioFailure.KEY_UNAVAILABLE
        pipeline.session.requestStop()
        pipeline.session.confirmStop()
        pipeline.io.all()
        pipeline.callbacks.all()
        assertEquals(RecordingWriterOperation.FINALIZE, pipeline.session.failureOperation)
        assertEquals(80000L, pipeline.session.state.durableFrames)
        assertEquals(RecordingPhase.INTERRUPTED, pipeline.session.state.phase)
    }

    @Test
    fun sustainedWriterStallReachesExactFenceWithAnEmptyCanonicalQueue() {
        val pipeline = Pipeline()
        repeat(320) { pipeline.frame() }
        assertEquals(256_000L, pipeline.session.state.frames)
        assertEquals(0L, pipeline.session.state.durableFrames)
        assertEquals(0, pipeline.queue.size)
        assertEquals(1, pipeline.queue.highWater)
        assertEquals(CaptureAdmission.Result.FULL, pipeline.offer())
        assertEquals(256_000L, pipeline.admission.snapshot().frames)
    }

    @Test
    fun committedWriterResultDoesNotReleaseAdmissionBeforeItsCallback() {
        val pipeline = Pipeline()
        repeat(320) { pipeline.frame() }
        // First task publishes OPEN and appends; completion remains queued independently.
        pipeline.io.next()
        assertEquals(80_000L, pipeline.writer.frames)
        assertEquals(0L, pipeline.session.state.durableFrames)
        assertEquals(CaptureAdmission.Result.FULL, pipeline.offer())
        assertEquals(3, pipeline.session.pendingWriterUnits)
        pipeline.callbacks.all()
        assertEquals(2, pipeline.session.pendingWriterUnits)
        assertTrue(pipeline.session.lastCompletedAppendDurationNanos >= 0)
        assertEquals(80_000L, pipeline.session.state.durableFrames)
        pipeline.frame()
        assertEquals(256_800L, pipeline.admission.snapshot().frames)
    }

    @Test
    fun repeatedMultiSecondStallsStayWithinFenceWhenDurableProgressCatchesUp() {
        val pipeline = Pipeline()
        repeat(6) {
            // Five seconds to seal plus a further 3.4 seconds of stalled writer progress.
            repeat(168) { pipeline.frame() }
            pipeline.io.all()
            pipeline.callbacks.all()
        }
        assertEquals(806_400L, pipeline.session.state.frames)
        assertEquals(800_000L, pipeline.session.state.durableFrames)
        assertEquals(1, pipeline.queue.highWater)
        assertTrue(pipeline.writer.contiguous)
    }

    @Test
    fun pauseDuringWriterStallPreservesTailAndDoesNotClaimDurability() {
        val pipeline = Pipeline()
        repeat(120) { pipeline.frame() }
        pipeline.admission.fence()
        pipeline.session.pause()
        assertEquals(RecordingPhase.PAUSED, pipeline.session.state.phase)
        assertEquals(96_000L, pipeline.session.state.frames)
        assertEquals(0L, pipeline.session.state.durableFrames)
        pipeline.io.all()
        pipeline.callbacks.all()
        assertEquals(96_000L, pipeline.session.state.durableFrames)
        assertTrue(pipeline.writer.contiguous)
    }

    @Test
    fun stopDuringWriterStallWaitsForEveryAppendBeforeFinalize() {
        val pipeline = Pipeline()
        repeat(220) { pipeline.frame() }
        pipeline.admission.fence()
        pipeline.session.requestStop()
        pipeline.session.confirmStop()
        assertEquals(RecordingPhase.FINALIZING, pipeline.session.state.phase)
        assertEquals(0, pipeline.writer.finalizations)
        pipeline.io.all()
        assertEquals(RecordingPhase.FINALIZING, pipeline.session.state.phase)
        pipeline.callbacks.all()
        assertEquals(RecordingPhase.SAVED, pipeline.session.state.phase)
        assertEquals(176_000L, pipeline.session.state.durableFrames)
        assertEquals(1, pipeline.writer.finalizations)
        assertTrue(pipeline.writer.contiguous)
    }

    private class Pipeline {
        val queue = BoundedPcmQueue(320)
        var clock = 0L
        val admission = CaptureAdmission(queue) { clock }
        val io = Steps()
        val callbacks = Steps()
        val writer = Writer()
        val session: RecordingSession =
            RecordingSession(
                AudioIdentity(
                    RecordingId("00000000-0000-4000-8000-000000000001"),
                    AudioAssetId("00000000-0000-4000-8000-000000000002"),
                    "00000000-0000-4000-8000-000000000003",
                ),
                writer,
                persistence = io,
                completion = callbacks,
                changed = { durable() },
            )
        val generation: Long

        init {
            assertTrue(session.start())
            generation = admission.begin(session.captureEpochId)
            assertTrue(admission.open(generation))
        }

        private fun durable() = admission.durableThrough(session.state.durableFrames)

        fun offer(): CaptureAdmission.Result {
            clock += 50_000_000L
            val pcm = ByteArray(1600) { 37 }
            return admission.offer(generation, pcm).also {
                if (it != CaptureAdmission.Result.ACCEPTED) pcm.fill(0)
            }
        }

        fun frame() {
            assertEquals(CaptureAdmission.Result.ACCEPTED, offer())
            queue.drainOwned(1) { session.accept(it.pcm, it.captureEpochId, it.firstFrame) }
        }
    }

    private class Steps : Executor {
        private val tasks = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun next() = tasks.removeFirst().run()

        fun all() {
            while (tasks.isNotEmpty()) next()
        }
    }

    private class Writer : ProductAudioWriterPort {
        var failure: AudioFailure? = null
        var frames = 0L
        var contiguous = true
        var finalizations = 0

        override fun create(identity: AudioIdentity) = AudioResult.Value(Unit)

        override fun segmentation(identity: AudioIdentity, metadata: SegmentationMetadata) =
            AudioResult.Value(Unit)

        override fun append(
            segment: AudioStorageUnitIdentity,
            format: AudioFormat,
            pcm: ByteArray,
        ): AudioResult<Unit> {
            failure?.let {
                return AudioResult.Failed(it)
            }
            contiguous = contiguous && segment.firstFrame == frames && pcm.all { it == 37.toByte() }
            frames += pcm.size / 2
            return AudioResult.Value(Unit)
        }

        override fun finalize(identity: AudioIdentity): AudioResult<Unit> {
            finalizations++
            failure?.let {
                return AudioResult.Failed(it)
            }
            return AudioResult.Value(Unit)
        }

        override fun reconcile(identity: AudioIdentity): AudioResult<Unit> =
            error("Unexpected reconciliation")
    }
}
