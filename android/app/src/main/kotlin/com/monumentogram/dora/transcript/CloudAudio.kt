@file:Suppress("MagicNumber") // Canonical RIFF/PCM field widths and offsets.

package com.monumentogram.dora.transcript

import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AuthenticatedAudioSource
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
        const val MAX_FRAMES = 4800000L
        const val MAX_PART_FRAMES = 28_800_000L
        const val MAX_COMPOSITE_FRAMES = 1_382_400_000L
        const val MAX_WINDOW_BYTES = 8 * 1024 * 1024
        private const val MIN_FRAMES = 8000L

        /** Read only this authenticated WAV range; network work begins after this returns. */
        @Suppress("LongParameterList", "LongMethod", "TooGenericExceptionCaught", "ThrowsCount")
        fun readWindow(
            source: AuthenticatedAudioSource,
            firstFrame: Long,
            partFrames: Long,
            wavOffset: Long,
            length: Int,
            cancelled: () -> Boolean,
        ): CloudAudio {
            require(partFrames in MIN_FRAMES..MAX_PART_FRAMES)
            require(firstFrame >= 0 && firstFrame <= source.reference.frames - partFrames)
            val total = Math.addExact(Math.multiplyExact(partFrames, 2L), HEADER_BYTES.toLong())
            require(length in 1..MAX_WINDOW_BYTES && wavOffset >= 0 && wavOffset <= total - length)
            if (cancelled()) throw CancellationException()
            val owned = CloudAudio(ByteArray(length))
            try {
                val dataBytes = Math.multiplyExact(partFrames, 2L).toInt()
                val header =
                    ByteBuffer.allocate(HEADER_BYTES)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .put("RIFF".toByteArray(Charsets.US_ASCII))
                        .putInt(dataBytes + 36)
                        .put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
                        .putInt(16)
                        .putShort(1)
                        .putShort(1)
                        .putInt(SAMPLE_RATE)
                        .putInt(SAMPLE_RATE * 2)
                        .putShort(2)
                        .putShort(16)
                        .put("data".toByteArray(Charsets.US_ASCII))
                        .putInt(dataBytes)
                        .array()
                if (wavOffset < HEADER_BYTES) {
                    val count = minOf(length.toLong(), HEADER_BYTES - wavOffset).toInt()
                    header.copyInto(owned.bytes, 0, wavOffset.toInt(), wavOffset.toInt() + count)
                }
                header.fill(0)
                val pcmFirst = maxOf(0L, wavOffset - HEADER_BYTES) / 2
                val pcmEnd = maxOf(0L, wavOffset + length - HEADER_BYTES + 1) / 2
                // A header-only request still rechecks the handle's current source authority.
                val rangeFirst = firstFrame + pcmFirst
                val rangeCount = maxOf(1L, pcmEnd - pcmFirst)
                var next = rangeFirst
                val result =
                    source.extractRange(rangeFirst, rangeCount) { first, bytes ->
                        if (cancelled()) throw CancellationException()
                        require(first == next && bytes.isNotEmpty() && bytes.size % 2 == 0)
                        require(bytes.size / 2L <= rangeFirst + rangeCount - next)
                        val start = HEADER_BYTES + (first - firstFrame) * 2
                        val begin = maxOf(start, wavOffset)
                        val end = minOf(start + bytes.size, wavOffset + length)
                        if (end > begin)
                            bytes.copyInto(
                                owned.bytes,
                                (begin - wavOffset).toInt(),
                                (begin - start).toInt(),
                                (end - start).toInt(),
                            )
                        next += bytes.size / 2L
                    }
                if (cancelled()) throw CancellationException()
                check(
                    result is AudioResult.Value &&
                        result.value == OriginalAudioStatus.Available(source.reference)
                )
                check(next == rangeFirst + rangeCount)
                return owned
            } catch (error: Throwable) {
                owned.close()
                throw error
            }
        }

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
