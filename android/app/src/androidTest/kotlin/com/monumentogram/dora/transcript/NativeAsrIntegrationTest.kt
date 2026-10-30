package com.monumentogram.dora.transcript

import android.system.Os
import android.system.OsConstants
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.transcript.TranscriptDocument
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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in local evidence only; absent opt-in is NOT_EVALUATED, never native PASS. Provision the
 * separately admitted model and authorized public/synthetic PCM in noBackup storage. Select this
 * class explicitly and require one passed test with zero skips for native evidence. No audio,
 * model, or recognized text is bundled in the test APK or printed by assertions.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class NativeAsrIntegrationTest {
    /** Read the already-published failed result; performs no additional native inference. */
    @Test(timeout = 120_000)
    @Suppress("LoopWithTooManyJumpStatements") // Reject unrelated sources before any text is read.
    fun diagnoseKnownPublicShortFixtureResult() {
        assumeTrue(
            "NATIVE_PUBLIC_DIAGNOSTIC_NOT_REQUESTED",
            InstrumentationRegistry.getArguments()
                .getString("doraNativeAsrPublicFailureDiagnostic") == "true",
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent {} }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val session = open(scenario, reopen = true)
            var matched = 0
            for (identity in value(session.transcripts.history())) {
                val document = value(session.transcripts.load(identity)) ?: continue
                if (document.source.frames != PUBLIC_DIAGNOSTIC_FRAMES) continue
                val opened = value(session.originals.openAuthenticated(document.source))
                val digest = MessageDigest.getInstance("SHA-256")
                opened.use { source ->
                    value(
                        source.extractRange(0, source.reference.frames) { _, bytes ->
                            digest.update(bytes)
                        }
                    )
                }
                if (!MessageDigest.isEqual(digest.digest(), publicDiagnosticDigest())) continue
                val recognized = normalize(document.versions.last().text)
                assertTrue("NATIVE_PUBLIC_DIAGNOSTIC_RESULT_EMPTY", recognized.isNotEmpty())
                assertTrue(
                    "NATIVE_PUBLIC_DIAGNOSTIC_REQUIRES_FAILED_EXPECTATION",
                    !recognized.contains("amber lantern"),
                )
                matched++
                assertTrue("NATIVE_PUBLIC_DIAGNOSTIC_AMBIGUOUS", matched == 1)
                File(context.cacheDir, PUBLIC_DIAGNOSTIC_PATH).writeText(recognized)
            }
            assertTrue("NATIVE_PUBLIC_DIAGNOSTIC_SOURCE_NOT_FOUND", matched == 1)
            println("NATIVE_PUBLIC_DIAGNOSTIC_WRITTEN inferenceCalls=0")
        }
    }

    @Suppress("LongMethod") // One ordered real-inference, immutable-history and reopen scenario.
    @Test(timeout = TEST_TIMEOUT_MS)
    fun realNativeRecognitionPreservesEditsVersionsAndSelectionAfterReopen() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "NATIVE_ASR_NOT_REQUESTED_NOT_EVALUATED",
            arguments.getString("doraNativeAsr") == "true",
        )
        arguments.getString("doraNativeAsrPageSize")?.let { expected ->
            assertTrue(
                "NATIVE_ASR_PAGE_SIZE_MISMATCH",
                Os.sysconf(OsConstants._SC_PAGESIZE).toString() == expected,
            )
        }
        val expectedDigest = arguments.getString("doraNativeAsrPcmSha256").orEmpty()
        assertTrue(
            "NATIVE_ASR_FIXTURE_DIGEST_REQUIRED",
            expectedDigest.matches(Regex("[0-9a-f]{64}")),
        )
        val expected = normalize(arguments.getString("doraNativeAsrExpectedPhrase").orEmpty())
        assertTrue(
            "NATIVE_ASR_PUBLIC_EXPECTATION_REQUIRED",
            expected.length in 3..160 && expected.split(' ').size >= 2,
        )
        val language = arguments.getString("doraNativeAsrLanguage") ?: "en"
        assertTrue("NATIVE_ASR_LANGUAGE_UNSUPPORTED", language == "en" || language == "ru")
        val expectation = Expectation(expected, language)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("NATIVE_ASR_RUNTIME_UNAVAILABLE", WhisperNative.available())
        val model = LocalAsrModel(context).installed()
        assertTrue("NATIVE_ASR_MODEL_UNAVAILABLE", model != null)
        val pcm = readPublicFixture(File(context.noBackupFilesDir, PUBLIC_PCM_PATH), expectedDigest)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                // This integration scenario owns the vault; the home library opens one separately.
                scenario.onActivity {
                    it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    it.setContent {}
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                val session = open(scenario, reopen = false)
                val identity = saveProtectedRecording(session, pcm)
                // Only the encrypted original is used by both actual inference attempts below.
                pcm.fill(0)
                val first = recognize(session, identity, checkNotNull(model), expectation)
                assertTrue("NATIVE_ASR_FIRST_VERSION_COUNT", first.versions.size == 1)
                val firstVersion = first.versions.single()
                val edited =
                    value(
                        session.transcripts.edit(
                            identity,
                            firstVersion.id,
                            first.revision,
                            PUBLIC_CORRECTION,
                            System.currentTimeMillis(),
                        )
                    )
                val selected = select(session, identity, edited, firstVersion.id)
                val second = recognize(session, identity, model, expectation)
                assertTrue("NATIVE_ASR_SECOND_VERSION_COUNT", second.versions.size == 2)
                assertTrue(
                    "NATIVE_ASR_FIRST_VERSION_MUTATED",
                    second.versions.first() == firstVersion,
                )
                assertTrue("NATIVE_ASR_EDIT_LOST", second.edits == selected.edits)
                assertTrue("NATIVE_ASR_SELECTION_LOST", second.selections == selected.selections)
                assertTrue("NATIVE_ASR_ACTIVE_VERSION_CHANGED", second.activeId == firstVersion.id)
                assertTrue(
                    "NATIVE_ASR_CORRECTION_OVERWRITTEN",
                    second.rendered(firstVersion.id) == PUBLIC_CORRECTION,
                )
                val final = select(session, identity, second, second.versions.last().id)
                assertTrue(
                    "NATIVE_ASR_EXPLICIT_SELECTION_FAILED",
                    final.activeId != firstVersion.id,
                )
                assertTrue("NATIVE_ASR_SELECTION_DELETED_EDIT", final.edits == edited.edits)
                val reopened = open(scenario, reopen = true)
                val restored = value(reopened.transcripts.load(identity))
                assertTrue("NATIVE_ASR_REOPEN_LOST_HISTORY", restored == final)
                val cancelled = cancelRecognition(reopened, identity, model, expectation)
                val afterCancellation = open(scenario, reopen = true)
                assertTrue(
                    "NATIVE_ASR_CANCEL_REOPEN_LOST_HISTORY",
                    value(afterCancellation.transcripts.load(identity)) == cancelled,
                )
            }
        } finally {
            pcm.fill(0)
        }
    }

    private fun readPublicFixture(file: File, expectedDigest: String): ByteArray {
        assertTrue("NATIVE_ASR_PUBLIC_FIXTURE_MISSING", file.isFile)
        val size = file.length()
        assertTrue(
            "NATIVE_ASR_PUBLIC_FIXTURE_SIZE",
            size in PCM_CHUNK_BYTES..MAX_PCM_BYTES && size % PCM_BYTES_PER_FRAME == 0L,
        )
        val bytes = ByteArray(size.toInt())
        try {
            file.inputStream().use { input ->
                var offset = 0
                while (offset < bytes.size) {
                    val count = input.read(bytes, offset, bytes.size - offset)
                    assertTrue("NATIVE_ASR_PUBLIC_FIXTURE_TRUNCATED", count > 0)
                    offset += count
                }
                assertTrue("NATIVE_ASR_PUBLIC_FIXTURE_OVERSIZED", input.read() == -1)
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val expected = expectedDigest.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            assertTrue("NATIVE_ASR_PUBLIC_FIXTURE_DIGEST", MessageDigest.isEqual(expected, digest))
            return bytes
        } catch (failure: Throwable) {
            bytes.fill(0)
            throw failure
        }
    }

    private fun saveProtectedRecording(
        session: ProductAudioSession,
        pcm: ByteArray,
    ): AudioIdentity {
        val identity =
            AudioIdentity(
                RecordingId(UUID.randomUUID().toString()),
                AudioAssetId(UUID.randomUUID().toString()),
                UUID.randomUUID().toString(),
            )
        value(session.writer.createLogicalRecording(identity))
        var offset = 0
        var ordinal = 0
        while (offset < pcm.size) {
            val end = minOf(offset + PCM_CHUNK_BYTES, pcm.size)
            val chunk = pcm.copyOfRange(offset, end)
            try {
                value(
                    session.writer.append(
                        AudioStorageUnitIdentity(
                            identity,
                            UUID.randomUUID().toString(),
                            ordinal,
                            (offset / PCM_BYTES_PER_FRAME).toLong(),
                        ),
                        AudioFormat.PCM,
                        chunk,
                    )
                )
            } finally {
                chunk.fill(0)
            }
            offset = end
            ordinal++
        }
        value(session.writer.finalize(identity))
        return identity
    }

    private fun recognize(
        session: ProductAudioSession,
        identity: AudioIdentity,
        model: File,
        expectation: Expectation,
    ): TranscriptDocument {
        val started = System.nanoTime()
        val run =
            LocalRecognitionRun({ samples, language, cancelled ->
                WhisperNative.recognize(model, samples, language) {
                    cancelled() || System.nanoTime() - started >= INFERENCE_TIMEOUT_NANOS
                }
            })
        val document =
            value(
                run.run(
                    session,
                    identity,
                    UUID.randomUUID().toString(),
                    expectation.language,
                    "whisper-small-q8_0",
                    RecognitionAttempt(),
                    state = {},
                )
            )
        val recognized = normalize(document.versions.last().text)
        if (!recognized.contains(expectation.phrase)) {
            writeKnownPublicFailureDiagnostic(session, document, expectation, recognized)
        }
        assertTrue("NATIVE_ASR_EMPTY_RESULT", recognized.isNotEmpty())
        val twoWordMarker = expectation.phrase.split(' ').size == 2
        val coverage =
            if (twoWordMarker) {
                NativePublicMarkerMatcher.analyze(recognized, listOf(expectation.phrase))
            } else {
                // Historical callers can provide longer phrases; their exact expectation is
                // retained.
                val exact = if (recognized.contains(expectation.phrase)) 1 else 0
                NativeMarkerCoverage(exact, exact, exact, exact)
            }
        println(
            "NATIVE_SHORT_MARKER_COVERAGE exact=${coverage.exactPresent}/1 " +
                "bounded=${coverage.boundedPresent}/1 maxCharacterEditsPerMarker=${if (twoWordMarker) 1 else 0} " +
                "quality=${if (coverage.exactPresent == 1) "EXACT" else "PARTIAL"}"
        )
        assertTrue(
            "NATIVE_ASR_PUBLIC_MARKER_MISSING_WITHIN_ONE_CHARACTER",
            coverage.boundedOrdered == 1,
        )
        return document
    }

    @Suppress(
        "ComplexCondition"
    ) // Every explicit fixture binding must match before text can leave the vault.
    private fun writeKnownPublicFailureDiagnostic(
        session: ProductAudioSession,
        document: TranscriptDocument,
        expectation: Expectation,
        recognized: String,
    ) {
        val arguments = InstrumentationRegistry.getArguments()
        if (
            arguments.getString("doraNativeAsrPublicFailureDiagnostic") != "true" ||
                arguments.getString("doraNativeAsrPcmSha256") != PUBLIC_DIAGNOSTIC_SHA ||
                expectation.language != "en" ||
                expectation.phrase != "amber lantern" ||
                document.source.frames != PUBLIC_DIAGNOSTIC_FRAMES
        )
            return
        val digest = MessageDigest.getInstance("SHA-256")
        value(session.originals.openAuthenticated(document.source)).use { source ->
            value(
                source.extractRange(0, source.reference.frames) { _, bytes -> digest.update(bytes) }
            )
        }
        assertTrue(
            "NATIVE_PUBLIC_DIAGNOSTIC_SOURCE_DIGEST",
            MessageDigest.isEqual(digest.digest(), publicDiagnosticDigest()),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, PUBLIC_DIAGNOSTIC_PATH).writeText(recognized)
    }

    private fun publicDiagnosticDigest() =
        PUBLIC_DIAGNOSTIC_SHA.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun select(
        session: ProductAudioSession,
        identity: AudioIdentity,
        document: TranscriptDocument,
        versionId: String,
    ): TranscriptDocument =
        value(
            session.transcripts.select(
                identity,
                versionId,
                document.revision,
                document.conflicts(versionId).map { it.id }.toSet(),
                System.currentTimeMillis(),
            )
        )

    @Suppress(
        "LongMethod"
    ) // Native callback cancellation and its durable outcome are one assertion sequence.
    private fun cancelRecognition(
        session: ProductAudioSession,
        identity: AudioIdentity,
        model: File,
        expectation: Expectation,
    ): TranscriptDocument {
        val before = checkNotNull(value(session.transcripts.load(identity)))
        val attempt = RecognitionAttempt()
        val probes = AtomicInteger()
        val cancellationObserved = AtomicBoolean()
        val jobId = UUID.randomUUID().toString()
        val started = System.nanoTime()
        val run =
            LocalRecognitionRun({ samples, language, cancelled ->
                WhisperNative.recognize(model, samples, language) {
                    // Ten probes require runtime callbacks beyond wrapper/JNI boundary checks.
                    // The exact compute phase and cancellation latency are not asserted.
                    if (probes.incrementAndGet() == CANCEL_AT_NATIVE_PROBE) {
                        cancellationObserved.set(attempt.cancel())
                    }
                    cancelled() || System.nanoTime() - started >= INFERENCE_TIMEOUT_NANOS
                }
            })
        var category: String? = null
        try {
            run.run(
                session,
                identity,
                jobId,
                expectation.language,
                "whisper-small-q8_0",
                attempt,
                state = {},
            )
        } catch (failure: RecognitionFailure) {
            category = failure.category
        }
        assertTrue("NATIVE_ASR_CANCEL_CALLBACK_NOT_OBSERVED", cancellationObserved.get())
        assertTrue("NATIVE_ASR_CANCEL_RESULT_INCORRECT", category == "CANCELLED")
        val after = checkNotNull(value(session.transcripts.load(identity)))
        assertTrue("NATIVE_ASR_CANCEL_JOB_COUNT", after.jobs.size == before.jobs.size + 1)
        assertTrue(
            "NATIVE_ASR_CANCEL_JOB_NOT_TERMINAL",
            after.jobs.last().id == jobId && after.jobs.last().status == "CANCELLED",
        )
        assertTrue("NATIVE_ASR_CANCEL_CHANGED_PREVIOUS_JOBS", after.jobs.dropLast(1) == before.jobs)
        assertTrue("NATIVE_ASR_CANCEL_CHANGED_VERSIONS", after.versions == before.versions)
        assertTrue("NATIVE_ASR_CANCEL_CHANGED_EDITS", after.edits == before.edits)
        assertTrue("NATIVE_ASR_CANCEL_CHANGED_SELECTIONS", after.selections == before.selections)
        assertTrue("NATIVE_ASR_CANCEL_CHANGED_ACTIVE_VERSION", after.activeId == before.activeId)
        assertTrue("NATIVE_ASR_CANCEL_CHANGED_SOURCE", after.source == before.source)
        return after
    }

    private fun open(
        scenario: ActivityScenario<MainActivity>,
        reopen: Boolean,
    ): ProductAudioSession {
        val opened = CountDownLatch(1)
        var session: ProductAudioSession? = null
        scenario.onActivity { activity ->
            val runtime = (activity.application as DoraApplication).audioRuntime
            if (reopen) runtime.lock()
            val mode = if (reopen) AudioOpenMode.OPEN_EXISTING else runtime.recordingOpenMode()
            runtime.requestOpen(activity, mode) { result ->
                session = (result as? AudioAvailability.Available)?.session
                opened.countDown()
            }
        }
        assertTrue("NATIVE_ASR_VAULT_OPEN_TIMEOUT", opened.await(30, TimeUnit.SECONDS))
        assertTrue("NATIVE_ASR_VAULT_UNAVAILABLE", session != null)
        return checkNotNull(session)
    }

    private fun <T> value(result: AudioResult<T>): T {
        assertTrue("NATIVE_ASR_PROTECTED_STORAGE_FAILURE", result is AudioResult.Value)
        return (result as AudioResult.Value).value
    }

    private fun normalize(text: String): String =
        text.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private class Expectation(val phrase: String, val language: String)

    private companion object {
        const val TEST_TIMEOUT_MS = 20 * 60 * 1000L
        const val INFERENCE_TIMEOUT_NANOS = 8 * 60 * 1_000_000_000L
        const val CANCEL_AT_NATIVE_PROBE = 10
        const val PCM_CHUNK_BYTES = 32_000
        const val PCM_BYTES_PER_FRAME = 2
        const val MAX_PCM_BYTES = 30L * PCM_CHUNK_BYTES
        const val PUBLIC_PCM_PATH = "native-asr-input/public-speech.pcm"
        const val PUBLIC_CORRECTION = "Public fixture correction retained by the owner."
        const val PUBLIC_DIAGNOSTIC_SHA =
            "44760d5d1906e22f823adc95841d8741162a5e8da774079b8b2326efe6cb02b9"
        const val PUBLIC_DIAGNOSTIC_FRAMES = 480_000L
        const val PUBLIC_DIAGNOSTIC_PATH = "native-public-failure.txt"
    }
}
