package com.monumentogram.dora.audio.persistence.journal

import org.junit.Assert.assertThrows
import org.junit.Test

class JournalWalCheckpointTest {
    @Test
    fun onlyCompleteSingleRowCheckpointResultsAreAccepted() {
        JournalWalCheckpoint.requireComplete(3, 1, longArrayOf(0, 7, 7), false)
        JournalWalCheckpoint.requireComplete(3, 1, longArrayOf(0, 0, 0), true)
        for ((columns, rows) in listOf(2 to 1, 4 to 1, 3 to 0, 3 to 2)) {
            assertThrows(IllegalStateException::class.java) {
                JournalWalCheckpoint.requireComplete(columns, rows, longArrayOf(0, 0, 0), false)
            }
        }
        for (values in
            listOf(
                null,
                longArrayOf(0, 0),
                longArrayOf(1, 7, 7),
                longArrayOf(0, 7, 6),
                longArrayOf(0, -1, -1),
            )) {
            assertThrows(IllegalStateException::class.java) {
                JournalWalCheckpoint.requireComplete(3, 1, values, false)
            }
        }
        assertThrows(IllegalStateException::class.java) {
            JournalWalCheckpoint.requireComplete(3, 1, longArrayOf(0, 7, 7), true)
        }
    }
}
