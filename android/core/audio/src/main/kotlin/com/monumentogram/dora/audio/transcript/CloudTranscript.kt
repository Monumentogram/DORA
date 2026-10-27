package com.monumentogram.dora.audio.transcript

/** Recording/source scope is the containing encrypted document, never a global preference. */
data class CloudConsent(
    val version: String = VERSION,
    val source: String = "MANUAL_USER_ACTION",
    val grantedAt: Long,
    val revoked: Boolean = false,
    val destination: String = "https://dora.invalid",
) {
    fun validate() {
        require(version == VERSION && source == "MANUAL_USER_ACTION" && grantedAt >= 0)
        require(destination.startsWith("https://") && destination.length <= MAX_DESTINATION_LENGTH)
    }

    companion object {
        const val VERSION = "dora-cloud-manual-v1"
        private const val MAX_DESTINATION_LENGTH = 2048
    }
}

data class CloudSegment(
    val text: String,
    val startMs: Long?,
    val endMs: Long?,
    val confidence: Double?,
) {
    override fun toString() = "CloudSegment(redacted)"
}

data class CloudTranscriptResult(
    val text: String,
    val providerId: String,
    val model: String,
    val providerJobId: String,
    val sourceDigest: String,
    val sourceVersion: Int,
    val completedAt: Long,
    val segments: List<CloudSegment> = emptyList(),
) {
    override fun toString() = "CloudTranscriptResult(redacted)"

    internal fun validate(doc: TranscriptDocument, language: String) {
        require(sourceDigest == doc.source.digest && sourceVersion == doc.source.version)
        require(providerId == "amazon-transcribe" && completedAt >= 0)
        require(
            model == "standard-batch:eu-central-1:" + if (language == "ru") "ru-RU" else "en-US"
        )
        require(providerJobId.matches(Regex("[A-Za-z0-9._-]{1,200}")))
        TranscriptReducer.validText(text)
        require(segments.size <= MAX_SEGMENTS)
        require(
            segments.sumOf { it.text.toByteArray(Charsets.UTF_8).size.toLong() } <=
                TranscriptCodec.MAX_TEXT_BYTES
        )
        val duration = doc.source.frames * MILLIS_PER_SECOND / SAMPLE_RATE_HZ
        segments.forEach {
            TranscriptReducer.validText(it.text)
            require(it.startMs == null || it.startMs in 0..duration)
            require(it.endMs == null || it.endMs in 0..duration)
            if (it.startMs != null && it.endMs != null)
                require(it.startMs in 0..it.endMs && it.endMs <= duration)
            require(it.confidence == null || it.confidence.isFinite() && it.confidence in 0.0..1.0)
        }
    }

    companion object {
        const val MAX_SEGMENTS = 10000
        private const val MILLIS_PER_SECOND = 1000
        private const val SAMPLE_RATE_HZ = 16000
    }
}
