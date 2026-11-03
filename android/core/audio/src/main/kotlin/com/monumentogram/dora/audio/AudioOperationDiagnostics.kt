package com.monumentogram.dora.audio

import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap

enum class AudioOperation {
    FINALIZE,
    AUTHENTICATED_OPEN,
    RANGE_READ,
    PLAYBACK_PREPARATION,
}

enum class AudioStage {
    KEY_OPEN,
    KEY_INFO,
    KEY_PROBE,
    KEY_DECRYPT,
    MANIFEST_AUTH,
    PCM_AUTH,
    RECOVERY_UNIT,
    CATALOG_COMMIT,
    AUDIO_TRACK,
    WINDOW_READ,
}

enum class AudioOperationStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

internal data class AudioStageTiming(val nanos: Long, val count: Long)

internal data class AudioOperationSnapshot(
    val operation: AudioOperation,
    val status: AudioOperationStatus,
    val elapsedNanos: Long,
    val completedUnits: Long,
    val totalUnits: Long,
    val stages: Map<AudioStage, AudioStageTiming>,
)

/** No content-bearing parameters; a measurement retains enum/scalar fields only. */
class AudioDiagnosticMeasurement
internal constructor(
    private val operation: AudioOperation,
    private val now: () -> Long,
) {
    private val started = now()
    private var elapsed = 0L
    private var status = AudioOperationStatus.RUNNING
    private var completed = 0L
    private var total = 0L
    private val stages = linkedMapOf<AudioStage, AudioStageTiming>()

    @Synchronized
    fun finish(outcome: AudioOperationStatus) {
        require(outcome != AudioOperationStatus.RUNNING)
        if (status == AudioOperationStatus.RUNNING) {
            elapsed = (now() - started).coerceAtLeast(0)
            status = outcome
        }
    }

    @Synchronized
    internal fun progress(done: Long, count: Long) {
        require(done in 0..count)
        if (status == AudioOperationStatus.RUNNING) {
            completed = done
            total = count
        }
    }

    fun <T> measure(stage: AudioStage, block: () -> T): T {
        val begin = now()
        try {
            return block()
        } finally {
            synchronized(this) {
                val previous = stages[stage] ?: AudioStageTiming(0, 0)
                stages[stage] =
                    AudioStageTiming(
                        previous.nanos + (now() - begin).coerceAtLeast(0),
                        previous.count + 1,
                    )
            }
        }
    }

    @Synchronized
    internal fun snapshot() =
        AudioOperationSnapshot(
            operation,
            status,
            if (status == AudioOperationStatus.RUNNING) (now() - started).coerceAtLeast(0)
            else elapsed,
            completed,
            total,
            stages.toMap(),
        )
}

internal class AudioDiagnosticRecorder(private val now: () -> Long) {
    @Volatile var enabled = false
    private val current = ThreadLocal<AudioDiagnosticMeasurement>()
    private val latest = ConcurrentHashMap<AudioOperation, AudioDiagnosticMeasurement>()

    @Synchronized
    fun begin(operation: AudioOperation): AudioDiagnosticMeasurement? =
        if (enabled) AudioDiagnosticMeasurement(operation, now).also { latest[operation] = it }
        else null

    @Suppress("TooGenericExceptionCaught") // Retain a fixed failure enum, never an exception.
    fun <T> collect(operation: AudioOperation, block: () -> T): T {
        val measurement = begin(operation) ?: return block()
        val previous = current.get()
        current.set(measurement)
        try {
            val result = block()
            measurement.finish(
                if (result is AudioResult.Failed) AudioOperationStatus.FAILED
                else AudioOperationStatus.SUCCEEDED
            )
            return result
        } catch (error: CancellationException) {
            measurement.finish(AudioOperationStatus.CANCELLED)
            throw error
        } catch (error: Throwable) {
            measurement.finish(AudioOperationStatus.FAILED)
            throw error
        } finally {
            if (previous == null) current.remove() else current.set(previous)
        }
    }

    fun <T> measure(stage: AudioStage, block: () -> T): T {
        val measurement = current.get() ?: return block()
        return measurement.measure(stage, block)
    }

    fun progress(completed: Long, total: Long) {
        current.get()?.progress(completed, total)
    }

    fun snapshots(): List<AudioOperationSnapshot> =
        AudioOperation.entries.mapNotNull { latest[it]?.snapshot() }

    fun render(): String =
        snapshots().joinToString("\n") { snapshot ->
            "audio_operation=${snapshot.operation} status=${snapshot.status}" +
                " elapsedNanos=${snapshot.elapsedNanos}" +
                " completedUnits=${snapshot.completedUnits} totalUnits=${snapshot.totalUnits}" +
                snapshot.stages.entries.joinToString("") { (stage, timing) ->
                    " ${stage}_nanos=${timing.nanos} ${stage}_count=${timing.count}"
                }
        }

    @Synchronized
    fun disableAndClear() {
        enabled = false
        latest.clear()
    }
}

/** Opt-in fixed-size diagnostics. Nested stage durations overlap and must not be summed. */
object AudioOperationDiagnostics {
    private val recorder = AudioDiagnosticRecorder(System::nanoTime)

    fun enable() {
        recorder.enabled = true
    }

    fun disableAndClear() = recorder.disableAndClear()

    fun summary(): String = recorder.render()

    fun begin(operation: AudioOperation): AudioDiagnosticMeasurement? = recorder.begin(operation)

    internal fun <T> collect(operation: AudioOperation, block: () -> T): T =
        recorder.collect(operation, block)

    internal fun <T> measure(stage: AudioStage, block: () -> T): T = recorder.measure(stage, block)

    internal fun progress(completed: Long, total: Long) = recorder.progress(completed, total)
}
