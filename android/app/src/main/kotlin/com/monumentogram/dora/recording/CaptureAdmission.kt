package com.monumentogram.dora.recording

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
    }

    data class Boundary(val frames: Long, val lastAcceptedNanos: Long, val fencedAtNanos: Long)

    private var generation = 0L
    private var accepting = false
    private var frames = 0L
    private var lastAccepted = 0L
    private var fencedAt = 0L
    private var physicalId = ""
    private val outstanding = ArrayDeque<Long>()
    private var durableFrames = 0L

    @Synchronized
    fun begin(physicalSegmentId: String = ""): Long {
        accepting = false
        physicalId = physicalSegmentId
        return ++generation
    }

    @Synchronized
    fun open(expected: Long): Boolean {
        if (expected != generation) return false
        accepting = true
        return true
    }

    @Synchronized
    fun offer(expected: Long, bytes: ByteArray): Result {
        if (!accepting || expected != generation) return Result.FENCED
        return if (
            outstanding.size >= queue.capacity ||
                frames - durableFrames + bytes.size / 2 > MAXIMUM_FRAMES
        )
            Result.FULL
        else if (!queue.offer(CapturedBlock(expected, physicalId, frames, bytes))) Result.FULL
        else {
            frames += bytes.size / 2
            outstanding.addLast(frames)
            lastAccepted = now()
            Result.ACCEPTED
        }
    }

    @Synchronized
    fun fence(): Boundary {
        accepting = false
        fencedAt = now() // Same monitor as PCM admission, before any native stop or queue work.
        generation++
        return Boundary(frames, lastAccepted, fencedAt)
    }

    @Synchronized fun snapshot(): Boundary = Boundary(frames, lastAccepted, fencedAt)

    /** Only verified durable completion releases budget, never a memory-only queue drain. */
    @Synchronized
    fun durableThrough(end: Long) {
        require(end in durableFrames..frames)
        durableFrames = end
        while (outstanding.firstOrNull()?.let { it <= end } == true) outstanding.removeFirst()
    }

    /** Called only after terminal writer settlement and native release, before a new recording. */
    @Synchronized
    fun retire() {
        check(!accepting && queue.size == 0)
        outstanding.clear()
        durableFrames = frames
    }

    private companion object {
        const val MAXIMUM_FRAMES = 256_000L
    }
}
