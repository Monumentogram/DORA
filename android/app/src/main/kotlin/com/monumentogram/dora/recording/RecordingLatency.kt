@file:Suppress("TooManyFunctions") // One bounded owner for correlated capture/durability telemetry.

package com.monumentogram.dora.recording

/**
 * Bounded, content-free diagnostics. Disabled outside debuggable builds. All times are monotonic.
 */
internal class RecordingLatency(
    val enabled: Boolean,
    private val presentationTrace: (String) -> Unit = {},
    private val now: () -> Long = System::nanoTime,
) {
    @Volatile var drawingFrameNanos: Long = 0

    data class Sample(
        val sequence: Long,
        val kind: String,
        val start: Long,
        val events: Map<String, Long>,
    )

    private data class Entry(val sequence: Long, val kind: String, val start: Long) {
        val events = linkedMapOf<String, Long>()
        val draws = linkedMapOf<String, Long>()
        val candidates = mutableListOf<Draw>()
        var durableTarget: Long? = null
    }

    private data class Draw(val label: String, val at: Long, val vsync: Long)

    private val entries = ArrayDeque<Entry>()
    private var sequence = 0L
    private var recordingOwner: String? = null
    private var presentationEpoch = 0L
    private var presentationOwner = 0L
    private var presentationEvent = 0L
    private var appendStart: Long? = null
    private val spans = ArrayDeque<Pair<Long, Long>>()
    private val timers = ArrayDeque<Triple<Long, Long, Long>>()

    @Synchronized fun newPresentationEpoch(): Long = if (enabled) ++presentationEpoch else 0L

    /** Every status display-list write is witnessed, including terminal/unknown operations. */
    @Synchronized
    fun presentation(event: String, window: Long, screen: Long = 0L, detail: String = "") {
        if (!enabled || window == 0L) return
        presentationTrace(
            "DORA_UI $event n=${++presentationEvent} w=$window s=$screen r=$presentationOwner $detail"
        )
    }

    @Synchronized
    fun appendEvent(event: String) {
        if (!enabled) return
        if (event == "append_start") {
            appendStart = now()
        } else if (event == "append_end") {
            val start = appendStart ?: return
            spans.addLast(start to now())
            appendStart = null
            if (spans.size > SPAN_LIMIT) spans.removeFirst()
        }
    }

    @Synchronized
    fun timer(frames: Long) {
        if (!enabled) return
        val seconds = frames / SAMPLE_RATE
        if (timers.lastOrNull()?.first == seconds) return
        timers.addLast(Triple(seconds, now(), drawingFrameNanos))
        if (timers.size > LIMIT) timers.removeFirst()
    }

    @Synchronized
    fun begin(kind: String, tap: Long = now()): Long {
        if (!enabled) return 0
        sequence++
        entries.addLast(Entry(sequence, kind, tap))
        if (entries.size > LIMIT) entries.removeFirst()
        mark(sequence, "handler")
        return sequence
    }

    @Synchronized
    fun mark(operation: Long, event: String) {
        if (!enabled) return
        entries
            .find { it.sequence == operation }
            ?.let { it.events.putIfAbsent(event, now() - it.start) }
    }

    @Synchronized
    fun kind(operation: Long): String? = entries.find { it.sequence == operation }?.kind

    @Synchronized
    fun value(operation: Long, event: String, value: Long) {
        if (enabled) entries.find { it.sequence == operation }?.events?.put(event, value)
    }

    @Synchronized
    fun draw(operation: Long, label: String, vsync: Long) {
        if (!enabled) return
        entries
            .find { it.sequence == operation }
            ?.let {
                if (
                    it.candidates.count { draw -> draw.label == label } < DRAW_LIMIT &&
                        it.candidates.none { draw -> draw.label == label && draw.vsync == vsync }
                )
                    it.candidates.add(Draw(label, now(), vsync))
                it.draws.putIfAbsent(label, vsync)
                it.events.putIfAbsent(label + "_vsync", vsync)
                it.events.putIfAbsent(label + "_draw", now() - it.start)
            }
    }

    @Synchronized
    fun recordingStarted(owner: String) {
        retireDurability()
        recordingOwner = owner
        if (enabled) presentationOwner++
    }

    @Synchronized
    fun awaitDurability(operation: Long, frames: Long, owner: String) {
        if (enabled && owner == recordingOwner)
            entries.find { it.sequence == operation }?.durableTarget = frames
    }

    @Synchronized
    fun retireDurability() {
        recordingOwner = null
        entries.forEach { it.durableTarget = null }
    }

    @Synchronized
    fun durableThrough(frames: Long) {
        if (!enabled) return
        entries.forEach { entry ->
            if (entry.durableTarget?.let { it <= frames } == true) {
                entry.events.putIfAbsent("durability_caught_up", now() - entry.start)
                entry.durableTarget = null
            }
        }
    }

    @Synchronized
    fun samples(): List<Sample> = entries.map {
        Sample(it.sequence, it.kind, it.start, it.events.toMap())
    }

    @Synchronized
    fun dump(): String =
        samples().joinToString("\n") { sample ->
            "latency sequence=${sample.sequence} kind=${sample.kind} tap=${sample.start} " +
                sample.events.entries.joinToString(" ") { "${it.key}=${it.value}" }
        } +
            entries
                .flatMap { entry ->
                    entry.candidates.map { draw ->
                        "draw_candidate sequence=${entry.sequence} label=${draw.label} " +
                            "draw=${draw.at} vsync=${draw.vsync}"
                    }
                }
                .joinToString("\n", prefix = "\n") +
            spans.joinToString("\n", prefix = "\n") {
                "append_span start=${it.first} end=${it.second}"
            } +
            timers.joinToString("\n", prefix = "\n") {
                "timer seconds=${it.first} draw=${it.second} vsync=${it.third}"
            }

    private companion object {
        const val LIMIT = 64
        const val DRAW_LIMIT = 16
        const val SPAN_LIMIT = 256
        const val SAMPLE_RATE = 16_000L
    }
}
