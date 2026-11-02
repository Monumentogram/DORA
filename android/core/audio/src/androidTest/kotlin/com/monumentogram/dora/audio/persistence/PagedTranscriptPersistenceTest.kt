package com.monumentogram.dora.audio.persistence

import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.transcript.PagedTranscriptProgress
import com.monumentogram.dora.audio.transcript.PagedTranscriptRead
import com.monumentogram.dora.audio.transcript.PagedTranscriptSpec
import com.monumentogram.dora.audio.transcript.TranscriptPageCursor
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Ordered real-vault scenarios intentionally retain setup, interruption, reopen and assertions.
@Suppress("LongMethod", "CyclomaticComplexMethod")
class PagedTranscriptPersistenceTest {
    @Test
    fun encrypted288PartCheckpointsResumeWithoutRedoAndStreamRevisionBoundEdits() {
        // Tiny source partitions isolate SQLCipher checkpoint/history behavior. The separate 24h
        // encrypted-audio test qualifies actual large ranges; this test is not native ASR evidence.
        val f = EncryptedAudioVaultFaultFixture()
        val job = UUID.randomUUID().toString()
        val source =
            f.open().use { vault ->
                value(vault.writer.create(f.audio))
                value(vault.writer.append(f.unit, AudioFormat.PCM, ByteArray(576)))
                value(vault.writer.finalize(f.audio))
                val ref =
                    (value(vault.originals.acquire(f.audio)) as OriginalAudioStatus.Available)
                        .reference
                value(
                    vault.transcripts.paged.begin(
                        PagedTranscriptSpec(
                            ref,
                            job,
                            "LOCAL",
                            "en",
                            "model",
                            "local-fixed-300-v1",
                            1,
                            1,
                        )
                    )
                )
                ref
            }
        var completed = 0
        var oldAttempt = 0L
        for (checkpoint in listOf(1, 3, 6, 288)) {
            f.open(create = false).use { vault ->
                value(vault.transcripts.recover(f.audio))
                val resumed = value(vault.transcripts.paged.resume(f.audio, job))
                assertEquals(completed, resumed.completedParts)
                assertEquals(completed, resumed.nextIncompleteIndex)
                assertTrue(resumed.attempt > oldAttempt)
                for (index in completed until checkpoint) value(
                    vault.transcripts.paged.commitPart(
                        source,
                        job,
                        resumed.attempt,
                        index,
                        publicPart(index),
                        null,
                    )
                )
                val after = value(vault.transcripts.paged.progress(f.audio, job))!!
                assertEquals(checkpoint, after.completedParts)
                assertTrue(value(vault.transcripts.load(f.audio))!!.versions.isEmpty())
                // A duplicate accepted result is idempotent; conflicting/stale completions fail.
                value(
                    vault.transcripts.paged.commitPart(
                        source,
                        job,
                        resumed.attempt,
                        0,
                        publicPart(0),
                        null,
                    )
                )
                assertTrue(
                    vault.transcripts.paged.commitPart(
                        source,
                        job,
                        resumed.attempt,
                        0,
                        "conflicting result",
                        null,
                    ) is AudioResult.Failed
                )
                if (oldAttempt > 0)
                    assertTrue(
                        vault.transcripts.paged.commitPart(
                            source,
                            job,
                            oldAttempt,
                            0,
                            publicPart(0),
                            null,
                        ) is AudioResult.Failed
                    )
                if (checkpoint == 1 || checkpoint == 3)
                    value(
                        vault.transcripts.paged.stop(
                            f.audio,
                            job,
                            resumed.attempt,
                            if (checkpoint == 1) "CANCELLED" else "FAILED",
                        )
                    )
                oldAttempt = resumed.attempt
                completed = checkpoint
            }
        }
        f.open(create = false).use { vault ->
            assertEquals(
                "INTERRUPTED",
                value(vault.transcripts.recover(f.audio))!!.jobs.last().status,
            )
            val resumed = value(vault.transcripts.paged.resume(f.audio, job))
            assertEquals(288, resumed.completedParts)
            assertEquals(null, resumed.nextIncompleteIndex)
            val done = value(vault.transcripts.paged.finish(source, job, resumed.attempt, 2))
            assertEquals(1, done.versions.size)
            assertTrue(done.versions.single().paged!!.textBytes > 262_144)
            val first = value(vault.transcripts.paged.page(f.audio, job, null))!!
            val changed =
                value(
                    vault.transcripts.paged.editPage(
                        f.audio,
                        job,
                        first.cursor,
                        done.revision,
                        first.digest,
                        "retained page correction",
                        3,
                    )
                )
            assertEquals(done.versions, changed.versions)
            fun streamedDigest(revision: Long): String {
                val digest = MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                PagedTranscriptRead.stream(
                    vault.transcripts.paged,
                    f.audio,
                    job,
                    revision,
                    { false },
                ) { part ->
                    val encoded = part.toByteArray(Charsets.UTF_8)
                    assertTrue(encoded.size <= 65_536)
                    bytes += encoded.size
                    digest.update(encoded)
                    encoded.fill(0)
                }
                assertTrue(bytes > 262_144)
                return digest.digest().joinToString("") { "%02x".format(it) }
            }
            fun expectedDigest(edited: Boolean): String {
                val digest = MessageDigest.getInstance("SHA-256")
                for (index in 0 until 288) {
                    if (index > 0) digest.update('\n'.code.toByte())
                    digest.update(
                        (if (edited && index == 0) "retained page correction"
                            else publicPart(index))
                            .toByteArray(Charsets.UTF_8)
                    )
                }
                return digest.digest().joinToString("") { "%02x".format(it) }
            }
            assertEquals(expectedDigest(false), streamedDigest(done.revision))
            assertEquals(expectedDigest(true), streamedDigest(changed.revision))
        }
        f.open(create = false).use { vault ->
            val document = value(vault.transcripts.load(f.audio))!!
            assertEquals(
                "retained page correction",
                value(vault.transcripts.paged.page(f.audio, job, null))!!.text,
            )
            assertEquals(source, document.source)
            value(vault.deleteRecordingConfirmed(f.audio))
            assertEquals(null, value(vault.transcripts.paged.progress(f.audio, job)))
            assertEquals(null, value(vault.transcripts.load(f.audio)))
            assertTrue(vault.transcripts.paged.page(f.audio, job, null) is AudioResult.Failed)
        }
    }

    private fun publicPart(index: Int) = "part ${index.toString().padStart(3, '0')}\n".repeat(256)

    @Test
    fun failureAndCancellationAtFirstThirdAndLastPartRetainProgressAcrossReopen() {
        for (failureAt in listOf(1, 3, 6)) {
            val fixture = EncryptedAudioVaultFaultFixture()
            val previous = UUID.randomUUID().toString()
            val job = UUID.randomUUID().toString()
            lateinit var saved: PagedTranscriptProgress
            fixture.open().use { vault ->
                value(vault.writer.create(fixture.audio))
                value(fixture.append(vault))
                value(vault.writer.finalize(fixture.audio))
                val source =
                    (value(vault.originals.acquire(fixture.audio)) as OriginalAudioStatus.Available)
                        .reference
                value(vault.transcripts.begin(source, previous, "en", 1))
                val old =
                    value(vault.transcripts.finish(source, previous, "previous text", "model", 2))
                value(
                    vault.transcripts.edit(
                        fixture.audio,
                        previous,
                        old.revision,
                        "owner correction",
                        3,
                    )
                )
                val spec =
                    PagedTranscriptSpec(
                        source,
                        job,
                        "LOCAL",
                        "en",
                        "model",
                        "local-fixed-300-v1",
                        27,
                        4,
                    )
                saved = value(vault.transcripts.paged.begin(spec))
                assertEquals(6, saved.totalParts)
                for (index in 0 until failureAt - 1) {
                    saved =
                        value(
                            vault.transcripts.paged.commitPart(
                                source,
                                job,
                                saved.attempt,
                                index,
                                "part $index\n".repeat(12_000),
                                null,
                            )
                        )
                }
                assertTrue(
                    vault.transcripts.paged.finish(source, job, saved.attempt, 5)
                        is AudioResult.Failed
                )
                assertEquals(1, value(vault.transcripts.load(fixture.audio))!!.versions.size)
                saved =
                    value(
                        vault.transcripts.paged.stop(fixture.audio, job, saved.attempt, "CANCELLED")
                    )
            }
            fixture.open(create = false).use { vault ->
                val before = value(vault.transcripts.recover(fixture.audio))!!
                assertEquals("owner correction", before.rendered(previous))
                val resumed = value(vault.transcripts.paged.resume(fixture.audio, job))
                assertEquals(failureAt - 1, resumed.completedParts)
                assertEquals(failureAt - 1, resumed.nextIncompleteIndex)
                assertTrue(resumed.attempt > saved.attempt)
                assertTrue(
                    vault.transcripts.paged.commitPart(
                        resumed.spec.source,
                        job,
                        saved.attempt,
                        failureAt - 1,
                        "late stale result",
                        null,
                    ) is AudioResult.Failed
                )
                for (index in failureAt - 1 until 6) {
                    value(
                        vault.transcripts.paged.commitPart(
                            resumed.spec.source,
                            job,
                            resumed.attempt,
                            index,
                            "part $index\n".repeat(12_000),
                            null,
                        )
                    )
                }
                val done =
                    value(
                        vault.transcripts.paged.finish(resumed.spec.source, job, resumed.attempt, 6)
                    )
                assertEquals(2, done.versions.size)
                assertEquals(before.versions, done.versions.take(1))
                assertEquals(before.edits, done.edits)
                assertEquals(before.selections, done.selections)
                assertEquals(before.activeId, done.activeId)
                assertTrue(done.versions.last().paged!!.textBytes > 262_144)
                var cursor: TranscriptPageCursor? = null
                var characters = 0L
                do {
                    val page =
                        value(vault.transcripts.paged.page(fixture.audio, job, cursor)) ?: break
                    assertTrue(page.text.toByteArray().size <= 65_536)
                    characters += page.text.length
                    cursor = page.cursor
                } while (true)
                assertEquals(504_000L, characters)
                fixture.exact(vault, finalized = true)
            }
        }
    }

    @Test
    fun pageEditIsAppendOnlyAndEarlierRenderRevisionRemainsExportable() {
        val f = EncryptedAudioVaultFaultFixture()
        f.open().use { vault ->
            value(vault.writer.create(f.audio))
            value(f.append(vault))
            value(vault.writer.finalize(f.audio))
            val source =
                (value(vault.originals.acquire(f.audio)) as OriginalAudioStatus.Available).reference
            val job = UUID.randomUUID().toString()
            val progress =
                value(
                    vault.transcripts.paged.begin(
                        PagedTranscriptSpec(
                            source,
                            job,
                            "LOCAL",
                            "ru",
                            "model",
                            "local-fixed-300-v1",
                            4_800_000,
                            1,
                        )
                    )
                )
            value(
                vault.transcripts.paged.commitPart(
                    source,
                    job,
                    progress.attempt,
                    0,
                    "original public page",
                    null,
                )
            )
            val done = value(vault.transcripts.paged.finish(source, job, progress.attempt, 2))
            val page = value(vault.transcripts.paged.page(f.audio, job, null))!!
            val edited =
                value(
                    vault.transcripts.paged.editPage(
                        f.audio,
                        job,
                        page.cursor,
                        done.revision,
                        page.digest,
                        "owner page correction",
                        3,
                    )
                )
            assertEquals(done.versions, edited.versions)
            assertEquals(
                "owner page correction",
                value(vault.transcripts.paged.page(f.audio, job, null))!!.text,
            )
            assertEquals(
                "original public page",
                value(vault.transcripts.paged.page(f.audio, job, null, done.revision))!!.text,
            )
            assertTrue(
                vault.transcripts.paged.editPage(
                    f.audio,
                    job,
                    page.cursor,
                    done.revision,
                    page.digest,
                    "stale overwrite",
                    4,
                ) is AudioResult.Failed
            )
            value(vault.deleteRecordingConfirmed(f.audio))
            assertTrue(vault.transcripts.paged.page(f.audio, job, null) is AudioResult.Failed)
        }
    }

    private fun <T> value(result: AudioResult<T>): T {
        assertTrue("Protected paged transcript operation failed", result is AudioResult.Value)
        return (result as AudioResult.Value).value
    }
}
