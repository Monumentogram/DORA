package com.monumentogram.dora.recording

import java.util.concurrent.ArrayBlockingQueue

/** Single producer/consumer; bounded memory and work. No audio is written to disk here. */
internal data class CapturedBlock(
    val generation: Long,
    val physicalId: String,
    val firstFrame: Long,
    val pcm: ByteArray,
)

internal class BoundedPcmQueue(val capacity: Int) {
    private val queue = ArrayBlockingQueue<CapturedBlock>(capacity)
    @Volatile
    var highWater = 0
        private set

    val size: Int
        get() = queue.size

    fun offer(bytes: ByteArray): Boolean = offer(CapturedBlock(0, "", 0, bytes))

    fun offer(block: CapturedBlock): Boolean {
        val accepted = queue.offer(block)
        if (accepted) highWater = maxOf(highWater, queue.size)
        return accepted
    }

    fun drain(maximumBlocks: Int, consume: (ByteArray) -> Unit) {
        drainOwned(maximumBlocks) { consume(it.pcm) }
    }

    fun drainOwned(maximumBlocks: Int, consume: (CapturedBlock) -> Unit) {
        require(maximumBlocks > 0)
        repeat(maximumBlocks) {
            val block = queue.poll() ?: return
            try {
                consume(block)
            } finally {
                block.pcm.fill(0)
            }
        }
    }
}
