package com.monumentogram.dora.recording

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.audio.recording.RecordingSession
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real admission, native ownership and session; only native hardware and disk are replaced. */
class AsyncCaptureIntegrationTest {
    @Test
    fun failedOldAppendDuringNewNativeStartFencesAdmissionAndReleasesReader() {
        val rig = Rig()
        rig.pauseFirstSegment()
        rig.next.onStart = {
            rig.writer.failure = AudioFailure.UNCERTAIN
            rig.writes.one()
        }
        rig.capture.start(
            {
                assertTrue(rig.session.canCapture)
                it()
            },
            physicalId = "B",
        )
        assertFalse(rig.session.resume("B"))
        rig.capture.stop()
        rig.completions.all()
        assertEquals(800L, rig.capture.admittedFrames)
        assertEquals(1, rig.next.releases.get())
        assertEquals(RecordingPhase.INTERRUPTED, rig.session.state.phase)
        assertEquals(AudioFailure.UNCERTAIN, rig.session.state.persistenceFailure)
    }

    @Test
    fun failedOldAppendImmediatelyStopsAlreadyResumedNativeReader() {
        val rig = Rig()
        rig.pauseFirstSegment()
        rig.capture.start({ it() }, physicalId = "B")
        assertTrue(rig.session.resume("B"))
        assertTrue(rig.next.entered.await(5, TimeUnit.SECONDS))
        rig.writer.failure = AudioFailure.UNCERTAIN
        rig.writes.one()
        assertTrue(rig.next.released.await(5, TimeUnit.SECONDS))
        rig.capture.stop()
        rig.completions.all()
        assertEquals(1, rig.next.releases.get())
        assertEquals(RecordingPhase.INTERRUPTED, rig.session.state.phase)
        assertFalse(rig.capture.healthy)
    }

    @Test
    fun resumedReaderHitsSharedBackpressureEvenAfterOldQueueWasDrained() {
        val rig = Rig()
        rig.pauseFirstSegment()
        rig.capture.start({ it() }, physicalId = "B")
        assertTrue(rig.session.resume("B"))
        repeat(320) { rig.next.reads.put(1600) }
        assertTrue(rig.next.released.await(5, TimeUnit.SECONDS))
        rig.capture.stop()
        assertEquals(CaptureFailure.PERSISTENCE_BACKPRESSURE, rig.capture.failure)
        assertEquals(256_000L, rig.capture.admittedFrames)
        assertEquals(319, rig.capture.queuedBlocks)
        assertEquals(0L, rig.session.state.durableFrames)
        rig.session.interrupt()
        rig.capture.drain {}
        rig.writes.all()
        rig.completions.all()
        assertEquals(RecordingPhase.INTERRUPTED, rig.session.state.phase)
        assertFalse(rig.session.resume())
    }

    private class Rig {
        val first = Mic()
        val next = Mic()
        private var constructed = false
        val capture =
            AudioRecordCapture(
                { if (constructed) next else first.also { constructed = true } },
                { true },
                {},
            )
        val writer = Writer()
        val writes = Steps()
        val completions = Steps()
        val session =
            RecordingSession(
                AudioIdentity(RecordingId(id()), AudioAssetId(id()), id()),
                writer,
                persistence = writes,
                completion = completions,
                persistenceFailed = { capture.requestStop() },
            )

        fun pauseFirstSegment() {
            session.start()
            val admitted = CountDownLatch(1)
            capture.start({ it() }, physicalId = session.physicalId) {
                if (it == "first_pcm") admitted.countDown()
            }
            first.reads.put(1600)
            assertTrue(admitted.await(5, TimeUnit.SECONDS))
            capture.requestStop()
            capture.stop()
            capture.drainOwned { session.accept(it.pcm, it.physicalId, it.firstFrame) }
            session.pause()
            assertEquals(RecordingPhase.PAUSED, session.state.phase)
            assertEquals(0L, session.state.durableFrames)
        }
    }

    private class Steps : Executor {
        private val tasks = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun one() {
            tasks.removeFirst().run()
        }

        fun all() {
            while (tasks.isNotEmpty()) one()
        }
    }

    private class Writer : ProductAudioWriterPort {
        var failure: AudioFailure? = null

        override fun create(identity: AudioIdentity): AudioResult<Unit> = AudioResult.Value(Unit)

        override fun append(
            segment: AudioStorageUnitIdentity,
            format: AudioFormat,
            pcm: ByteArray,
        ): AudioResult<Unit> = failure?.let { AudioResult.Failed(it) } ?: AudioResult.Value(Unit)

        override fun finalize(identity: AudioIdentity): AudioResult<Unit> = AudioResult.Value(Unit)

        override fun reconcile(identity: AudioIdentity): AudioResult<Unit> = AudioResult.Value(Unit)
    }

    private class Mic : NativeMicrophone {
        val reads = LinkedBlockingQueue<Int>()
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val releases = AtomicInteger()
        var onStart: () -> Unit = {}
        @Volatile override var recording = false
        override val routeType: Int? = null

        override fun requireConfiguration() = Unit

        override fun startRecording() {
            recording = true
            onStart()
        }

        override fun read(bytes: ByteArray): Int {
            entered.countDown()
            bytes.fill(1)
            return checkNotNull(reads.poll(5, TimeUnit.SECONDS))
        }

        override fun stop() {
            recording = false
            reads.offer(0)
        }

        override fun release() {
            releases.incrementAndGet()
            released.countDown()
        }
    }

    companion object {
        private fun id() = UUID.randomUUID().toString()
    }
}
