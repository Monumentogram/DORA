@file:Suppress(
    "LongMethod",
    "CyclomaticComplexMethod",
) // Compose recovery states and permission callbacks.

package com.monumentogram.dora.recording

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.audio.AudioCompletion
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.recording.RecordingRecovery

@Composable
internal fun RecordingRecoveryCard(activity: Activity, authorized: Boolean) {
    val app = activity.application as DoraApplication
    var entries by remember { mutableStateOf(emptyList<RecordingRecovery>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var acknowledged by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<RecordingRecovery?>(null) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(authorized) {
        if (!authorized) {
            entries = emptyList()
            acknowledged = false
        }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val selected = pending
            pending = null
            if (granted && selected != null) app.recording.start(activity, selected.identity)
            else message = "Микрофон не разрешён. Запись не возобновлена"
        }
    fun load(after: String = "") {
        if (loading) return
        if (app.audioRuntime.recordingOpenMode() == AudioOpenMode.CREATE_NEW) {
            message = "Сохранённых записей пока нет"
            return
        }
        loading = true
        app.audioRuntime.requestRecordingRecovery(activity, after) { result ->
            loading = false
            when (result) {
                is AudioResult.Value -> {
                    entries = result.value
                    message = if (entries.isEmpty()) "Сохранённых записей больше нет" else null
                }
                is AudioResult.Failed ->
                    message = "Проверка не завершена: требуется доступ к защищённому хранилищу"
            }
        }
    }
    TextButton(enabled = !loading, onClick = { load() }) {
        Text(if (loading) "Проверяем сохранение…" else "Проверить сохранённые записи")
    }
    message?.let { Text(it) }
    if (authorized && entries.isNotEmpty()) {
        if (entries.any { it.canResume }) {
            Text("Продолжение снова включает микрофон. Аудио остаётся только на устройстве.")
            Checkbox(
                checked = acknowledged,
                onCheckedChange = { acknowledged = it },
                modifier =
                    Modifier.semantics {
                        contentDescription = "Я предупредил(а) участников о записи"
                    },
            )
            Text("Я предупредил(а) участников о записи")
        }
        entries.forEach { entry ->
            Column {
                val summary = entry.summary
                Text(
                    when {
                        summary == null -> "Не удалось подтвердить сохранённое аудио"
                        summary.completion == AudioCompletion.FINALIZED ->
                            "Запись сохранена · ${capturedTime(summary.frames)}"
                        else -> "Часть записи восстановлена · ${capturedTime(summary.frames)}"
                    }
                )
                if (entry.failure != null || summary?.tailFailure != null)
                    Text(
                        "Последняя часть требует восстановления. Продолжение этой записи недоступно"
                    )
                if (entry.canResume)
                    Button(
                        enabled = acknowledged,
                        onClick = {
                            acknowledged = false
                            if (
                                activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                    PackageManager.PERMISSION_GRANTED
                            )
                                app.recording.start(activity, entry.identity)
                            else {
                                pending = entry
                                permission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    ) {
                        Text("Продолжить эту запись")
                    }
            }
        }
        if (entries.size == RECOVERY_PAGE_SIZE)
            TextButton(onClick = { load(entries.last().identity.assetId.value) }) {
                Text("Следующие записи")
            }
    }
}

private const val RECOVERY_PAGE_SIZE = 20
