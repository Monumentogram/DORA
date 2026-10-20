package com.monumentogram.dora.recording

import com.monumentogram.dora.audio.recording.AlphaRecordingLimit

/**
 * The offer and Pause fence share one short monitor; native calls and persistence never hold it.
 */
internal class CaptureAdmission(
    private val queue: BoundedPcmQueue,
    private val now: () -> Long = System::nanoTime,
) {
    enum class Result {
        ACCEPTED,
        FENCED,
        FULL,
        LIMIT_REACHED,
    }

    data class Boundary(val frames: Long, val lastAcceptedNanos: Long, val fencedAtNanos: Long)

    data class Diagnostics(
        val generation: Long,
        val frames: Long,
        val durableFrames: Long,
        val outstandingBlocks: Int,
        val accepting: Boolean,
    )

    data class Rejection(val atNanos: Long, val admission: Diagnostics)

    private var lastRejection: Rejection? = null

    private var generation = 0L
    private var accepting = false
    private var frames = 0L
    private var lastAccepted = 0L
    private var fencedAt = 0L
    private var captureEpochId = ""
    private val outstanding = ArrayDeque<Long>()
    private var durableFrames = 0L
    private var limit = AlphaRecordingLimit.MAX_ALPHA_FRAMES

    @Synchronized
    fun begin(captureEpochId: String = ""): Long {
        accepting = false
        this.captureEpochId = captureEpochId
        lastRejection = null
        return ++generation
    }

    @Synchronized
    fun open(expected: Long): Boolean {
        if (expected != generation || frames >= limit) return false
        accepting = true
        return true
    }

    @Synchronized
    @Suppress("ReturnCount") // Reject at each ownership/budget fence before transferring PCM.
    fun offer(expected: Long, bytes: ByteArray): Result {
        if (!accepting || expected != generation) return Result.FENCED
        require(bytes.isNotEmpty() && bytes.size % 2 == 0)
        val count = minOf(bytes.size / 2L, limit - frames).toInt()
        if (count == 0) {
            accepting = false
            return Result.FENCED
        }
        if (outstanding.size >= queue.capacity || frames - durableFrames + count > MAXIMUM_FRAMES)
            return rejected()
        val owned = if (count * 2 == bytes.size) bytes else bytes.copyOf(count * 2)
        if (!queue.offer(CapturedBlock(expected, captureEpochId, frames, owned))) {
            if (owned !== bytes) owned.fill(0)
            return rejected()
        }
        if (owned !== bytes) bytes.fill(0)
        frames += count
        outstanding.addLast(frames)
        lastAccepted = now()
        if (frames == limit) {
            accepting = false
            fencedAt = lastAccepted
            return Result.LIMIT_REACHED
        }
        return Result.ACCEPTED
    }

    @Synchronized
    fun fence(): Boundary {
        accepting = false
        fencedAt = now() // Same monitor as PCM admission, before any native stop or queue work.
        generation++
        return Boundary(frames, lastAccepted, fencedAt)
    }

    @Synchronized fun snapshot(): Boundary = Boundary(frames, lastAccepted, fencedAt)

    @Synchronized
    fun diagnostics() = Diagnostics(generation, frames, durableFrames, outstanding.size, accepting)

    @Synchronized fun rejection(): Rejection? = lastRejection

    private fun rejected(): Result {
        lastRejection = Rejection(now(), diagnostics())
        return Result.FULL
    }

    /** Only verified durable completion releases budget, never a memory-only queue drain. */
    @Synchronized
    fun durableThrough(end: Long) {
        require(end in durableFrames..frames)
        durableFrames = end
        while (outstanding.firstOrNull()?.let { it <= end } == true) outstanding.removeFirst()
    }

    /** Called only after terminal writer settlement and native release, before a new recording. */
    @Synchronized
    fun retire(recoveredFrames: Long = 0) {
        check(!accepting && queue.size == 0)
        limit = Math.addExact(frames, AlphaRecordingLimit.remaining(recoveredFrames))
        outstanding.clear()
        durableFrames = frames
    }

    private companion object {
        const val MAXIMUM_FRAMES = 256_000L
    }
}
