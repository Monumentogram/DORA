package com.monumentogram.dora.recording

import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.audio.recording.RecordingSegmentation
import com.monumentogram.dora.audio.recording.RecordingSession
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

/** Real session, observer and service generation; only microphone, disk and clocks are replaced. */
class RecordingPreparationIntegrationTest {
    @Test
    fun delayedNativeFactoryKeepsPreparingThenFirstCapturedFrameIsZero() {
        val rig = Rig()
        rig.begin()
        assertEquals(RecordingPhase.PREPARING, rig.visiblePhase())
        assertEquals(0, rig.microphoneStarts)
        rig.inference.run()
        assertEquals(0, rig.microphoneStarts) // Native completion only posts to the control owner.
        rig.controls.run()
        assertEquals(RecordingPhase.RECORDING, rig.visiblePhase())
        assertEquals(1, rig.microphoneStarts)
        rig.capture()
        rig.stop()
        assertEquals(RecordingPhase.SAVED, rig.session.state.phase)
        assertEquals(800L, rig.writer.frames)
        assertEquals(listOf(0L), rig.captureStarts)
        assertArrayEquals(rig.expectedHash(), rig.writer.digest.digest())
        assertTrue(rig.segmentation.diagnostics().contains("vadFailure=NONE"))
    }

    @Test
    fun stopWhilePreparingPreservesBothEmptyAndRestoredSourceSemantics() {
        for (prefix in listOf(0L, 16000L)) {
            val rig = Rig(prefix)
            rig.begin()
            rig.stop()
            rig.inference.run()
            rig.controls.run()
            assertEquals(
                if (prefix == 0L) RecordingPhase.EMPTY else RecordingPhase.SAVED,
                rig.session.state.phase,
            )
            assertEquals(prefix, rig.session.state.durableFrames)
            assertEquals(0, rig.microphoneStarts)
            assertEquals(0, rig.factoryCreations)
        }
    }

    @Test
    fun pendingPauseDoesNotStartMicrophoneAndResumeStillNeedsCurrentAuthority() {
        val rig = Rig()
        rig.begin()
        rig.pauseRequested = true
        assertEquals(RecordingPhase.PREPARING, rig.visiblePhase())
        assertEquals(
            RecordingPhase.RECORDING,
            rig.session.state.phase,
        ) // Logical Stop owner remains.
        rig.inference.run()
        rig.controls.run()
        assertEquals(RecordingPhase.PAUSED, rig.visiblePhase())
        assertEquals(0, rig.microphoneStarts)
        rig.authorized = false
        rig.resume()
        assertEquals(0, rig.microphoneStarts)
        assertEquals(RecordingPhase.PAUSED, rig.session.state.phase)
        rig.stop()
        rig.inference.run()
    }

    @Test
    fun destroyedServiceCannotUseAnAlreadyQueuedReadyCallback() {
        val rig = Rig()
        rig.begin()
        rig.inference.run()
        rig.service.destroyed()
        rig.service.created()
        rig.controls.run()
        rig.inference.run()
        assertEquals(0, rig.microphoneStarts)
        assertEquals(1, rig.engineCloses)
    }

    @Test
    fun revocationAfterReadySettlementStillFencesNativeStart() {
        val rig = Rig()
        rig.beforeNative = { rig.authorized = false }
        rig.begin()
        rig.inference.run()
        rig.controls.run()
        assertEquals(0, rig.microphoneStarts)
        assertEquals(RecordingPhase.PREPARING, rig.visiblePhase())
        rig.stop()
        rig.inference.run()
        assertEquals(1, rig.engineCloses)
    }

    @Test
    fun timeoutDisablesObserverBeforeCanonicalFallbackAndLateFactoryCannotRun() {
        val rig = Rig()
        rig.begin()
        checkNotNull(rig.controls.deadline).run()
        assertEquals(1, rig.microphoneStarts)
        rig.capture()
        rig.inference.run()
        rig.controls.run()
        rig.stop()
        assertEquals(1, rig.microphoneStarts)
        assertEquals(0, rig.factoryCreations)
        assertEquals(RecordingPhase.SAVED, rig.session.state.phase)
        assertArrayEquals(rig.expectedHash(), rig.writer.digest.digest())
        assertTrue(rig.writer.rows.any { it.reason == "INITIALIZATION_FAILED" && it.degraded })
        assertTrue(rig.segmentation.diagnostics().contains("vadFailure=INITIALIZATION_FAILED"))
    }

    private class Controls : RecordingPreparation.Driver {
        val tasks = ArrayDeque<() -> Unit>()
        var deadline: Runnable? = null

        override fun post(action: () -> Unit) {
            tasks.add(action)
        }

        override fun schedule(task: Runnable, delayMillis: Long): () -> Unit {
            assertEquals(10000L, delayMillis)
            deadline = task
            return { deadline = null }
        }

        fun run() {
            while (tasks.isNotEmpty()) tasks.removeFirst().invoke()
        }
    }

    private class Tasks : AbstractExecutorService() {
        private val queued = ArrayDeque<Runnable>()
        private var closed = false

        override fun execute(command: Runnable) {
            if (closed) throw RejectedExecutionException()
            queued.add(command)
        }

        override fun shutdown() {
            closed = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown()
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
        val rows = mutableListOf<SegmentationMetadata>()
        val digest = java.security.MessageDigest.getInstance("SHA-256")

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
            rows += metadata
            return AudioResult.Value(Unit)
        }

        override fun finalize(identity: AudioIdentity) = AudioResult.Value(Unit)

        override fun reconcile(identity: AudioIdentity) = AudioResult.Value(Unit)
    }

    private class Rig(prefix: Long = 0) {
        val writer = Writer().also { it.frames = prefix }
        val session =
            RecordingSession(AudioIdentity(RecordingId(id()), AudioAssetId(id()), id()), writer)
        val inference = Tasks()
        val controls = Controls()
        val preparation = RecordingPreparation(controls)
        val service = RecordingServiceOwner()
        private val serviceToken = service.created()
        var authorized = true
        var pauseRequested = false
        var microphoneStarts = 0
        var factoryCreations = 0
        var engineCloses = 0
        var beforeNative: () -> Unit = {}
        val captureStarts = mutableListOf<Long>()
        private val pcm = ByteArray(1600) { (it * 17 + 3).toByte() }
        val segmentation: RecordingSegmentation

        init {
            if (prefix == 0L) check(session.start())
            else {
                session.restore(prefix, 1)
                check(session.resume())
            }
            segmentation =
                RecordingSegmentation(
                    session,
                    VadEngineFactory {
                        assertEquals(0, microphoneStarts)
                        factoryCreations++
                        object : VadEngine {
                            override fun probability(samples: FloatArray) = 0f

                            override fun reset() = Unit

                            override fun close() {
                                engineCloses++
                            }
                        }
                    },
                    inference,
                )
        }

        fun begin() {
            preparation.begin(
                session,
                segmentation.prepare(),
                { authorized && service.isCurrent(serviceToken) },
                { reason ->
                    if (reason != null) segmentation.preparationUnavailable(reason)
                    if (pauseRequested) {
                        segmentation.discontinuity(VadFailure.PAUSED)
                        session.pause()
                    } else startMicrophone()
                },
                segmentation::stop,
            )
        }

        private fun startMicrophone() {
            beforeNative()
            if (authorized)
                service.runCurrent(serviceToken) { if (session.canCapture) microphoneStarts++ }
        }

        fun resume() {
            if (authorized && session.state.phase == RecordingPhase.PAUSED) {
                service.runCurrent(serviceToken) { if (session.resume()) microphoneStarts++ }
            }
        }

        fun visiblePhase() =
            preparation.visiblePhase(session, session.state.phase, microphoneStarts > 0)

        fun capture() {
            check(microphoneStarts > 0)
            val first = session.state.frames
            captureStarts += first
            session.accept(pcm)
            segmentation.accept(pcm, first)
        }

        fun stop() {
            preparation.cancel()
            segmentation.stop()
            session.requestStop()
            session.confirmStop()
            inference.run()
            controls.run()
        }

        fun expectedHash(): ByteArray =
            java.security.MessageDigest.getInstance("SHA-256").digest(pcm)
    }

    private companion object {
        fun id() = UUID.randomUUID().toString()
    }
}
