package com.monumentogram.dora.recording

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/** The capture loop owns this handle through final release. Only the Android adapter creates it. */
internal interface NativeMicrophone {
    fun requireConfiguration()

    fun startRecording()

    val recording: Boolean
    val routeType: Int?

    fun read(bytes: ByteArray): Int

    fun stop()

    fun release()
}

internal class AndroidMicrophone private constructor(private val record: AudioRecord) :
    NativeMicrophone {
    override fun requireConfiguration() {
        if (record.state != AudioRecord.STATE_INITIALIZED)
            throw CaptureException(CaptureFailure.INITIALIZATION_FAILED)
        if (
            record.sampleRate != SAMPLE_RATE ||
                record.channelCount != 1 ||
                record.audioFormat != AudioFormat.ENCODING_PCM_16BIT
        )
            throw CaptureException(CaptureFailure.CONFIGURATION_UNAVAILABLE)
    }

    override fun startRecording() = record.startRecording()

    override val recording: Boolean
        get() = record.recordingState == AudioRecord.RECORDSTATE_RECORDING

    override val routeType: Int?
        get() = record.routedDevice?.type

    override fun read(bytes: ByteArray): Int =
        record.read(bytes, 0, bytes.size, AudioRecord.READ_BLOCKING)

    override fun stop() = record.stop()

    override fun release() = record.release()

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val NATIVE_BUFFER_MULTIPLIER = 4

        @SuppressLint("MissingPermission")
        fun create(): NativeMicrophone {
            val minimum =
                AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            if (minimum <= 0) throw CaptureException(CaptureFailure.CONFIGURATION_UNAVAILABLE)
            return AndroidMicrophone(
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
            )
        }
    }
}
