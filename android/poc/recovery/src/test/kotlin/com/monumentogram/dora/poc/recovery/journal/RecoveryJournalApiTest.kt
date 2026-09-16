package com.monumentogram.dora.poc.recovery.journal

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryJournalApiTest {
    @Test
    fun `api 28 through 32 reject journal admission explicitly`() {
        (28..32).forEach { api ->
            val failure =
                assertThrows(IllegalStateException::class.java) {
                    requireRecoveryJournalApi(api)
                }
            assertTrue(failure.message.orEmpty().contains("API 33"))
        }
    }

    @Test
    fun `api 33 and current runtime pass platform admission`() {
        listOf(33, 36).forEach(::requireRecoveryJournalApi)
    }
}
