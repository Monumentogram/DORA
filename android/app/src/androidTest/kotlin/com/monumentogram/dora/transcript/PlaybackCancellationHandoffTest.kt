@file:Suppress("MagicNumber", "LongMethod", "MaxLineLength")

package com.monumentogram.dora.transcript

import android.os.Process
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.R
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.persistence.runtime.AndroidProductAudioRuntime
import com.monumentogram.dora.audio.recording.RecordingAccess
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.ui.theme.DoraBootstrapTheme
import java.lang.reflect.Field
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Real player, authenticated read and exclusive recording-vault handoff. The only injected delay is
 * after a real encrypted unit has authenticated. Recording uses synthetic PCM through the real
 * RecordingAccess writer; this does not claim microphone or physical-device timing evidence.
 */
class PlaybackCancellationHandoffTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test(timeout = 120_000)
    fun loadingCancelStopsAuthenticationAndAllowsNewRecordingWithoutRestart() = exercise(false)

    @Test(timeout = 120_000)
    fun closingLoadingPlayerStopsAuthenticationAndAllowsNewRecordingWithoutRestart() =
        exercise(true)

    @Suppress("NestedBlockDepth") // One UI flow owns the Activity, blocked read and writer handoff.
    private fun exercise(dispose: Boolean) {
        val pid = Process.myPid()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent {} }
            ui.waitForIdle()
            val session = alphaSession(scenario)
            val source = identity()
            writeSource(session, source)
            lateinit var runtime: AndroidProductAudioRuntime
            scenario.onActivity { runtime = (it.application as DoraApplication).audioRuntime }
            AuthenticatedUnitGate(runtime).use { gate ->
                scenario.onActivity { host ->
                    host.setContent {
                        DoraBootstrapTheme {
                            OriginalAudioControls(host, source, knownFrames = 48_000)
                        }
                    }
                }
                ui.onNodeWithText(label(R.string.signal_play)).performClick()
                assertTrue(
                    "Authenticated read did not reach its first unit",
                    gate.entered.await(15, TimeUnit.SECONDS),
                )
                ui.onNodeWithText(label(android.R.string.cancel)).assertIsEnabled()
                try {
                    if (dispose) {
                        scenario.onActivity { it.setContent { Text("player-closed") } }
                        ui.waitForIdle() // DisposableEffect must retire the generation before
                        // release.
                    } else ui.onNodeWithText(label(android.R.string.cancel)).performClick()
                } finally {
                    gate.release.countDown()
                }
                assertTrue(
                    "Cancelled read did not unwind",
                    gate.finished.await(10, TimeUnit.SECONDS),
                )
                assertEquals("Abandoned open authenticated more units", 1, gate.units.get())
                assertTrue(
                    "Borrowed PCM survived cancellation",
                    checkNotNull(gate.borrowed.get()).all { it == 0.toByte() },
                )
                ui.waitForIdle()
                ui.onNodeWithText(label(android.R.string.cancel)).assertDoesNotExist()
                ui.onNodeWithText(label(R.string.signal_pause)).assertDoesNotExist()
                ui.onNodeWithText(label(R.string.signal_playback_error)).assertDoesNotExist()
                if (dispose) ui.onNodeWithText("player-closed").assertExists()
                else ui.onNodeWithText(label(R.string.signal_play)).assertIsEnabled()
            }
            // Dispose the normal-cancel player's metadata owner before the real writer handoff.
            scenario.onActivity { it.setContent {} }
            ui.waitForIdle()
            val recorded = writeAfterRecordingHandoff(scenario, runtime)
            val reopened = alphaSession(scenario)
            alphaValue(reopened.originals.openAuthenticated(source)).use {
                assertEquals(48_000L, it.reference.frames)
            }
            alphaValue(reopened.originals.openAuthenticated(recorded)).use {
                assertEquals(16_000L, it.reference.frames)
            }
            assertEquals("Recording handoff required a process restart", pid, Process.myPid())
        }
    }

    private fun writeSource(session: ProductAudioSession, identity: AudioIdentity) {
        alphaValue(session.writer.createLogicalRecording(identity))
        repeat(3) { ordinal ->
            val pcm = ByteArray(32_000) { 7 }
            try {
                alphaValue(
                    session.writer.append(
                        AudioStorageUnitIdentity(identity, alphaId(), ordinal, ordinal * 16_000L),
                        AudioFormat.PCM,
                        pcm,
                    )
                )
            } finally {
                pcm.fill(0)
            }
        }
        alphaValue(session.writer.finalize(identity))
    }

    private fun writeAfterRecordingHandoff(
        scenario: ActivityScenario<MainActivity>,
        runtime: AndroidProductAudioRuntime,
    ): AudioIdentity {
        val identity = identity()
        val ready = CountDownLatch(1)
        var opened: AudioResult<RecordingAccess>? = null
        scenario.onActivity { activity ->
            runtime.requestRecording(activity, identity) {
                opened = it
                ready.countDown()
            }
        }
        assertTrue(
            "New recording remained blocked behind cancelled read",
            ready.await(15, TimeUnit.SECONDS),
        )
        alphaValue(checkNotNull(opened)).use { access ->
            alphaValue(access.writer.createLogicalRecording(identity))
            val pcm = ByteArray(32_000) { 3 }
            try {
                alphaValue(
                    access.writer.append(
                        AudioStorageUnitIdentity(identity, alphaId(), 0, 0),
                        AudioFormat.PCM,
                        pcm,
                    )
                )
            } finally {
                pcm.fill(0)
            }
            alphaValue(access.writer.finalize(identity))
        }
        val recordings = checkNotNull(field(runtime, "recordings").get(runtime))
        val inUse = field(recordings, "inUse")
        ui.waitUntil(10_000) { inUse.get(recordings) == false }
        return identity
    }

    private fun identity() =
        AudioIdentity(RecordingId(alphaId()), AudioAssetId(alphaId()), alphaId())

    private fun label(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /** Test-only reflection inserts a bounded delay; original crypto/read/authority always run. */
    private class AuthenticatedUnitGate(runtime: AndroidProductAudioRuntime) : AutoCloseable {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val units = AtomicInteger()
        val borrowed = AtomicReference<ByteArray>()
        private val coordinator = checkNotNull(field(runtime, "coordinator").get(runtime))
        private val opening = field(coordinator, "openVault")
        @Suppress("UNCHECKED_CAST")
        private val original = opening.get(coordinator) as (Any, Any) -> AudioResult<*>

        init {
            val gated: (Any, Any) -> AudioResult<*> = { mode, authorization ->
                original(mode, authorization).also { result ->
                    if (result is AudioResult.Value) install(checkNotNull(result.value))
                }
            }
            opening.set(coordinator, gated)
        }

        @Suppress("UNCHECKED_CAST")
        private fun install(vault: Any) {
            val lifecycle = checkNotNull(field(vault, "originals").get(vault))
            val reading = field(lifecycle, "readHeld")
            val originalRead =
                reading.get(lifecycle) as (Any, (Long, ByteArray) -> Unit) -> AudioResult<*>
            val gated: (Any, (Long, ByteArray) -> Unit) -> AudioResult<*> = { asset, consume ->
                try {
                    originalRead(asset) { first, bytes ->
                        if (units.incrementAndGet() == 1) {
                            borrowed.set(bytes)
                            entered.countDown()
                            check(release.await(15, TimeUnit.SECONDS)) {
                                "Authenticated unit gate timed out"
                            }
                        }
                        consume(first, bytes)
                    }
                } finally {
                    finished.countDown()
                }
            }
            reading.set(lifecycle, gated)
        }

        override fun close() {
            release.countDown()
            opening.set(coordinator, original)
        }
    }

    private companion object {
        fun field(owner: Any, name: String): Field =
            owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    }
}
