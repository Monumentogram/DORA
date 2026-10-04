package com.monumentogram.dora

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.logical.LogicalRecordingResult
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.audio.recording.RecordingRecovery
import com.monumentogram.dora.audio.recording.RecordingSession
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Explicit local observer. Capture/Resume/Stop only via host-driven visible UI. No PCM export. */
class LogicalRecoveryProductReadbackTest {
    @Suppress("LongMethod") // One bounded campaign, paired host activity and observer cleanup.
    @Test
    fun observeControlledRecovery() {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("doraRecoveryCampaign")
        org.junit.Assume.assumeTrue(phase in setOf("capture", "resume", "read", "final-read"))
        val campaign = checkNotNull(args.getString("campaignId"))
        require(campaign.matches(Regex("[a-z0-9-]{1,64}")))
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as DoraApplication
        assertEquals("com.monumentogram.dora.debug", app.packageName)
        val expectedApk = checkNotNull(args.getString("expectedApkSha256"))
        assertEquals(expectedApk, fileDigest(File(app.applicationInfo.sourceDir)))
        val destination = File(app.noBackupFilesDir, "recovery-$campaign.json")
        assertFalse(destination.exists())
        withHostActivity(app) { activity ->
            assertEquals(RecordingPhase.PREFLIGHT, app.recording.state.value.recording.phase)
            val expectedIdentity = args.getString("sourceIdentitySha256")
            if (phase != "capture") {
                val session = openExisting(app, activity)
                val identity =
                    existingIdentities(session).single { identityHash(it) == expectedIdentity }
                val snapshot = exactRecovery(session, identity)
                val receipt =
                    recoveryReceipt(snapshot)
                        .put("identityFingerprint", expectedIdentity)
                        .put("logicalOrigin", logicalOrigin(session, identity))
                        .put("pid", android.os.Process.myPid())
                destination.writeText(receipt.toString())
                checkpoint("RECOVERED")
                assertEquals(RecordingPhase.PREFLIGHT, app.recording.state.value.recording.phase)
                if (phase == "read") return@withHostActivity
                if (phase == "final-read") {
                    assertFalse(snapshot.canResume)
                    verifyFinal(session, identity, snapshot.recoveredFrames, destination)
                    return@withHostActivity
                }
                assertTrue(snapshot.canResume)
            }
            checkpoint("WAITING_EXPLICIT_CAPTURE")
            awaitPhysical(900) {
                app.recording.state.value.recording.phase == RecordingPhase.RECORDING
            }
            val bound = onControl(app) { checkNotNull(currentSession(app)) }
            val identity = bound.identity
            if (expectedIdentity != null) assertEquals(expectedIdentity, identityHash(identity))
            checkpoint("SOURCE_BOUND")
            val finish = args.getString("finish") == "true"
            awaitPhysical(300) {
                val state = onControl(app) { bound.state }
                destination.writeText(
                    JSONObject()
                        .put("identityFingerprint", identityHash(identity))
                        .put("sampleAssembledFrames", state.frames)
                        .put("sampleDurableFrames", state.durableFrames)
                        .put("sampleUptimeMillis", android.os.SystemClock.elapsedRealtime())
                        .put("exactTailAtKill", false)
                        .put("phase", state.phase.name)
                        .put("pid", android.os.Process.myPid())
                        .put("diagnostics", app.recording.diagnosticSummary())
                        .toString()
                )
                finish && state.phase == RecordingPhase.SAVED
            }
            verifyFinal(
                openExisting(app, activity),
                identity,
                onControl(app) { bound.state.frames },
                destination,
            )
        }
    }

    private fun openExisting(app: DoraApplication, activity: MainActivity): ProductAudioSession {
        val opened = AtomicReference<AudioAvailability>()
        val done = CountDownLatch(1)
        onMain {
            app.audioRuntime.requestOpen(activity, AudioOpenMode.OPEN_EXISTING) {
                opened.set(it)
                done.countDown()
            }
        }
        check(done.await(30, TimeUnit.SECONDS))
        // An automatic UI discovery may supersede this observer's open request.
        // Opening is an observation of the newer request, never a usable session.
        if (opened.get() == AudioAvailability.Opening) {
            awaitPhysical(30) {
                onMain { opened.set(app.audioRuntime.availability) }
                opened.get() != AudioAvailability.Opening
            }
        }
        val available = opened.get()
        check(available is AudioAvailability.Available) {
            "Existing vault unavailable: $available"
        }
        return available.session
    }

    @Suppress("LongMethod") // Keep final-source identity and exact mapping assertions together.
    private fun verifyFinal(
        session: ProductAudioSession,
        identity: AudioIdentity,
        expectedFrames: Long,
        destination: File,
    ) {
        val source =
            ((session.originals.acquire(identity) as AudioResult.Value).value
                    as OriginalAudioStatus.Available)
                .reference
        val logical =
            ((session.logicalRecordings.read(source) as AudioResult.Value).value
                    as LogicalRecordingResult.Ready)
                .recording
        assertEquals(expectedFrames, source.frames)
        assertEquals(identity.recordingId, logical.authorizationUnitId.recordingId)
        assertEquals(
            source.frames,
            logical.technicalChunks.sumOf { it.canonicalEndFrame - it.canonicalFirstFrame },
        )
        assertTrue(logical.technicalChunks.all { it.sourceAudioReference == source })
        assertFalse(exactRecovery(session, identity).canResume)
        destination.writeText(
            JSONObject()
                .put("identityFingerprint", identityHash(identity))
                .put("frames", source.frames)
                .put(
                    "sourceFingerprint",
                    digest(
                        listOf(
                                identityHash(identity),
                                source.version.toString(),
                                source.digest,
                                source.frames.toString(),
                            )
                            .joinToString("/")
                    ),
                )
                .put("version", source.version)
                .put("finalized", true)
                .put("authorizationUnits", 1)
                .put("logicalOrigin", logicalOrigin(session, identity))
                .put(
                    "chunks",
                    JSONArray(
                        logical.technicalChunks.map {
                            JSONObject()
                                .put("idFingerprint", digest(it.chunkId))
                                .put("first", it.canonicalFirstFrame)
                                .put("end", it.canonicalEndFrame)
                                .put("open", it.openReason)
                                .put("close", it.closeReason)
                        }
                    ),
                )
                .put("pid", android.os.Process.myPid())
                .toString()
        )
        checkpoint("FINAL_COHERENT_SOURCE")
    }

    private fun logicalOrigin(session: ProductAudioSession, identity: AudioIdentity): Boolean {
        val rows = (session.reader.segmentation(identity) as AudioResult.Value).value
        val origin = rows.singleOrNull { it.kind == SegmentationKind.RECORDING_ORIGIN }
        if (origin != null) {
            origin.validate(0)
            assertEquals(identity.sessionId, origin.segmentId)
        }
        if (InstrumentationRegistry.getArguments().getString("requireLogicalOrigin") == "true")
            assertTrue(origin != null)
        return origin != null
    }

    private fun recoveryReceipt(snapshot: RecordingRecovery) =
        JSONObject()
            .put("recoveredFrames", snapshot.recoveredFrames)
            .put("canResume", snapshot.canResume)
            .put("completion", snapshot.completionState.name)
            .put("technical", snapshot.technicalMetadataState.name)
            .put("semantic", snapshot.semanticMetadataState.name)
            .put("tailFailure", snapshot.tailFailure?.name)

    private fun exactRecovery(
        session: ProductAudioSession,
        identity: AudioIdentity,
    ): RecordingRecovery {
        val vault =
            session.javaClass.getDeclaredField("vault").apply { isAccessible = true }.get(session)
        val method =
            vault.javaClass
                .getDeclaredMethod("recordingRecovery", AudioIdentity::class.java)
                .apply { isAccessible = true }
        return (method.invoke(vault, identity) as AudioResult.Value<*>).value as RecordingRecovery
    }

    /** Same bounded host-launch observer used by the accepted physical campaign. */
    private fun withHostActivity(app: DoraApplication, action: (MainActivity) -> Unit) {
        val activity = AtomicReference<MainActivity>()
        val resumed = CountDownLatch(1)
        val callbacks =
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(current: Activity) {
                    if (current is MainActivity) {
                        activity.set(current)
                        resumed.countDown()
                    }
                }

                override fun onActivityCreated(current: Activity, state: Bundle?) = Unit

                override fun onActivityStarted(current: Activity) = Unit

                override fun onActivityPaused(current: Activity) = Unit

                override fun onActivityStopped(current: Activity) = Unit

                override fun onActivitySaveInstanceState(current: Activity, state: Bundle) = Unit

                override fun onActivityDestroyed(current: Activity) = Unit
            }
        onMain { app.registerActivityLifecycleCallbacks(callbacks) }
        try {
            checkpoint("HOST_LAUNCH_READY")
            check(resumed.await(30, TimeUnit.SECONDS)) { "Host activity launch timed out" }
            action(checkNotNull(activity.get()))
        } finally {
            onMain {
                app.audioRuntime.lock()
                app.unregisterActivityLifecycleCallbacks(callbacks)
            }
        }
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
        check(done.await(5, TimeUnit.SECONDS)) { "Main-thread observer timed out" }
        error.get()?.let { throw it }
    }

    /**
     * Test-only bounded encrypted catalog discovery after real runtime authentication; no Recovery
     * mutation.
     */
    @Suppress("UNCHECKED_CAST")
    private fun existingIdentities(session: ProductAudioSession): List<AudioIdentity> {
        val vault =
            session.javaClass.getDeclaredField("vault").apply { isAccessible = true }.get(session)
        val journal =
            vault.javaClass.getDeclaredField("journal").apply { isAccessible = true }.get(vault)
        val method =
            journal.javaClass.getDeclaredMethod("recordingCandidates", String::class.java).apply {
                isAccessible = true
            }
        val found = mutableListOf<AudioIdentity>()
        var after = ""
        repeat(50) {
            val page = method.invoke(journal, after) as List<AudioIdentity>
            if (page.isEmpty()) return found
            check(page.all { it.assetId.value > after })
            found += page
            after = page.last().assetId.value
        }
        error("Bounded catalog discovery exceeded")
    }

    private fun checkpoint(stage: String) {
        InstrumentationRegistry.getInstrumentation()
            .sendStatus(
                2,
                Bundle().apply {
                    putString("doraRecoveryCheckpoint", stage)
                },
            )
    }

    private fun digest(value: String) =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    private fun identityHash(identity: AudioIdentity) =
        digest(
            listOf(identity.recordingId.value, identity.assetId.value, identity.sessionId)
                .joinToString("/")
        )

    private fun fileDigest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var count = stream.read(buffer)
            while (count >= 0) {
                hash.update(buffer, 0, count)
                count = stream.read(buffer)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
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
}
