@file:Suppress(
    "LongMethod",
    "CyclomaticComplexMethod",
) // Explicit product states and Compose trees.

package com.monumentogram.dora.recording

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.recording.RecordingDurability
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.ui.DoraBootstrapApp
import com.monumentogram.dora.ui.theme.DoraDesignTokens
import com.monumentogram.dora.ui.theme.DoraDimensions
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay

@Composable
internal fun ProductRecordingHost(activity: MainActivity, action: Triple<String?, String?, Int>) {
    val app = activity.application as DoraApplication
    val controller = app.recording
    val snapshot by controller.state.collectAsState()
    var showRecording by rememberSaveable { mutableStateOf(false) }
    var foreground by remember { mutableStateOf(false) }
    var authorized by remember { mutableStateOf(false) }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) foreground = true
            if (event == Lifecycle.Event.ON_PAUSE) {
                foreground = false
                authorized = false
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(foreground) {
        while (foreground) {
            authorized = app.audioRuntime.isRecordingUiAuthorized()
            delay(AUTH_REFRESH_MILLIS)
        }
    }
    LaunchedEffect(action) {
        if (
            action.second != null &&
                action.second == controller.actionToken &&
                action.first in
                    setOf(
                        ProductRecordingService.OPEN,
                        ProductRecordingService.STOP,
                        ProductRecordingService.RESUME,
                    )
        ) {
            showRecording = true
            if (action.first == ProductRecordingService.STOP) controller.requestStop()
        }
    }
    // Notification returns to visible UI; Continue requires current foreground authority or fresh
    // proof.
    if (showRecording) {
        BackHandler { showRecording = false }
        RecordingScreen(activity, snapshot, authorized, { showRecording = false })
    } else
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            val phase = snapshot.recording.phase
            if (
                phase in
                    setOf(
                        RecordingPhase.PREPARING,
                        RecordingPhase.RECORDING,
                        RecordingPhase.PAUSED,
                        RecordingPhase.FINALIZING,
                    )
            ) {
                TextButton(
                    onClick = { showRecording = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = DoraDimensions.touchMinimum),
                ) {
                    Text(
                        (if (phase == RecordingPhase.PAUSED) "Пауза" else "Запись") +
                            " · " +
                            (if (authorized) capturedTime(snapshot.recording.frames) + " · "
                            else "") +
                            "К записи"
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                DoraBootstrapApp(onRecordingRequested = { showRecording = true })
            }
        }
}

@Composable
internal fun RecordingScreen(
    activity: Activity,
    snapshot: RecordingViewState,
    authorized: Boolean,
    onBack: () -> Unit,
) {
    val app = activity.application as DoraApplication
    val controller = app.recording
    val state = snapshot.recording
    val palette = DoraDesignTokens.darkPalette
    val window = (activity as? MainActivity)?.presentationWindow ?: 0L
    val screen = remember(activity) { controller.latency.newPresentationEpoch() }
    val scroll = rememberScrollState()
    DisposableEffect(activity, screen) {
        controller.latency.presentation("mount", window, screen)
        onDispose { controller.latency.presentation("unmount", window, screen) }
    }
    LaunchedEffect(scroll.value) {
        controller.latency.presentation("scroll", window, screen, "y=${scroll.value}")
    }
    if (
        state.phase in
            setOf(
                RecordingPhase.PREFLIGHT,
                RecordingPhase.EMPTY,
                RecordingPhase.SAVED,
                RecordingPhase.INTERRUPTED,
            )
    ) {
        Surface(
            color = Color(palette.canvas.surfaceDeep),
            contentColor = Color(palette.text.onDeep),
        ) {
            RecordingPreflight(
                activity,
                controller,
                onBack,
                header = {
                    Text("Запись", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        when (state.phase) {
                            RecordingPhase.SAVED -> "Запись сохранена"
                            RecordingPhase.EMPTY -> "Запись завершена без аудио"
                            RecordingPhase.INTERRUPTED -> "Запись прервана"
                            else -> "Перед началом записи"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    if (state.phase == RecordingPhase.SAVED && authorized) {
                        Text(capturedTime(state.frames))
                        Text("Сохранено на устройстве: ${capturedTime(state.durableFrames)}")
                    }
                    if (state.phase == RecordingPhase.INTERRUPTED) {
                        Text(
                            persistenceMessage(state.persistenceFailure)
                                ?: captureMessage(snapshot.failure)
                        )
                        if (state.persistenceFailure == AudioFailure.CREDENTIAL_SETUP_REQUIRED)
                            Button(onClick = { app.audioRuntime.openCredentialSetup(activity) }) {
                                Text("Настроить защиту устройства")
                            }
                        Text(
                            "Сохранённое аудио остаётся на устройстве. Для проверки требуется разблокировка."
                        )
                    }
                },
                footer = { RecordingRecoveryCard(activity, authorized) },
            )
        }
        return
    }
    Surface(color = Color(palette.canvas.surfaceDeep), contentColor = Color(palette.text.onDeep)) {
        Column(
            Modifier.fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    controller.latency.presentation(
                        "geometry",
                        window,
                        screen,
                        "x=${bounds.left.toRawBits()} y=${bounds.top.toRawBits()} " +
                            "h=${bounds.height.toRawBits()} z=${bounds.width.toRawBits()}",
                    )
                }
                .safeDrawingPadding()
                .verticalScroll(scroll)
                .padding(DoraDimensions.space6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DoraDimensions.space4),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = DoraDimensions.touchMinimum),
                ) {
                    Text("Назад")
                }
                Text("Запись", style = MaterialTheme.typography.headlineMedium)
            }
            val status =
                when (state.phase) {
                    RecordingPhase.PREFLIGHT -> "Перед началом записи"
                    RecordingPhase.PREPARING -> "Подключаем микрофон…"
                    RecordingPhase.RECORDING -> "Запись продолжается"
                    RecordingPhase.PAUSED -> "Запись приостановлена"
                    RecordingPhase.FINALIZING -> "Проверяем сохранение"
                    RecordingPhase.SAVED -> "Запись сохранена"
                    RecordingPhase.EMPTY -> "Запись завершена без аудио"
                    RecordingPhase.INTERRUPTED -> "Запись прервана"
                }
            Text(
                when {
                    snapshot.pausePending -> "Приостанавливаем…"
                    snapshot.resumePending -> "Возобновляем запись…"
                    else -> status
                },
                style = MaterialTheme.typography.titleLarge,
                modifier =
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        .onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            controller.latency.presentation(
                                "status_geometry",
                                window,
                                screen,
                                "x=${bounds.left.toRawBits()} y=${bounds.top.toRawBits()} " +
                                    "h=${bounds.height.toRawBits()} z=${bounds.width.toRawBits()}",
                            )
                        }
                        .drawWithContent {
                            drawContent()
                            controller.rendered(
                                snapshot,
                                window,
                                screen,
                                "${(activity as? MainActivity)?.presentationVisibility.orEmpty()} y=${scroll.value}",
                            )
                        },
            )
            if (
                state.phase in
                    setOf(
                        RecordingPhase.RECORDING,
                        RecordingPhase.PAUSED,
                        RecordingPhase.FINALIZING,
                        RecordingPhase.SAVED,
                    )
            ) {
                if (authorized) {
                    DoraWave(
                        snapshot.level,
                        snapshot.signalAtNanos,
                        state.phase == RecordingPhase.RECORDING &&
                            !snapshot.pausePending &&
                            !snapshot.resumePending,
                        activity,
                    )
                    Text(
                        capturedTime(state.frames),
                        style = MaterialTheme.typography.displayLarge,
                        modifier =
                            Modifier.drawWithContent {
                                drawContent()
                                controller.latency.timer(state.frames)
                            },
                    )
                    Text(snapshot.route)
                    // Keep the control row stationary when asynchronous persistence catches up.
                    Text(
                        if (state.durability != RecordingDurability.PENDING) " "
                        else if (state.phase == RecordingPhase.PAUSED)
                            "Сохраняем последние секунды…"
                        else "Сохраняем…",
                        minLines = 2,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        if (state.durableFrames > 0)
                            "Сохранено на устройстве: ${capturedTime(state.durableFrames)}"
                        else "Ожидаем подтверждения сохранения",
                        minLines = 2,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                } else {
                    Text("Запись защищена. Разблокируйте DORA для просмотра.")
                    Button(
                        onClick = { app.audioRuntime.requestRecordingUiUnlock(activity) {} },
                        modifier = Modifier.heightIn(min = DoraDimensions.touchMinimum),
                    ) {
                        Text("Разблокировать")
                    }
                }
            }
            when (state.phase) {
                RecordingPhase.PREFLIGHT,
                RecordingPhase.EMPTY,
                RecordingPhase.SAVED -> Unit // Rendered in the bounded preflight surface above.
                RecordingPhase.PREPARING ->
                    TextButton(onClick = controller::requestStop) { Text("Отмена") }
                RecordingPhase.RECORDING,
                RecordingPhase.PAUSED ->
                    Row(horizontalArrangement = Arrangement.spacedBy(DoraDimensions.space4)) {
                        Button(
                            enabled = !snapshot.pausePending && !snapshot.resumePending,
                            onClick = {
                                if (state.phase == RecordingPhase.PAUSED)
                                    controller.resume(activity)
                                else controller.pause()
                            },
                            modifier = Modifier.heightIn(min = DoraDimensions.recordControl),
                        ) {
                            Text(
                                if (state.phase == RecordingPhase.PAUSED) "Продолжить" else "Пауза"
                            )
                        }
                        OutlinedButton(
                            onClick = controller::requestStop,
                            modifier = Modifier.heightIn(min = DoraDimensions.recordControl),
                        ) {
                            Text("Стоп")
                        }
                    }
                RecordingPhase.INTERRUPTED -> Unit
                RecordingPhase.FINALIZING -> Text("Дождитесь подтверждения сохранения")
            }
        }
    }
    if (state.stopConfirmation)
        AlertDialog(
            onDismissRequest = controller::cancelStop,
            title = { Text("Завершить запись?") },
            text = {
                Column {
                    Text("Уже записанное будет защищено и останется на устройстве.")
                    Text(
                        if (state.phase == RecordingPhase.PAUSED) "Запись приостановлена"
                        else if (state.phase == RecordingPhase.RECORDING) "Запись продолжается"
                        else "Подготовка записи"
                    )
                }
            },
            confirmButton = { TextButton(onClick = controller::confirmStop) { Text("Завершить") } },
            dismissButton = {
                TextButton(onClick = controller::cancelStop) { Text("Продолжить запись") }
            },
        )
}

@Composable
private fun RecordingPreflight(
    activity: Activity,
    controller: RecordingController,
    onCancel: () -> Unit,
    header: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    var storageBudget by remember { mutableStateOf(controller.storageBudget()) }
    var microphone by remember {
        mutableStateOf(
            activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var denied by remember { mutableStateOf(false) }
    var notifications by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val micRequest =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            microphone = granted
            denied = !granted
            if (granted && acknowledged) {
                acknowledged = false
                controller.start(activity)
            }
        }
    val notificationRequest =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            notifications = it
        }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(DoraDimensions.space4),
        verticalArrangement = Arrangement.spacedBy(DoraDimensions.space2),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DoraDimensions.space4),
        ) {
            header()
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            Text("Микрофон: ${if (microphone) "доступ разрешён" else "нужно разрешение"}")
            Text("Активный маршрут микрофона будет определён при запуске")
            val available = storageBudget.availableBytes
            Text(
                if (available == null) "Не удалось проверить свободное место"
                else "Доступно на устройстве: ${available / BYTES_PER_MB} МБ"
            )
            Text(
                "Для часа записи нужно 125 МБ и резерв на завершение 16 MiB: всего не менее 142 МБ."
            )
            Text("Резерв учитывается при запуске; место заранее не выделяется.")
            if (!storageBudget.canStart) {
                Text("Освободите место и обновите проверку. Сохранённые записи останутся доступны.")
            }
            TextButton(onClick = { storageBudget = controller.storageBudget() }) {
                Text("Обновить проверку места")
            }
            Text("Аудио записывается только на этом устройстве и сохраняется в зашифрованном виде.")
            Text(
                "DORA использует микрофон, чтобы записывать звук, пока вы не нажмёте «Пауза» " +
                    "или не завершите запись. Запись продолжится при выключенном экране."
            )
            if (denied) {
                Text("Микрофон не разрешён. Запись не началась.")
                if (
                    !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                )
                    TextButton(
                        onClick = {
                            activity.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${activity.packageName}"),
                                )
                            )
                        }
                    ) {
                        Text("Разрешить в настройках")
                    }
            }
            if (!notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text(
                    "Уведомления отключены. Android может показывать запись только в списке активных приложений."
                )
                TextButton(
                    onClick = { notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS) }
                ) {
                    Text("Разрешить уведомления")
                }
            }
            footer()
        }
        Button(
            enabled = acknowledged && storageBudget.canStart,
            onClick = {
                if (
                    activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                ) {
                    acknowledged = false
                    controller.start(activity)
                } else micRequest.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = DoraDimensions.buttonPrimaryHeight),
        ) {
            Text("Начать запись")
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.heightIn(min = DoraDimensions.touchMinimum),
        ) {
            Text("Отмена")
        }
    }
}

@Composable
private fun DoraWave(level: Float, signalAtNanos: Long, active: Boolean, activity: Activity) {
    val reduced =
        Settings.Global.getFloat(
            activity.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    var lastSample by remember { mutableStateOf(0L) }
    var levels by remember { mutableStateOf(List(if (reduced) REDUCED_BARS else WAVE_BARS) { 0f }) }
    LaunchedEffect(active, reduced, signalAtNanos) {
        val interval = if (reduced) REDUCED_PERIOD_MILLIS else WAVE_PERIOD_MILLIS
        if (
            active && signalAtNanos > 0 && signalAtNanos - lastSample >= interval * NANOS_PER_MILLI
        ) {
            levels = listOf(level) + levels.dropLast(1)
            lastSample = signalAtNanos
        }
    }
    val palette = DoraDesignTokens.darkPalette
    val color = Color(if (active) palette.wave.active else palette.wave.paused)
    Canvas(Modifier.size(WAVE_SIZE_DP.dp).clearAndSetSemantics {}) {
        val radius = size.minDimension * WAVE_RADIUS
        levels.forEachIndexed { index, amplitude ->
            val angle = index * 2.0 * Math.PI / levels.size - Math.PI / 2
            val length =
                WAVE_STROKE_DP.dp.toPx() +
                    sqrt(amplitude.coerceIn(0f, 1f)) * size.minDimension * WAVE_EXTENT
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(
                color,
                center + direction * radius,
                center + direction * (radius + length),
                WAVE_STROKE_DP.dp.toPx(),
                StrokeCap.Round,
            )
        }
    }
}

internal fun capturedTime(frames: Long): String {
    val seconds = frames / SAMPLE_RATE
    return "%02d:%02d:%02d"
        .format(
            java.util.Locale.ROOT,
            seconds / SECONDS_PER_HOUR,
            seconds / SECONDS_PER_MINUTE % SECONDS_PER_MINUTE,
            seconds % SECONDS_PER_MINUTE,
        )
}

private fun persistenceMessage(failure: AudioFailure?): String? =
    when (failure) {
        null -> null
        AudioFailure.LOCKED,
        AudioFailure.CANCELLED -> "Требуется разблокировка DORA"
        AudioFailure.CREDENTIAL_SETUP_REQUIRED ->
            "Настройте PIN-код, пароль или графический ключ Android"
        AudioFailure.KEY_UNAVAILABLE -> "Ключ хранилища временно недоступен"
        AudioFailure.KEY_INVALIDATED ->
            "Ключ хранилища недоступен. Аудио сохранено в зашифрованном виде"
        AudioFailure.CORRUPT,
        AudioFailure.AUTHENTICATION_FAILED -> "Проверка сохранённого аудио не пройдена"
        AudioFailure.BUSY -> "Хранилище занято. Повторите после завершения операции"
        AudioFailure.UNCERTAIN,
        AudioFailure.INCOMPLETE -> "Сохранение требует проверки восстановления"
        else -> "Не удалось подтвердить сохранение аудио"
    }

private fun captureMessage(failure: CaptureFailure?): String =
    when (failure) {
        CaptureFailure.PERMISSION_DENIED -> "Доступ к микрофону отозван"
        CaptureFailure.CONFIGURATION_UNAVAILABLE ->
            "Микрофон не поддерживает необходимый формат 16 кГц, моно, PCM16"
        CaptureFailure.INITIALIZATION_FAILED -> "Не удалось подготовить микрофон"
        CaptureFailure.START_FAILED,
        CaptureFailure.MICROPHONE_UNAVAILABLE -> "Микрофон недоступен"
        CaptureFailure.DEAD_OBJECT -> "Соединение с микрофоном потеряно"
        CaptureFailure.READ_ERROR -> "Ошибка чтения микрофона"
        CaptureFailure.SERVICE_REJECTED ->
            "Android не разрешил запуск записи. Откройте DORA и повторите"
        CaptureFailure.STORAGE_FULL -> "Недостаточно свободного места"
        CaptureFailure.PERSISTENCE_BACKPRESSURE -> "Хранилище не успевает сохранять аудио"
        CaptureFailure.THREAD_TIMEOUT -> "Не удалось подтвердить остановку микрофона"
        else -> "Запись остановлена. Необходимо проверить сохранённое аудио"
    }

private const val AUTH_REFRESH_MILLIS = 100L
private const val WAVE_BARS = 72
private const val WAVE_SIZE_DP = 248
private const val WAVE_STROKE_DP = 3
private const val BYTES_PER_MB = 1_000_000L
private const val SAMPLE_RATE = 16_000
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
private const val NANOS_PER_MILLI = 1_000_000L
private const val REDUCED_BARS = 12
private const val WAVE_PERIOD_MILLIS = 50L
private const val REDUCED_PERIOD_MILLIS = 250L
private const val WAVE_RADIUS = 0.32f
private const val WAVE_EXTENT = 0.15f
