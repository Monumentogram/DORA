package com.monumentogram.dora.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingStorageBudgetTest {
    @Test
    fun finalizationHeadroomCannotBeSpentOnTheHourAllowance() {
        val budget = RecordingStorageBudget.assess(200_000_000)
        assertFalse(budget.canStart)
        assertEquals(216_777_216L, budget.requiredBytes)
        assertEquals(183_222_784L, budget.recordingBudgetBytes)
    }

    @Test
    fun exactMinimumAdmitsOneHourWithoutConsumingFinalizationReserve() {
        val budget = RecordingStorageBudget.assess(216_777_216)
        assertTrue(budget.canStart)
        assertEquals(200_000_000L, budget.recordingBudgetBytes)
    }

    @Test
    fun oneByteShortDoesNotRoundUpIntoAdmission() {
        assertFalse(RecordingStorageBudget.assess(216_777_215).canStart)
    }

    @Test
    fun unknownOrInvalidStorageNeverAdmitsCapture() {
        for (bytes in listOf(null, -1L, Long.MIN_VALUE)) {
            val budget = RecordingStorageBudget.assess(bytes)
            assertFalse(budget.canStart)
            assertNull(budget.availableBytes)
            assertEquals(0L, budget.recordingBudgetBytes)
        }
    }

    @Test
    fun floorAndEmptyStorageHaveNoCaptureBudget() {
        for (bytes in listOf(0L, 16_777_215L, 16_777_216L)) {
            val budget = RecordingStorageBudget.assess(bytes)
            assertFalse(budget.canStart)
            assertEquals(0L, budget.recordingBudgetBytes)
        }
    }

    @Test
    fun laterCapacityLossCannotReuseAnEarlierEligibleSnapshot() {
        assertTrue(RecordingStorageBudget.assess(300_000_000).canStart)
        assertFalse(RecordingStorageBudget.assess(100_000_000).canStart)
    }

    @Test
    fun dayEstimateUsesLongAndWarnsWithoutPreventingShorterRecording() {
        val partial = RecordingStorageBudget.assess(2_900_000_000L)
        assertTrue(partial.canStart)
        assertFalse(partial.canFitFullDay)
        assertEquals(4_816_777_216L, partial.fullDayRequiredBytes)
        assertEquals(51_898L, partial.estimatedSeconds)
        val full = RecordingStorageBudget.assess(4_816_777_216L)
        assertTrue(full.canFitFullDay)
        assertEquals(86_400L, full.estimatedSeconds)
        assertEquals(86_400L, RecordingStorageBudget.assess(Long.MAX_VALUE).estimatedSeconds)
    }

    @Test
    fun veryLargeCapacityDoesNotOverflowTheBudget() {
        val budget = RecordingStorageBudget.assess(Long.MAX_VALUE)
        assertTrue(budget.canStart)
        assertEquals(9_223_372_036_837_998_591L, budget.recordingBudgetBytes)
    }
}
