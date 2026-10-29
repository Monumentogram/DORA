@file:Suppress(
    "MagicNumber",
    "LongMethod",
    "NestedBlockDepth",
) // Bounded synthetic lifecycle fixtures.

package com.monumentogram.dora.transcript

import android.Manifest
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Process
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Functional execution only. No language-accuracy assertion or ASR benchmark. */
class Stage11OfflineNativeTest {
    @Test(timeout = 600000)
    fun installedSmallModelRunsRuAndEnWithoutNetworkPermission() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("stage11Native") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(Manifest.permission.INTERNET),
        )
        val before = TrafficStats.getUidTxBytes(Process.myUid())
        assertTrue("UID traffic counter unavailable", before >= 0)
        val model = checkNotNull(LocalAsrModel(context).installed())
        assertTrue(WhisperNative.available())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent {} }
            val opened = CountDownLatch(1)
            var current: ProductAudioSession? = null
            scenario.onActivity { host ->
                val runtime = (host.application as DoraApplication).audioRuntime
                runtime.requestOpen(host, runtime.recordingOpenMode()) {
                    current = (it as? AudioAvailability.Available)?.session
                    opened.countDown()
                }
            }
            assertTrue(opened.await(30, TimeUnit.SECONDS))
            val session = checkNotNull(current)
            for (language in listOf("ru", "en")) {
                val pcm =
                    File(context.noBackupFilesDir, "stage11-synthetic-$language.pcm").readBytes()
                require(pcm.size in 32000..640000 && pcm.size % 2 == 0)
                val identity = AudioIdentity(RecordingId(id()), AudioAssetId(id()), id())
                value(session.writer.create(identity))
                var offset = 0
                var ordinal = 0
                while (offset < pcm.size) {
                    val end = minOf(offset + 32000, pcm.size)
                    val part = pcm.copyOfRange(offset, end)
                    try {
                        value(
                            session.writer.append(
                                AudioStorageUnitIdentity(identity, id(), ordinal++, offset / 2L),
                                AudioFormat.PCM,
                                part,
                            )
                        )
                    } finally {
                        part.fill(0)
                    }
                    offset = end
                }
                pcm.fill(0)
                value(session.writer.finalize(identity))
                val result =
                    value(
                        LocalRecognitionRun({ samples, lang, cancel ->
                                WhisperNative.recognize(model, samples, lang, cancel)
                            })
                            .run(
                                session,
                                identity,
                                id(),
                                language,
                                "whisper-small-q8_0",
                                RecognitionAttempt(),
                            ) {}
                    )
                assertEquals(1, result.versions.size)
                assertTrue(result.versions.single().text.isNotBlank())
                value(
                    session.transcripts.edit(
                        identity,
                        checkNotNull(result.activeId),
                        result.revision,
                        "Synthetic offline correction $language",
                        100,
                    )
                )
            }
        }
        assertEquals(
            "Local ASR must not transmit bytes",
            before,
            TrafficStats.getUidTxBytes(Process.myUid()),
        )
    }

    private fun id() = UUID.randomUUID().toString()

    private fun <T> value(result: AudioResult<T>): T {
        assertTrue("Protected operation failed", result is AudioResult.Value)
        return (result as AudioResult.Value).value
    }
}
