package com.monumentogram.dora.bootstrap.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import com.monumentogram.dora.bootstrap.alpha.AlphaLoadResult
import com.monumentogram.dora.bootstrap.alpha.AlphaSaveResult
import com.monumentogram.dora.bootstrap.alpha.AlphaWorkspaceCodec
import com.monumentogram.dora.bootstrap.alpha.AlphaWorkspaceRepository
import com.monumentogram.dora.bootstrap.alpha.AlphaWorkspaceSnapshot
import com.monumentogram.dora.bootstrap.ui.theme.DoraBootstrapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DoraAlphaWorkflowTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun savesEditsSearchesCompletesAndDeletesManualConversation() {
        val repository = MemoryRepository()
        composeRule.setContent {
            DoraBootstrapTheme {
                DoraBootstrapApp(repository = repository)
            }
        }

        composeRule.onNodeWithTag(AlphaTestTags.NEW_CONVERSATION).performClick()
        enterManualDraft()
        composeRule.onNodeWithTag(AlphaTestTags.SAVE).performScrollTo().performClick()

        composeRule.runOnIdle {
            assertTrue(repository.snapshot.conversations.single().title == "План Alpha")
        }
        composeRule.onNodeWithText("Назад").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Раздел Задачи").performClick()
        composeRule.onNodeWithText("Проверить перезапуск").assertIsDisplayed()

        val taskId = repository.snapshot.conversations.single().tasks.single().id
        composeRule
            .onNodeWithTag(AlphaTestTags.TASK_TOGGLE_PREFIX + taskId)
            .assertContentDescriptionEquals("Проверить перезапуск")
            .assertIsOff()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Не выполнено",
                )
            )
            .performClick()
        composeRule
            .onNodeWithTag(AlphaTestTags.TASK_TOGGLE_PREFIX + taskId)
            .assertContentDescriptionEquals("Проверить перезапуск")
            .assertIsOn()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Выполнено",
                )
            )
        composeRule.runOnIdle {
            assertTrue(repository.snapshot.conversations.single().tasks.single().completed)
        }

        composeRule.onNodeWithContentDescription("Раздел История").performClick()
        composeRule.onNodeWithTag(AlphaTestTags.SEARCH).performTextInput("Синтетический")
        composeRule
            .onNodeWithContentDescription("Открыть разговор План Alpha")
            .assertIsDisplayed()
            .performClick()
        composeRule
            .onNodeWithTag(AlphaTestTags.TITLE)
            .performTextReplacement("План Alpha — изменён")
        composeRule.onNodeWithTag(AlphaTestTags.SAVE).performScrollTo().performClick()
        composeRule.runOnIdle {
            assertTrue(repository.snapshot.conversations.single().title == "План Alpha — изменён")
        }
        composeRule.onNodeWithTag(AlphaTestTags.DELETE).performScrollTo().performClick()
        composeRule.onNodeWithTag(AlphaTestTags.DELETE_CONFIRM).assertIsDisplayed().performClick()

        composeRule.onNodeWithText("Пока нет сохранённых разговоров.").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(repository.snapshot.conversations.isEmpty()) }
    }

    @Test
    fun wideLayoutWithTwoHundredPercentTextKeepsCriticalActionsReachable() {
        val repository = MemoryRepository()
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = LARGE_TEXT_SCALE)
            ) {
                DoraBootstrapTheme {
                    DoraBootstrapApp(
                        forcedLayout = BootstrapNavigationLayout.WIDE_RAIL,
                        repository = repository,
                    )
                }
            }
        }

        composeRule
            .onNodeWithTag(AlphaTestTags.NEW_CONVERSATION)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Раздел История").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Запись недоступна в Alpha 1").assertIsDisplayed()
    }

    @Test
    fun oversizedEditorPasteIsRejectedWithVisibleError() {
        val repository = MemoryRepository()
        composeRule.setContent {
            DoraBootstrapTheme {
                DoraBootstrapApp(repository = repository)
            }
        }

        composeRule.onNodeWithTag(AlphaTestTags.NEW_CONVERSATION).performClick()
        composeRule
            .onNodeWithTag(AlphaTestTags.TITLE)
            .performTextInput("x".repeat(AlphaWorkspaceCodec.MAX_TITLE_BYTES + 1))

        composeRule.onNodeWithTag(AlphaTestTags.TITLE).assertTextEquals("")
        composeRule
            .onNodeWithText(
                "Текст не добавлен: все поля вместе ограничены 64 КБ, " +
                    "действуют лимиты отдельных полей и проверка символов."
            )
            .assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(repository.snapshot.conversations.isEmpty()) }
    }

    @Test
    fun unsavedBoundedDraftSurvivesSavedStateRestoration() {
        val repository = MemoryRepository()
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            DoraBootstrapTheme {
                DoraBootstrapApp(repository = repository)
            }
        }
        composeRule.onNodeWithTag(AlphaTestTags.NEW_CONVERSATION).performClick()
        composeRule.onNodeWithTag(AlphaTestTags.TITLE).performTextInput("Черновик")
        composeRule
            .onNodeWithTag(AlphaTestTags.NOTES)
            .performScrollTo()
            .performTextInput("Несохранённые заметки")
        composeRule
            .onNodeWithTag(AlphaTestTags.SUMMARY)
            .performScrollTo()
            .performTextInput("Несохранённое резюме")
        composeRule
            .onNodeWithTag(AlphaTestTags.TASK_LINES)
            .performScrollTo()
            .performTextInput("Несохранённая задача")

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule
            .onNodeWithTag(AlphaTestTags.TITLE)
            .performScrollTo()
            .assertTextContains("Черновик")
        composeRule
            .onNodeWithTag(AlphaTestTags.NOTES)
            .performScrollTo()
            .assertTextContains("Несохранённые заметки")
        composeRule
            .onNodeWithTag(AlphaTestTags.SUMMARY)
            .performScrollTo()
            .assertTextContains("Несохранённое резюме")
        composeRule
            .onNodeWithTag(AlphaTestTags.TASK_LINES)
            .performScrollTo()
            .assertTextContains("Несохранённая задача")
        composeRule.runOnIdle { assertTrue(repository.snapshot.conversations.isEmpty()) }
    }

    private fun enterManualDraft() {
        composeRule.onNodeWithTag(AlphaTestTags.TITLE).performTextInput("План Alpha")
        composeRule
            .onNodeWithTag(AlphaTestTags.NOTES)
            .performScrollTo()
            .performTextInput("Синтетический текст встречи")
        composeRule
            .onNodeWithTag(AlphaTestTags.SUMMARY)
            .performScrollTo()
            .performTextInput("Резюме введено вручную")
        composeRule
            .onNodeWithTag(AlphaTestTags.TASK_LINES)
            .performScrollTo()
            .performTextInput("Проверить перезапуск")
    }

    private class MemoryRepository : AlphaWorkspaceRepository {
        var snapshot = AlphaWorkspaceSnapshot()

        override fun load(): AlphaLoadResult = AlphaLoadResult.Ready(snapshot)

        override fun save(snapshot: AlphaWorkspaceSnapshot): AlphaSaveResult {
            this.snapshot = snapshot
            return AlphaSaveResult.Saved
        }
    }

    private companion object {
        const val LARGE_TEXT_SCALE = 2f
    }
}
