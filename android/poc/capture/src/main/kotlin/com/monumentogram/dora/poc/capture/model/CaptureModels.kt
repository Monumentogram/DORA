@file:Suppress(
    "MagicNumber"
) // Run durations and PCM defaults are named Stage 0 evidence constants.

package com.monumentogram.dora.poc.capture.model

enum class RunKind(
    val id: String,
    val title: String,
    val targetSeconds: Int,
    val screenOffExpected: Boolean,
    val fixtureAllowed: Boolean,
) {
    RUN_A("run-a", "Run A — 3 минуты", 3 * 60, false, true),
    RUN_B("run-b", "Run B — 15 минут, экран выключен", 15 * 60, true, false),
    RUN_C("run-c", "Run C — 60 минут, экран выключен", 60 * 60, true, false);

    companion object {
        fun fromId(id: String?): RunKind? = entries.firstOrNull { it.id == id }
    }
}

enum class FlowPhase {
    DEVICE,
    RUN_SELECTION,
    PREFLIGHT,
    STARTING,
    RECORDING,
    REVIEW,
    QUESTIONNAIRE,
    READY_TO_EXPORT,
    RECOVERY,
    ERROR,
}

enum class ManualAnswer(val wireValue: String, val label: String) {
    YES("yes", "Да"),
    NO("no", "Нет"),
    UNKNOWN("unknown", "Не знаю"),
}

enum class ServiceState(val label: String) {
    STOPPED("Остановлен"),
    STARTING("Запускается"),
    RECORDING("Активен"),
    STOPPING("Останавливается"),
    ERROR("Ошибка"),
}

data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val androidApi: Int,
    val buildId: String,
    val securityPatch: String?,
    val primaryAbi: String,
    val supportedAbis: List<String>,
    val totalRamMb: Long,
    val freeStorageMb: Long,
    val batteryPercent: Double?,
    val chargingState: String,
    val thermalStatus: String?,
    val audioInputTypes: List<String>,
    val supportedSampleRates: List<Int>,
    val pageSizeBytes: Long,
    val candidateProfileId: String,
)

data class SystemSnapshot(
    val elapsedRealtimeMs: Long,
    val batteryPercent: Double?,
    val chargingState: String,
    val chargeCounterMicroAh: Long?,
    val currentNowMicroA: Long?,
    val energyCounterNanoWh: Long?,
    val thermalStatus: String?,
    val processPssMb: Double?,
    val processRssMb: Double?,
    val nativeHeapMb: Double?,
    val freeStorageMb: Long,
    val screenInteractive: Boolean,
)

data class AudioConfiguration(
    val sampleRate: Int,
    val channelCount: Int = 1,
    val bitsPerSample: Int = 16,
    val bufferBytes: Int,
    val fallbackUsed: Boolean,
)

data class AudioCounters(
    val samples: Long = 0,
    val bytes: Long = 0,
    val fileBytes: Long = 0,
    val shortReads: Long = 0,
    val errors: Map<String, Int> = emptyMap(),
    val route: String = "Не определён",
    val audioTimestampFrames: Long? = null,
    val audioTimestampNanos: Long? = null,
)

data class LiveMetrics(
    val run: RunKind,
    val runId: String,
    val elapsedMs: Long = 0,
    val counters: AudioCounters = AudioCounters(),
    val screenOnMs: Long = 0,
    val screenOffMs: Long = 0,
    val batteryPercent: Double? = null,
    val chargingState: String = "unknown",
    val thermalStatus: String? = null,
    val peakPssMb: Double? = null,
    val routeChanges: Int = 0,
    val serviceState: ServiceState = ServiceState.STARTING,
)

data class WavAnalysis(
    val valid: Boolean,
    val reason: String,
    val sampleRate: Int?,
    val channelCount: Int?,
    val bitsPerSample: Int?,
    val dataBytes: Long,
    val sha256: String?,
)

data class CaptureOutcome(
    val run: RunKind,
    val runId: String,
    val startedAtUtc: String,
    val finishedAtUtc: String,
    val actualDurationMs: Long,
    val configuration: AudioConfiguration,
    val counters: AudioCounters,
    val screenOnMs: Long,
    val screenOffMs: Long,
    val startSnapshot: SystemSnapshot,
    val endSnapshot: SystemSnapshot,
    val maxThermalStatus: String?,
    val peakPssMb: Double?,
    val peakRssMb: Double?,
    val peakNativeHeapMb: Double?,
    val startLatencyMs: Long,
    val finalizationLatencyMs: Long,
    val routeChanges: Int,
    val interruptionCount: Int,
    val fixtureUsed: Boolean,
    val privateFileName: String,
    val wavAnalysis: WavAnalysis,
) {
    val expectedSamples: Long
        get() = configuration.sampleRate.toLong() * actualDurationMs / 1_000L

    val sampleDelta: Long
        get() = counters.samples - expectedSamples

    val hasCriticalCaptureFailure: Boolean
        get() =
            !wavAnalysis.valid ||
                counters.samples == 0L ||
                counters.errors.isNotEmpty() ||
                wavAnalysis.dataBytes != counters.bytes
}

data class DeletionReceipt(
    val runId: String,
    val verifiedAtUtc: String,
    val wavWasValid: Boolean,
    val sha256: String?,
    val bytesBeforeDeletion: Long,
    val deletionSucceeded: Boolean,
    val absenceVerified: Boolean,
    val failureReason: String?,
)

data class ManualObservations(
    val notificationVisible: ManualAnswer? = null,
    val screenMostlyOff: ManualAnswer? = null,
    val callOrInterruption: ManualAnswer? = null,
    val phoneCharging: ManualAnswer? = null,
    val overheatingOrUnexpectedStop: ManualAnswer? = null,
) {
    val complete: Boolean
        get() =
            notificationVisible != null &&
                screenMostlyOff != null &&
                callOrInterruption != null &&
                phoneCharging != null &&
                overheatingOrUnexpectedStop != null

    val reportsCriticalFailure: Boolean
        get() =
            notificationVisible == ManualAnswer.NO ||
                overheatingOrUnexpectedStop == ManualAnswer.YES
}

data class RecoveryCandidate(
    val fileName: String,
    val bytes: Long,
    val finalized: Boolean,
)

data class SanitizedEvent(
    val elapsedMs: Long,
    val type: String,
    val detail: String,
)

data class CaptureUiState(
    val phase: FlowPhase = FlowPhase.DEVICE,
    val preparedProfile: DeviceProfile? = null,
    val preparationMessage: String? = null,
    val selectedRun: RunKind? = null,
    val acknowledgementChecked: Boolean = false,
    val fixtureEnabled: Boolean = false,
    val completedRuns: Set<RunKind> = emptySet(),
    val criticalRuns: Set<RunKind> = emptySet(),
    val liveMetrics: LiveMetrics? = null,
    val outcome: CaptureOutcome? = null,
    val deletionReceipt: DeletionReceipt? = null,
    val observations: ManualObservations = ManualObservations(),
    val recoveryCandidate: RecoveryCandidate? = null,
    val exportMessage: String? = null,
    val errorMessage: String? = null,
) {
    fun isUnlocked(run: RunKind): Boolean =
        when (run) {
            RunKind.RUN_A -> true
            RunKind.RUN_B -> RunKind.RUN_A in completedRuns && RunKind.RUN_A !in criticalRuns
            RunKind.RUN_C -> RunKind.RUN_B in completedRuns && RunKind.RUN_B !in criticalRuns
        }
}
