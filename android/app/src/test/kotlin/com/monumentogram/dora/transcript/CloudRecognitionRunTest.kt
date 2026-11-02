package com.monumentogram.dora.transcript

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioPort
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.ProductAudioReaderPort
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.audio.VaultKeyProtection
import com.monumentogram.dora.audio.transcript.CloudConsent
import com.monumentogram.dora.audio.transcript.CloudTranscriptResult
import com.monumentogram.dora.audio.transcript.TranscriptDocument
import com.monumentogram.dora.audio.transcript.TranscriptJob
import com.monumentogram.dora.audio.transcript.TranscriptPort
import com.monumentogram.dora.audio.transcript.UnavailableTranscriptPort
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecognitionRunTest {
    private fun id(n: Int) = "00000000-0000-0000-0000-${n.toString().padStart(12, '0')}"

    private val identity = AudioIdentity(RecordingId(id(1)), AudioAssetId(id(2)), id(3))
    private val source = OriginalAudioReference(1, identity, "a".repeat(64), 8000)

    private fun result() =
        CloudTranscriptResult(
            "synthetic cloud",
            "amazon-transcribe",
            "standard-batch:eu-central-1:ru-RU",
            "dora-synthetic",
            source.digest,
            1,
            7,
        )

    private inner class Fixture(grant: CloudConsent? = CloudConsent(grantedAt = 1)) {
        var doc =
            TranscriptDocument(
                source,
                jobs = listOf(TranscriptJob(id(4), "QUEUED", "ru", 1, 1, grant)),
            )
        var published: CloudTranscriptResult? = null
        val attempt = RecognitionAttempt()
        val uploaded = mutableListOf<Byte>()
        var calls = 0
        var cancelOnUpload = false
        var alreadyUploaded = false
        var revokeLocked = false
        var cancelOnLoad = -1
        var loads = 0
        var failedRemote = false
        var retries = 0
        val cancellations = MemoryCancellations()
        val port =
            object : TranscriptPort by UnavailableTranscriptPort {
                override fun load(identity: AudioIdentity): AudioResult<TranscriptDocument?> {
                    if (++loads == cancelOnLoad) attempt.cancel()
                    return AudioResult.Value(doc)
                }

                override fun resumeCloud(identity: AudioIdentity, jobId: String) =
                    AudioResult.Value(doc)

                override fun finishCloud(
                    source: OriginalAudioReference,
                    jobId: String,
                    result: CloudTranscriptResult,
                ): AudioResult<TranscriptDocument> {
                    published = result
                    return AudioResult.Value(doc)
                }

                override fun stop(
                    identity: AudioIdentity,
                    jobId: String,
                    status: String,
                ): AudioResult<TranscriptDocument> {
                    doc = doc.copy(jobs = doc.jobs.map { it.copy(status = status) })
                    return AudioResult.Value(doc)
                }

                override fun revokeCloud(
                    identity: AudioIdentity,
                    jobId: String,
                ): AudioResult<TranscriptDocument> {
                    if (revokeLocked) return AudioResult.Failed(AudioFailure.LOCKED)
                    doc =
                        doc.copy(
                            jobs =
                                doc.jobs.map {
                                    it.copy(
                                        status = "CANCELLED",
                                        consent = it.consent?.copy(revoked = true),
                                    )
                                }
                        )
                    return AudioResult.Value(doc)
                }
            }
        val session =
            object : ProductAudioSession {
                override val transcripts = port
                override val originals =
                    object : OriginalAudioPort {
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
                        ): AudioResult<OriginalAudioStatus> {
                            consume(0, ByteArray(16000))
                            return inspect(reference)
                        }
                    }
                override val protection = VaultKeyProtection.SOFTWARE
                override val writer: ProductAudioWriterPort
                    get() = error("not used")

                override val reader: ProductAudioReaderPort
                    get() = error("not used")

                override fun sourceState(identity: AudioIdentity) =
                    AudioResult.Failed(AudioFailure.UNAVAILABLE)

                override fun retryRemainingDeletion(identity: AudioIdentity) =
                    AudioResult.Failed(AudioFailure.UNAVAILABLE)
            }
        val api =
            object : CloudApi {
                override val endpoint = "https://dora.invalid"

                private fun pending() =
                    CloudJobState(
                        id(4),
                        "UPLOADING",
                        if (alreadyUploaded) 16044 else uploaded.size.toLong(),
                        false,
                        false,
                        null,
                    )

                override fun register(source: OriginalAudioReference) {
                    calls++
                }

                override fun create(
                    source: OriginalAudioReference,
                    id: String,
                    language: String,
                    consent: CloudConsent,
                ): CloudJobState {
                    calls++
                    return if (failedRemote) CloudJobState(id, "FAILED", 0, true, false, null)
                    else pending()
                }

                override fun status(id: String) =
                    CloudJobState(id, "COMPLETED", uploaded.size.toLong(), false, false, result())

                override fun retry(id: String, cancelled: () -> Boolean): CloudJobState {
                    if (cancelled()) throw java.util.concurrent.CancellationException()
                    retries++
                    return pending()
                }

                override fun upload(
                    id: String,
                    offset: Long,
                    bytes: ByteArray,
                    final: Boolean,
                    cancelled: () -> Boolean,
                ): CloudJobState {
                    assertEquals(uploaded.size.toLong(), offset)
                    uploaded.addAll(bytes.toList())
                    if (cancelOnUpload) attempt.cancel()
                    return CloudJobState(
                        id,
                        "COMPLETED",
                        uploaded.size.toLong(),
                        false,
                        false,
                        result(),
                    )
                }

                override fun cancel(id: String) =
                    CloudJobState(id, "CANCELLED", uploaded.size.toLong(), false, true, null)

                override fun abortTransfer() = Unit
            }

        fun run() =
            CloudRecognitionRun(api, cancellations, pause = {}).run(
                session,
                identity,
                id(4),
                attempt,
            ) {}
    }

    @Test
    fun absentOrRevokedConsentSendsZeroBytesAndNoRequests() {
        listOf(null, CloudConsent(grantedAt = 1, revoked = true)).forEach { grant ->
            val f = Fixture(grant)
            assertThrows(Exception::class.java) { f.run() }
            assertEquals(0, f.calls)
            assertTrue(f.uploaded.isEmpty())
        }
    }

    @Test
    fun cloudOnlyRunsWithoutLocalModelAndPublishesNormalizedResult() {
        val f = Fixture()
        f.run()
        assertEquals(16044, f.uploaded.size)
        assertEquals(result(), f.published)
    }

    @Test
    fun cancellationRejectsLateCompleteAndDurablyRevokesGrant() {
        val f = Fixture()
        f.cancelOnUpload = true
        assertThrows(Exception::class.java) { f.run() }
        assertNull(f.published)
        assertTrue(f.doc.jobs.single().consent!!.revoked)
    }

    @Test
    fun acceptedFinalChunkCanBeReplayedAfterDispatchInterruption() {
        val f = Fixture()
        f.alreadyUploaded = true
        f.run()
        assertEquals(result(), f.published)
        assertEquals(16044, f.uploaded.size)
    }

    @Test
    fun cancellationOutboxBlocksRestartWhenVaultRevocationFails() {
        val f = Fixture()
        f.cancelOnUpload = true
        f.revokeLocked = true
        assertThrows(Exception::class.java) { f.run() }
        assertFalse(f.doc.jobs.single().consent!!.revoked)
        assertTrue(f.cancellations.blocked(id(4), f.api.endpoint))
        val calls = f.calls
        assertThrows(Exception::class.java) {
            CloudRecognitionRun(f.api, f.cancellations, pause = {}).run(
                f.session,
                identity,
                id(4),
                RecognitionAttempt(),
            ) {}
        }
        assertEquals(calls, f.calls)
        assertNull(f.published)
    }

    @Test
    fun cancellationDuringConsentReadPreventsPaidRetry() {
        val f = Fixture()
        f.failedRemote = true
        f.cancelOnLoad = 5
        assertThrows(Exception::class.java) { f.run() }
        assertEquals(0, f.retries)
        assertTrue(f.uploaded.isEmpty())
        assertTrue(f.cancellations.blocked(id(4), f.api.endpoint))
    }
}

internal class MemoryCancellations : CloudCancellations {
    private val entries = mutableSetOf<Pair<String, String>>()

    override fun blocked(id: String, destination: String) = id to destination in entries

    override fun record(id: String, destination: String) {
        entries.add(id to destination)
    }

    override fun actions(destination: String) =
        entries.filter { it.second == destination }.map { it.first }
}
