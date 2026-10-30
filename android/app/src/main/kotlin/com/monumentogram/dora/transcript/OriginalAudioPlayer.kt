@file:Suppress(
    "TooGenericExceptionCaught"
) // AudioTrack/platform failures become visible retry states.

package com.monumentogram.dora.transcript

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AuthenticatedAudioSource
import com.monumentogram.dora.audio.transcript.PcmWindows
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Foreground-only playback. Extraction completes/authenticates before any sample is played. */
@Suppress("TooManyFunctions") // Track, focus and bounded source loading share one lifecycle owner.
internal class OriginalAudioPlayer(private val activity: MainActivity) : AutoCloseable {
    private val runtime = (activity.application as DoraApplication).audioRuntime
    private val worker = Executors.newSingleThreadExecutor()
    private val sourceCache = PlaybackSourceCache()
    private val generation = AtomicLong()
    private val manager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    private val focus =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (change != AudioManager.AUDIOFOCUS_GAIN) pause()
            }
            .build()
    private val trackLock = Any()
    private var track: AudioTrack? = null
    private var closed = false
    var playing by mutableStateOf(false)
        private set

    var loading by mutableStateOf(false)
        private set

    var failed by mutableStateOf(false)
        private set

    var frames by mutableStateOf(0L)
        private set

    var position by mutableStateOf(0L)
        private set

    fun describe(frameCount: Long) {
        if (
            !playing &&
                !loading &&
                frameCount in
                    1..com.monumentogram.dora.audio.recording.AlphaRecordingLimit.MAX_ALPHA_FRAMES
        )
            frames = frameCount
    }

    fun seek(frame: Long) {
        pause()
        position = frame.coerceIn(0, frames)
    }

    fun pause() {
        generation.incrementAndGet()
        playing = false
        loading = false
        synchronized(trackLock) {
            try {
                track?.pause()
                track?.flush()
            } catch (_: IllegalStateException) {
                /* Already stopped. */
            }
        }
        manager.abandonAudioFocusRequest(focus)
    }

    fun clear() {
        pause()
        if (!closed) worker.execute { sourceCache.close() }
        position = 0
        frames = 0
        failed = false
    }

    fun play(identity: AudioIdentity) {
        if (closed || playing || loading) return
        if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            failed = true
            return
        }
        val request = generation.incrementAndGet()
        val start = if (position >= frames) 0 else position
        loading = true
        failed = false
        worker.execute { prepareSource(request, start, identity) }
    }

    private fun prepareSource(request: Long, start: Long, identity: AudioIdentity) {
        try {
            if (generation.get() != request) return
            val retained = sourceCache.reuse(identity)
            if (retained != null) {
                playSource(request, start, retained)
            } else
                activity.runOnUiThread {
                    // A normal pause/seek reuses the existing runtime. Only a fresh source opens
                    // it.
                    if (generation.get() == request) openSource(request, start, identity)
                }
        } catch (_: Exception) {
            sourceCache.close()
            activity.runOnUiThread { finish(request, true) }
        }
    }

    @Suppress(
        "NestedBlockDepth"
    ) // Main-thread authorization hands off to a fenced playback worker.
    private fun openSource(request: Long, start: Long, identity: AudioIdentity) {
        runtime.requestOpen(activity, AudioOpenMode.OPEN_EXISTING) { result ->
            if (!current(request)) return@requestOpen
            if (result !is AudioAvailability.Available) {
                worker.execute { sourceCache.close() }
                finish(request, true)
            } else
                worker.execute {
                    try {
                        if (!current(request)) return@execute
                        val source =
                            sourceCache.remember(
                                identity,
                                result.session.originals.openAuthenticated(identity),
                            )
                        playSource(request, start, source)
                    } catch (_: Exception) {
                        sourceCache.close()
                        activity.runOnUiThread { finish(request, true) }
                    }
                }
        }
    }

    private fun playSource(
        request: Long,
        start: Long,
        opened: AudioResult<AuthenticatedAudioSource>,
    ) {
        try {
            check(opened is AudioResult.Value) { "SOURCE_UNAVAILABLE" }
            if (!current(request)) return
            val authenticated = opened.value
            stream(request, authenticated, start.coerceAtMost(authenticated.reference.frames))
            activity.runOnUiThread { finish(request, false) }
        } catch (_: Exception) {
            // Expected pause/seek cancellation keeps the snapshot; an active failure retires it.
            if (generation.get() == request) sourceCache.close()
            activity.runOnUiThread { finish(request, true) }
        } finally {
            if (!runtime.isRecordingUiAuthorized()) sourceCache.close()
        }
    }

    private fun current(request: Long) =
        generation.get() == request && runtime.isRecordingUiAuthorized()

    private fun finish(request: Long, error: Boolean) {
        if (generation.get() != request) return
        loading = false
        playing = false
        failed = error
        manager.abandonAudioFocusRequest(focus)
    }

    @Suppress(
        "LongMethod",
        "NestedBlockDepth",
    ) // Track and window lifetimes share one cleanup boundary.
    private fun stream(
        request: Long,
        authenticated: com.monumentogram.dora.audio.AuthenticatedAudioSource,
        start: Long,
    ) {
        val source = authenticated.reference
        val output =
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .build()
                )
                .setBufferSizeInBytes(BUFFER_FRAMES * Float.SIZE_BYTES)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        try {
            synchronized(trackLock) {
                if (!current(request)) return
                track = output
                output.play()
            }
            PcmWindows(authenticated, start) { !current(request) }
                .use { windows ->
                    var ready = false
                    while (current(request)) {
                        val window = windows.next() ?: break
                        window.use {
                            if (!ready) {
                                publishReady(request, source.frames, start)
                                ready = true
                            }
                            writeWindow(request, output, it.samples, start)
                        }
                    }
                }
            while (
                current(request) && output.playbackHeadPosition.toLong() < source.frames - start
            ) {
                publishPosition(request, start + output.playbackHeadPosition.toLong())
                Thread.sleep(POLL_MILLIS)
            }
            if (current(request)) publishPosition(request, source.frames)
        } finally {
            synchronized(trackLock) {
                if (track === output) track = null
                try {
                    output.pause()
                    output.flush()
                } finally {
                    output.release()
                }
            }
        }
    }

    private fun publishReady(request: Long, total: Long, start: Long) {
        activity.runOnUiThread {
            if (current(request)) {
                loading = false
                playing = true
                frames = total
                position = start
            }
        }
    }

    private fun writeWindow(request: Long, output: AudioTrack, samples: FloatArray, start: Long) {
        var next = 0
        while (current(request) && next < samples.size) {
            val written =
                output.write(
                    samples,
                    next,
                    minOf(BUFFER_FRAMES, samples.size - next),
                    AudioTrack.WRITE_NON_BLOCKING,
                )
            check(written >= 0)
            next += written
            publishPosition(request, start + output.playbackHeadPosition.toLong())
            Thread.sleep(POLL_MILLIS)
        }
    }

    private fun publishPosition(request: Long, frame: Long) {
        activity.runOnUiThread { if (current(request)) position = frame.coerceAtMost(frames) }
    }

    override fun close() {
        clear()
        closed = true
        worker.shutdown()
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val BUFFER_FRAMES = 1600
        const val POLL_MILLIS = 20L
    }
}
