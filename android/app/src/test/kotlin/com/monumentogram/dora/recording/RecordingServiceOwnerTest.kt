package com.monumentogram.dora.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RecordingServiceOwnerTest {
    @Test
    fun queuedStartCannotResurrectDestroyedService() {
        val owner = RecordingServiceOwner()
        val created = owner.created()
        var starts = 0
        val queued = { owner.runCurrent(created) { starts++ } }
        owner.destroyed()
        assertFalse(queued())
        assertEquals(0, starts)
    }

    @Test
    fun replacementServiceCannotConsumeOldStartOrResume() {
        val owner = RecordingServiceOwner()
        val old = owner.created()
        owner.destroyed()
        val next = owner.created()
        assertFalse(owner.runCurrent(old) { fail("Stale native start") })
        assertTrue(owner.runCurrent(next) {})
    }
}
