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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.transcript.TranscriptDocument
import com.monumentogram.dora.ui.theme.DoraDimensions

@Composable
internal fun TranscriptLauncher(
    activity: MainActivity,
    identity: AudioIdentity? = null,
    enabled: Boolean = true,
) {
    var visible by remember { mutableStateOf(false) }
    TextButton(enabled = enabled, onClick = { visible = true }) {
        Text(if (identity == null) "История расшифровок" else "Расшифровать / открыть текст")
    }
    if (visible) TranscriptDialog(activity, identity) { visible = false }
}

@Composable
private fun TranscriptDialog(activity: MainActivity, initial: AudioIdentity?, onClose: () -> Unit) {
    val actions = remember(activity) { TranscriptActions(activity) }
    var identity by remember { mutableStateOf(initial) }
    var pendingExit by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    fun close() {
        if (dirty) pendingExit = true else onClose()
    }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                actions.clearVisible()
                dirty = false
                pendingExit = false
            }
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
                    .padding(DoraDimensions.space4)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DoraDimensions.space2),
            ) {
                Text("Расшифровка", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "На устройстве · облако только по вашему запросу",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(actions.message)
                Text(activity.modelImportMessage)
                TextButton(
                    onClick = ::close,
                    modifier = Modifier.heightIn(min = DoraDimensions.touchMinimum),
                ) {
                    Text("Закрыть")
                }
                if (identity == null) {
                    TextButton(enabled = !actions.busy, onClick = actions::library) {
                        Text("Обновить историю")
                    }
                    actions.history.forEachIndexed { index, item ->
                        Button(onClick = { identity = item }) {
                            Text("Открыть расшифровку ${index + 1}")
                        }
                    }
                } else {
                    val selected = checkNotNull(identity)
                    TextButton(
                        enabled = !actions.busy && !dirty,
                        onClick = { actions.load(selected) },
                    ) {
                        Text("Открыть снова / обновить")
                    }
                    var language by remember { mutableStateOf("ru") }
                    Row(horizontalArrangement = Arrangement.spacedBy(DoraDimensions.space2)) {
                        FilterChip(
                            selected = language == "ru",
                            enabled = !actions.busy,
                            onClick = { language = "ru" },
                            label = { Text("Русский") },
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
                                "Распознать заново"
                            else "Расшифровать"
                        )
                    }
                    if (actions.busy)
                        TextButton(onClick = actions::cancel) { Text("Отменить обработку") }
                    CloudControls(activity, selected, actions, language, !actions.busy && !dirty)
                    TextButton(
                        enabled = !actions.busy && !dirty && !activity.modelImportStatus.busy,
                        onClick = { activity.launchModelImport() },
                    ) {
                        Text("Установить пакет распознавания")
                    }
                    Text(
                        "Модель импортируется вручную и не удаляется из-за календарной даты. " +
                            "Точность временной привязки не проверена."
                    )
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
                        )
                    }
                }
            }
        }
        if (pendingExit)
            AlertDialog(
                onDismissRequest = { pendingExit = false },
                title = { Text("Есть несохранённые изменения") },
                text = { Text("Закрыть без сохранения введённого текста?") },
                confirmButton = {
                    TextButton(onClick = onClose) { Text("Закрыть без сохранения") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingExit = false }) { Text("Вернуться") }
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
    Text("Сохранённые версии", style = MaterialTheme.typography.titleLarge)
    document.versions.forEachIndexed { index, item ->
        TextButton(enabled = !busy && !dirty, onClick = { preview = item.id }) {
            Text(
                "Версия ${index + 1} · ${if (item.engine == "CLOUD") "Облако" else "На устройстве"}" +
                    if (item.id == document.activeId) " · активная" else ""
            )
        }
    }
    Text(
        "${if (version.engine == "CLOUD") "Облачная расшифровка" else "Машинный текст на устройстве"} · " +
            "${java.time.Instant.ofEpochMilli(version.createdAt)}"
    )
    SelectionContainer { Text(version.text, style = MaterialTheme.typography.bodyLarge) }
    Text(
        if (document.edits.any { it.versionId == version.id }) "Ваши сохранённые правки"
        else "Редактирование текста",
        style = MaterialTheme.typography.titleMedium,
    )
    OutlinedTextField(
        value = draft,
        onValueChange = { if (it.length <= MAX_EDIT_CHARS) draft = it },
        enabled = !busy,
        label = { Text("Текст расшифровки") },
        modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
        minLines = 5,
    )
    Button(enabled = !busy && dirty, onClick = { save(version.id, draft) }) {
        Text("Сохранить изменения")
    }
    TextButton(onClick = { clipboard.setText(AnnotatedString(draft)) }) { Text("Копировать текст") }
    if (version.id != document.activeId)
        TextButton(enabled = !busy && !dirty, onClick = { choosing = true }) {
            Text("Сделать активной")
        }
    if (choosing) {
        val conflicts = document.conflicts(version.id)
        Text(
            "Выбор версии сохраняет всю историю. Решите, какие правки оставить только в прежних версиях."
        )
        conflicts.forEachIndexed { index, edit ->
            Text("Правка ${index + 1}: ${edit.text}", style = MaterialTheme.typography.bodyLarge)
            Row {
                Checkbox(
                    checked = edit.id in resolved,
                    onCheckedChange = { checked ->
                        resolved = if (checked) resolved + edit.id else resolved - edit.id
                    },
                )
                Text("Выбрать текст этой версии; сохранить правку в истории")
            }
        }
        Button(
            enabled = !busy && conflicts.all { it.id in resolved },
            onClick = {
                select(version.id, resolved)
                choosing = false
            },
        ) {
            Text("Подтвердить выбор версии")
        }
        TextButton(onClick = { choosing = false }) { Text("Отмена") }
    }
}

private const val MAX_EDIT_CHARS = 32_000
