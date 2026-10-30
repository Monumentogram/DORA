package com.monumentogram.dora.transcript

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AuthenticatedAudioSource
import com.monumentogram.dora.audio.OriginalAudioPort
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.ProductAudioReaderPort
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.audio.VaultKeyProtection
import com.monumentogram.dora.audio.transcript.CloudConsent
import com.monumentogram.dora.audio.transcript.CloudSegment
import com.monumentogram.dora.audio.transcript.CloudTranscriptResult
import com.monumentogram.dora.audio.transcript.PagedTranscriptPart
import com.monumentogram.dora.audio.transcript.PagedTranscriptPort
import com.monumentogram.dora.audio.transcript.PagedTranscriptProgress
import com.monumentogram.dora.audio.transcript.PagedTranscriptSpec
import com.monumentogram.dora.audio.transcript.TranscriptDocument
import com.monumentogram.dora.audio.transcript.TranscriptJob
import com.monumentogram.dora.audio.transcript.TranscriptPartProvenance
import com.monumentogram.dora.audio.transcript.TranscriptPort
import com.monumentogram.dora.audio.transcript.UnavailablePagedTranscriptPort
import com.monumentogram.dora.audio.transcript.UnavailableTranscriptPort
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCompositeRunTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"

    private inner class Fixture(val frames: Long = 1_382_400_000L) {
        val identity = AudioIdentity(RecordingId(id(1)), AudioAssetId(id(2)), id(3))
        val source = OriginalAudioReference(1, identity, "a".repeat(64), frames)
        val grant = CloudConsent(grantedAt = 1)
        val spec =
            PagedTranscriptSpec(
                source,
                id(4),
                "CLOUD",
                "en",
                "standard-batch:eu-central-1:en-US",
                "cloud-balanced-1800-v1",
                28_800_000L,
                1,
                grant,
            )
        var doc =
            TranscriptDocument(
                source,
                jobs = listOf(TranscriptJob(id(4), "RUNNING", "en", 1, 1, grant)),
            )
        val done = mutableMapOf<Int, TranscriptPartProvenance>()
        var finished = false
        var failAt = -1
        var enabled = true
        var affordable = true
        var creates = 0
        var audioCalls = 0
        val requested = mutableListOf<Int>()
        val cancellations = MemoryCancellations()
        val paged =
            object : PagedTranscriptPort by UnavailablePagedTranscriptPort {
                private fun progress() =
                    PagedTranscriptProgress(
                        spec,
                        "RUNNING",
                        1,
                        done.size,
                        spec.partCount,
                        (0 until spec.partCount).firstOrNull { it !in done },
                        done.size.toLong(),
                        finished,
                    )

                override fun progress(identity: AudioIdentity, jobId: String) =
                    AudioResult.Value(progress())

                override fun resume(identity: AudioIdentity, jobId: String) =
                    AudioResult.Value(progress())

                override fun part(identity: AudioIdentity, jobId: String, index: Int) =
                    AudioResult.Value(
                        done[index]?.let {
                            val range = spec.range(index)
                            PagedTranscriptPart(
                                index,
                                range.firstFrame,
                                range.frameCount,
                                1,
                                1,
                                "b".repeat(64),
                                it,
                            )
                        }
                    )

                override fun commitPart(
                    source: OriginalAudioReference,
                    jobId: String,
                    attempt: Long,
                    index: Int,
                    text: String,
                    provenance: TranscriptPartProvenance?,
                ): AudioResult<PagedTranscriptProgress> {
                    assertFalse(done.containsKey(index))
                    done[index] = requireNotNull(provenance)
                    return AudioResult.Value(progress())
                }

                override fun stop(
                    identity: AudioIdentity,
                    jobId: String,
                    attempt: Long,
                    status: String,
                ) = AudioResult.Value(progress())

                override fun finish(
                    source: OriginalAudioReference,
                    jobId: String,
                    attempt: Long,
                    completedAt: Long,
                ): AudioResult<TranscriptDocument> {
                    assertEquals(spec.partCount, done.size)
                    finished = true
                    return AudioResult.Value(doc)
                }
            }
        val originals =
            object : OriginalAudioPort {
                override fun openAuthenticated(reference: OriginalAudioReference) =
                    AudioResult.Value(
                        object : AuthenticatedAudioSource {
                            override val reference = source

                            override fun checkAvailable() =
                                AudioResult.Value(OriginalAudioStatus.Available(source))

                            override fun extractRange(
                                firstFrame: Long,
                                frameCount: Long,
                                consume: (Long, ByteArray) -> Unit,
                            ): AudioResult<OriginalAudioStatus> =
                                error("No duplicate audio extraction")

                            override fun close() = Unit
                        }
                    )

                override fun acquire(identity: AudioIdentity) = inspect(source)

                override fun inspect(reference: OriginalAudioReference) =
                    AudioResult.Value(OriginalAudioStatus.Available(source))

                override fun withAvailable(
                    reference: OriginalAudioReference,
                    action: () -> Unit,
                ): AudioResult<OriginalAudioStatus> {
                    action()
                    return inspect(reference)
                }

                override fun extract(
                    reference: OriginalAudioReference,
                    consume: (Long, ByteArray) -> Unit,
                ): AudioResult<OriginalAudioStatus> = error("No whole-source plaintext extraction")
            }
        val transcripts =
            object : TranscriptPort by UnavailableTranscriptPort {
                override val paged: PagedTranscriptPort = this@Fixture.paged

                override fun load(identity: AudioIdentity) = AudioResult.Value(doc)

                override fun revokeCloud(
                    identity: AudioIdentity,
                    jobId: String,
                ): AudioResult<TranscriptDocument> {
                    doc =
                        doc.copy(
                            jobs =
                                doc.jobs.map {
                                    it.copy(
                                        status = "CANCELLED",
                                        consent = it.consent!!.copy(revoked = true),
                                    )
                                }
                        )
                    return AudioResult.Value(doc)
                }
            }
        val session =
            object : ProductAudioSession {
                override val originals = this@Fixture.originals
                override val transcripts = this@Fixture.transcripts
                override val protection = VaultKeyProtection.SOFTWARE
                override val writer: ProductAudioWriterPort
                    get() = error("unused")

                override val reader: ProductAudioReaderPort
                    get() = error("unused")

                override fun sourceState(identity: AudioIdentity) =
                    AudioResult.Failed(AudioFailure.UNAVAILABLE)

                override fun retryRemainingDeletion(identity: AudioIdentity) =
                    AudioResult.Failed(AudioFailure.UNAVAILABLE)
            }
        val api =
            object : CloudApi {
                override val endpoint = "https://dora.invalid"

                override fun quoteRun(spec: PagedTranscriptSpec) =
                    CloudRunQuote(
                        enabled,
                        affordable,
                        43_680_000,
                        250_000,
                        spec.partCount,
                        spec.windowPolicy,
                        1_382_400_000,
                        28_800_000,
                    )

                override fun createRun(spec: PagedTranscriptSpec): CloudRunState {
                    creates++
                    return CloudRunState(
                        spec.jobId,
                        "PARTIAL",
                        source.digest,
                        source.version,
                        source.frames,
                        spec.windowPolicy,
                        List(spec.partCount) { i ->
                            val r = spec.range(i)
                            CloudRunPart(i, id(100 + i), r.firstFrame, r.frameCount, "COMPLETED")
                        },
                    )
                }

                override fun runPart(id: String, index: Int): CloudJobState {
                    requested.add(index)
                    if (index == failAt) throw CloudFailure("NETWORK")
                    val range = spec.range(index)
                    return CloudJobState(
                        this@CloudCompositeRunTest.id(100 + index),
                        "COMPLETED",
                        range.frameCount * 2 + 44,
                        false,
                        false,
                        CloudTranscriptResult(
                            "part $index",
                            "amazon-transcribe",
                            spec.model,
                            "provider-$index",
                            source.digest,
                            source.version,
                            5,
                            listOf(CloudSegment("word", 10, 20, 0.9)),
                        ),
                        spec.jobId,
                        index,
                        range.firstFrame,
                        range.frameCount,
                    )
                }

                override fun register(source: OriginalAudioReference) =
                    error("No legacy registration")

                override fun create(
                    source: OriginalAudioReference,
                    id: String,
                    language: String,
                    consent: CloudConsent,
                ): CloudJobState = error("No legacy creation")

                override fun status(id: String): CloudJobState = error("Already completed fixture")

                override fun retry(id: String, cancelled: () -> Boolean): CloudJobState =
                    error("No paid retry")

                override fun upload(
                    id: String,
                    offset: Long,
                    bytes: ByteArray,
                    final: Boolean,
                    cancelled: () -> Boolean,
                ): CloudJobState {
                    audioCalls++
                    error("No duplicate audio upload")
                }

                override fun cancel(id: String) =
                    CloudJobState(id, "CANCELLED", 0, false, false, null)

                override fun abortTransfer() = Unit
            }

        fun run() =
            CloudCompositeRun(api, cancellations, pause = {}).run(
                session,
                identity,
                spec.jobId,
                RecognitionAttempt(),
            ) {}
    }

    @Test
    fun allFortyEightChildrenPublishOnceWithGlobalTimestampsWithoutLoadingTheirAudio() {
        val f = Fixture()
        f.run()
        assertTrue(f.finished)
        assertEquals(48, f.done.size)
        assertEquals(84_600_010L, f.done.getValue(47).segments.single().startMs)
        assertEquals(0, f.audioCalls)
    }

    @Test
    fun networkFailurePreservesCommittedPartsAndRestartSkipsThem() {
        val f = Fixture()
        f.failAt = 2
        assertThrows(CloudFailure::class.java) { f.run() }
        assertEquals(setOf(0, 1), f.done.keys)
        assertFalse(f.finished)
        f.failAt = -1
        f.requested.clear()
        f.run()
        assertEquals((2 until 48).toList(), f.requested)
        assertEquals(48, f.done.size)
    }

    @Test
    fun disabledCapabilityOrInsufficientAggregateBudgetCreatesNoRemoteRunAndUploadsNothing() {
        val old = Fixture().apply { enabled = false }
        assertThrows(CloudFailure::class.java) { old.run() }
        assertEquals(0, old.creates)
        val poor = Fixture().apply { affordable = false }
        val failure = assertThrows(CloudBudgetFailure::class.java) { poor.run() }
        assertEquals(43_680_000L, failure.requiredMicroUsd)
        assertEquals(0, poor.creates)
        assertEquals(0, poor.audioCalls)
    }
}
