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
    LOCKED,
    CANCELLED,
    CREDENTIAL_SETUP_REQUIRED,
    UNAVAILABLE,
    INVALID_INPUT,
    COLLISION,
    KEY_UNAVAILABLE,
    KEY_INVALIDATED,
    AUTHENTICATION_FAILED,
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
    /** Derived metadata failure never grants permission to discard canonical PCM. */
    fun segmentation(identity: AudioIdentity, metadata: SegmentationMetadata): AudioResult<Unit> =
        AudioResult.Failed(AudioFailure.UNAVAILABLE)

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
    /** Bounded source views; empty means NOT_EVALUATED for historical recordings. */
    fun segmentation(
        identity: AudioIdentity,
        afterKey: String = "",
    ): AudioResult<List<SegmentationMetadata>> = AudioResult.Failed(AudioFailure.UNAVAILABLE)

    fun extract(
        identity: AudioIdentity,
        consume: (firstFrame: Long, pcm: ByteArray) -> Unit,
    ): AudioResult<AudioReadSummary>
}

sealed interface AudioAvailability {
    data object Locked : AudioAvailability

    data object Opening : AudioAvailability

    data class Available(val session: ProductAudioSession) : AudioAvailability

    data class Failed(val reason: AudioFailure) : AudioAvailability
}

enum class AudioOpenMode {
    CREATE_NEW,
    OPEN_EXISTING,
}

/** Describes only the vault wrapping key, not every audio key or the entire device. */
enum class VaultKeyProtection {
    STRONGBOX,
    TRUSTED_ENVIRONMENT,
    HARDWARE_BACKED_UNSPECIFIED,
    SOFTWARE,
}

sealed interface AudioSourceState {
    data class Readable(val summary: AudioReadSummary) : AudioSourceState

    data object Missing : AudioSourceState

    data object UserDeleted : AudioSourceState

    data class Deleting(val remainingCategories: Set<AudioDeletionCategory>) : AudioSourceState

    data class Unavailable(val reason: AudioFailure) : AudioSourceState
}

enum class AudioDeletionCategory {
    KEY_MATERIAL,
    AUDIO_FILES,
    JOURNAL_COMPLETION,
}

/**
 * Generation-bound access. Submitted calls wait through caller interruption until worker access to
 * input and callbacks ends. The interrupt flag is restored; interrupted success returns UNCERTAIN.
 * Synchronous methods require a background caller; main-thread calls and reentry from a borrowed
 * PCM callback return BUSY. PCM callbacks execute on the persistence worker, must be bounded, and
 * must never synchronously wait for main-thread work. Do not retain or post the borrowed array. Any
 * failed extraction invalidates the whole attempt, including earlier callbacks.
 */
interface ProductAudioSession {
    val logicalRecordings: com.monumentogram.dora.audio.logical.LogicalRecordingPort
        get() =
            com.monumentogram.dora.audio.logical.LogicalRecordingPort {
                AudioResult.Failed(AudioFailure.UNAVAILABLE)
            }

    val originals: OriginalAudioPort
    val writer: ProductAudioWriterPort
    val reader: ProductAudioReaderPort
    val protection: VaultKeyProtection
    val protectionDisclosure: String
        get() =
            "Vault wrapping key protection: " +
                when (protection) {
                    VaultKeyProtection.STRONGBOX -> "StrongBox"
                    VaultKeyProtection.TRUSTED_ENVIRONMENT -> "Trusted execution environment"
                    VaultKeyProtection.HARDWARE_BACKED_UNSPECIFIED ->
                        "Hardware-backed; type unspecified"
                    VaultKeyProtection.SOFTWARE -> "Software-backed Android Keystore"
                }

    fun sourceState(identity: AudioIdentity): AudioResult<AudioSourceState>

    /** Resumes only an already durably confirmed exact audio deletion; never starts one. */
    fun retryRemainingDeletion(identity: AudioIdentity): AudioResult<Unit>
}

interface ProductAudioRuntime {
    val availability: AudioAvailability
}
