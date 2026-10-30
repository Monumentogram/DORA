package com.monumentogram.dora.audio.transcript

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioReference
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** A job identity includes its exact immutable source and versioned partitioning policy. */
data class PagedTranscriptSpec(
    val source: OriginalAudioReference,
    val jobId: String,
    val engine: String,
    val language: String,
    val model: String,
    val windowPolicy: String,
    val windowFrames: Long,
    val createdAt: Long,
    val consent: CloudConsent? = null,
) {
    init {
        TranscriptReducer.validId(jobId)
        require(source.frames in 1..PagedTranscriptLimits.MAX_SOURCE_FRAMES && source.version == 1)
        require(source.digest.matches(Regex("[a-f0-9]{64}")))
        require(
            language in setOf("ru", "en") &&
                model.isNotBlank() &&
                model.length <= PagedTranscriptLimits.MAX_MODEL_NAME_LENGTH
        )
        require(createdAt >= 0)
        require(
            (engine == "LOCAL" &&
                windowPolicy == "local-fixed-300-v1" &&
                windowFrames in 1..PagedTranscriptLimits.LOCAL_WINDOW_FRAMES &&
                consent == null) ||
                (engine == "CLOUD" &&
                    windowPolicy == "cloud-balanced-1800-v1" &&
                    windowFrames in
                        PagedTranscriptLimits.MIN_CLOUD_PART_FRAMES..PagedTranscriptLimits
                                .CLOUD_WINDOW_FRAMES &&
                    consent != null)
        )
        consent?.validate()
        require(consent?.revoked != true)
        require(
            partCount in
                1..if (engine == "CLOUD") PagedTranscriptLimits.MAX_CLOUD_PARTS
                    else PagedTranscriptLimits.MAX_LOCAL_PARTS
        )
        if (engine == "CLOUD")
            require(range(partCount - 1).frameCount >= PagedTranscriptLimits.MIN_CLOUD_PART_FRAMES)
    }

    val partCount: Int
        get() = ((source.frames - 1) / windowFrames + 1).toInt()

    /**
     * Excludes action UUID/time/consent so a fresh authorized action can reuse immutable results.
     */
    val configDigest: String
        get() =
            PagedTranscriptText.digest(
                listOf(
                        "dora-paged-config-v1",
                        engine,
                        language,
                        model,
                        windowPolicy,
                        windowFrames.toString(),
                    )
                    .joinToString("\u0000")
                    .toByteArray(Charsets.UTF_8)
            )

    fun range(index: Int): TranscriptFrameRange {
        require(index in 0 until partCount)
        val start =
            if (engine == "CLOUD") Math.multiplyExact(source.frames, index.toLong()) / partCount
            else Math.multiplyExact(index.toLong(), windowFrames)
        val end =
            if (engine == "CLOUD") Math.multiplyExact(source.frames, index + 1L) / partCount
            else minOf(source.frames, Math.addExact(start, windowFrames))
        return TranscriptFrameRange(start, end - start)
    }

    override fun toString() = "PagedTranscriptSpec(redacted)"
}

data class TranscriptFrameRange(val firstFrame: Long, val frameCount: Long)

data class TranscriptPageCursor(val partIndex: Int, val pageIndex: Int)

object PagedTranscriptLimits {
    const val MAX_SOURCE_FRAMES = 1_382_400_000L
    const val MAX_MODEL_NAME_LENGTH = 256
    const val LOCAL_WINDOW_FRAMES = 4_800_000L
    const val CLOUD_WINDOW_FRAMES = 28_800_000L
    const val MIN_CLOUD_PART_FRAMES = 8_000L
    const val MAX_LOCAL_PARTS = 288
    const val MAX_CLOUD_PARTS = 48
    const val MAX_PAGES_PER_PART = 17
    const val MAX_CLOUD_SEGMENTS = 20_000
    const val MAX_PROVENANCE_BYTES = 2_097_152
}

data class PagedTranscriptReference(
    val engine: String,
    val configDigest: String,
    val windowPolicy: String,
    val partCount: Int,
    val pageCount: Long,
    val textBytes: Long,
    val rootDigest: String,
    val publicationRevision: Long,
) {
    fun validate() {
        require(engine in setOf("LOCAL", "CLOUD"))
        require(
            configDigest.matches(Regex("[a-f0-9]{64}")) && rootDigest.matches(Regex("[a-f0-9]{64}"))
        )
        require(
            windowPolicy ==
                if (engine == "LOCAL") "local-fixed-300-v1" else "cloud-balanced-1800-v1"
        )
        require(
            partCount in
                1..if (engine == "CLOUD") PagedTranscriptLimits.MAX_CLOUD_PARTS
                    else PagedTranscriptLimits.MAX_LOCAL_PARTS
        )
        require(
            pageCount in
                partCount.toLong()..partCount.toLong() * PagedTranscriptLimits.MAX_PAGES_PER_PART
        )
        require(
            textBytes in 0..partCount.toLong() * PagedTranscriptText.MAX_PART_BYTES &&
                publicationRevision > 0
        )
    }
}

data class TranscriptPartProvenance(
    val providerId: String,
    val providerJobId: String,
    val completedAt: Long,
    val segments: List<CloudSegment> = emptyList(),
) {
    override fun toString() = "TranscriptPartProvenance(redacted)"
}

data class PagedTranscriptPart(
    val index: Int,
    val firstFrame: Long,
    val frameCount: Long,
    val pageCount: Int,
    val textBytes: Long,
    val digest: String,
    val provenance: TranscriptPartProvenance? = null,
) {
    override fun toString() = "PagedTranscriptPart(redacted)"
}

data class PagedTranscriptProgress(
    val spec: PagedTranscriptSpec,
    val status: String,
    val attempt: Long,
    val completedParts: Int,
    val totalParts: Int,
    val nextIncompleteIndex: Int?,
    val textBytes: Long,
    val published: Boolean,
) {
    override fun toString() = "PagedTranscriptProgress(redacted)"
}

data class TranscriptTextPage(
    val cursor: TranscriptPageCursor,
    val text: String,
    val digest: String,
    val completeVersion: Boolean,
) {
    override fun toString() = "TranscriptTextPage(redacted)"
}

/** All methods require the same authenticated worker/source lease as the legacy transcript port. */
@Suppress("TooManyFunctions", "LongParameterList")
interface PagedTranscriptPort {
    fun begin(spec: PagedTranscriptSpec): AudioResult<PagedTranscriptProgress>

    fun resume(identity: AudioIdentity, jobId: String): AudioResult<PagedTranscriptProgress>

    fun progress(identity: AudioIdentity, jobId: String): AudioResult<PagedTranscriptProgress?>

    fun part(identity: AudioIdentity, jobId: String, index: Int): AudioResult<PagedTranscriptPart?>

    fun commitPart(
        source: OriginalAudioReference,
        jobId: String,
        attempt: Long,
        index: Int,
        text: String,
        provenance: TranscriptPartProvenance? = null,
    ): AudioResult<PagedTranscriptProgress>

    fun reusePart(
        source: OriginalAudioReference,
        jobId: String,
        attempt: Long,
        fromJobId: String,
        index: Int,
    ): AudioResult<PagedTranscriptProgress>

    fun stop(
        identity: AudioIdentity,
        jobId: String,
        attempt: Long,
        status: String,
    ): AudioResult<PagedTranscriptProgress>

    fun finish(
        source: OriginalAudioReference,
        jobId: String,
        attempt: Long,
        completedAt: Long,
    ): AudioResult<TranscriptDocument>

    fun page(
        identity: AudioIdentity,
        versionId: String,
        after: TranscriptPageCursor?,
        revision: Long? = null,
        allowPartial: Boolean = false,
    ): AudioResult<TranscriptTextPage?>

    fun editPage(
        identity: AudioIdentity,
        versionId: String,
        cursor: TranscriptPageCursor,
        expectedRevision: Long,
        beforeDigest: String,
        text: String,
        createdAt: Long,
    ): AudioResult<TranscriptDocument>
}

@Suppress("TooManyFunctions", "LongParameterList")
object UnavailablePagedTranscriptPort : PagedTranscriptPort {
    private fun unavailable() = AudioResult.Failed(AudioFailure.UNAVAILABLE)

    override fun begin(spec: PagedTranscriptSpec) = unavailable()

    override fun resume(identity: AudioIdentity, jobId: String) = unavailable()

    override fun progress(identity: AudioIdentity, jobId: String) = unavailable()

    override fun part(identity: AudioIdentity, jobId: String, index: Int) = unavailable()

    override fun commitPart(
        source: OriginalAudioReference,
        jobId: String,
        attempt: Long,
        index: Int,
        text: String,
        provenance: TranscriptPartProvenance?,
    ) = unavailable()

    override fun reusePart(
        source: OriginalAudioReference,
        jobId: String,
        attempt: Long,
        fromJobId: String,
        index: Int,
    ) = unavailable()

    override fun stop(identity: AudioIdentity, jobId: String, attempt: Long, status: String) =
        unavailable()

    override fun finish(
        source: OriginalAudioReference,
        jobId: String,
        attempt: Long,
        completedAt: Long,
    ) = unavailable()

    override fun page(
        identity: AudioIdentity,
        versionId: String,
        after: TranscriptPageCursor?,
        revision: Long?,
        allowPartial: Boolean,
    ) = unavailable()

    override fun editPage(
        identity: AudioIdentity,
        versionId: String,
        cursor: TranscriptPageCursor,
        expectedRevision: Long,
        beforeDigest: String,
        text: String,
        createdAt: Long,
    ) = unavailable()
}

object PagedTranscriptText {
    private const val UTF8_CONTINUATION_MASK = 0xc0
    private const val UTF8_CONTINUATION_TAG = 0x80
    const val PAGE_BYTES = 65_536
    const val MAX_PART_BYTES = 1_048_576

    fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun decode(bytes: ByteArray): String =
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()

    fun pages(text: String): List<String> {
        val encoded =
            try {
                Charsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(text))
            } catch (_: java.nio.charset.CharacterCodingException) {
                throw IllegalArgumentException("Invalid transcript encoding")
            }
        require(encoded.remaining() <= MAX_PART_BYTES)
        val bytes = ByteArray(encoded.remaining()).also { encoded.get(it) }
        try {
            if (bytes.isEmpty()) return listOf("")
            val pages = mutableListOf<String>()
            var first = 0
            while (first < bytes.size) {
                var end = minOf(first + PAGE_BYTES, bytes.size)
                while (
                    end < bytes.size &&
                        bytes[end].toInt() and UTF8_CONTINUATION_MASK == UTF8_CONTINUATION_TAG
                ) end--
                pages += decode(bytes.copyOfRange(first, end))
                first = end
            }
            return pages
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("Invalid transcript encoding")
        } finally {
            bytes.fill(0)
        }
    }
}
