package com.monumentogram.dora.bootstrap.alpha

import java.util.UUID

internal class AlphaWorkspaceController(
    private val repository: AlphaWorkspaceRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    var state: AlphaWorkspaceState =
        when (val result = repository.load()) {
            is AlphaLoadResult.Ready -> AlphaWorkspaceState(snapshot = result.snapshot)
            is AlphaLoadResult.Unavailable ->
                AlphaWorkspaceState(blockingError = result.userMessage)
        }
        private set

    fun saveConversation(
        existingConversationId: String?,
        draft: AlphaConversationDraft,
    ): AlphaMutationResult =
        when {
            !state.isWritable -> unchangedFailure()
            draft.title.isBlank() -> operationFailure("Добавьте название разговора.")
            else -> saveValidatedConversation(existingConversationId, draft)
        }

    private fun saveValidatedConversation(
        existingConversationId: String?,
        draft: AlphaConversationDraft,
    ): AlphaMutationResult {
        val title = draft.title.trim()
        val taskTexts = draft.taskLines.lines().map(String::trim).filter(String::isNotEmpty)
        val existing = existingConversationId?.let { id ->
            state.snapshot.conversations.firstOrNull { it.id == id }
        }
        return when {
            taskTexts.size > AlphaWorkspaceCodec.MAX_TASKS_PER_CONVERSATION ->
                operationFailure("В одном разговоре Alpha доступно не более 100 задач.")
            existingConversationId != null && existing == null ->
                operationFailure("Разговор больше не найден.")
            else -> saveResolvedConversation(existing, title, draft, taskTexts)
        }
    }

    private fun saveResolvedConversation(
        existing: AlphaConversation?,
        title: String,
        draft: AlphaConversationDraft,
        taskTexts: List<String>,
    ): AlphaMutationResult {
        val occupiedIds = state.snapshot.collectIds().toMutableSet()
        val conversationId = existing?.id ?: nextUniqueId("conversation", occupiedIds)
        val tasks = conversationId?.let { buildTasks(existing, taskTexts, occupiedIds) }
        if (conversationId == null || tasks == null) {
            return operationFailure(ID_ALLOCATION_MESSAGE)
        }
        val now = clock().coerceAtLeast(0)
        val createdAt = existing?.createdAtEpochMillis ?: now
        val updatedAt = maxOf(now, createdAt, existing?.updatedAtEpochMillis ?: 0)
        val conversation =
            AlphaConversation(
                id = conversationId,
                title = title,
                notes = draft.notes,
                summary = draft.summary,
                tasks = tasks,
                createdAtEpochMillis = createdAt,
                updatedAtEpochMillis = updatedAt,
            )
        val conversations =
            state.snapshot.conversations
                .filterNot { it.id == conversationId }
                .plus(conversation)
                .sortedByDescending(AlphaConversation::updatedAtEpochMillis)
        return persist(
            candidate = AlphaWorkspaceSnapshot(conversations),
            changedConversationId = conversationId,
        )
    }

    private fun buildTasks(
        existing: AlphaConversation?,
        taskTexts: List<String>,
        occupiedIds: MutableSet<String>,
    ): List<AlphaTask>? {
        val unmatchedExistingTasks = existing?.tasks.orEmpty().toMutableList()
        val tasks = mutableListOf<AlphaTask>()
        for (text in taskTexts) {
            val matchingIndex = unmatchedExistingTasks.indexOfFirst { it.text == text }
            val task =
                if (matchingIndex >= 0) {
                    unmatchedExistingTasks.removeAt(matchingIndex)
                } else {
                    val taskId = nextUniqueId("task", occupiedIds) ?: return null
                    AlphaTask(id = taskId, text = text)
                }
            tasks += task
        }
        return tasks
    }

    fun toggleTask(conversationId: String, taskId: String): AlphaMutationResult =
        if (!state.isWritable) {
            unchangedFailure()
        } else {
            var found = false
            val now = clock().coerceAtLeast(0)
            val conversations =
                state.snapshot.conversations.map { conversation ->
                    if (conversation.id == conversationId) {
                        val tasks =
                            conversation.tasks.map { task ->
                                if (task.id == taskId) {
                                    found = true
                                    task.copy(completed = !task.completed)
                                } else {
                                    task
                                }
                            }
                        conversation.copy(
                            tasks = tasks,
                            updatedAtEpochMillis =
                                maxOf(
                                    now,
                                    conversation.createdAtEpochMillis,
                                    conversation.updatedAtEpochMillis,
                                ),
                        )
                    } else {
                        conversation
                    }
                }
            if (found) {
                persist(
                    candidate = AlphaWorkspaceSnapshot(conversations),
                    changedConversationId = conversationId,
                )
            } else {
                operationFailure("Задача больше не найдена.")
            }
        }

    fun deleteConversation(conversationId: String): AlphaMutationResult =
        if (!state.isWritable) {
            unchangedFailure()
        } else {
            val conversations = state.snapshot.conversations.filterNot { it.id == conversationId }
            if (conversations.size == state.snapshot.conversations.size) {
                operationFailure("Разговор больше не найден.")
            } else {
                persist(candidate = AlphaWorkspaceSnapshot(conversations))
            }
        }

    private fun persist(
        candidate: AlphaWorkspaceSnapshot,
        changedConversationId: String? = null,
    ): AlphaMutationResult =
        when (val result = repository.save(candidate)) {
            AlphaSaveResult.Saved -> {
                state = AlphaWorkspaceState(snapshot = candidate)
                AlphaMutationResult(
                    state = state,
                    changedConversationId = changedConversationId,
                    succeeded = true,
                )
            }
            is AlphaSaveResult.Failed -> operationFailure(result.userMessage)
        }

    private fun operationFailure(message: String): AlphaMutationResult {
        state = state.copy(operationError = message)
        return AlphaMutationResult(state = state, succeeded = false)
    }

    private fun unchangedFailure(): AlphaMutationResult =
        AlphaMutationResult(state = state, succeeded = false)

    private fun nextUniqueId(prefix: String, occupiedIds: MutableSet<String>): String? {
        repeat(MAX_ID_ATTEMPTS) {
            val candidate = "$prefix-${idFactory()}"
            if (
                candidate.length <= AlphaWorkspaceCodec.MAX_ID_BYTES && occupiedIds.add(candidate)
            ) {
                return candidate
            }
        }
        return null
    }

    private companion object {
        const val MAX_ID_ATTEMPTS = 10
        const val ID_ALLOCATION_MESSAGE =
            "Не удалось создать безопасный идентификатор. Изменение не сохранено."
    }
}
