@file:Suppress("LongMethod", "MagicNumber", "TooGenericExceptionCaught", "TooManyFunctions")

package com.monumentogram.dora.poc.capture.runtime

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.monumentogram.dora.poc.capture.audio.AudioCaptureEngine
import com.monumentogram.dora.poc.capture.audio.AudioStartResult
import com.monumentogram.dora.poc.capture.audio.FixturePlayer
import com.monumentogram.dora.poc.capture.audio.WavAnalyzer
import com.monumentogram.dora.poc.capture.device.DeviceInspector
import com.monumentogram.dora.poc.capture.model.CaptureOutcome
import com.monumentogram.dora.poc.capture.model.CaptureUiState
import com.monumentogram.dora.poc.capture.model.DeletionReceipt
import com.monumentogram.dora.poc.capture.model.FlowPhase
import com.monumentogram.dora.poc.capture.model.LiveMetrics
import com.monumentogram.dora.poc.capture.model.ManualObservations
import com.monumentogram.dora.poc.capture.model.RunKind
import com.monumentogram.dora.poc.capture.model.SanitizedEvent
import com.monumentogram.dora.poc.capture.model.ServiceState
import com.monumentogram.dora.poc.capture.model.SystemSnapshot
import com.monumentogram.dora.poc.capture.report.ExportEntry
import com.monumentogram.dora.poc.capture.report.SafeExportArchive
import com.monumentogram.dora.poc.capture.report.SafeExportPolicy
import com.monumentogram.dora.poc.capture.report.SanitizedReportBuilder
import com.monumentogram.dora.poc.capture.service.CaptureService
import com.monumentogram.dora.poc.capture.state.CaptureStateMachine
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CaptureController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = CaptureStore(appContext)
    private val inspector = DeviceInspector(appContext)
    private val exporter = SafeExportArchive(appContext)
    private val engine = AudioCaptureEngine()
    private val fixturePlayer = FixturePlayer()
    private val initialized = AtomicBoolean(false)
    private val stateMachine =
        CaptureStateMachine(
            CaptureUiState(
                completedRuns = store.completedRuns(),
                criticalRuns = store.criticalRuns(),
            )
        )
    private val _uiState = MutableStateFlow(stateMachine.state)
    private val _shareRequests = MutableSharedFlow<Intent>(extraBufferCapacity = 2)
    private var activeSession: ActiveSession? = null
    private var monitorJob: Job? = null
    private var runEvents: MutableList<SanitizedEvent> = mutableListOf()

    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()
    val shareRequests: SharedFlow<Intent> = _shareRequests.asSharedFlow()

    fun initialize() {
        if (!initialized.compareAndSet(false, true)) return
        store.recoveryCandidate()?.let { publish(stateMachine.showRecovery(it)) }
    }

    fun prepareDevice() {
        if (stateMachine.state.phase == FlowPhase.RECOVERY) return
        scope.launch {
            runCatching { withContext(Dispatchers.Default) { inspector.inspect() } }
                .onSuccess { profile ->
                    val failures = buildList {
                        if (profile.supportedSampleRates.isEmpty()) {
                            add("mono PCM16 configuration не найдена")
                        }
                        if (profile.freeStorageMb < MINIMUM_FREE_STORAGE_MB) {
                            add("свободно меньше $MINIMUM_FREE_STORAGE_MB MiB")
                        }
                    }
                    if (failures.isEmpty()) {
                        publish(
                            stateMachine.prepared(
                                profile,
                                "Устройство готово: профиль безопасно собран, " +
                                    "место и микрофонная конфигурация доступны.",
                            )
                        )
                    } else {
                        publish(
                            stateMachine.fail("Подготовка не завершена: ${failures.joinToString()}")
                        )
                    }
                }
                .onFailure { error ->
                    publish(
                        stateMachine.fail("Не удалось подготовить устройство: ${safeError(error)}")
                    )
                }
        }
    }

    fun selectRun(run: RunKind) {
        transition { stateMachine.selectRun(run) }
        if (stateMachine.state.phase == FlowPhase.PREFLIGHT) {
            scope.launch {
                runCatching { withContext(Dispatchers.Default) { inspector.inspect() } }
                    .onSuccess { publish(stateMachine.refreshProfile(it)) }
            }
        }
    }

    fun setAcknowledgement(checked: Boolean) = transition {
        stateMachine.setAcknowledgement(checked)
    }

    fun setFixtureEnabled(enabled: Boolean) = transition { stateMachine.setFixtureEnabled(enabled) }

    fun requestStart(): Boolean =
        try {
            publish(stateMachine.requestStart())
            true
        } catch (error: IllegalStateException) {
            publish(stateMachine.fail(safeError(error)))
            false
        }

    fun startAfterPermissionResult(granted: Boolean) {
        if (stateMachine.state.phase != FlowPhase.STARTING) return
        if (!granted) {
            publish(
                stateMachine.fail(
                    "Запись не началась. Для этого технического теста нужны разрешения на микрофон и уведомление."
                )
            )
            return
        }
        val run = requireNotNull(stateMachine.state.selectedRun)
        val runId = newRunId(run)
        runEvents =
            mutableListOf(SanitizedEvent(0L, "start_requested", "explicit_visible_activity_action"))
        val intent =
            CaptureService.startIntent(
                appContext,
                run = run,
                runId = runId,
                fixtureEnabled = stateMachine.state.fixtureEnabled,
            )
        runCatching { ContextCompat.startForegroundService(appContext, intent) }
            .onFailure {
                publish(stateMachine.fail("Foreground service не запущен: ${safeError(it)}"))
            }
    }

    suspend fun beginServiceCapture(run: RunKind, runId: String, fixtureEnabled: Boolean) {
        check(stateMachine.state.phase == FlowPhase.STARTING) { "Unexpected service start state" }
        check(stateMachine.state.selectedRun == run) { "Run mismatch" }
        val startSnapshot = withContext(Dispatchers.Default) { inspector.snapshot() }
        val partFile = store.captureFile("capture-${run.id}-$runId.wav.part")
        try {
            val audioStart = withContext(Dispatchers.IO) { engine.start(partFile) }
            if (fixtureEnabled) withContext(Dispatchers.Default) { fixturePlayer.start() }
            val session =
                ActiveSession(
                    run = run,
                    runId = runId,
                    fixtureEnabled = fixtureEnabled,
                    startedAtUtc = nowUtc(),
                    startSnapshot = startSnapshot,
                    lastSnapshot = startSnapshot,
                    audioStart = audioStart,
                    maxThermalStatus = startSnapshot.thermalStatus,
                    peakPssMb = startSnapshot.processPssMb,
                    peakRssMb = startSnapshot.processRssMb,
                    peakNativeHeapMb = startSnapshot.nativeHeapMb,
                    lastRoute = engine.currentCounters().route,
                    lastThermalStatus = startSnapshot.thermalStatus,
                )
            activeSession = session
            addEvent(session, "recording_started", "microphone_foreground_service_active")
            val live = liveMetrics(session, startSnapshot)
            publish(stateMachine.recording(live))
            startMonitor()
        } catch (error: Throwable) {
            fixturePlayer.stop()
            if (engine.isRecording()) {
                withContext(Dispatchers.IO) {
                    runCatching { engine.stop() }.getOrNull()?.finalizedFile?.delete()
                }
            }
            withContext(Dispatchers.IO) { runCatching { if (partFile.exists()) partFile.delete() } }
            activeSession = null
            publish(stateMachine.fail("AudioRecord не запущен: ${safeError(error)}"))
            throw error
        }
    }

    fun requestStop() = requestServiceStop(abort = false)

    fun abortAndDelete() = requestServiceStop(abort = true)

    suspend fun finishServiceCapture(abort: Boolean) {
        val session = activeSession ?: return
        if (session.finishing) return
        session.finishing = true
        monitorJob?.cancel()
        monitorJob = null
        addEvent(
            session,
            if (abort) "abort_requested" else "stop_requested",
            "explicit_user_action",
        )
        val stopResult = withContext(Dispatchers.IO) { engine.stop() }
        withContext(Dispatchers.Default) { fixturePlayer.stop() }
        val endSnapshot = withContext(Dispatchers.Default) { inspector.snapshot() }
        accountScreenInterval(session, endSnapshot)
        val analysis = withContext(Dispatchers.IO) { WavAnalyzer.analyze(stopResult.finalizedFile) }
        val counters =
            if (stopResult.failure == null) {
                stopResult.counters
            } else {
                stopResult.counters.copy(
                    errors = stopResult.counters.errors + ("AUDIO_STOP_FAILURE" to 1)
                )
            }
        val outcome =
            CaptureOutcome(
                run = session.run,
                runId = session.runId,
                startedAtUtc = session.startedAtUtc,
                finishedAtUtc = nowUtc(),
                actualDurationMs =
                    (endSnapshot.elapsedRealtimeMs - session.audioStart.startElapsedMs)
                        .coerceAtLeast(0L),
                configuration = session.audioStart.configuration,
                counters = counters,
                screenOnMs = session.screenOnMs,
                screenOffMs = session.screenOffMs,
                startSnapshot = session.startSnapshot,
                endSnapshot = endSnapshot,
                maxThermalStatus = session.maxThermalStatus,
                peakPssMb = session.peakPssMb,
                peakRssMb = session.peakRssMb,
                peakNativeHeapMb = session.peakNativeHeapMb,
                startLatencyMs = session.audioStart.startLatencyMs,
                finalizationLatencyMs = stopResult.finalizationLatencyMs,
                routeChanges = session.routeChanges,
                interruptionCount =
                    counters.errors.filterKeys { it.contains("DEAD_OBJECT") }.values.sum(),
                fixtureUsed = session.fixtureEnabled,
                privateFileName = stopResult.finalizedFile.name,
                wavAnalysis = analysis,
            )
        addEvent(session, "wav_finalized", if (analysis.valid) "valid" else "invalid")
        activeSession = null
        publish(stateMachine.finished(outcome))
        if (abort) {
            val receipt =
                withContext(Dispatchers.IO) {
                    WavAnalyzer.analyzeAndDelete(stopResult.finalizedFile, outcome.runId, nowUtc())
                }
            if (receipt.deletionSucceeded && receipt.absenceVerified) {
                publish(
                    stateMachine.aborted(
                        "Тест прерван; app-private аудио удалено и отсутствие проверено."
                    )
                )
            } else {
                publish(stateMachine.fail("Тест прерван, но удалить app-private аудио не удалось."))
            }
        }
    }

    fun analyzeAndDelete() {
        val outcome = stateMachine.state.outcome ?: return
        if (stateMachine.state.phase != FlowPhase.REVIEW) return
        scope.launch {
            val file = store.captureFile(outcome.privateFileName)
            val receipt =
                withContext(Dispatchers.IO) {
                    WavAnalyzer.analyzeAndDelete(file, outcome.runId, nowUtc())
                }
            runEvents.add(
                SanitizedEvent(
                    outcome.actualDurationMs,
                    "raw_audio_deletion",
                    if (receipt.absenceVerified) "verified_absent" else "failed",
                )
            )
            publish(stateMachine.deleted(receipt))
        }
    }

    fun discardStoppedRun() {
        val outcome = stateMachine.state.outcome ?: return
        if (stateMachine.state.phase != FlowPhase.REVIEW) return
        scope.launch {
            val receipt =
                withContext(Dispatchers.IO) {
                    WavAnalyzer.analyzeAndDelete(
                        store.captureFile(outcome.privateFileName),
                        outcome.runId,
                        nowUtc(),
                    )
                }
            if (receipt.deletionSucceeded && receipt.absenceVerified) {
                publish(stateMachine.aborted("Результат отброшен; app-private аудио удалено."))
            } else {
                publish(stateMachine.fail("Результат отброшен, но raw audio не удалён."))
            }
        }
    }

    fun submitObservations(observations: ManualObservations) {
        transition {
            val next = stateMachine.observations(observations)
            store.saveRunProgress(next.completedRuns, next.criticalRuns)
            next
        }
    }

    fun exportRun() {
        val state = stateMachine.state
        if (state.phase != FlowPhase.READY_TO_EXPORT) return
        val profile = checkNotNull(state.preparedProfile)
        val outcome = checkNotNull(state.outcome)
        val receipt = checkNotNull(state.deletionReceipt)
        scope.launch {
            runCatching {
                    withContext(Dispatchers.IO) {
                        exporter.createRunArchive(
                            profile,
                            outcome,
                            receipt,
                            state.observations,
                            runEvents.toList(),
                        )
                    }
                }
                .onSuccess { file ->
                    publish(stateMachine.markExported("Безопасный ZIP создан: ${file.name}"))
                    _shareRequests.emit(exporter.shareIntent(file, "application/zip"))
                }
                .onFailure { publish(stateMachine.fail("Экспорт заблокирован: ${safeError(it)}")) }
        }
    }

    fun exportDeviceProfile() {
        val profile = stateMachine.state.preparedProfile ?: return
        scope.launch {
            runCatching {
                    withContext(Dispatchers.IO) { exporter.createDeviceProfile(profile, nowUtc()) }
                }
                .onSuccess { file ->
                    _shareRequests.emit(exporter.shareIntent(file, "application/json"))
                }
                .onFailure {
                    publish(stateMachine.fail("Профиль не экспортирован: ${safeError(it)}"))
                }
        }
    }

    fun resolveRecovery(analyzeFirst: Boolean) {
        val candidate = stateMachine.state.recoveryCandidate ?: return
        scope.launch {
            val file = store.captureFile(candidate.fileName)
            val runId = "recovery-${utcFileTimestamp()}"
            val receipt =
                withContext(Dispatchers.IO) {
                    val analysis = if (analyzeFirst) WavAnalyzer.analyze(file) else null
                    val bytes = file.length().coerceAtLeast(0L)
                    val deleted = runCatching { file.delete() }.getOrDefault(false)
                    val absent = !file.exists()
                    DeletionReceipt(
                        runId = runId,
                        verifiedAtUtc = nowUtc(),
                        wavWasValid = analysis?.valid ?: false,
                        sha256 = analysis?.sha256,
                        bytesBeforeDeletion = bytes,
                        deletionSucceeded = deleted && absent,
                        absenceVerified = absent,
                        failureReason =
                            if (absent) null else "Незавершённый файл остался в storage",
                    )
                }
            val receiptText = SanitizedReportBuilder.deletionReceiptJson(receipt)
            val safeEntry =
                ExportEntry(
                    SafeExportArchive.DELETION_RECEIPT,
                    receiptText.toByteArray(Charsets.UTF_8),
                )
            runCatching { SafeExportPolicy.validate(listOf(safeEntry)) }
                .onSuccess {
                    val exportDirectory = File(appContext.cacheDir, "exports")
                    exportDirectory.mkdirs()
                    val receiptFile =
                        File(exportDirectory, "dora-capture-poc-recovery-receipt.json")
                    withContext(Dispatchers.IO) { receiptFile.writeBytes(safeEntry.bytes) }
                    if (receipt.absenceVerified) {
                        val nextCandidate = store.recoveryCandidate()
                        if (nextCandidate == null) {
                            publish(
                                stateMachine.recoveryResolved(
                                    "Незавершённые данные удалены; sanitized failure receipt создан."
                                )
                            )
                        } else {
                            publish(stateMachine.showRecovery(nextCandidate))
                        }
                    } else {
                        publish(stateMachine.fail("Незавершённый файл не удалён."))
                    }
                    _shareRequests.emit(exporter.shareIntent(receiptFile, "application/json"))
                }
                .onFailure {
                    publish(stateMachine.fail("Failure receipt не создан: ${safeError(it)}"))
                }
        }
    }

    fun backToRuns() = transition { stateMachine.backToRuns() }

    fun retryRecovery() {
        stateMachine.state.recoveryCandidate?.let { publish(stateMachine.showRecovery(it)) }
    }

    fun serviceFailure(message: String) {
        fixturePlayer.stop()
        monitorJob?.cancel()
        monitorJob = null
        activeSession = null
        publish(stateMachine.fail(message))
        if (engine.isRecording()) stopUnexpectedCapture()
    }

    fun unexpectedServiceDestroyed() {
        if (engine.isRecording()) stopUnexpectedCapture()
    }

    private fun startMonitor() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                delay(MONITOR_INTERVAL_MS)
                val session = activeSession ?: break
                val snapshot = withContext(Dispatchers.Default) { inspector.snapshot() }
                accountScreenInterval(session, snapshot)
                session.maxThermalStatus =
                    DeviceInspector.maxThermalStatus(
                        session.maxThermalStatus,
                        snapshot.thermalStatus,
                    )
                if (
                    snapshot.thermalStatus != null &&
                        snapshot.thermalStatus != session.lastThermalStatus
                ) {
                    addEvent(session, "thermal_status_changed", snapshot.thermalStatus)
                }
                session.lastThermalStatus = snapshot.thermalStatus
                session.peakPssMb = maxNullable(session.peakPssMb, snapshot.processPssMb)
                session.peakRssMb = maxNullable(session.peakRssMb, snapshot.processRssMb)
                session.peakNativeHeapMb =
                    maxNullable(session.peakNativeHeapMb, snapshot.nativeHeapMb)
                val counters = engine.currentCounters()
                if (
                    session.lastRoute != "Не определён" &&
                        counters.route != "Не определён" &&
                        session.lastRoute != counters.route
                ) {
                    session.routeChanges += 1
                    addEvent(session, "audio_route_changed", counters.route)
                }
                session.lastRoute = counters.route
                if (stateMachine.state.phase == FlowPhase.RECORDING) {
                    publish(stateMachine.updateMetrics(liveMetrics(session, snapshot, counters)))
                }
                if (!engine.isRecording() && !session.stopRequested) {
                    session.stopRequested = true
                    requestServiceStop(abort = false)
                }
            }
        }
    }

    private fun stopUnexpectedCapture() {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { engine.stop() } }
            fixturePlayer.stop()
            activeSession = null
            store.recoveryCandidate()?.let { publish(stateMachine.showRecovery(it)) }
        }
    }

    private fun liveMetrics(
        session: ActiveSession,
        snapshot: SystemSnapshot,
        counters: com.monumentogram.dora.poc.capture.model.AudioCounters = engine.currentCounters(),
    ): LiveMetrics =
        LiveMetrics(
            run = session.run,
            runId = session.runId,
            elapsedMs =
                (snapshot.elapsedRealtimeMs - session.audioStart.startElapsedMs).coerceAtLeast(0L),
            counters = counters,
            screenOnMs = session.screenOnMs,
            screenOffMs = session.screenOffMs,
            batteryPercent = snapshot.batteryPercent,
            chargingState = snapshot.chargingState,
            thermalStatus = snapshot.thermalStatus,
            peakPssMb = session.peakPssMb,
            routeChanges = session.routeChanges,
            serviceState = ServiceState.RECORDING,
        )

    private fun accountScreenInterval(session: ActiveSession, snapshot: SystemSnapshot) {
        val interval =
            (snapshot.elapsedRealtimeMs - session.lastSnapshot.elapsedRealtimeMs).coerceAtLeast(0L)
        if (session.lastSnapshot.screenInteractive) session.screenOnMs += interval
        else session.screenOffMs += interval
        session.lastSnapshot = snapshot
    }

    private fun requestServiceStop(abort: Boolean) {
        val phase = stateMachine.state.phase
        if (phase != FlowPhase.RECORDING && phase != FlowPhase.STARTING) return
        activeSession?.stopRequested = true
        val intent = CaptureService.stopIntent(appContext, abort)
        runCatching { appContext.startService(intent) }
            .onFailure { publish(stateMachine.fail("Stop не передан сервису: ${safeError(it)}")) }
    }

    private fun addEvent(session: ActiveSession, type: String, detail: String) {
        val elapsed =
            (SystemClock.elapsedRealtime() - session.audioStart.startElapsedMs).coerceAtLeast(0L)
        runEvents.add(SanitizedEvent(elapsed, type, detail.take(200)))
    }

    private fun transition(block: () -> CaptureUiState) {
        runCatching(block).onSuccess(::publish).onFailure {
            publish(stateMachine.fail(safeError(it)))
        }
    }

    private fun publish(value: CaptureUiState) {
        _uiState.value = value
    }

    private fun newRunId(run: RunKind): String =
        "${run.id}-${utcFileTimestamp()}-${UUID.randomUUID().toString().take(8)}"

    private fun utcFileTimestamp(): String = FILE_TIME_FORMAT.format(Instant.now())

    private fun nowUtc(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

    private fun safeError(error: Throwable): String =
        error.message?.replace(Regex("[\\r\\n\\t]+"), " ")?.take(240) ?: error.javaClass.simpleName

    private fun maxNullable(first: Double?, second: Double?): Double? =
        when {
            first == null -> second
            second == null -> first
            else -> maxOf(first, second)
        }

    private data class ActiveSession(
        val run: RunKind,
        val runId: String,
        val fixtureEnabled: Boolean,
        val startedAtUtc: String,
        val startSnapshot: SystemSnapshot,
        var lastSnapshot: SystemSnapshot,
        val audioStart: AudioStartResult,
        var maxThermalStatus: String?,
        var peakPssMb: Double?,
        var peakRssMb: Double?,
        var peakNativeHeapMb: Double?,
        var lastRoute: String,
        var lastThermalStatus: String?,
        var routeChanges: Int = 0,
        var screenOnMs: Long = 0,
        var screenOffMs: Long = 0,
        var stopRequested: Boolean = false,
        var finishing: Boolean = false,
    )

    companion object {
        private const val MINIMUM_FREE_STORAGE_MB = 300L
        private const val MONITOR_INTERVAL_MS = 1_000L
        private val FILE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US).withZone(ZoneOffset.UTC)
    }
}
