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
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.vad.FrameRange
import com.monumentogram.dora.vad.SegmentationProfile
import com.monumentogram.dora.vad.TechnicalTimeline
import com.monumentogram.dora.vad.VadFailure
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
    val segmentationFailure: VadFailure? = null,
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

    var captureEpochId = physicalId
        private set

    private var technicalTimeline = newTechnicalTimeline(0)
    private var epochStart = 0L
    private var physicalOpened = false
    private var physicalClosed = false
    private val pendingMetadata = ArrayDeque<SegmentationMetadata>()
    private val metadataFailed = AtomicReference<VadFailure?>(null)

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
        technicalTimeline = newTechnicalTimeline(frames)
        epochStart = frames
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
        val end = AudioTimeline.nextFrame(state.frames, frames)
        val slices = technicalTimeline.accept(FrameRange(state.frames, end), captureEpochId)
        var offset = 0
        for (slice in slices) {
            if (state.phase != RecordingPhase.RECORDING || failed.get() != null) break
            if (slice.technicalId != physicalId) {
                seal()
                closePhysical("CAP")
                physicalId = slice.technicalId
                physicalStart = slice.technicalFirstFrame
                physicalOpened = false
                physicalClosed = false
            }
            var remaining = Math.toIntExact(slice.range.count * 2)
            while (
                remaining > 0 && state.phase == RecordingPhase.RECORDING && failed.get() == null
            ) {
                val count = minOf(remaining, pending.size - pendingBytes)
                pcm.copyInto(pending, pendingBytes, offset, offset + count)
                pendingBytes += count
                offset += count
                remaining -= count
                state = state.copy(frames = AudioTimeline.nextFrame(state.frames, count / 2L))
                if (pendingBytes == pending.size) seal()
            }
            if (state.frames - physicalStart == SegmentationProfile.FROZEN.technicalCapFrames)
                closePhysical("CAP")
        }
    }

    /** Exact admission provenance; delayed draining cannot relabel a block. */
    fun accept(pcm: ByteArray, physicalSegmentId: String, firstFrame: Long) {
        if (failed.get() != null) return
        if (
            physicalSegmentId != captureEpochId ||
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
        closePhysical("PAUSE")
    }

    fun resume(physicalSegmentId: String = freshId()): Boolean {
        if (state.phase != RecordingPhase.PAUSED || failed.get() != null) return false
        physicalId = physicalSegmentId
        captureEpochId = physicalSegmentId
        technicalTimeline.resume(state.frames, captureEpochId)
        physicalStart = state.frames
        epochStart = state.frames
        physicalOpened = false
        physicalClosed = false
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
        closePhysical("STOP")
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

    /** Control-owner entry. Metadata waits only for source sealing, never the microphone. */
    fun retainMetadata(metadata: SegmentationMetadata) {
        if (metadataFailed.get() != null || state.phase == RecordingPhase.INTERRUPTED) return
        if (pendingMetadata.size >= MAX_PENDING_METADATA) {
            metadataFailure()
            return
        }
        pendingMetadata.addLast(metadata)
        flushMetadata()
    }

    private fun metadataFailure() {
        metadataFailed.set(VadFailure.METADATA_FAILED)
        pendingMetadata.clear()
        state = state.copy(segmentationFailure = VadFailure.METADATA_FAILED)
        changed()
    }

    private fun flushMetadata() {
        while (pendingMetadata.isNotEmpty() && metadataFailed.get() == null) {
            val metadata = pendingMetadata.first()
            if (metadata.endFrame > sealedFrames) return
            pendingMetadata.removeFirst()
            persistence.execute {
                val result =
                    try {
                        if (failed.get() != null || metadataFailed.get() != null)
                            AudioResult.Failed(AudioFailure.UNAVAILABLE)
                        else writer.segmentation(identity, metadata)
                    } catch (_: Exception) {
                        AudioResult.Failed(AudioFailure.UNAVAILABLE)
                    }
                if (result is AudioResult.Failed) {
                    metadataFailed.set(VadFailure.METADATA_FAILED)
                    completion.execute { metadataFailure() }
                }
            }
        }
    }

    private fun physicalMetadata(kind: SegmentationKind, end: Long, reason: String) =
        SegmentationMetadata(
            kind,
            physicalId,
            physicalStart,
            end,
            captureEpochId,
            if (physicalStart == epochStart) null
            else maxOf(epochStart, physicalStart - SegmentationProfile.FROZEN.overlapFrames),
            reason,
        )

    private fun closePhysical(reason: String) {
        if (physicalOpened && !physicalClosed && state.frames > physicalStart) {
            physicalClosed = true
            retainMetadata(physicalMetadata(SegmentationKind.TECHNICAL_CLOSE, state.frames, reason))
        }
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
        if (!physicalOpened) {
            physicalOpened = true
            retainMetadata(
                physicalMetadata(
                    SegmentationKind.TECHNICAL_OPEN,
                    physicalStart,
                    if (physicalStart != epochStart) "CAP"
                    else if (physicalStart == 0L) "START" else "RESUME",
                )
            )
        }
        flushMetadata()
    }

    private fun newTechnicalTimeline(firstFrame: Long) =
        TechnicalTimeline(SegmentationProfile.FROZEN, firstFrame, captureEpochId) { freshId() }

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
        private const val MAX_PENDING_METADATA = 128
        private val ACTIVE_PHASES = setOf(RecordingPhase.RECORDING, RecordingPhase.PAUSED)

        private fun freshId() = UUID.randomUUID().toString()
    }
}
