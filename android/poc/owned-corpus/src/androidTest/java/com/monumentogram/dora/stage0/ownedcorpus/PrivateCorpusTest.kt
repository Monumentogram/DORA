package com.monumentogram.dora.stage0.ownedcorpus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
    fun easyEnglishV3AcceptsExactProfileAndRejectsMismatches() {
        store.importSeed(easyEnglishSeed())
        assertFalse(store.recordingEnabled())
        assertEquals("READ", store.item("en-read-03").getString("speech_class"))
        assertEquals("READ", store.item("en-read-04").getString("speech_class"))
        assertFalse(
            CapturePolicy.eligible(
                46 * Wav.RATE,
                store.item("en-read-04").getString("speech_class"),
            )
        )
        assertEquals(20_000_000L, Wav.inspect(store.audio("en-read-03").readBytes()))
        assertThrows(IllegalArgumentException::class.java) { store.item("en-spontaneous-01") }
        val invalid =
            listOf<(JSONObject) -> Unit>(
                { it.put("protocol_version", "dora-owned-reduced8-v2") },
                { it.getJSONArray("items").getJSONObject(6).put("id", "en-spontaneous-01") },
                { it.getJSONArray("items").getJSONObject(6).put("speech_class", "read") },
                { it.getJSONArray("items").getJSONObject(6).put("language", "ru") },
                { it.getJSONArray("items").getJSONObject(7).put("id", "en-read-03") },
            )
        invalid.forEachIndexed { index, mutation ->
            val rejected = CorpusStore(File(sandbox, "invalid-v3-$index"))
            assertThrows(IllegalArgumentException::class.java) {
                rejected.importSeed(easyEnglishSeed(mutation))
            }
            assertFalse(rejected.installed())
            assertFalse(File(sandbox, "invalid-v3-$index/audio/en-read-03.wav").exists())
        }
    }

    private fun easyEnglishSeed(mutate: (JSONObject) -> Unit = {}): File {
        val data =
            ZipFile(seed()).use { zip ->
                JSONObject(
                    zip.getInputStream(zip.getEntry("seed.json")).bufferedReader().readText()
                )
            }
        val original = data.getJSONArray("items")
        val items = JSONArray()
        for (i in 0 until original.length()) {
            val item = original.getJSONObject(i)
            if (!item.getString("id").startsWith("en-spontaneous")) items.put(item)
        }
        for (index in 3..4) items.put(
            JSONObject()
                .put("id", "en-read-0$index")
                .put("language", "en")
                .put("speech_class", "READ")
                .put("material", "SYNTHETIC easy English material")
                .put("condition", "CLEAN")
                .put("recording", JSONObject.NULL)
                .put("reference", JSONObject.NULL)
        )
        val audio = wav(20)
        items
            .getJSONObject(6)
            .put(
                "recording",
                JSONObject()
                    .put("audio_path", "audio/en-read-03.wav")
                    .put("audio_sha256", Wav.sha256(audio))
                    .put("duration_us", 20_000_000L),
            )
        data.put("protocol_version", "dora-owned-easy-en8-v3").put("items", items)
        mutate(data)
        return File(sandbox, "seed-v3-${UUID.randomUUID()}.zip").also { archive ->
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("seed.json"))
                zip.write(data.toString().toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("audio/en-read-03.wav"))
                zip.write(audio)
                zip.closeEntry()
            }
        }
    }

    @Test
    fun v2ProgressCannotBeReplacedByAV3Seed() {
        store.importSeed(seed(withAudio = true))
        store.verifyReference(id, "SYNTHETIC first verified text", true)
        store.saveDraft(id, "SYNTHETIC corrected text")
        store.verifyReference(id, "SYNTHETIC corrected text", true)
        val before = store.state().toString()
        val original = store.audio(id).readBytes()
        assertThrows(IllegalArgumentException::class.java) { store.importSeed(easyEnglishSeed()) }
        assertEquals(before, store.state().toString())
        assertTrue(original.contentEquals(store.audio(id).readBytes()))
    }

    @Test
    fun v3CaptureRecoveryAndExportUseNewEnglishReadIds() {
        store.importSeed(easyEnglishSeed())
        activate()
        val first = store.beginAttempt("en-read-04")
        first.file.writeBytes(wav(1))
        store = CorpusStore(File(sandbox, "store"))
        assertEquals(
            "INTERRUPTED_PROCESS",
            store.state().getJSONArray("rejected").getJSONObject(0).getString("reason"),
        )
        val retry = store.beginAttempt("en-read-04")
        retry.file.writeBytes(wav(20))
        assertTrue(store.completeAttempt(retry, null))
        for (case in listOf("en-read-03", "en-read-04")) store.verifyReference(
            case,
            "SYNTHETIC actual words",
            true,
        )
        val archive = store.exportArchive(File(sandbox, "v3-export.zip"))
        ZipFile(archive).use { zip ->
            val records =
                JSONObject(
                        zip.getInputStream(zip.getEntry("export.json")).bufferedReader().readText()
                    )
                    .getJSONArray("records")
            assertEquals(
                setOf("en-read-03", "en-read-04"),
                (0 until records.length())
                    .map { records.getJSONObject(it).getString("id") }
                    .toSet(),
            )
            assertNotNull(zip.getEntry("audio/en-read-03.wav"))
            assertNotNull(zip.getEntry("audio/en-read-04.wav"))
        }
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
                val record = activity.window.decorView.findViewWithTag<Button>("primary")
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
    fun primaryActionStaysOutsideLongScrollingTask() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity =
            instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ) as MainActivity
        try {
            instrumentation.runOnMainSync {
                val primary = activity.window.decorView.findViewWithTag<Button>("primary")
                assertNotNull("One persistent primary action is required", primary)
                var ancestor = primary.parent
                while (ancestor is View) {
                    assertFalse("Primary action must not scroll away", ancestor is ScrollView)
                    ancestor = ancestor.parent
                }
                val content =
                    activity.window.decorView.findViewWithTag<LinearLayout>("task_content")
                assertNotNull(content)
                content.addView(
                    TextView(activity).apply { text = "Synthetic long task\n".repeat(100) }
                )
            }
            instrumentation.waitForIdleSync()
            val before = Rect()
            instrumentation.runOnMainSync {
                val primary = activity.window.decorView.findViewWithTag<Button>("primary")
                assertTrue(primary.getGlobalVisibleRect(before))
                assertEquals(primary.height, before.height())
                activity.window.decorView
                    .findViewWithTag<ScrollView>("task_scroll")
                    .fullScroll(View.FOCUS_DOWN)
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val after = Rect()
                activity.window.decorView
                    .findViewWithTag<Button>("primary")
                    .getGlobalVisibleRect(after)
                assertEquals(before, after)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test
    fun optionalGuidedEightTaskFlowRequiresEveryHumanCheck() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("guidedUiSeed") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val session = prepareSyntheticUiSession()
        val activity =
            instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ) as MainActivity
        try {
            instrumentation.runOnMainSync {
                assertTrue(
                    "Startup must apply the bound activation",
                    session.store.recordingEnabled(),
                )
                assertFalse(session.capture.running)
                assertEquals(0, session.capture.frames)
                assertEquals(
                    PackageManager.PERMISSION_DENIED,
                    activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO),
                )
            }
            repeat(8) { index -> completeSyntheticUiTask(activity, session, index) }
            instrumentation.runOnMainSync {
                assertEquals(
                    "Закрыть",
                    activity.window.decorView.findViewWithTag<Button>("primary").text.toString(),
                )
            }
            val records = session.store.state().getJSONObject("records")
            assertEquals(8, records.length())
            session.store.activeIds().forEach { case ->
                assertTrue(records.getJSONObject(case).getBoolean("human_verified_reference"))
                assertEquals(20_000_000L, Wav.inspect(session.store.audio(case).readBytes()))
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun completeSyntheticUiTask(activity: MainActivity, session: OwnedSession, index: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            fillSyntheticUiTask(activity, index)
            activity.window.decorView.findViewWithTag<Button>("more").performClick()
            val records = session.store.state().getJSONObject("records")
            assertTrue(
                session.store.activeIds().any { case ->
                    records.getJSONObject(case).getString("draft_text") ==
                        "SYNTHETIC actual speech $index corrected"
                }
            )
        }
        instrumentation.waitForIdleSync()
        closeServiceDialog()
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertFalse(
                "Closing the service dialog must not finish the Activity",
                activity.isFinishing,
            )
            assertFalse("Activity must still own its export executor", activity.isDestroyed)
            assertTrue(activity.window.decorView.findViewWithTag<Button>("primary").performClick())
            val records = session.store.state().getJSONObject("records")
            val verified =
                session.store.activeIds().count {
                    records.getJSONObject(it).getBoolean("human_verified_reference")
                }
            val status = activity.window.decorView.findViewWithTag<TextView>("status").text
            assertEquals(
                "Task $index failed to verify; UI status: $status",
                index + 1,
                verified,
            )
        }
        instrumentation.waitForIdleSync()
    }

    private fun closeServiceDialog() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + 5_000
        var clicked = false
        while (!clicked && SystemClock.uptimeMillis() < deadline) {
            val close =
                automation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByText("ЗАКРЫТЬ")
                    ?.firstOrNull {
                        it.text?.toString().equals("Закрыть", ignoreCase = true) && it.isClickable
                    }
            clicked = close?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            if (!clicked) SystemClock.sleep(20)
        }
        assertTrue("The active service dialog must expose its Close button", clicked)
    }

    private fun prepareSyntheticUiSession(): OwnedSession {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = OwnedSession.obtain(context, File(context.filesDir, "owned"))
        require(!session.store.installed()) { "UI_TEST_REQUIRES_EMPTY_SYNTHETIC_EMULATOR" }
        val archive = File(context.filesDir, "cross-inbox/seed.zip")
        ZipFile(archive).use { zip ->
            val seed =
                JSONObject(
                    zip.getInputStream(zip.getEntry("seed.json")).bufferedReader().readText()
                )
            val items = seed.getJSONArray("items")
            for (i in 0 until items.length()) {
                require(items.getJSONObject(i).getString("material").contains("SYNTHETIC")) {
                    "UI_TEST_REQUIRES_SYNTHETIC_MATERIAL"
                }
            }
        }
        session.store.importSeed(archive)
        val activation = File(context.filesDir, "cross-inbox/activation.json")
        session.store.activate(activation)
        session.store.activeIds().sorted().forEachIndexed { index, case ->
            val attempt = session.store.beginAttempt(case)
            val pcm = ByteArray(20 * Wav.RATE * 2)
            for (frame in 0 until 20 * Wav.RATE) pcm[frame * 2] = (index + 1).toByte()
            attempt.file.writeBytes(Wav.header(20 * Wav.RATE) + pcm)
            assertTrue(session.store.completeAttempt(attempt, null))
        }
        // Exercise automatic bound activation with saved synthetic takes, without touching the mic.
        val stateFile = File(context.filesDir, "owned/state.json")
        val state = JSONObject(stateFile.readText()).put("recording_enabled", false)
        stateFile.writeText(state.toString())
        File(context.filesDir, "inbox").mkdirs()
        activation.copyTo(File(context.filesDir, "inbox/activation.json"))
        return session
    }

    private fun fillSyntheticUiTask(activity: MainActivity, index: Int) {
        val root = activity.window.decorView
        val reference = root.findViewWithTag<EditText>("reference")
        val check = root.findViewWithTag<CheckBox>("human_confirmation")
        val primary = root.findViewWithTag<Button>("primary")
        assertNotNull(reference)
        assertFalse(check.isChecked)
        assertFalse(primary.isEnabled)
        reference.setText("SYNTHETIC actual speech $index")
        check.isChecked = true
        assertTrue(primary.isEnabled)
        reference.append(" corrected")
        assertFalse(check.isChecked)
        assertFalse(primary.isEnabled)
        check.isChecked = true
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
        for ((index, case) in crossStore.activeIds().sorted().withIndex()) {
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
