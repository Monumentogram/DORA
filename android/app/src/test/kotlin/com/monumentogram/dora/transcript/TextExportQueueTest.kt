package com.monumentogram.dora.transcript

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.Executor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextExportQueueTest {
    private val direct = Executor { it.run() }

    @Test
    fun exportsExactDisplayedUtf8ParagraphsAndWipesSnapshot() {
        val states = mutableListOf<TextExportStatus>()
        val queue = TextExportQueue(direct, direct, states::add)
        val text = "Ручная правка\n\nEnglish paragraph 😀\n"
        assertTrue(queue.beginSelection(text))
        assertFalse(queue.beginSelection("different version"))
        var borrowed: ByteArray? = null
        val output =
            object : ByteArrayOutputStream() {
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    borrowed = bytes
                    super.write(bytes, offset, length)
                }
            }
        queue.completeSelection { output }
        assertArrayEquals(text.toByteArray(Charsets.UTF_8), output.toByteArray())
        assertTrue(checkNotNull(borrowed).all { it == 0.toByte() })
        assertEquals(TextExportStatus.SAVED, states.last())
        queue.completeSelection { error("Snapshot must be consumed") }
    }

    @Test
    fun cancellationDoesNotWriteOrReportSuccess() {
        val states = mutableListOf<TextExportStatus>()
        val queue = TextExportQueue(direct, direct, states::add)
        assertTrue(queue.beginSelection("saved edit"))
        queue.completeSelection(null)
        assertEquals(TextExportStatus.CANCELLED, states.last())
        queue.completeSelection { error("Snapshot must be consumed") }
        assertFalse(TextExportStatus.SAVED in states)
    }

    @Test
    fun outputOpenWriteAndCloseErrorsNeverReportSuccess() {
        val broken =
            listOf<() -> OutputStream?>(
                { null },
                { throw IOException("private locator must not be reported") },
                {
                    object : OutputStream() {
                        override fun write(value: Int) {
                            throw IOException()
                        }
                    }
                },
                {
                    object : ByteArrayOutputStream() {
                        override fun close() {
                            throw IOException()
                        }
                    }
                },
            )
        broken.forEach { open ->
            val states = mutableListOf<TextExportStatus>()
            val queue = TextExportQueue(direct, direct, states::add)
            queue.beginSelection("user correction")
            queue.completeSelection(open)
            assertEquals(TextExportStatus.FAILED, states.last())
            queue.completeSelection { error("Snapshot must be consumed") }
            assertFalse(TextExportStatus.SAVED in states)
        }
    }

    @Test
    fun orphanResultAndClosedControllerCannotWrite() {
        val states = mutableListOf<TextExportStatus>()
        val queue = TextExportQueue(direct, direct, states::add)
        queue.completeSelection { error("No snapshot after process death") }
        assertEquals(TextExportStatus.CANCELLED, states.last())
        queue.beginSelection("secret")
        queue.close()
        queue.completeSelection { error("Closed owner") }
        queue.completeSelection { error("Snapshot must be consumed") }
    }

    @Test
    fun pickerLaunchFailureIsFailureAndReleasesSnapshot() {
        val states = mutableListOf<TextExportStatus>()
        val queue = TextExportQueue(direct, direct, states::add)
        queue.beginSelection("saved edit")
        queue.selectionFailed()
        assertEquals(TextExportStatus.FAILED, states.last())
        queue.completeSelection { error("Failed picker must release snapshot") }
        assertTrue(queue.beginSelection("retry"))
    }
}
