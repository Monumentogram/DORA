@file:Suppress(
    "ThrowsCount",
    "LongMethod",
    "CyclomaticComplexMethod",
    "TooGenericExceptionCaught",
    "TooManyFunctions",
) // Native acquisition maps failures and releases on every exceptional exit.

package com.monumentogram.dora.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioRecord
import android.os.Process
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

enum class CaptureFailure {
    PERMISSION_DENIED,
    CONFIGURATION_UNAVAILABLE,
    INITIALIZATION_FAILED,
    START_FAILED,
    MICROPHONE_UNAVAILABLE,
    READ_ERROR,
    DEAD_OBJECT,
    SERVICE_REJECTED,
    STORAGE_FULL,
    PERSISTENCE_BACKPRESSURE,
    SERVICE_DESTROYED,
    THREAD_TIMEOUT,
}

internal class CaptureException(val failure: CaptureFailure) : IllegalStateException(failure.name)

internal data class MicrophoneSignal(val level: Float = 0f, val atNanos: Long = 0)

/** Audio thread does only bounded reading/amplitude/queueing. No disk, Compose or logging. */
internal class AudioRecordCapture(
    private val create: () -> NativeMicrophone,
    private val permitted: () -> Boolean,
    private val prioritize: () -> Unit,
) {
    constructor(
        context: Context
    ) : this(
        AndroidMicrophone::create,
        {
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        },
        { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) },
    )

    @Volatile private var stopTiming: (String) -> Unit = {}
    private val running = AtomicBoolean(false)
    private val queue = BoundedPcmQueue(QUEUE_CAPACITY)
    private val admission = CaptureAdmission(queue)
    private val nativeOwnership = Any()
    private val stopRequests = Any()
    private var stopRequestedHandle: NativeMicrophone? = null
    private val nativeControl = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Dora microphone control").apply { isDaemon = true }
    }
    @Volatile private var recorder: NativeMicrophone? = null
    @Volatile private var thread: Thread? = null
    val maximumQueuedBlocks: Int
        get() = queue.highWater

    val queuedBlocks: Int
        get() = queue.size

    val admittedFrames: Long
        get() = admission.snapshot().frames

    @Volatile
    var signal = MicrophoneSignal()
        private set

    @Volatile
    var failure: CaptureFailure? = null
        private set

    @Volatile
    var route = "Маршрут определяется"
        private set

    @Volatile
    var shortReads = 0L
        private set

    @Volatile
    var readErrors = 0L
        private set

    val healthy: Boolean
        get() = running.get() && thread?.isAlive == true

    fun start(
        withStartAuthority: (() -> Unit) -> Unit,
        physicalId: String = "",
        timing: (String) -> Unit = {},
    ) {
        check(recorder == null && thread?.isAlive != true)
        failure = null
        stopTiming = {}
        val generation = admission.begin(physicalId)
        if (!permitted()) throw CaptureException(CaptureFailure.PERMISSION_DENIED)
        val created =
            try {
                timing("construct_start")
                create()
            } catch (error: CaptureException) {
                throw error
            } catch (_: SecurityException) {
                throw CaptureException(CaptureFailure.PERMISSION_DENIED)
            } catch (_: Exception) {
                throw CaptureException(CaptureFailure.INITIALIZATION_FAILED)
            }
        try {
            timing("construct_end")
            created.requireConfiguration()
            withStartAuthority {
                try {
                    timing("native_start_begin")
                    created.startRecording()
                    timing("native_start_end")
                } catch (_: SecurityException) {
                    throw CaptureException(CaptureFailure.PERMISSION_DENIED)
                } catch (_: Exception) {
                    throw CaptureException(CaptureFailure.START_FAILED)
                }
                if (!created.recording) throw CaptureException(CaptureFailure.START_FAILED)
                timing("native_recording")
                // Publish ownership under the same authority monitor as native start.
                // Revocation can now always find and stop the reader it revoked.
                failure = null
                signal = MicrophoneSignal()
                recorder = created
                running.set(admission.open(generation))
                thread =
                    Thread({ readLoop(created, generation, timing) }, "Dora microphone").also {
                        it.start()
                    }
            }
        } catch (error: Exception) {
            runCatching { created.stop() }
            created.release()
            throw error
        }
    }

    @Suppress(
        "LoopWithTooManyJumpStatements"
    ) // Both native stop and admission fence end this reader.
    private fun readLoop(record: NativeMicrophone, generation: Long, timing: (String) -> Unit) {
        val bytes = ByteArray(READ_BYTES)
        var lastSignal = 0L
        var firstPcm = true
        try {
            prioritize()
            while (running.get()) {
                if (!permitted()) throw CaptureException(CaptureFailure.PERMISSION_DENIED)
                val count = record.read(bytes)
                if (!running.get()) break
                if (count <= 0 || count % 2 != 0) {
                    readErrors++
                    throw CaptureException(
                        if (count == AudioRecord.ERROR_DEAD_OBJECT) CaptureFailure.DEAD_OBJECT
                        else CaptureFailure.READ_ERROR
                    )
                }
                if (count < bytes.size) shortReads++
                val copied = bytes.copyOf(count)
                when (admission.offer(generation, copied)) {
                    CaptureAdmission.Result.ACCEPTED -> Unit
                    CaptureAdmission.Result.FENCED -> {
                        copied.fill(0)
                        break
                    }
                    CaptureAdmission.Result.FULL -> {
                        copied.fill(0)
                        throw CaptureException(CaptureFailure.PERSISTENCE_BACKPRESSURE)
                    }
                }
                if (firstPcm) {
                    timing("first_pcm")
                    firstPcm = false
                }
                val now = System.nanoTime()
                if (now - lastSignal >= SIGNAL_PERIOD_NANOS) {
                    signal = MicrophoneSignal(level(bytes, count), now)
                    lastSignal = now
                    route = routeLabel(record.routeType)
                }
                bytes.fill(0)
            }
        } catch (error: CaptureException) {
            failure = error.failure
        } catch (_: SecurityException) {
            failure = CaptureFailure.PERMISSION_DENIED
        } catch (_: Exception) {
            failure = CaptureFailure.READ_ERROR
        } finally {
            running.set(false)
            bytes.fill(0)
            val stopped = stopTiming
            synchronized(nativeOwnership) {
                stopped("reader_stop_start")
                runCatching { record.stop() }
                stopped("reader_stop_end")
                record.release()
                recorder = null
            }
            signal = MicrophoneSignal()
            stopped("reader_released")
        }
    }

    /** Called off main; the reader owns final release. Never start another until it has exited. */
    fun stop() {
        requestStop()
        thread?.join(STOP_WAIT_MILLIS)
        stopTiming("join_end")
        if (thread?.isAlive == true) throw CaptureException(CaptureFailure.THREAD_TIMEOUT)
        thread = null
        signal = MicrophoneSignal()
    }

    fun requestStop(timing: ((String) -> Unit)? = null): CaptureAdmission.Boundary {
        val boundary = stopReading(timing)
        val selected = recorder ?: return boundary
        val stopped = stopTiming
        synchronized(stopRequests) {
            if (stopRequestedHandle === selected) return@synchronized
            stopRequestedHandle = selected
            nativeControl.execute {
                synchronized(nativeOwnership) {
                    if (recorder === selected) {
                        stopped("control_stop_start")
                        runCatching { selected.stop() }
                        stopped("control_stop_end")
                    }
                }
            }
        }
        return boundary
    }

    /** Nonblocking control signal; the bounded native read returns within its current buffer. */
    fun stopReading(timing: ((String) -> Unit)? = null): CaptureAdmission.Boundary {
        if (timing != null) stopTiming = timing
        val boundary = admission.fence()
        running.set(false)
        return boundary
    }

    val hasLiveThread: Boolean
        get() = thread?.isAlive == true

    fun drain(maximumBlocks: Int = QUEUE_CAPACITY, consume: (ByteArray) -> Unit) {
        queue.drain(maximumBlocks, consume)
    }

    fun drainOwned(maximumBlocks: Int = QUEUE_CAPACITY, consume: (CapturedBlock) -> Unit) {
        queue.drainOwned(maximumBlocks, consume)
    }

    fun durableThrough(frames: Long) = admission.durableThrough(frames)

    fun retire() {
        check(!hasLiveThread)
        admission.retire()
    }

    private fun level(bytes: ByteArray, count: Int): Float {
        var energy = 0.0
        var index = 0
        while (index < count) {
            val sample =
                ((bytes[index].toInt() and BYTE_MASK) or (bytes[index + 1].toInt() shl BYTE_BITS))
                    .toShort()
                    .toDouble()
            energy += sample * sample
            index += 2
        }
        return (sqrt(energy / (count / 2)) / PCM_FULL_SCALE).toFloat().coerceIn(0f, 1f)
    }

    private fun routeLabel(type: Int?): String =
        when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Встроенный микрофон"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Проводная гарнитура"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB-микрофон"
            null -> "Маршрут определяется"
            else -> "Внешний микрофон"
        }

    private companion object {
        const val BYTE_MASK = 0xff
        const val BYTE_BITS = 8
        const val READ_BYTES = 1_600
        // 16 seconds / 512000 bytes: bounded headroom for foreground-service fsync stalls.
        // Sustained overload still stops explicitly; no audio is dropped to keep recording.
        const val QUEUE_CAPACITY = 320
        const val SIGNAL_PERIOD_NANOS = 50_000_000L
        const val STOP_WAIT_MILLIS = 2_000L
        const val PCM_FULL_SCALE = 32768.0
    }
}
