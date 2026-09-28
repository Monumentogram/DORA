package com.monumentogram.dora.stage0.ownedcorpus

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

// RIFF widths and PCM16 encodings are fixed by the binary format, verified byte-for-byte.
@Suppress("MagicNumber")
object Wav {
    const val HEADER_BYTES = 44
    const val MAX_FRAMES = 960_000
    const val RATE = 16_000
    const val MAX_BYTES = 1_920_044

    fun header(frames: Int): ByteArray {
        require(frames in 0..960_000) { "INVALID_FRAME_COUNT" }
        return ByteBuffer.allocate(44)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(36 + frames * 2)
                put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1)
                putShort(1)
                putInt(RATE)
                putInt(RATE * 2)
                putShort(2)
                putShort(16)
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(frames * 2)
            }
            .array()
    }

    fun durationUs(frames: Int): Long = frames.toLong() * 1_000_000 / RATE

    fun inspect(bytes: ByteArray): Long {
        require(bytes.size in 44..MAX_BYTES && (bytes.size - 44) % 2 == 0) { "INVALID_WAV_SIZE" }
        val expected = header((bytes.size - 44) / 2)
        require(bytes.copyOfRange(0, 44).contentEquals(expected)) { "INVALID_WAV_FORMAT" }
        return durationUs((bytes.size - 44) / 2)
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it.toInt() and 255)
        }
}

object CapturePolicy {
    private const val READ_SECONDS = 44
    private const val SPONTANEOUS_SECONDS = 59
    private const val READ_LIMIT = 45
    private const val SPONTANEOUS_LIMIT = 60
    private const val MIN_FRAMES = 320_000
    private const val STOP_FRAMES = 324_000

    fun maximumFrames(speechClass: String): Int {
        require(speechClass == "READ" || speechClass == "SPONTANEOUS") { "INVALID_SPEECH_CLASS" }
        return (if (speechClass == "READ") READ_SECONDS else SPONTANEOUS_SECONDS) * Wav.RATE
    }

    fun canStop(frames: Int): Boolean = frames >= STOP_FRAMES

    fun eligible(frames: Int, speechClass: String): Boolean =
        frames >= MIN_FRAMES &&
            frames <= (if (speechClass == "READ") READ_LIMIT else SPONTANEOUS_LIMIT) * Wav.RATE
}
