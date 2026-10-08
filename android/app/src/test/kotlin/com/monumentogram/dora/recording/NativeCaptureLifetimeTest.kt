package com.monumentogram.dora.recording

import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCaptureLifetimeTest {
    @Test
    fun readFailureRetainsItsAdmissionBoundaryBeforeShutdownAndRetirement() {
        val mic = Microphone()
        val capture = capture { mic }
        val accepted = CountDownLatch(1)
        capture.start({ it() }) { if (it == "first_pcm") accepted.countDown() }
        mic.reads.put(1600)
        assertTrue(accepted.await(5, TimeUnit.SECONDS))
        mic.reads.put(-1)
        assertTrue(mic.releaseEntered.await(5, TimeUnit.SECONDS))
        capture.stop()
        val event = checkNotNull(capture.terminalEvent)
        capture.durableThrough(800)
        assertEquals(CaptureFailure.READ_ERROR, event.failure)
        assertEquals(800L, event.admission.frames)
        assertEquals(0L, event.admission.durableFrames)
        assertEquals(800L, capture.admissionDiagnostics().durableFrames)
        assertTrue(capture.admissionDiagnostics().generation > event.admission.generation)
        assertEquals(1, event.admission.outstandingBlocks)
        assertTrue(event.atNanos > 0)
        capture.drain {}
        capture.retire()
        assertEquals(event, capture.terminalEvent)
        assertFalse(capture.hasLiveThread)
    }

    @Test
    fun cancelledNewStartDoesNotInheritAnOldReadersFailure() {
        val old = Microphone()
        val next = Microphone()
        var constructions = 0
        val capture = capture { if (constructions++ == 0) old else next }
        capture.start({ it() })
        old.reads.put(-1)
        assertTrue(old.releaseEntered.await(5, TimeUnit.SECONDS))
        capture.stop()
        assertEquals(CaptureFailure.READ_ERROR, capture.failure)
        assertThrows(IllegalStateException::class.java) {
            capture.start({
                capture.requestStop()
                error("PAUSE_BEFORE_NATIVE_START")
            })
        }
        assertNull(capture.failure)
        assertEquals(0, next.starts.get())
        assertEquals(1, next.releases.get())
    }

    @Test
    fun pauseUnblocksReadWithoutAdmittingTheReturnedBlockAndReleasesOnce() {
        val mic = Microphone()
        val capture = capture { mic }
        capture.start({ it() })
        assertTrue(mic.readEntered.await(5, TimeUnit.SECONDS))
        capture.requestStop()
        capture.stop()
        capture.stop()
        assertEquals(0L, capture.admittedFrames)
        assertEquals(0, capture.queuedBlocks)
        assertEquals(1, mic.releases.get())
        assertEquals(0, mic.callsAfterRelease.get())
        assertFalse(capture.hasLiveThread)
    }

    @Test
    fun acceptedFramesSurvivePauseUntilTheWorkerDrainsThem() {
        val mic = Microphone()
        val capture = capture { mic }
        val accepted = CountDownLatch(1)
        capture.start({ it() }) { if (it == "first_pcm") accepted.countDown() }
        mic.reads.put(1600)
        assertTrue(accepted.await(5, TimeUnit.SECONDS))
        val fence = capture.requestStop()
        capture.stop()
        var bytes = 0
        capture.drain { bytes += it.size }
        assertEquals(800L, fence.frames)
        assertEquals(1600, bytes)
        assertEquals(fence.frames, capture.admittedFrames)
    }

    @Test
    fun pauseDuringNativeStartCannotReopenAdmission() {
        val mic = Microphone()
        val capture = capture { mic }
        mic.onStart = { capture.requestStop() }
        capture.start({ it() })
        capture.stop()
        assertEquals(0L, capture.admittedFrames)
        assertEquals(1, mic.starts.get())
        assertEquals(1, mic.releases.get())
    }

    @Test
    fun rejectedStartAuthorityReleasesConstructedHandleWithoutStartingMic() {
        val mic = Microphone()
        val capture = capture { mic }
        assertThrows(IllegalStateException::class.java) { capture.start({ error("REVOKED") }) }
        assertEquals(0, mic.starts.get())
        assertEquals(1, mic.releases.get())
        assertFalse(capture.hasLiveThread)
    }

    @Test
    fun delayedOldReaderReleasePreventsEveryNewStartAndEventuallyJoins() {
        val old = Microphone(delayedRelease = true)
        val next = Microphone()
        var constructions = 0
        val capture = capture { if (constructions++ == 0) old else next }
        capture.start({ it() })
        assertTrue(old.readEntered.await(5, TimeUnit.SECONDS))
        capture.requestStop()
        assertTrue(old.releaseEntered.await(5, TimeUnit.SECONDS))
        try {
            val failure = assertThrows(CaptureException::class.java) { capture.stop() }
            assertEquals(CaptureFailure.THREAD_TIMEOUT, failure.failure)
            assertThrows(IllegalStateException::class.java) { capture.start({ it() }) }
            assertEquals(1, constructions)
            assertEquals(0, next.starts.get())
        } finally {
            old.allowRelease.countDown()
            capture.stop()
        }
        capture.start({ it() })
        capture.stop()
        assertEquals(1, old.releases.get())
        assertEquals(1, next.releases.get())
        assertEquals(0, old.callsAfterRelease.get())
    }

    @Test
    fun duplicateStartCannotConstructSecondNativeMicrophone() {
        val mic = Microphone()
        var constructions = 0
        val capture = capture {
            constructions++
            mic
        }
        capture.start({ it() })
        try {
            assertThrows(IllegalStateException::class.java) { capture.start({ it() }) }
            assertEquals(1, constructions)
        } finally {
            capture.stop()
        }
    }

    private fun capture(factory: () -> NativeMicrophone) = AudioRecordCapture(factory, { true }, {})

    private class Microphone(delayedRelease: Boolean = false) : NativeMicrophone {
        val reads = LinkedBlockingQueue<Int>()
        val readEntered = CountDownLatch(1)
        val releaseEntered = CountDownLatch(1)
        val allowRelease = CountDownLatch(if (delayedRelease) 1 else 0)
        val starts = AtomicInteger()
        val releases = AtomicInteger()
        val callsAfterRelease = AtomicInteger()
        var onStart: () -> Unit = {}
        @Volatile override var recording = false
        override val routeType: Int? = null

        override fun requireConfiguration() = Unit

        override fun startRecording() {
            starts.incrementAndGet()
            recording = true
            onStart()
        }

        override fun read(bytes: ByteArray): Int {
            readEntered.countDown()
            val count = checkNotNull(reads.poll(5, TimeUnit.SECONDS))
            bytes.fill(1)
            return count
        }

        override fun stop() {
            if (releases.get() != 0) callsAfterRelease.incrementAndGet()
            recording = false
            reads.offer(0)
        }

        override fun release() {
            releaseEntered.countDown()
            check(allowRelease.await(5, TimeUnit.SECONDS))
            releases.incrementAndGet()
        }
    }
}
