package com.monumentogram.dora.vad

/** Bounded S16LE adapter; no assumption about the microphone read size. */
class PcmWindowAdapter(
    windowFrames: Int,
    private val emit: (FrameRange, FloatArray) -> Unit,
) {
    private companion object {
        const val BYTE_MASK = 255
        const val BYTE_BITS = 8
        const val S16_SCALE = 32768f
    }

    init {
        require(windowFrames > 0)
    }

    private val samples = FloatArray(windowFrames)
    private var expected: Long? = null
    private var windowStart = 0L
    var pendingFrames = 0
        private set

    fun accept(pcm: ByteArray, firstFrame: Long) {
        require(pcm.isNotEmpty() && pcm.size % 2 == 0 && firstFrame >= 0)
        require(expected == null || expected == firstFrame)
        val end = Math.addExact(firstFrame, pcm.size / 2L)
        var position = firstFrame
        for (offset in pcm.indices step 2) {
            if (pendingFrames == 0) windowStart = position
            val signed =
                ((pcm[offset].toInt() and BYTE_MASK) or (pcm[offset + 1].toInt() shl BYTE_BITS))
                    .toShort()
            samples[pendingFrames++] = signed.toFloat() / S16_SCALE
            position++
            if (pendingFrames == samples.size) {
                try {
                    emit(FrameRange(windowStart, position), samples)
                } finally {
                    samples.fill(0f)
                    pendingFrames = 0
                }
            }
        }
        expected = end
    }

    fun unclassifiedTail(): FrameRange? =
        if (pendingFrames == 0) null else FrameRange(windowStart, windowStart + pendingFrames)

    fun reset() {
        samples.fill(0f)
        pendingFrames = 0
        expected = null
        windowStart = 0
    }
}
