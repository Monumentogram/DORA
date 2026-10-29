package com.monumentogram.dora

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.ui.signal.uiCopy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignalRecordingFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test
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
