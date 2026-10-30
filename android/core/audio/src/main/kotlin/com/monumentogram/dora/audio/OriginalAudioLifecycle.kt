package com.monumentogram.dora.audio

import com.monumentogram.dora.audio.persistence.auth.LocalAccessDeniedException
import com.monumentogram.dora.audio.persistence.journal.UncertainAudioSourceState

/** Product-owned policy; storage callbacks require this operation's exact catalog lease. */
@Suppress("LongParameterList", "ReturnCount", "TooManyFunctions")
internal class OriginalAudioLifecycle(
    private val owner: String,
    private val vault: String,
    private val catalog: EncryptedAudioCatalog,
    private val sourceState: (AudioIdentity) -> AudioSourceState?,
    private val retainedLoss: (OriginalAudioReference) -> AudioFailure?,
    private val retain: (OriginalAudioReference, AudioFailure?) -> Unit,
    private val readHeld:
        (StoredAudioAsset, (Long, ByteArray) -> Unit) -> AudioResult<AudioReadSummary>,
    private val requireActive: () -> Unit,
    private val deliverAuthorized: (() -> Unit) -> Unit,
    private val readRangeHeld:
        ((StoredAudioAsset, Long, Long, (Long, ByteArray) -> Unit) -> AudioResult<
                AudioReadSummary
            >)? =
        null,
) : OriginalAudioPort {
    private val verifiedSources = java.util.concurrent.ConcurrentHashMap.newKeySet<VerifiedSource>()

    override fun acquire(identity: AudioIdentity) = operate(identity, null)

    override fun inspect(reference: OriginalAudioReference) = operate(reference.identity, reference)

    override fun extract(reference: OriginalAudioReference, consume: (Long, ByteArray) -> Unit) =
        operate(reference.identity, reference, consume)

    override fun withAvailable(reference: OriginalAudioReference, action: () -> Unit) =
        operate(reference.identity, reference, action = action)

    override fun openAuthenticated(
        reference: OriginalAudioReference
    ): AudioResult<AuthenticatedAudioSource> = openVerified(reference.identity, reference)

    override fun openAuthenticated(identity: AudioIdentity): AudioResult<AuthenticatedAudioSource> =
        openVerified(identity, null)

    private fun openVerified(
        identity: AudioIdentity,
        expected: OriginalAudioReference?,
    ): AudioResult<AuthenticatedAudioSource> = guarded {
        requireActive()
        val lease =
            catalog.tryAcquire(identity) ?: return@guarded AudioResult.Failed(AudioFailure.BUSY)
        lease.use {
            terminalState(identity)?.let {
                return@guarded unavailableHandle(it)
            }
            val loaded =
                catalog.load(identity) ?: return@guarded AudioResult.Failed(AudioFailure.UNCERTAIN)
            if (loaded.pending != null || loaded.finalization == null)
                return@guarded AudioResult.Failed(AudioFailure.INCOMPLETE)
            val reference = OriginalAudioReferenceCodec.derive(owner, vault, loaded)
            if (expected != null && reference != expected)
                return@guarded AudioResult.Failed(AudioFailure.INVALID_INPUT)
            retainedLoss(reference)?.let {
                return@guarded AudioResult.Failed(it)
            }
            val read = readHeld(loaded) { _, _ -> requireActive() }
            requireActive()
            if (read is AudioResult.Failed) {
                failedRead(reference, read.reason)
                return@guarded read
            }
            if (
                (read as AudioResult.Value).value !=
                    AudioReadSummary(
                        reference.identity,
                        reference.frames,
                        AudioCompletion.FINALIZED,
                    )
            )
                return@guarded AudioResult.Failed(AudioFailure.INCOMPLETE)
            retain(reference, null)
            requireActive()
            // Catalog snapshots contain immutable metadata only, never PCM or key material.
            AudioResult.Value(VerifiedSource(reference, loaded).also { verifiedSources.add(it) })
        }
    }

    /** New paged publication boundary; legacy withAvailable retains its full reauthentication. */
    internal fun withAuthenticatedAvailable(
        reference: OriginalAudioReference,
        action: () -> Unit,
    ): AudioResult<OriginalAudioStatus> {
        val handle =
            verifiedSources.firstOrNull { it.reference == reference && it.snapshot != null }
                ?: return withAvailable(reference, action)
        return withVerifiedAvailable(handle, action)
    }

    private fun withVerifiedAvailable(
        handle: VerifiedSource,
        action: () -> Unit,
    ): AudioResult<OriginalAudioStatus> {
        val reference = handle.reference
        return guarded {
            requireActive()
            val snapshot =
                handle.snapshot ?: return@guarded AudioResult.Failed(AudioFailure.UNAVAILABLE)
            val lease =
                catalog.tryAcquire(reference.identity)
                    ?: return@guarded AudioResult.Failed(AudioFailure.BUSY)
            lease.use {
                terminalState(reference.identity)?.let {
                    return@guarded it
                }
                if (catalog.load(reference.identity) != snapshot)
                    return@guarded value(OriginalAudioStatus.StaleReference)
                retainedLoss(reference)?.let {
                    return@guarded value(OriginalAudioStatus.SourceUnavailable(it))
                }
                deliverAuthorized {
                    requireActive()
                    check(handle.snapshot === snapshot)
                    action()
                }
                requireActive()
                value(OriginalAudioStatus.Available(reference))
            }
        }
    }

    private fun unavailableHandle(result: AudioResult<OriginalAudioStatus>): AudioResult.Failed =
        when (result) {
            is AudioResult.Failed -> result
            is AudioResult.Value ->
                AudioResult.Failed(
                    (result.value as? OriginalAudioStatus.SourceUnavailable)?.reason
                        ?: AudioFailure.UNAVAILABLE
                )
        }

    private inner class VerifiedSource(
        override val reference: OriginalAudioReference,
        snapshot: StoredAudioAsset,
    ) : AuthenticatedAudioSource {
        @Volatile
        var snapshot: StoredAudioAsset? = snapshot
            private set

        override fun checkAvailable(): AudioResult<OriginalAudioStatus> =
            withVerifiedAvailable(this) {}

        override fun close() {
            snapshot = null
            verifiedSources.remove(this)
        }

        override fun extractRange(
            firstFrame: Long,
            frameCount: Long,
            consume: (Long, ByteArray) -> Unit,
        ): AudioResult<OriginalAudioStatus> = guarded {
            requireActive()
            val snapshot = snapshot ?: return@guarded AudioResult.Failed(AudioFailure.UNAVAILABLE)
            if (firstFrame < 0 || frameCount <= 0 || firstFrame > reference.frames - frameCount)
                return@guarded AudioResult.Failed(AudioFailure.INVALID_INPUT)
            val lease =
                catalog.tryAcquire(reference.identity)
                    ?: return@guarded AudioResult.Failed(AudioFailure.BUSY)
            lease.use {
                terminalState(reference.identity)?.let {
                    return@guarded it
                }
                if (catalog.load(reference.identity) != snapshot)
                    return@guarded value(OriginalAudioStatus.StaleReference)
                retainedLoss(reference)?.let {
                    return@guarded value(OriginalAudioStatus.SourceUnavailable(it))
                }
                val reader =
                    readRangeHeld ?: return@guarded AudioResult.Failed(AudioFailure.UNAVAILABLE)
                val result =
                    reader(snapshot, firstFrame, frameCount) { first, bytes ->
                        deliverAuthorized {
                            requireActive()
                            check(this.snapshot === snapshot)
                            consume(first, bytes)
                        }
                    }
                requireActive()
                if (this.snapshot !== snapshot)
                    return@guarded AudioResult.Failed(AudioFailure.UNAVAILABLE)
                if (result is AudioResult.Failed)
                    return@guarded failedRead(reference, result.reason)
                if (
                    (result as AudioResult.Value).value !=
                        AudioReadSummary(reference.identity, frameCount, AudioCompletion.FINALIZED)
                )
                    return@guarded failedRead(reference, AudioFailure.INCOMPLETE)
                value(OriginalAudioStatus.Available(reference))
            }
        }
    }

    private fun operate(
        identity: AudioIdentity,
        expected: OriginalAudioReference?,
        consume: (Long, ByteArray) -> Unit = { _, _ -> },
        action: () -> Unit = {},
    ): AudioResult<OriginalAudioStatus> = guarded {
        requireActive()
        val lease =
            catalog.tryAcquire(identity) ?: return@guarded AudioResult.Failed(AudioFailure.BUSY)
        lease.use {
            terminalState(identity)?.let {
                return@guarded it
            }
            val asset =
                catalog.load(identity) ?: return@guarded AudioResult.Failed(AudioFailure.UNCERTAIN)
            if (asset.pending != null) return@guarded AudioResult.Failed(AudioFailure.UNCERTAIN)
            if (asset.finalization == null) return@guarded value(OriginalAudioStatus.NotFinalized)
            val reference = OriginalAudioReferenceCodec.derive(owner, vault, asset)
            if (expected != null && reference != expected)
                return@guarded value(OriginalAudioStatus.StaleReference)
            retainedLoss(reference)?.let {
                return@guarded value(OriginalAudioStatus.SourceUnavailable(it))
            }
            val read =
                readHeld(asset) { frame, bytes ->
                    deliverAuthorized {
                        requireActive()
                        consume(frame, bytes)
                    }
                }
            requireActive()
            if (read is AudioResult.Failed) return@guarded failedRead(reference, read.reason)
            val summary = (read as AudioResult.Value).value
            if (summary != AudioReadSummary(identity, reference.frames, AudioCompletion.FINALIZED))
                return@guarded failedRead(reference, AudioFailure.INCOMPLETE)
            retain(reference, null)
            deliverAuthorized {
                requireActive()
                action()
            }
            requireActive()
            value(OriginalAudioStatus.Available(reference))
        }
    }

    private fun terminalState(identity: AudioIdentity): AudioResult<OriginalAudioStatus>? =
        when (val state = sourceState(identity)) {
            null -> null
            AudioSourceState.UserDeleted -> value(OriginalAudioStatus.SourceDeleted)
            is AudioSourceState.Deleting -> value(OriginalAudioStatus.DeletionPending)
            AudioSourceState.Missing ->
                value(OriginalAudioStatus.SourceUnavailable(AudioFailure.UNAVAILABLE))
            is AudioSourceState.Unavailable -> AudioResult.Failed(state.reason)
            is AudioSourceState.Readable -> AudioResult.Failed(AudioFailure.UNCERTAIN)
        }

    private fun failedRead(
        reference: OriginalAudioReference,
        reason: AudioFailure,
    ): AudioResult<OriginalAudioStatus> {
        if (reason !in permanentFailures) return AudioResult.Failed(reason)
        retain(reference, reason)
        return value(OriginalAudioStatus.SourceUnavailable(reason))
    }

    private fun value(status: OriginalAudioStatus) = AudioResult.Value(status)

    @Suppress("TooGenericExceptionCaught")
    private fun <T> guarded(block: () -> AudioResult<T>): AudioResult<T> =
        try {
            block()
        } catch (_: LocalAccessDeniedException) {
            AudioResult.Failed(AudioFailure.LOCKED)
        } catch (_: UncertainAudioSourceState) {
            AudioResult.Failed(AudioFailure.UNCERTAIN)
        } catch (_: Exception) {
            AudioResult.Failed(AudioFailure.UNAVAILABLE)
        }

    companion object {
        val permanentFailures = setOf(AudioFailure.KEY_INVALIDATED, AudioFailure.CORRUPT)
    }
}
