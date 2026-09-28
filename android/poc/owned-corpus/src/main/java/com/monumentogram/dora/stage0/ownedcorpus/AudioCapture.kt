package com.monumentogram.dora.stage0.ownedcorpus

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.min

/** Foreground-only worker. No microphone is created until start() is explicitly invoked. */
class AudioCapture(private val context: Context, private val store: CorpusStore) {
    companion object {
        private const val BUFFER_FRAMES = 1024
        private const val MIN_BUFFER_BYTES = 8192
        private const val PCM_MAGNITUDE = 32768
        private const val BYTE_BITS = 8
        private const val PERCENT = 100
    }

    private val stopping = AtomicBoolean(false)
    @Volatile private var interruption: String? = null
    @Volatile private var recorder: AudioRecord? = null
    @Volatile
    var running: Boolean = false
        private set

    @Volatile
    var frames: Int = 0
        private set

    fun start(id: String, onProgress: (Int, Int) -> Unit, onComplete: (Boolean, String?) -> Unit) {
        check(!running && store.recordingEnabled()) { "RECORDING_DEFERRED_OR_BUSY" }
        check(
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            "MICROPHONE_PERMISSION_REQUIRED"
        }
        val attempt = store.beginAttempt(id)
        running = true
        frames = 0
        stopping.set(false)
        interruption = null
        Thread({ runAttempt(attempt, onProgress, onComplete) }, "dora-owned-capture").start()
    }

    @SuppressLint("MissingPermission")
    private fun runAttempt(
        attempt: CorpusStore.Attempt,
        onProgress: (Int, Int) -> Unit,
        onComplete: (Boolean, String?) -> Unit,
    ) {
        var failure: String? = null
        try {
            val audio = createRecorder()
            recorder = audio
            check(!stopping.get()) { "CAPTURE_CANCELLED_BEFORE_START" }
            audio.startRecording()
            check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "AUDIO_START_FAILED"
            }
            recordFrames(audio, attempt, onProgress)
        } catch (_: Exception) {
            failure = interruption ?: "AUDIO_CAPTURE_FAILED"
        } finally {
            closeRecorder()
            failure = interruption ?: failure
            var accepted = false
            try {
                accepted = store.completeAttempt(attempt, failure)
            } catch (_: Exception) {
                failure = "PRIVATE_SAVE_FAILED_REOPEN_TO_RECOVER"
            }
            running = false
            onComplete(accepted, if (accepted) null else failure ?: "INVALID_FORMAT_OR_DURATION")
        }
    }

    @SuppressLint("MissingPermission")
    private fun createRecorder(): AudioRecord {
        val minimum =
            AudioRecord.getMinBufferSize(
                Wav.RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        check(minimum > 0) { "AUDIO_FORMAT_UNSUPPORTED" }
        // MIC can include OEM processing; its exact PCM is preserved without claiming raw hardware
        // audio.
        return AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(Wav.RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minimum * 2, MIN_BUFFER_BYTES))
            .build()
            .also {
                if (it.state != AudioRecord.STATE_INITIALIZED || it.sampleRate != Wav.RATE) {
                    it.release()
                    error("AUDIO_INITIALIZATION_FAILED")
                }
            }
    }

    private fun recordFrames(
        audio: AudioRecord,
        attempt: CorpusStore.Attempt,
        onProgress: (Int, Int) -> Unit,
    ) {
        val buffer = ShortArray(BUFFER_FRAMES)
        val maximum = CapturePolicy.maximumFrames(attempt.speechClass)
        RandomAccessFile(attempt.file, "rw").use { output ->
            output.seek(Wav.HEADER_BYTES.toLong())
            while (!stopping.get() && frames < maximum) {
                val count =
                    audio.read(
                        buffer,
                        0,
                        min(buffer.size, maximum - frames),
                        AudioRecord.READ_BLOCKING,
                    )
                if (count <= 0 && stopping.get()) break
                check(count > 0) { "AUDIO_READ_FAILED" }
                val (bytes, peak) = pcmBytes(buffer, count)
                output.write(bytes)
                frames += count
                onProgress(frames, peak * PERCENT / PCM_MAGNITUDE)
            }
            output.fd.sync()
        }
    }

    private fun pcmBytes(buffer: ShortArray, count: Int): Pair<ByteArray, Int> {
        val bytes = ByteArray(count * 2)
        var peak = 0
        for (i in 0 until count) {
            val value = buffer[i].toInt()
            bytes[i * 2] = value.toByte()
            bytes[i * 2 + 1] = (value shr BYTE_BITS).toByte()
            peak = maxOf(peak, abs(value))
        }
        return bytes to peak
    }

    private fun closeRecorder() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
            /* Keep already captured original bytes. */
        }
        recorder?.release()
        recorder = null
    }

    fun stop(reason: String? = null) {
        if (!running) return
        if (reason != null) interruption = reason
        stopping.set(true)
        try {
            recorder?.stop()
        } catch (_: Exception) {
            /* Worker retains original bytes. */
        }
    }
}
