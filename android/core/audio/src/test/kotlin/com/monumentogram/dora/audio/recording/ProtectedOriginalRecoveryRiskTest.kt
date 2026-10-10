package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.audio.StoredAudioSegment
import com.monumentogram.dora.audio.logical.LogicalRecordingProjectionTest.Companion.id
import com.monumentogram.dora.audio.logical.LogicalRecordingProjectionTest.Companion.pair
import com.monumentogram.dora.audio.logical.LogicalRecordingProjectionTest.Companion.source
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic structural reproduction only; no owner identities, PCM or device access. */
class ProtectedOriginalRecoveryRiskTest {
    @Test
    fun nonLongOriginalWithOpenCapTailWouldRequireTwoDurableMetadataAdditions() {
        val units =
            (0 until 127).map { ordinal ->
                val first = ordinal * 80_000L
                val physicalFirst = if (ordinal < 120) 0L else 9_600_000L
                StoredAudioSegment(
                    AudioStorageUnitIdentity(
                        source(10_160_000).identity,
                        id(1000 + ordinal),
                        ordinal,
                        first,
                        id(if (ordinal < 120) 10 else 11),
                        physicalFirst,
                        first - physicalFirst,
                    ),
                    80_000,
                    Sha256Value.ZERO,
                )
            }
        val rows =
            pair(id(10), id(10), 0, 9_600_000, "START", "CAP") +
                pair(id(11), id(10), 9_600_000, 10_160_000, "CAP", "STOP", 9_568_000).take(1) +
                SegmentationMetadata(
                    SegmentationKind.SEMANTIC_CLOSE,
                    id(20),
                    69_888,
                    4_038_912,
                    reason = "SILENCE_90_SECONDS",
                )
        val plan = RecoveryMetadata.plan(units, rows, 10_160_000)
        assertEquals(2, plan.additions.size)
        assertEquals(
            setOf(SegmentationKind.TECHNICAL_CLOSE, SegmentationKind.RECOVERY_INTERRUPTED),
            plan.additions.map { it.kind }.toSet(),
        )
        assertTrue(
            plan.additions.all {
                it.firstFrame == 9_600_000L && it.endFrame == 10_160_000L && it.reason == "RECOVERY"
            }
        )
        // Computing a plan neither performs the additions nor authorizes their publication.
        assertEquals(4, rows.size)
        assertTrue(
            RecoveryMetadata.plan(units, rows + plan.additions, 10_160_000).additions.isEmpty()
        )
    }
}
