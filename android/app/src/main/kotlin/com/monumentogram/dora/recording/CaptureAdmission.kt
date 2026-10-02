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

    data class Boundary(val frames: Long, val lastAcceptedNanos: Long)

    private var generation = 0L
    private var accepting = false
    private var frames = 0L
    private var lastAccepted = 0L

    @Synchronized
    fun begin(): Long {
        accepting = false
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
        return if (!queue.offer(bytes)) Result.FULL
        else {
            frames += bytes.size / 2
            lastAccepted = now()
            Result.ACCEPTED
        }
    }

    @Synchronized
    fun fence(): Boundary {
        accepting = false
        generation++
        return Boundary(frames, lastAccepted)
    }

    @Synchronized fun snapshot(): Boundary = Boundary(frames, lastAccepted)
}
