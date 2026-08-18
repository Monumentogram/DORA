package com.monumentogram.dora.bootstrap.ui

import com.monumentogram.dora.bootstrap.alpha.AlphaMutationResult
import com.monumentogram.dora.model.BootstrapAction
import com.monumentogram.dora.model.BootstrapDestination
import com.monumentogram.dora.model.BootstrapEffect

internal data class AlphaUiMessages(
    val saved: String,
    val deleted: String,
    val recordingUnavailable: String,
)

internal data class BootstrapShellActions(
    val selectDestination: (BootstrapDestination) -> Unit,
    val invokeRecording: () -> Unit,
)

internal fun AlphaAppState.createUiActions(
    messages: AlphaUiMessages,
    showMessage: (String) -> Unit,
): AlphaUiActions =
    AlphaUiActions(
        conversation =
            AlphaConversationActions(
                create = ::openNewConversation,
                open = ::openConversation,
                close = ::closeEditor,
                save = { conversationId, draft ->
                    saveConversation(conversationId, draft)
                        .userMessage(messages.saved)
                        ?.let(showMessage)
                },
                delete = { conversationId ->
                    deleteConversation(conversationId)
                        .userMessage(messages.deleted)
                        ?.let(showMessage)
                },
            ),
        toggleTask = { conversationId, taskId ->
            toggleTask(conversationId, taskId).userMessage()?.let(showMessage)
        },
    )

internal fun AlphaAppState.createShellActions(
    messages: AlphaUiMessages,
    showMessage: (String) -> Unit,
): BootstrapShellActions =
    BootstrapShellActions(
        selectDestination = { destination ->
            dispatch(BootstrapAction.SelectDestination(destination))
                .showNoticeIfNeeded(messages.recordingUnavailable, showMessage)
        },
        invokeRecording = {
            dispatch(BootstrapAction.InvokeUnavailableRecording)
                .showNoticeIfNeeded(messages.recordingUnavailable, showMessage)
        },
    )

private fun AlphaMutationResult.userMessage(successMessage: String? = null): String? =
    if (succeeded) successMessage else state.operationError

private fun BootstrapEffect?.showNoticeIfNeeded(
    message: String,
    showMessage: (String) -> Unit,
) {
    if (this == BootstrapEffect.ShowRecordingUnavailableNotice) showMessage(message)
}
