package com.monumentogram.dora.transcript

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.ui.theme.DoraBootstrapTheme
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Synthetic UI boundary test; fixture text is never a substitute for actual native ASR evidence.
 */
class TranscriptUiTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test
    fun encryptedEditorSavesReopensAndMissingModelIsTruthful() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var host: MainActivity
            val opened = CountDownLatch(1)
            var session: ProductAudioSession? = null
            scenario.onActivity { activity ->
                host = activity
                val runtime = (activity.application as DoraApplication).audioRuntime
                runtime.requestOpen(activity, runtime.recordingOpenMode()) { result ->
                    session = (result as? AudioAvailability.Available)?.session
                    opened.countDown()
                }
            }
            assertTrue(opened.await(30, TimeUnit.SECONDS))
            val vault = checkNotNull(session)
            val identity =
                AudioIdentity(
                    RecordingId(UUID.randomUUID().toString()),
                    AudioAssetId(UUID.randomUUID().toString()),
                    UUID.randomUUID().toString(),
                )
            value(vault.writer.createLogicalRecording(identity))
            value(
                vault.writer.append(
                    AudioStorageUnitIdentity(identity, UUID.randomUUID().toString(), 0, 0),
                    AudioFormat.PCM,
                    ByteArray(32_000),
                )
            )
            value(vault.writer.finalize(identity))
            val source =
                (value(vault.originals.acquire(identity)) as OriginalAudioStatus.Available)
                    .reference
            val job = UUID.randomUUID().toString()
            value(vault.transcripts.begin(source, job, "en", 1))
            value(vault.transcripts.finish(source, job, "Synthetic machine", "fixture-only", 2))
            scenario.onActivity {
                it.setContent { DoraBootstrapTheme { TranscriptLauncher(host, identity) } }
            }
            ui.onNodeWithText("Расшифровать / открыть текст").performClick()
            awaitText("Synthetic machine")
            ui.onNodeWithText("Текст расшифровки")
                .performScrollTo()
                .performTextReplacement("Synthetic correction")
            ui.onNodeWithText("Сохранить изменения").performScrollTo().performClick()
            awaitText("Ваши сохранённые правки")
            ui.onNodeWithText("Закрыть", useUnmergedTree = true).performScrollTo().performClick()
            ui.onNodeWithText("Расшифровать / открыть текст").performClick()
            awaitText("Synthetic correction")
            ui.onNodeWithText("Распознать заново").performScrollTo().performClick()
            awaitText(
                "Установите пакет распознавания без интернета. Запись аудио доступна без него."
            )
            ui.onNodeWithText("Synthetic machine").assertExists()
            ui.onNodeWithText("Synthetic correction").assertExists()
        }
    }

    private fun awaitText(text: String) {
        ui.waitUntil(30_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun <T> value(result: AudioResult<T>): T {
        assertTrue(result is AudioResult.Value)
        return (result as AudioResult.Value).value
    }
}
