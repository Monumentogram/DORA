@file:Suppress(
    "LongMethod",
    "MagicNumber",
    "MaxLineLength",
) // Synthetic end-to-end acceptance fixtures.

package com.monumentogram.dora

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.transcript.OriginalAudioControls
import com.monumentogram.dora.ui.signal.SignalShell
import com.monumentogram.dora.ui.theme.DoraBootstrapTheme
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignalAcceptanceTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test
    fun originalPlaybackSurvivesTranscriptEditAndStopsOnBackground() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val (vault, identity) = fixture(scenario)
            val before = value(vault.originals.acquire(identity))
            val document = checkNotNull(value(vault.transcripts.load(identity)))
            value(
                vault.transcripts.edit(
                    identity,
                    checkNotNull(document.activeId),
                    document.revision,
                    "Synthetic edited note. Original audio must remain unchanged.",
                    System.currentTimeMillis(),
                )
            )
            assertEquals(before, value(vault.originals.acquire(identity)))
            val blocked = mutableStateOf(true)
            scenario.onActivity { host ->
                host.setContent {
                    DoraBootstrapTheme {
                        OriginalAudioControls(host, identity, canPlay = { !blocked.value })
                    }
                }
            }
            ui.onNodeWithText(label(R.string.signal_play)).assertIsNotEnabled()
            scenario.onActivity { blocked.value = false }
            ui.onNodeWithText(label(R.string.signal_play)).performClick()
            await(label(R.string.signal_pause))
            scenario.onActivity { blocked.value = true }
            await(label(R.string.signal_play))
            ui.onNodeWithText(label(R.string.signal_play)).assertIsNotEnabled()
            scenario.onActivity { blocked.value = false }
            ui.onNodeWithText(label(R.string.signal_forward_ten)).performClick()
            ui.onNodeWithText("00:00:03 / 00:00:03").assertExists()
            // Playback updates every 20 ms. Automatic idle synchronization can skip
            // the entire three-second state on a physical device; inspect each frame.
            ui.mainClock.autoAdvance = false
            try {
                ui.onNodeWithText(label(R.string.signal_play)).performClick()
                ui.waitUntil(30000) {
                    ui.mainClock.advanceTimeByFrame()
                    ui.onAllNodesWithText(label(R.string.signal_pause))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
            } finally {
                ui.mainClock.autoAdvance = true
            }
            ui.onNodeWithText(label(R.string.signal_play)).assertExists()
            ui.onNodeWithText("00:00:00 / 00:00:00").assertExists()
        }
    }

    @Test
    fun syntheticLibraryLightDarkAndSettingsScreenshots() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fixture(scenario)
            for (dark in listOf(false, true)) {
                scenario.onActivity { host ->
                    host.setContent {
                        DoraBootstrapTheme(darkTheme = dark) { SignalShell(host, {}) }
                    }
                    // Test fixtures only. Production keeps FLAG_SECURE on every screen/dialog.
                    host.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                // Large fonts can place the fixture title outside the lazy list's visible rows.
                ui.waitUntil(30000) {
                    listOf(R.string.signal_transcript_ready, R.string.signal_audio_saved).any {
                        ui.onAllNodesWithText(label(it), substring = true)
                            .fetchSemanticsNodes()
                            .isNotEmpty()
                    }
                }
                scenario.onActivity { it.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
                screenshot(scenario, if (dark) "dark-home" else "light-home")
                ui.onNodeWithText(label(R.string.nav_settings)).performClick()
                ui.onNodeWithText(label(R.string.signal_local_model)).performScrollTo()
                screenshot(scenario, if (dark) "dark-settings" else "light-settings")
                ui.onNodeWithText(label(R.string.signal_recordings)).performClick()
            }
        }
    }

    private fun fixture(
        scenario: ActivityScenario<MainActivity>
    ): Pair<ProductAudioSession, AudioIdentity> {
        // The fixture owns its session until the synthetic content has been committed.
        scenario.onActivity { it.setContent {} }
        ui.waitForIdle()
        val latch = CountDownLatch(1)
        var session: ProductAudioSession? = null
        scenario.onActivity { activity ->
            val runtime = (activity.application as DoraApplication).audioRuntime
            runtime.requestOpen(activity, runtime.recordingOpenMode()) {
                session = (it as? AudioAvailability.Available)?.session
                latch.countDown()
            }
        }
        assertTrue(latch.await(30, TimeUnit.SECONDS))
        val vault = checkNotNull(session)
        val identity =
            AudioIdentity(
                RecordingId(UUID.randomUUID().toString()),
                AudioAssetId(UUID.randomUUID().toString()),
                UUID.randomUUID().toString(),
            )
        value(vault.writer.createLogicalRecording(identity))
        repeat(3) { ordinal ->
            value(
                vault.writer.append(
                    AudioStorageUnitIdentity(
                        identity,
                        UUID.randomUUID().toString(),
                        ordinal,
                        ordinal * 16000L,
                    ),
                    AudioFormat.PCM,
                    ByteArray(32000),
                )
            )
        }
        value(vault.writer.finalize(identity))
        val source =
            (value(vault.originals.acquire(identity)) as OriginalAudioStatus.Available).reference
        val job = UUID.randomUUID().toString()
        val time = System.currentTimeMillis()
        value(vault.transcripts.begin(source, job, "en", time))
        value(
            vault.transcripts.finish(
                source,
                job,
                "A quiet moment to remember\nA synthetic demonstration of private notes. " +
                    "Plan a walk, capture an idea, and keep the original audio alongside every revision.",
                "synthetic-ui-fixture",
                time,
            )
        )
        return vault to identity
    }

    private fun screenshot(scenario: ActivityScenario<MainActivity>, name: String) {
        ui.waitForIdle()
        var directory: File? = null
        scenario.onActivity {
            directory = it.getExternalFilesDir(null)
            it.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        android.os.SystemClock.sleep(400) // Let the compositor apply this test-only window flag.
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        checkNotNull(bitmap)
        assertTrue(
            "Screenshot must contain rendered UI",
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) != android.graphics.Color.BLACK,
        )
        File(checkNotNull(directory), "stage10-$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    private fun await(text: String) {
        ui.waitUntil(30000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun label(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun <T> value(result: AudioResult<T>): T {
        assertTrue(result is AudioResult.Value)
        return (result as AudioResult.Value).value
    }
}
