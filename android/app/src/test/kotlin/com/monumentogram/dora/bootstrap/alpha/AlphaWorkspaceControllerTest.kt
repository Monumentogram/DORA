package com.monumentogram.dora.bootstrap.alpha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphaWorkspaceControllerTest {
    @Test
    fun createEditRestartSearchToggleAndDeleteUsePersistedSnapshots() {
        val repository = MemoryRepository()
        var now = 1_000L
        var id = 0
        var controller =
            AlphaWorkspaceController(
                repository = repository,
                clock = { now++ },
                idFactory = { (++id).toString() },
            )

        val created = createConversation(controller)

        assertTrue(created.succeeded)
        val conversationId = requireNotNull(created.changedConversationId)
        assertEquals(1, repository.snapshot.conversations.size)
        assertTrue(
            repository.snapshot.conversations.single().tasks.all {
                it.origin == AlphaTaskOrigin.USER
            }
        )

        controller =
            AlphaWorkspaceController(
                repository = repository,
                clock = { now++ },
                idFactory = { (++id).toString() },
            )
        val restartedConversation = controller.state.snapshot.conversations.single()
        val firstTaskId = restartedConversation.tasks.first().id
        assertEquals("План Alpha", restartedConversation.title)
        assertEquals(1, controller.state.snapshot.searchConversations("перезапуск").size)
        assertTrue(controller.state.snapshot.searchConversations("нет совпадения").isEmpty())

        val toggled = controller.toggleTask(conversationId, firstTaskId)
        assertTrue(toggled.succeeded)
        assertTrue(repository.snapshot.conversations.single().tasks.first().completed)

        val edited =
            controller.saveConversation(
                existingConversationId = conversationId,
                draft =
                    AlphaConversationDraft(
                        title = "План Alpha — обновлён",
                        notes = "Только синтетический текст",
                        summary = "Ручное резюме изменено",
                        taskLines = "Проверить перезапуск\nПроверить удаление",
                    ),
            )
        assertTrue(edited.succeeded)
        assertTrue(repository.snapshot.conversations.single().tasks.first().completed)

        val deleted = controller.deleteConversation(conversationId)
        assertTrue(deleted.succeeded)
        assertTrue(repository.snapshot.conversations.isEmpty())
        assertTrue(AlphaWorkspaceController(repository).state.snapshot.conversations.isEmpty())
    }

    @Test
    fun failedSaveKeepsPreviousPersistedAndVisibleState() {
        val repository = MemoryRepository()
        val controller =
            AlphaWorkspaceController(
                repository = repository,
                clock = { 1_000 },
                idFactory = { "stable" },
            )
        repository.failSaves = true

        val result =
            controller.saveConversation(
                existingConversationId = null,
                draft = AlphaConversationDraft("Title", "Notes", "Summary", "Task"),
            )

        assertFalse(result.succeeded)
        assertTrue(result.state.snapshot.conversations.isEmpty())
        assertTrue(repository.snapshot.conversations.isEmpty())
        assertEquals("Контролируемый сбой записи", result.state.operationError)
    }

    @Test
    fun corruptLoadBlocksMutationWithoutOverwritingSource() {
        val repository =
            MemoryRepository(
                loadResult = AlphaLoadResult.Unavailable("Повреждённый тестовый снимок")
            )
        val controller = AlphaWorkspaceController(repository)

        val result =
            controller.saveConversation(
                existingConversationId = null,
                draft = AlphaConversationDraft("Title", "", "", ""),
            )

        assertFalse(result.succeeded)
        assertFalse(result.state.isWritable)
        assertEquals("Повреждённый тестовый снимок", result.state.blockingError)
        assertEquals(0, repository.saveCalls)
        assertNull(result.changedConversationId)
    }

    @Test
    fun idCollisionExhaustionIsControlledAndDoesNotSave() {
        val repository = MemoryRepository()
        repository.snapshot =
            AlphaWorkspaceSnapshot(
                listOf(
                    AlphaConversation(
                        id = "conversation-collision",
                        title = "Existing",
                        notes = "",
                        summary = "",
                        tasks = emptyList(),
                        createdAtEpochMillis = 1,
                        updatedAtEpochMillis = 1,
                    )
                )
            )
        val controller =
            AlphaWorkspaceController(
                repository = repository,
                clock = { 2 },
                idFactory = { "collision" },
            )

        val result =
            controller.saveConversation(
                existingConversationId = null,
                draft = AlphaConversationDraft("New", "", "", ""),
            )

        assertFalse(result.succeeded)
        assertEquals(0, repository.saveCalls)
        assertEquals(1, result.state.snapshot.conversations.size)
        assertEquals(
            "Не удалось создать безопасный идентификатор. Изменение не сохранено.",
            result.state.operationError,
        )
    }

    private fun createConversation(controller: AlphaWorkspaceController): AlphaMutationResult =
        controller.saveConversation(
            existingConversationId = null,
            draft =
                AlphaConversationDraft(
                    title = "План Alpha",
                    notes = "Только тестовые данные",
                    summary = "Ручное резюме",
                    taskLines = "Проверить перезапуск\nПроверить удаление",
                ),
        )

    private class MemoryRepository(private val loadResult: AlphaLoadResult? = null) :
        AlphaWorkspaceRepository {
        var snapshot = AlphaWorkspaceSnapshot()
        var failSaves = false
        var saveCalls = 0

        override fun load(): AlphaLoadResult = loadResult ?: AlphaLoadResult.Ready(snapshot)

        override fun save(snapshot: AlphaWorkspaceSnapshot): AlphaSaveResult {
            saveCalls += 1
            if (failSaves) return AlphaSaveResult.Failed("Контролируемый сбой записи")
            this.snapshot = snapshot
            return AlphaSaveResult.Saved
        }
    }
}
