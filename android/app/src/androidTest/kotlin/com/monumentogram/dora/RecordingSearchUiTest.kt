@file:Suppress("MagicNumber", "LongMethod", "MaxLineLength")

package com.monumentogram.dora

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
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
import com.monumentogram.dora.ui.signal.SignalShell
import com.monumentogram.dora.ui.theme.DoraBootstrapTheme
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecordingSearchUiTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun label(id: Int) = instrumentation.targetContext.getString(id)

    @Test
    fun searchClearRapidInputEditorRefreshAndRestart() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent {} }
            ui.waitForIdle()
            val opened = CountDownLatch(1)
            var vault: ProductAudioSession? = null
            scenario.onActivity { activity ->
                val runtime = (activity.application as DoraApplication).audioRuntime
                runtime.requestOpen(activity, runtime.recordingOpenMode()) {
                    vault = (it as? AudioAvailability.Available)?.session
                    opened.countDown()
                }
            }
            assertTrue(opened.await(30, TimeUnit.SECONDS))
            val session = checkNotNull(vault)
            val marker = UUID.randomUUID().toString().take(8)
            val identity =
                AudioIdentity(
                    RecordingId(UUID.randomUUID().toString()),
                    AudioAssetId(UUID.randomUUID().toString()),
                    UUID.randomUUID().toString(),
                )
            value(session.writer.createLogicalRecording(identity))
            value(
                session.writer.append(
                    AudioStorageUnitIdentity(identity, UUID.randomUUID().toString(), 0, 0),
                    AudioFormat.PCM,
                    ByteArray(32000),
                )
            )
            value(session.writer.finalize(identity))
            val audioOnly =
                AudioIdentity(
                    RecordingId(UUID.randomUUID().toString()),
                    AudioAssetId(UUID.randomUUID().toString()),
                    UUID.randomUUID().toString(),
                )
            value(session.writer.createLogicalRecording(audioOnly))
            value(
                session.writer.append(
                    AudioStorageUnitIdentity(audioOnly, UUID.randomUUID().toString(), 0, 0),
                    AudioFormat.PCM,
                    ByteArray(32000),
                )
            )
            value(session.writer.finalize(audioOnly))
            val source =
                (value(session.originals.acquire(identity)) as OriginalAudioStatus.Available)
                    .reference
            val job = UUID.randomUUID().toString()
            value(session.transcripts.begin(source, job, "ru", 1))
            val title = "План модернизации · $marker"
            value(
                session.transcripts.finish(
                    source,
                    job,
                    "$title\n" +
                        "Обсуждение проекта. ".repeat(300) +
                        "Сегодня обсуждали модернизацию конвейерного оборудования$marker. Equipment review.",
                    "synthetic-ui",
                    System.currentTimeMillis(),
                )
            )
            scenario.onActivity { host ->
                host.setContent { DoraBootstrapTheme { SignalShell(host, {}) } }
            }
            ui.onNodeWithContentDescription(label(R.string.signal_search)).performClick()
            ui.onNodeWithTag("library-search").assertIsFocused()
            query("оборудования$marker")
            await(title)
            ui.onNodeWithText(title).assertExists()
            for (text in
                listOf(
                    "absent-$marker",
                    "КОНВЕЙЕР",
                    "absent2-$marker",
                    "  оборудования$marker  ",
                )) query(text)
            await(title)
            capture(scenario, "search-light")
            query("never-present-$marker")
            await(label(R.string.signal_search_empty_title))
            capture(scenario, "no-results")
            ui.onNodeWithContentDescription(label(R.string.signal_search_clear)).performClick()
            ui.onNodeWithTag("library-search")
                .assert(
                    SemanticsMatcher.expectValue(
                        androidx.compose.ui.semantics.SemanticsProperties.EditableText,
                        androidx.compose.ui.text.AnnotatedString(""),
                    )
                )
            query(marker)
            await(title)
            ui.onNodeWithText(title).performClick()
            await(label(R.string.signal_copy_32))
            val correction = "Исправленный план · $marker"
            ui.onNodeWithText(label(R.string.signal_copy_32))
                .performScrollTo()
                .performTextReplacement(correction)
            ui.onNodeWithText(label(R.string.signal_copy_33)).performScrollTo().performClick()
            await(com.monumentogram.dora.ui.signal.uiCopy("Ваши сохранённые правки"))
            ui.onNodeWithText(label(R.string.signal_copy_3), useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            await(correction)
            query("оборудования$marker")
            await(label(R.string.signal_search_empty_title))
            query(marker)
            await(correction)
            ui.onNodeWithContentDescription(label(R.string.signal_search_close)).performClick()
            ui.onNodeWithTag("library-search").assertDoesNotExist()
            scenario.recreate()
            ui.onNodeWithContentDescription(label(R.string.signal_search)).performClick()
            query(marker)
            await(correction)
            scenario.onActivity { host ->
                host.setContent { DoraBootstrapTheme(darkTheme = true) { SignalShell(host, {}) } }
            }
            // Replacing the theme may retain search; explicitly open if needed.
            if (ui.onAllNodesWithTag("library-search").fetchSemanticsNodes().isEmpty())
                ui.onNodeWithContentDescription(label(R.string.signal_search)).performClick()
            query(marker)
            await(correction)
            capture(scenario, "search-dark")
            query(audioOnly.recordingId.value.take(8))
            await(label(R.string.signal_no_transcript))
            ui.onNodeWithText(label(R.string.signal_no_transcript)).assertExists()
        }
    }

    private fun query(text: String) {
        ui.onNodeWithTag("library-search").performTextReplacement(text)
    }

    private fun await(text: String) {
        ui.waitUntil(30000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(scenario: ActivityScenario<MainActivity>, name: String) {
        scenario.onActivity { it.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        android.os.SystemClock.sleep(400)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.getExternalFilesDir(null), "stage103c-$name.png")
            .outputStream()
            .use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    private fun <T> value(result: AudioResult<T>): T {
        assertTrue(result is AudioResult.Value)
        return (result as AudioResult.Value).value
    }
}
