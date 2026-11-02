package com.monumentogram.dora.transcript

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.transcript.PagedTranscriptRead
import java.util.concurrent.Executor
import java.util.concurrent.Executors

internal fun registerTextExportPicker(
    registry: ActivityResultRegistry,
    owner: LifecycleOwner,
    consume: (Uri?) -> Unit,
) = registry.register("transcript-text-export", owner, LocalTextDocument()) { consume(it) }

internal class LocalTextDocument : ActivityResultContracts.CreateDocument("text/plain") {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

/** Survives the explicitly launched picker and rotation, never serializes transcript content. */
internal class TextExportController(application: Application) : AndroidViewModel(application) {
    var status by mutableStateOf(TextExportStatus.IDLE)
        private set

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val queue = TextExportQueue(worker, Executor { main.post(it) }) { status = it }

    private data class PagedSelection(
        val identity: AudioIdentity,
        val version: String,
        val revision: Long,
    )

    private var pagedSelection: PagedSelection? = null
    private var pagedSession: ProductAudioSession? = null

    fun beginSelection(text: String): Boolean {
        val accepted = queue.beginSelection(text)
        if (accepted) pagedSelection = null
        return accepted
    }

    fun beginPagedSelection(identity: AudioIdentity, version: String, revision: Long): Boolean {
        val selected = PagedSelection(identity, version, revision)
        val accepted = queue.beginSelection { output ->
            try {
                val session = checkNotNull(pagedSession)
                PagedTranscriptRead.stream(
                    session.transcripts.paged,
                    identity,
                    version,
                    revision,
                    queue::cancelled,
                ) { text ->
                    val bytes = text.toByteArray(Charsets.UTF_8)
                    try {
                        output.write(bytes)
                    } finally {
                        bytes.fill(0)
                    }
                }
            } finally {
                pagedSession = null
                pagedSelection = null
            }
        }
        if (accepted) pagedSelection = selected
        return accepted
    }

    fun selectionFailed() {
        pagedSelection = null
        pagedSession = null
        queue.selectionFailed()
    }

    fun completeSelection(uri: Uri?, host: MainActivity? = null) {
        if (uri != null && pagedSelection != null) {
            val activity =
                host
                    ?: run {
                        queue.selectionFailed()
                        return
                    }
            val runtime = (getApplication<Application>() as DoraApplication).audioRuntime
            runtime.requestOpen(activity, AudioOpenMode.OPEN_EXISTING) { available ->
                if (available is AudioAvailability.Available) {
                    pagedSession = available.session
                    queue.completeSelection {
                        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    }
                } else {
                    pagedSelection = null
                    queue.selectionFailed()
                }
            }
            return
        }
        pagedSelection = null
        if (uri == null) queue.completeSelection(null)
        else
            queue.completeSelection {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
            }
    }

    override fun onCleared() {
        queue.close()
        pagedSelection = null
        pagedSession = null
        worker.shutdown()
        super.onCleared()
    }
}
