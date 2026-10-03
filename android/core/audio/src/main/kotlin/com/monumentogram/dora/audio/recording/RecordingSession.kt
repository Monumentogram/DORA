@file:Suppress(
    "ReturnCount",
    "TooManyFunctions",
    "LongParameterList",
) // Early exits keep terminal-state and durability fences explicit.

package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.AudioTimeline
import com.monumentogram.dora.audio.PersistenceLatency
import com.monumentogram.dora.audio.ProductAudioWriterPort
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

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

enum class RecordingDurability {
    CAUGHT_UP,
    PENDING,
    FAILED,
}

/** Content-free state. Signal is supplied separately by the Android capture adapter. */
data class RecordingState(
    val phase: RecordingPhase = RecordingPhase.PREFLIGHT,
    val frames: Long = 0,
    val durableFrames: Long = 0,
    val stopConfirmation: Boolean = false,
    val persistenceFailure: AudioFailure? = null,
) {
    val durability: RecordingDurability
        get() =
            when {
                phase == RecordingPhase.INTERRUPTED -> RecordingDurability.FAILED
                frames > durableFrames -> RecordingDurability.PENDING
                else -> RecordingDurability.CAUGHT_UP
            }

    val durationUs: Long
        get() = AudioTimeline.durationUs(frames)
}

/**
 * One control owner assembles PCM and seals immutable units. Only the serialized persistence
 * executor borrows sealed bytes; completion returns to the control owner. No disk work runs in
 * pause/resume. The caller fences admission and releases the reader before invoking pause.
 */
class RecordingSession(
    val identity: AudioIdentity,
    private val writer: ProductAudioWriterPort,
    private val timing: (String) -> Unit = {},
    private val appendTiming: ((Map<String, Long>) -> Unit)? = null,
    private val persistence: Executor = Executor { it.run() },
    private val completion: Executor = Executor { it.run() },
    private val changed: () -> Unit = {},
    private val persistenceFailed: () -> Unit = {},
) {
    private val failed = AtomicReference<AudioFailure?>(null)
    val canCapture: Boolean
        get() = failed.get() == null && state.phase in ACTIVE_PHASES

    var state = RecordingState()
        private set

    private val pending = ByteArray(TRANSPORT_BYTES)
    private var pendingBytes = 0
    private var ordinal = 0
    var physicalId = freshId()
        private set

    private var physicalStart = 0L
    private var sealedFrames = 0L
    var maximumAppendNanos: Long = 0
        private set

    /** Called only with the runtime's reauthenticated, pending-free continuation point. */
    fun restore(frames: Long, nextOrdinal: Int) {
        check(state.phase == RecordingPhase.PREFLIGHT)
        require(frames >= 0 && nextOrdinal >= 0)
        ordinal = nextOrdinal
        physicalStart = frames
        sealedFrames = frames
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
        if (state.phase != RecordingPhase.RECORDING || failed.get() != null) return
        val frames = AudioTimeline.frames(pcm.size)
        state = state.copy(frames = AudioTimeline.nextFrame(state.frames, frames))
        var offset = 0
        while (offset < pcm.size && state.phase == RecordingPhase.RECORDING) {
            val count = minOf(pcm.size - offset, pending.size - pendingBytes)
            pcm.copyInto(pending, pendingBytes, offset, offset + count)
            pendingBytes += count
            offset += count
            if (pendingBytes == pending.size) seal()
        }
    }

    /** Exact admission provenance; delayed draining cannot relabel a block. */
    fun accept(pcm: ByteArray, physicalSegmentId: String, firstFrame: Long) {
        if (failed.get() != null) return
        if (
            physicalSegmentId != physicalId ||
                firstFrame != state.frames ||
                state.phase != RecordingPhase.RECORDING
        ) {
            interrupt(AudioFailure.INVALID_INPUT)
            persistenceFailed()
            return
        }
        accept(pcm)
    }

    fun pause() {
        if (state.phase != RecordingPhase.RECORDING) return
        state = state.copy(phase = RecordingPhase.PAUSED)
        seal()
    }

    fun resume(physicalSegmentId: String = freshId()): Boolean {
        if (state.phase != RecordingPhase.PAUSED || failed.get() != null) return false
        physicalId = physicalSegmentId
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
        seal()
        if (failed.get() != null) return
        if (state.frames == 0L) {
            state = state.copy(phase = RecordingPhase.EMPTY)
            return
        }
        // Exactly one finalize call. UNCERTAIN remains interrupted until authenticated recovery.
        persistence.execute {
            val result = write { writer.finalize(identity) }
            completion.execute {
                if (
                    checkResult(result) &&
                        state.phase == RecordingPhase.FINALIZING &&
                        failed.get() == null
                )
                    state = state.copy(phase = RecordingPhase.SAVED)
                changed()
            }
        }
    }

    /** Retire access only after every task has stopped borrowing its writer and plaintext. */
    fun whenSettled(action: () -> Unit) {
        persistence.execute { completion.execute(action) }
    }

    /** Keeps the committed prefix and never converts abnormal termination into successful Stop. */
    fun interrupt(reason: AudioFailure? = null) {
        failed.compareAndSet(null, reason ?: AudioFailure.UNAVAILABLE)
        pending.fill(0)
        pendingBytes = 0
        state =
            state.copy(
                phase = RecordingPhase.INTERRUPTED,
                stopConfirmation = false,
                persistenceFailure = reason,
            )
    }

    private fun seal() {
        if (pendingBytes == 0) return
        val bytes = pending.copyOf(pendingBytes)
        pending.fill(0)
        pendingBytes = 0
        val count = AudioTimeline.frames(bytes.size)
        val unit =
            AudioStorageUnitIdentity(
                identity,
                freshId(),
                ordinal,
                sealedFrames,
                physicalId,
                physicalStart,
                sealedFrames - physicalStart,
            )
        // Reserve before enqueue: even a direct executor may reenter through completion.
        ordinal++
        sealedFrames = AudioTimeline.nextFrame(sealedFrames, count)
        val durableEnd = sealedFrames
        persistence.execute {
            var elapsed = 0L
            val result =
                try {
                    write {
                        val started = System.nanoTime()
                        timing("append_start")
                        val report = appendTiming
                        (if (report == null) writer.append(unit, AudioFormat.PCM, bytes)
                            else
                                PersistenceLatency.collect(report) {
                                    writer.append(unit, AudioFormat.PCM, bytes)
                                })
                            .also {
                                elapsed = System.nanoTime() - started
                                timing("append_end")
                            }
                    }
                } finally {
                    bytes.fill(0)
                }
            completion.execute {
                maximumAppendNanos = maxOf(maximumAppendNanos, elapsed)
                if (checkResult(result)) state = state.copy(durableFrames = durableEnd)
                changed()
            }
        }
    }

    private fun write(action: () -> AudioResult<Unit>): AudioResult<Unit> {
        failed.get()?.let {
            return AudioResult.Failed(it)
        }
        val result =
            try {
                action()
            } catch (_: Exception) {
                AudioResult.Failed(AudioFailure.UNAVAILABLE)
            }
        if (result is AudioResult.Failed && failed.compareAndSet(null, result.reason))
            persistenceFailed()
        return result
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
