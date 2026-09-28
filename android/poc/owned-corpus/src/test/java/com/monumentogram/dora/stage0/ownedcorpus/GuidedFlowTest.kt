package com.monumentogram.dora.stage0.ownedcorpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuidedFlowTest {
    @Test
    fun allEightNeedHumanVerificationBeforeFinish() {
        val ids = (1..8).map { "synthetic-$it" }
        val verified = mutableSetOf<String>()
        var current = ids.first()
        ids.forEachIndexed { index, expected ->
            assertEquals(expected, current)
            assertEquals(GuidedFlow.Action.CONFIRM, GuidedFlow.action(false, true, false, true))
            verified.add(current)
            val next = GuidedFlow.next(ids, verified, current)
            if (index == ids.lastIndex) assertNull(next) else current = requireNotNull(next)
        }
    }

    @Test
    fun deferredAndRecordedTasksNeverOfferAnotherRecording() {
        assertEquals(GuidedFlow.Action.WAIT, GuidedFlow.action(false, false, false, false))
        assertEquals(GuidedFlow.Action.START, GuidedFlow.action(false, false, false, true))
        assertEquals(GuidedFlow.Action.STOP, GuidedFlow.action(true, false, false, true))
        assertEquals(GuidedFlow.Action.CONFIRM, GuidedFlow.action(false, true, false, false))
        assertEquals(GuidedFlow.Action.NEXT, GuidedFlow.action(false, true, true, true))
    }

    @Test
    fun correctionReturnsToEarlierUnverifiedTask() {
        assertEquals(
            "one",
            GuidedFlow.next(listOf("one", "two", "three"), setOf("two", "three"), "three"),
        )
    }
}
