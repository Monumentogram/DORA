@file:Suppress("MagicNumber", "NestedBlockDepth", "TooGenericExceptionCaught")

package com.monumentogram.dora.poc.capture.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.os.Process
import android.os.SystemClock
import com.monumentogram.dora.poc.capture.device.DeviceInspector
import com.monumentogram.dora.poc.capture.model.AudioConfiguration
import com.monumentogram.dora.poc.capture.model.AudioCounters
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

data class AudioStartResult(
    val configuration: AudioConfiguration,
    val startElapsedMs: Long,
    val startLatencyMs: Long,
)

data class AudioStopResult(
    val finalizedFile: File,
    val counters: AudioCounters,
    val finalizationLatencyMs: Long,
    val failure: String?,
)

class AudioCaptureEngine {
    private val running = AtomicBoolean(false)
    private val samples = AtomicLong(0)
    private val bytes = AtomicLong(0)
    private val shortReads = AtomicLong(0)
    private val errors = ConcurrentHashMap<String, Int>()
    private val failure = AtomicReference<String?>(null)
    private val stopLock = Any()
    private var recorder: AudioRecord? = null
    private var writer: WavWriter? = null
    private var audioThread: Thread? = null
    private var partFile: File? = null
    private var cachedStopResult: AudioStopResult? = null

    @SuppressLint("MissingPermission")
    fun start(targetPartFile: File): AudioStartResult {
        check(!running.get()) { "AudioRecord уже запущен" }
        resetCounters()
        cachedStopResult = null
        val (created, selected) = createInitializedRecorder()
        val wavWriter =
            WavWriter(
                targetPartFile,
                WavFormat(selected.sampleRate, selected.channelCount, selected.bitsPerSample),
            )
        recorder = created
        writer = wavWriter
        partFile = targetPartFile
        running.set(true)
        val requestAt = SystemClock.elapsedRealtime()
        try {
            created.startRecording()
            check(created.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "AudioRecord не перешёл в состояние записи"
            }
        } catch (error: Throwable) {
            running.set(false)
            wavWriter.closeLeavingPartial()
            created.release()
            recorder = null
            throw error
        }
        val startedAt = SystemClock.elapsedRealtime()
        audioThread =
            Thread({ captureLoop(created, wavWriter, selected.bufferBytes) }, "dora-poc-audio")
                .also {
                    it.start()
                }
        return AudioStartResult(
            configuration = selected,
            startElapsedMs = startedAt,
            startLatencyMs = startedAt - requestAt,
        )
    }

    fun currentCounters(): AudioCounters {
        val record = recorder
        var timestampFrames: Long? = null
        var timestampNanos: Long? = null
        if (record != null) {
            val timestamp = AudioTimestamp()
            if (
                record.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) ==
                    AudioRecord.SUCCESS
            ) {
                timestampFrames = timestamp.framePosition
                timestampNanos = timestamp.nanoTime
            }
        }
        val route =
            record?.routedDevice?.let { DeviceInspector.audioRouteLabel(it.type) } ?: "Не определён"
        return AudioCounters(
            samples = samples.get(),
            bytes = bytes.get(),
            fileBytes = partFile?.length() ?: 0L,
            shortReads = shortReads.get(),
            errors = errors.toSortedMap(),
            route = route,
            audioTimestampFrames = timestampFrames,
            audioTimestampNanos = timestampNanos,
        )
    }

    fun stop(): AudioStopResult =
        synchronized(stopLock) {
            cachedStopResult?.let {
                return@synchronized it
            }
            val record = checkNotNull(recorder) { "AudioRecord не был запущен" }
            val wavWriter = checkNotNull(writer)
            val stopRequestedAt = SystemClock.elapsedRealtime()
            running.set(false)
            runCatching {
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
                }
                .onFailure { recordError("STOP_${it.javaClass.simpleName.uppercase()}") }
            audioThread?.join(10_000)
            if (audioThread?.isAlive == true) {
                recordError("AUDIO_THREAD_STOP_TIMEOUT")
                failure.compareAndSet(null, "Audio thread не завершился за 10 секунд")
            }
            val preFinalCounters = currentCounters()
            val finalized =
                try {
                    wavWriter.finish()
                } finally {
                    record.release()
                    recorder = null
                    writer = null
                    audioThread = null
                }
            val finalCounters = preFinalCounters.copy(fileBytes = finalized.length())
            val result =
                AudioStopResult(
                    finalizedFile = finalized,
                    counters = finalCounters,
                    finalizationLatencyMs = SystemClock.elapsedRealtime() - stopRequestedAt,
                    failure = failure.get(),
                )
            cachedStopResult = result
            result
        }

    fun isRecording(): Boolean = running.get()

    private fun captureLoop(record: AudioRecord, wavWriter: WavWriter, bufferBytes: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val buffer = ByteArray(bufferBytes)
        try {
            while (running.get()) {
                val count = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (count > 0) {
                    wavWriter.write(buffer, 0, count)
                    bytes.addAndGet(count.toLong())
                    samples.addAndGet(pcm16SampleCount(count).toLong())
                    if (count < buffer.size) shortReads.incrementAndGet()
                } else if (count < 0) {
                    val code = classifyAudioReadError(count)
                    recordError(code)
                    failure.compareAndSet(null, "AudioRecord.read вернул $code")
                    running.set(false)
                }
            }
        } catch (error: Throwable) {
            recordError("AUDIO_LOOP_${error.javaClass.simpleName.uppercase()}")
            failure.compareAndSet(null, "Audio loop завершился с технической ошибкой")
            running.set(false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun createInitializedRecorder(): Pair<AudioRecord, AudioConfiguration> {
        configurations().forEach { candidate ->
            val audioFormat =
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(candidate.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            val created =
                runCatching {
                        AudioRecord.Builder()
                            .setAudioSource(MediaRecorder.AudioSource.MIC)
                            .setAudioFormat(audioFormat)
                            .setBufferSizeInBytes(candidate.bufferBytes)
                            .build()
                    }
                    .getOrNull()
            if (created?.state == AudioRecord.STATE_INITIALIZED) return created to candidate
            created?.release()
        }
        error("Не найдена безопасная mono PCM16 конфигурация")
    }

    private fun configurations(): List<AudioConfiguration> =
        DeviceInspector.SAMPLE_RATES.mapNotNull { sampleRate ->
            val minimum =
                AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            if (minimum <= 0) {
                null
            } else {
                val quarterSecond = sampleRate * PCM16_BYTES_PER_SAMPLE / 4
                AudioConfiguration(
                    sampleRate = sampleRate,
                    bufferBytes = alignEven(max(minimum * 4, quarterSecond)),
                    fallbackUsed = sampleRate != PREFERRED_SAMPLE_RATE,
                )
            }
        }

    private fun recordError(code: String) {
        errors.merge(code, 1, Int::plus)
    }

    private fun resetCounters() {
        samples.set(0)
        bytes.set(0)
        shortReads.set(0)
        errors.clear()
        failure.set(null)
    }

    private fun alignEven(value: Int): Int = if (value % 2 == 0) value else value + 1

    companion object {
        private const val PREFERRED_SAMPLE_RATE = 16_000
        private const val PCM16_BYTES_PER_SAMPLE = 2
    }
}

internal fun classifyAudioReadError(value: Int): String =
    when (value) {
        AudioRecord.ERROR_INVALID_OPERATION -> "AUDIORECORD_INVALID_OPERATION"
        AudioRecord.ERROR_BAD_VALUE -> "AUDIORECORD_BAD_VALUE"
        AudioRecord.ERROR_DEAD_OBJECT -> "AUDIORECORD_DEAD_OBJECT"
        AudioRecord.ERROR -> "AUDIORECORD_GENERIC_ERROR"
        else -> "AUDIORECORD_UNKNOWN_ERROR"
    }

internal fun pcm16SampleCount(byteCount: Int): Int = byteCount.coerceAtLeast(0) / 2
