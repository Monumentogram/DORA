package com.monumentogram.dora.ui.signal

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.R
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.recording.RecordingSearchRow
import java.util.concurrent.atomic.AtomicLong

/** Foreground-only presentation. Query and snippets never enter saved state or plaintext files. */
internal class RecordingLibrary(private val activity: MainActivity) : AutoCloseable {
    private val runtime = (activity.application as DoraApplication).audioRuntime
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicLong()
    private var pending: Runnable? = null
    private var closed = false
    private var after: String? = null
    var entries by mutableStateOf<List<RecordingSearchRow>>(emptyList())
        private set

    var query by mutableStateOf("")
        private set

    var searching by mutableStateOf(false)
        private set

    var busy by mutableStateOf(false)
        private set

    var failed by mutableStateOf(false)
        private set

    var more by mutableStateOf(false)
        private set

    fun openSearch() {
        searching = true
    }

    fun closeSearch() {
        searching = false
        query = ""
        load()
    }

    fun search(value: String) {
        val changed = value.trim() != query.trim()
        query = value
        if (!changed) return
        invalidate()
        busy = true
        val action = Runnable {
            pending = null
            load()
        }
        pending = action
        main.postDelayed(action, DEBOUNCE_MS)
    }

    /** Also used before opening the editor so a pending search cannot revoke its session. */
    fun invalidate() {
        generation.incrementAndGet()
        pending?.let(main::removeCallbacks)
        pending = null
        entries = emptyList()
        after = null
        busy = false
        failed = false
        more = false
    }

    fun clear() {
        invalidate()
        searching = false
        query = ""
    }

    @Suppress("ReturnCount") // Reject closed/invalid paging and empty-vault cases before dispatch.
    fun load(next: Boolean = false) {
        if (closed) return
        if (next && (busy || !more)) return
        val cursor = if (next) after.orEmpty() else ""
        if (!next) invalidate()
        if (runtime.recordingOpenMode() == AudioOpenMode.CREATE_NEW) return
        val request = generation.incrementAndGet()
        busy = true
        failed = false
        runtime.requestRecordingSearch(
            activity,
            query.trim(),
            activity.getString(R.string.signal_recording_name),
            cursor,
            { generation.get() != request },
        ) { result ->
            if (current(request)) {
                busy = false
                if (result is AudioResult.Value) {
                    entries =
                        (if (next) entries + result.value.rows else result.value.rows).distinctBy {
                            it.recording.cursor
                        }
                    after = result.value.nextCursor
                    more = after != null
                } else {
                    entries = emptyList()
                    more = false
                    failed = true
                }
            }
        }
    }

    private fun current(request: Long) =
        !closed &&
            generation.get() == request &&
            !activity.isFinishing &&
            !activity.isDestroyed &&
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    override fun close() {
        clear()
        closed = true
    }

    private companion object {
        const val DEBOUNCE_MS = 200L
    }
}
