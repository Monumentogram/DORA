@file:Suppress("ReturnCount") // Identity and authority rejection precede all storage work.

package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.audio.persistence.auth.AppLockedException
import com.monumentogram.dora.audio.persistence.runtime.RuntimeVault

/**
 * Process-local, exact-source writer; no read/delete/export/catalog authority. Runtime constructs
 * it only after real system authentication. All writer calls and close share its monitor, so close
 * cannot race a borrowed write. The caller must be a background persistence worker.
 */
class RecordingAccess
internal constructor(
    val identity: AudioIdentity,
    private val authority: RecordingAuthority,
    private val vault: RuntimeVault,
    private val isMainThread: () -> Boolean,
    val continuation: RecordingRecovery? = null,
    private val release: (RuntimeVault) -> Unit,
) : AutoCloseable {
    private var closed = false
    private var created = continuation != null
    private var finalized = false

    /** Consumed only at the visible, authenticated initial microphone-start boundary. */
    fun activate(start: () -> Unit) = authority.activate(start)

    fun onRevocation(listener: () -> Unit) = authority.onRevocation(listener)

    internal fun resumeGrant(boundary: (() -> Unit) -> Unit) = RecordingResumeGrant { start ->
        authority.resume(boundary, start)
    }

    internal fun revoke() = authority.revoke()

    val writer: ProductAudioWriterPort =
        object : ProductAudioWriterPort {
            override fun segmentation(identity: AudioIdentity, metadata: SegmentationMetadata) =
                operation(identity) {
                    if (!created) AudioResult.Failed(AudioFailure.COLLISION)
                    else vault.writer.segmentation(identity, metadata)
                }

            override fun create(identity: AudioIdentity) =
                operation(identity) {
                    if (created) AudioResult.Failed(AudioFailure.COLLISION)
                    else {
                        created = true
                        vault.writer.create(identity)
                    }
                }

            override fun append(
                segment: AudioStorageUnitIdentity,
                format: AudioFormat,
                pcm: ByteArray,
            ) =
                operation(segment.audio) {
                    if (!created || finalized) AudioResult.Failed(AudioFailure.COLLISION)
                    else vault.writer.append(segment, format, pcm)
                }

            override fun finalize(identity: AudioIdentity) =
                operation(identity) {
                    if (!created || finalized) AudioResult.Failed(AudioFailure.COLLISION)
                    else {
                        finalized = true
                        vault.writer.finalize(identity)
                    }
                }

            override fun reconcile(identity: AudioIdentity) =
                operation(identity) { vault.writer.reconcile(identity) }
        }

    @Synchronized
    private fun operation(
        target: AudioIdentity,
        action: () -> AudioResult<Unit>,
    ): AudioResult<Unit> {
        if (isMainThread()) return AudioResult.Failed(AudioFailure.BUSY)
        if (target != identity) return AudioResult.Failed(AudioFailure.INVALID_INPUT)
        return try {
            if (closed) throw AppLockedException()
            authority.requireWriter()
            action().also { authority.requireWriter() }
        } catch (_: AppLockedException) {
            AudioResult.Failed(AudioFailure.LOCKED)
        } catch (_: Exception) {
            AudioResult.Failed(AudioFailure.UNAVAILABLE)
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        authority.revoke()
        release(vault)
    }
}
