package com.monumentogram.dora.audio.transcript

import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AuthenticatedAudioSource
import com.monumentogram.dora.audio.OriginalAudioPort
import com.monumentogram.dora.audio.OriginalAudioReference
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** One owned consumer window plus one prefetched window; never the complete long source. */
@Suppress("TooGenericExceptionCaught") // Every failed handoff must wipe owned PCM.
class PcmWindows(
    private val handle: AuthenticatedAudioSource,
    firstFrame: Long,
    private val cancelled: () -> Boolean,
) : AutoCloseable {
    constructor(
        port: OriginalAudioPort,
        source: OriginalAudioReference,
        firstFrame: Long,
        cancelled: () -> Boolean,
    ) : this(open(port, source), firstFrame, cancelled)

    private val source = handle.reference
    private val loader = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false
    private var position = firstFrame
    private var pending: Future<ProtectedPcm>? = null

    init {
        require(firstFrame in 0..source.frames)
        pending = schedule()
    }

    private fun schedule(): Future<ProtectedPcm>? {
        if (position == source.frames) return null
        val first = position
        val count = minOf(source.frames - first, ProtectedPcm.MAX_WINDOW_FRAMES.toLong()).toInt()
        return loader.submit<ProtectedPcm> {
            ProtectedPcm.readWindow(handle, first, count) { closed || cancelled() }
        }
    }

    /** Caller must close each returned window before requesting another. */
    fun next(): ProtectedPcm? {
        check(!closed)
        val window = pending?.get() ?: return null
        pending = null
        position += window.samples.size
        try {
            pending = schedule()
            return window
        } catch (error: Exception) {
            window.close()
            throw error
        }
    }

    override fun close() {
        closed = true
        var interrupted = false
        try {
            // Do not cancel a completed Future: its plaintext result must still be wiped.
            var waiting = true
            while (waiting) {
                try {
                    pending?.get()?.close()
                    waiting = false
                } catch (_: InterruptedException) {
                    interrupted = true
                } catch (_: java.util.concurrent.ExecutionException) {
                    waiting = false // ProtectedPcm wipes before propagating extraction failure.
                }
            }
        } finally {
            loader.shutdown()
            pending = null
            handle.close()
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    companion object {
        private fun open(
            port: OriginalAudioPort,
            reference: OriginalAudioReference,
        ): AuthenticatedAudioSource {
            val result = port.openAuthenticated(reference)
            check(result is AudioResult.Value) { "SOURCE_UNAVAILABLE" }
            return result.value
        }
    }
}
