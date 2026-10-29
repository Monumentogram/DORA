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

    fun beginSelection(text: String) = queue.beginSelection(text)

    fun selectionFailed() = queue.selectionFailed()

    fun completeSelection(uri: Uri?) {
        if (uri == null) queue.completeSelection(null)
        else
            queue.completeSelection {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
            }
    }

    override fun onCleared() {
        queue.close()
        worker.shutdown()
        super.onCleared()
    }
}
