package com.monumentogram.dora.audio.transcript

import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.util.Base64
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
