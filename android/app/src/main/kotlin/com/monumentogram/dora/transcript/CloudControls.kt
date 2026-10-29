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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.R
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.ui.signal.uiCopy

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
        Text(stringResource(R.string.signal_copy_39))
    }
    if (pending != null) {
        if (pending.consent?.revoked != true && pending.status != "CANCELLED") {
            TextButton(
                enabled = enabled,
                onClick = { actions.recognizeCloud(identity, pending.language, pending.id) },
            ) {
                Text(stringResource(R.string.signal_copy_40))
            }
        }
        TextButton(enabled = enabled, onClick = { actions.cancelCloud(identity, pending.id) }) {
            Text(
                if (pending.consent?.revoked == true) uiCopy("Проверить отмену в облаке")
                else uiCopy("Отменить облачную операцию")
            )
        }
    }
    TextButton(enabled = enabled, onClick = { setup = true }) {
        Text(stringResource(R.string.signal_copy_41))
    }
    if (disclosure)
        AlertDialog(
            onDismissRequest = { disclosure = false },
            title = { Text(stringResource(R.string.signal_copy_42)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row {
                        FilterChip(
                            selected = cloudLanguage == "ru",
                            onClick = { cloudLanguage = "ru" },
                            label = { Text(stringResource(R.string.signal_copy_43)) },
                        )
                        FilterChip(
                            selected = cloudLanguage == "en",
                            onClick = { cloudLanguage = "en" },
                            label = { Text("English · EN") },
                        )
                    }
                    Text(
                        uiCopy("Только эта сохранённая запись будет отправлена серверу DORA и ") +
                            uiCopy(
                                "Amazon Transcribe во Франкфурте (Германия) для распознавания "
                            ) +
                            "${if (cloudLanguage == "ru") uiCopy("русской") else uiCopy("английской")}" +
                            uiCopy(" речи.")
                    )
                    Text(uiCopy("Сервер DORA") + ": ${endpoint ?: uiCopy("ещё не подключён")}")
                    Text(
                        uiCopy("Временные аудио и файлы результата удаляются после обработки; ") +
                            uiCopy(
                                "предельный срок — 24 часа. Принятая сервером расшифровка хранится "
                            ) +
                            uiCopy(
                                "до 30 дней. Версии на устройстве сохраняются до вашего удаления."
                            )
                    )
                    Text(
                        uiCopy(
                            "Обработка разрешена только при проверенном запрете использования "
                        ) +
                            uiCopy(
                                "контента для улучшения сервиса. Срок удаления внутренних копий "
                            ) +
                            uiCopy("провайдера не гарантируется. Уже отправленные байты нельзя ") +
                            uiCopy("отозвать мгновенно.")
                    )
                    Text(
                        uiCopy(
                            "Разрешение относится к этой записи и ручным повторам этой операции. "
                        ) +
                            uiCopy(
                                "Автоматической отправки нет. Ваши правки сохранятся отдельной "
                            ) +
                            uiCopy(
                                "версией. Английское распознавание имеет известное ограничение "
                            ) +
                            uiCopy("точности; доступ — только владельцу Alpha.")
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
                    Text(
                        if (endpoint == null) uiCopy("Подключить облако")
                        else uiCopy("Разрешить и распознать")
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { disclosure = false }) {
                    Text(stringResource(R.string.signal_copy_44))
                }
            },
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
internal fun CloudSetup(installation: CloudIdentity, saved: () -> Unit, close: () -> Unit) {
    var address by remember { mutableStateOf(installation.endpoint() ?: "") }
    var error by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.signal_copy_45)) },
        text = {
            Column {
                Text(
                    uiCopy("Укажите HTTPS-адрес сервера DORA. Администратор должен допустить ") +
                        uiCopy("открытый ключ этой установки. Копируется только открытый ключ; ") +
                        uiCopy("секретный ключ остаётся на устройстве.")
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.signal_copy_46)) },
                )
                TextButton(
                    onClick = {
                        try {
                            clipboard.setText(AnnotatedString(installation.publicInvitation()))
                            error = uiCopy("Открытый ключ скопирован")
                        } catch (_: Exception) {
                            error = uiCopy("Не удалось создать ключ установки")
                        }
                    }
                ) {
                    Text(stringResource(R.string.signal_copy_47))
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
                        error = uiCopy("Нужен HTTPS-адрес без пути, пароля и параметров")
                    }
                }
            ) {
                Text(stringResource(R.string.signal_copy_48))
            }
        },
        dismissButton = {
            TextButton(onClick = close) { Text(stringResource(R.string.signal_copy_49)) }
        },
    )
}
