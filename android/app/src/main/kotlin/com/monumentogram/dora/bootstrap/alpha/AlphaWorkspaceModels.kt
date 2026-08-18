package com.monumentogram.dora.bootstrap.alpha

internal enum class AlphaTaskOrigin {
    USER
}

internal data class AlphaTask(
    val id: String,
    val text: String,
    val completed: Boolean = false,
    val origin: AlphaTaskOrigin = AlphaTaskOrigin.USER,
)

internal data class AlphaConversation(
    val id: String,
    val title: String,
    val notes: String,
    val summary: String,
    val tasks: List<AlphaTask>,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

internal data class AlphaWorkspaceSnapshot(val conversations: List<AlphaConversation> = emptyList())

internal data class AlphaConversationDraft(
    val title: String,
    val notes: String,
    val summary: String,
    val taskLines: String,
)

internal data class AlphaWorkspaceState(
    val snapshot: AlphaWorkspaceSnapshot = AlphaWorkspaceSnapshot(),
    val blockingError: String? = null,
    val operationError: String? = null,
) {
    val isWritable: Boolean
        get() = blockingError == null
}

internal data class AlphaMutationResult(
    val state: AlphaWorkspaceState,
    val changedConversationId: String? = null,
    val succeeded: Boolean,
)

internal fun AlphaWorkspaceSnapshot.collectIds(): Set<String> = buildSet {
    conversations.forEach { conversation ->
        add(conversation.id)
        conversation.tasks.forEach { add(it.id) }
    }
}
