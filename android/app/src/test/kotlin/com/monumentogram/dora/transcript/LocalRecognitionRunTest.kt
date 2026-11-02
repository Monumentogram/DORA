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
import com.monumentogram.dora.audio.transcript.MachineTranscript
import com.monumentogram.dora.audio.transcript.PagedTranscriptPort
import com.monumentogram.dora.audio.transcript.PagedTranscriptProgress
import com.monumentogram.dora.audio.transcript.PagedTranscriptReference
import com.monumentogram.dora.audio.transcript.PagedTranscriptSpec
import com.monumentogram.dora.audio.transcript.PagedTranscriptText
import com.monumentogram.dora.audio.transcript.TranscriptDocument
import com.monumentogram.dora.audio.transcript.TranscriptJob
import com.monumentogram.dora.audio.transcript.TranscriptPartProvenance
import com.monumentogram.dora.audio.transcript.TranscriptPort
import com.monumentogram.dora.audio.transcript.UnavailablePagedTranscriptPort
import com.monumentogram.dora.audio.transcript.UnavailableTranscriptPort
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRecognitionRunTest {
    private val id =
        AudioIdentity(
            RecordingId("00000000-0000-4000-8000-000000000001"),
            AudioAssetId("00000000-0000-4000-8000-000000000002"),
            "00000000-0000-4000-8000-000000000003",
        )
    private var source = OriginalAudioReference(1, id, "a".repeat(64), 2)
    private val job = "00000000-0000-4000-8000-000000000004"

    @Test(timeout = 180_000)
    fun twentyFourHourVirtualSourceResumesWithoutDecodingDurableWindowsAndNeverJoinsAllText() {
        source = source.copy(frames = 1_382_400_000L)
        val checkpoints = Checkpoints()
        val store = Store(checkpoints)
        var previous: FloatArray? = null
        var firstCalls = 0
        val first =
            LocalRecognitionRun({ pcm, _, _ ->
                previous?.let { assertTrue(it.all { sample -> sample == 0f }) }
                previous = pcm
                firstCalls++
                if (firstCalls == 6) error("controlled decoder failure")
                "window $firstCalls " + "x".repeat(2048)
            })
        assertThrows(RecognitionFailure::class.java) {
            first.run(session(store), id, job, "en", "test", RecognitionAttempt()) {}
        }
        assertEquals(5, checkpoints.digests.size)
        assertEquals(0, checkpoints.publications)
        val preserved = checkpoints.digests.toMap()
        var resumedCalls = 0
        val resumed =
            LocalRecognitionRun({ pcm, _, _ ->
                previous?.let { assertTrue(it.all { sample -> sample == 0f }) }
                previous = pcm
                assertEquals(4_800_000, pcm.size)
                val expectedWindow = 6 + resumedCalls++
                assertEquals(expectedWindow / 32768f, pcm.first(), 0f)
                assertEquals(expectedWindow / 32768f, pcm.last(), 0f)
                "window $expectedWindow " + "x".repeat(2048)
            })
        val completed = resumed.run(session(store), id, job, "en", "test", RecognitionAttempt()) {}
        assertTrue(completed is AudioResult.Value)
        assertEquals(283, resumedCalls)
        assertEquals(288, checkpoints.digests.size)
        assertEquals(1, checkpoints.publications)
        assertTrue(checkpoints.bytes > 262_144)
        assertEquals(preserved, checkpoints.digests.filterKeys { it < 5 })
        assertTrue(checkNotNull(previous).all { it == 0f })
    }

    @Test
    fun pagedCancellationOnFirstThirdAndSixthWindowRetainsOnlyCompletedParts() {
        for (at in listOf(1, 3, 6)) {
            source = source.copy(frames = 28_800_000)
            val checkpoints = Checkpoints()
            val store = Store(checkpoints)
            val attempt = RecognitionAttempt()
            var calls = 0
            var previous: FloatArray? = null
            val run =
                LocalRecognitionRun({ pcm, _, _ ->
                    previous?.let { assertTrue(it.all { sample -> sample == 0f }) }
                    previous = pcm
                    if (++calls == at) attempt.cancel()
                    "late controlled output"
                })
            val failure =
                assertThrows(RecognitionFailure::class.java) {
                    run.run(session(store), id, job, "ru", "test", attempt) {}
                }
            assertEquals("CANCELLED", failure.category)
            assertEquals(at - 1, checkpoints.digests.size)
            assertEquals("CANCELLED", checkpoints.status)
            assertEquals(0, checkpoints.publications)
            assertTrue(checkNotNull(previous).all { it == 0f })
        }
    }

    @Test
    fun longAudioUsesBoundedWindowsInOrderAndPublishesOnce() {
        source = source.copy(frames = 28_800_000)
        val store = Store()
        val lengths = mutableListOf<Int>()
        val run =
            LocalRecognitionRun({ pcm, _, _ ->
                lengths.add(pcm.size)
                assertTrue(pcm.size <= 4_800_000)
                assertEquals(lengths.size / 32768f, pcm.first(), 0f)
                assertEquals(lengths.size / 32768f, pcm.last(), 0f)
                "part${lengths.size}"
            })
        run.run(session(store), id, job, "en", "test", RecognitionAttempt()) {}
        assertEquals(List(6) { 4_800_000 }, lengths)
        assertEquals("part1\npart2\npart3\npart4\npart5\npart6", store.published)
    }

    @Test
    fun cancellationInSecondWindowDoesNotPublishPartialTextAndWipesBothWindows() {
        source = source.copy(frames = 4_800_002)
        val store = Store()
        val attempt = RecognitionAttempt()
        val owned = mutableListOf<FloatArray>()
        val run =
            LocalRecognitionRun({ pcm, _, _ ->
                owned.add(pcm)
                if (owned.size == 2) attempt.cancel()
                "partial"
            })
        assertThrows(RecognitionFailure::class.java) {
            run.run(session(store), id, job, "en", "test", attempt) {}
        }
        assertNull(store.published)
        assertEquals("CANCELLED", store.terminal)
        assertEquals(2, owned.size)
        assertTrue(owned.all { window -> window.all { it == 0f } })
    }

    @Test
    fun realPcmConversionAndSuccessfulPublicationReleaseOwnedMemory() {
        val store = Store()
        var borrowed: FloatArray? = null
        val states = mutableListOf<String>()
        val run =
            LocalRecognitionRun(
                { pcm, language, _ ->
                    borrowed = pcm
                    assertEquals("en", language)
                    assertEquals(-1f, pcm[0], 0f)
                    "synthetic output"
                },
                { 1L },
            )
        assertTrue(
            run.run(
                session(store),
                id,
                job,
                "en",
                "test-boundary",
                RecognitionAttempt(),
                states::add,
            ) is AudioResult.Value
        )
        assertEquals("synthetic output", store.published)
        assertEquals(listOf("Ожидает обработки", "Распознавание", "Сохраняем расшифровку"), states)
        assertArrayEquals(floatArrayOf(0f, 0f), borrowed, 0f)
    }

    @Test
    fun cancellationDuringDecoderRejectsLateOutputAndWipesMemory() {
        val store = Store()
        val attempt = RecognitionAttempt()
        var borrowed: FloatArray? = null
        val run =
            LocalRecognitionRun({ pcm, _, _ ->
                borrowed = pcm
                attempt.cancel()
                "late"
            })
        val failure =
            assertThrows(RecognitionFailure::class.java) {
                run.run(session(store), id, job, "ru", "test", attempt) {}
            }
        assertEquals("CANCELLED", failure.category)
        assertNull(store.published)
        assertEquals("CANCELLED", store.terminal)
        assertArrayEquals(floatArrayOf(0f, 0f), borrowed, 0f)
    }

    @Test
    fun corruptTailDoesNotReachDecoder() {
        val store = Store()
        val run =
            LocalRecognitionRun({ _, _, _ ->
                throw AssertionError("decoder must not see incomplete audio")
            })
        assertThrows(RecognitionFailure::class.java) {
            run.run(session(store, corrupt = true), id, job, "ru", "test", RecognitionAttempt()) {}
        }
        assertNull(store.published)
        assertEquals("FAILED", store.terminal)
    }

    @Test
    fun nativeAbortExceptionIsPersistedAsCancellation() {
        val store = Store()
        val attempt = RecognitionAttempt()
        val run =
            LocalRecognitionRun({ _, _, _ ->
                attempt.cancel()
                error("native aborted")
            })
        val failure =
            assertThrows(RecognitionFailure::class.java) {
                run.run(session(store), id, job, "en", "test", attempt) {}
            }
        assertEquals("CANCELLED", failure.category)
        assertEquals("CANCELLED", store.terminal)
        assertNull(store.published)
    }

    @Test
    fun engineFailureWipesMemoryAndLeavesNoPublication() {
        val store = Store()
        var borrowed: FloatArray? = null
        val run =
            LocalRecognitionRun({ pcm, _, _ ->
                borrowed = pcm
                error("controlled engine failure")
            })
        assertThrows(RecognitionFailure::class.java) {
            run.run(session(store), id, job, "ru", "test", RecognitionAttempt()) {}
        }
        assertNull(store.published)
        assertArrayEquals(floatArrayOf(0f, 0f), borrowed, 0f)
    }

    private fun session(store: Store, corrupt: Boolean = false) =
        object : ProductAudioSession {
            override val transcripts: TranscriptPort = store
            override val originals =
                object : OriginalAudioPort {
                    override fun openAuthenticated(
                        identity: AudioIdentity
                    ): AudioResult<AuthenticatedAudioSource> =
                        AudioResult.Value(
                            object : AuthenticatedAudioSource {
                                override val reference = source
                                private var closed = false

                                override fun checkAvailable() =
                                    AudioResult.Value(OriginalAudioStatus.Available(reference))

                                override fun extractRange(
                                    firstFrame: Long,
                                    frameCount: Long,
                                    consume: (Long, ByteArray) -> Unit,
                                ): AudioResult<OriginalAudioStatus> {
                                    check(!closed)
                                    var first = firstFrame
                                    val end = firstFrame + frameCount
                                    while (first < end) {
                                        val count = minOf(80_000L, end - first).toInt()
                                        val sample = (first / 4_800_000 + 1).toInt()
                                        val bytes =
                                            ByteArray(count * 2) {
                                                if (it % 2 == 0) sample.toByte()
                                                else (sample ushr 8).toByte()
                                            }
                                        try {
                                            consume(first, bytes)
                                        } finally {
                                            bytes.fill(0)
                                        }
                                        first += count
                                    }
                                    return checkAvailable()
                                }

                                override fun close() {
                                    closed = true
                                }
                            }
                        )

                    override fun acquire(identity: AudioIdentity) =
                        AudioResult.Value(OriginalAudioStatus.Available(source))

                    override fun inspect(reference: OriginalAudioReference) =
                        acquire(reference.identity)

                    override fun withAvailable(
                        reference: OriginalAudioReference,
                        action: () -> Unit,
                    ) = inspect(reference)

                    override fun extract(
                        reference: OriginalAudioReference,
                        consume: (Long, ByteArray) -> Unit,
                    ): AudioResult<OriginalAudioStatus> {
                        if (source.frames == 2L) consume(0, byteArrayOf(0, -128, -1, 127))
                        else {
                            var next = 0L
                            while (next < source.frames) {
                                val count = minOf(80_000L, source.frames - next).toInt()
                                consume(
                                    next,
                                    ByteArray(count * 2) {
                                        if (it % 2 == 0) (next / 4_800_000 + 1).toByte() else 0
                                    },
                                )
                                next += count
                            }
                        }
                        return if (corrupt) AudioResult.Failed(AudioFailure.CORRUPT)
                        else inspect(reference)
                    }
                }
            override val writer: ProductAudioWriterPort
                get() = error("not capture")

            override val reader: ProductAudioReaderPort
                get() = error("must use protected reference")

            override val protection = VaultKeyProtection.SOFTWARE

            override fun sourceState(identity: AudioIdentity) =
                AudioResult.Failed(AudioFailure.UNAVAILABLE)

            override fun retryRemainingDeletion(identity: AudioIdentity) =
                AudioResult.Failed(AudioFailure.UNAVAILABLE)
        }

    private inner class Store(
        override val paged: PagedTranscriptPort = UnavailablePagedTranscriptPort
    ) : TranscriptPort by UnavailableTranscriptPort {
        var published: String? = null
        var terminal: String? = null
        private val document = TranscriptDocument(source)

        override fun begin(
            source: OriginalAudioReference,
            jobId: String,
            language: String,
            createdAt: Long,
        ) = AudioResult.Value(document)

        override fun running(identity: AudioIdentity, jobId: String) = AudioResult.Value(document)

        override fun finish(
            source: OriginalAudioReference,
            jobId: String,
            text: String,
            model: String,
            createdAt: Long,
        ): AudioResult<TranscriptDocument> {
            assertEquals(this@LocalRecognitionRunTest.source, source)
            assertEquals(job, jobId)
            published = text
            return AudioResult.Value(document)
        }

        override fun stop(
            identity: AudioIdentity,
            jobId: String,
            status: String,
        ): AudioResult<TranscriptDocument> {
            terminal = status
            return AudioResult.Value(document)
        }
    }

    private inner class Checkpoints : PagedTranscriptPort by UnavailablePagedTranscriptPort {
        private var spec: PagedTranscriptSpec? = null
        private var attempt = 0L
        var status = "RUNNING"
        val digests = mutableMapOf<Int, String>()
        var bytes = 0L
        var publications = 0

        private fun current(): PagedTranscriptProgress {
            val value = checkNotNull(spec)
            return PagedTranscriptProgress(
                value,
                status,
                attempt,
                digests.size,
                value.partCount,
                (0 until value.partCount).firstOrNull { it !in digests },
                bytes,
                status == "SUCCEEDED",
            )
        }

        override fun progress(
            identity: AudioIdentity,
            jobId: String,
        ): AudioResult<PagedTranscriptProgress?> =
            AudioResult.Value(if (spec == null) null else current())

        override fun begin(spec: PagedTranscriptSpec): AudioResult<PagedTranscriptProgress> {
            this.spec = spec
            attempt = 1
            return AudioResult.Value(current())
        }

        override fun resume(
            identity: AudioIdentity,
            jobId: String,
        ): AudioResult<PagedTranscriptProgress> {
            attempt++
            status = "RUNNING"
            return AudioResult.Value(current())
        }

        override fun commitPart(
            source: OriginalAudioReference,
            jobId: String,
            attempt: Long,
            index: Int,
            text: String,
            provenance: TranscriptPartProvenance?,
        ): AudioResult<PagedTranscriptProgress> {
            check(attempt == this.attempt && status == "RUNNING" && index !in digests)
            val encoded = text.toByteArray(Charsets.UTF_8)
            bytes += encoded.size
            digests[index] = PagedTranscriptText.digest(encoded)
            return AudioResult.Value(current())
        }

        override fun stop(
            identity: AudioIdentity,
            jobId: String,
            attempt: Long,
            status: String,
        ): AudioResult<PagedTranscriptProgress> {
            check(attempt == this.attempt)
            this.status = status
            return AudioResult.Value(current())
        }

        override fun finish(
            source: OriginalAudioReference,
            jobId: String,
            attempt: Long,
            completedAt: Long,
        ): AudioResult<TranscriptDocument> {
            check(digests.size == current().totalParts && attempt == this.attempt)
            publications++
            status = "SUCCEEDED"
            val reference =
                PagedTranscriptReference(
                    "LOCAL",
                    checkNotNull(spec).configDigest,
                    "local-fixed-300-v1",
                    digests.size,
                    digests.size.toLong(),
                    bytes,
                    "b".repeat(64),
                    3,
                )
            return AudioResult.Value(
                TranscriptDocument(
                    source,
                    3,
                    jobId,
                    listOf(MachineTranscript(jobId, "", completedAt, "test", paged = reference)),
                    jobs = listOf(TranscriptJob(jobId, status, checkNotNull(spec).language, 1, 1)),
                )
            )
        }
    }
}
