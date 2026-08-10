package com.monumentogram.dora.poc.capture

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.monumentogram.dora.poc.capture.ui.CaptureTestTags
import org.junit.Rule
import org.junit.Test

class CaptureFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun appOpensOnDeviceScreenAndDoesNotAutoRecord() {
        compose.onNodeWithTag(CaptureTestTags.SCREEN_DEVICE).assertIsDisplayed()
        compose.onNodeWithText("Технический тест. Не является готовой Dora.").assertIsDisplayed()
        compose.onAllNodesWithTag(CaptureTestTags.SCREEN_RECORDING).assertCountEquals(0)
    }

    @Test
    fun preflightStartRequiresReminderCheckbox() {
        openRunAPreflight()

        compose.onNodeWithTag(CaptureTestTags.START).assertIsNotEnabled()
        compose.onNodeWithTag(CaptureTestTags.ACKNOWLEDGEMENT).performClick()
        compose.onNodeWithTag(CaptureTestTags.START).assertIsEnabled()
    }

    @Test
    fun laterRunsRemainLockedBeforeRunACompletes() {
        prepareDevice()

        compose.onNodeWithTag(CaptureTestTags.RUN_A).assertIsEnabled()
        compose.onNodeWithTag(CaptureTestTags.RUN_B).assertIsNotEnabled()
        compose.onNodeWithTag(CaptureTestTags.RUN_C).assertIsNotEnabled()
    }

    private fun openRunAPreflight() {
        prepareDevice()
        compose.onNodeWithTag(CaptureTestTags.RUN_A).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose
                .onAllNodesWithTag(CaptureTestTags.SCREEN_PREFLIGHT)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun prepareDevice() {
        compose.onNodeWithTag(CaptureTestTags.PREPARE_DEVICE).performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose
                .onAllNodesWithTag(CaptureTestTags.SCREEN_RUNS)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
