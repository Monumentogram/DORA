package com.monumentogram.dora.bootstrap

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DoraBootstrapAppTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

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
    fun recordActionShowsUnavailableNoticeInsteadOfStartingRecording() {
        composeRule
            .onNodeWithContentDescription("Запись недоступна в Stage 00")
            .assertIsDisplayed()
            .performClick()

        composeRule
            .onNodeWithText("Запись пока недоступна: Stage 00 не запрашивает доступ к микрофону.")
            .assertIsDisplayed()
    }
}
