@file:Suppress("MagicNumber", "LongMethod")

package com.monumentogram.dora.transcript

import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.R
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Stage11DeletionUiTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test
    fun cancelKeepsRecordingAndConfirmedDeleteRemovesIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent {} }
            ui.waitForIdle()
            var session: ProductAudioSession? = null
            val opened = CountDownLatch(1)
            scenario.onActivity { host ->
                val runtime = (host.application as DoraApplication).audioRuntime
                runtime.requestOpen(host, runtime.recordingOpenMode()) {
                    session = (it as? AudioAvailability.Available)?.session
                    opened.countDown()
                }
            }
            assertTrue(opened.await(30, TimeUnit.SECONDS))
            val vault = checkNotNull(session)
            val identity = AudioIdentity(RecordingId(id()), AudioAssetId(id()), id())
            value(vault.writer.create(identity))
            value(
                vault.writer.append(
                    AudioStorageUnitIdentity(identity, id(), 0, 0),
                    AudioFormat.PCM,
                    ByteArray(320),
                )
            )
            value(vault.writer.finalize(identity))
            val source =
                (value(vault.originals.acquire(identity)) as OriginalAudioStatus.Available)
                    .reference
            val job = id()
            value(vault.transcripts.begin(source, job, "en", 1))
            val before =
                value(
                    vault.transcripts.finish(source, job, "Synthetic deletion note", "fixture", 2)
                )
            val deleted = mutableStateOf(false)
            scenario.onActivity { host ->
                host.setContent {
                    DoraBootstrapTheme {
                        if (deleted.value) Text("synthetic-deleted")
                        else TranscriptDialog(host, identity) { deleted.value = true }
                    }
                }
            }
            await(label(R.string.signal_copy_32))
            ui.onNodeWithText(label(R.string.stage11_delete_recording))
                .performScrollTo()
                .performClick()
            ui.onNodeWithText(label(R.string.signal_copy_2)).performClick()
            scenario.onActivity {
                session =
                    ((it.application as DoraApplication).audioRuntime.availability
                            as AudioAvailability.Available)
                        .session
            }
            assertEquals(before, value(checkNotNull(session).transcripts.load(identity)))
            assertTrue(
                value(checkNotNull(session).originals.acquire(identity))
                    is OriginalAudioStatus.Available
            )
            ui.onNodeWithText(label(R.string.stage11_delete_recording))
                .performScrollTo()
                .performClick()
            ui.onNodeWithText(label(R.string.stage11_delete_confirm)).performClick()
            await("synthetic-deleted")
            scenario.onActivity {
                session =
                    ((it.application as DoraApplication).audioRuntime.availability
                            as AudioAvailability.Available)
                        .session
            }
            assertNull(value(checkNotNull(session).transcripts.load(identity)))
            assertTrue(value(checkNotNull(session).inspectAudioDeletion(identity)).complete)
        }
    }

    private fun label(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun await(text: String) {
        ui.waitUntil(30000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun id() = UUID.randomUUID().toString()

    private fun <T> value(result: AudioResult<T>): T {
        assertTrue(result is AudioResult.Value)
        return (result as AudioResult.Value).value
    }
}
