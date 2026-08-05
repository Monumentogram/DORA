@file:Suppress("CyclomaticComplexMethod", "LongMethod", "MagicNumber", "TooManyFunctions")

package com.monumentogram.dora.poc.capture.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monumentogram.dora.poc.capture.model.CaptureOutcome
import com.monumentogram.dora.poc.capture.model.CaptureUiState
import com.monumentogram.dora.poc.capture.model.DeviceProfile
import com.monumentogram.dora.poc.capture.model.FlowPhase
import com.monumentogram.dora.poc.capture.model.LiveMetrics
import com.monumentogram.dora.poc.capture.model.ManualAnswer
import com.monumentogram.dora.poc.capture.model.ManualObservations
import com.monumentogram.dora.poc.capture.model.RunKind
import com.monumentogram.dora.poc.capture.runtime.CaptureController
import java.util.Locale

@Composable
fun CaptureApp(controller: CaptureController, onExplicitStart: () -> Unit) {
    val state by controller.uiState.collectAsState()
    BackHandler(
        enabled =
            state.phase == FlowPhase.PREFLIGHT ||
                state.phase == FlowPhase.QUESTIONNAIRE ||
                state.phase == FlowPhase.READY_TO_EXPORT ||
                state.phase == FlowPhase.RECORDING ||
                state.phase == FlowPhase.STARTING ||
                state.phase == FlowPhase.REVIEW
    ) {
        when (state.phase) {
            FlowPhase.PREFLIGHT,
            FlowPhase.QUESTIONNAIRE,
            FlowPhase.READY_TO_EXPORT -> controller.backToRuns()
            else -> Unit
        }
    }
    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
    ) {
        AppHeader()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state.phase) {
                FlowPhase.DEVICE -> DeviceScreen(state, controller)
                FlowPhase.RUN_SELECTION -> RunSelectionScreen(state, controller)
                FlowPhase.PREFLIGHT -> PreflightScreen(state, controller, onExplicitStart)
                FlowPhase.STARTING -> StartingScreen(controller)
                FlowPhase.RECORDING -> RecordingScreen(state, controller)
                FlowPhase.REVIEW -> ReviewScreen(state, controller)
                FlowPhase.QUESTIONNAIRE -> QuestionnaireScreen(state, controller)
                FlowPhase.READY_TO_EXPORT -> ReadyToExportScreen(state, controller)
                FlowPhase.RECOVERY -> RecoveryScreen(state, controller)
                FlowPhase.ERROR -> ErrorScreen(state, controller)
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = CaptureDimensions.space4, vertical = CaptureDimensions.space3)
    ) {
        Text(
            text = "Dora Capture PoC",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Технический тест. Не является готовой Dora.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun DeviceScreen(state: CaptureUiState, controller: CaptureController) {
    Page(CaptureTestTags.SCREEN_DEVICE) {
        Title("Об устройстве", "Сначала приложение безопасно проверит этот телефон.")
        NoticeCard(
            "Собираются только модель, версия Android, память, место, заряд, " +
                "thermal status и типы аудиовходов. Уникальные идентификаторы, " +
                "account и сетевые адреса не читаются."
        )
        state.preparationMessage?.let { NoticeCard(it) }
        Button(
            onClick = controller::prepareDevice,
            modifier = Modifier.fillMaxWidth().testTag(CaptureTestTags.PREPARE_DEVICE),
        ) {
            Text("Подготовить устройство")
        }
        Text(
            "Разрешение на микрофон на этом шаге не запрашивается.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RunSelectionScreen(state: CaptureUiState, controller: CaptureController) {
    Page(CaptureTestTags.SCREEN_RUNS) {
        Title("Выбор теста", "Тесты открываются последовательно после безопасного завершения.")
        state.preparationMessage?.let { SuccessCard("✓ $it") }
        state.preparedProfile?.let { profile ->
            DeviceSummary(profile)
            OutlinedButton(
                onClick = controller::exportDeviceProfile,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Экспортировать профиль устройства")
            }
        }
        HorizontalDivider()
        RunKind.entries.forEach { run ->
            val enabled = state.isUnlocked(run)
            Button(
                onClick = { controller.selectRun(run) },
                enabled = enabled,
                modifier =
                    Modifier.fillMaxWidth()
                        .testTag(
                            when (run) {
                                RunKind.RUN_A -> CaptureTestTags.RUN_A
                                RunKind.RUN_B -> CaptureTestTags.RUN_B
                                RunKind.RUN_C -> CaptureTestTags.RUN_C
                            }
                        ),
            ) {
                Text(
                    when {
                        run in state.criticalRuns -> "${run.title} · critical failure"
                        run in state.completedRuns -> "${run.title} · завершён"
                        enabled -> run.title
                        else -> "${run.title} · пока заблокирован"
                    }
                )
            }
        }
        NoticeCard(
            "Один телефон и три запуска не дают общий PASS для D1–D7. Без critical failure итог остаётся INCONCLUSIVE."
        )
    }
}

@Composable
private fun PreflightScreen(
    state: CaptureUiState,
    controller: CaptureController,
    onExplicitStart: () -> Unit,
) {
    val run = requireNotNull(state.selectedRun)
    val profile = state.preparedProfile
    Page(CaptureTestTags.SCREEN_PREFLIGHT) {
        Title("Перед запуском", run.title)
        InfoRow("Целевая длительность", formatDuration(run.targetSeconds * 1_000L))
        InfoRow("Заряд", profile?.batteryPercent.percentText())
        InfoRow("Питание", profile?.chargingState ?: "Недоступно")
        InfoRow("Свободное место", profile?.freeStorageMb?.let { "$it MiB" } ?: "Недоступно")
        InfoRow("Thermal status", profile?.thermalStatus ?: "Недоступно")
        NoticeCard(
            "Проводите тест в тихом помещении. Не разговаривайте рядом с телефоном " +
                "и убедитесь, что рядом нет чужих разговоров."
        )
        if (run.screenOffExpected) {
            NoticeCard(
                "Рекомендуется включить авиарежим. После появления постоянного уведомления " +
                    "выключите экран на большую часть теста."
            )
        }
        if (run.fixtureAllowed) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Воспроизводить тестовые сигналы", fontWeight = FontWeight.Medium)
                    Text(
                        "Собственные tones/chirps без речи и музыки",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = state.fixtureEnabled,
                    onCheckedChange = controller::setFixtureEnabled,
                )
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(
                checked = state.acknowledgementChecked,
                onCheckedChange = controller::setAcknowledgement,
                modifier = Modifier.testTag(CaptureTestTags.ACKNOWLEDGEMENT),
            )
            Text(
                "Я понимаю, что это технический тест, и рядом нет людей, которых записывают без предупреждения.",
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Text(
            "Checkbox является напоминанием и не определяет законность записи.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onExplicitStart,
            enabled = state.acknowledgementChecked,
            modifier = Modifier.fillMaxWidth().testTag(CaptureTestTags.START),
        ) {
            Text("Start")
        }
        TextButton(onClick = controller::backToRuns, modifier = Modifier.fillMaxWidth()) {
            Text("Назад к выбору теста")
        }
    }
}

@Composable
private fun StartingScreen(controller: CaptureController) {
    Page("screen_starting") {
        Title("Запуск записи", "Создаётся microphone foreground service…")
        NoticeCard("Не выключайте экран, пока не появится экран записи и постоянное уведомление.")
        OutlinedButton(
            onClick = controller::abortAndDelete,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Прервать тест и удалить данные")
        }
    }
}

@Composable
private fun RecordingScreen(state: CaptureUiState, controller: CaptureController) {
    val metrics = requireNotNull(state.liveMetrics)
    Page(CaptureTestTags.SCREEN_RECORDING) {
        Title("Идёт запись", metrics.run.title)
        RecordingStatus(metrics)
        Button(
            onClick = controller::requestStop,
            modifier = Modifier.fillMaxWidth().testTag(CaptureTestTags.STOP),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
        ) {
            Text("Stop")
        }
        OutlinedButton(
            onClick = controller::abortAndDelete,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Прервать тест и удалить данные")
        }
        Text(
            "Абсолютный путь файла намеренно не показывается.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReviewScreen(state: CaptureUiState, controller: CaptureController) {
    val outcome = requireNotNull(state.outcome)
    Page(CaptureTestTags.SCREEN_REVIEW) {
        Title("Запись остановлена", "Сначала проверьте и безвозвратно удалите app-private аудио.")
        OutcomeMetrics(outcome)
        Button(
            onClick = controller::analyzeAndDelete,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Проверить и удалить аудио")
        }
        OutlinedButton(
            onClick = controller::discardStoppedRun,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Прервать тест и удалить данные")
        }
        NoticeCard("Экспорт заблокирован, пока приложение не подтвердит отсутствие WAV-файла.")
    }
}

@Composable
private fun QuestionnaireScreen(state: CaptureUiState, controller: CaptureController) {
    val runId = state.outcome?.runId.orEmpty()
    var answers by remember(runId) { mutableStateOf(ManualObservations()) }
    Page("screen_questionnaire") {
        Title("Пять наблюдений", "Выберите Да, Нет или Не знаю для каждого пункта.")
        ManualQuestion(
            "Постоянное уведомление было видно?",
            answers.notificationVisible,
        ) {
            answers = answers.copy(notificationVisible = it)
        }
        ManualQuestion(
            "Экран был выключен большую часть теста?",
            answers.screenMostlyOff,
        ) {
            answers = answers.copy(screenMostlyOff = it)
        }
        ManualQuestion(
            "Происходил звонок или другое вмешательство?",
            answers.callOrInterruption,
        ) {
            answers = answers.copy(callOrInterruption = it)
        }
        ManualQuestion(
            "Телефон находился на зарядке?",
            answers.phoneCharging,
        ) {
            answers = answers.copy(phoneCharging = it)
        }
        ManualQuestion(
            "Был перегрев или неожиданная остановка?",
            answers.overheatingOrUnexpectedStop,
        ) {
            answers = answers.copy(overheatingOrUnexpectedStop = it)
        }
        Button(
            onClick = { controller.submitObservations(answers) },
            enabled = answers.complete,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Сохранить наблюдения")
        }
    }
}

@Composable
private fun ReadyToExportScreen(state: CaptureUiState, controller: CaptureController) {
    val receipt = requireNotNull(state.deletionReceipt)
    Page("screen_ready_export") {
        Title("Отчёт готов", "Raw audio удалён до формирования ZIP.")
        SuccessCard("✓ Удаление подтверждено: файл отсутствует")
        InfoRow("WAV был валиден", if (receipt.wavWasValid) "Да" else "Нет")
        InfoRow("Удалено байт", receipt.bytesBeforeDeletion.toString())
        InfoRow("SHA-256", receipt.sha256 ?: "Недоступен", monospaceLike = true)
        Button(onClick = controller::exportRun, modifier = Modifier.fillMaxWidth()) {
            Text("Экспортировать безопасный ZIP")
        }
        state.exportMessage?.let { SuccessCard(it) }
        if (state.selectedRun in state.criticalRuns) {
            NoticeCard("Обнаружен critical failure. Следующий Run остаётся заблокирован.")
        }
        TextButton(onClick = controller::backToRuns, modifier = Modifier.fillMaxWidth()) {
            Text("Вернуться к тестам")
        }
    }
}

@Composable
private fun RecoveryScreen(state: CaptureUiState, controller: CaptureController) {
    val candidate = requireNotNull(state.recoveryCandidate)
    Page("screen_recovery") {
        Title("Незавершённый тест", "После сбоя запись никогда не продолжается автоматически.")
        NoticeCard(
            "Найден app-private тестовый файл (${candidate.bytes} байт). Путь и содержимое не экспортируются."
        )
        Button(
            onClick = { controller.resolveRecovery(analyzeFirst = true) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Безопасно проанализировать, удалить и создать receipt")
        }
        OutlinedButton(
            onClick = { controller.resolveRecovery(analyzeFirst = false) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Удалить без анализа и создать failure receipt")
        }
    }
}

@Composable
private fun ErrorScreen(state: CaptureUiState, controller: CaptureController) {
    Page("screen_error") {
        Title("Тест не продолжен", "Микрофон не запускается автоматически после ошибки.")
        Card(
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Text(
                state.errorMessage ?: "Неизвестная техническая ошибка",
                modifier = Modifier.padding(CaptureDimensions.space4),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        if (state.recoveryCandidate != null) {
            Button(onClick = controller::retryRecovery, modifier = Modifier.fillMaxWidth()) {
                Text("Вернуться к безопасному удалению")
            }
        } else {
            Button(onClick = controller::backToRuns, modifier = Modifier.fillMaxWidth()) {
                Text("Вернуться")
            }
        }
    }
}

@Composable
private fun DeviceSummary(profile: DeviceProfile) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CaptureDimensions.space4)) {
            Text("Профиль устройства", style = MaterialTheme.typography.titleMedium)
            InfoRow("Модель", "${profile.manufacturer} ${profile.model}")
            InfoRow("Android", "${profile.androidVersion} · API ${profile.androidApi}")
            InfoRow("Build.ID", profile.buildId)
            InfoRow("Security patch", profile.securityPatch ?: "Недоступен")
            InfoRow("ABI", profile.primaryAbi)
            InfoRow("RAM", "≈ ${profile.totalRamMb} MiB")
            InfoRow("Свободно", "${profile.freeStorageMb} MiB")
            InfoRow("Заряд", profile.batteryPercent.percentText())
            InfoRow("Питание", profile.chargingState)
            InfoRow("Thermal", profile.thermalStatus ?: "Недоступен")
            InfoRow("Page size", "${profile.pageSizeBytes} bytes")
            InfoRow("Кандидат D-профиля", "${profile.candidateProfileId} · требует проверки Codex")
            InfoRow("Аудиовходы", profile.audioInputTypes.joinToString())
        }
    }
}

@Composable
private fun RecordingStatus(metrics: LiveMetrics) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CaptureDimensions.space4)) {
            InfoRow("Таймер", formatDuration(metrics.elapsedMs))
            InfoRow("Статус", "Запись активна")
            InfoRow("Сэмплы", metrics.counters.samples.toString())
            InfoRow("Записано байт", metrics.counters.bytes.toString())
            InfoRow("Размер файла", formatBytes(metrics.counters.fileBytes))
            InfoRow("Short reads", metrics.counters.shortReads.toString())
            InfoRow("AudioRecord errors", metrics.counters.errors.values.sum().toString())
            InfoRow("Экран включён", formatDuration(metrics.screenOnMs))
            InfoRow("Экран выключен", formatDuration(metrics.screenOffMs))
            InfoRow("Заряд", metrics.batteryPercent.percentText())
            InfoRow("Thermal", metrics.thermalStatus ?: "Недоступен")
            InfoRow("Foreground service", metrics.serviceState.label)
            InfoRow("Маршрут микрофона", metrics.counters.route)
        }
    }
}

@Composable
private fun OutcomeMetrics(outcome: CaptureOutcome) {
    val batteryChange =
        "${outcome.startSnapshot.batteryPercent.percentText()} / " +
            outcome.endSnapshot.batteryPercent.percentText()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CaptureDimensions.space4)) {
            InfoRow("Фактическая длительность", formatDuration(outcome.actualDurationMs))
            InfoRow("Ожидаемые сэмплы", outcome.expectedSamples.toString())
            InfoRow("Фактические сэмплы", outcome.counters.samples.toString())
            InfoRow("Расхождение", outcome.sampleDelta.toString())
            InfoRow("Записано байт", outcome.counters.bytes.toString())
            InfoRow("WAV validity", if (outcome.wavAnalysis.valid) "Валиден" else "Ошибка")
            InfoRow("SHA-256", outcome.wavAnalysis.sha256 ?: "Недоступен", monospaceLike = true)
            InfoRow("Start latency", "${outcome.startLatencyMs} ms")
            InfoRow("Stop/finalization latency", "${outcome.finalizationLatencyMs} ms")
            InfoRow("Short reads", outcome.counters.shortReads.toString())
            InfoRow("Ошибки", outcome.counters.errors.values.sum().toString())
            InfoRow("Заряд до / после", batteryChange)
            InfoRow("Thermal maximum", outcome.maxThermalStatus ?: "Недоступен")
            InfoRow(
                "Peak process PSS",
                outcome.peakPssMb?.let { "%.1f MiB".format(it) } ?: "Недоступен",
            )
            InfoRow("Экран выключен", formatDuration(outcome.screenOffMs))
            InfoRow("Прерывания", outcome.interruptionCount.toString())
            InfoRow("Размер файла", formatBytes(outcome.counters.fileBytes))
            InfoRow(
                "Формат",
                "mono PCM16 · ${outcome.configuration.sampleRate} Hz" +
                    if (outcome.configuration.fallbackUsed) " · fallback" else "",
            )
        }
    }
}

@Composable
private fun ManualQuestion(
    question: String,
    selected: ManualAnswer?,
    onSelected: (ManualAnswer) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CaptureDimensions.space2)) {
        Text(question, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CaptureDimensions.space2),
        ) {
            ManualAnswer.entries.forEach { answer ->
                OutlinedButton(
                    onClick = { onSelected(answer) },
                    modifier = Modifier.weight(1f),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            containerColor =
                                if (selected == answer) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                        ),
                ) {
                    Text(answer.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Page(testTag: String, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .widthIn(max = CaptureDimensions.readingWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(CaptureDimensions.space4)
                    .testTag(testTag),
            verticalArrangement = Arrangement.spacedBy(CaptureDimensions.space3),
            content = content,
        )
    }
}

@Composable
private fun Title(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NoticeCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(text, modifier = Modifier.padding(CaptureDimensions.space4))
    }
}

@Composable
private fun SuccessCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Text(
            text,
            modifier = Modifier.padding(CaptureDimensions.space4),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, monospaceLike: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CaptureDimensions.space1),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.42f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(CaptureDimensions.space2))
        Text(
            value,
            modifier = Modifier.weight(0.58f),
            style =
                if (monospaceLike) MaterialTheme.typography.bodySmall
                else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun Double?.percentText(): String =
    this?.let { "%.1f%%".format(Locale.US, it) } ?: "Недоступен"

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
}

private fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1_048_576L -> "%.1f MiB".format(Locale.US, bytes / 1_048_576.0)
        bytes >= 1_024L -> "%.1f KiB".format(Locale.US, bytes / 1_024.0)
        else -> "$bytes B"
    }
