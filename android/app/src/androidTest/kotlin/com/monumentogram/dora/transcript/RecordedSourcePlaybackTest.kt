package com.monumentogram.dora.transcript

import android.os.Process
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.recording.RecordingLibraryState
import com.monumentogram.dora.audio.recording.RecordingSearchPage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Existing capture only: no new audio, identity logging, source edits or test runtime delegates.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class RecordedSourcePlaybackTest {
    @Test(timeout = TEST_TIMEOUT_MILLIS)
    @Suppress("LongMethod") // One saved source and player lifetime cover all three seek positions.
    fun savedCaptureReopensAndPlaysStartMiddleAndEndInNewProcess() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "RECORDED_SOURCE_PLAYBACK_NOT_REQUESTED",
            arguments.getString("doraRecordedSourcePlayback") == "true",
        )
        val frames = checkNotNull(arguments.getString("doraRecordedSourceFrames")).toLong()
        val capturePid = checkNotNull(arguments.getString("doraRecordedSourceCapturePid")).toInt()
        assertTrue("RECORDED_SOURCE_CAPTURE_PID_REQUIRED", capturePid > 0)
        assertTrue("RECORDED_SOURCE_REQUIRES_NEW_PROCESS", capturePid != Process.myPid())
        assertTrue("RECORDED_SOURCE_TOO_SHORT", frames >= END_MARGIN_FRAMES * 2)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                it.setContent {}
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val identity = findExactCapture(scenario, frames)
            val session = openExisting(scenario)
            value(session.originals.openAuthenticated(identity)).use { source ->
                assertEquals(
                    "RECORDED_SOURCE_AUTHENTICATED_FRAMES",
                    frames,
                    source.reference.frames,
                )
            }
            var player: OriginalAudioPlayer? = null
            scenario.onActivity { activity ->
                player = OriginalAudioPlayer(activity).also { it.describe(frames) }
            }
            val owned = checkNotNull(player)
            try {
                for ((label, start) in
                    listOf(
                        "START" to 0L,
                        "MIDDLE" to frames / 2,
                        "END" to frames - END_MARGIN_FRAMES,
                    )) {
                    assertPlaybackAdvances(scenario, owned, identity, label, start, frames)
                }
            } finally {
                scenario.onActivity { owned.close() }
            }
            println(
                "PASS_RECORDED_SOURCE_PLAYBACK frames=$frames capturePid=$capturePid " +
                    "readPid=${Process.myPid()} positions=START,MIDDLE,END processRestart=PASS"
            )
        }
    }

    private fun findExactCapture(
        scenario: ActivityScenario<MainActivity>,
        frames: Long,
    ): AudioIdentity {
        var cursor = ""
        var found: AudioIdentity? = null
        do {
            val ready = CountDownLatch(1)
            var result: AudioResult<RecordingSearchPage>? = null
            scenario.onActivity { activity ->
                (activity.application as DoraApplication).audioRuntime.requestRecordingSearch(
                    activity,
                    "",
                    "Recording %s",
                    cursor,
                    { false },
                ) {
                    result = it
                    ready.countDown()
                }
            }
            assertTrue(
                "RECORDED_SOURCE_LIBRARY_TIMEOUT",
                ready.await(OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            val page = value(checkNotNull(result))
            for (row in page.rows) {
                val recording = row.recording
                if (
                    recording.committedFrames == frames &&
                        recording.state == RecordingLibraryState.FINALIZED
                ) {
                    assertTrue("RECORDED_SOURCE_FRAME_MATCH_AMBIGUOUS", found == null)
                    found = checkNotNull(recording.identity)
                }
            }
            val next = page.nextCursor
            assertTrue("RECORDED_SOURCE_LIBRARY_CURSOR", next == null || next > cursor)
            cursor = next.orEmpty()
        } while (next != null)
        return checkNotNull(found) { "RECORDED_SOURCE_EXACT_CAPTURE_NOT_FOUND" }
    }

    private fun openExisting(scenario: ActivityScenario<MainActivity>): ProductAudioSession {
        val ready = CountDownLatch(1)
        var session: ProductAudioSession? = null
        scenario.onActivity { activity ->
            (activity.application as DoraApplication).audioRuntime.requestOpen(
                activity,
                AudioOpenMode.OPEN_EXISTING,
            ) { result ->
                session = (result as? AudioAvailability.Available)?.session
                ready.countDown()
            }
        }
        assertTrue(
            "RECORDED_SOURCE_VAULT_TIMEOUT",
            ready.await(OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
        return checkNotNull(session) { "RECORDED_SOURCE_VAULT_UNAVAILABLE" }
    }

    @Suppress("LongParameterList") // Keep source, requested range and player ownership explicit.
    private fun assertPlaybackAdvances(
        scenario: ActivityScenario<MainActivity>,
        player: OriginalAudioPlayer,
        identity: AudioIdentity,
        label: String,
        start: Long,
        frames: Long,
    ) {
        val started = SystemClock.elapsedRealtime()
        var readyMillis: Long? = null
        scenario.onActivity {
            player.seek(start)
            player.play(identity)
        }
        val deadline = SystemClock.elapsedRealtime() + PLAY_TIMEOUT_MILLIS
        var position = start
        var observedPlaying = false
        do {
            scenario.onActivity {
                assertTrue("RECORDED_SOURCE_PLAYBACK_FAILED_$label", !player.failed)
                observedPlaying = observedPlaying || player.playing
                if (player.playing && readyMillis == null)
                    readyMillis = SystemClock.elapsedRealtime() - started
                position = player.position
            }
            if (observedPlaying && position >= start + REQUIRED_ADVANCE_FRAMES) break
            assertTrue(
                "RECORDED_SOURCE_PLAYBACK_STALLED_$label",
                SystemClock.elapsedRealtime() < deadline,
            )
            Thread.sleep(POLL_MILLIS)
        } while (true)
        scenario.onActivity { player.pause() }
        assertTrue(
            "RECORDED_SOURCE_PLAYBACK_RANGE_$label",
            position in (start + REQUIRED_ADVANCE_FRAMES)..frames,
        )
        println(
            "RECORDED_SOURCE_PLAYBACK_POSITION label=$label requested=$start observed=$position " +
                "readyMs=$readyMillis elapsedMs=${SystemClock.elapsedRealtime() - started}"
        )
    }

    private fun <T> value(result: AudioResult<T>): T {
        assertTrue("RECORDED_SOURCE_STORAGE_FAILURE", result is AudioResult.Value)
        return (result as AudioResult.Value).value
    }

    private companion object {
        const val TEST_TIMEOUT_MILLIS = 360_000L
        const val OPEN_TIMEOUT_SECONDS = 60L
        const val PLAY_TIMEOUT_MILLIS = 90_000L
        const val POLL_MILLIS = 100L
        const val REQUIRED_ADVANCE_FRAMES = 16_000L
        const val END_MARGIN_FRAMES = 48_000L
    }
}
