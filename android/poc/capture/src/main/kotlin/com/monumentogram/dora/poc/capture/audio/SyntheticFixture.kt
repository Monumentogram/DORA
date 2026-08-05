@file:Suppress("MagicNumber") // Fixed DSP fixture constants are independently digest-pinned.

package com.monumentogram.dora.poc.capture.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.sin

data class FixtureData(
    val id: String,
    val version: String,
    val sampleRate: Int,
    val samples: ShortArray,
    val sha256: String,
    val segments: List<String>,
)

object SyntheticFixture {
    const val ID = "dora-capture-non-speech-v1"
    const val VERSION = "1.0.0"
    const val SAMPLE_RATE = 16_000
    const val DURATION_SECONDS = 12

    fun generate(): FixtureData {
        val samples = ShortArray(SAMPLE_RATE * DURATION_SECONDS)
        addTone(samples, startSecond = 1.0, durationSeconds = 1.0, frequencyHz = 440.0)
        addChirp(
            samples,
            startSecond = 3.0,
            durationSeconds = 2.0,
            startFrequencyHz = 300.0,
            endFrequencyHz = 2_400.0,
        )
        addMarkers(samples, startSecond = 6.0, markerCount = 4)
        addTone(samples, startSecond = 8.0, durationSeconds = 1.0, frequencyHz = 880.0)
        val bytes = pcmBytes(samples)
        return FixtureData(
            id = ID,
            version = VERSION,
            sampleRate = SAMPLE_RATE,
            samples = samples,
            sha256 = sha256(bytes),
            segments =
                listOf(
                    "silence:0.0-1.0s",
                    "tone-440hz:1.0-2.0s",
                    "silence:2.0-3.0s",
                    "chirp-300-2400hz:3.0-5.0s",
                    "silence:5.0-6.0s",
                    "markers:6.0-7.0s",
                    "silence:7.0-8.0s",
                    "tone-880hz:8.0-9.0s",
                    "silence:9.0-12.0s",
                ),
        )
    }

    fun pcmBytes(samples: ShortArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        var output = 0
        for (sample in samples) {
            val value = sample.toInt()
            bytes[output++] = (value and 0xff).toByte()
            bytes[output++] = (value ushr 8 and 0xff).toByte()
        }
        return bytes
    }

    private fun addTone(
        target: ShortArray,
        startSecond: Double,
        durationSeconds: Double,
        frequencyHz: Double,
    ) {
        val start = (startSecond * SAMPLE_RATE).toInt()
        val count = (durationSeconds * SAMPLE_RATE).toInt()
        for (index in 0 until count) {
            val envelope = fadeEnvelope(index, count)
            val value = sin(2.0 * PI * frequencyHz * index / SAMPLE_RATE)
            target[start + index] = (value * envelope * AMPLITUDE).toInt().toShort()
        }
    }

    private fun addChirp(
        target: ShortArray,
        startSecond: Double,
        durationSeconds: Double,
        startFrequencyHz: Double,
        endFrequencyHz: Double,
    ) {
        val start = (startSecond * SAMPLE_RATE).toInt()
        val count = (durationSeconds * SAMPLE_RATE).toInt()
        val frequencySlope = (endFrequencyHz - startFrequencyHz) / durationSeconds
        for (index in 0 until count) {
            val time = index.toDouble() / SAMPLE_RATE
            val phase = 2.0 * PI * (startFrequencyHz * time + 0.5 * frequencySlope * time * time)
            val value = sin(phase) * fadeEnvelope(index, count)
            target[start + index] = (value * AMPLITUDE).toInt().toShort()
        }
    }

    private fun addMarkers(target: ShortArray, startSecond: Double, markerCount: Int) {
        val base = (startSecond * SAMPLE_RATE).toInt()
        val markerLength = SAMPLE_RATE / 100
        val spacing = SAMPLE_RATE / 5
        repeat(markerCount) { marker ->
            repeat(markerLength) { index ->
                val phase = 2.0 * PI * 1_600.0 * index / SAMPLE_RATE
                target[base + marker * spacing + index] =
                    (sin(phase) * AMPLITUDE * fadeEnvelope(index, markerLength)).toInt().toShort()
            }
        }
    }

    private fun fadeEnvelope(index: Int, count: Int): Double {
        val fadeSamples = (SAMPLE_RATE * 0.01).toInt()
        return when {
            index < fadeSamples -> index.toDouble() / fadeSamples
            index >= count - fadeSamples ->
                (count - index - 1).coerceAtLeast(0).toDouble() / fadeSamples
            else -> 1.0
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }

    private const val AMPLITUDE = 8_000.0
}

class FixturePlayer {
    private var audioTrack: AudioTrack? = null

    fun start(fixture: FixtureData = SyntheticFixture.generate()) {
        stop()
        val bytes = SyntheticFixture.pcmBytes(fixture.samples)
        var candidate: AudioTrack? = null
        try {
            val track =
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(fixture.sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bytes.size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            candidate = track
            check(track.state == AudioTrack.STATE_INITIALIZED) { "Тестовый сигнал не подготовлен" }
            val written = track.write(bytes, 0, bytes.size)
            check(written == bytes.size) { "Тестовый сигнал загружен не полностью" }
            check(track.setLoopPoints(0, fixture.samples.size, -1) == AudioTrack.SUCCESS) {
                "Цикл тестового сигнала не настроен"
            }
            track.setVolume(AudioTrack.getMaxVolume().coerceAtMost(0.55f))
            track.play()
            audioTrack = track
            candidate = null
        } finally {
            candidate?.let(::releaseTrack)
        }
    }

    fun stop() {
        val track = audioTrack ?: return
        audioTrack = null
        releaseTrack(track)
    }

    private fun releaseTrack(track: AudioTrack) {
        runCatching { track.stop() }
        runCatching { track.release() }
    }
}
