package com.monumentogram.dora.transcript

import com.monumentogram.dora.audio.transcript.CloudConsent
import com.monumentogram.dora.audio.transcript.TranscriptJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudActionsTest {
    private fun job(index: Int, status: String, revoked: Boolean = false, cloud: Boolean = true) =
        TranscriptJob(
            "00000000-0000-4000-8000-${index.toString().padStart(12, '0')}",
            status,
            "en",
            index.toLong(),
            1,
            if (cloud) CloudConsent(grantedAt = 1, revoked = revoked) else null,
        )

    @Test
    fun unfinishedCloudActionRequiresRetryOrCancelInsteadOfAnotherNewAction() {
        listOf("QUEUED", "RUNNING", "FAILED", "INTERRUPTED").forEach { status ->
            val prior = job(1, status)
            val result = cloudActions(listOf(prior))
            assertTrue(result.blocksNew)
            assertEquals(prior, result.pending)
        }
    }

    @Test
    fun newerCancellationDoesNotHideOlderResumableAction() {
        val prior = job(1, "INTERRUPTED")
        val cancelled = job(2, "CANCELLED", revoked = true)
        val result = cloudActions(listOf(prior, cancelled))
        assertTrue(result.blocksNew)
        assertEquals(prior, result.pending)
    }

    @Test
    fun completedCancelledOrRevokedCloudAndUnrelatedLocalJobsAllowNewAction() {
        listOf(
                job(1, "SUCCEEDED"),
                job(2, "CANCELLED"),
                job(3, "FAILED", revoked = true),
                job(4, "INTERRUPTED", cloud = false),
            )
            .forEach { prior ->
                assertFalse(cloudActions(listOf(prior)).blocksNew)
            }
        val cancelled = job(5, "CANCELLED", revoked = true)
        assertEquals(cancelled, cloudActions(listOf(cancelled)).pending)
        assertFalse(cloudActions(emptyList()).blocksNew)
    }
}
