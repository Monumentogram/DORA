package com.monumentogram.dora.bootstrap.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.monumentogram.dora.bootstrap.alpha.AlphaConversationDraft
import com.monumentogram.dora.bootstrap.alpha.AlphaMutationResult
import com.monumentogram.dora.bootstrap.alpha.AlphaWorkspaceController
import com.monumentogram.dora.bootstrap.alpha.AlphaWorkspaceRepository
import com.monumentogram.dora.bootstrap.alpha.AtomicFileAlphaWorkspaceRepository
import com.monumentogram.dora.model.BootstrapAction
import com.monumentogram.dora.model.BootstrapDestination
import com.monumentogram.dora.model.BootstrapEffect
import com.monumentogram.dora.model.BootstrapUiState
import com.monumentogram.dora.model.reduce

internal class AlphaAppState(
    private val controller: AlphaWorkspaceController,
    initialRoute: String = BootstrapDestination.HOME.route,
    initialEditorOpen: Boolean = false,
    initialEditorConversationId: String? = null,
) {
    var workspaceState by mutableStateOf(controller.state)
        private set

    private var selectedRoute by
        mutableStateOf(
            BootstrapDestination.ordered.firstOrNull { it.route == initialRoute }?.route
                ?: BootstrapDestination.HOME.route
        )

    var editorOpen by mutableStateOf(initialEditorOpen)
        private set

    var editorConversationId by mutableStateOf(initialEditorConversationId)
        private set

    val uiState: BootstrapUiState
        get() = BootstrapUiState(selectedDestination())

    val editorSelection: AlphaEditorSelection
        get() = AlphaEditorSelection(editorOpen, editorConversationId)

    fun openNewConversation() {
        editorOpen = true
        editorConversationId = null
    }

    fun openConversation(conversationId: String) {
        editorOpen = true
        editorConversationId = conversationId
    }

    fun closeEditor() {
        editorOpen = false
        editorConversationId = null
    }

    fun saveConversation(
        conversationId: String?,
        draft: AlphaConversationDraft,
    ): AlphaMutationResult {
        val result = controller.saveConversation(conversationId, draft)
        publish(result)
        if (result.succeeded) editorConversationId = result.changedConversationId
        return result
    }

    fun deleteConversation(conversationId: String): AlphaMutationResult {
        val result = controller.deleteConversation(conversationId)
        publish(result)
        if (result.succeeded) {
            closeEditor()
            selectedRoute = BootstrapDestination.HISTORY.route
        }
        return result
    }

    fun toggleTask(conversationId: String, taskId: String): AlphaMutationResult =
        controller.toggleTask(conversationId, taskId).also(::publish)

    fun dispatch(action: BootstrapAction): BootstrapEffect? {
        val update = uiState.reduce(action)
        selectedRoute = update.state.selectedDestination.route
        if (action is BootstrapAction.SelectDestination) closeEditor()
        return update.effect
    }

    private fun selectedDestination(): BootstrapDestination =
        BootstrapDestination.ordered.first { it.route == selectedRoute }

    private fun publish(result: AlphaMutationResult) {
        workspaceState = result.state
    }

    internal companion object {
        fun saver(controller: AlphaWorkspaceController): Saver<AlphaAppState, List<String>> =
            Saver(
                save = { state ->
                    listOf(
                        state.selectedRoute,
                        state.editorOpen.toString(),
                        state.editorConversationId.orEmpty(),
                    )
                },
                restore = { saved ->
                    AlphaAppState(
                        controller = controller,
                        initialRoute = saved.getOrNull(0).orEmpty(),
                        initialEditorOpen = saved.getOrNull(1)?.toBooleanStrictOrNull() ?: false,
                        initialEditorConversationId = saved.getOrNull(2)?.ifEmpty { null },
                    )
                },
            )
    }
}

@Composable
internal fun rememberAlphaAppState(repository: AlphaWorkspaceRepository?): AlphaAppState {
    val context = LocalContext.current
    val workspaceRepository =
        remember(repository, context.applicationContext) {
            repository ?: AtomicFileAlphaWorkspaceRepository(context.applicationContext.filesDir)
        }
    val controller = remember(workspaceRepository) { AlphaWorkspaceController(workspaceRepository) }
    return rememberSaveable(controller, saver = AlphaAppState.saver(controller)) {
        AlphaAppState(controller)
    }
}
