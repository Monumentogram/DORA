package com.monumentogram.dora

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.recording.RecordingController
import com.monumentogram.dora.ui.signal.uiCopy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignalRecordingFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test
    @Suppress("LongMethod", "NestedBlockDepth") // One flow owns capture, UI controls and cleanup.
    fun microphonePauseResumeSaveAndReturnToLibrary() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation
            .executeShellCommand(
                "pm grant ${ui.activity.packageName} android.permission.RECORD_AUDIO"
            )
            .close()
        ui.onNodeWithText(label(R.string.signal_new_recording), useUnmergedTree = true)
            .performClick()
        ui.onNodeWithContentDescription(uiCopy("Я предупредил(а) участников о записи"))
            .performScrollTo()
            .performClick()
        ui.onNodeWithText(label(R.string.signal_copy_4)).performClick()
        val controller = (ui.activity.application as DoraApplication).recording
        // Capture intentionally updates state continuously. Idle synchronization can
        // prevent pressing Pause forever; advance explicit frames while controls run.
        ui.mainClock.autoAdvance = false
        try {
            try {
                ui.waitUntil(30000) {
                    ui.mainClock.advanceTimeByFrame()
                    controller.state.value.recording.phase == RecordingPhase.RECORDING &&
                        controller.state.value.recording.frames > 16000
                }
            } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
                throw AssertionError(
                    "Capture start failed: ${controller.state.value.failure}\n" +
                        controller.diagnosticSummary(),
                    timeout,
                )
            }
            val duration =
                InstrumentationRegistry.getArguments().getString("longRecordingSeconds")?.toInt()
                    ?: 0
            if (duration > 0) {
                require(duration in 10..1799)
                automation.executeShellCommand("input keyevent 223").close()
                val deadline = android.os.SystemClock.elapsedRealtime() + (duration + 60) * 1000L
                while (controller.state.value.recording.frames < duration * 16000L) {
                    assertTrue(
                        controller.diagnosticSummary(),
                        controller.state.value.recording.phase == RecordingPhase.RECORDING,
                    )
                    assertTrue(
                        "Capture stalled",
                        android.os.SystemClock.elapsedRealtime() < deadline,
                    )
                    Thread.sleep(1000)
                }
                if (duration >= 310)
                    assertTrue(controller.state.value.recording.durableFrames > 300 * 16000L)
                else assertTrue(controller.state.value.recording.durableFrames > 0)
                automation.executeShellCommand("input keyevent 224").close()
                automation.executeShellCommand("wm dismiss-keyguard").close()
                ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                ui.waitUntil(10000) {
                    ui.mainClock.advanceTimeByFrame()
                    ui.onAllNodesWithText(uiCopy("Пауза"), useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .size == 1
                }
            }
            clickControl(uiCopy("Пауза"), unmerged = true)
            ui.waitUntil(10000) {
                ui.mainClock.advanceTimeByFrame()
                controller.state.value.recording.phase == RecordingPhase.PAUSED
            }
            clickControl(uiCopy("Продолжить"))
            ui.waitUntil(10000) {
                ui.mainClock.advanceTimeByFrame()
                controller.state.value.recording.phase == RecordingPhase.RECORDING
            }
            clickControl(label(R.string.signal_copy_13))
            ui.onNodeWithText(label(R.string.signal_copy_17)).performClick()
            ui.waitUntil(30000) {
                ui.mainClock.advanceTimeByFrame()
                controller.state.value.recording.phase == RecordingPhase.SAVED
            }
            assertTrue(controller.state.value.recording.durableFrames > 0)
            ui.mainClock.advanceTimeByFrame()
            ui.onNodeWithText(label(R.string.signal_copy_2)).performClick()
            ui.mainClock.advanceTimeByFrame()
            ui.onNodeWithText("DORA").assertExists()
        } catch (error: Throwable) {
            stopAfterFailure(controller, error)
            throw error
        } finally {
            ui.mainClock.autoAdvance = true
        }
    }

    private fun stopAfterFailure(controller: RecordingController, original: Throwable) {
        // Preserve the assertion failure while releasing the shared Application's
        // writer; otherwise later independent vault tests only observe BUSY.
        val pending =
            setOf(
                RecordingPhase.RECORDING,
                RecordingPhase.PAUSED,
                RecordingPhase.PREPARING,
                RecordingPhase.FINALIZING,
            )
        runCatching {
                if (
                    controller.state.value.recording.phase in pending &&
                        controller.state.value.recording.phase != RecordingPhase.FINALIZING
                ) {
                    ui.activityRule.scenario.onActivity {
                        controller.requestStop()
                        controller.confirmStop()
                    }
                }
                ui.waitUntil(30000) { controller.state.value.recording.phase !in pending }
            }
            .exceptionOrNull()
            ?.let(original::addSuppressed)
    }

    private fun clickControl(text: String, unmerged: Boolean = false) {
        ui.mainClock.advanceTimeByFrame()
        ui.onNodeWithText(text, useUnmergedTree = unmerged).performScrollTo()
        ui.mainClock.advanceTimeByFrame()
        ui.onNodeWithText(text, useUnmergedTree = unmerged).performClick()
        ui.mainClock.advanceTimeByFrame()
    }

    private fun label(id: Int) = ui.activity.getString(id)
}
