package com.monumentogram.dora.poc.recovery.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidOsRecoveryReconciliationStorageTest {
    @Test
    fun `bounded descriptor closes on success zero progress overflow and growth`() {
        fun run(
            bytes: ByteArray,
            size: Long = bytes.size.toLong(),
            reads: List<Int>? = null,
        ): Pair<ByteArray?, FakeDescriptor> {
            val descriptor = FakeDescriptor(bytes, size, reads)
            val result =
                runCatching { RecoveryBoundedDescriptorReader { descriptor }.read("artifact", 8) }
                    .getOrNull()
            return result to descriptor
        }

        val success = run(byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), success.first)
        assertTrue(success.second.closed)

        val zero = FakeDescriptor(byteArrayOf(1), 1, listOf(0))
        assertEqualsFailure(zero) { RecoveryBoundedDescriptorReader { zero }.read("artifact", 8) }

        val over = FakeDescriptor(ByteArray(9), 9)
        assertThrows(IllegalStateException::class.java) {
            RecoveryBoundedDescriptorReader { over }.read("artifact", 8)
        }
        assertTrue(over.closed)

        val growth = FakeDescriptor(byteArrayOf(1, 2), 1)
        assertThrows(IllegalStateException::class.java) {
            RecoveryBoundedDescriptorReader { growth }.read("artifact", 8)
        }
        assertTrue(growth.closed)
    }

    @Test
    fun `bounded descriptor rejects non regular and negative metadata and always closes`() {
        listOf(
                FakeDescriptor(byteArrayOf(), 0, regularFile = false),
                FakeDescriptor(byteArrayOf(), -1),
            )
            .forEach { descriptor ->
                assertThrows(IllegalStateException::class.java) {
                    RecoveryBoundedDescriptorReader { descriptor }.read("artifact", 8)
                }
                assertTrue(descriptor.closed)
            }
    }

    private fun assertEqualsFailure(descriptor: FakeDescriptor, block: () -> Unit) {
        assertThrows(IllegalStateException::class.java, block)
        assertTrue(descriptor.closed)
    }

    private class FakeDescriptor(
        private val bytes: ByteArray,
        override val size: Long,
        private val scriptedReads: List<Int>? = null,
        override val regularFile: Boolean = true,
    ) : RecoveryReadDescriptor {
        private var offset = 0
        private var call = 0
        var closed = false
            private set

        @Suppress("ReturnCount")
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val scripted = scriptedReads?.getOrNull(call++)
            if (scripted != null) return scripted
            if (this.offset >= bytes.size) return -1
            val count = minOf(length, bytes.size - this.offset)
            bytes.copyInto(buffer, offset, this.offset, this.offset + count)
            this.offset += count
            return count
        }

        override fun close() {
            closed = true
        }
    }
}
