package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.StoredAudioSegment
import com.monumentogram.dora.audio.logical.LogicalRecordingProjectionTest.Companion.id
import com.monumentogram.dora.audio.logical.LogicalRecordingProjectionTest.Companion.pair
import com.monumentogram.dora.audio.logical.LogicalRecordingProjectionTest.Companion.source
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryMetadataTest {
    private val open = pair(id(10), id(10), 0, 80000, "START", "STOP").first()

    private fun segment(frames: Long) =
        StoredAudioSegment(
            AudioStorageUnitIdentity(source(1).identity, id(20), 0, 0, id(10), 0, 0),
            frames,
            Sha256Value.ZERO,
        )

    private fun later(first: Long, number: Int) =
        StoredAudioSegment(
            AudioStorageUnitIdentity(
                source(1).identity,
                id(20 + number),
                number,
                first,
                id(10 + number),
                first,
                0,
            ),
            80000,
            Sha256Value.ZERO,
        )

    @Test
    fun missingLaterOpenRetainsOldMarkerAndFencesResume() {
        val prefix = listOf(segment(80000))
        val rows = listOf(open) + RecoveryMetadata.plan(prefix, listOf(open), 80000).additions
        val units = prefix + later(80000, 1)
        val plan = RecoveryMetadata.plan(units, rows, 160000)
        assertEquals(RecoveryMetadataState.INCOMPLETE, plan.technical)
        assertFalse(plan.continuationSafe)
        assertTrue(plan.additions.isEmpty())
        assertEquals(plan, RecoveryMetadata.plan(units, rows, 160000))
        val corrupted = rows.map {
            if (it.kind == SegmentationKind.RECOVERY_INTERRUPTED) it.copy(endFrame = 79999) else it
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryMetadata.plan(units, corrupted, 160000)
        }
    }

    @Test
    fun lostCloseBeforeUnknownLaterChunkIsIncompleteWithoutInventingClose() {
        val units = listOf(segment(80000), later(80000, 1))
        val plan = RecoveryMetadata.plan(units, listOf(open), 160000)
        assertEquals(RecoveryMetadataState.INCOMPLETE, plan.technical)
        assertFalse(plan.continuationSafe)
        assertTrue(plan.additions.none { it.kind == SegmentationKind.TECHNICAL_CLOSE })
        assertEquals(80000L, plan.additions.single().endFrame)
        assertTrue(
            RecoveryMetadata.plan(units, listOf(open) + plan.additions, 160000).additions.isEmpty()
        )
    }

    @Test
    fun legacyLeadingUnknownPrefixKeepsContinuationAcrossCycles() {
        val units = listOf(segment(80000), later(80000, 1))
        val rows = pair(id(11), id(11), 80000, 160000, "RESUME", "STOP").take(1)
        val plan = RecoveryMetadata.plan(units, rows, 160000)
        assertEquals(RecoveryMetadataState.INCOMPLETE, plan.technical)
        assertTrue(plan.continuationSafe)
        val replay = RecoveryMetadata.plan(units, rows + plan.additions, 160000)
        assertTrue(replay.continuationSafe)
        assertTrue(replay.additions.isEmpty())
    }

    @Test
    fun missingInternalOpenCannotGainContinuationFromLaterKnownOpen() {
        val units = listOf(segment(80000), later(80000, 1), later(160000, 2))
        val rows =
            pair(id(10), id(10), 0, 80000, "START", "PAUSE") +
                pair(id(12), id(12), 160000, 240000, "RESUME", "STOP").take(1)
        val plan = RecoveryMetadata.plan(units, rows, 240000)
        assertEquals(RecoveryMetadataState.INCOMPLETE, plan.technical)
        assertFalse(plan.continuationSafe)
    }

    @Test
    fun danglingOpenClosesOnlyAtAuthenticatedPrefixAndReplayAddsNothing() {
        val plan = RecoveryMetadata.plan(listOf(segment(80000)), listOf(open), 80000)
        assertEquals(RecoveryMetadataState.INTERRUPTED, plan.technical)
        assertEquals(
            80000L,
            plan.additions.single { it.kind == SegmentationKind.TECHNICAL_CLOSE }.endFrame,
        )
        assertEquals(
            "RECOVERY",
            plan.additions.single { it.kind == SegmentationKind.TECHNICAL_CLOSE }.reason,
        )
        assertTrue(plan.additions.none { it.kind == SegmentationKind.SEMANTIC_CLOSE })
        assertTrue(
            RecoveryMetadata.plan(listOf(segment(80000)), listOf(open) + plan.additions, 80000)
                .additions
                .isEmpty()
        )
    }

    @Test
    fun zeroOpenKeepsEvidenceWithAbortAndNoEmptyAudioClose() {
        val plan = RecoveryMetadata.plan(emptyList(), listOf(open), 0)
        assertEquals(1, plan.additions.count { it.kind == SegmentationKind.TECHNICAL_ABORT })
        assertTrue(plan.additions.none { it.kind == SegmentationKind.TECHNICAL_CLOSE })
        assertTrue(
            RecoveryMetadata.plan(emptyList(), listOf(open) + plan.additions, 0).additions.isEmpty()
        )
    }

    @Test
    fun missingHistoricalOpenDoesNotInventEpoch() {
        val plan = RecoveryMetadata.plan(listOf(segment(80000)), emptyList(), 80000)
        assertEquals(RecoveryMetadataState.NOT_EVALUATED, plan.technical)
        assertTrue(plan.additions.isEmpty())
    }

    @Test
    fun catalogFramesCannotEnlargeAuthenticatedPrefix() {
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryMetadata.plan(listOf(segment(80000)), listOf(open), 79999)
        }
    }

    @Test
    fun existingStopNeverRewrittenAsRecovery() {
        val rows = pair(id(10), id(10), 0, 80000, "START", "STOP")
        val plan = RecoveryMetadata.plan(listOf(segment(80000)), rows, 80000)
        assertTrue(plan.additions.none { it.kind == SegmentationKind.TECHNICAL_CLOSE })
    }

    @Test
    fun prefixThenEmptyOpenDoesNotExpandMarkersOnSecondScan() {
        val rows =
            pair(id(10), id(10), 0, 80000, "START", "PAUSE") +
                pair(id(11), id(11), 80000, 80001, "RESUME", "STOP").first()
        val plan = RecoveryMetadata.plan(listOf(segment(80000)), rows, 80000)
        assertEquals(2, plan.additions.size)
        assertTrue(
            RecoveryMetadata.plan(listOf(segment(80000)), rows + plan.additions, 80000)
                .additions
                .isEmpty()
        )
    }
}
