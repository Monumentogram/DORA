package com.monumentogram.dora.audio.transcript

import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedTranscriptTest {
    private fun spec(frames: Long, cloud: Boolean = false) =
        PagedTranscriptSpec(
            OriginalAudioReference(
                1,
                AudioIdentity(RecordingId(id(1)), AudioAssetId(id(2)), id(3)),
                "a".repeat(64),
                frames,
            ),
            id(4),
            if (cloud) "CLOUD" else "LOCAL",
            "en",
            "controlled-model",
            if (cloud) "cloud-balanced-1800-v1" else "local-fixed-300-v1",
            if (cloud) 28_800_000L else 4_800_000L,
            1,
            if (cloud) CloudConsent(grantedAt = 1) else null,
        )

    @Test
    fun twentyFourHoursHas288ExactLocalWindowsWithoutIntegerOverflow() {
        val value = spec(1_382_400_000L)
        assertEquals(288, value.partCount)
        assertEquals(0L, value.range(0).firstFrame)
        assertEquals(1_377_600_000L, value.range(287).firstFrame)
        assertEquals(4_800_000L, value.range(287).frameCount)
        assertThrows(IllegalArgumentException::class.java) { value.range(288) }
    }

    @Test
    fun cloudBalancesTinyTailWithoutDroppingFramesOrCreatingInvalidProviderPart() {
        val value = spec(28_800_001L, true)
        assertEquals(2, value.partCount)
        assertEquals(TranscriptFrameRange(0, 14_400_000), value.range(0))
        assertEquals(TranscriptFrameRange(14_400_000, 14_400_001), value.range(1))
    }

    @Test
    fun utf8PagesPreserveSupplementaryCharactersAndExceedOldTotalLimit() {
        val text = "Русский 😀 English\n".repeat(20_000)
        val pages = PagedTranscriptText.pages(text)
        assertTrue(text.toByteArray(Charsets.UTF_8).size > TranscriptLimits.MAX_TEXT_BYTES)
        assertTrue(pages.size > 1)
        assertEquals(text, pages.joinToString(""))
        assertTrue(pages.all { it.toByteArray(Charsets.UTF_8).size <= 65_536 })
        assertFalse(pages.any { it.lastOrNull()?.isHighSurrogate() == true })
        assertThrows(IllegalArgumentException::class.java) {
            PagedTranscriptText.pages("broken\uD800")
        }
    }

    @Test
    fun maximumUtf8PartMayNeedSeventeenPagesAndManifestRejectsImpossibleCounts() {
        // 1,048,575 UTF-8 bytes; boundaries inside a four-byte character shorten full pages.
        val pages = PagedTranscriptText.pages("a😀".repeat(209_715))
        assertEquals(17, pages.size)
        val reference =
            PagedTranscriptReference(
                "LOCAL",
                "a".repeat(64),
                "local-fixed-300-v1",
                1,
                17,
                1_048_575,
                "b".repeat(64),
                1,
            )
        reference.validate()
        assertThrows(IllegalArgumentException::class.java) {
            reference.copy(pageCount = 18).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            reference.copy(textBytes = 1_048_577).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            reference
                .copy(
                    engine = "CLOUD",
                    windowPolicy = "cloud-balanced-1800-v1",
                    partCount = 49,
                    pageCount = 49,
                )
                .validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            spec(235_200_000, true).copy(windowFrames = 4_800_000)
        }
        assertEquals(48, spec(1_382_400_000, true).partCount)
    }

    @Test
    fun dtr3ReferencesLargeTextWithoutSerializingItsContentsAndPreservesLegacyEdit() {
        val source = spec(28_800_000).source
        val old =
            TranscriptReducer.finish(
                TranscriptReducer.begin(null, source, id(5), "en", 1),
                id(5),
                "old",
                "model",
                2,
            )
        val edited = TranscriptReducer.edit(old, id(5), old.revision, "owner correction", 3)
        val begun = TranscriptReducer.begin(edited, source, id(4), "en", 4)
        val reference =
            PagedTranscriptReference(
                "LOCAL",
                "a".repeat(64),
                "local-fixed-300-v1",
                6,
                12,
                500_000,
                "b".repeat(64),
                begun.revision + 1,
            )
        val done = TranscriptReducer.finishPaged(begun, id(4), reference, "model", 5)
        val bytes = TranscriptCodec.encode(done)
        assertEquals("DTR3", bytes.copyOf(4).toString(Charsets.US_ASCII))
        assertTrue(bytes.size < 4096)
        val restored = TranscriptCodec.decode(bytes)
        assertEquals(done, restored)
        assertEquals("owner correction", restored.rendered(id(5)))
        assertEquals(id(5), restored.activeId)
        assertEquals(edited.edits, restored.edits)
        assertThrows(IllegalStateException::class.java) { restored.rendered(id(4)) }
    }

    private fun id(value: Int) = "00000000-0000-4000-8000-${value.toString().padStart(12, '0')}"
}
