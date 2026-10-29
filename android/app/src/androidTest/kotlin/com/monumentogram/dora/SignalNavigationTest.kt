package com.monumentogram.dora

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class SignalNavigationTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeSettingsAndRecordingAreRealDestinations() {
        ui.onNodeWithText("DORA").assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.nav_settings)).performClick()
        ui.onNodeWithText(ui.activity.getString(R.string.signal_local_model)).assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.signal_about))
            .performScrollTo()
            .assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.signal_recordings)).performClick()
        ui.onNodeWithText(
                ui.activity.getString(R.string.signal_new_recording),
                useUnmergedTree = true,
            )
            .performClick()
        ui.onNodeWithText(ui.activity.getString(R.string.signal_copy_4)).assertExists()
        ui.onNodeWithText(ui.activity.getString(R.string.signal_copy_2)).performClick()
        ui.onNodeWithText("DORA").assertExists()
    }
}
