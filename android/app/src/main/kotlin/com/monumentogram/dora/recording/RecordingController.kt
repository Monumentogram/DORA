@file:Suppress(
    "ReturnCount",
    "TooManyFunctions",
    "LongMethod",
    "CyclomaticComplexMethod",
) // Serialized state transitions fence stale actions and share terminal cleanup.

package com.monumentogram.dora.recording

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.persistence.runtime.AndroidProductAudioRuntime
import com.monumentogram.dora.audio.recording.RecordingAccess
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.audio.recording.RecordingSession
import com.monumentogram.dora.audio.recording.RecordingState
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RecordingViewState(
    val recording: RecordingState = RecordingState(),
    val level: Float = 0f,
    val signalAtNanos: Long = 0,
    val pausePending: Boolean = false,
    val resumePending: Boolean = false,
    val timingOperation: Long = 0,
    val route: String = "Маршрут определяется при запуске",
    val failure: CaptureFailure? = null,
    val shortReads: Long = 0,
    val readErrors: Long = 0,
    val captureThreadHealthy: Boolean = false,
)

/** Application state survives Activity recreation; only the foreground service runs capture. */
class RecordingController(
    private val context: Context,
    private val runtime: AndroidProductAudioRuntime,
) {
    private val mutable = MutableStateFlow(RecordingViewState())
    val state = mutable.asStateFlow()
    internal val latency =
        RecordingLatency(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
    @Volatile private var timingOperation = 0L
    private var inputTimeNanos = 0L
    @Volatile private var lastAppendStages = emptyMap<String, Long>()

    fun inputAt(nanos: Long) {
        inputTimeNanos = nanos
    }

    fun rendered(snapshot: RecordingViewState) {
        if (!latency.enabled) return
        val operation = snapshot.timingOperation
        val kind = latency.kind(operation) ?: return
        val label =
            when {
                snapshot.pausePending || snapshot.resumePending -> "ack"
                kind == "pause" && snapshot.recording.phase == RecordingPhase.PAUSED -> "confirmed"
                kind == "resume" && snapshot.recording.phase == RecordingPhase.RECORDING ->
                    "confirmed"
                else -> return
            }
        latency.draw(operation, label, latency.drawingFrameNanos)
    }

    private fun beginTiming(kind: String): Long {
        if (!latency.enabled) return 0
        val now = System.nanoTime()
        val tap = inputTimeNanos.takeIf { it > 0 } ?: now
        inputTimeNanos = 0
        return latency.begin(kind, tap).also { timingOperation = it }
    }

    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "Dora recording persistence")
    }
    private val capture = AudioRecordCapture(context)
    private val starting = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val pauseRequested = AtomicBoolean(false)
    private val resumes = ResumeRequests()
    private val serviceOwner = RecordingServiceOwner()
    @Volatile private var timeline = CapturedTimeline(0, 0)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var presentationActive = false
    private val presentationTick =
        object : Runnable {
            override fun run() {
                if (!presentationActive) return
                refreshCapturePresentation()
                if (presentationActive) main.postDelayed(this, UI_PERIOD_MILLIS)
            }
        }
    private var session: RecordingSession? = null
    @Volatile private var maximumAppendNanos = 0L
    @Volatile private var access: RecordingAccess? = null
    @Volatile private var serviceActive = false
    @Volatile
    var actionToken: String? = null
        private set

    private var awaitingServiceStop = false
    private var servicePending = false
    @Volatile private var servicePresent = false
    private var shutdownUnconfirmed = false
    @Volatile var onTerminal: (() -> Unit)? = null

    init {
        worker.scheduleWithFixedDelay(
            { tick() },
            UI_PERIOD_MILLIS,
            UI_PERIOD_MILLIS,
            TimeUnit.MILLISECONDS,
        )
    }

    fun availableBytes(): Long = StatFs(context.noBackupFilesDir.path).availableBytes

    /** Aggregate health only, exposed through Android's privileged service dump. No IDs or PCM. */
    fun diagnosticSummary(): String =
        "phase=${state.value.recording.phase} queue=${capture.queuedBlocks} " +
            "queueHighWater=${capture.maximumQueuedBlocks} maxAppendNanos=$maximumAppendNanos " +
            "frames=${state.value.recording.frames} durableFrames=${state.value.recording.durableFrames} " +
            "shortReads=${capture.shortReads} readErrors=${capture.readErrors} healthy=${capture.healthy}\n" +
            latency.dump() +
            "\nappend_stages " +
            lastAppendStages.entries.joinToString(" ") { "${it.key}=${it.value}" }

    fun start(activity: Activity, recoveredIdentity: AudioIdentity? = null) {
        if (!starting.compareAndSet(false, true)) return
        if (state.value.recording.phase in ACTIVE_PHASES) {
            starting.set(false)
            return
        }
        stopRequested.set(false)
        pauseRequested.set(false)
        presentationActive = true
        main.removeCallbacks(presentationTick)
        main.post(presentationTick)
        val token = id()
        actionToken = token
        worker.execute {
            session = null
            mutable.value =
                RecordingViewState(recording = RecordingState(phase = RecordingPhase.PREPARING))
        }
        if (availableBytes() < MINIMUM_FREE_BYTES) {
            worker.execute { failBeforeStart(CaptureFailure.STORAGE_FULL) }
            return
        }
        val identity =
            recoveredIdentity ?: AudioIdentity(RecordingId(id()), AudioAssetId(id()), id())
        val completion: (AudioResult<RecordingAccess>) -> Unit = { result ->
            worker.execute {
                if (actionToken != token) {
                    if (result is AudioResult.Value) result.value.close()
                    return@execute
                }
                when (result) {
                    is AudioResult.Failed -> {
                        stopPresentation()
                        mutable.update {
                            it.copy(
                                recording =
                                    RecordingState(
                                        phase = RecordingPhase.INTERRUPTED,
                                        persistenceFailure = result.reason,
                                    )
                            )
                        }
                        actionToken = null
                        starting.set(false)
                    }
                    is AudioResult.Value -> {
                        access = result.value
                        if (stopRequested.get()) {
                            release()
                            publishEmpty()
                        } else
                            try {
                                servicePending = true
                                ContextCompat.startForegroundService(
                                    context,
                                    ProductRecordingService.startIntent(
                                        context,
                                        token,
                                    ),
                                )
                            } catch (_: Exception) {
                                servicePending = false
                                release()
                                failBeforeStart(CaptureFailure.SERVICE_REJECTED)
                            }
                    }
                }
            }
        }
        if (recoveredIdentity == null) runtime.requestRecording(activity, identity, completion)
        else runtime.requestRecordingContinuation(activity, identity, completion)
    }

    /**
     * Called only by the service after successful startForeground. Null/replayed intents do not
     * mint authority.
     */
    @Suppress(
        "ThrowsCount",
        "ComplexCondition",
    ) // Recheck each independent authority at the native boundary.
    fun serviceStart(token: String?) {
        val owner = serviceOwner.snapshot()
        worker.execute {
            if (token == null || token != actionToken) return@execute
            if (!serviceOwner.isCurrent(owner)) return@execute
            if (serviceActive) return@execute
            val selected = access ?: return@execute
            serviceActive = true
            if (!starting.get() || stopRequested.get()) {
                release()
                publishEmpty()
                return@execute
            }
            try {
                selected.onRevocation {
                    capture.requestStop()
                    worker.execute {
                        if (access === selected && serviceActive)
                            failPersistence(AudioFailure.LOCKED)
                    }
                }
                timeline =
                    CapturedTimeline(
                        capture.admittedFrames,
                        selected.continuation?.summary?.frames ?: 0L,
                    )
                // Establish the logical owner before admitting PCM. An immediate Stop/Pause then
                // drains into this same session, including frames captured during native startup.
                val created =
                    RecordingSession(
                        selected.identity,
                        selected.writer,
                        timing = latency::appendEvent,
                        appendTiming =
                            if (latency.enabled) ({ stages -> lastAppendStages = stages })
                            else null,
                    )
                session = created
                val continuation = selected.continuation
                if (continuation != null)
                    created.restore(
                        checkNotNull(continuation.summary).frames,
                        continuation.nextOrdinal,
                    )
                if (!(if (continuation != null) created.resume() else created.start())) {
                    terminate()
                    return@execute
                }
                if (state.value.recording.stopConfirmation) created.requestStop()
                if (stopRequested.get()) return@execute
                val initialStart: (() -> Unit) -> Unit = { nativeStart ->
                    selected.activate {
                        if (
                            !serviceOwner.runCurrent(owner) {
                                if (
                                    access !== selected ||
                                        token != actionToken ||
                                        stopRequested.get() ||
                                        pauseRequested.get()
                                )
                                    throw ResumeCancelled()
                                nativeStart()
                            }
                        )
                            throw ResumeCancelled()
                    }
                }
                try {
                    capture.start(initialStart)
                } catch (_: ResumeCancelled) {
                    if (stopRequested.get() || !serviceOwner.isCurrent(owner)) return@execute
                    if (pauseRequested.get())
                        selected.activate {
                            if (
                                !serviceOwner.isCurrent(owner) ||
                                    access !== selected ||
                                    token != actionToken
                            )
                                throw ResumeCancelled()
                        }
                    else throw ResumeCancelled()
                }
                if (pauseRequested.get()) pauseNow() else publish()
            } catch (error: CaptureException) {
                fail(error.failure)
            } catch (_: Exception) {
                failPersistence(AudioFailure.LOCKED)
            }
        }
    }

    fun serviceCreated() {
        serviceOwner.created()
        servicePresent = true
        worker.execute { servicePending = false }
    }

    fun pause(token: String? = actionToken) {
        if (token == null || token != actionToken) return
        resumes.cancel()
        if (pauseRequested.getAndSet(true)) return
        val operation = beginTiming("pause")
        val boundary = capture.requestStop { event -> latency.mark(operation, event) }
        latency.mark(operation, "stop_signal")
        latency.value(operation, "fence_frames", boundary.frames)
        latency.value(operation, "last_admission", boundary.lastAcceptedNanos)
        mutable.update {
            it.copy(
                pausePending = it.recording.phase == RecordingPhase.RECORDING || it.resumePending,
                resumePending = false,
                level = 0f,
                timingOperation = operation,
            )
        }
        worker.execute { if (token == actionToken) pauseNow() }
    }

    private fun pauseNow() {
        latency.mark(timingOperation, "pause_worker")
        val current = session ?: return
        if (current.state.phase != RecordingPhase.RECORDING) {
            mutable.update { it.copy(pausePending = false) }
            return
        }
        try {
            capture.stop()
            capture.failure?.let {
                fail(it)
                return
            }
            latency.mark(timingOperation, "drain_start")
            capture.drain(consume = current::accept)
            latency.mark(timingOperation, "drain_end")
            latency.mark(timingOperation, "tail_start")
            current.pause()
            latency.mark(timingOperation, "tail_end")
            publish()
            if (current.state.phase == RecordingPhase.PAUSED)
                latency.mark(timingOperation, "paused_publication")
            if (current.state.phase == RecordingPhase.INTERRUPTED) terminate()
        } catch (error: CaptureException) {
            fail(error.failure)
        }
    }

    @Suppress("ComplexCondition") // Every condition independently fences the final native start.
    fun resume(activity: Activity) {
        if (state.value.recording.phase != RecordingPhase.PAUSED) return
        val selected = access ?: return
        val token = actionToken ?: return
        val owner = serviceOwner.snapshot() ?: return
        if (!serviceActive || !servicePresent || stopRequested.get()) return
        val request = resumes.begin(selected) ?: return
        val operation = beginTiming("resume")
        mutable.update { it.copy(timingOperation = operation, resumePending = true) }
        pauseRequested.set(false)
        latency.mark(operation, "auth_start")
        runtime.requestRecordingResume(
            activity,
            selected,
            authorityRoute = { fresh ->
                latency.mark(operation, if (fresh) "auth_fresh" else "auth_current")
            },
        ) { result ->
            latency.mark(operation, "auth_end")
            worker.execute {
                if (!resumes.isCurrent(request, access) || token != actionToken) return@execute
                try {
                    if (result !is AudioResult.Value) {
                        latency.mark(operation, "auth_denied")
                        return@execute
                    }
                    val current = session ?: return@execute
                    if (
                        current.state.phase != RecordingPhase.PAUSED ||
                            stopRequested.get() ||
                            pauseRequested.get() ||
                            !serviceActive ||
                            !servicePresent
                    )
                        return@execute
                    capture.start(
                        withStartAuthority = { nativeStart ->
                            result.value.consume {
                                if (
                                    !serviceOwner.runCurrent(owner) {
                                        if (
                                            !(resumes.isCurrent(request, access) &&
                                                token == actionToken &&
                                                serviceActive &&
                                                servicePresent &&
                                                !stopRequested.get() &&
                                                !pauseRequested.get())
                                        )
                                            throw ResumeCancelled()
                                        nativeStart()
                                    }
                                )
                                    throw ResumeCancelled()
                            }
                        },
                        timing = { event -> latency.mark(operation, event) },
                    )
                    // Establish provenance even if Stop/Pause arrived during native start.
                    // Accepted frames are then drained by that command; they are never discarded.
                    check(current.resume())
                    latency.mark(operation, "segment_ready")
                    if (pauseRequested.get()) pauseNow()
                    else if (!stopRequested.get()) {
                        publish()
                        latency.mark(operation, "recording_publication")
                    }
                } catch (_: ResumeCancelled) {
                    latency.mark(operation, "cancelled")
                } catch (error: CaptureException) {
                    fail(error.failure)
                } catch (_: Exception) {
                    failPersistence(AudioFailure.LOCKED)
                } finally {
                    if (resumes.complete(request)) mutable.update { it.copy(resumePending = false) }
                }
            }
        }
    }

    fun requestStop() {
        mutable.update { it.copy(recording = it.recording.copy(stopConfirmation = true)) }
        worker.execute {
            session?.requestStop()
            publish()
        }
    }

    fun cancelStop() {
        mutable.update { it.copy(recording = it.recording.copy(stopConfirmation = false)) }
        worker.execute {
            session?.cancelStop()
            publish()
        }
    }

    fun confirmStop() {
        if (!state.value.recording.stopConfirmation || !stopRequested.compareAndSet(false, true))
            return
        resumes.cancel()
        mutable.update { it.copy(resumePending = false) }
        capture.stopReading()
        worker.execute {
            val current = session
            if (current == null) {
                if (access != null) {
                    release()
                    publishEmpty()
                }
                return@execute
            }
            try {
                capture.stop()
                capture.failure?.let {
                    fail(it)
                    return@execute
                }
                capture.drain(consume = current::accept)
                current.requestStop()
                mutable.update {
                    it.copy(
                        level = 0f,
                        recording =
                            current.state.copy(
                                phase = RecordingPhase.FINALIZING,
                                stopConfirmation = false,
                            ),
                    )
                }
                current.confirmStop()
                publish()
                release()
            } catch (error: CaptureException) {
                fail(error.failure)
            } catch (_: Exception) {
                failPersistence(AudioFailure.UNAVAILABLE)
            }
        }
    }

    fun serviceRejected() = worker.execute { fail(CaptureFailure.SERVICE_REJECTED) }

    fun serviceDestroyed() {
        val destroyedToken = actionToken
        serviceOwner.destroyed()
        servicePresent = false
        resumes.cancel()
        capture.stopReading()
        worker.execute {
            servicePending = false
            val ownsPreparation =
                destroyedToken != null && destroyedToken == actionToken && starting.get()
            if (serviceActive || ownsPreparation) fail(CaptureFailure.SERVICE_DESTROYED)
            if (awaitingServiceStop && !capture.hasLiveThread) {
                awaitingServiceStop = false
                starting.set(false)
            }
        }
    }

    private fun tick() {
        if (shutdownUnconfirmed) {
            if (!capture.hasLiveThread) terminate()
            return
        }
        val current = session ?: return
        if (!serviceActive || current.state.phase != RecordingPhase.RECORDING) return
        try {
            // A finite quantum guarantees queued commands run even under sustained storage load.
            capture.drain(DRAIN_BLOCKS_PER_TICK, current::accept)
            if (current.state.phase == RecordingPhase.INTERRUPTED) {
                terminate()
                return
            }
            capture.failure?.let {
                fail(it)
                return
            }
            if (availableBytes() < MINIMUM_FREE_BYTES) {
                fail(CaptureFailure.STORAGE_FULL)
                return
            }
            publish()
        } catch (_: Exception) {
            failPersistence(AudioFailure.UNAVAILABLE)
        }
    }

    private fun fail(failure: CaptureFailure) {
        mutable.update { it.copy(failure = failure) }
        session?.interrupt()
        terminate()
    }

    private fun failPersistence(reason: AudioFailure) {
        session?.interrupt(reason)
        mutable.update {
            it.copy(
                recording =
                    it.recording.copy(
                        phase = RecordingPhase.INTERRUPTED,
                        persistenceFailure = reason,
                    )
            )
        }
        terminate()
    }

    private fun terminate() {
        try {
            capture.stop()
        } catch (_: CaptureException) {
            shutdownUnconfirmed = true
            mutable.update {
                it.copy(
                    level = 0f,
                    failure = CaptureFailure.THREAD_TIMEOUT,
                    recording = it.recording.copy(phase = RecordingPhase.INTERRUPTED),
                )
            }
            return
        }
        shutdownUnconfirmed = false
        capture.drain {}
        if (session == null)
            mutable.update {
                it.copy(recording = it.recording.copy(phase = RecordingPhase.INTERRUPTED))
            }
        publish()
        release()
    }

    private fun release() {
        check(!capture.hasLiveThread)
        stopPresentation()
        actionToken = null
        resumes.cancel()
        mutable.update { it.copy(pausePending = false, resumePending = false) }
        val previous = access
        access = null
        previous?.close()
        awaitingServiceStop = servicePresent || servicePending
        serviceActive = false
        if (!awaitingServiceStop) starting.set(false)
        onTerminal?.invoke()
    }

    private fun publishEmpty() {
        stopPresentation()
        mutable.value = RecordingViewState(RecordingState(phase = RecordingPhase.EMPTY))
        if (!awaitingServiceStop) starting.set(false)
        onTerminal?.invoke()
    }

    private fun failBeforeStart(reason: CaptureFailure) {
        stopPresentation()
        actionToken = null
        mutable.value =
            RecordingViewState(RecordingState(phase = RecordingPhase.INTERRUPTED), failure = reason)
        starting.set(false)
    }

    private fun publish() {
        val current = session ?: return
        maximumAppendNanos = current.maximumAppendNanos
        val signal = capture.signal
        val fresh = System.nanoTime() - signal.atNanos < SIGNAL_STALE_NANOS
        mutable.update {
            it.copy(
                recording =
                    current.state.let { value ->
                        if (value.phase == RecordingPhase.RECORDING)
                            value.copy(
                                frames =
                                    maxOf(value.frames, timeline.frames(capture.admittedFrames))
                            )
                        else value
                    },
                pausePending =
                    pauseRequested.get() && current.state.phase == RecordingPhase.RECORDING,
                resumePending = it.resumePending && current.state.phase == RecordingPhase.PAUSED,
                signalAtNanos = signal.atNanos,
                level =
                    if (current.state.phase == RecordingPhase.RECORDING && fresh) signal.level
                    else 0f,
                route = capture.route,
                shortReads = capture.shortReads,
                readErrors = capture.readErrors,
                captureThreadHealthy = capture.healthy,
            )
        }
    }

    /**
     * Native counters only: no worker/disk waits, wall-clock extrapolation, or durability claims.
     */
    private fun refreshCapturePresentation() {
        if (state.value.recording.phase != RecordingPhase.RECORDING) return
        val captured = timeline.frames(capture.admittedFrames)
        val signal = capture.signal
        val fresh = System.nanoTime() - signal.atNanos < SIGNAL_STALE_NANOS
        mutable.update {
            if (it.recording.phase != RecordingPhase.RECORDING) it
            else
                it.copy(
                    recording = it.recording.copy(frames = maxOf(it.recording.frames, captured)),
                    signalAtNanos = signal.atNanos,
                    level = if (fresh && !it.pausePending) signal.level else 0f,
                    route = capture.route,
                )
        }
    }

    private fun stopPresentation() {
        presentationActive = false
        main.removeCallbacks(presentationTick)
    }

    private class ResumeCancelled : IllegalStateException()

    companion object {
        private const val UI_PERIOD_MILLIS = 50L
        private const val DRAIN_BLOCKS_PER_TICK = 20
        private const val SIGNAL_STALE_NANOS = 250_000_000L
        private const val MINIMUM_FREE_BYTES = 16L * 1024 * 1024
        private val ACTIVE_PHASES =
            setOf(
                RecordingPhase.PREPARING,
                RecordingPhase.RECORDING,
                RecordingPhase.PAUSED,
                RecordingPhase.FINALIZING,
            )

        private fun id() = UUID.randomUUID().toString()
    }
}
