package com.monumentogram.dora.audio.transcript

import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class TranscriptStateTest {
    @Test
    fun legacyV1LocalPayloadPreservesContentAndUpgradesWithoutCloudConsent() {
        // Independent fixed v1 wire fixture, not emitted by the current encoder.
        val fixture = checkNotNull(javaClass.getResource("/transcript-v1.b64")).readText().trim()
        val decoded = TranscriptCodec.decode(Base64.getDecoder().decode(fixture))
        assertEquals(complete(), decoded)
        assertEquals(decoded, TranscriptCodec.decode(TranscriptCodec.encode(decoded)))
        assertEquals(null, decoded.jobs.single().consent)
        assertEquals(null, decoded.versions.single().cloud)
    }

    @Test
    fun richLegacyV1PreservesEveryFieldThroughDtr2Upgrade() {
        val decoded = richLegacy()
        assertEquals(expectedRichLegacy(), decoded)
        assertEquals(decoded, TranscriptReducer.recover(decoded))
        val upgraded = TranscriptCodec.encode(decoded)
        assertArrayEquals("DTR2".toByteArray(Charsets.US_ASCII), upgraded.copyOf(4))
        assertEquals(decoded, TranscriptCodec.decode(upgraded))
        assertArrayEquals(upgraded, TranscriptCodec.encode(TranscriptCodec.decode(upgraded)))
        assertEquals("исправленный первый", decoded.rendered(id(4)))
        assertEquals("corrected second", decoded.rendered(id(5)))
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptReducer.finish(decoded, id(6), "late cancelled output", "small-q8", 10)
        }
    }

    @Test
    fun localAndCloudAppendPreserveRichLegacyHistoryAndOlderSelection() {
        val legacy = richLegacy()
        val local =
            TranscriptReducer.finish(
                TranscriptReducer.begin(legacy, source, id(9), "en", 10),
                id(9),
                "new local",
                "small-q8",
                11,
            )
        val pending =
            TranscriptReducer.beginCloud(
                TranscriptCodec.decode(TranscriptCodec.encode(local)),
                source,
                id(10),
                "en",
                CloudConsent(grantedAt = 12),
                12,
            )
        val result =
            CloudTranscriptResult(
                "new cloud",
                "amazon-transcribe",
                "standard-batch:eu-central-1:en-US",
                "synthetic-legacy-append",
                source.digest,
                1,
                13,
            )
        val completed = TranscriptReducer.finishCloud(pending, id(10), result)
        val reopened = TranscriptCodec.decode(TranscriptCodec.encode(completed))
        assertEquals(completed, reopened)
        assertEquals(legacy.source, reopened.source)
        assertEquals(legacy.activeId, reopened.activeId)
        assertEquals(legacy.versions, reopened.versions.take(2))
        assertEquals(legacy.jobs, reopened.jobs.take(3))
        assertEquals(legacy.edits, reopened.edits)
        assertEquals(legacy.selections, reopened.selections)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), reopened.jobs.map { it.generation })
        assertEquals("new local", reopened.rendered(id(9)))
        assertEquals(result, reopened.versions.last().cloud)
        assertEquals("исправленный первый", reopened.rendered(id(4)))
        assertEquals("corrected second", reopened.rendered(id(5)))
    }

    private fun richLegacy(): TranscriptDocument {
        // See generate_transcript_v1_rich.py: independently serialized from the old DTR1 layout.
        val fixture =
            checkNotNull(javaClass.getResource("/transcript-v1-rich.b64")).readText().trim()
        val bytes = Base64.getDecoder().decode(fixture)
        assertArrayEquals("DTR1".toByteArray(Charsets.US_ASCII), bytes.copyOf(4))
        return TranscriptCodec.decode(bytes)
    }

    private fun expectedRichLegacy() =
        TranscriptDocument(
            source,
            revision = 9,
            activeId = id(4),
            versions =
                listOf(
                    MachineTranscript(id(4), "первый оригинал", 2, "small-q8"),
                    MachineTranscript(id(5), "second original", 5, "small-q8"),
                ),
            edits =
                listOf(
                    TranscriptEdit(id(7), id(4), "первый оригинал", "исправленный первый", 3),
                    TranscriptEdit(id(8), id(5), "second original", "corrected second", 6),
                ),
            jobs =
                listOf(
                    TranscriptJob(id(4), "SUCCEEDED", "ru", 1, 1),
                    TranscriptJob(id(5), "SUCCEEDED", "en", 2, 4),
                    TranscriptJob(id(6), "CANCELLED", "ru", 3, 8),
                ),
            selections = listOf(TranscriptSelection(id(4), 6, setOf(id(8)), 7)),
        )

    private val source =
        OriginalAudioReference(
            1,
            AudioIdentity(RecordingId(id(1)), AudioAssetId(id(2)), id(3)),
            "a".repeat(64),
            16000,
        )

    private fun initial() = TranscriptReducer.begin(null, source, id(4), "ru", 1)

    private fun complete() = TranscriptReducer.finish(initial(), id(4), "machine", "small-q8", 2)

    @Test
    fun firstResultAndRedeliveryPreserveImmutableIdentity() {
        val doc = complete()
        assertEquals(id(4), doc.activeId)
        assertEquals(doc, TranscriptReducer.finish(doc, id(4), "machine", "small-q8", 2))
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptReducer.finish(doc, id(4), "different", "small-q8", 2)
        }
    }

    @Test
    fun cancellationAndRecoveryFenceLateResultsAndKeepGenerations() {
        val cancelled = TranscriptReducer.stop(initial(), id(4), "CANCELLED")
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptReducer.finish(cancelled, id(4), "late", "small-q8", 2)
        }
        val next = TranscriptReducer.begin(cancelled, source, id(5), "en", 3)
        assertEquals(2L, next.jobs.last().generation)
        assertEquals("INTERRUPTED", TranscriptReducer.recover(next).jobs.last().status)
    }

    @Test
    fun editsRemainSeparateAndSelectionRequiresEveryForeignEdit() {
        val raw = complete()
        val edited = TranscriptReducer.edit(raw, id(4), raw.revision, "correction", 3)
        assertEquals("machine", edited.versions.single().text)
        assertEquals("correction", edited.rendered(id(4)))
        val next =
            TranscriptReducer.finish(
                TranscriptReducer.begin(edited, source, id(5), "ru", 4),
                id(5),
                "candidate",
                "small-q8",
                5,
            )
        assertEquals(id(4), next.activeId)
        assertEquals(1, next.conflicts(id(5)).size)
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptReducer.select(next, id(5), next.revision, emptySet(), 6)
        }
        val selected =
            TranscriptReducer.select(
                next,
                id(5),
                next.revision,
                next.edits.map { it.id }.toSet(),
                6,
            )
        assertEquals(id(5), selected.activeId)
        assertEquals(edited.edits, selected.edits)
        assertEquals(1, selected.selections.size)
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptReducer.edit(selected, id(5), next.revision, "stale", 7)
        }
    }

    @Test
    fun codecRoundtripRejectsTrailingTruncatedAndOversizedData() {
        val doc = complete()
        val bytes = TranscriptCodec.encode(doc)
        assertEquals(doc, TranscriptCodec.decode(bytes))
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptCodec.decode(bytes + byteArrayOf(0))
        }
        assertThrows(Exception::class.java) { TranscriptCodec.decode(bytes.copyOf(12)) }
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptReducer.finish(
                initial(),
                id(4),
                "x".repeat(TranscriptCodec.MAX_TEXT_BYTES + 1),
                "small-q8",
                2,
            )
        }
        assertFalse(doc.toString().contains("machine"))
        assertFalse(doc.versions.single().toString().contains("machine"))
    }

    private fun id(number: Int) = "00000000-0000-0000-0000-${number.toString().padStart(12, '0')}"

    @Test
    fun sourceMismatchCannotReuseExistingHistory() {
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptReducer.begin(
                complete(),
                source.copy(digest = "b".repeat(64)),
                id(5),
                "ru",
                3,
            )
        }
    }

    @Test
    fun oneRunningJobAndDuplicateBeginDoNotResetState() {
        val queued = initial()
        val running = TranscriptReducer.running(queued, id(4))
        assertEquals(running, TranscriptReducer.begin(running, source, id(4), "ru", 1))
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptReducer.begin(running, source, id(5), "ru", 3)
        }
        assertEquals(running, TranscriptReducer.running(running, id(4)))
    }

    @Test
    fun manualOlderSelectionSurvivesNewResultsAndCountersAreSeparate() {
        val first = complete()
        val second =
            TranscriptReducer.finish(
                TranscriptReducer.begin(first, source, id(5), "en", 3),
                id(5),
                "second",
                "small-q8",
                4,
            )
        val selected = TranscriptReducer.select(second, id(4), second.revision, emptySet(), 5)
        val third =
            TranscriptReducer.finish(
                TranscriptReducer.begin(selected, source, id(6), "en", 6),
                id(6),
                "third",
                "small-q8",
                7,
            )
        assertEquals(id(4), third.activeId)
        assertEquals(0L, third.editRevision)
        assertEquals(1L, third.selectionRevision)
        assertEquals("UNAVAILABLE", third.versions.first().timestampAvailability)
        assertEquals("NOT_EVALUABLE", third.versions.first().timestampQuality)
        assertEquals("LOCAL", third.versions.first().engine)
    }

    @Test
    fun unpairedSurrogateCannotBeSilentlyReplacedDuringPersistence() {
        assertThrows(Exception::class.java) {
            TranscriptReducer.finish(initial(), id(4), "broken\uD800", "small-q8", 2)
        }
    }

    @Test
    fun editContextIsExactAndCodecRejectsForgedHistory() {
        val raw = complete()
        val edited = TranscriptReducer.edit(raw, id(4), raw.revision, "one", 3)
        val editedAgain = TranscriptReducer.edit(edited, id(4), edited.revision, "two", 4)
        assertEquals("one", editedAgain.edits.last().before)
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptCodec.encode(
                editedAgain.copy(edits = editedAgain.edits.map { it.copy(before = "forged") })
            )
        }
    }
}
