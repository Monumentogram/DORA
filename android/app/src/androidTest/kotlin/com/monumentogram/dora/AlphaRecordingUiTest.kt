package com.monumentogram.dora

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.audio.recording.RecordingState
import com.monumentogram.dora.recording.RecordingScreen
import com.monumentogram.dora.recording.RecordingViewState
import com.monumentogram.dora.ui.theme.DoraBootstrapTheme
import org.junit.Rule
import org.junit.Test

class AlphaRecordingUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun elapsedAndRemainingUseCapturedFrames() {
        render(RecordingPhase.PAUSED, 4_784_000)
        rule.onNodeWithText("00:04:59").assertIsDisplayed()
        rule.onNodeWithText("Осталось в Alpha: 00:00:01").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun capSavingNeverClaimsSaved() {
        render(RecordingPhase.FINALIZING, 4_800_000)
        rule.onNodeWithText("Проверяем сохранение").assertIsDisplayed()
        rule.onNodeWithText(SAVED).assertDoesNotExist()
    }

    @Test
    fun onlyDurableSavedShowsCapCompletion() {
        render(RecordingPhase.SAVED, 4_800_000)
        rule.onNodeWithText(SAVED).assertIsDisplayed()
    }

    @Test
    fun interruptedCapShowsActualFailure() {
        render(RecordingPhase.INTERRUPTED, 4_800_000)
        rule.onNodeWithText("Запись прервана").assertIsDisplayed()
        rule.onNodeWithText(SAVED).assertDoesNotExist()
    }

    private fun render(phase: RecordingPhase, frames: Long) {
        rule.activity.setContent {
            DoraBootstrapTheme {
                RecordingScreen(
                    rule.activity,
                    RecordingViewState(
                        RecordingState(
                            phase,
                            frames,
                            if (phase == RecordingPhase.SAVED) frames else 0,
                        )
                    ),
                    authorized = true,
                    onBack = {},
                )
            }
        }
    }

    companion object {
        private const val SAVED = "Достигнут лимит записи Alpha — 5 минут. Запись сохранена"
    }
}
