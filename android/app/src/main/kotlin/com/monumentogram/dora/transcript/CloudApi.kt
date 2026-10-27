package com.monumentogram.dora.transcript

import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.transcript.CloudConsent
import com.monumentogram.dora.audio.transcript.CloudTranscriptResult

internal data class CloudJobState(
    val id: String,
    val status: String,
    val uploadedBytes: Int,
    val retryable: Boolean,
    val cleanupPending: Boolean,
    val result: CloudTranscriptResult?,
)

internal interface CloudApi {
    val endpoint: String

    fun register(source: OriginalAudioReference)

    fun create(
        source: OriginalAudioReference,
        id: String,
        language: String,
        consent: CloudConsent,
    ): CloudJobState

    fun status(id: String): CloudJobState

    fun retry(id: String, cancelled: () -> Boolean): CloudJobState

    fun upload(
        id: String,
        offset: Int,
        bytes: ByteArray,
        final: Boolean,
        cancelled: () -> Boolean,
    ): CloudJobState

    fun cancel(id: String): CloudJobState

    fun abortTransfer()
}

internal class CloudFailure(val code: String) : IllegalStateException("Cloud operation failed")
