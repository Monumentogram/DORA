@file:Suppress("MagicNumber") // Canonical RIFF/PCM field widths and offsets.

package com.monumentogram.dora.transcript

import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioPort
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

/** Canonical WAV of the original PCM; no resampling, lossy conversion or plaintext disk file. */
internal class CloudAudio private constructor(val bytes: ByteArray) : AutoCloseable {
    override fun close() = bytes.fill(0)

    companion object {
        private const val HEADER_BYTES = 44
        private const val SAMPLE_RATE = 16000
        private const val MAX_FRAMES = 4800000L
        private const val MIN_FRAMES = 8000L

        @Suppress("TooGenericExceptionCaught", "ThrowsCount") // Wipe owned PCM on every failure.
        fun read(
            port: OriginalAudioPort,
            source: OriginalAudioReference,
            cancelled: () -> Boolean,
        ): CloudAudio {
            require(source.frames in MIN_FRAMES..MAX_FRAMES)
            val dataBytes = source.frames.toInt() * 2
            val owned = CloudAudio(ByteArray(HEADER_BYTES + dataBytes))
            try {
                val buffer = ByteBuffer.wrap(owned.bytes).order(ByteOrder.LITTLE_ENDIAN)
                buffer.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(dataBytes + 36)
                buffer.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
                buffer.putShort(1).putShort(1).putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2)
                buffer
                    .putShort(2)
                    .putShort(16)
                    .put("data".toByteArray(Charsets.US_ASCII))
                    .putInt(dataBytes)
                var frames = 0L
                val result =
                    port.extract(source) { first, pcm ->
                        if (cancelled()) throw CancellationException()
                        require(first == frames && pcm.isNotEmpty() && pcm.size % 2 == 0)
                        require(pcm.size <= buffer.remaining())
                        buffer.put(pcm)
                        frames += pcm.size / 2
                    }
                if (cancelled()) throw CancellationException()
                check(
                    result is AudioResult.Value &&
                        result.value == OriginalAudioStatus.Available(source)
                )
                check(frames == source.frames && !buffer.hasRemaining())
                return owned
            } catch (error: Throwable) {
                owned.close()
                throw error
            }
        }
    }
}

internal object CloudEndpoint {
    fun validate(value: String): String {
        val uri = java.net.URI(value.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null)
        require(uri.path.isNullOrEmpty() || uri.path == "/")
        require(uri.rawQuery == null && uri.rawFragment == null && value.length <= 2048)
        require(uri.port == -1 || uri.port in 1..65535)
        return uri.toASCIIString().removeSuffix("/")
    }
}
