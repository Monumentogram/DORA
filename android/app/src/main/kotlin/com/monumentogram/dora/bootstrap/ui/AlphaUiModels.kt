package com.monumentogram.dora.bootstrap.ui

import com.monumentogram.dora.bootstrap.alpha.AlphaConversationDraft

internal data class AlphaEditorSelection(
    val isOpen: Boolean,
    val conversationId: String?,
)

internal data class AlphaConversationActions(
    val create: () -> Unit,
    val open: (String) -> Unit,
    val close: () -> Unit,
    val save: (String?, AlphaConversationDraft) -> Unit,
    val delete: (String) -> Unit,
)

internal data class AlphaUiActions(
    val conversation: AlphaConversationActions,
    val toggleTask: (String, String) -> Unit,
)
