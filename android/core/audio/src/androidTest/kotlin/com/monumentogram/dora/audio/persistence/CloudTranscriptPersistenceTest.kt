package com.monumentogram.dora.audio.persistence

import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.transcript.CloudConsent
import com.monumentogram.dora.audio.transcript.CloudTranscriptResult
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudTranscriptPersistenceTest {
    private fun <T> value(result: AudioResult<T>): T = (result as AudioResult.Value).value

    @Test
    fun cloudConsentAndResultSurviveEncryptedReopenWithoutReplacingLocalEdits() {
        val fixture = EncryptedAudioVaultFaultFixture()
        val local = UUID.randomUUID().toString()
        val cloud = UUID.randomUUID().toString()
        val source =
            fixture.open().use { vault ->
                value(vault.writer.create(fixture.audio))
                value(fixture.append(vault))
                value(vault.writer.finalize(fixture.audio))
                val ref =
                    (value(vault.originals.acquire(fixture.audio)) as OriginalAudioStatus.Available)
                        .reference
                value(vault.transcripts.begin(ref, local, "en", 1))
                val doc = value(vault.transcripts.finish(ref, local, "synthetic local", "small", 2))
                value(
                    vault.transcripts.edit(
                        fixture.audio,
                        local,
                        doc.revision,
                        "synthetic correction",
                        3,
                    )
                )
                value(
                    vault.transcripts.beginCloud(ref, cloud, "en", CloudConsent(grantedAt = 4), 4)
                )
                ref
            }
        val result =
            CloudTranscriptResult(
                "synthetic cloud",
                "amazon-transcribe",
                "standard-batch:eu-central-1:en-US",
                "synthetic-job",
                source.digest,
                1,
                5,
            )
        fixture.open(create = false).use { vault ->
            assertEquals(
                "QUEUED",
                value(vault.transcripts.recover(fixture.audio))!!.jobs.last().status,
            )
            value(vault.transcripts.finishCloud(source, cloud, result))
        }
        fixture.open(create = false).use { vault ->
            val doc = value(vault.transcripts.load(fixture.audio))!!
            assertEquals(local, doc.activeId)
            assertEquals("synthetic correction", doc.rendered(local))
            assertEquals("CLOUD", doc.versions.last().engine)
            assertEquals(doc, value(vault.transcripts.finishCloud(source, cloud, result)))
            assertEquals(2, doc.versions.size)
            assertTrue(
                vault.transcripts.select(fixture.audio, cloud, doc.revision, emptySet(), 6)
                    is AudioResult.Failed
            )
        }
    }

    @Test
    fun cancelledCloudCannotUploadOrPublishAfterReopenAndAudioRemains() {
        val fixture = EncryptedAudioVaultFaultFixture()
        val job = UUID.randomUUID().toString()
        fixture.open().use { vault ->
            value(vault.writer.create(fixture.audio))
            value(fixture.append(vault))
            value(vault.writer.finalize(fixture.audio))
            val source =
                (value(vault.originals.acquire(fixture.audio)) as OriginalAudioStatus.Available)
                    .reference
            value(vault.transcripts.beginCloud(source, job, "ru", CloudConsent(grantedAt = 1), 1))
            value(vault.transcripts.revokeCloud(fixture.audio, job))
        }
        fixture.open(create = false).use { vault ->
            val doc = value(vault.transcripts.load(fixture.audio))!!
            assertTrue(doc.jobs.single().consent!!.revoked)
            assertTrue(vault.transcripts.resumeCloud(fixture.audio, job) is AudioResult.Failed)
            assertTrue(
                value(vault.originals.acquire(fixture.audio)) is OriginalAudioStatus.Available
            )
            val result =
                CloudTranscriptResult(
                    "late",
                    "amazon-transcribe",
                    "standard-batch:eu-central-1:ru-RU",
                    "synthetic",
                    doc.source.digest,
                    1,
                    2,
                )
            assertTrue(vault.transcripts.finishCloud(doc.source, job, result) is AudioResult.Failed)
        }
    }
}
