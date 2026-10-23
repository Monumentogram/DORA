package com.monumentogram.dora.transcript

import java.io.File

/** The only native ASR entry; unsupported or unpackaged runtime fails closed. */
internal object WhisperNative {
    private val loaded: Boolean by lazy {
        try {
            System.loadLibrary("dora-whisper-jni")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun available(): Boolean = loaded && nativeAvailable() && LocalAsrModel.withinRetention()

    fun recognize(
        model: File,
        pcm: FloatArray,
        language: String,
        cancelled: () -> Boolean,
    ): String {
        check(available()) { "ASR_RUNTIME_UNAVAILABLE" }
        require(language == "ru" || language == "en") { "ASR_LANGUAGE_UNSUPPORTED" }
        require(
            pcm.isNotEmpty() &&
                pcm.size <= MAX_SAMPLES &&
                pcm.all { it.isFinite() && it in -1f..1f }
        ) {
            "ASR_PCM_INVALID"
        }
        check(LocalAsrModel.verify(model)) { "ASR_MODEL_UNAVAILABLE" }
        val probe = CancellationProbe { cancelled() || !LocalAsrModel.withinRetention() }
        check(!probe.cancelled()) { "ASR_CANCELLED" }
        return nativeRecognize(model.absolutePath, pcm, language, probe).also {
            check(!probe.cancelled()) { "ASR_CANCELLED" }
        }
    }

    private fun interface CancellationProbe {
        fun cancelled(): Boolean
    }

    private external fun nativeAvailable(): Boolean

    private external fun nativeRecognize(
        model: String,
        pcm: FloatArray,
        language: String,
        cancellation: CancellationProbe,
    ): String

    private const val MAX_SAMPLES = 300 * 16_000
}
