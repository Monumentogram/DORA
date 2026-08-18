package com.monumentogram.dora.bootstrap.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.monumentogram.dora.bootstrap.R
import com.monumentogram.dora.bootstrap.alpha.AlphaConversation
import com.monumentogram.dora.bootstrap.alpha.AlphaTask
import com.monumentogram.dora.bootstrap.ui.theme.DoraDimensions

@Composable
internal fun AlphaConversationCard(
    conversation: AlphaConversation,
    onOpenConversation: (String) -> Unit,
) {
    val openDescription = stringResource(R.string.alpha_open_conversation, conversation.title)
    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = DoraDimensions.listRowMinimum)
                .testTag(AlphaTestTags.CONVERSATION_PREFIX + conversation.id)
                .semantics { contentDescription = openDescription }
                .clickable { onOpenConversation(conversation.id) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(DoraDimensions.space4),
            verticalArrangement = Arrangement.spacedBy(DoraDimensions.space2),
        ) {
            Text(conversation.title, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.alpha_conversation_tasks, conversation.tasks.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AlphaManualBadge()
        }
    }
}

@Composable
internal fun AlphaTaskCard(row: AlphaTaskRow, onToggle: () -> Unit) {
    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = DoraDimensions.listRowMinimum)
                .testTag(AlphaTestTags.TASK_PREFIX + row.task.id),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(DoraDimensions.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DoraDimensions.space3),
        ) {
            Checkbox(
                checked = row.task.completed,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag(AlphaTestTags.TASK_TOGGLE_PREFIX + row.task.id),
            )
            Column(verticalArrangement = Arrangement.spacedBy(DoraDimensions.space1)) {
                Text(row.task.text, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.alpha_task_origin, row.conversation.title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun AlphaPage(
    title: String,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = DoraDimensions.readingColumnMaximum),
            contentPadding =
                PaddingValues(
                    horizontal = DoraDimensions.space6,
                    vertical = DoraDimensions.space8,
                ),
            verticalArrangement = Arrangement.spacedBy(DoraDimensions.space4),
        ) {
            item { AlphaStageBadge() }
            item {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            content()
        }
    }
}

@Composable
private fun AlphaStageBadge() {
    Surface(
        shape = RoundedCornerShape(DoraDimensions.radiusFull),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = stringResource(R.string.alpha_stage_label),
            style = MaterialTheme.typography.labelMedium,
            modifier =
                Modifier.padding(
                    horizontal = DoraDimensions.space3,
                    vertical = DoraDimensions.space2,
                ),
        )
    }
}

@Composable
internal fun AlphaDataWarning() {
    AlphaInformationCard(
        title = stringResource(R.string.alpha_data_warning_title),
        body = stringResource(R.string.alpha_data_warning_body),
        emphasized = true,
    )
}

@Composable
internal fun AlphaInformationCard(title: String, body: String, emphasized: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color =
            if (emphasized) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        contentColor =
            if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(DoraDimensions.space4),
            verticalArrangement = Arrangement.spacedBy(DoraDimensions.space2),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun AlphaManualBadge() {
    Text(
        text = stringResource(R.string.alpha_manual_badge),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun AlphaOperationError(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier.padding(DoraDimensions.space4),
            verticalArrangement = Arrangement.spacedBy(DoraDimensions.space2),
        ) {
            Text(
                stringResource(R.string.alpha_operation_error_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun AlphaEmptyMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = DoraDimensions.space4),
    )
}

@Composable
internal fun AlphaBlockedPage(message: String, modifier: Modifier) {
    AlphaPage(title = stringResource(R.string.alpha_blocked_title), modifier = modifier) {
        item { AlphaOperationError(message) }
        item { AlphaDataWarning() }
    }
}

internal data class AlphaTaskRow(val conversation: AlphaConversation, val task: AlphaTask)
