@file:Suppress("MagicNumber") // Bounded WAV fixture and explicitly gated live acceptance.

package com.monumentogram.dora.transcript

import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.transcript.CloudConsent
import com.monumentogram.dora.audio.transcript.TranscriptDocument
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.ui.theme.DoraBootstrapTheme
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Owner-invoked live acceptance only. Default CI skips; no endpoint or audio is bundled. */
class CloudLiveAcceptanceTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun prepareInvitation() {
        val endpoint = requireLive()
        val installation = CloudIdentity(context)
        installation.setEndpoint(endpoint)
        context.filesDir.resolve("live-invitation.json").writeText(installation.publicInvitation())
    }

    @Test fun recognizeRussian() = recognize("ru")

    @Test fun recognizeEnglish() = recognize("en")

    @Test
    fun verifyLiveConnection() {
        val endpoint = requireLive()
        val receipt = JSONObject()
        probeConnection("backend", "$endpoint/v1/session", receipt)
        probeConnection("egress", "https://checkip.amazonaws.com", receipt)
        context.filesDir.resolve("live-connection.json").writeText(receipt.toString())
        assertEquals(401, receipt.optInt("backend_status"))
    }

    private fun probeConnection(label: String, address: String, receipt: JSONObject) {
        val connection = java.net.URL(address).openConnection() as javax.net.ssl.HttpsURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.instanceFollowRedirects = false
        var phase = "connect"
        try {
            if (label == "backend") {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(2)
                connection.setRequestProperty("Content-Type", "application/json")
                phase = "write"
                connection.outputStream.use { it.write("{}".toByteArray()) }
            }
            phase = "response"
            receipt.put(label + "_status", connection.responseCode)
            if (label == "egress")
                receipt.put(
                    "egress",
                    connection.inputStream.bufferedReader().use { it.readLine().take(64).trim() },
                )
        } catch (failure: java.io.IOException) {
            receipt.put(label + "_error", failure.javaClass.simpleName)
            receipt.put(label + "_phase", phase)
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun completePreparedRussianUiAction() {
        val endpoint = requireLive()
        val receipt = read("ru")
        assertEquals("PREPARED", receipt.getString("status"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val session = open(scenario)
            val identity = identity(receipt)
            val before = value(session.transcripts.load(identity))!!
            assertTrue(before.versions.size in 1..2)
            val pending = before.jobs.singleOrNull { it.consent != null }
            if (pending != null) {
                assertFalse(
                    "Cancelled actions require operator reconciliation",
                    pending.consent!!.revoked,
                )
                assertTrue(pending.status != "CANCELLED")
                assertEquals(endpoint, pending.consent!!.destination)
                context.filesDir
                    .resolve("live-ru.json")
                    .writeText(receipt.put("action", pending.id).toString())
            }
            val done =
                if (before.versions.size == 2) before
                else manualCloud(scenario, identity, "ru", pending != null)
            assertEquals(before.versions.take(1), done.versions.take(1))
            assertEquals(before.edits, done.edits)
            assertEquals(before.activeId, done.activeId)
            val result = done.versions.last().cloud!!
            assertEquals("amazon-transcribe", result.providerId)
            assertEquals(before.source.digest, result.sourceDigest)
            assertEquals(before.source.version, result.sourceVersion)
            assertTrue(result.text.isNotBlank())
            val installation = CloudIdentity(context)
            val api = CloudHttpClient(endpoint, installation.installationId, installation::sign)
            val action = done.versions.last().id
            ui.waitUntil(60000) { !api.status(action).cleanupPending }
            assertEquals("COMPLETED", api.status(action).status)
            context.filesDir
                .resolve("live-ru.json")
                .writeText(
                    receipt
                        .put("status", "COMPLETED")
                        .put("action", action)
                        .put("text", result.text)
                        .put("provider_job_id", result.providerJobId)
                        .put("model", result.model)
                        .put("completed_at", result.completedAt)
                        .put("cleanup_pending", false)
                        .put("segments", result.segments.size)
                        .toString()
                )
        }
    }

    @Test
    fun resumeKnownRejectedRussianAction() {
        val endpoint = requireLive()
        val receipt = read("ru")
        val identity = identity(receipt)
        val action = receipt.getString("action")
        val installation = CloudIdentity(context)
        val api = CloudHttpClient(endpoint, installation.installationId, installation::sign)
        val prior = api.status(action)
        assertEquals("FAILED", prior.status)
        assertTrue(prior.retryable)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val session = open(scenario)
            val before = value(session.transcripts.load(identity))!!
            val done =
                value(
                    CloudRecognitionRun(api, EncryptedCloudCancellations(context)).run(
                        session,
                        identity,
                        action,
                        RecognitionAttempt(),
                    ) {}
                )
            assertEquals(before.versions, done.versions.take(before.versions.size))
            assertEquals(before.edits, done.edits)
            assertEquals(before.activeId, done.activeId)
            val result = done.versions.last().cloud!!
            assertTrue(result.text.isNotBlank())
            assertEquals("COMPLETED", api.status(action).status)
            assertFalse(api.status(action).cleanupPending)
            receipt.remove("error_code")
            context.filesDir
                .resolve("live-ru.json")
                .writeText(
                    receipt
                        .put("status", "COMPLETED")
                        .put("text", result.text)
                        .put("provider_job_id", result.providerJobId)
                        .put("model", result.model)
                        .put("completed_at", result.completedAt)
                        .put("cleanup_pending", false)
                        .put("segments", result.segments.size)
                        .toString()
                )
        }
    }

    @Test
    fun versionsAndEditsSurviveProcessRestart() {
        requireLive()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val session = open(scenario)
            val languages =
                if (arguments.getString("doraLiveLanguage") == "ru") listOf("ru")
                else listOf("ru", "en")
            languages.forEach { language ->
                val receipt = read(language)
                val doc = value(session.transcripts.load(identity(receipt)))!!
                val cloud = doc.versions.last()
                assertEquals(2, doc.versions.size)
                assertEquals("CLOUD", cloud.engine)
                assertEquals(receipt.getString("text"), cloud.text)
                assertEquals(receipt.getString("provider_job_id"), cloud.cloud!!.providerJobId)
                assertEquals(receipt.getString("local"), doc.activeId)
                assertEquals("saved owner correction $language", doc.rendered(doc.activeId!!))
                assertEquals("synthetic local $language", doc.versions.first().text)
                assertTrue(
                    value(session.originals.inspect(doc.source)) is OriginalAudioStatus.Available
                )
            }
        }
    }

    @Suppress(
        "LongMethod",
        "NestedBlockDepth",
    ) // One Activity lifetime, with PCM wiping and durable pre-dispatch marker.
    private fun recognize(language: String) {
        val endpoint = requireLive()
        val marker = context.filesDir.resolve("live-$language.json")
        check(!marker.exists()) { "Existing action: reconcile it; do not create another paid job" }
        val wav = context.filesDir.resolve("synthetic-$language.wav").readBytes()
        assertEquals(arguments.getString("fixtureSha256"), sha256(wav))
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVEfmt ", String(wav, 8, 8, Charsets.US_ASCII))
        assertEquals(16, header.getInt(16))
        assertEquals(1, header.getShort(20).toInt())
        assertEquals(1, header.getShort(22).toInt())
        assertEquals(16000, header.getInt(24))
        assertEquals(16, header.getShort(34).toInt())
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        assertEquals(wav.size - 44, header.getInt(40))
        assertTrue(wav.size in 320044..960044)
        val pcm = wav.copyOfRange(44, wav.size)
        wav.fill(0)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val session = open(scenario)
            val identity = AudioIdentity(RecordingId(uuid()), AudioAssetId(uuid()), uuid())
            value(session.writer.createLogicalRecording(identity))
            try {
                var offset = 0
                var sequence = 0
                while (offset < pcm.size) {
                    val end = minOf(offset + 160000, pcm.size)
                    val part = pcm.copyOfRange(offset, end)
                    try {
                        value(
                            session.writer.append(
                                AudioStorageUnitIdentity(identity, uuid(), sequence++, offset / 2L),
                                AudioFormat.PCM,
                                part,
                            )
                        )
                    } finally {
                        part.fill(0)
                    }
                    offset = end
                }
            } finally {
                pcm.fill(0)
            }
            value(session.writer.finalize(identity))
            val source =
                (value(session.originals.acquire(identity)) as OriginalAudioStatus.Available)
                    .reference
            val local = uuid()
            val time = System.currentTimeMillis()
            value(session.transcripts.begin(source, local, language, time))
            val first =
                value(
                    session.transcripts.finish(
                        source,
                        local,
                        "synthetic local $language",
                        "fixture-only",
                        time,
                    )
                )
            val edited =
                value(
                    session.transcripts.edit(
                        identity,
                        local,
                        first.revision,
                        "saved owner correction $language",
                        time,
                    )
                )
            var action = uuid()
            val receipt =
                JSONObject()
                    .put("recording", identity.recordingId.value)
                    .put("asset", identity.assetId.value)
                    .put("session", identity.sessionId)
                    .put("action", action)
                    .put("local", local)
                    .put("language", language)
                    .put("source_digest", source.digest)
                    .put("source_version", source.version)
                    .put("frames", source.frames)
                    .put("status", "PREPARED")
            marker.writeText(receipt.toString())
            val installation = CloudIdentity(context)
            val api = CloudHttpClient(endpoint, installation.installationId, installation::sign)
            val done =
                try {
                    if (arguments.getString("doraLiveUi") == "true") {
                        manualCloud(scenario, identity, language).also {
                            action = it.versions.last().id
                            receipt.put("action", action)
                        }
                    } else {
                        value(
                            session.transcripts.beginCloud(
                                source,
                                action,
                                language,
                                CloudConsent(grantedAt = time, destination = endpoint),
                                time,
                            )
                        )
                        value(
                            CloudRecognitionRun(api, EncryptedCloudCancellations(context)).run(
                                session,
                                identity,
                                action,
                                RecognitionAttempt(),
                            ) {}
                        )
                    }
                } catch (failure: CloudFailure) {
                    marker.writeText(
                        receipt.put("status", "FAILED").put("error_code", failure.code).toString()
                    )
                    throw failure
                }
            assertEquals(2, done.versions.size)
            assertEquals(edited.versions, done.versions.take(1))
            assertEquals(edited.edits, done.edits)
            assertEquals(local, done.activeId)
            assertEquals("saved owner correction $language", done.rendered(local))
            val result = done.versions.last().cloud!!
            assertEquals("amazon-transcribe", result.providerId)
            assertTrue(result.text.isNotBlank())
            assertEquals(source.digest, result.sourceDigest)
            assertEquals(source.version, result.sourceVersion)
            var remote = api.status(action)
            var count = 0
            while (remote.cleanupPending && count++ < 30) {
                Thread.sleep(2000)
                remote = api.status(action)
            }
            assertEquals("COMPLETED", remote.status)
            assertFalse(remote.cleanupPending)
            marker.writeText(
                receipt
                    .put("status", "COMPLETED")
                    .put("text", result.text)
                    .put("provider_job_id", result.providerJobId)
                    .put("model", result.model)
                    .put("completed_at", result.completedAt)
                    .put("cleanup_pending", false)
                    .put("segments", result.segments.size)
                    .toString()
            )
        }
    }

    private fun requireLive(): String {
        assumeTrue(arguments.getString("doraLiveOwnerAuthorized") == "true")
        return CloudEndpoint.validate(requireNotNull(arguments.getString("doraLiveEndpoint")))
    }

    private fun manualCloud(
        scenario: ActivityScenario<MainActivity>,
        identity: AudioIdentity,
        language: String,
        retry: Boolean = false,
    ): TranscriptDocument {
        assertEquals(requireLive(), CloudIdentity(context).endpoint())
        val runtime = (context.applicationContext as DoraApplication).audioRuntime
        scenario.onActivity { activity ->
            activity.setContent {
                DoraBootstrapTheme { TranscriptLauncher(activity, identity) }
            }
        }
        ui.onNodeWithText("Расшифровать / открыть текст").performClick()
        val dispatchLabel = if (retry) "Повторить / проверить результат" else "Распознать в облаке"
        ui.waitUntil(30000) {
            ui.onAllNodes(hasText(dispatchLabel) and isEnabled()).fetchSemanticsNodes().size == 1
        }
        if (retry) {
            ui.onNodeWithText("Повторить / проверить результат").performScrollTo().performClick()
        } else {
            ui.onNodeWithText("Распознать в облаке").performScrollTo().performClick()
            ui.onNodeWithText(if (language == "ru") "Русский · RU" else "English · EN")
                .performClick()
            ui.onNodeWithText("Разрешить и распознать").performClick()
        }
        var document: TranscriptDocument? = null
        ui.waitUntil(360000) {
            val current = (runtime.availability as? AudioAvailability.Available)?.session
            document = (current?.transcripts?.load(identity) as? AudioResult.Value)?.value
            document
                ?.jobs
                ?.lastOrNull { it.consent != null }
                ?.let { job ->
                    val marker = context.filesDir.resolve("live-$language.json")
                    val receipt = JSONObject(marker.readText())
                    if (receipt.optString("action") != job.id)
                        marker.writeText(receipt.put("action", job.id).toString())
                }
            document?.versions?.size == 2
        }
        // Secure physical-device windows can refuse screenshots; storage assertions are
        // authoritative.
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { screenshot
            ->
            try {
                context.filesDir.resolve("live-ui.png").outputStream().use {
                    screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally {
                screenshot.recycle()
            }
        }
        return checkNotNull(document)
    }

    private fun read(language: String) =
        JSONObject(context.filesDir.resolve("live-$language.json").readText())

    private fun identity(receipt: JSONObject) =
        AudioIdentity(
            RecordingId(receipt.getString("recording")),
            AudioAssetId(receipt.getString("asset")),
            receipt.getString("session"),
        )

    private fun uuid() = UUID.randomUUID().toString()

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun <T> value(result: AudioResult<T>): T {
        check(result is AudioResult.Value) { "Fixture storage operation failed: $result" }
        return result.value
    }

    private fun open(scenario: ActivityScenario<MainActivity>): ProductAudioSession {
        var session: ProductAudioSession? = null
        var failure = "No callback"
        val ready = CountDownLatch(1)
        scenario.onActivity { activity ->
            val runtime = (activity.application as DoraApplication).audioRuntime
            runtime.requestOpen(activity, runtime.recordingOpenMode()) { outcome ->
                session = (outcome as? AudioAvailability.Available)?.session
                failure =
                    if (outcome is AudioAvailability.Failed) outcome.reason.name
                    else outcome.javaClass.simpleName
                ready.countDown()
            }
        }
        assertTrue(ready.await(30, TimeUnit.SECONDS))
        return checkNotNull(session) { "Live fixture vault open: $failure" }
    }
}
