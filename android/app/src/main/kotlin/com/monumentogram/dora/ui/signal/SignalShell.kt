@file:Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")

package com.monumentogram.dora.ui.signal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.R
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.recording.RecordingCompletionState
import com.monumentogram.dora.recording.capturedTime
import com.monumentogram.dora.transcript.TranscriptDialog
import com.monumentogram.dora.ui.theme.DoraDimensions
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SignalShell(activity: MainActivity, onRecord: () -> Unit) {
    val library = remember(activity) { RecordingLibrary(activity) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AudioIdentity?>(null) }
    var history by remember { mutableStateOf(false) }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) library.load()
            if (event == Lifecycle.Event.ON_PAUSE) library.clear()
        }
        activity.lifecycle.addObserver(observer)
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) library.load()
        onDispose {
            activity.lifecycle.removeObserver(observer)
            library.close()
        }
    }
    BackHandler(settings) { settings = false }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (settings) stringResource(R.string.nav_settings) else "DORA",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                actions = {
                    if (!settings)
                        IconButton(onClick = { library.load() }, enabled = !library.busy) {
                            Icon(
                                painterResource(R.drawable.ic_signal_refresh),
                                stringResource(R.string.signal_refresh),
                            )
                        }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = !settings,
                    onClick = { settings = false },
                    icon = { Icon(painterResource(R.drawable.ic_bootstrap_history), null) },
                    label = { Text(stringResource(R.string.signal_recordings)) },
                )
                NavigationBarItem(
                    selected = settings,
                    onClick = { settings = true },
                    icon = { Icon(painterResource(R.drawable.ic_bootstrap_settings), null) },
                    label = { Text(stringResource(R.string.nav_settings)) },
                )
            }
        },
        floatingActionButton = {
            if (!settings)
                ExtendedFloatingActionButton(
                    onClick = onRecord,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(painterResource(R.drawable.ic_bootstrap_mic), null) },
                    text = { Text(stringResource(R.string.signal_new_recording)) },
                )
        },
    ) { padding ->
        if (settings) SignalSettings(activity, Modifier.padding(padding))
        else
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Column(
                        Modifier.fillMaxWidth()
                            .widthIn(max = DoraDimensions.readingColumnMaximum)
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Text(
                            stringResource(R.string.signal_private_library),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            stringResource(R.string.signal_local_caption),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (library.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (library.failed)
                    item {
                        Column(Modifier.padding(24.dp)) {
                            Text(stringResource(R.string.signal_library_error))
                            TextButton(onClick = { library.load() }) {
                                Text(stringResource(R.string.signal_retry))
                            }
                        }
                    }
                if (!library.busy && !library.failed && library.entries.isEmpty())
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_bootstrap_mic),
                                    null,
                                    Modifier.padding(24.dp).size(40.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            Text(
                                stringResource(R.string.signal_empty_title),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                stringResource(R.string.signal_empty_body),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                items(library.entries, key = { it.recording.cursor }) { entry ->
                    RecordingRow(entry) { selected = entry.recording.identity }
                }
                if (library.more)
                    item {
                        TextButton(enabled = !library.busy, onClick = { library.load(true) }) {
                            Text(stringResource(R.string.signal_more))
                        }
                    }
                item {
                    TextButton(onClick = { history = true }, modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.signal_transcript_history))
                    }
                }
            }
    }
    if (selected != null || history)
        TranscriptDialog(
            activity,
            selected,
            library.entries.find { it.recording.identity == selected }?.recording?.recoveredFrames
                ?: 0,
        ) {
            selected = null
            history = false
            library.load()
        }
}

@Composable
private fun RecordingRow(entry: LibraryEntry, onClick: () -> Unit) {
    val document = entry.document
    val active =
        document?.versions?.find { it.id == document.activeId } ?: document?.versions?.lastOrNull()
    val text = active?.let { document?.rendered(it.id) }.orEmpty()
    val identity = entry.recording.identity
    val title =
        text.lineSequence().firstOrNull { it.isNotBlank() }
            ?: stringResource(
                R.string.signal_recording_name,
                identity?.recordingId?.value?.take(SHORT_ID_LENGTH).orEmpty(),
            )
    Surface(
        onClick = onClick,
        enabled = identity != null,
        modifier = Modifier.widthIn(max = DoraDimensions.readingColumnMaximum).fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    painterResource(R.drawable.ic_bootstrap_mic),
                    null,
                    Modifier.padding(14.dp).size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    capturedTime(entry.recording.recoveredFrames) +
                        " · " +
                        stringResource(
                            when {
                                entry.recording.completionState ==
                                    RecordingCompletionState.DELETED ->
                                    R.string.signal_audio_deleted
                                entry.recording.completionState !=
                                    RecordingCompletionState.FINALIZED ->
                                    R.string.signal_recovery_needed
                                document?.jobs?.lastOrNull()?.status in
                                    setOf("QUEUED", "RUNNING") -> R.string.signal_processing
                                active != null -> R.string.signal_transcript_ready
                                else -> R.string.signal_audio_saved
                            }
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (text.isNotBlank())
                    Text(
                        text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                active?.let {
                    Text(
                        stringResource(
                            R.string.signal_transcript_date,
                            DateFormat.getDateTimeInstance(
                                    DateFormat.MEDIUM,
                                    DateFormat.SHORT,
                                )
                                .format(Date(it.createdAt)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    HorizontalDivider(
        Modifier.padding(start = 88.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

private const val SHORT_ID_LENGTH = 8
