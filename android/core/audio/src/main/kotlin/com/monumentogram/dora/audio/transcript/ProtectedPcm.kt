package com.monumentogram.dora.audio.transcript

import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AuthenticatedAudioSource
import com.monumentogram.dora.audio.OriginalAudioPort
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.recording.AlphaRecordingLimit
import java.util.concurrent.CancellationException

/** Owned, bounded memory. Never retain a borrowed vault byte array or infer inside its callback. */
class ProtectedPcm private constructor(val samples: FloatArray) : AutoCloseable {
    override fun close() = samples.fill(0f)

    companion object {
        const val MAX_WINDOW_FRAMES = 4_800_000
        private const val PCM_SAMPLE_BYTES = 2
        private const val UNSIGNED_BYTE_MASK = 255
        private const val PCM_NORMALIZATION = 32768f

        private fun checkCancellation(cancelled: () -> Boolean) {
            if (cancelled()) throw CancellationException()
        }

        /**
         * The handle authenticated the complete source at open; this call reauthenticates range
         * units.
         */
        @Suppress("TooGenericExceptionCaught")
        fun readWindow(
            handle: AuthenticatedAudioSource,
            firstFrame: Long,
            frameCount: Int,
            cancelled: () -> Boolean,
        ): ProtectedPcm {
            val source = handle.reference
            require(source.frames in 1..AlphaRecordingLimit.MAX_ALPHA_FRAMES)
            require(firstFrame >= 0 && frameCount in 1..MAX_WINDOW_FRAMES)
            require(firstFrame <= source.frames - frameCount)
            checkCancellation(cancelled)
            val owned = ProtectedPcm(FloatArray(frameCount))
            var next = firstFrame
            try {
                val result =
                    handle.extractRange(firstFrame, frameCount.toLong()) { first, bytes ->
                        checkCancellation(cancelled)
                        require(
                            first == next &&
                                bytes.isNotEmpty() &&
                                bytes.size % PCM_SAMPLE_BYTES == 0
                        )
                        val count = bytes.size / PCM_SAMPLE_BYTES
                        require(count <= firstFrame + frameCount - next)
                        for (sample in 0 until count) {
                            val index = sample * PCM_SAMPLE_BYTES
                            val value =
                                (bytes[index].toInt() and UNSIGNED_BYTE_MASK) or
                                    (bytes[index + 1].toInt() shl Byte.SIZE_BITS)
                            owned.samples[(next - firstFrame).toInt() + sample] =
                                value.toShort() / PCM_NORMALIZATION
                        }
                        next += count
                    }
                checkCancellation(cancelled)
                check(
                    result is AudioResult.Value &&
                        result.value == OriginalAudioStatus.Available(source)
                )
                check(next == firstFrame + frameCount)
                return owned
            } catch (error: Throwable) {
                owned.close()
                throw error
            }
        }

        @Suppress(
            "TooGenericExceptionCaught"
        ) // Wipe owned samples for every failure, including native errors.
        fun read(
            port: OriginalAudioPort,
            source: OriginalAudioReference,
            cancelled: () -> Boolean,
        ): ProtectedPcm {
            require(source.frames in 1..MAX_WINDOW_FRAMES.toLong())
            return readWindow(port, source, 0, source.frames.toInt(), cancelled)
        }

        /** Authenticate the complete source, retain only this window, then release the vault. */
        @Suppress("TooGenericExceptionCaught", "LongMethod")
        fun readWindow(
            port: OriginalAudioPort,
            source: OriginalAudioReference,
            firstFrame: Long,
            frameCount: Int,
            cancelled: () -> Boolean,
        ): ProtectedPcm {
            require(source.frames in 1..AlphaRecordingLimit.MAX_ALPHA_FRAMES)
            require(firstFrame >= 0 && frameCount in 1..MAX_WINDOW_FRAMES)
            require(firstFrame <= source.frames - frameCount)
            checkCancellation(cancelled)
            val owned = ProtectedPcm(FloatArray(frameCount))
            var next = 0L
            try {
                val result =
                    port.extract(source) { first, bytes ->
                        checkCancellation(cancelled)
                        require(
                            first == next &&
                                bytes.isNotEmpty() &&
                                bytes.size % PCM_SAMPLE_BYTES == 0
                        )
                        require(bytes.size / PCM_SAMPLE_BYTES.toLong() <= source.frames - next)
                        val count = bytes.size / PCM_SAMPLE_BYTES
                        val begin =
                            maxOf(0L, firstFrame - first).coerceAtMost(count.toLong()).toInt()
                        val end =
                            minOf(count.toLong(), firstFrame + frameCount - first)
                                .coerceAtLeast(0)
                                .toInt()
                        for (sample in begin until end) {
                            val index = sample * PCM_SAMPLE_BYTES
                            val value =
                                (bytes[index].toInt() and UNSIGNED_BYTE_MASK) or
                                    (bytes[index + 1].toInt() shl Byte.SIZE_BITS)
                            owned.samples[(first + sample - firstFrame).toInt()] =
                                value.toShort() / PCM_NORMALIZATION
                        }
                        next += count
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
