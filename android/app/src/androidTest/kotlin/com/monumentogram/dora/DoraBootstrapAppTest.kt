package com.monumentogram.dora

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.audio.recording.RecordingState
import com.monumentogram.dora.recording.CaptureFailure
import com.monumentogram.dora.recording.RecordingScreen
import com.monumentogram.dora.recording.RecordingViewState
import com.monumentogram.dora.ui.theme.DoraBootstrapTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DoraBootstrapAppTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun compactPreflightKeepsActionsVisibleWithoutStartingMicrophone() {
        assertCompactPreflight(1f)
    }

    @Test
    fun largeFontCompactPreflightKeepsActionsVisibleWhileInformationScrolls() {
        assertCompactPreflight(2f)
    }

    @Test
    fun largeFontPreflightWithImeSizedHeightKeepsCancelReachable() {
        var cancelled = false
        render(
            RecordingViewState(),
            fontScale = 2f,
            viewport = DpSize(320.dp, 240.dp),
            onBack = { cancelled = true },
        )
        composeRule.onNodeWithText("Отмена").assertIsDisplayed().performClick()
        composeRule.runOnIdle { org.junit.Assert.assertTrue(cancelled) }
        composeRule.onNodeWithText("Начать запись").assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithText("Обновить проверку места").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun landscapeLargeFontPreflightKeepsConsentExplicit() {
        render(RecordingViewState(), fontScale = 2f, viewport = DpSize(480.dp, 320.dp))
        composeRule.onNodeWithText("Отмена").assertIsDisplayed()
        composeRule.onNodeWithText("Начать запись").assertIsDisplayed().assertIsNotEnabled()
        composeRule
            .onNodeWithContentDescription("Я предупредил(а) участников о записи")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Начать запись").assertIsDisplayed().assertIsEnabled()
        composeRule.runOnIdle {
            org.junit.Assert.assertFalse(
                (composeRule.activity.application as DoraApplication)
                    .recording
                    .state
                    .value
                    .captureThreadHealthy
            )
        }
    }

    @Test
    fun insufficientStorageRemainsBlockedAfterExplicitConsentInCompactPreflight() {
        val controller = (composeRule.activity.application as DoraApplication).recording
        val field =
            controller.javaClass.getDeclaredField("freeStorageBytes").apply { isAccessible = true }
        val original = field.get(controller)
        try {
            composeRule.runOnIdle { field.set(controller, { 0L }) }
            render(RecordingViewState(), fontScale = 2f, viewport = DpSize(320.dp, 320.dp))
            composeRule
                .onNodeWithContentDescription("Я предупредил(а) участников о записи")
                .performScrollTo()
                .performClick()
            composeRule.onNodeWithText("Начать запись").assertIsDisplayed().assertIsNotEnabled()
            composeRule
                .onNodeWithText(
                    "Освободите место и обновите проверку. Сохранённые записи останутся доступны."
                )
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onNodeWithText("Обновить проверку места").performScrollTo().performClick()
            composeRule.onNodeWithText("Начать запись").assertIsNotEnabled()
            composeRule.onNodeWithText("Отмена").assertIsDisplayed()
            composeRule.runOnIdle {
                org.junit.Assert.assertFalse(controller.state.value.captureThreadHealthy)
            }
        } finally {
            composeRule.runOnIdle { field.set(controller, original) }
        }
    }

    @Test
    fun grantingMicrophonePermissionDoesNotGrantConsentOrStartDuringRecreation() {
        val instrumentation =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation
            .executeShellCommand(
                "pm grant com.monumentogram.dora.debug android.permission.RECORD_AUDIO"
            )
            .use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
        composeRule.onNodeWithContentDescription("Открыть экран записи").performClick()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("Начать запись").assertIsDisplayed().assertIsNotEnabled()
        composeRule
            .onNodeWithText("Микрофон: доступ разрешён")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Отмена").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("Раздел Главная").assertIsDisplayed()
        composeRule.runOnIdle {
            org.junit.Assert.assertFalse(
                (composeRule.activity.application as DoraApplication)
                    .recording
                    .state
                    .value
                    .captureThreadHealthy
            )
        }
    }

    private fun assertCompactPreflight(fontScale: Float) {
        render(RecordingViewState(), fontScale = fontScale, viewport = DpSize(320.dp, 480.dp))
        composeRule.onNodeWithText("Отмена").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("Начать запись").assertIsDisplayed().assertIsNotEnabled()
        composeRule
            .onNodeWithContentDescription("Я предупредил(а) участников о записи")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Обновить проверку места").performScrollTo().performClick()
        composeRule.onNodeWithText("Отмена").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            org.junit.Assert.assertFalse(
                (composeRule.activity.application as DoraApplication)
                    .recording
                    .state
                    .value
                    .captureThreadHealthy
            )
        }
    }

    @Test
    fun largeFontDurabilityCatchUpKeepsResumeStationary() {
        render(
            RecordingViewState(recording = RecordingState(RecordingPhase.PAUSED, 32000, 0)),
            fontScale = 2f,
        )
        val pendingTop =
            composeRule.onNodeWithText("Продолжить").fetchSemanticsNode().boundsInRoot.top
        render(
            RecordingViewState(recording = RecordingState(RecordingPhase.PAUSED, 32000, 32000)),
            fontScale = 2f,
        )
        org.junit.Assert.assertEquals(
            pendingTop,
            composeRule.onNodeWithText("Продолжить").fetchSemanticsNode().boundsInRoot.top,
        )
    }

    @Test
    fun durabilityCatchUpDoesNotMoveResumeUnderUsersFinger() {
        render(RecordingViewState(recording = RecordingState(RecordingPhase.PAUSED, 32000, 0)))
        val pendingTop =
            composeRule.onNodeWithText("Продолжить").fetchSemanticsNode().boundsInRoot.top
        render(RecordingViewState(recording = RecordingState(RecordingPhase.PAUSED, 32000, 32000)))
        org.junit.Assert.assertEquals(
            pendingTop,
            composeRule.onNodeWithText("Продолжить").fetchSemanticsNode().boundsInRoot.top,
        )
    }

    @Test
    fun pausedPendingTailKeepsResumeEnabledWithoutFalseSaved() {
        render(RecordingViewState(recording = RecordingState(RecordingPhase.PAUSED, 32000, 0)))
        composeRule.onNodeWithText("Запись приостановлена").assertIsDisplayed()
        composeRule.onNodeWithText("Продолжить").assertIsEnabled()
        composeRule.onNodeWithText("Сохраняем последние секунды…").assertIsDisplayed()
        composeRule.onNodeWithText("Запись сохранена").assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(5000)
        composeRule.onNodeWithText("00:00:02").assertIsDisplayed()
    }

    @Test
    fun resumedPendingTailKeepsPauseEnabled() {
        render(RecordingViewState(recording = RecordingState(RecordingPhase.RECORDING, 32000, 0)))
        composeRule.onNodeWithText("Запись продолжается").assertIsDisplayed()
        composeRule.onNodeWithText("Пауза").assertIsEnabled()
        composeRule.onNodeWithText("Сохраняем…").assertIsDisplayed()
        composeRule.onNodeWithText("Запись сохранена").assertDoesNotExist()
    }

    // Presentation-only fixtures. No capture, writer, authentication or runtime state is replaced.
    private fun render(
        snapshot: RecordingViewState,
        authorized: Boolean = true,
        fontScale: Float = 1f,
        viewport: DpSize? = null,
        onBack: () -> Unit = {},
    ) {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale)
                ) {
                    DoraBootstrapTheme {
                        Box(
                            when {
                                viewport != null -> Modifier.requiredSize(viewport)
                                else -> Modifier
                            }
                        ) {
                            RecordingScreen(composeRule.activity, snapshot, authorized, onBack)
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun activePresentationSeparatesCapturedAndDurableTime() {
        render(
            RecordingViewState(
                recording =
                    RecordingState(
                        phase = RecordingPhase.RECORDING,
                        frames = 32000,
                        durableFrames = 16000,
                    )
            )
        )
        composeRule.onNodeWithText("Запись продолжается").assertIsDisplayed()
        composeRule.onNodeWithText("00:00:02").assertIsDisplayed()
        composeRule.onNodeWithText("Сохранено на устройстве: 00:00:01").assertIsDisplayed()
        composeRule.onNodeWithText("Пауза").assertIsEnabled()
        composeRule.onNodeWithText("Стоп").assertIsEnabled()
    }

    @Test
    fun pausedPresentationOffersExplicitResumeAndStableFrameTime() {
        render(
            RecordingViewState(
                recording =
                    RecordingState(
                        phase = RecordingPhase.PAUSED,
                        frames = 32000,
                        durableFrames = 32000,
                    )
            )
        )
        composeRule.onNodeWithText("Запись приостановлена").assertIsDisplayed()
        composeRule.onNodeWithText("Продолжить").assertIsEnabled()
        composeRule.onNodeWithText("00:00:02").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(5000)
        composeRule.onNodeWithText("00:00:02").assertIsDisplayed()
    }

    @Test
    fun lockedPresentationHidesCapturedTimeButAllowsPauseAndStop() {
        render(
            RecordingViewState(
                recording =
                    RecordingState(
                        phase = RecordingPhase.RECORDING,
                        frames = 32000,
                    )
            ),
            authorized = false,
        )
        composeRule.onNodeWithText("00:00:02").assertDoesNotExist()
        composeRule.onNodeWithText("Разблокировать").assertIsDisplayed()
        composeRule.onNodeWithText("Пауза").assertIsEnabled()
        composeRule.onNodeWithText("Стоп").assertIsEnabled()
    }

    @Test
    fun stopConfirmationTruthfullyPreservesPausedState() {
        render(
            RecordingViewState(
                recording =
                    RecordingState(
                        phase = RecordingPhase.PAUSED,
                        stopConfirmation = true,
                    )
            )
        )
        composeRule.onNodeWithText("Завершить запись?").assertIsDisplayed()
        composeRule.onNodeWithText("Продолжить запись").assertIsEnabled()
        composeRule.onNodeWithText("Завершить").assertIsEnabled()
    }

    @Test
    fun finalizingPresentationDoesNotClaimSaved() {
        render(RecordingViewState(recording = RecordingState(phase = RecordingPhase.FINALIZING)))
        composeRule.onNodeWithText("Проверяем сохранение").assertIsDisplayed()
        composeRule.onNodeWithText("Запись сохранена").assertDoesNotExist()
        composeRule.onNodeWithText("Стоп").assertDoesNotExist()
    }

    @Test
    fun permissionFailurePresentationDoesNotClaimCaptureOrSaved() {
        render(
            RecordingViewState(
                recording = RecordingState(phase = RecordingPhase.INTERRUPTED),
                failure = CaptureFailure.PERMISSION_DENIED,
            )
        )
        composeRule.onNodeWithText("Запись прервана").assertIsDisplayed()
        composeRule.onNodeWithText("Запись продолжается").assertDoesNotExist()
        composeRule.onNodeWithText("Запись сохранена").assertDoesNotExist()
    }

    @Test
    fun preflightSurvivesActivityRecreationWithoutStartingMicrophone() {
        composeRule.onNodeWithContentDescription("Открыть экран записи").performClick()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("Перед началом записи").assertIsDisplayed()
        composeRule.onNodeWithText("Начать запись").assertIsNotEnabled()
        composeRule
            .onNodeWithContentDescription("Я предупредил(а) участников о записи")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Отмена").assertIsDisplayed()
        composeRule
            .onNodeWithText("Разблокировать сохранённые записи")
            .performScrollTo()
            .assertIsEnabled()
        composeRule.runOnIdle {
            org.junit.Assert.assertFalse(
                (composeRule.activity.application as DoraApplication)
                    .recording
                    .state
                    .value
                    .captureThreadHealthy
            )
        }
    }

    @Test
    fun showsFourDestinationsAndChangesSelectedSection() {
        val home = "Раздел Главная"
        val history = "Раздел История"
        val tasks = "Раздел Задачи"
        val settings = "Раздел Настройки"

        composeRule.onNodeWithContentDescription(home).assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithContentDescription(history).assertIsDisplayed().assertIsNotSelected()
        composeRule.onNodeWithContentDescription(tasks).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(settings).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(history).performClick()

        composeRule.onNodeWithContentDescription(home).assertIsNotSelected()
        composeRule.onNodeWithContentDescription(history).assertIsSelected()
    }

    @Test
    fun recordActionOpensPreflightWithoutStartingMicrophone() {
        composeRule
            .onNodeWithContentDescription("Открыть экран записи")
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithText("Перед началом записи").assertIsDisplayed()
        composeRule.onNodeWithText("Начать запись").assertIsNotEnabled()
        composeRule.runOnIdle {
            val app = composeRule.activity.application as DoraApplication
            org.junit.Assert.assertEquals(
                com.monumentogram.dora.audio.recording.RecordingPhase.PREFLIGHT,
                app.recording.state.value.recording.phase,
            )
        }
    }

    @Test
    fun leavingPreflightReturnsToNavigationWithoutStartingCapture() {
        composeRule.onNodeWithContentDescription("Открыть экран записи").performClick()
        composeRule.onNodeWithText("Отмена").performClick()
        composeRule.onNodeWithContentDescription("Раздел Главная").assertIsDisplayed()
        composeRule.runOnIdle {
            val app = composeRule.activity.application as DoraApplication
            org.junit.Assert.assertFalse(app.recording.state.value.captureThreadHealthy)
        }
    }

    @Test
    fun staleNotificationCannotStartOrStopRecording() {
        composeRule.runOnIdle {
            val originalIntent = composeRule.activity.intent
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .callActivityOnNewIntent(
                    composeRule.activity,
                    android.content
                        .Intent(composeRule.activity, MainActivity::class.java)
                        .setAction(com.monumentogram.dora.recording.ProductRecordingService.STOP)
                        .putExtra(
                            com.monumentogram.dora.recording.ProductRecordingService.TOKEN,
                            "expired",
                        )
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            // ActivityScenario matches teardown events against its original launch intent.
            // onNewIntent already copied the delivered action into observable product state.
            composeRule.activity.intent = originalIntent
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Раздел Главная").assertIsDisplayed()
        composeRule.runOnIdle {
            val app = composeRule.activity.application as DoraApplication
            org.junit.Assert.assertFalse(app.recording.state.value.recording.stopConfirmation)
            org.junit.Assert.assertFalse(app.recording.state.value.captureThreadHealthy)
        }
    }
}
