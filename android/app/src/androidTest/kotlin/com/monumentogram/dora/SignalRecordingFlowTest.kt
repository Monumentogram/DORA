package com.monumentogram.dora

import android.content.Intent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.recording.ProductRecordingService
import com.monumentogram.dora.ui.signal.uiCopy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignalRecordingFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test
    @Suppress("LongMethod") // One user flow includes optional bounded screen-off duration.
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
        try {
            ui.waitUntil(30000) {
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
            InstrumentationRegistry.getArguments().getString("longRecordingSeconds")?.toInt() ?: 0
        if (duration > 0) {
            require(duration in 310..1799)
            automation.executeShellCommand("input keyevent 3").close()
            automation.executeShellCommand("input keyevent 223").close()
            val deadline = android.os.SystemClock.elapsedRealtime() + (duration + 60) * 1000L
            while (controller.state.value.recording.frames < duration * 16000L) {
                assertTrue(
                    controller.diagnosticSummary(),
                    controller.state.value.recording.phase == RecordingPhase.RECORDING,
                )
                assertTrue("Capture stalled", android.os.SystemClock.elapsedRealtime() < deadline)
                Thread.sleep(1000)
            }
            assertTrue(controller.state.value.recording.durableFrames > 300 * 16000L)
            automation.executeShellCommand("input keyevent 224").close()
            automation.executeShellCommand("wm dismiss-keyguard").close()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .setAction(ProductRecordingService.OPEN)
                    .putExtra(ProductRecordingService.TOKEN, controller.actionToken)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
            )
            ui.waitUntil(10000) {
                ui.onAllNodesWithText(uiCopy("Пауза"), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .size == 1
            }
        }
        ui.onNodeWithText(uiCopy("Пауза"), useUnmergedTree = true).performScrollTo().performClick()
        ui.waitUntil(10000) { controller.state.value.recording.phase == RecordingPhase.PAUSED }
        ui.onNodeWithText(uiCopy("Продолжить")).performScrollTo().performClick()
        ui.waitUntil(10000) { controller.state.value.recording.phase == RecordingPhase.RECORDING }
        ui.onNodeWithText(label(R.string.signal_copy_13)).performScrollTo().performClick()
        ui.onNodeWithText(label(R.string.signal_copy_17)).performClick()
        ui.waitUntil(30000) { controller.state.value.recording.phase == RecordingPhase.SAVED }
        assertTrue(controller.state.value.recording.durableFrames > 0)
        ui.onNodeWithText(label(R.string.signal_copy_2)).performClick()
        ui.onNodeWithText("DORA").assertExists()
    }

    private fun label(id: Int) = ui.activity.getString(id)
}
