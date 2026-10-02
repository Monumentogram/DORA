@file:Suppress(
    "ReturnCount",
    "TooManyFunctions",
) // Early exits keep terminal-state and durability fences explicit.

package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.AudioTimeline
import com.monumentogram.dora.audio.ProductAudioWriterPort
import java.util.UUID

enum class RecordingPhase {
    PREFLIGHT,
    PREPARING,
    RECORDING,
    PAUSED,
    FINALIZING,
    SAVED,
    EMPTY,
    INTERRUPTED,
}

/** Content-free state. Signal is supplied separately by the Android capture adapter. */
data class RecordingState(
    val phase: RecordingPhase = RecordingPhase.PREFLIGHT,
    val frames: Long = 0,
    val durableFrames: Long = 0,
    val stopConfirmation: Boolean = false,
    val persistenceFailure: AudioFailure? = null,
) {
    val durationUs: Long
        get() = AudioTimeline.durationUs(frames)
}

/**
 * Single persistence-worker owner. The service drains the bounded capture queue before pause or
 * stop; calls here never run on the AudioRecord thread or on main. Confirmation is orthogonal to
 * capture, so audio continues until the service receives explicit confirmation and releases mic.
 */
class RecordingSession(
    val identity: AudioIdentity,
    private val writer: ProductAudioWriterPort,
) {
    var state = RecordingState()
        private set

    private val pending = ByteArray(TRANSPORT_BYTES)
    private var pendingBytes = 0
    private var ordinal = 0
    private var physicalId = freshId()
    private var physicalStart = 0L
    var maximumAppendNanos: Long = 0
        private set

    /** Called only with the runtime's reauthenticated, pending-free continuation point. */
    fun restore(frames: Long, nextOrdinal: Int) {
        check(state.phase == RecordingPhase.PREFLIGHT)
        require(frames >= 0 && nextOrdinal >= 0)
        ordinal = nextOrdinal
        physicalStart = frames
        state = RecordingState(RecordingPhase.PAUSED, frames, frames)
    }

    fun start(): Boolean {
        if (state.phase != RecordingPhase.PREFLIGHT) return false
        state = state.copy(phase = RecordingPhase.PREPARING)
        if (!checkResult(writer.create(identity))) return false
        state = state.copy(phase = RecordingPhase.RECORDING)
        return true
    }

    /** Input is borrowed; the caller always clears its own array after return. */
    fun accept(pcm: ByteArray) {
        if (state.phase != RecordingPhase.RECORDING) return
        val frames = AudioTimeline.frames(pcm.size)
        state = state.copy(frames = AudioTimeline.nextFrame(state.frames, frames))
        var offset = 0
        while (offset < pcm.size && state.phase == RecordingPhase.RECORDING) {
            val count = minOf(pcm.size - offset, pending.size - pendingBytes)
            pcm.copyInto(pending, pendingBytes, offset, offset + count)
            pendingBytes += count
            offset += count
            if (pendingBytes == pending.size) flush()
        }
    }

    fun pause() {
        if (state.phase != RecordingPhase.RECORDING) return
        if (flush()) state = state.copy(phase = RecordingPhase.PAUSED)
    }

    fun resume(): Boolean {
        if (state.phase != RecordingPhase.PAUSED) return false
        physicalId = freshId()
        physicalStart = state.frames
        state = state.copy(phase = RecordingPhase.RECORDING)
        return true
    }

    fun requestStop() {
        if (state.phase == RecordingPhase.RECORDING || state.phase == RecordingPhase.PAUSED)
            state = state.copy(stopConfirmation = true)
    }

    fun cancelStop() {
        state = state.copy(stopConfirmation = false)
    }

    fun confirmStop() {
        if (!state.stopConfirmation || state.phase !in ACTIVE_PHASES) return
        state = state.copy(phase = RecordingPhase.FINALIZING, stopConfirmation = false)
        if (!flush()) return
        if (state.frames == 0L) {
            state = state.copy(phase = RecordingPhase.EMPTY)
            return
        }
        // Exactly one finalize call. UNCERTAIN remains interrupted until authenticated recovery.
        if (checkResult(writer.finalize(identity))) state = state.copy(phase = RecordingPhase.SAVED)
    }

    /** Keeps the committed prefix and never converts abnormal termination into successful Stop. */
    fun interrupt(reason: AudioFailure? = null) {
        pending.fill(0)
        pendingBytes = 0
        state =
            state.copy(
                phase = RecordingPhase.INTERRUPTED,
                stopConfirmation = false,
                persistenceFailure = reason,
            )
    }

    private fun flush(): Boolean {
        if (pendingBytes == 0) return true
        val bytes = pending.copyOf(pendingBytes)
        pending.fill(0)
        pendingBytes = 0
        val count = AudioTimeline.frames(bytes.size)
        val unit =
            AudioStorageUnitIdentity(
                identity,
                freshId(),
                ordinal,
                state.durableFrames,
                physicalId,
                physicalStart,
                state.durableFrames - physicalStart,
            )
        val result =
            try {
                val started = System.nanoTime()
                writer.append(unit, AudioFormat.PCM, bytes).also {
                    maximumAppendNanos = maxOf(maximumAppendNanos, System.nanoTime() - started)
                }
            } finally {
                bytes.fill(0)
            }
        if (!checkResult(result)) return false
        ordinal++
        state = state.copy(durableFrames = AudioTimeline.nextFrame(state.durableFrames, count))
        return true
    }

    private fun checkResult(result: AudioResult<Unit>): Boolean =
        when (result) {
            is AudioResult.Value -> true
            is AudioResult.Failed -> {
                interrupt(result.reason)
                false
            }
        }

    companion object {
        // Existing accepted bridge limit. A persistence unit, never a semantic/VAD segment.
        const val TRANSPORT_BYTES = 160_000
        private val ACTIVE_PHASES = setOf(RecordingPhase.RECORDING, RecordingPhase.PAUSED)

        private fun freshId() = UUID.randomUUID().toString()
    }
}
