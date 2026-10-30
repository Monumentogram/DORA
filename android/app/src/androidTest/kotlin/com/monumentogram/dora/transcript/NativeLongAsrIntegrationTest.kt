package com.monumentogram.dora.transcript

import android.content.Context
import android.os.Debug
import android.os.Process
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.transcript.PagedTranscriptRead
import com.monumentogram.dora.audio.transcript.TranscriptPageCursor
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * One controlled fixture owns opt-in native inference and a separate process-restart readback.
 * Complete utterances align to partition edges; crossing-utterance quality is not evaluated. A
 * skipped phase is never qualification evidence.
 */
@Suppress("LongMethod", "MagicNumber", "TooManyFunctions")
class NativeLongAsrIntegrationTest {
    @Test(timeout = 7_200_000)
    // One opt-in scenario owns import, native callbacks, immutable history and encrypted reopen.
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    fun realThirtyMinuteNativePassPublishesOnePagedVersionAndPreservesEditsAfterReopen() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "NATIVE_30M_VALIDATION_PENDING",
            arguments.getString("doraNativeLongAsr") == "true" &&
                arguments.getString("doraNativeLongRead") != "true",
        )
        val language = arguments.getString("doraNativeAsrLanguage") ?: "en"
        require(language in setOf("ru", "en"))
        val runName = requireRunName()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.noBackupFilesDir, "native-asr-input/long-$language.pcm")
        val manifest = readManifest(context, language)
        assertTrue(
            "NATIVE_LONG_FORMAT",
            fixture.length() == 57_600_000L &&
                manifest.getLong("frames") == 28_800_000L &&
                manifest.getString("language") == language,
        )
        val expected = boundaryMarkers(manifest)
        assertTrue("NATIVE_LONG_RUNTIME_UNAVAILABLE", WhisperNative.available())
        val model = checkNotNull(LocalAsrModel(context).installed())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                it.setContent {}
            }
            val session = open(scenario, false)
            val identity = fixtureIdentity(runName, language)
            value(session.writer.createLogicalRecording(identity))
            val digest = MessageDigest.getInstance("SHA-256")
            fixture.inputStream().buffered().use { input ->
                val bytes = ByteArray(160_000)
                var first = 0L
                var ordinal = 0
                while (first < 28_800_000L) {
                    var count = 0
                    while (count < bytes.size) {
                        val read = input.read(bytes, count, bytes.size - count)
                        assertTrue("NATIVE_LONG_FIXTURE_TRUNCATED", read > 0)
                        count += read
                    }
                    digest.update(bytes)
                    value(
                        session.writer.append(
                            AudioStorageUnitIdentity(identity, id(), ordinal++, first),
                            AudioFormat.PCM,
                            bytes,
                        )
                    )
                    bytes.fill(0)
                    first += count / 2L
                }
                assertEquals(-1, input.read())
            }
            assertTrue(
                "NATIVE_LONG_FIXTURE_DIGEST",
                digest.digest().joinToString("") { "%02x".format(it) } ==
                    manifest.getString("pcmSha256"),
            )
            value(session.writer.finalize(identity))
            assertTrue("NATIVE_LONG_FIXTURE_STAGING_DELETE", fixture.delete())
            val source =
                (value(session.originals.acquire(identity)) as OriginalAudioStatus.Available)
                    .reference
            val previous = scopedId(runName, language, "previous")
            value(session.transcripts.begin(source, previous, language, 1))
            val baseline =
                value(
                    session.transcripts.finish(
                        source,
                        previous,
                        "Public baseline",
                        "synthetic-prior-version",
                        2,
                    )
                )
            val corrected =
                value(
                    session.transcripts.edit(
                        identity,
                        previous,
                        baseline.revision,
                        "Public owner correction retained",
                        3,
                    )
                )
            var calls = 0
            var boundedWindowMarkers = 0
            val peakPssKb = AtomicInteger()
            val started = System.nanoTime()
            val job = scopedId(runName, language, "job")
            val run =
                LocalRecognitionRun({ samples, lang, cancelled ->
                    assertEquals(4_800_000, samples.size)
                    val index = calls++
                    val pending = value(session.transcripts.load(identity))!!
                    assertEquals(corrected.versions, pending.versions)
                    val sampling = AtomicBoolean(true)
                    val sampler = Thread {
                        while (sampling.get()) {
                            val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                            peakPssKb.accumulateAndGet(memory.totalPss) { old, current ->
                                maxOf(old, current)
                            }
                            Thread.sleep(500)
                        }
                    }
                    sampler.start()
                    val text =
                        try {
                            WhisperNative.recognize(model, samples, lang) {
                                cancelled() || System.nanoTime() - started > 7_000_000_000_000L
                            }
                        } finally {
                            sampling.set(false)
                            sampler.join(2_000)
                        }
                    val normalized = normalize(text)
                    assertTrue("NATIVE_LONG_EMPTY_WINDOW", normalized.isNotBlank())
                    val coverage =
                        NativePublicMarkerMatcher.analyze(
                            normalized,
                            expected.subList(index * 3, index * 3 + 3),
                        )
                    boundedWindowMarkers += coverage.boundedOrdered
                    println(
                        "NATIVE_LONG_WINDOW_MARKERS window=$index exact=${coverage.exactPresent}/3 " +
                            "bounded=${coverage.boundedPresent}/3 exactOrdered=${coverage.exactOrdered}/3 " +
                            "boundedOrdered=${coverage.boundedOrdered}/3 maxCharacterEditsPerMarker=1"
                    )
                    text
                })
            val completed =
                value(
                    run.run(
                        session,
                        identity,
                        job,
                        language,
                        "whisper-small-q8_0",
                        RecognitionAttempt(),
                    ) {}
                )
            assertEquals(6, calls)
            assertEquals(2, completed.versions.size)
            assertEquals(corrected.versions, completed.versions.take(1))
            assertEquals(corrected.edits, completed.edits)
            assertEquals(corrected.selections, completed.selections)
            assertEquals(previous, completed.activeId)
            assertEquals(6, completed.versions.last().paged!!.partCount)
            assertDurableParts(session, identity, job)
            val coverage = orderedMarkers(session, identity, job, completed.revision, expected)
            val reopened = open(scenario, true)
            assertEquals(completed, value(reopened.transcripts.load(identity)))
            assertDurableParts(reopened, identity, job)
            assertEquals("Public owner correction retained", completed.rendered(previous))
            assertEquals(
                coverage,
                orderedMarkers(reopened, identity, job, completed.revision, expected),
            )
            assertTrue("NATIVE_LONG_METRICS", peakPssKb.get() > 0)
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000
            // Functional receipts are independent of the separately evaluated marker quality.
            processReceipt(context, runName, language).writeText(Process.myPid().toString())
            File(context.noBackupFilesDir, "native-asr-input/long-$language-$runName.metrics.json")
                .writeText(
                    JSONObject()
                        .apply {
                            put("pid", Process.myPid())
                            put("peakPssKb", peakPssKb.get())
                            put("elapsedMs", elapsedMillis)
                            put("windows", calls)
                            put("frames", 28_800_000L)
                            put("functional", "PASS")
                        }
                        .toString()
                )
            println("NATIVE_LONG_FUNCTIONAL windows=6 nonempty=6 history=PASS vaultReopen=PASS")
            reportCoverage(coverage)
            println(
                "NATIVE_LONG_WINDOW_MARKER_COVERAGE boundedOrdered=$boundedWindowMarkers/18 " +
                    "evaluation=SEPARATE_QUALITY_TEST"
            )
            android.util.Log.i(
                "DoraNativeLong",
                "PASS language=$language windows=$calls frames=28800000 " +
                    "peakPssKb=${peakPssKb.get()} " +
                    "elapsedMs=$elapsedMillis " +
                    "vaultReopen=PASS processRestart=NOT_TESTED",
            )
        }
    }

    /** Diagnostic text is permitted only after authenticating the exact known public PCM bytes. */
    @Test(timeout = 120_000)
    @Suppress(
        "NestedBlockDepth"
    ) // Source authentication and streamed file cleanup share one lifetime.
    fun diagnoseKnownPublicLongFixtureResult() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "NATIVE_LONG_PUBLIC_DIAGNOSTIC_NOT_REQUESTED",
            arguments.getString("doraNativeLongDiagnostic") == "true" &&
                arguments.getString("doraNativeLongAsr") != "true",
        )
        val language = arguments.getString("doraNativeAsrLanguage") ?: "en"
        val expectedDigest =
            when (language) {
                "en" -> "cddbfd9a2d1bca6ef6453a1efd270349ab00a878930c8e0ac59b698462ccaba0"
                "ru" -> "20141a2ca7716f5110d784ac911d52754a467f5c8059f725f691465910d45552"
                else -> error("NATIVE_LONG_DIAGNOSTIC_LANGUAGE_UNSUPPORTED")
            }
        val runName = requireRunName()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                it.setContent {}
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val session = open(scenario, true)
            val identity = fixtureIdentity(runName, language)
            val job = scopedId(runName, language, "job")
            val restored = checkNotNull(value(session.transcripts.load(identity)))
            assertEquals(28_800_000L, restored.source.frames)
            assertEquals(6, restored.versions.single { it.id == job }.paged!!.partCount)
            assertEquals("SUCCEEDED", restored.jobs.single { it.id == job }.status)
            value(session.originals.openAuthenticated(restored.source)).use { source ->
                val digest = MessageDigest.getInstance("SHA-256")
                val status =
                    value(
                        source.extractRange(0, source.reference.frames) { _, bytes ->
                            digest.update(bytes)
                        }
                    )
                assertEquals(OriginalAudioStatus.Available(restored.source), status)
                val expectedBytes =
                    expectedDigest.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                assertTrue(
                    "NATIVE_LONG_DIAGNOSTIC_REQUIRES_EXACT_KNOWN_PUBLIC_SOURCE",
                    MessageDigest.isEqual(expectedBytes, digest.digest()),
                )
                File(context.cacheDir, "native-public-long-$language.txt")
                    .bufferedWriter(Charsets.UTF_8)
                    .use { output ->
                        PagedTranscriptRead.stream(
                            session.transcripts.paged,
                            identity,
                            job,
                            restored.revision,
                            { false },
                        ) { text ->
                            output.write(text)
                        }
                    }
            }
        }
        println("NATIVE_LONG_PUBLIC_DIAGNOSTIC_WRITTEN language=$language parts=6 inferenceCalls=0")
    }

    /** Run after force-stopping the isolated test app; this phase performs no native inference. */
    @Test(timeout = 120_000)
    fun verifyPublishedVersionAfterProcessRestart() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            arguments.getString("doraNativeLongRead") == "true" &&
                arguments.getString("doraNativeLongAsr") != "true"
        )
        val language = arguments.getString("doraNativeAsrLanguage") ?: "en"
        require(language in setOf("ru", "en"))
        val runName = requireRunName()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val receipt = processReceipt(context, runName, language)
        val explicitPid = arguments.getString("doraNativeLongPreviousPid")?.toInt()
        val receiptPid =
            if (receipt.exists()) {
                require(receipt.length() in 1..32)
                receipt.readText().toInt()
            } else null
        if (explicitPid != null && receiptPid != null) {
            assertEquals("NATIVE_LONG_PREVIOUS_PID_RECEIPT_MISMATCH", receiptPid, explicitPid)
        }
        val originalPid =
            checkNotNull(explicitPid ?: receiptPid) { "NATIVE_LONG_PREVIOUS_PID_REQUIRED" }
        assertTrue(
            "NATIVE_LONG_PROCESS_MUST_CHANGE",
            originalPid > 0 && originalPid != Process.myPid(),
        )
        val expected = boundaryMarkers(readManifest(context, language))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                it.setContent {}
            }
            val session = open(scenario, true)
            val identity = fixtureIdentity(runName, language)
            val previous = scopedId(runName, language, "previous")
            val job = scopedId(runName, language, "job")
            val restored = value(session.transcripts.load(identity))!!
            assertEquals(2, restored.versions.size)
            assertEquals(previous, restored.activeId)
            assertEquals("Public owner correction retained", restored.rendered(previous))
            assertEquals(1, restored.edits.size)
            assertTrue(restored.selections.isEmpty())
            assertEquals(job, restored.versions.last().id)
            assertEquals(6, restored.versions.last().paged!!.partCount)
            assertEquals("SUCCEEDED", restored.jobs.last().status)
            assertDurableParts(session, identity, job)
            val original =
                value(session.originals.acquire(identity)) as OriginalAudioStatus.Available
            assertEquals(restored.source, original.reference)
            val coverage = orderedMarkers(session, identity, job, restored.revision, expected)
            reportCoverage(coverage)
        }
        android.util.Log.i(
            "DoraNativeLong",
            "PASS_NATIVE_LONG_PROCESS_RESTART language=$language " +
                "versions=2 parts=6 inferenceCalls=0",
        )
    }

    /** Strict boundary quality remains independently opt-in; failed historical runs stay failed. */
    @Test(timeout = 120_000)
    fun verifyAllPublicBoundaryMarkers() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "NATIVE_LONG_QUALITY_NOT_REQUESTED",
            arguments.getString("doraNativeLongQuality") == "true" &&
                arguments.getString("doraNativeLongAsr") != "true",
        )
        val language = arguments.getString("doraNativeAsrLanguage") ?: "en"
        require(language in setOf("en", "ru"))
        val runName = requireRunName()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = boundaryMarkers(readManifest(context, language))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                it.setContent {}
            }
            val session = open(scenario, true)
            val identity = fixtureIdentity(runName, language)
            val job = scopedId(runName, language, "job")
            val document = checkNotNull(value(session.transcripts.load(identity)))
            assertDurableParts(session, identity, job)
            val coverage = orderedMarkers(session, identity, job, document.revision, expected)
            reportCoverage(coverage)
            val perWindow =
                List(6) { NativePublicMarkerMatcher(expected.subList(it * 3, it * 3 + 3)) }
            var after: TranscriptPageCursor? = null
            while (true) {
                val page =
                    value(session.transcripts.paged.page(identity, job, after, document.revision))
                        ?: break
                assertTrue("NATIVE_LONG_QUALITY_COMPLETE_PAGE", page.completeVersion)
                require(page.cursor.partIndex in 0..5)
                perWindow[page.cursor.partIndex].consume(page.text)
                after = page.cursor
            }
            val windowOrdered = perWindow.map { it.finish().boundedOrdered }.sum()
            assertEquals("NATIVE_LONG_WINDOW_MARKERS_WITHIN_ONE_CHARACTER", 18, windowOrdered)
            assertEquals(
                "NATIVE_LONG_ORDERED_MARKERS_WITHIN_ONE_CHARACTER",
                18,
                coverage.boundedOrdered,
            )
        }
    }

    private fun assertDurableParts(
        session: ProductAudioSession,
        identity: AudioIdentity,
        job: String,
    ) {
        val progress = checkNotNull(value(session.transcripts.paged.progress(identity, job)))
        assertEquals("SUCCEEDED", progress.status)
        assertEquals(6, progress.totalParts)
        assertEquals(6, progress.completedParts)
        assertTrue("NATIVE_LONG_PUBLICATION_REQUIRED", progress.published)
        for (index in 0 until 6) {
            val part = checkNotNull(value(session.transcripts.paged.part(identity, job, index)))
            assertEquals(index, part.index)
            assertTrue("NATIVE_LONG_NONEMPTY_DURABLE_PART", part.textBytes > 0)
        }
    }

    private fun readManifest(context: Context, language: String): JSONObject {
        val file = File(context.noBackupFilesDir, "native-asr-input/long-$language.json")
        require(file.length() in 1..16_384)
        return JSONObject(file.readText()).also {
            assertEquals(2, it.getInt("version"))
            assertEquals("local-fixed-300-v1", it.getString("windowPolicy"))
            assertEquals(language, it.getString("language"))
            assertEquals(28_800_000L, it.getLong("frames"))
        }
    }

    private fun boundaryMarkers(manifest: JSONObject): List<String> {
        val markers = manifest.getJSONArray("markers")
        assertEquals(18, markers.length())
        return List(18) { index ->
            val marker = markers.getJSONObject(index)
            val window = index / 3
            val start = window * 4_800_000L
            val end = start + 4_800_000
            val first = marker.getLong("firstFrame")
            val count = marker.getLong("frameCount")
            assertEquals(window, marker.getInt("windowIndex"))
            assertTrue(
                "NATIVE_LONG_MARKER_RANGE",
                count > 0 && first >= start && first + count <= end,
            )
            when (index % 3) {
                0 -> {
                    assertEquals("WINDOW_START", marker.getString("role"))
                    assertEquals(start, first)
                }
                1 -> assertEquals("MIDPOINT", marker.getString("role"))
                2 -> {
                    assertEquals("WINDOW_END", marker.getString("role"))
                    assertEquals(end, first + count)
                }
            }
            normalize(marker.getString("phrase"))
        }
    }

    private fun orderedMarkers(
        session: ProductAudioSession,
        identity: AudioIdentity,
        job: String,
        revision: Long,
        expected: List<String>,
    ): NativeMarkerCoverage {
        val matcher = NativePublicMarkerMatcher(expected)
        PagedTranscriptRead.stream(session.transcripts.paged, identity, job, revision, { false }) {
            text ->
            matcher.consume(text)
        }
        return matcher.finish()
    }

    private fun reportCoverage(coverage: NativeMarkerCoverage) {
        println(
            "NATIVE_LONG_MARKER_COVERAGE exact=${coverage.exactPresent}/18 " +
                "bounded=${coverage.boundedPresent}/18 exactOrdered=${coverage.exactOrdered}/18 " +
                "boundedOrdered=${coverage.boundedOrdered}/18 maxCharacterEditsPerMarker=1 " +
                "quality=${if (coverage.exactPresent == 18 && coverage.exactOrdered == 18) "EXACT" else "PARTIAL"}"
        )
    }

    private fun requireRunName(): String =
        checkNotNull(InstrumentationRegistry.getArguments().getString("doraNativeLongRun")).also {
            require(it.matches(Regex("[a-zA-Z0-9-]{1,64}")))
        }

    private fun scopedId(run: String, language: String, role: String) =
        UUID.nameUUIDFromBytes(
                "dora-native-long-v1:$run:$language:$role".toByteArray(Charsets.UTF_8)
            )
            .toString()

    private fun fixtureIdentity(run: String, language: String) =
        AudioIdentity(
            RecordingId(scopedId(run, language, "recording")),
            AudioAssetId(scopedId(run, language, "asset")),
            scopedId(run, language, "session"),
        )

    private fun processReceipt(context: Context, run: String, language: String) =
        File(context.noBackupFilesDir, "native-asr-input/long-$language-$run.pid")

    private fun open(
        scenario: ActivityScenario<MainActivity>,
        reopen: Boolean,
    ): ProductAudioSession {
        val ready = CountDownLatch(1)
        var session: ProductAudioSession? = null
        scenario.onActivity { activity ->
            val runtime = (activity.application as DoraApplication).audioRuntime
            if (reopen) runtime.lock()
            runtime.requestOpen(
                activity,
                if (reopen) AudioOpenMode.OPEN_EXISTING else runtime.recordingOpenMode(),
            ) {
                session = (it as? AudioAvailability.Available)?.session
                ready.countDown()
            }
        }
        assertTrue("NATIVE_LONG_VAULT_OPEN", ready.await(30, TimeUnit.SECONDS))
        return checkNotNull(session)
    }

    private fun <T> value(result: AudioResult<T>): T {
        assertTrue("NATIVE_LONG_PROTECTED_STORAGE", result is AudioResult.Value)
        return (result as AudioResult.Value).value
    }

    private fun normalize(text: String) =
        text.lowercase(Locale.ROOT).replace('ё', 'е').replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun id() = UUID.randomUUID().toString()
}
