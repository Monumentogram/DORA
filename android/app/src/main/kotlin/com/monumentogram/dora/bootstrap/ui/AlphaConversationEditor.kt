package com.monumentogram.dora.bootstrap.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.monumentogram.dora.bootstrap.R
import com.monumentogram.dora.bootstrap.alpha.AlphaConversation
import com.monumentogram.dora.bootstrap.alpha.AlphaConversationDraft
import com.monumentogram.dora.bootstrap.alpha.AlphaDraftPolicy
import com.monumentogram.dora.bootstrap.alpha.AlphaTask
import com.monumentogram.dora.bootstrap.ui.theme.DoraDimensions

@Composable
internal fun AlphaConversationEditor(
    conversation: AlphaConversation?,
    operationError: String?,
    modifier: Modifier,
    actions: AlphaConversationActions,
) {
    val editorKey = conversation?.id ?: "new"
    var title by rememberSaveable(editorKey) { mutableStateOf(conversation?.title.orEmpty()) }
    var notes by rememberSaveable(editorKey) { mutableStateOf(conversation?.notes.orEmpty()) }
    var summary by rememberSaveable(editorKey) { mutableStateOf(conversation?.summary.orEmpty()) }
    var taskLines by
        rememberSaveable(editorKey) {
            mutableStateOf(
                conversation?.tasks?.joinToString("\n", transform = AlphaTask::text).orEmpty()
            )
        }
    var draftError by rememberSaveable(editorKey) { mutableStateOf<String?>(null) }
    var showDeleteDialog by rememberSaveable(editorKey) { mutableStateOf(false) }
    val limitMessage = stringResource(R.string.alpha_draft_limit_error)
    val viewState =
        AlphaEditorViewState(
            conversation = conversation,
            draft = AlphaEditorDraft(title, notes, summary, taskLines),
            operationError = operationError,
            draftError = draftError,
            showDeleteDialog = showDeleteDialog,
        )
    val callbacks =
        AlphaEditorCallbacks(
            close = actions.close,
            updateDraft = { candidate ->
                if (candidate.isAccepted()) {
                    title = candidate.title
                    notes = candidate.notes
                    summary = candidate.summary
                    taskLines = candidate.taskLines
                    draftError = null
                } else {
                    draftError = limitMessage
                }
            },
            save = { actions.save(conversation?.id, it.toConversationDraft()) },
            delete = actions.delete,
            setDeleteDialog = { showDeleteDialog = it },
        )
    AlphaConversationEditorLayout(viewState, callbacks, modifier)
}

@Composable
private fun AlphaConversationEditorLayout(
    state: AlphaEditorViewState,
    callbacks: AlphaEditorCallbacks,
    modifier: Modifier,
) {
    AlphaPage(
        title =
            stringResource(
                if (state.conversation == null) R.string.alpha_editor_new_title
                else R.string.alpha_editor_edit_title
            ),
        modifier = modifier.testTag(AlphaTestTags.EDITOR),
    ) {
        item {
            TextButton(
                onClick = callbacks.close,
                modifier = Modifier.heightIn(min = DoraDimensions.touchMinimum),
            ) {
                Text(stringResource(R.string.alpha_back))
            }
        }
        item { AlphaDataWarning() }
        item { AlphaManualBadge() }
        state.operationError?.let { error -> item { AlphaOperationError(error) } }
        state.draftError?.let { error -> item { AlphaOperationError(error) } }
        item { AlphaEditorFields(state.draft, callbacks.updateDraft) }
        item { AlphaSaveButton { callbacks.save(state.draft) } }
        state.conversation?.let { conversation ->
            item { AlphaDeleteButton { callbacks.setDeleteDialog(true) } }
            if (state.showDeleteDialog) {
                item {
                    AlphaDeleteDialog(
                        onDismiss = { callbacks.setDeleteDialog(false) },
                        onConfirm = {
                            callbacks.setDeleteDialog(false)
                            callbacks.delete(conversation.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlphaEditorFields(
    draft: AlphaEditorDraft,
    updateDraft: (AlphaEditorDraft) -> Unit,
) {
    AlphaEditorTextField(
        value = draft.title,
        onValueChange = { updateDraft(draft.copy(title = it)) },
        spec = AlphaEditorFieldSpec.title(),
    )
    AlphaEditorTextField(
        value = draft.notes,
        onValueChange = { updateDraft(draft.copy(notes = it)) },
        spec = AlphaEditorFieldSpec.notes(),
    )
    AlphaEditorTextField(
        value = draft.summary,
        onValueChange = { updateDraft(draft.copy(summary = it)) },
        spec = AlphaEditorFieldSpec.summary(),
    )
    AlphaEditorTextField(
        value = draft.taskLines,
        onValueChange = { updateDraft(draft.copy(taskLines = it)) },
        spec = AlphaEditorFieldSpec.tasks(),
    )
}

@Composable
private fun AlphaEditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    spec: AlphaEditorFieldSpec,
) {
    val supportingContent: (@Composable () -> Unit)? =
        spec.supporting?.let { supporting ->
            @Composable { Text(stringResource(supporting)) }
        }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(spec.label)) },
        supportingText = supportingContent,
        singleLine = spec.singleLine,
        minLines = spec.minimumLines,
        modifier = Modifier.fillMaxWidth().testTag(spec.testTag),
    )
}

@Composable
private fun AlphaSaveButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = DoraDimensions.buttonPrimaryHeight)
                .testTag(AlphaTestTags.SAVE),
    ) {
        Text(stringResource(R.string.alpha_save_local))
    }
}

@Composable
private fun AlphaDeleteButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = DoraDimensions.buttonPrimaryHeight)
                .testTag(AlphaTestTags.DELETE),
    ) {
        Text(stringResource(R.string.alpha_delete_local))
    }
}

@Composable
private fun AlphaDeleteDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alpha_delete_title)) },
        text = { Text(stringResource(R.string.alpha_delete_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(AlphaTestTags.DELETE_CONFIRM),
            ) {
                Text(stringResource(R.string.alpha_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.alpha_delete_cancel))
            }
        },
    )
}

private data class AlphaEditorViewState(
    val conversation: AlphaConversation?,
    val draft: AlphaEditorDraft,
    val operationError: String?,
    val draftError: String?,
    val showDeleteDialog: Boolean,
)

private data class AlphaEditorCallbacks(
    val close: () -> Unit,
    val updateDraft: (AlphaEditorDraft) -> Unit,
    val save: (AlphaEditorDraft) -> Unit,
    val delete: (String) -> Unit,
    val setDeleteDialog: (Boolean) -> Unit,
)

private data class AlphaEditorDraft(
    val title: String,
    val notes: String,
    val summary: String,
    val taskLines: String,
) {
    fun isAccepted(): Boolean =
        AlphaDraftPolicy.acceptsEditorDraft(title, notes, summary, taskLines)

    fun toConversationDraft(): AlphaConversationDraft =
        AlphaConversationDraft(title, notes, summary, taskLines)
}

private data class AlphaEditorFieldSpec(
    @param:StringRes val label: Int,
    @param:StringRes val supporting: Int?,
    val minimumLines: Int,
    val singleLine: Boolean,
    val testTag: String,
) {
    companion object {
        fun title() =
            AlphaEditorFieldSpec(
                R.string.alpha_title_label,
                null,
                1,
                true,
                AlphaTestTags.TITLE,
            )

        fun notes() =
            AlphaEditorFieldSpec(
                R.string.alpha_notes_label,
                R.string.alpha_notes_support,
                NOTES_MINIMUM_LINES,
                false,
                AlphaTestTags.NOTES,
            )

        fun summary() =
            AlphaEditorFieldSpec(
                R.string.alpha_summary_label,
                R.string.alpha_summary_support,
                MULTILINE_MINIMUM_LINES,
                false,
                AlphaTestTags.SUMMARY,
            )

        fun tasks() =
            AlphaEditorFieldSpec(
                R.string.alpha_task_lines_label,
                R.string.alpha_task_lines_support,
                MULTILINE_MINIMUM_LINES,
                false,
                AlphaTestTags.TASK_LINES,
            )

        private const val NOTES_MINIMUM_LINES = 5
        private const val MULTILINE_MINIMUM_LINES = 3
    }
}
