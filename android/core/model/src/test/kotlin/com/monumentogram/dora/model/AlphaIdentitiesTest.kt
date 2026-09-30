package com.monumentogram.dora.model

import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecognitionJobId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.model.alpha.TranscriptId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AlphaIdentitiesTest {
    private val id = "00000000-0000-4000-8000-000000000001"

    @Test
    fun stableValuesRemainDistinctAcrossIdentityKinds() {
        assertEquals(RecordingId(id), RecordingId(id))
        assertEquals(RecordingId(id).hashCode(), RecordingId(id).hashCode())
        assertNotEquals(RecordingId(id) as Any, AudioAssetId(id) as Any)
        assertNotEquals(RecognitionJobId(id) as Any, TranscriptId(id) as Any)
        assertEquals(1, setOf(RecordingId(id), RecordingId(id)).size)
    }

    @Test
    fun identitiesRejectPathsProviderNamesEmptyAndNonCanonicalValues() {
        listOf(
                "",
                " ",
                "/private/audio.wav",
                "arn:aws:job",
                "1-1-1-1-1",
                id.uppercase().replace("4000", "ABCD"),
            )
            .forEach {
                assertThrows(IllegalArgumentException::class.java) { RecordingId(it) }
                assertThrows(IllegalArgumentException::class.java) { AudioAssetId(it) }
                assertThrows(IllegalArgumentException::class.java) { RecognitionJobId(it) }
                assertThrows(IllegalArgumentException::class.java) { TranscriptId(it) }
            }
    }
}
