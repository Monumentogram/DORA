@file:Suppress(
    "ThrowsCount",
    "LongMethod",
    "CyclomaticComplexMethod",
    "TooGenericExceptionCaught",
) // Native acquisition maps failures and releases on every exceptional exit.

package com.monumentogram.dora.recording

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
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
internal class AudioRecordCapture(private val context: Context) {
    private val running = AtomicBoolean(false)
    private val queue = BoundedPcmQueue(QUEUE_CAPACITY)
    @Volatile private var recorder: AudioRecord? = null
    private var thread: Thread? = null
    val maximumQueuedBlocks: Int
        get() = queue.highWater

    val queuedBlocks: Int
        get() = queue.size

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

    @SuppressLint("MissingPermission")
    fun start(withStartAuthority: (() -> Unit) -> Unit) {
        check(recorder == null && thread?.isAlive != true)
        if (
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
        )
            throw CaptureException(CaptureFailure.PERMISSION_DENIED)
        val minimum =
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        if (minimum <= 0) throw CaptureException(CaptureFailure.CONFIGURATION_UNAVAILABLE)
        val created =
            try {
                AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(
                        maxOf(minimum * NATIVE_BUFFER_MULTIPLIER, SAMPLE_RATE * 2)
                    )
                    .build()
            } catch (_: SecurityException) {
                throw CaptureException(CaptureFailure.PERMISSION_DENIED)
            } catch (_: Exception) {
                throw CaptureException(CaptureFailure.INITIALIZATION_FAILED)
            }
        try {
            if (created.state != AudioRecord.STATE_INITIALIZED)
                throw CaptureException(CaptureFailure.INITIALIZATION_FAILED)
            if (
                created.sampleRate != SAMPLE_RATE ||
                    created.channelCount != 1 ||
                    created.audioFormat != AudioFormat.ENCODING_PCM_16BIT
            )
                throw CaptureException(CaptureFailure.CONFIGURATION_UNAVAILABLE)
            withStartAuthority {
                try {
                    created.startRecording()
                } catch (_: SecurityException) {
                    throw CaptureException(CaptureFailure.PERMISSION_DENIED)
                } catch (_: Exception) {
                    throw CaptureException(CaptureFailure.START_FAILED)
                }
                if (created.recordingState != AudioRecord.RECORDSTATE_RECORDING)
                    throw CaptureException(CaptureFailure.START_FAILED)
                // Publish ownership under the same authority monitor as native start.
                // Revocation can now always find and stop the reader it revoked.
                failure = null
                signal = MicrophoneSignal()
                recorder = created
                running.set(true)
                thread = Thread({ readLoop(created) }, "Dora microphone").also { it.start() }
            }
        } catch (error: Exception) {
            runCatching { created.stop() }
            created.release()
            throw error
        }
    }

    private fun readLoop(record: AudioRecord) {
        val bytes = ByteArray(READ_BYTES)
        var lastSignal = 0L
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            while (running.get()) {
                if (
                    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                        PackageManager.PERMISSION_GRANTED
                )
                    throw CaptureException(CaptureFailure.PERMISSION_DENIED)
                val count = record.read(bytes, 0, bytes.size, AudioRecord.READ_BLOCKING)
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
                if (!queue.offer(copied)) {
                    copied.fill(0)
                    throw CaptureException(CaptureFailure.PERSISTENCE_BACKPRESSURE)
                }
                val now = System.nanoTime()
                if (now - lastSignal >= SIGNAL_PERIOD_NANOS) {
                    signal = MicrophoneSignal(level(bytes, count), now)
                    lastSignal = now
                    route = routeLabel(record.routedDevice?.type)
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
            runCatching { record.stop() }
            record.release()
            recorder = null
            signal = MicrophoneSignal()
        }
    }

    /** Called off main; the reader owns final release. Never start another until it has exited. */
    fun stop() {
        requestStop()
        thread?.join(STOP_WAIT_MILLIS)
        if (thread?.isAlive == true) throw CaptureException(CaptureFailure.THREAD_TIMEOUT)
        thread = null
        signal = MicrophoneSignal()
    }

    fun requestStop() {
        running.set(false)
        runCatching { recorder?.stop() }
    }

    /** Nonblocking control signal; the bounded native read returns within its current buffer. */
    fun stopReading() {
        running.set(false)
    }

    val hasLiveThread: Boolean
        get() = thread?.isAlive == true

    fun drain(maximumBlocks: Int = QUEUE_CAPACITY, consume: (ByteArray) -> Unit) {
        queue.drain(maximumBlocks, consume)
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
        const val SAMPLE_RATE = 16_000
        const val NATIVE_BUFFER_MULTIPLIER = 4
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
