package com.monumentogram.dora.ui.signal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.recording.RecordingRecovery
import com.monumentogram.dora.audio.transcript.TranscriptDocument
import java.util.concurrent.Executors

internal data class LibraryEntry(
    val recording: RecordingRecovery,
    val document: TranscriptDocument?,
)

/** Activity-owned presentation data; never stored in saved instance state or plaintext files. */
internal class RecordingLibrary(private val activity: MainActivity) : AutoCloseable {
    private val runtime = (activity.application as DoraApplication).audioRuntime
    private val worker = Executors.newSingleThreadExecutor()
    private var generation = 0L
    private var closed = false
    var entries by mutableStateOf<List<LibraryEntry>>(emptyList())
        private set

    var busy by mutableStateOf(false)
        private set

    var failed by mutableStateOf(false)
        private set

    var more by mutableStateOf(false)
        private set

    fun clear() {
        generation++
        entries = emptyList()
        busy = false
        failed = false
        more = false
    }

    fun load(next: Boolean = false) {
        if (busy || closed) return
        if (runtime.recordingOpenMode() == AudioOpenMode.CREATE_NEW) {
            clear()
            return
        }
        val request = ++generation
        val after = if (next) entries.lastOrNull()?.recording?.cursor.orEmpty() else ""
        busy = true
        failed = false
        runtime.requestRecordingRecovery(activity, after) { result ->
            if (!current(request)) return@requestRecordingRecovery
            val session = (runtime.availability as? AudioAvailability.Available)?.session
            if (result !is AudioResult.Value || session == null) {
                busy = false
                failed = true
                return@requestRecordingRecovery
            }
            worker.execute {
                val rows =
                    result.value.map { entry ->
                        val document =
                            entry.identity?.let {
                                (session.transcripts.load(it) as? AudioResult.Value)?.value
                            }
                        LibraryEntry(entry, document)
                    }
                activity.runOnUiThread {
                    if (current(request)) {
                        busy = false
                        if (
                            (runtime.availability as? AudioAvailability.Available)?.session ===
                                session
                        ) {
                            entries =
                                (if (next) entries + rows else rows).distinctBy {
                                    it.recording.cursor
                                }
                            more = rows.size == PAGE_SIZE
                        } else failed = true
                    }
                }
            }
        }
    }

    private fun current(request: Long) =
        !closed &&
            generation == request &&
            !activity.isFinishing &&
            !activity.isDestroyed &&
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    override fun close() {
        clear()
        closed = true
        worker.shutdown()
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
