package com.monumentogram.dora.audio.persistence

import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioStatus
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptPersistenceTest {
    private fun <T> success(value: AudioResult<T>): T {
        assertTrue(value is AudioResult.Value)
        return (value as AudioResult.Value).value
    }

    @Test
    fun encryptedVersionsEditsAndSelectionSurviveReopenAndAudioDeletion() {
        val f = EncryptedAudioVaultFaultFixture()
        val job = UUID.randomUUID().toString()
        val source =
            f.open().use { vault ->
                success(vault.writer.create(f.audio))
                success(f.append(vault))
                success(vault.writer.finalize(f.audio))
                val ref =
                    (success(vault.originals.acquire(f.audio)) as OriginalAudioStatus.Available)
                        .reference
                success(vault.transcripts.begin(ref, job, "ru", 1))
                success(vault.transcripts.running(f.audio, job))
                val finished =
                    success(
                        vault.transcripts.finish(ref, job, "Synthetic machine text", "small-q8", 2)
                    )
                success(
                    vault.transcripts.edit(
                        f.audio,
                        job,
                        finished.revision,
                        "Synthetic user text",
                        3,
                    )
                )
                ref
            }
        f.open(create = false).use { vault ->
            val doc = success(vault.transcripts.load(f.audio))!!
            assertEquals("Synthetic user text", doc.rendered(job))
            success(vault.deleteAudio(f.audio))
            assertEquals(doc, success(vault.transcripts.load(f.audio)))
            assertEquals(listOf(f.audio), success(vault.transcripts.history()))
            assertTrue(
                vault.transcripts.begin(source, UUID.randomUUID().toString(), "ru", 4)
                    is AudioResult.Failed
            )
        }
        f.open(create = false).use { vault ->
            assertEquals(
                "Synthetic user text",
                success(vault.transcripts.load(f.audio))!!.rendered(job),
            )
        }
    }

    @Test
    fun interruptedAndCancelledWorkCannotPublishAndFreshGenerationCanRun() {
        val f = EncryptedAudioVaultFaultFixture()
        val job = UUID.randomUUID().toString()
        val source =
            f.open().use { vault ->
                success(vault.writer.create(f.audio))
                success(f.append(vault))
                success(vault.writer.finalize(f.audio))
                val ref =
                    (success(vault.originals.acquire(f.audio)) as OriginalAudioStatus.Available)
                        .reference
                success(vault.transcripts.begin(ref, job, "en", 1))
                ref
            }
        f.open(create = false).use { vault ->
            assertEquals(
                "INTERRUPTED",
                success(vault.transcripts.recover(f.audio))!!.jobs.single().status,
            )
            assertTrue(
                vault.transcripts.finish(source, job, "late", "small-q8", 2) is AudioResult.Failed
            )
            val next = UUID.randomUUID().toString()
            assertEquals(
                2L,
                success(vault.transcripts.begin(source, next, "en", 3)).jobs.last().generation,
            )
            success(vault.transcripts.stop(f.audio, next, "CANCELLED"))
            assertTrue(
                vault.transcripts.finish(source, next, "late", "small-q8", 4) is AudioResult.Failed
            )
        }
    }

    @Test
    fun deletingAudioCancelsDurableJobsBeforeSourceRemoval() {
        val f = EncryptedAudioVaultFaultFixture()
        val job = UUID.randomUUID().toString()
        f.open().use { vault ->
            success(vault.writer.create(f.audio))
            success(f.append(vault))
            success(vault.writer.finalize(f.audio))
            val ref =
                (success(vault.originals.acquire(f.audio)) as OriginalAudioStatus.Available)
                    .reference
            success(vault.transcripts.begin(ref, job, "ru", 1))
            success(vault.deleteAudio(f.audio))
            assertEquals(
                "CANCELLED",
                success(vault.transcripts.load(f.audio))!!.jobs.single().status,
            )
            assertTrue(
                vault.transcripts.finish(ref, job, "late", "small-q8", 2) is AudioResult.Failed
            )
        }
    }
}
