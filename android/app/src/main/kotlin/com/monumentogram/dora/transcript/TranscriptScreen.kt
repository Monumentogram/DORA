@file:Suppress(
    "LongMethod",
    "LongParameterList",
    "CyclomaticComplexMethod",
) // Small complete Alpha editor with explicit decisions.

package com.monumentogram.dora.transcript

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.R
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.transcript.TranscriptDocument
import com.monumentogram.dora.ui.signal.uiCopy
import com.monumentogram.dora.ui.theme.DoraDimensions
import java.text.DateFormat
import java.util.Date

@Composable
internal fun TranscriptLauncher(
    activity: MainActivity,
    identity: AudioIdentity? = null,
    enabled: Boolean = true,
) {
    var visible by remember { mutableStateOf(false) }
    TextButton(enabled = enabled, onClick = { visible = true }) {
        Text(
            if (identity == null) uiCopy("История расшифровок")
            else uiCopy("Расшифровать / открыть текст")
        )
    }
    if (visible) TranscriptDialog(activity, identity) { visible = false }
}

@Composable
internal fun TranscriptDialog(
    activity: MainActivity,
    initial: AudioIdentity?,
    initialFrames: Long = 0,
    onClose: () -> Unit,
) {
    val actions = remember(activity) { TranscriptActions(activity) }
    var identity by remember { mutableStateOf(initial) }
    var pendingExit by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    fun close() {
        if (dirty) pendingExit = true else onClose()
    }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                actions.clearVisible()
                dirty = false
                pendingExit = false
                deleting = false
            }
            if (event == Lifecycle.Event.ON_RESUME) identity?.let(actions::load)
        }
        activity.lifecycle.addObserver(observer)
        onDispose {
            activity.lifecycle.removeObserver(observer)
            actions.close()
        }
    }
    LaunchedEffect(identity) { identity?.let(actions::load) ?: actions.library() }
    Dialog(
        onDismissRequest = ::close,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                securePolicy = SecureFlagPolicy.SecureOn,
            ),
    ) {
        BackHandler(onBack = ::close)
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .padding(horizontal = DoraDimensions.space6, vertical = DoraDimensions.space4)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DoraDimensions.space4),
            ) {
                Text(
                    stringResource(R.string.signal_copy_19),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    stringResource(R.string.signal_copy_20),
                    style = MaterialTheme.typography.labelLarge,
                )
                if (actions.busy)
                    androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    uiCopy(actions.message),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (activity.modelImportMessage.isNotBlank())
                    Text(uiCopy(activity.modelImportMessage))
                TextExportFeedback(activity.textExportStatus)
                TextButton(
                    onClick = ::close,
                    modifier = Modifier.heightIn(min = DoraDimensions.touchMinimum),
                ) {
                    Text(stringResource(R.string.signal_copy_3))
                }
                if (identity == null) {
                    TextButton(enabled = !actions.busy, onClick = actions::library) {
                        Text(stringResource(R.string.signal_copy_21))
                    }
                    actions.history.forEachIndexed { index, item ->
                        Button(onClick = { identity = item }) {
                            Text(uiCopy("Открыть расшифровку") + " ${index + 1}")
                        }
                    }
                } else {
                    val selected = checkNotNull(identity)
                    RecordingHeading(selected, actions.document)
                    TextButton(
                        enabled = !actions.busy,
                        onClick = { deleting = true },
                    ) {
                        Text(stringResource(R.string.stage11_delete_recording))
                    }
                    OriginalAudioControls(
                        activity,
                        selected,
                        actions.document?.source?.frames ?: initialFrames,
                        canPlay = { !actions.busy },
                    )
                    TextButton(
                        enabled = !actions.busy && !dirty,
                        onClick = { actions.load(selected) },
                    ) {
                        Text(stringResource(R.string.signal_copy_22))
                    }
                    actions.document?.let { document ->
                        TranscriptEditor(
                            document,
                            actions.busy,
                            { dirty = it },
                            { version, text ->
                                actions.edit(selected, version, document.revision, text)
                            },
                            { version, edits ->
                                actions.select(selected, version, document.revision, edits)
                            },
                            exportEnabled = !activity.textExportStatus.busy,
                            export = activity::launchTextExport,
                        )
                    }
                    androidx.compose.material3.HorizontalDivider()
                    Text(
                        stringResource(R.string.signal_copy_23),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    var language by remember { mutableStateOf("ru") }
                    Row(horizontalArrangement = Arrangement.spacedBy(DoraDimensions.space2)) {
                        FilterChip(
                            selected = language == "ru",
                            enabled = !actions.busy,
                            onClick = { language = "ru" },
                            label = { Text(stringResource(R.string.signal_copy_24)) },
                        )
                        FilterChip(
                            selected = language == "en",
                            enabled = !actions.busy,
                            onClick = { language = "en" },
                            label = { Text("English") },
                        )
                    }
                    Button(
                        enabled = !actions.busy && !dirty,
                        onClick = { actions.recognize(selected, language) },
                    ) {
                        Text(
                            if (actions.document?.versions?.isNotEmpty() == true)
                                uiCopy("Распознать заново")
                            else uiCopy("Расшифровать")
                        )
                    }
                    if (actions.busy)
                        TextButton(onClick = actions::cancel) {
                            Text(stringResource(R.string.signal_copy_25))
                        }
                    CloudControls(activity, selected, actions, language, !actions.busy && !dirty)
                    TextButton(
                        enabled = !actions.busy && !dirty && !activity.modelImportStatus.busy,
                        onClick = { activity.launchModelImport() },
                    ) {
                        Text(stringResource(R.string.signal_copy_26))
                    }
                    Text(
                        uiCopy(
                            "Модель импортируется вручную и не удаляется из-за календарной даты. "
                        ) + uiCopy("Точность временной привязки не проверена.")
                    )
                }
            }
        }
        if (pendingExit)
            AlertDialog(
                onDismissRequest = { pendingExit = false },
                title = { Text(stringResource(R.string.signal_copy_27)) },
                text = { Text(stringResource(R.string.signal_copy_28)) },
                confirmButton = {
                    TextButton(onClick = onClose) { Text(stringResource(R.string.signal_copy_29)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingExit = false }) {
                        Text(stringResource(R.string.signal_copy_30))
                    }
                },
            )
        if (deleting)
            AlertDialog(
                onDismissRequest = { deleting = false },
                title = { Text(stringResource(R.string.stage11_delete_recording)) },
                text = { Text(stringResource(R.string.stage11_delete_scope)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            deleting = false
                            identity?.let { actions.deleteRecording(it, onClose) }
                        }
                    ) {
                        Text(stringResource(R.string.stage11_delete_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleting = false }) {
                        Text(stringResource(R.string.signal_copy_2))
                    }
                },
            )
    }
}

@Suppress("DEPRECATION") // Explicit user clipboard action; no automatic copy.
@Composable
private fun TranscriptEditor(
    document: TranscriptDocument,
    busy: Boolean,
    onDirty: (Boolean) -> Unit,
    save: (String, String) -> Unit,
    select: (String, Set<String>) -> Unit,
    exportEnabled: Boolean,
    export: (String) -> Unit,
) {
    var preview by remember(document.source) { mutableStateOf<String?>(null) }
    val version =
        document.versions.find { it.id == preview }
            ?: document.versions.find { it.id == document.activeId }
            ?: document.versions.lastOrNull()
    if (version == null) return
    val rendered = document.rendered(version.id)
    var draft by remember(version.id, document.revision) { mutableStateOf(rendered) }
    var choosing by remember(document.revision, version.id) { mutableStateOf(false) }
    var resolved by remember(document.revision, version.id) { mutableStateOf(emptySet<String>()) }
    val dirty = draft != rendered
    LaunchedEffect(dirty) { onDirty(dirty) }
    val clipboard = LocalClipboardManager.current
    Text(stringResource(R.string.signal_copy_31), style = MaterialTheme.typography.titleLarge)
    document.versions.forEachIndexed { index, item ->
        TextButton(enabled = !busy && !dirty, onClick = { preview = item.id }) {
            Text(
                uiCopy("Версия") +
                    " ${index + 1} · ${if (item.engine == "CLOUD") uiCopy("Облако") else uiCopy("На устройстве")}" +
                    if (item.id == document.activeId) uiCopy(" · активная") else ""
            )
        }
    }
    val engineLabel =
        if (version.engine == "CLOUD") uiCopy("Облачная расшифровка")
        else uiCopy("Машинный текст на устройстве")
    Text(
        engineLabel +
            " · " +
            java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.MEDIUM,
                    java.text.DateFormat.SHORT,
                )
                .format(java.util.Date(version.createdAt))
    )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        SelectionContainer {
            Text(
                version.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
            )
        }
    }
    Text(
        if (document.edits.any { it.versionId == version.id }) uiCopy("Ваши сохранённые правки")
        else uiCopy("Редактирование текста"),
        style = MaterialTheme.typography.titleMedium,
    )
    OutlinedTextField(
        value = draft,
        onValueChange = { if (it.length <= MAX_EDIT_CHARS) draft = it },
        enabled = !busy,
        label = { Text(stringResource(R.string.signal_copy_32)) },
        modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
        minLines = 8,
        shape = MaterialTheme.shapes.large,
    )
    Button(enabled = !busy && dirty, onClick = { save(version.id, draft) }) {
        Text(stringResource(R.string.signal_copy_33))
    }
    TextButton(onClick = { clipboard.setText(AnnotatedString(draft)) }) {
        Text(stringResource(R.string.signal_copy_34))
    }
    TextButton(enabled = !busy && !dirty && exportEnabled, onClick = { export(draft) }) {
        Text(stringResource(R.string.stage11_export_text))
    }
    if (dirty) Text(stringResource(R.string.stage11_export_save_first))
    if (version.id != document.activeId)
        TextButton(enabled = !busy && !dirty, onClick = { choosing = true }) {
            Text(stringResource(R.string.signal_copy_35))
        }
    if (choosing) {
        val conflicts = document.conflicts(version.id)
        Text(
            uiCopy(
                "Выбор версии сохраняет всю историю. Решите, какие правки оставить только в прежних версиях."
            )
        )
        conflicts.forEachIndexed { index, edit ->
            Text(
                uiCopy("Правка") + " ${index + 1}: ${edit.text}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Row {
                Checkbox(
                    checked = edit.id in resolved,
                    onCheckedChange = { checked ->
                        resolved = if (checked) resolved + edit.id else resolved - edit.id
                    },
                )
                Text(stringResource(R.string.signal_copy_37))
            }
        }
        Button(
            enabled = !busy && conflicts.all { it.id in resolved },
            onClick = {
                select(version.id, resolved)
                choosing = false
            },
        ) {
            Text(stringResource(R.string.signal_copy_38))
        }
        TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.signal_copy_2)) }
    }
}

@Composable
private fun RecordingHeading(identity: AudioIdentity, document: TranscriptDocument?) {
    val active =
        document?.versions?.find { it.id == document.activeId } ?: document?.versions?.lastOrNull()
    val title =
        active?.let { document?.rendered(it.id) }?.lineSequence()?.firstOrNull { it.isNotBlank() }
            ?: stringResource(
                R.string.signal_recording_name,
                identity.recordingId.value.take(TITLE_ID_LENGTH),
            )
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    active?.let {
        Text(
            stringResource(
                R.string.signal_transcript_date,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(it.createdAt)),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val TITLE_ID_LENGTH = 8
private const val MAX_EDIT_CHARS = 32_000
