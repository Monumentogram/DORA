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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
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
import com.monumentogram.dora.R
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.recording.RecordingDurability
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.ui.signal.SignalShell
import com.monumentogram.dora.ui.signal.uiCopy
import com.monumentogram.dora.ui.theme.DoraDimensions
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
                        (if (phase == RecordingPhase.PAUSED) uiCopy("Пауза")
                        else uiCopy("Запись")) +
                            " · " +
                            (if (authorized) capturedTime(snapshot.recording.frames) + " · "
                            else "") +
                            uiCopy("К записи")
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                SignalShell(activity, onRecord = { showRecording = true })
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
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            RecordingPreflight(
                activity,
                controller,
                onBack,
                header = {
                    Text(
                        stringResource(R.string.signal_copy_1),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        when (state.phase) {
                            RecordingPhase.SAVED ->
                                if (state.alphaLimitReached)
                                    uiCopy(
                                        "Достигнут лимит записи Alpha — 5 минут. Запись сохранена"
                                    )
                                else uiCopy("Запись сохранена")
                            RecordingPhase.EMPTY -> uiCopy("Запись завершена без аудио")
                            RecordingPhase.INTERRUPTED -> uiCopy("Запись прервана")
                            else -> uiCopy("Перед началом записи")
                        },
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    if (state.phase == RecordingPhase.SAVED && authorized) {
                        Text(capturedTime(state.frames))
                        Text(
                            uiCopy("Сохранено на устройстве") +
                                ": ${capturedTime(state.durableFrames)}"
                        )
                    }
                    if (state.phase == RecordingPhase.INTERRUPTED) {
                        Text(
                            persistenceMessage(state.persistenceFailure)
                                ?: captureMessage(snapshot.failure)
                        )
                        Text(
                            uiCopy(
                                "Сохранённое аудио остаётся на устройстве. Проверьте его состояние ниже."
                            )
                        )
                    }
                },
                footer = { RecordingRecoveryCard(activity, authorized) },
            )
        }
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
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
                    Text(stringResource(R.string.signal_copy_0))
                }
                Text(
                    stringResource(R.string.signal_copy_1),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            val status =
                when (state.phase) {
                    RecordingPhase.PREFLIGHT -> uiCopy("Перед началом записи")
                    RecordingPhase.PREPARING -> uiCopy("Подключаем микрофон…")
                    RecordingPhase.RECORDING -> uiCopy("Запись продолжается")
                    RecordingPhase.PAUSED -> uiCopy("Запись приостановлена")
                    RecordingPhase.FINALIZING -> uiCopy("Проверяем сохранение")
                    RecordingPhase.SAVED -> uiCopy("Запись сохранена")
                    RecordingPhase.EMPTY -> uiCopy("Запись завершена без аудио")
                    RecordingPhase.INTERRUPTED -> uiCopy("Запись прервана")
                }
            Text(
                when {
                    snapshot.pausePending -> uiCopy("Приостанавливаем…")
                    snapshot.resumePending -> uiCopy("Возобновляем запись…")
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
                    Text(uiCopy("Осталось в Alpha") + ": ${capturedTime(state.remainingFrames)}")
                    // Keep the control row stationary when asynchronous persistence catches up.
                    Text(
                        if (state.durability != RecordingDurability.PENDING) " "
                        else if (state.phase == RecordingPhase.PAUSED)
                            uiCopy("Сохраняем последние секунды…")
                        else uiCopy("Сохраняем…"),
                        minLines = 2,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        if (state.durableFrames > 0)
                            uiCopy("Сохранено на устройстве") +
                                ": ${capturedTime(state.durableFrames)}"
                        else uiCopy("Ожидаем подтверждения сохранения"),
                        minLines = 2,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                } else {
                    Text(stringResource(R.string.signal_copy_12))
                }
            }
            when (state.phase) {
                RecordingPhase.PREFLIGHT,
                RecordingPhase.EMPTY,
                RecordingPhase.SAVED -> Unit // Rendered in the bounded preflight surface above.
                RecordingPhase.PREPARING ->
                    TextButton(onClick = controller::requestStop) {
                        Text(stringResource(R.string.signal_copy_2))
                    }
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
                                if (state.phase == RecordingPhase.PAUSED) uiCopy("Продолжить")
                                else uiCopy("Пауза")
                            )
                        }
                        OutlinedButton(
                            onClick = controller::requestStop,
                            modifier = Modifier.heightIn(min = DoraDimensions.recordControl),
                        ) {
                            Text(stringResource(R.string.signal_copy_13))
                        }
                    }
                RecordingPhase.INTERRUPTED -> Unit
                RecordingPhase.FINALIZING -> Text(stringResource(R.string.signal_copy_14))
            }
        }
    }
    if (state.stopConfirmation)
        AlertDialog(
            onDismissRequest = controller::cancelStop,
            title = { Text(stringResource(R.string.signal_copy_15)) },
            text = {
                Column {
                    Text(stringResource(R.string.signal_copy_16))
                    Text(
                        if (state.phase == RecordingPhase.PAUSED) uiCopy("Запись приостановлена")
                        else if (state.phase == RecordingPhase.RECORDING)
                            uiCopy("Запись продолжается")
                        else uiCopy("Подготовка записи")
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = controller::confirmStop) {
                    Text(stringResource(R.string.signal_copy_17))
                }
            },
            dismissButton = {
                TextButton(onClick = controller::cancelStop) {
                    Text(stringResource(R.string.signal_copy_18))
                }
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
                            contentDescription = uiCopy("Я предупредил(а) участников о записи")
                        },
                )
                Text(stringResource(R.string.signal_copy_5))
            }
            Text(
                uiCopy("Микрофон") +
                    ": ${if (microphone) uiCopy("доступ разрешён") else uiCopy("нужно разрешение")}"
            )
            Text(stringResource(R.string.signal_copy_6))
            val available = storageBudget.availableBytes
            Text(
                if (available == null) uiCopy("Не удалось проверить свободное место")
                else
                    uiCopy("Доступно на устройстве") +
                        ": ${available / BYTES_PER_MB} " +
                        uiCopy("МБ")
            )
            Text(
                uiCopy(
                    "Alpha: до 5 минут аудио. Для начала записи требуется не менее 142 МБ свободного места."
                )
            )
            Text(stringResource(R.string.signal_copy_7))
            if (!storageBudget.canStart) {
                Text(
                    uiCopy(
                        "Освободите место и обновите проверку. Сохранённые записи останутся доступны."
                    )
                )
            }
            TextButton(onClick = { storageBudget = controller.storageBudget() }) {
                Text(stringResource(R.string.signal_copy_8))
            }
            Text(stringResource(R.string.signal_copy_9))
            Text(
                uiCopy(
                    "DORA использует микрофон, чтобы записывать звук, пока вы не нажмёте «Пауза» "
                ) + uiCopy("или не завершите запись. Запись продолжится при выключенном экране.")
            )
            if (denied) {
                Text(uiCopy("Микрофон не разрешён. Запись не началась."))
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
                        Text(stringResource(R.string.signal_copy_10))
                    }
            }
            if (!notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text(
                    uiCopy(
                        "Уведомления отключены. Android может показывать запись только в списке активных приложений."
                    )
                )
                TextButton(
                    onClick = { notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS) }
                ) {
                    Text(stringResource(R.string.signal_copy_11))
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
            Text(stringResource(R.string.signal_copy_4))
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.heightIn(min = DoraDimensions.touchMinimum),
        ) {
            Text(stringResource(R.string.signal_copy_2))
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
    val color =
        if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(WAVE_SIZE_DP.dp).clearAndSetSemantics {}) {
        val step = size.width / levels.size
        levels.asReversed().forEachIndexed { index, amplitude ->
            val length =
                WAVE_STROKE_DP.dp.toPx() +
                    sqrt(amplitude.coerceIn(0f, 1f)) * size.height * WAVE_EXTENT
            val x = (index + BAR_CENTER_OFFSET) * step
            drawLine(
                color,
                Offset(x, center.y - length),
                Offset(x, center.y + length),
                minOf(WAVE_STROKE_DP.dp.toPx(), step / 2),
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
        AudioFailure.CANCELLED -> uiCopy("Действие прервано. Повторите в открытом приложении")
        AudioFailure.KEY_UNAVAILABLE -> uiCopy("Ключ хранилища временно недоступен")
        AudioFailure.KEY_INVALIDATED ->
            uiCopy("Ключ хранилища недоступен. Аудио сохранено в зашифрованном виде")
        AudioFailure.CORRUPT,
        AudioFailure.AUTHENTICATION_FAILED -> uiCopy("Проверка сохранённого аудио не пройдена")
        AudioFailure.BUSY -> uiCopy("Хранилище занято. Повторите после завершения операции")
        AudioFailure.UNCERTAIN,
        AudioFailure.INCOMPLETE -> uiCopy("Сохранение требует проверки восстановления")
        else -> uiCopy("Не удалось подтвердить сохранение аудио")
    }

private fun captureMessage(failure: CaptureFailure?): String =
    when (failure) {
        CaptureFailure.PERMISSION_DENIED -> uiCopy("Доступ к микрофону отозван")
        CaptureFailure.CONFIGURATION_UNAVAILABLE ->
            uiCopy("Микрофон не поддерживает необходимый формат 16 кГц, моно, PCM16")
        CaptureFailure.INITIALIZATION_FAILED -> uiCopy("Не удалось подготовить микрофон")
        CaptureFailure.START_FAILED,
        CaptureFailure.MICROPHONE_UNAVAILABLE -> uiCopy("Микрофон недоступен")
        CaptureFailure.DEAD_OBJECT -> uiCopy("Соединение с микрофоном потеряно")
        CaptureFailure.READ_ERROR -> uiCopy("Ошибка чтения микрофона")
        CaptureFailure.SERVICE_REJECTED ->
            uiCopy("Android не разрешил запуск записи. Откройте DORA и повторите")
        CaptureFailure.STORAGE_FULL -> uiCopy("Недостаточно свободного места")
        CaptureFailure.PERSISTENCE_BACKPRESSURE -> uiCopy("Хранилище не успевает сохранять аудио")
        CaptureFailure.THREAD_TIMEOUT -> uiCopy("Не удалось подтвердить остановку микрофона")
        else -> uiCopy("Запись остановлена. Необходимо проверить сохранённое аудио")
    }

private const val AUTH_REFRESH_MILLIS = 100L
private const val WAVE_BARS = 72
private const val WAVE_SIZE_DP = 120
private const val WAVE_STROKE_DP = 3
private const val BYTES_PER_MB = 1_000_000L
private const val SAMPLE_RATE = 16_000
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
private const val NANOS_PER_MILLI = 1_000_000L
private const val REDUCED_BARS = 12
private const val WAVE_PERIOD_MILLIS = 50L
private const val REDUCED_PERIOD_MILLIS = 250L
private const val WAVE_EXTENT = 0.42f

private const val BAR_CENTER_OFFSET = 0.5f
