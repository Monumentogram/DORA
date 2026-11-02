package com.monumentogram.dora.transcript

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AuthenticatedAudioSource
import com.monumentogram.dora.audio.OriginalAudioStatus

/**
 * Single playback-worker owner. Retains authenticated metadata, never PCM or a held source lease.
 */
internal class PlaybackSourceCache : AutoCloseable {
    private var retained: AuthenticatedAudioSource? = null

    /** Null requests a fresh explicit open; a failed live check must fail this playback attempt. */
    fun reuse(identity: AudioIdentity): AudioResult<AuthenticatedAudioSource>? {
        val source = retained
        return when {
            source == null -> null
            source.reference.identity != identity -> {
                close()
                null
            }
            else -> checked(source)
        }
    }

    fun remember(
        identity: AudioIdentity,
        opened: AudioResult<AuthenticatedAudioSource>,
    ): AudioResult<AuthenticatedAudioSource> {
        close()
        return when (opened) {
            is AudioResult.Failed -> opened
            is AudioResult.Value -> {
                val source = opened.value
                retained = source
                if (source.reference.identity != identity) {
                    close()
                    AudioResult.Failed(AudioFailure.CORRUPT)
                } else checked(source)
            }
        }
    }

    private fun checked(source: AuthenticatedAudioSource): AudioResult<AuthenticatedAudioSource> {
        val checked = source.checkAvailable()
        val status = (checked as? AudioResult.Value)?.value
        if (status is OriginalAudioStatus.Available && status.reference == source.reference) {
            // PcmWindows closes this borrowed view after draining/wiping its own windows.
            return AudioResult.Value(
                object : AuthenticatedAudioSource {
                    override val reference = source.reference

                    override fun checkAvailable() = source.checkAvailable()

                    override fun extractRange(
                        firstFrame: Long,
                        frameCount: Long,
                        consume: (Long, ByteArray) -> Unit,
                    ) = source.extractRange(firstFrame, frameCount, consume)

                    override fun close() = Unit
                }
            )
        }
        close()
        return if (checked is AudioResult.Failed) checked
        else AudioResult.Failed(AudioFailure.UNAVAILABLE)
    }

    override fun close() {
        val previous = retained
        retained = null
        previous?.close()
    }
}
