package com.monumentogram.dora.vad

/** Frozen by a separate calibration campaign, before acceptance. No UI-owned thresholds. */
@Suppress("MagicNumber") // This immutable profile is the named, hash-verified threshold definition.
class SegmentationProfile private constructor() {
    val version = 1
    val engine = "sherpa-onnx"
    val model = "Silero VAD 6.2.1"
    val api = "Vad.compute"
    val debug = false
    val builderSegmentationUsed = false
    val reset =
        "START_PAUSE_RESUME_COVERAGE_GAP_FAILURE_RECOVERY_CAPTURE_RECREATION; " +
            "clear context/neural state and silence continuity; " +
            "clamp pre-roll to continuity start"
    val id = "dora-segmentation-1"
    val sha256 = "1c5ceb70f08593dfa96e522f43c51b7b28e36ab8b65a423fae417cc4e1cd2292"
    val engineVersion = "1.13.8"
    val engineSource = "11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf"
    val modelSha256 = "1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3"
    val aarSha256 = "64969d0fbf5d3936a127879684bc2f14014b99817ca3c9b2e16298c1372208db"
    val sampleRate = 16_000
    val windowFrames = 512
    val contextFrames = 64
    val probabilityThreshold = 0.5f
    val onsetFrames = 4_800L
    val hysteresisFrames = 16_000L
    val preRollFrames = 32_000L
    val semanticSilenceFrames = 1_440_000L
    val technicalCapFrames = 9_600_000L
    val overlapFrames = 32_000L
    val provider = "cpu"
    val numThreads = 1
    val builderMinSpeechSeconds = 0.032f
    val builderMinSilenceSeconds = 0.032f
    val builderMaxSpeechSeconds = 600f

    /** Stable UTF-8 JSON used by the prospective calibration freeze. No runtime inputs. */
    fun canonicalJson(): String =
        sortedMapOf<String, Any>(
                "aarSha256" to aarSha256,
                "api" to api,
                "builderMaxSpeechSeconds" to builderMaxSpeechSeconds.toInt(),
                "builderMinSilenceSeconds" to builderMinSilenceSeconds,
                "builderMinSpeechSeconds" to builderMinSpeechSeconds,
                "builderSegmentationUsed" to builderSegmentationUsed,
                "contextFrames" to contextFrames,
                "debug" to debug,
                "engine" to engine,
                "engineSource" to engineSource,
                "engineVersion" to engineVersion,
                "hysteresisFrames" to hysteresisFrames,
                "id" to id,
                "model" to model,
                "modelSha256" to modelSha256,
                "numThreads" to numThreads,
                "onsetFrames" to onsetFrames,
                "overlapFrames" to overlapFrames,
                "preRollFrames" to preRollFrames,
                "provider" to provider,
                "reset" to reset,
                "sampleRate" to sampleRate,
                "semanticSilenceFrames" to semanticSilenceFrames,
                "technicalCapFrames" to technicalCapFrames,
                "threshold" to probabilityThreshold,
                "version" to version,
                "windowFrames" to windowFrames,
            )
            .entries
            .joinToString(",", "{", "}") { (key, value) ->
                "\"$key\":" + if (value is String) "\"$value\"" else value.toString()
            }

    companion object {
        val FROZEN = SegmentationProfile()
    }
}
