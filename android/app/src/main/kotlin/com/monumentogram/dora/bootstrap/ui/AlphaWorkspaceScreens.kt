package com.monumentogram.dora.bootstrap.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.monumentogram.dora.bootstrap.R
import com.monumentogram.dora.bootstrap.alpha.AlphaConversation
import com.monumentogram.dora.bootstrap.alpha.AlphaDraftPolicy
import com.monumentogram.dora.bootstrap.alpha.AlphaWorkspaceState
import com.monumentogram.dora.bootstrap.alpha.searchConversations
import com.monumentogram.dora.bootstrap.ui.theme.DoraDimensions
import com.monumentogram.dora.model.BootstrapDestination

@Composable
internal fun AlphaDestinationContent(
    destination: BootstrapDestination,
    workspaceState: AlphaWorkspaceState,
    editor: AlphaEditorSelection,
    modifier: Modifier = Modifier,
    actions: AlphaUiActions,
) {
    val blockingError = workspaceState.blockingError
    if (blockingError != null) {
        AlphaBlockedPage(message = blockingError, modifier = modifier)
    } else if (editor.isOpen) {
        AlphaConversationEditor(
            conversation =
                editor.conversationId?.let { id ->
                    workspaceState.snapshot.conversations.firstOrNull { it.id == id }
                },
            operationError = workspaceState.operationError,
            modifier = modifier,
            actions = actions.conversation,
        )
    } else {
        when (destination) {
            BootstrapDestination.HOME ->
                AlphaHomePage(workspaceState, modifier, actions.conversation)
            BootstrapDestination.HISTORY ->
                AlphaHistoryPage(workspaceState, modifier, actions.conversation.open)
            BootstrapDestination.TASKS ->
                AlphaTasksPage(workspaceState, modifier, actions.toggleTask)
            BootstrapDestination.SETTINGS -> AlphaSettingsPage(modifier)
        }
    }
}

@Composable
private fun AlphaHomePage(
    state: AlphaWorkspaceState,
    modifier: Modifier,
    actions: AlphaConversationActions,
) {
    val conversations = state.snapshot.conversations
    AlphaPage(
        title = stringResource(R.string.alpha_home_title),
        modifier = modifier,
    ) {
        item { AlphaDataWarning() }
        item {
            Text(
                text = stringResource(R.string.alpha_home_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.operationError?.let { error -> item { AlphaOperationError(error) } }
        item {
            Button(
                onClick = actions.create,
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(min = DoraDimensions.buttonPrimaryHeight)
                        .testTag(AlphaTestTags.NEW_CONVERSATION),
            ) {
                Text(stringResource(R.string.alpha_new_conversation))
            }
        }
        item {
            Text(
                text = stringResource(R.string.alpha_conversation_count, conversations.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                text = stringResource(R.string.alpha_recent),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (conversations.isEmpty()) {
            item { AlphaEmptyMessage(stringResource(R.string.alpha_empty_conversations)) }
        } else {
            items(conversations.take(RECENT_CONVERSATION_COUNT), key = AlphaConversation::id) {
                AlphaConversationCard(it, actions.open)
            }
        }
    }
}

@Composable
private fun AlphaHistoryPage(
    state: AlphaWorkspaceState,
    modifier: Modifier,
    onOpenConversation: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var queryError by remember { mutableStateOf<String?>(null) }
    val queryLimitMessage = stringResource(R.string.alpha_search_limit_error)
    val matches = state.snapshot.searchConversations(query)
    AlphaPage(
        title = stringResource(R.string.alpha_history_title),
        modifier = modifier,
    ) {
        item { AlphaDataWarning() }
        state.operationError?.let { error -> item { AlphaOperationError(error) } }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { candidate ->
                    if (AlphaDraftPolicy.acceptsSearchQuery(candidate)) {
                        query = candidate
                        queryError = null
                    } else {
                        queryError = queryLimitMessage
                    }
                },
                label = { Text(stringResource(R.string.alpha_search_label)) },
                supportingText = { queryError?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(AlphaTestTags.SEARCH),
            )
        }
        if (matches.isEmpty()) {
            item {
                AlphaEmptyMessage(
                    stringResource(
                        if (query.isBlank()) R.string.alpha_empty_conversations
                        else R.string.alpha_no_search_results
                    )
                )
            }
        } else {
            items(matches, key = AlphaConversation::id) {
                AlphaConversationCard(it, onOpenConversation)
            }
        }
    }
}

@Composable
private fun AlphaTasksPage(
    state: AlphaWorkspaceState,
    modifier: Modifier,
    onToggleTask: (String, String) -> Unit,
) {
    val rows =
        state.snapshot.conversations.flatMap { conversation ->
            conversation.tasks.map { task -> AlphaTaskRow(conversation, task) }
        }
    AlphaPage(
        title = stringResource(R.string.alpha_tasks_title),
        modifier = modifier,
    ) {
        item { AlphaDataWarning() }
        state.operationError?.let { error -> item { AlphaOperationError(error) } }
        if (rows.isEmpty()) {
            item { AlphaEmptyMessage(stringResource(R.string.alpha_no_tasks)) }
        } else {
            items(rows, key = { it.task.id }) { row ->
                AlphaTaskCard(row) { onToggleTask(row.conversation.id, row.task.id) }
            }
        }
    }
}

@Composable
private fun AlphaSettingsPage(modifier: Modifier) {
    AlphaPage(
        title = stringResource(R.string.alpha_settings_title),
        modifier = modifier,
    ) {
        item { AlphaDataWarning() }
        item {
            AlphaInformationCard(
                title = stringResource(R.string.alpha_local_mode_heading),
                body = stringResource(R.string.alpha_local_mode_body),
            )
        }
        item {
            AlphaInformationCard(
                title = stringResource(R.string.alpha_unavailable_heading),
                body = stringResource(R.string.alpha_unavailable_body),
            )
        }
    }
}

private const val RECENT_CONVERSATION_COUNT = 3
