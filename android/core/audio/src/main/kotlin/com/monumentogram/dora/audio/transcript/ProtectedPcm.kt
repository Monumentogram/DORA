package com.monumentogram.dora.audio.transcript

import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioPort
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.recording.AlphaRecordingLimit
import java.util.concurrent.CancellationException

/** Owned, bounded memory. Never retain a borrowed vault byte array or infer inside its callback. */
class ProtectedPcm private constructor(val samples: FloatArray) : AutoCloseable {
    override fun close() = samples.fill(0f)

    companion object {
        private const val PCM_SAMPLE_BYTES = 2
        private const val UNSIGNED_BYTE_MASK = 255
        private const val PCM_NORMALIZATION = 32768f

        private fun checkCancellation(cancelled: () -> Boolean) {
            if (cancelled()) throw CancellationException()
        }

        @Suppress("TooGenericExceptionCaught") // Wipe owned samples for every failure, including native errors.
        fun read(
            port: OriginalAudioPort,
            source: OriginalAudioReference,
            cancelled: () -> Boolean,
        ): ProtectedPcm {
            require(source.frames in 1..AlphaRecordingLimit.MAX_ALPHA_FRAMES)
            val owned = ProtectedPcm(FloatArray(source.frames.toInt()))
            var next = 0L
            try {
                val result =
                    port.extract(source) { first, bytes ->
                        checkCancellation(cancelled)
                        require(first == next && bytes.isNotEmpty() && bytes.size % PCM_SAMPLE_BYTES == 0)
                        require(bytes.size / PCM_SAMPLE_BYTES.toLong() <= source.frames - next)
                        for (index in bytes.indices step PCM_SAMPLE_BYTES) {
                            val value =
                                (bytes[index].toInt() and UNSIGNED_BYTE_MASK) or (bytes[index + 1].toInt() shl Byte.SIZE_BITS)
                            owned.samples[next.toInt()] = value.toShort() / PCM_NORMALIZATION
                            next++
                        }
                    }
                checkCancellation(cancelled)
                check(
                    result is AudioResult.Value &&
                        result.value == OriginalAudioStatus.Available(source)
                )
                check(next == source.frames)
                return owned
            } catch (error: Throwable) {
                owned.close()
                throw error
            }
        }
    }
}
