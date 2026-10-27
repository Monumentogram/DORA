package com.monumentogram.dora.audio.transcript

import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudTranscriptTest {
    private fun id(n: Int) = "00000000-0000-0000-0000-${n.toString().padStart(12, '0')}"

    private val source =
        OriginalAudioReference(
            1,
            AudioIdentity(RecordingId(id(1)), AudioAssetId(id(2)), id(3)),
            "a".repeat(64),
            16000,
        )

    private fun cloud(old: TranscriptDocument? = null) =
        TranscriptReducer.beginCloud(old, source, id(5), "ru", CloudConsent(grantedAt = 3), 3)

    private fun result() =
        CloudTranscriptResult(
            "cloud text",
            "amazon-transcribe",
            "standard-batch:eu-central-1:ru-RU",
            "dora-test-job",
            source.digest,
            1,
            5,
            listOf(CloudSegment("cloud", 0, 500, 0.9), CloudSegment("text", null, null, null)),
        )

    @Test
    fun cloudCompletionPreservesEditsAndSelectedLocalTextAcrossCodecRestart() {
        val local =
            TranscriptReducer.finish(
                TranscriptReducer.begin(null, source, id(4), "ru", 1),
                id(4),
                "local",
                "small",
                2,
            )
        val edited = TranscriptReducer.edit(local, id(4), local.revision, "my correction", 3)
        val pending = cloud(edited)
        val restart =
            TranscriptReducer.recover(TranscriptCodec.decode(TranscriptCodec.encode(pending)))
        assertEquals("QUEUED", restart.jobs.last().status)
        val done = TranscriptReducer.finishCloud(restart, id(5), result())
        val restored = TranscriptCodec.decode(TranscriptCodec.encode(done))
        assertEquals("my correction", restored.rendered(id(4)))
        assertEquals(id(4), restored.activeId)
        assertEquals("CLOUD", restored.versions.last().engine)
        assertEquals(result(), restored.versions.last().cloud)
        assertEquals(restored, TranscriptReducer.finishCloud(restored, id(5), result()))
        assertEquals(2, restored.versions.size)
        assertNull(restored.versions.last().cloud!!.segments.last().startMs)
    }

    @Test
    fun revokedConsentAndCancelledLateCallbackCannotPublishOrResume() {
        val cancelled = TranscriptReducer.revokeCloud(cloud(), id(5))
        assertTrue(cancelled.jobs.last().consent!!.revoked)
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptReducer.finishCloud(cancelled, id(5), result())
        }
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptReducer.resumeCloud(cancelled, id(5))
        }
        assertEquals(source, cancelled.source)
    }

    @Test
    fun retryKeepsIdentityAndRejectsWrongSourceOrAlteredResult() {
        val failed = TranscriptReducer.stop(cloud(), id(5), "FAILED")
        val resumed =
            TranscriptReducer.resumeCloud(
                TranscriptCodec.decode(TranscriptCodec.encode(failed)),
                id(5),
            )
        assertEquals(1, resumed.jobs.size)
        assertEquals(3L, resumed.jobs.single().consent!!.grantedAt)
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptReducer.finishCloud(
                resumed,
                id(5),
                result().copy(sourceDigest = "b".repeat(64)),
            )
        }
        val done = TranscriptReducer.finishCloud(resumed, id(5), result())
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptReducer.finishCloud(done, id(5), result().copy(text = "changed"))
        }
    }

    @Test
    fun invalidConsentAndFabricatedTimingsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptReducer.beginCloud(
                null,
                source,
                id(5),
                "en",
                CloudConsent(version = "old", grantedAt = 3),
                3,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptReducer.finishCloud(
                cloud(),
                id(5),
                result().copy(segments = listOf(CloudSegment("bad", 900, 100, null))),
            )
        }
    }

    @Test
    fun olderCloudCanResumeAfterLocalFallbackWithoutChangingHistoryOrSelection() {
        val failed = TranscriptReducer.stop(cloud(), id(5), "FAILED")
        val local =
            TranscriptReducer.finish(
                TranscriptReducer.begin(failed, source, id(6), "en", 6),
                id(6),
                "local",
                "small",
                7,
            )
        val edited = TranscriptReducer.edit(local, id(6), local.revision, "my correction", 8)
        val resumed = TranscriptReducer.resumeCloud(edited, id(5))
        assertEquals(listOf(id(5), id(6)), resumed.jobs.map { it.id })
        assertEquals(listOf(1L, 2L), resumed.jobs.map { it.generation })
        val done = TranscriptReducer.finishCloud(resumed, id(5), result())
        assertEquals(id(6), done.activeId)
        assertEquals("my correction", done.rendered(id(6)))
        assertEquals(done, TranscriptCodec.decode(TranscriptCodec.encode(done)))
        assertEquals(listOf(id(6), id(5)), done.versions.map { it.id })
    }

    @Test
    fun olderCloudCannotResumeWhileAnotherJobIsActive() {
        val failed = TranscriptReducer.stop(cloud(), id(5), "FAILED")
        val pendingLocal = TranscriptReducer.begin(failed, source, id(6), "en", 6)
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptReducer.resumeCloud(pendingLocal, id(5))
        }
    }

    @Test
    fun partialProviderTimingRemainsPartialRatherThanInvented() {
        val partial = result().copy(segments = listOf(CloudSegment("cloud", null, 500, null)))
        val done = TranscriptReducer.finishCloud(cloud(), id(5), partial)
        val restored = TranscriptCodec.decode(TranscriptCodec.encode(done))
        assertNull(restored.versions.single().cloud!!.segments.single().startMs)
        assertEquals(500L, restored.versions.single().cloud!!.segments.single().endMs)
        for (segment in
            listOf(CloudSegment("bad", null, 1001, null), CloudSegment("bad", -1, null, null))) {
            assertThrows(IllegalArgumentException::class.java) {
                TranscriptReducer.finishCloud(
                    cloud(),
                    id(5),
                    result().copy(segments = listOf(segment)),
                )
            }
        }
    }

    @Test
    fun aggregateCloudSegmentBytesAreBoundedBeforeSerialization() {
        val versions =
            (0 until 33).map { index ->
                val output =
                    result()
                        .copy(
                            text = "",
                            segments =
                                listOf(
                                    CloudSegment(
                                        "x".repeat(TranscriptCodec.MAX_TEXT_BYTES),
                                        null,
                                        null,
                                        null,
                                    )
                                ),
                        )
                MachineTranscript(
                    id(100 + index),
                    output.text,
                    output.completedAt,
                    output.model,
                    output,
                )
            }
        val jobs = versions.mapIndexed { index, version ->
            TranscriptJob(version.id, "SUCCEEDED", "ru", index + 1L, 3, CloudConsent(grantedAt = 3))
        }
        val tooLarge = TranscriptDocument(source, revision = 70, versions = versions, jobs = jobs)
        assertThrows(IllegalArgumentException::class.java) { TranscriptCodec.validate(tooLarge) }
    }
}
