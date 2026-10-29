@file:Suppress(
    "MagicNumber",
    "LongMethod",
    "NestedBlockDepth",
) // Bounded synthetic lifecycle fixtures.

package com.monumentogram.dora.transcript

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.runtime.key
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
import com.monumentogram.dora.audio.transcript.CloudConsent
import com.monumentogram.dora.audio.transcript.CloudTranscriptResult
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.ui.theme.DoraBootstrapTheme
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The real editor/export click and ActivityResult callback; picker result is synthetic. */
class TextExportVersionUiTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test
    fun exportsDisplayedLocalCloudAndEditsWithoutLosingEarlierVersions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val output = File(context.cacheDir, "synthetic-version-export-${id()}.txt")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.setContent {} }
                val opened = CountDownLatch(1)
                var session: ProductAudioSession? = null
                scenario.onActivity { host ->
                    val runtime = (host.application as DoraApplication).audioRuntime
                    runtime.requestOpen(host, runtime.recordingOpenMode()) {
                        session = (it as? AudioAvailability.Available)?.session
                        opened.countDown()
                    }
                }
                assertTrue(opened.await(30, TimeUnit.SECONDS))
                var vault = checkNotNull(session)
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
                val local = id()
                value(vault.transcripts.begin(source, local, "ru", 1))
                var doc =
                    value(
                        vault.transcripts.finish(
                            source,
                            local,
                            "Машинный текст\n\nLocal paragraph",
                            "fixture",
                            2,
                        )
                    )
                val refresh = mutableStateOf(0)
                scenario.onActivity { host ->
                    host.setContent {
                        DoraBootstrapTheme {
                            key(refresh.value) { TranscriptDialog(host, identity) {} }
                        }
                    }
                }
                fun export(expected: String, resultCode: Int = Activity.RESULT_OK) {
                    ui.waitUntil(30000) {
                        ui.onAllNodesWithText(label(R.string.stage11_export_text))
                            .fetchSemanticsNodes()
                            .isNotEmpty()
                    }
                    val monitor =
                        instrumentation.addMonitor(
                            IntentFilter(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addDataType("text/plain")
                                addCategory(Intent.CATEGORY_OPENABLE)
                            },
                            Instrumentation.ActivityResult(
                                resultCode,
                                Intent().setData(Uri.fromFile(output)),
                            ),
                            true,
                        )
                    try {
                        ui.onNodeWithText(label(R.string.stage11_export_text))
                            .performScrollTo()
                            .performClick()
                        ui.waitUntil(30000) {
                            var status = TextExportStatus.IDLE
                            scenario.onActivity { status = it.textExportStatus }
                            status ==
                                if (resultCode == Activity.RESULT_OK) TextExportStatus.SAVED
                                else TextExportStatus.CANCELLED
                        }
                        assertEquals(1, monitor.hits)
                        if (resultCode == Activity.RESULT_OK)
                            assertArrayEquals(
                                expected.toByteArray(Charsets.UTF_8),
                                output.readBytes(),
                            )
                    } finally {
                        instrumentation.removeMonitor(monitor)
                    }
                    // Dialog loading reopens the generation-bound runtime session.
                    // Fixture mutations must use that current handle, like product actions.
                    ui.waitUntil(30000) {
                        var ready = false
                        scenario.onActivity {
                            val available =
                                (it.application as DoraApplication).audioRuntime.availability
                                    as? AudioAvailability.Available
                            if (available != null) {
                                vault = available.session
                                ready = true
                            }
                        }
                        ready
                    }
                    // The session opens before the asynchronous document read is published.
                    ui.waitUntil(30000) {
                        ui.onAllNodesWithText(label(R.string.stage11_export_text))
                            .fetchSemanticsNodes()
                            .isNotEmpty()
                    }
                }
                export(doc.rendered(local))
                val corrected = "Ручная правка\n\nSaved correction 😀\n"
                doc = value(vault.transcripts.edit(identity, local, doc.revision, corrected, 3))
                scenario.onActivity { refresh.value++ }
                export(corrected)
                val cloud = id()
                value(
                    vault.transcripts.beginCloud(
                        source,
                        cloud,
                        "en",
                        CloudConsent(grantedAt = 4),
                        4,
                    )
                )
                doc =
                    value(
                        vault.transcripts.finishCloud(
                            source,
                            cloud,
                            CloudTranscriptResult(
                                "Synthetic cloud\n\nSecond paragraph",
                                "amazon-transcribe",
                                "standard-batch:eu-central-1:en-US",
                                "synthetic-job",
                                source.digest,
                                1,
                                5,
                            ),
                        )
                    )
                assertEquals(local, doc.activeId)
                assertEquals(corrected, doc.rendered(local))
                scenario.onActivity { refresh.value++ }
                export(corrected)
                ui.onNodeWithText(
                        if (context.resources.configuration.locales[0].language == "ru")
                            "Версия 2 · Облако"
                        else "Version 2 · Cloud"
                    )
                    .performScrollTo()
                    .performClick()
                export(doc.rendered(cloud))
                doc =
                    value(
                        vault.transcripts.select(
                            identity,
                            cloud,
                            doc.revision,
                            doc.conflicts(cloud).map { it.id }.toSet(),
                            6,
                        )
                    )
                val cloudCorrection = "Cloud correction\n\nИсправленный абзац"
                doc =
                    value(vault.transcripts.edit(identity, cloud, doc.revision, cloudCorrection, 7))
                val repeat = id()
                value(vault.transcripts.begin(source, repeat, "en", 8))
                doc =
                    value(
                        vault.transcripts.finish(
                            source,
                            repeat,
                            "New machine proposal",
                            "fixture",
                            9,
                        )
                    )
                assertEquals(cloud, doc.activeId)
                assertEquals(corrected, doc.rendered(local))
                assertEquals(cloudCorrection, doc.rendered(cloud))
                scenario.onActivity { refresh.value++ }
                export(cloudCorrection)
                val beforeCancel = output.readBytes()
                export("", Activity.RESULT_CANCELED)
                assertArrayEquals(beforeCancel, output.readBytes())
                assertEquals(doc, value(vault.transcripts.load(identity)))
            }
        } finally {
            output.delete()
        }
    }

    private fun label(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun id() = UUID.randomUUID().toString()

    private fun <T> value(result: AudioResult<T>): T {
        assertTrue(result is AudioResult.Value)
        return (result as AudioResult.Value).value
    }
}
