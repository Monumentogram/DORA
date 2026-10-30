@file:Suppress("MaxLineLength") // Literal multilingual search fixture.

package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.transcript.TranscriptDocument
import com.monumentogram.dora.audio.transcript.TranscriptReducer
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingSearchTest {
    private fun id(n: Int) = "00000000-0000-0000-0000-${n.toString().padStart(12, '0')}"

    private fun identity(n: Int) = AudioIdentity(RecordingId(id(n)), AudioAssetId(id(n)), id(9999))

    private val source = OriginalAudioReference(1, identity(1), "a".repeat(64), 16000)

    private fun document(text: String): TranscriptDocument =
        TranscriptReducer.finish(
            TranscriptReducer.begin(null, source, id(2001), "ru", 1),
            id(2001),
            text,
            "fixture",
            2,
        )

    @Test
    fun fullTextCaseSubstringUnicodeAndLiteralOperators() {
        val doc =
            document(
                "Заголовок\n" +
                    "длинный текст ".repeat(10000) +
                    "Сегодня обсуждали модернизацию конвейерного оборудования. English EQUIPMENT Ελληνικά 😀 100% _ [a]*"
            )
        for (query in
            listOf("зАГОЛОВОК", "  КОНВЕЙЕР  ", "equip", "ΕΛΛΗΝ", "😀", "100%", "_", "[a]*")) {
            val result = RecordingSearch.preview(doc, "Запись 00000000", query)
            assertNotNull(query, result)
            assertTrue(result!!.snippet.length <= 242)
        }
        assertTrue(
            RecordingSearch.preview(doc, "fallback", "конвейер")!!.snippet.contains("конвейер")
        )
        assertNull(RecordingSearch.preview(doc, "fallback", "отсутствует"))
    }

    @Test
    fun onlyCurrentEditedVersionIsSearchable() {
        val machine = document("old machine")
        val edited =
            TranscriptReducer.edit(machine, id(2001), machine.revision, "правка пользователя", 3)
        assertNull(RecordingSearch.preview(edited, "fallback", "machine"))
        assertNotNull(RecordingSearch.preview(edited, "fallback", "ПРАВКА"))
        val second =
            TranscriptReducer.finish(
                TranscriptReducer.begin(edited, source, id(2002), "en", 4),
                id(2002),
                "new version",
                "fixture",
                5,
            )
        val selected =
            TranscriptReducer.select(
                second,
                id(2002),
                second.revision,
                edited.edits.map { it.id }.toSet(),
                6,
            )
        assertNull(RecordingSearch.preview(selected, "fallback", "правка"))
        assertNotNull(RecordingSearch.preview(selected, "fallback", "VERSION"))
        assertEquals(edited.edits, selected.edits)
    }

    @Test
    fun noTranscriptAndEmptyQueryUseDisplayedTitle() {
        assertNotNull(RecordingSearch.preview(null, "Запись abcd1234", "ABCD"))
        assertNotNull(RecordingSearch.preview(null, "Recording abcd1234", "record"))
        assertNull(RecordingSearch.preview(null, "Recording abcd1234", "nothing"))
        assertNotNull(RecordingSearch.preview(null, "Recording abcd1234", "  \n "))
        assertNull(RecordingSearch.preview(document(""), "Recording abcd1234", "old"))
    }

    @Test
    fun sparsePagesHaveNoDuplicatesOrSkippedCandidates() {
        val ids = (1..100).map(::identity)
        var reads = 0
        var recovered = 0
        val search =
            RecordingSearch(
                candidates = { after ->
                    ids.filter { it.assetId.value > after }.take(20).map { it.assetId.value to it }
                },
                document = {
                    reads++
                    AudioResult.Value(
                        document(
                            if (it.assetId.value.takeLast(1).toInt() % 2 == 0) "hit" else "miss"
                        )
                    )
                },
                metadata = {
                    recovered++
                    AudioResult.Value(
                        RecordingLibraryMetadata(it, 0, RecordingLibraryState.NEEDS_RECOVERY)
                    )
                },
            )
        var cursor = ""
        val found = mutableListOf<String>()
        do {
            val page =
                (search.page("hit", "Recording %s", cursor) { false } as AudioResult.Value).value
            assertTrue(page.rows.size <= 20)
            found += page.rows.map { it.recording.cursor }
            cursor = page.nextCursor.orEmpty()
        } while (cursor.isNotEmpty())
        assertEquals(
            ids.filter { it.assetId.value.takeLast(1).toInt() % 2 == 0 }.map { it.assetId.value },
            found,
        )
        assertEquals(100, reads)
        assertEquals(50, recovered)
    }

    @Test
    fun cancellationStopsScanAndReadFailureIsNotNoMatch() {
        var reads = 0
        val ids = (1..1000).map(::identity)
        val search =
            RecordingSearch(
                { after ->
                    ids.filter { it.assetId.value > after }.take(20).map { it.assetId.value to it }
                },
                {
                    reads++
                    AudioResult.Value(null)
                },
                { error("No match must not recover audio") },
            )
        assertEquals(
            AudioResult.Failed(AudioFailure.CANCELLED),
            search.page("absent", "Recording %s", "") { reads >= 3 },
        )
        assertEquals(3, reads)
        val broken =
            RecordingSearch(
                { listOf(id(1) to identity(1)) },
                { AudioResult.Failed(AudioFailure.CORRUPT) },
                { error("unreachable") },
            )
        assertEquals(
            AudioResult.Failed(AudioFailure.CORRUPT),
            broken.page("text", "Recording %s", "") { false },
        )
    }
}
