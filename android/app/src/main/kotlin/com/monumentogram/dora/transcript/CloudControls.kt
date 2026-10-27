@file:Suppress("LongMethod", "LongParameterList")

package com.monumentogram.dora.transcript

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioIdentity

@Composable
internal fun CloudControls(
    activity: MainActivity,
    identity: AudioIdentity,
    actions: TranscriptActions,
    language: String,
    enabled: Boolean,
) {
    var disclosure by remember { mutableStateOf(false) }
    var cloudLanguage by remember(language) { mutableStateOf(language) }
    var setup by remember { mutableStateOf(false) }
    val installation = remember(activity) { CloudIdentity(activity.applicationContext) }
    var endpoint by remember { mutableStateOf(installation.endpoint()) }
    val pending =
        actions.document?.jobs?.lastOrNull { it.consent != null && it.status != "SUCCEEDED" }
    val active = actions.document?.jobs?.any { it.status in setOf("QUEUED", "RUNNING") } == true
    Button(enabled = enabled && !active, onClick = { disclosure = true }) {
        Text("Распознать в облаке")
    }
    if (pending != null) {
        if (pending.consent?.revoked != true && pending.status != "CANCELLED") {
            TextButton(
                enabled = enabled,
                onClick = { actions.recognizeCloud(identity, pending.language, pending.id) },
            ) {
                Text("Повторить / проверить результат")
            }
        }
        TextButton(enabled = enabled, onClick = { actions.cancelCloud(identity, pending.id) }) {
            Text(
                if (pending.consent?.revoked == true) "Проверить отмену в облаке"
                else "Отменить облачную операцию"
            )
        }
    }
    TextButton(enabled = enabled, onClick = { setup = true }) { Text("Подключение облака") }
    if (disclosure)
        AlertDialog(
            onDismissRequest = { disclosure = false },
            title = { Text("Аудио покинет устройство") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row {
                        FilterChip(
                            selected = cloudLanguage == "ru",
                            onClick = { cloudLanguage = "ru" },
                            label = { Text("Русский · RU") },
                        )
                        FilterChip(
                            selected = cloudLanguage == "en",
                            onClick = { cloudLanguage = "en" },
                            label = { Text("English · EN") },
                        )
                    }
                    Text(
                        "Только эта сохранённая запись будет отправлена серверу DORA и " +
                            "Amazon Transcribe во Франкфурте (Германия) для распознавания " +
                            "${if (cloudLanguage == "ru") "русской" else "английской"} речи."
                    )
                    Text("Сервер DORA: ${endpoint ?: "ещё не подключён"}")
                    Text(
                        "Временные аудио и файлы результата удаляются после обработки; " +
                            "предельный срок — 24 часа. Принятая сервером расшифровка хранится " +
                            "до 30 дней. Версии на устройстве сохраняются до вашего удаления."
                    )
                    Text(
                        "Обработка разрешена только при проверенном запрете использования " +
                            "контента для улучшения сервиса. Срок удаления внутренних копий " +
                            "провайдера не гарантируется. Уже отправленные байты нельзя " +
                            "отозвать мгновенно."
                    )
                    Text(
                        "Разрешение относится к этой записи и ручным повторам этой операции. " +
                            "Автоматической отправки нет. Ваши правки сохранятся отдельной " +
                            "версией. Английское распознавание имеет известное ограничение " +
                            "точности; доступ — только владельцу Alpha."
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        disclosure = false
                        if (endpoint == null) setup = true
                        else actions.recognizeCloud(identity, cloudLanguage)
                    }
                ) {
                    Text(if (endpoint == null) "Подключить облако" else "Разрешить и распознать")
                }
            },
            dismissButton = { TextButton(onClick = { disclosure = false }) { Text("Не сейчас") } },
        )
    if (setup)
        CloudSetup(
            installation,
            {
                endpoint = installation.endpoint()
                setup = false
            },
        ) {
            setup = false
        }
}

@Suppress("DEPRECATION", "TooGenericExceptionCaught")
@Composable
private fun CloudSetup(installation: CloudIdentity, saved: () -> Unit, close: () -> Unit) {
    var address by remember { mutableStateOf(installation.endpoint() ?: "") }
    var error by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Подключение облака для владельца") },
        text = {
            Column {
                Text(
                    "Укажите HTTPS-адрес сервера DORA. Администратор должен допустить " +
                        "открытый ключ этой установки. Копируется только открытый ключ; " +
                        "секретный ключ остаётся на устройстве."
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Адрес сервера DORA") },
                )
                TextButton(
                    onClick = {
                        try {
                            clipboard.setText(AnnotatedString(installation.publicInvitation()))
                            error = "Открытый ключ скопирован"
                        } catch (_: Exception) {
                            error = "Не удалось создать ключ установки"
                        }
                    }
                ) {
                    Text("Копировать открытый ключ установки")
                }
                Text(error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        installation.setEndpoint(address)
                        saved()
                    } catch (_: Exception) {
                        error = "Нужен HTTPS-адрес без пути, пароля и параметров"
                    }
                }
            ) {
                Text("Сохранить подключение")
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("Закрыть подключение") } },
    )
}
