package com.monumentogram.dora

import android.app.KeyguardManager
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.security.keystore.KeyInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.recording.RecordingRecovery
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit local before/after opt-in only. Runs in the existing target UID, never creates a vault,
 * changes credentials/markers, records audio, exports keys or retains borrowed PCM. Receipts remain
 * private no-backup data and are not CI artifacts. A failed readback fails the whole attestation.
 */
@RunWith(AndroidJUnit4::class)
class DevelopmentVaultContinuityTest {
    @Test
    fun existingVaultSurvivesOwnerCredentialChange() {
        val phase = InstrumentationRegistry.getArguments().getString("doraVaultContinuity")
        assumeTrue(phase == "before" || phase == "after")
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as DoraApplication
        assertEquals("com.monumentogram.dora.debug", app.packageName)
        assertTrue(app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        val secure = app.getSystemService(KeyguardManager::class.java).isDeviceSecure
        assertEquals(phase == "before", secure)
        assertEquals(AudioOpenMode.OPEN_EXISTING, app.audioRuntime.recordingOpenMode())
        val destination = File(app.noBackupFilesDir, "development-vault-attestation-$phase.json")
        assertFalse("Never replace an earlier attestation", destination.exists())
        val keysBefore = keys()
        val recordings = JSONArray()
        var selectedCursor = ""
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            val selected = select(activity, app, phase!!)
            selectedCursor = selected.first
            val session = (app.audioRuntime.availability as? AudioAvailability.Available)?.session
            assertTrue("No current authorized read session", session != null)
            for (entry in selected.second) {
                recordings.put(readback(session!!, entry))
                progress("readback_complete", recordings.length())
            }
            activity.onActivity { app.audioRuntime.lock() }
        }
        assertTrue("An existing recording is required", recordings.length() > 0)
        assertEquals(
            "Opening must not replace/create persistent keys",
            keysBefore.toString(),
            keys().toString(),
        )
        val receipt =
            JSONObject()
                .put("version", 1)
                .put("phase", phase)
                .put("deviceSecure", secure)
                .put("scope", "selected-existing-recordings")
                .put("exhaustiveReadback", false)
                .put("discoveryAfter", selectedCursor)
                .put("keys", keysBefore)
                .put("recordings", recordings)
        if (phase == "after") compareBaseline(app, receipt)
        destination.writeText(receipt.toString())
        progress("attestation_complete", recordings.length())
    }

    private fun select(
        activity: ActivityScenario<MainActivity>,
        app: DoraApplication,
        phase: String,
    ): Pair<String, List<RecordingRecovery>> {
        val cursors =
            if (phase == "after") {
                val baseline =
                    JSONObject(
                        File(app.noBackupFilesDir, "development-vault-attestation-before.json")
                            .readText()
                    )
                listOf(baseline.getString("discoveryAfter"))
            } else listOf("f", "e", "c", "8", "")
        for ((index, cursor) in cursors.withIndex()) {
            progress("discovery_start", index)
            val found = page(activity, app, cursor)
            progress("discovery_complete", found.size)
            if (found.isNotEmpty()) return cursor to found
            // Broaden only after SUCCESSFUL EMPTY discovery, never after any failed source/open.
        }
        throw AssertionError("An existing recording is required; no replacement after PIN removal")
    }

    private fun page(
        activity: ActivityScenario<MainActivity>,
        app: DoraApplication,
        cursor: String,
    ): List<RecordingRecovery> {
        val completed = CountDownLatch(1)
        val result = AtomicReference<AudioResult<List<RecordingRecovery>>>()
        activity.onActivity { current ->
            app.audioRuntime.requestRecordingRecovery(current, cursor) {
                result.set(it)
                completed.countDown()
            }
        }
        // Existing multi-recording Recovery/readback is distinct from capture-control latency.
        assertTrue(
            "Authenticated existing-vault discovery timed out",
            completed.await(300, TimeUnit.SECONDS),
        )
        val page = (result.get() as? AudioResult.Value)?.value
        assertTrue("Existing-vault open/read failed", page != null)
        return page!!
    }

    private fun progress(phase: String, count: Int) {
        InstrumentationRegistry.getInstrumentation()
            .sendStatus(
                2,
                Bundle().apply {
                    putString("doraVaultPhase", phase)
                    putInt("completedCount", count)
                },
            )
    }

    private fun compareBaseline(app: DoraApplication, receipt: JSONObject) {
        val baseline =
            JSONObject(
                File(app.noBackupFilesDir, "development-vault-attestation-before.json").readText()
            )
        assertEquals(baseline.getString("discoveryAfter"), receipt.getString("discoveryAfter"))
        for (field in listOf("keys", "recordings")) {
            assertEquals(
                "Existing encrypted $field changed",
                baseline.getJSONArray(field).toString(),
                receipt.getJSONArray(field).toString(),
            )
        }
    }

    private fun readback(session: ProductAudioSession, entry: RecordingRecovery): JSONObject {
        assertTrue(
            "Existing source is not readable",
            entry.failure == null && entry.summary != null,
        )
        var frames = 0L
        val read =
            session.reader.extract(entry.identity) { firstFrame, pcm ->
                assertEquals(frames, firstFrame)
                assertTrue(pcm.isNotEmpty() && pcm.size % 2 == 0)
                frames += pcm.size / 2
                // Library zeroes this borrowed array after return. Never copy/log/hash audio.
            }
        assertTrue("Authenticated source extraction failed", read is AudioResult.Value)
        val summary = (read as AudioResult.Value).value
        assertEquals(entry.summary!!.frames, frames)
        assertEquals(summary.frames, frames)
        assertTrue("Incomplete source extraction", summary.tailFailure == null)
        return JSONObject()
            .put(
                "identitySha256",
                digest(
                    listOf(
                            entry.identity.recordingId.value,
                            entry.identity.assetId.value,
                            entry.identity.sessionId,
                        )
                        .joinToString("/")
                ),
            )
            .put("frames", frames)
            .put("completion", summary.completion.name)
    }

    private fun keys(): JSONArray {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val aliases = store.aliases().toList().filter { it.startsWith("dora.vault.") }.sorted()
        assertTrue("Existing wrapping keys are required", aliases.isNotEmpty())
        return JSONArray().apply {
            for (alias in aliases) {
                val key = store.getKey(alias, null) as SecretKey
                val info =
                    SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
                        .getKeySpec(key, KeyInfo::class.java) as KeyInfo
                assertFalse(
                    "Vault key unexpectedly requires credential authentication",
                    info.isUserAuthenticationRequired,
                )
                assertTrue("Vault key must be non-exportable", key.encoded == null)
                put(
                    JSONObject()
                        .put("aliasSha256", digest(alias))
                        .put("createdMs", store.getCreationDate(alias).time)
                        .put("authenticationRequired", false)
                )
            }
        }
    }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(
            ""
        ) {
            "%02x".format(it)
        }
}
