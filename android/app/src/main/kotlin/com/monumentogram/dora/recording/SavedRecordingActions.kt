package com.monumentogram.dora.recording

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.ProductAudioSession
import java.util.concurrent.Executors

/** Ordinary local actions; no command interface, injected authority or retained PCM. */
internal class SavedRecordingActions(private val activity: MainActivity) {
    private val runtime = (activity.application as DoraApplication).audioRuntime
    private val worker = Executors.newSingleThreadExecutor()
    private var attempt = 0L
    private var closed = false
    var busy by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    fun clearVisible() {
        message = null
    }

    fun close() {
        closed = true
        attempt++
        message = null
        worker.shutdown()
    }

    fun verify(identity: AudioIdentity) = open { request, session ->
        worker.execute {
            val result = SavedRecordingVerification.verify(session, identity)
            activity.runOnUiThread {
                publish(request, session) {
                    when (result) {
                        is AudioResult.Value ->
                            "Аудио проверено: ${result.value.frames} кадров · ${result.value.units} блоков"
                        is AudioResult.Failed -> "Проверка аудио не завершена: ${result.reason}"
                    }
                }
            }
        }
    }

    fun delete(identity: AudioIdentity) = open { request, session ->
        runtime.requestAudioDeletion(activity, session, identity) { result ->
            if (!active(request)) return@requestAudioDeletion
            if (result is AudioResult.Failed) {
                publish(request, session) { "Удаление не подтверждено: ${result.reason}" }
            } else
                worker.execute {
                    val inspected = session.inspectAudioDeletion(identity)
                    activity.runOnUiThread {
                        publish(request, session) {
                            if (inspected is AudioResult.Value && inspected.value.complete)
                                "Аудио удалено. Удаление проверено."
                            else "Удаление не подтверждено: ${AudioFailure.INCOMPLETE}"
                        }
                    }
                }
        }
    }

    private fun open(action: (Long, ProductAudioSession) -> Unit) {
        if (busy || closed) return
        val request = ++attempt
        busy = true
        message = null
        runtime.requestOpen(activity, AudioOpenMode.OPEN_EXISTING) { availability ->
            if (!active(request)) return@requestOpen
            if (availability is AudioAvailability.Available && current(availability.session)) {
                action(request, availability.session)
            } else {
                busy = false
                message = "Доступ не подтверждён. Разблокируйте DORA и повторите действие."
            }
        }
    }

    private fun publish(request: Long, session: ProductAudioSession, text: () -> String) {
        if (!active(request)) return
        busy = false
        message = if (current(session)) text() else null
    }

    private fun active(request: Long) =
        !closed && request == attempt && !activity.isDestroyed && !activity.isFinishing

    private fun current(session: ProductAudioSession) =
        activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            (runtime.availability as? AudioAvailability.Available)?.session === session
}
