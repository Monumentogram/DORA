package com.monumentogram.dora

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioCompletion
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.audio.recording.RecordingSession
import com.monumentogram.dora.vad.SegmentationProfile
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Test-only observer of host-driven real UI capture. Reflection reads identity/state on the
 * existing control owner; it never mutates fields or injects a provider. Exact-source readback
 * only, no Recovery discovery/reconcile, plaintext export or key export.
 */
class VadProductReadbackTest {
    @Suppress("LongMethod") // One bound-session campaign; registration is paired with cleanup.
    @Test
    fun readControlledProductCampaign() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("doraVadReadback") == "observe-controlled-campaign")
        val campaign = checkNotNull(args.getString("campaignId"))
        require(campaign.matches(Regex("[a-z0-9-]{1,64}")))
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as DoraApplication
        assertEquals("com.monumentogram.dora.debug", app.packageName)
        assertTrue(
            app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        )
        val destination = File(app.noBackupFilesDir, "vad-readback-$campaign.json")
        assertFalse("Preserve previous receipts", destination.exists())
        val expectedApk = checkNotNull(args.getString("expectedApkSha256"))
        assertEquals(expectedApk, fileDigest(File(app.applicationInfo.sourceDir)))
        val activity = AtomicReference<MainActivity>()
        val callbacks =
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(current: Activity) {
                    if (current is MainActivity) activity.set(current)
                }

                override fun onActivityCreated(current: Activity, state: Bundle?) = Unit

                override fun onActivityStarted(current: Activity) = Unit

                override fun onActivityPaused(current: Activity) = Unit

                override fun onActivityStopped(current: Activity) = Unit

                override fun onActivitySaveInstanceState(current: Activity, state: Bundle) = Unit

                override fun onActivityDestroyed(current: Activity) {
                    if (current is MainActivity) activity.compareAndSet(current, null)
                }
            }
        onMain { app.registerActivityLifecycleCallbacks(callbacks) }
        try {
            assertEquals(RecordingPhase.PREFLIGHT, app.recording.state.value.recording.phase)
            signal("OBSERVER_READY")
            awaitPhysical(120) {
                app.recording.state.value.recording.phase == RecordingPhase.RECORDING
            }
            val bound = onControl(app) { checkNotNull(currentSession(app)) }
            val identity = bound.identity
            signal("SOURCE_BOUND")
            awaitPhysical(960) {
                onControl(app) {
                    val live = currentSession(app)
                    check(live == null || live === bound) { "Recording owner changed" }
                    check(bound.state.phase != RecordingPhase.INTERRUPTED) {
                        "Campaign interrupted"
                    }
                    bound.state.phase == RecordingPhase.SAVED
                }
            }
            val finalFrames = onControl(app) { bound.state.frames }
            assertTrue(finalFrames > 9_600_000)
            val session = openExisting(checkNotNull(activity.get()), app)
            val receipt =
                verifyReadback(session, identity, finalFrames)
                    .put("campaignId", campaign)
                    .put("installedApkSha256", expectedApk)
                    .put(
                        "sourceIdentitySha256",
                        digest(
                            listOf(
                                    identity.recordingId.value,
                                    identity.assetId.value,
                                    identity.sessionId,
                                )
                                .joinToString("/")
                        ),
                    )
            destination.writeText(receipt.toString())
            signal("COMPLETE_CONTIGUOUS")
            onMain { app.audioRuntime.lock() }
        } finally {
            onMain { app.unregisterActivityLifecycleCallbacks(callbacks) }
        }
    }

    @Suppress("LongMethod") // Exact source topology and encrypted readback checked in one scope.
    private fun verifyReadback(
        session: ProductAudioSession,
        identity: AudioIdentity,
        finalFrames: Long,
    ): JSONObject {
        val rows = mutableListOf<SegmentationMetadata>()
        var after = ""
        do {
            val page = (session.reader.segmentation(identity, after) as AudioResult.Value).value
            rows += page
            if (page.isEmpty()) break
            after = page.last().key
            assertTrue("Bounded campaign metadata", rows.size < 256)
        } while (true)
        val opens =
            rows.filter { it.kind == SegmentationKind.TECHNICAL_OPEN }.sortedBy { it.firstFrame }
        val closes =
            rows.filter { it.kind == SegmentationKind.TECHNICAL_CLOSE }.sortedBy { it.firstFrame }
        assertEquals(3, opens.size)
        assertEquals(3, closes.size)
        assertEquals(listOf("CAP", "PAUSE", "STOP"), closes.map { it.reason })
        assertEquals(0L, opens.first().firstFrame)
        assertEquals(9_600_000L, closes.first().endFrame)
        assertEquals(9_600_000L, opens[1].firstFrame)
        assertEquals(9_568_000L, opens[1].overlapFirstFrame)
        assertEquals(opens[0].captureEpochId, opens[1].captureEpochId)
        assertNotEquals(opens[1].captureEpochId, opens[2].captureEpochId)
        assertNull(opens[2].overlapFirstFrame)
        opens.zip(closes).forEach { (open, close) ->
            assertEquals(open.segmentId, close.segmentId)
            assertEquals(open.firstFrame, close.firstFrame)
            assertTrue(close.endFrame - close.firstFrame <= 9_600_000)
        }
        closes.zipWithNext().forEach { (a, b) -> assertEquals(a.endFrame, b.firstFrame) }
        rows.forEach { assertEquals(SegmentationProfile.FROZEN.sha256, it.profileSha256) }
        var frames = 0L
        val read =
            session.reader.extract(identity) { first, pcm ->
                assertEquals(frames, first)
                assertTrue(pcm.isNotEmpty() && pcm.size % 2 == 0)
                frames += pcm.size / 2
                // Borrowed PCM is erased by the reader, never copied, hashed or logged here.
            }
        assertTrue(read is AudioResult.Value)
        val summary = (read as AudioResult.Value).value
        assertEquals(AudioCompletion.FINALIZED, summary.completion)
        assertNull(summary.tailFailure)
        assertEquals(finalFrames, frames)
        assertEquals(closes.last().endFrame, frames)
        val receipt =
            JSONObject()
                .put("frames", frames)
                .put("canonicalReadback", "COMPLETE_CONTIGUOUS")
                .put("profileSha256", SegmentationProfile.FROZEN.sha256)
                .put(
                    "technical",
                    JSONArray(
                        closes.map {
                            JSONObject()
                                .put("first", it.firstFrame)
                                .put("end", it.endFrame)
                                .put("reason", it.reason)
                                .put("overlapFirst", it.overlapFirstFrame)
                        }
                    ),
                )
                .put(
                    "semantic",
                    JSONArray(
                        rows
                            .filter { it.kind == SegmentationKind.SEMANTIC_CLOSE }
                            .map {
                                JSONObject()
                                    .put("first", it.firstFrame)
                                    .put("end", it.endFrame)
                                    .put("reason", it.reason)
                                    .put("degraded", it.degraded)
                            }
                    ),
                )
                .put(
                    "coverageGaps",
                    JSONArray(
                        rows
                            .filter { it.kind == SegmentationKind.DEGRADED }
                            .map {
                                JSONObject()
                                    .put("first", it.firstFrame)
                                    .put("end", it.endFrame)
                                    .put("reason", it.reason)
                            }
                    ),
                )
        return receipt
    }

    private fun currentSession(app: DoraApplication): RecordingSession? =
        app.recording.javaClass
            .getDeclaredField("session")
            .apply { isAccessible = true }
            .get(app.recording) as RecordingSession?

    private fun <T> onControl(app: DoraApplication, action: () -> T): T {
        val owner =
            app.recording.javaClass
                .getDeclaredField("worker")
                .apply { isAccessible = true }
                .get(app.recording) as java.util.concurrent.ExecutorService
        return owner.submit(java.util.concurrent.Callable { action() }).get(5, TimeUnit.SECONDS)
    }

    private fun awaitPhysical(seconds: Long, ready: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + seconds * 1000
        while (!ready()) {
            check(android.os.SystemClock.elapsedRealtime() < deadline) {
                "Controlled physical campaign timed out"
            }
            Thread.sleep(
                200
            ) // Physical observation only; deterministic JVM suites use frame events.
        }
    }

    private fun openExisting(
        activity: MainActivity,
        app: DoraApplication,
    ): ProductAudioSession {
        val done = CountDownLatch(1)
        val result = AtomicReference<AudioAvailability>()
        onMain {
            app.audioRuntime.requestOpen(activity, AudioOpenMode.OPEN_EXISTING) {
                result.set(it)
                done.countDown()
            }
        }
        assertTrue("Existing vault open timed out", done.await(30, TimeUnit.SECONDS))
        assertTrue(
            "No authenticated exact-source readback",
            result.get() is AudioAvailability.Available,
        )
        return (result.get() as AudioAvailability.Available).session
    }

    private fun onMain(action: () -> Unit) {
        val done = CountDownLatch(1)
        val error = AtomicReference<Throwable>()
        Handler(Looper.getMainLooper()).post {
            try {
                action()
            } catch (failure: Throwable) {
                error.set(failure)
            } finally {
                done.countDown()
            }
        }
        check(done.await(5, TimeUnit.SECONDS)) { "Main thread observer timed out" }
        error.get()?.let { throw it }
    }

    private fun digest(text: String) =
        java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString(
            ""
        ) {
            "%02x".format(it)
        }

    private fun fileDigest(file: File): String {
        val hash = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    private fun signal(value: String) =
        InstrumentationRegistry.getInstrumentation()
            .sendStatus(2, Bundle().apply { putString("doraVadReadback", value) })
}
