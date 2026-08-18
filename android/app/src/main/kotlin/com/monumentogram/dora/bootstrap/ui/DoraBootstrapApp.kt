package com.monumentogram.dora.bootstrap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.monumentogram.dora.bootstrap.R
import com.monumentogram.dora.bootstrap.alpha.AlphaWorkspaceRepository
import com.monumentogram.dora.bootstrap.ui.theme.DoraDimensions
import com.monumentogram.dora.model.BootstrapDestination
import com.monumentogram.dora.model.BootstrapUiState
import kotlinx.coroutines.launch

@Composable
internal fun DoraBootstrapApp(
    forcedLayout: BootstrapNavigationLayout? = null,
    repository: AlphaWorkspaceRepository? = null,
) {
    val appState = rememberAlphaAppState(repository)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val messages =
        AlphaUiMessages(
            saved = stringResource(R.string.alpha_saved_message),
            deleted = stringResource(R.string.alpha_deleted_message),
            recordingUnavailable = stringResource(R.string.record_unavailable_message),
        )

    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(enabled = appState.editorOpen, onBack = appState::closeEditor)
    val uiActions = appState.createUiActions(messages, ::showMessage)
    val shellActions = appState.createShellActions(messages, ::showMessage)
    val selectedDestination = appState.uiState.selectedDestination
    DoraBootstrapSurface(
        forcedLayout = forcedLayout,
        state = appState.uiState,
        snackbarHostState = snackbarHostState,
        actions = shellActions,
    ) { contentModifier ->
        AlphaDestinationContent(
            destination = selectedDestination,
            workspaceState = appState.workspaceState,
            editor = appState.editorSelection,
            modifier = contentModifier,
            actions = uiActions,
        )
    }
}

@Composable
private fun DoraBootstrapSurface(
    forcedLayout: BootstrapNavigationLayout?,
    state: BootstrapUiState,
    snackbarHostState: SnackbarHostState,
    actions: BootstrapShellActions,
    content: @Composable (Modifier) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layout = forcedLayout ?: BootstrapNavigationLayout.forWidth(maxWidth)
            when (layout) {
                BootstrapNavigationLayout.COMPACT_DOCK ->
                    CompactBootstrapShell(
                        state = state,
                        snackbarHostState = snackbarHostState,
                        onDestinationSelected = actions.selectDestination,
                        onRecordingAction = actions.invokeRecording,
                        content = content,
                    )
                BootstrapNavigationLayout.WIDE_RAIL ->
                    WideBootstrapShell(
                        state = state,
                        snackbarHostState = snackbarHostState,
                        onDestinationSelected = actions.selectDestination,
                        onRecordingAction = actions.invokeRecording,
                        content = content,
                    )
            }
        }
    }
}

@Composable
private fun CompactBootstrapShell(
    state: BootstrapUiState,
    snackbarHostState: SnackbarHostState,
    onDestinationSelected: (BootstrapDestination) -> Unit,
    onRecordingAction: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets =
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            DoraDock(
                selectedDestination = state.selectedDestination,
                onDestinationSelected = onDestinationSelected,
                onRecordingAction = onRecordingAction,
            )
        },
    ) { contentPadding ->
        content(Modifier.padding(contentPadding))
    }
}

@Composable
private fun WideBootstrapShell(
    state: BootstrapUiState,
    snackbarHostState: SnackbarHostState,
    onDestinationSelected: (BootstrapDestination) -> Unit,
    onRecordingAction: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        Row(modifier = Modifier.fillMaxSize().padding(contentPadding).safeDrawingPadding()) {
            DoraRail(
                selectedDestination = state.selectedDestination,
                onDestinationSelected = onDestinationSelected,
                onRecordingAction = onRecordingAction,
            )
            content(Modifier.weight(1f))
        }
    }
}

@Composable
private fun DoraDock(
    selectedDestination: BootstrapDestination,
    onDestinationSelected: (BootstrapDestination) -> Unit,
    onRecordingAction: () -> Unit,
) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = DoraDimensions.compactMargin,
                    end = DoraDimensions.compactMargin,
                    bottom = DoraDimensions.space2,
                )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = DoraDimensions.dockVisibleHeight),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BootstrapDestination.ordered.take(2).forEach { destination ->
                    DockDestination(
                        destination = destination,
                        selected = destination == selectedDestination,
                        onClick = { onDestinationSelected(destination) },
                    )
                }
                RecordingUnavailableButton(
                    onClick = onRecordingAction,
                    modifier = Modifier.weight(1f),
                )
                BootstrapDestination.ordered.drop(2).forEach { destination ->
                    DockDestination(
                        destination = destination,
                        selected = destination == selectedDestination,
                        onClick = { onDestinationSelected(destination) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.DockDestination(
    destination: BootstrapDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(destination.labelResource())
    val description = stringResource(R.string.nav_destination_description, label)
    val tint =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier =
            Modifier.weight(1f)
                .heightIn(min = DoraDimensions.listRowMinimum)
                .clip(MaterialTheme.shapes.medium)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                )
                .semantics { contentDescription = description }
                .padding(vertical = DoraDimensions.space1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(destination.iconResource()),
            contentDescription = null,
            tint = tint,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun DoraRail(
    selectedDestination: BootstrapDestination,
    onDestinationSelected: (BootstrapDestination) -> Unit,
    onRecordingAction: () -> Unit,
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight().selectableGroup(),
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            RecordingUnavailableButton(
                onClick = onRecordingAction,
                showLabel = true,
            )
            Spacer(modifier = Modifier.size(DoraDimensions.space4))
        },
    ) {
        BootstrapDestination.ordered.forEach { destination ->
            val label = stringResource(destination.labelResource())
            val description = stringResource(R.string.nav_destination_description, label)
            NavigationRailItem(
                selected = destination == selectedDestination,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        painter = painterResource(destination.iconResource()),
                        contentDescription = null,
                    )
                },
                label = { Text(text = label) },
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
    }
}

@Composable
private fun RecordingUnavailableButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
) {
    val description = stringResource(R.string.record_action_description)
    val state = stringResource(R.string.record_action_state)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier =
                Modifier.size(DoraDimensions.recordControl).semantics {
                    contentDescription = description
                    stateDescription = state
                },
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bootstrap_mic),
                contentDescription = null,
            )
        }
        if (showLabel) {
            Text(
                text = stringResource(R.string.record_action_label),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = DoraDimensions.space1),
            )
        }
    }
}
