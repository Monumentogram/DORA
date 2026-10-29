@file:Suppress("MagicNumber", "LongMethod")

package com.monumentogram.dora.transcript

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.setContent
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
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Stage11OfflineTest {
    @Test
    fun noInternetCloudFailsBeforeJobCreationAndMissingModelKeepsAudio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(
            context.checkSelfPermission(Manifest.permission.INTERNET) ==
                PackageManager.PERMISSION_DENIED
        )
        assertNull(LocalAsrModel(context).installed())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent {} }
            val latch = CountDownLatch(1)
            var opened: ProductAudioSession? = null
            scenario.onActivity {
                val runtime = (it.application as DoraApplication).audioRuntime
                runtime.requestOpen(it, runtime.recordingOpenMode()) { result ->
                    opened = (result as? AudioAvailability.Available)?.session
                    latch.countDown()
                }
            }
            assertTrue(latch.await(30, TimeUnit.SECONDS))
            val vault = checkNotNull(opened)
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
            val source = value(vault.originals.acquire(identity))
            lateinit var actions: TranscriptActions
            scenario.onActivity {
                actions = TranscriptActions(it)
                actions.recognizeCloud(identity, "ru")
                assertFalse(actions.busy)
                assertEquals(it.getString(R.string.stage11_cloud_offline), actions.message)
                actions.recognize(identity, "en")
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            var done = false
            while (!done && System.nanoTime() < deadline) {
                scenario.onActivity { done = !actions.busy }
                if (!done) Thread.sleep(50)
            }
            assertTrue(done)
            var current: ProductAudioSession? = null
            scenario.onActivity {
                assertTrue(actions.message.contains("Установите пакет"))
                current =
                    ((it.application as DoraApplication).audioRuntime.availability
                            as AudioAvailability.Available)
                        .session
            }
            assertEquals(source, value(checkNotNull(current).originals.acquire(identity)))
            assertNull(value(checkNotNull(current).transcripts.load(identity)))
            scenario.onActivity { actions.close() }
        }
    }

    private fun id() = UUID.randomUUID().toString()

    private fun <T> value(result: AudioResult<T>): T {
        assertTrue(result is AudioResult.Value)
        return (result as AudioResult.Value).value
    }
}
