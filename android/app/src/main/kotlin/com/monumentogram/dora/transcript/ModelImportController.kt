package com.monumentogram.dora.transcript

import android.app.Application
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal fun registerModelPicker(
    registry: ActivityResultRegistry,
    owner: LifecycleOwner,
    consume: (Uri?) -> Unit,
) =
    registry.register(
        "local-asr-model-picker",
        owner,
        ActivityResultContracts.OpenDocument(),
    ) {
        consume(it)
    }

internal enum class ModelImportStatus(val message: String) {
    IDLE(""),
    SELECTING("Выберите пакет распознавания."),
    IMPORTING("Проверяем и устанавливаем пакет распознавания…"),
    INSTALLED("Пакет распознавания установлен."),
    CANCELLED("Выбор пакета отменён."),
    REJECTED("Не удалось установить пакет. Проверьте выбранный файл и срок использования.");

    val busy: Boolean
        get() = this == SELECTING || this == IMPORTING
}

/** Retained model-only owner. No audio authorization, transcript, or Activity is retained. */
internal class ModelImportController(application: Application) : AndroidViewModel(application) {
    var status by mutableStateOf(ModelImportStatus.IDLE)
        private set

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val model = LocalAsrModel(application)
    private val queue = ModelImportQueue(worker, Executor { main.post(it) }) { status = it }

    fun beginSelection(): Boolean = queue.beginSelection()

    fun completeSelection(uri: Uri?) {
        if (uri == null) {
            queue.completeSelection(null)
        } else {
            queue.completeSelection {
                // URI stays inside this one worker closure; no grant or locator is persisted.
                getApplication<Application>()
                    .contentResolver
                    .openInputStream(uri)
                    ?.let(model::install) ?: false
            }
        }
    }

    override fun onCleared() {
        queue.close()
        worker.shutdownNow()
        super.onCleared()
    }
}

/** One selection/import at a time, independently of any dialog's composition lifecycle. */
internal class ModelImportQueue(
    private val worker: Executor,
    private val main: Executor,
    private val notify: (ModelImportStatus) -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val state = AtomicReference(ModelImportStatus.IDLE)

    fun beginSelection(): Boolean {
        val current = state.get()
        if (
            closed.get() ||
                current.busy ||
                !state.compareAndSet(current, ModelImportStatus.SELECTING)
        ) {
            return false
        }
        publish(ModelImportStatus.SELECTING)
        return true
    }

    @Suppress("TooGenericExceptionCaught")
    fun completeSelection(operation: (() -> Boolean)?) {
        // ActivityResultRegistry may restore a pending result after process recreation.
        state.compareAndSet(ModelImportStatus.IDLE, ModelImportStatus.SELECTING)
        val next =
            if (operation == null) ModelImportStatus.CANCELLED else ModelImportStatus.IMPORTING
        if (closed.get() || !state.compareAndSet(ModelImportStatus.SELECTING, next)) return
        publish(next)
        if (operation != null) {
            try {
                worker.execute { runImport(operation) }
            } catch (_: RuntimeException) {
                finish(ModelImportStatus.REJECTED)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun runImport(operation: () -> Boolean) {
        if (closed.get()) return
        val installed =
            try {
                operation()
            } catch (_: Exception) {
                false
            }
        finish(if (installed) ModelImportStatus.INSTALLED else ModelImportStatus.REJECTED)
    }

    private fun finish(value: ModelImportStatus) {
        if (!closed.get() && state.compareAndSet(ModelImportStatus.IMPORTING, value)) publish(value)
    }

    private fun publish(value: ModelImportStatus) {
        main.execute { if (!closed.get() && state.get() == value) notify(value) }
    }

    override fun close() {
        closed.set(true)
    }
}
