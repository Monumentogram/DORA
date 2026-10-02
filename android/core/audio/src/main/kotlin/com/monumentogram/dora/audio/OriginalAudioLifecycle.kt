package com.monumentogram.dora.audio

import com.monumentogram.dora.audio.persistence.auth.AppLockedException
import com.monumentogram.dora.audio.persistence.journal.UncertainAudioSourceState

/** Product-owned policy; storage callbacks require this operation's exact catalog lease. */
@Suppress("LongParameterList", "ReturnCount")
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
) : OriginalAudioPort {
    override fun acquire(identity: AudioIdentity) = operate(identity, null)

    override fun inspect(reference: OriginalAudioReference) = operate(reference.identity, reference)

    override fun extract(reference: OriginalAudioReference, consume: (Long, ByteArray) -> Unit) =
        operate(reference.identity, reference, consume)

    override fun withAvailable(reference: OriginalAudioReference, action: () -> Unit) =
        operate(reference.identity, reference, action = action)

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
    private fun guarded(
        block: () -> AudioResult<OriginalAudioStatus>
    ): AudioResult<OriginalAudioStatus> =
        try {
            block()
        } catch (_: AppLockedException) {
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
