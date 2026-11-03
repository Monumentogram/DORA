package com.monumentogram.dora.audio

/** Immutable provenance, not permission to access audio. Keep inside encrypted storage. */
data class OriginalAudioReference(
    val version: Int,
    val identity: AudioIdentity,
    val digest: String,
    val frames: Long,
) {
    override fun toString(): String = "OriginalAudioReference(redacted)"
}

sealed interface OriginalAudioStatus {
    data class Available(val reference: OriginalAudioReference) : OriginalAudioStatus

    data object NotFinalized : OriginalAudioStatus

    data object DeletionPending : OriginalAudioStatus

    data object SourceDeleted : OriginalAudioStatus

    data class SourceUnavailable(val reason: AudioFailure) : OriginalAudioStatus

    data object StaleReference : OriginalAudioStatus
}

/**
 * Local-only exact source boundary. LOCKED, KEY_UNAVAILABLE, BUSY and UNCERTAIN are transient
 * AudioResult failures, never deletion. Only Available admits audio-dependent work.
 *
 * Future work must store the exact reference in its own admitted encrypted journal. Register,
 * dispatch, retry and accept a late result through withAvailable; never cache its success as
 * permission. That callback is serialized with deletion and authorization revocation. It must be
 * bounded, synchronous, and must not reenter audio ports or wait for another thread. It may commit
 * a dependent local decision; it cannot create source authority. Remote work/consent and remote
 * deletion receipts require separate future boundaries.
 *
 * extract borrows bounded PCM arrays with the same zeroization and failed-attempt rules as the
 * product reader. Reprocessing takes a reference, never transcript text. No method deletes text.
 */
interface OriginalAudioPort {
    /** Discover provenance and authenticate once when the caller has only a recording identity. */
    fun openAuthenticated(identity: AudioIdentity): AudioResult<AuthenticatedAudioSource> =
        AudioResult.Failed(AudioFailure.UNAVAILABLE)

    /** Cooperative cancellation never grants partial-source authority or retains source loss. */
    fun openAuthenticated(
        identity: AudioIdentity,
        cancelled: () -> Boolean,
    ): AudioResult<AuthenticatedAudioSource> =
        cancellableAudioOpen(cancelled) { openAuthenticated(identity) }

    /**
     * Authenticate the whole immutable source once for this attempt; close on cancellation/resume.
     */
    fun openAuthenticated(
        reference: OriginalAudioReference
    ): AudioResult<AuthenticatedAudioSource> = AudioResult.Failed(AudioFailure.UNAVAILABLE)

    fun openAuthenticated(
        reference: OriginalAudioReference,
        cancelled: () -> Boolean,
    ): AudioResult<AuthenticatedAudioSource> =
        cancellableAudioOpen(cancelled) { openAuthenticated(reference) }

    fun acquire(identity: AudioIdentity): AudioResult<OriginalAudioStatus>

    fun inspect(reference: OriginalAudioReference): AudioResult<OriginalAudioStatus>

    fun extract(
        reference: OriginalAudioReference,
        consume: (firstFrame: Long, pcm: ByteArray) -> Unit,
    ): AudioResult<OriginalAudioStatus>

    fun withAvailable(
        reference: OriginalAudioReference,
        action: () -> Unit,
    ): AudioResult<OriginalAudioStatus>
}

/**
 * Process-local verified snapshot, never persisted permission. Each range rechecks live authority,
 * deletion and exact source metadata, then authenticates the selected encrypted units. Callbacks
 * borrow bounded PCM; callers must discard the entire range on failure and never do slow work in
 * the callback. Offsets are global source frames. Closing retires this attempt's metadata snapshot
 * and must be safe from any thread; it does not close a vault or own outstanding PCM callbacks.
 */
interface AuthenticatedAudioSource : AutoCloseable {
    val reference: OriginalAudioReference

    /** Cheap live authority/deletion/exact-source fence; authenticates no additional PCM. */
    fun checkAvailable(): AudioResult<OriginalAudioStatus> =
        AudioResult.Failed(AudioFailure.UNAVAILABLE)

    fun extractRange(
        firstFrame: Long,
        frameCount: Long,
        consume: (firstFrame: Long, pcm: ByteArray) -> Unit,
    ): AudioResult<OriginalAudioStatus>
}

/** Own a late successful result until its caller's cancellation fence accepts the handoff. */
internal fun cancellableAudioOpen(
    cancelled: () -> Boolean,
    open: () -> AudioResult<AuthenticatedAudioSource>,
): AudioResult<AuthenticatedAudioSource> {
    if (cancelled()) return AudioResult.Failed(AudioFailure.UNCERTAIN)
    val opened = open()
    var accepted = false
    return try {
        accepted = !cancelled()
        if (accepted) opened else AudioResult.Failed(AudioFailure.UNCERTAIN)
    } finally {
        if (!accepted && opened is AudioResult.Value) opened.value.close()
    }
}
