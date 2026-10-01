package com.monumentogram.dora.audio

import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId

data class AudioFormat(val encoding: String, val sampleRateHz: Int, val channels: Int) {
    fun requireSupported() {
        require(this == PCM) { "Unsupported product audio format" }
    }

    companion object {
        val PCM = AudioFormat("PCM_S16LE", 16_000, 1)
    }
}

object AudioTimeline {
    private const val SAMPLE_RATE = 16_000
    private const val MICROS_PER_SECOND = 1_000_000

    fun frames(byteCount: Int): Long {
        require(byteCount > 0 && byteCount % 2 == 0) { "Incomplete PCM frame" }
        return byteCount.toLong() / 2
    }

    fun nextFrame(start: Long, count: Long): Long {
        require(start >= 0 && count >= 0) { "Negative frame position" }
        return Math.addExact(start, count)
    }

    fun durationUs(frameCount: Long): Long {
        require(frameCount >= 0) { "Negative frame count" }
        return Math.addExact(
            Math.multiplyExact(frameCount / SAMPLE_RATE, MICROS_PER_SECOND.toLong()),
            (frameCount % SAMPLE_RATE) * MICROS_PER_SECOND / SAMPLE_RATE,
        )
    }
}

data class AudioIdentity(
    val recordingId: RecordingId,
    val assetId: AudioAssetId,
    val sessionId: String,
)

data class AudioStorageUnitIdentity(
    val audio: AudioIdentity,
    val unitId: String,
    val ordinal: Int,
    val firstFrame: Long,
    val physicalSegmentId: String = unitId,
    val physicalFirstFrame: Long = firstFrame,
    val sourceFrameOffset: Long = 0,
)

enum class AudioFailure {
    UNAVAILABLE,
    INVALID_INPUT,
    COLLISION,
    KEY_UNAVAILABLE,
    KEY_INVALIDATED,
    INCOMPLETE,
    CORRUPT,
    BUSY,
    UNCERTAIN,
}

sealed interface AudioResult<out T> {
    data class Value<T>(val value: T) : AudioResult<T>

    data class Failed(val reason: AudioFailure) : AudioResult<Nothing>
}

enum class AudioCompletion {
    FINALIZED,
    PARTIAL_RECOVERED,
}

data class AudioReadSummary(
    val identity: AudioIdentity,
    val frames: Long,
    val completion: AudioCompletion,
    val tailFailure: AudioFailure? = null,
) {
    val durationUs: Long
        get() = AudioTimeline.durationUs(frames)
}

/**
 * Each bounded storage unit is explicit; capture segment rotation/overlap is a separate concern.
 */
interface ProductAudioWriterPort {
    fun create(identity: AudioIdentity): AudioResult<Unit>

    fun append(
        segment: AudioStorageUnitIdentity,
        format: AudioFormat,
        pcm: ByteArray,
    ): AudioResult<Unit>

    fun finalize(identity: AudioIdentity): AudioResult<Unit>

    fun reconcile(identity: AudioIdentity): AudioResult<Unit>
}

/**
 * PCM is delivered only after authentication. Each array is borrowed during the callback and zeroed
 * immediately afterwards. A failed extraction invalidates the overall result even if earlier
 * authenticated units were delivered. Receivers must not persist plaintext.
 */
interface ProductAudioReaderPort {
    fun extract(
        identity: AudioIdentity,
        consume: (firstFrame: Long, pcm: ByteArray) -> Unit,
    ): AudioResult<AudioReadSummary>
}

sealed interface AudioAvailability {
    data object RequiresEncryptedPersistence : AudioAvailability
}

/** Stage 8.2 must admit an encrypted composition before product persistence can be obtained. */
object ProductAudioRuntime {
    val availability: AudioAvailability = AudioAvailability.RequiresEncryptedPersistence
}
