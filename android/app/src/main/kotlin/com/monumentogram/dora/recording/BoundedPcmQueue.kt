package com.monumentogram.dora.recording

import java.util.concurrent.ArrayBlockingQueue

/** Single producer/consumer; bounded memory and work. No audio is written to disk here. */
internal class BoundedPcmQueue(capacity: Int) {
    private val queue = ArrayBlockingQueue<ByteArray>(capacity)
    @Volatile
    var highWater = 0
        private set

    val size: Int
        get() = queue.size

    fun offer(bytes: ByteArray): Boolean {
        val accepted = queue.offer(bytes)
        if (accepted) highWater = maxOf(highWater, queue.size)
        return accepted
    }

    fun drain(maximumBlocks: Int, consume: (ByteArray) -> Unit) {
        require(maximumBlocks > 0)
        repeat(maximumBlocks) {
            val bytes = queue.poll() ?: return
            try {
                consume(bytes)
            } finally {
                bytes.fill(0)
            }
        }
    }
}
