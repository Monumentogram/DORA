package com.monumentogram.dora.audio

import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioSegmentIndexTest {
    @Test
    fun persistentIndexKeepsOldSnapshotsAndLongTimelinePastTwoGiB() {
        val id = AudioIdentity(RecordingId(uuid(1)), AudioAssetId(uuid(2)), uuid(3))
        var units: List<StoredAudioSegment> = AudioSegmentIndex.empty()
        repeat(18_000) { ordinal ->
            val previous = units
            val segment =
                StoredAudioSegment(
                    AudioStorageUnitIdentity(id, uuid(ordinal + 4), ordinal, ordinal * 80_000L),
                    80_000,
                    Sha256Value.ZERO,
                )
            units = AudioSegmentIndex.append(units, segment)
            assertEquals(ordinal, previous.size)
            if (ordinal > 0)
                assertEquals((ordinal - 1) * 80_000L, previous.last().identity.firstFrame)
        }
        assertEquals(0L, units.first().identity.firstFrame)
        assertEquals(720_000_000L, units[9_000].identity.firstFrame)
        assertEquals(1_439_920_000L, units.last().identity.firstFrame)
        assertEquals(2_880_000_000L, (units.last().identity.firstFrame + units.last().frames) * 2)
    }

    private fun uuid(value: Int) = "00000000-0000-0000-0000-%012d".format(value)
}
