package com.monumentogram.dora.poc.capture.runtime

internal enum class CaptureStartStage(val code: String) {
    FOREGROUND_SERVICE("CAPTURE_START_FOREGROUND_SERVICE"),
    SYSTEM_SNAPSHOT("CAPTURE_START_SYSTEM_SNAPSHOT"),
    PRIVATE_FILE("CAPTURE_START_PRIVATE_FILE"),
    AUDIO_RECORD("CAPTURE_START_AUDIO_RECORD"),
    FIXTURE_PLAYBACK("CAPTURE_START_FIXTURE_PLAYBACK"),
    INITIAL_STATE("CAPTURE_START_INITIAL_STATE"),
    UNKNOWN("CAPTURE_START_UNKNOWN"),
}

internal class CaptureStartException(
    val stage: CaptureStartStage,
    cause: Throwable,
) : IllegalStateException(stage.code, cause)

internal fun captureStartFailureMessage(error: Throwable): String {
    val failure = error as? CaptureStartException
    val stage = failure?.stage ?: CaptureStartStage.UNKNOWN
    val causeType = (failure?.cause ?: error).javaClass.simpleName.ifBlank { "Throwable" }
    return "Запись не началась: ${stage.code} ($causeType)"
}
