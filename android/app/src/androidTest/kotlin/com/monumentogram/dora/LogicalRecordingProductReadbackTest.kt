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
import com.monumentogram.dora.audio.logical.LogicalRecordingResult
import com.monumentogram.dora.audio.logical.RecordingAuthorization
import com.monumentogram.dora.audio.logical.RecordingConsentMode
import com.monumentogram.dora.audio.logical.SourceFrameTime
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Read-only, explicit host opt-in. Never reconciles, records, deletes, exports or substitutes
 * audio.
 */
class LogicalRecordingProductReadbackTest {
    @Suppress("LongMethod") // One bounded authenticated source and receipt, with paired cleanup.
    @Test
    fun readExactHistoricalSourceAfterRestart() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("doraLogicalReadback") == "exact-existing-source")
        checkpoint("ENTERED")
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as DoraApplication
        assertEquals("com.monumentogram.dora.debug", app.packageName)
        assertEquals(
            checkNotNull(args.getString("expectedApkSha256")),
            fileDigest(File(app.applicationInfo.sourceDir)),
        )
        checkpoint("APK_VERIFIED")
        val expectedIdentity = checkNotNull(args.getString("sourceIdentitySha256"))
        require(expectedIdentity.matches(Regex("[0-9a-f]{64}")))
        withHostActivity(app) { activity ->
            checkpoint("ACTIVITY_LAUNCHED")
            val opened = AtomicReference<AudioAvailability>()
            val done = CountDownLatch(1)
            onMain {
                app.audioRuntime.requestOpen(activity, AudioOpenMode.OPEN_EXISTING) {
                    opened.set(it)
                    done.countDown()
                }
            }
            assertTrue(done.await(30, TimeUnit.SECONDS))
            checkpoint("OPEN_CALLBACK")
            val session = (opened.get() as AudioAvailability.Available).session
            val identity =
                existingIdentities(session).single { identityHash(it) == expectedIdentity }
            checkpoint("IDENTITY_FOUND")
            val source =
                ((session.originals.acquire(identity) as AudioResult.Value).value
                        as OriginalAudioStatus.Available)
                    .reference
            checkpoint("SOURCE_ACQUIRED")
            val recording =
                ((session.logicalRecordings.read(source) as AudioResult.Value).value
                        as LogicalRecordingResult.Ready)
                    .recording
            checkpoint("PROJECTION_READ")
            assertEquals(10603200L, source.frames)
            assertEquals(source, recording.originalAudioReference)
            assertEquals(identity.recordingId, recording.authorizationUnitId.recordingId)
            assertEquals(
                listOf(0L, 9600000L, 10004800L),
                recording.technicalChunks.map { it.canonicalFirstFrame },
            )
            assertEquals(
                listOf(9600000L, 10004800L, 10603200L),
                recording.technicalChunks.map { it.canonicalEndFrame },
            )
            assertEquals(9568000L, recording.technicalChunks[1].originalFrame(0))
            assertEquals(2, recording.lookup(9599999)!!.processingViews.size)
            assertEquals(
                600000000000L,
                SourceFrameTime.toNanos(recording.technicalChunks[1].canonicalFirstFrame),
            )
            assertTrue(recording.technicalChunks.all { it.sourceAudioReference == source })
            var consent =
                RecordingAuthorization(
                    recording.recordingId,
                    RecordingConsentMode.ASK_EACH_RECORDING,
                )
            var prompts = 0
            recording.technicalChunks.forEach {
                val next = consent.automaticOpportunity()
                if (next.prompt != null) prompts++
                consent = next.state
            }
            assertEquals(1, prompts)
            val fingerprint =
                digest(
                    listOf(
                            source.version.toString(),
                            identity.recordingId.value,
                            identity.assetId.value,
                            identity.sessionId,
                            source.digest,
                            source.frames.toString(),
                        )
                        .joinToString("/")
                )
            val chunks =
                JSONArray(
                    recording.technicalChunks.map { chunk ->
                        JSONObject()
                            .put("idFingerprint", digest(chunk.chunkId))
                            .put("first", chunk.canonicalFirstFrame)
                            .put("end", chunk.canonicalEndFrame)
                            .put("processingFirst", chunk.processingFirstFrame)
                            .put("epochFingerprint", digest(chunk.captureEpochId))
                            .put("open", chunk.openReason)
                            .put("close", chunk.closeReason)
                    }
                )
            val receipt =
                JSONObject()
                    .put("frames", source.frames)
                    .put("version", source.version)
                    .put("sourceFingerprint", fingerprint)
                    .put("identityFingerprint", expectedIdentity)
                    .put("chunks", chunks)
                    .put("semanticCount", recording.semanticSegments.size)
                    .put("authorizationUnits", 1)
                    .put("askOpportunities", prompts)
                    .put("profileSha256", recording.technicalChunks.first().profileSha256)
                    .put("pid", android.os.Process.myPid())
            InstrumentationRegistry.getInstrumentation()
                .sendStatus(
                    2,
                    Bundle().apply { putString("doraLogicalReadback", receipt.toString()) },
                )
            onMain { app.audioRuntime.lock() }
        }
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
                    putString("doraLogicalCheckpoint", stage)
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
}
