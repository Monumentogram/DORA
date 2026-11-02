package com.monumentogram.dora.transcript

import java.io.OutputStream
import java.util.concurrent.Executor

internal enum class TextExportStatus {
    IDLE,
    SELECTING,
    WRITING,
    SAVED,
    CANCELLED,
    FAILED;

    val busy: Boolean
        get() = this == SELECTING || this == WRITING
}

/** One explicitly requested text snapshot, in memory only; no saved state, temp file or URI log. */
internal class TextExportQueue(
    private val worker: Executor,
    private val main: Executor,
    private val notify: (TextExportStatus) -> Unit,
) : AutoCloseable {
    private var snapshot: ByteArray? = null
    private var streaming: ((OutputStream) -> Unit)? = null
    private var status = TextExportStatus.IDLE
    private var closed = false

    @Synchronized
    fun beginSelection(text: String): Boolean {
        if (closed || status.busy) return false
        snapshot = text.toByteArray(Charsets.UTF_8)
        publish(TextExportStatus.SELECTING)
        return true
    }

    @Synchronized
    fun beginSelection(write: (OutputStream) -> Unit): Boolean {
        if (closed || status.busy) return false
        streaming = write
        publish(TextExportStatus.SELECTING)
        return true
    }

    @Synchronized fun cancelled(): Boolean = closed

    @Synchronized
    fun selectionFailed() {
        if (closed || status != TextExportStatus.SELECTING) return
        snapshot?.fill(0)
        snapshot = null
        streaming = null
        publish(TextExportStatus.FAILED)
    }

    @Synchronized
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    fun completeSelection(open: (() -> OutputStream?)?) {
        if (closed) return
        val bytes = snapshot
        val stream = streaming
        snapshot = null
        streaming = null
        if (bytes == null && stream == null) {
            if (status == TextExportStatus.IDLE) publish(TextExportStatus.CANCELLED)
            return
        }
        if (open == null) {
            bytes?.fill(0)
            publish(TextExportStatus.CANCELLED)
            return
        }
        publish(TextExportStatus.WRITING)
        try {
            worker.execute { write(bytes, stream, open) }
        } catch (_: RuntimeException) {
            bytes?.fill(0)
            publish(TextExportStatus.FAILED)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun write(
        bytes: ByteArray?,
        stream: ((OutputStream) -> Unit)?,
        open: () -> OutputStream?,
    ) {
        val result =
            try {
                if (synchronized(this) { closed }) return
                checkNotNull(open()).use {
                    if (bytes != null) it.write(bytes) else checkNotNull(stream).invoke(it)
                    it.flush()
                }
                TextExportStatus.SAVED
            } catch (_: Exception) {
                TextExportStatus.FAILED
            } finally {
                bytes?.fill(0)
            }
        synchronized(this) { if (!closed) publish(result) }
    }

    private fun publish(value: TextExportStatus) {
        status = value
        main.execute { synchronized(this) { if (!closed && status == value) notify(value) } }
    }

    @Synchronized
    override fun close() {
        closed = true
        snapshot?.fill(0)
        snapshot = null
        streaming = null
    }
}
