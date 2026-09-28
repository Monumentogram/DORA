package com.monumentogram.dora.stage0.ownedcorpus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** All fixtures are synthetic in an isolated cache subdirectory. No microphone API is invoked. */
@RunWith(AndroidJUnit4::class)
class PrivateCorpusTest {
    private lateinit var sandbox: File
    private lateinit var store: CorpusStore
    private val id = "en-read-01"

    @Before
    fun prepare() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        sandbox = File(context.cacheDir, "owned-test-${UUID.randomUUID()}").apply { mkdirs() }
        store = CorpusStore(File(sandbox, "store"))
    }

    @After
    fun cleanSyntheticFixtures() {
        sandbox.deleteRecursively()
    }

    private fun wav(seconds: Int): ByteArray =
        Wav.header(seconds * Wav.RATE) + ByteArray(seconds * Wav.RATE * 2)

    private fun seed(
        withAudio: Boolean = false,
        enabled: Boolean = false,
        extra: Boolean = false,
    ): File {
        val items = JSONArray()
        val audio = wav(20)
        CorpusStore.IDS.sorted().forEach { case ->
            val parts = case.split('-')
            val item =
                JSONObject()
                    .put("id", case)
                    .put("language", parts[0])
                    .put("speech_class", parts[1].uppercase())
                    .put("material", "Synthetic test material. No private speech.")
                    .put("condition", "CLEAN")
                    .put("recording", JSONObject.NULL)
                    .put("reference", JSONObject.NULL)
            if (withAudio && case == id)
                item.put(
                    "recording",
                    JSONObject()
                        .put("audio_path", "audio/$case.wav")
                        .put("audio_sha256", Wav.sha256(audio))
                        .put("duration_us", 20_000_000L),
                )
            items.put(item)
        }
        val data =
            JSONObject()
                .put("schema_version", "1.0")
                .put("app", CorpusStore.APP)
                .put("protocol_version", "dora-owned-reduced8-v2")
                .put("seed_id", "1".repeat(32))
                .put("inventory_sha256", "2".repeat(64))
                .put("protocol_overlay_sha256", "3".repeat(64))
                .put("recording_enabled", enabled)
                .put(
                    "attestation",
                    JSONObject()
                        .put("version", "dora-owned-corpus-attestation-v1")
                        .put("sha256", Wav.sha256(CorpusStore.ATTESTATION.toByteArray()))
                        .put("text", CorpusStore.ATTESTATION)
                        .put("confirmed", true),
                )
                .put("items", items)
        return writeSeedArchive(data, audio, withAudio, extra)
    }

    private fun writeSeedArchive(
        data: JSONObject,
        audio: ByteArray,
        withAudio: Boolean,
        extra: Boolean,
    ): File {
        return File(sandbox, "seed-${UUID.randomUUID()}.zip").also { archive ->
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("seed.json"))
                zip.write(data.toString().toByteArray())
                zip.closeEntry()
                if (withAudio) {
                    zip.putNextEntry(ZipEntry("audio/$id.wav"))
                    zip.write(audio)
                    zip.closeEntry()
                }
                if (extra) {
                    zip.putNextEntry(ZipEntry("audio/ru-read-02.wav"))
                    zip.write(audio)
                    zip.closeEntry()
                }
            }
        }
    }

    private fun activate(wrong: Boolean = false) {
        val seed = store.seed()
        val activation =
            JSONObject()
                .put("schema_version", "1.0")
                .put("app", CorpusStore.APP)
                .put("recording_enabled", true)
                .put(
                    "seed_sha256",
                    if (wrong) "0".repeat(64) else store.state().getString("seed_sha256"),
                )
        listOf("seed_id", "inventory_sha256", "protocol_overlay_sha256").forEach {
            activation.put(it, seed.getString(it))
        }
        val file = File(sandbox, "activation.json").apply { writeText(activation.toString()) }
        store.activate(file)
    }

    @Test
    fun deferredSeedCannotRecordAndActivationIsExactlyBound() {
        store.importSeed(seed())
        assertFalse(store.recordingEnabled())
        assertThrows(IllegalArgumentException::class.java) { store.beginAttempt(id) }
        assertThrows(IllegalArgumentException::class.java) { activate(wrong = true) }
        assertFalse(store.recordingEnabled())
        activate()
        assertTrue(store.recordingEnabled())
        val attempt = store.beginAttempt(id)
        attempt.file.writeBytes(wav(1))
        assertFalse(store.completeAttempt(attempt, "INTERRUPTED_BACKGROUND"))
        assertEquals(1, store.state().getJSONArray("rejected").length())
    }

    @Test
    fun enabledSeedAndRejectedArchiveNeverInstallOrPoisonAudio() {
        assertThrows(IllegalArgumentException::class.java) {
            store.importSeed(seed(enabled = true))
        }
        assertFalse(store.installed())
        assertThrows(IllegalArgumentException::class.java) {
            store.importSeed(seed(withAudio = true, extra = true))
        }
        assertFalse(File(sandbox, "store/audio/$id.wav").exists())
        store.importSeed(seed(withAudio = true))
        assertTrue(store.audio(id).isFile)
    }

    @Test
    fun humanCorrectionsInvalidateGoldAndSeedReimportPreservesHistory() {
        val seed = seed(withAudio = true)
        store.importSeed(seed)
        assertThrows(IllegalArgumentException::class.java) {
            store.verifyReference(id, "first actual text", false)
        }
        store.verifyReference(id, "first actual text", true)
        store.saveDraft(id, "corrected actual text")
        val pending = store.state().getJSONObject("records").getJSONObject(id)
        assertFalse(pending.getBoolean("human_verified_reference"))
        assertEquals(1, pending.getJSONArray("reference_history").length())
        store.importSeed(seed)
        assertEquals(
            "corrected actual text",
            store.state().getJSONObject("records").getJSONObject(id).getString("draft_text"),
        )
        store.verifyReference(id, "corrected actual text", true)
        val export = store.exportArchive(File(sandbox, "export/mobile-export.zip"))
        ZipFile(export).use { zip ->
            assertEquals(ZipEntry.STORED, zip.getEntry("audio/$id.wav").method)
            val manifest =
                JSONObject(
                    zip.getInputStream(zip.getEntry("export.json")).bufferedReader().readText()
                )
            val record = manifest.getJSONArray("records").getJSONObject(0)
            assertEquals(1L, manifest.getLong("export_revision"))
            assertTrue(record.getBoolean("human_verified_reference"))
            assertEquals("corrected actual text", record.getString("reference_text"))
            assertEquals(
                "first actual text",
                record.getJSONArray("reference_revisions").getJSONObject(0).getString("text"),
            )
            val audit = zip.getInputStream(zip.getEntry("audit.json")).readBytes()
            assertEquals(manifest.getString("audit_sha256"), Wav.sha256(audit))
        }
        store.exportArchive(export)
        assertEquals(2L, store.state().getLong("export_revision"))
    }

    @Test
    fun interruptedProcessPreservesPartialAndOnlyExplicitTechnicalRetryIsAllowed() {
        store.importSeed(seed())
        activate()
        val first = store.beginAttempt(id)
        first.file.writeBytes(wav(1))
        store = CorpusStore(File(sandbox, "store"))
        assertEquals(
            "INTERRUPTED_PROCESS",
            store.state().getJSONArray("rejected").getJSONObject(0).getString("reason"),
        )
        assertTrue(first.file.isFile)
        val retry = store.beginAttempt(id)
        retry.file.writeBytes(wav(20))
        assertTrue(store.completeAttempt(retry, null))
        assertThrows(IllegalArgumentException::class.java) { store.beginAttempt(id) }
        assertEquals(1, store.state().getJSONArray("rejected").length())
        assertEquals(20_000_000L, Wav.inspect(store.audio(id).readBytes()))
    }

    @Test
    fun activitySessionReuseNeverRecoversAnotherLiveAttempt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(sandbox, "session-store")
        val first = OwnedSession.obtain(context, root)
        store = first.store
        store.importSeed(seed())
        activate()
        val attempt = store.beginAttempt(id)
        attempt.file.writeBytes(wav(1))
        val duringCapture = attempt.file.readBytes()
        val recreated = OwnedSession.obtain(context, root)
        assertSame(first.store, recreated.store)
        assertSame(first.capture, recreated.capture)
        assertTrue(duringCapture.contentEquals(attempt.file.readBytes()))
        assertFalse(recreated.store.state().isNull("pending_attempt"))
        assertEquals(0, recreated.store.state().getJSONArray("rejected").length())
        store.completeAttempt(attempt, "INTERRUPTED_BACKGROUND")
        assertEquals(1, recreated.store.state().getJSONArray("rejected").length())
    }

    @Test
    fun deferredActivityDoesNotAskForMicrophoneOrExposeNetworkPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val permissions =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                .orEmpty()
                .toSet()
        assertEquals(setOf(Manifest.permission.RECORD_AUDIO), permissions)
        val activity =
            instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ) as MainActivity
        try {
            instrumentation.runOnMainSync {
                val record = activity.window.decorView.findViewWithTag<Button>("record")
                assertNotNull(record)
                assertFalse(record.isEnabled)
                assertEquals(
                    PackageManager.PERMISSION_DENIED,
                    activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO),
                )
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test
    fun optionalCrossLanguageExportUsesOnlyExplicitSyntheticSeed() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("crossSeed") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = File(context.filesDir, "cross-inbox/seed.zip")
        val activation = File(context.filesDir, "cross-inbox/activation.json")
        val crossStore = CorpusStore(File(context.filesDir, "cross-test/${UUID.randomUUID()}"))
        crossStore.importSeed(archive)
        val items = crossStore.seed().getJSONArray("items")
        for (i in 0 until items.length()) {
            require(items.getJSONObject(i).getString("material").contains("SYNTHETIC")) {
                "CROSS_TEST_REQUIRES_SYNTHETIC_MATERIAL"
            }
        }
        crossStore.activate(activation)
        for ((index, case) in CorpusStore.IDS.sorted().withIndex()) {
            val attempt = crossStore.beginAttempt(case)
            val pcm = ByteArray(20 * Wav.RATE * 2)
            for (frame in 0 until 20 * Wav.RATE) pcm[frame * 2] = (index + 1).toByte()
            attempt.file.writeBytes(Wav.header(20 * Wav.RATE) + pcm)
            assertTrue(crossStore.completeAttempt(attempt, null))
            crossStore.verifyReference(
                case,
                "SYNTHETIC verified reference for case $case only",
                true,
            )
        }
        val result = crossStore.exportArchive(File(context.filesDir, "cross-export.zip"))
        assertTrue(result.length() > 44)
        assertEquals(8, crossStore.state().getJSONObject("records").length())
    }
}
