package com.monumentogram.dora.recording

import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.vad.VadFailure
import java.util.concurrent.CompletableFuture

/** Control-owner-only startup deadline and identity fence. Never holds recording authority. */
internal class RecordingPreparation(private val driver: Driver) {
    interface Driver {
        fun post(action: () -> Unit)

        fun schedule(task: Runnable, delayMillis: Long): () -> Unit
    }

    private class Pending(
        val owner: Any,
        val current: () -> Boolean,
        val settled: (VadFailure?) -> Unit,
        val cancelled: () -> Unit,
    ) {
        var cancelDeadline: () -> Unit = {}
    }

    private var pending: Pending? = null

    fun begin(
        owner: Any,
        initialized: CompletableFuture<VadFailure?>,
        current: () -> Boolean,
        settled: (VadFailure?) -> Unit,
        cancelled: () -> Unit,
    ) {
        cancel()
        val request = Pending(owner, current, settled, cancelled)
        pending = request
        request.cancelDeadline =
            driver.schedule(
                Runnable { settle(request, VadFailure.INITIALIZATION_FAILED) },
                TIMEOUT_MILLIS,
            )
        initialized.whenComplete { failure, error ->
            driver.post {
                settle(request, if (error == null) failure else VadFailure.INITIALIZATION_FAILED)
            }
        }
    }

    fun pending(owner: Any): Boolean = pending?.owner === owner

    /** Internal RECORDING owns Stop while native preparation has not admitted a microphone. */
    fun visiblePhase(owner: Any, phase: RecordingPhase, captureStarted: Boolean): RecordingPhase =
        if (phase == RecordingPhase.RECORDING && (pending(owner) || !captureStarted))
            RecordingPhase.PREPARING
        else phase

    fun cancel() {
        val request = pending ?: return
        pending = null
        request.cancelDeadline()
        request.cancelled()
    }

    private fun settle(request: Pending, failure: VadFailure?) {
        if (pending !== request) return
        pending = null
        request.cancelDeadline()
        if (request.current()) request.settled(failure) else request.cancelled()
    }

    private companion object {
        // Headroom over the observed ~3s cold-start coverage gap; measured factory time is
        // separate.
        const val TIMEOUT_MILLIS = 10_000L
    }
}
