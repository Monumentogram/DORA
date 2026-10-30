package com.monumentogram.dora.transcript

import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.transcript.CloudConsent
import com.monumentogram.dora.audio.transcript.CloudTranscriptResult
import com.monumentogram.dora.audio.transcript.PagedTranscriptSpec

internal data class CloudJobState(
    val id: String,
    val status: String,
    val uploadedBytes: Long,
    val retryable: Boolean,
    val cleanupPending: Boolean,
    val result: CloudTranscriptResult?,
    val parentId: String? = null,
    val partIndex: Int? = null,
    val firstFrame: Long? = null,
    val frames: Long? = null,
)

internal data class CloudRunQuote(
    val enabled: Boolean,
    val affordable: Boolean,
    val requiredMicroUsd: Long,
    val availableMicroUsd: Long,
    val partCount: Int,
    val windowPolicy: String,
    val maxFrames: Long,
    val maxPartFrames: Long,
)

internal data class CloudRunPart(
    val index: Int,
    val id: String,
    val firstFrame: Long,
    val frames: Long,
    val status: String,
)

internal data class CloudRunState(
    val id: String,
    val status: String,
    val sourceDigest: String,
    val sourceVersion: Int,
    val frames: Long,
    val windowPolicy: String,
    val parts: List<CloudRunPart>,
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

    fun quoteRun(spec: PagedTranscriptSpec): CloudRunQuote = throw CloudFailure("LONG_NOT_READY")

    fun createRun(spec: PagedTranscriptSpec): CloudRunState = throw CloudFailure("LONG_NOT_READY")

    fun runPart(id: String, index: Int): CloudJobState = throw CloudFailure("LONG_NOT_READY")

    fun upload(
        id: String,
        offset: Long,
        bytes: ByteArray,
        final: Boolean,
        cancelled: () -> Boolean,
    ): CloudJobState

    fun cancel(id: String): CloudJobState

    fun abortTransfer()
}

internal open class CloudFailure(val code: String) : IllegalStateException("Cloud operation failed")

internal class CloudBudgetFailure(val requiredMicroUsd: Long, val availableMicroUsd: Long) :
    CloudFailure("BUDGET")
