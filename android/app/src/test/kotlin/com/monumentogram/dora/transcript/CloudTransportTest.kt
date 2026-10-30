package com.monumentogram.dora.transcript

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CloudTransportTest {
    private val parent = "00000000-0000-4000-8000-000000000001"
    private val child = "00000000-0000-4000-8000-000000000002"

    @Test
    fun completedChildCanBeRecoveredThroughItsCompositePartRouteAboveLegacyResponseLimit() {
        val payload = ByteArray(2 * 1024 * 1024 + 1) { 1 }
        val recovered =
            readCloudResponse(
                ByteArrayInputStream(payload),
                "/v1/runs/$parent/parts/17",
                emptySet(),
            )
        assertEquals(payload.size, recovered.size)
        assertEquals(
            payload.size,
            readCloudResponse(ByteArrayInputStream(payload), "/v1/jobs/$child", setOf(child)).size,
        )
    }

    @Test
    fun legacyUnrelatedOrMalformedRoutesKeepTheirOldBound() {
        val payload = ByteArray(2 * 1024 * 1024 + 1)
        listOf("/v1/jobs/$child", "/v1/runs/$parent/parts/48", "/v1/runs/$parent/parts/017")
            .forEach { path ->
                assertThrows(IllegalArgumentException::class.java) {
                    readCloudResponse(ByteArrayInputStream(payload), path, emptySet())
                }
            }
    }

    @Test
    fun compositeEnvelopeAboveEightMiBFailsWithoutReturningTruncatedContent() {
        assertThrows(IllegalArgumentException::class.java) {
            readCloudResponse(
                ByteArrayInputStream(ByteArray(8 * 1024 * 1024 + 1)),
                "/v1/runs/$parent/parts/0",
                emptySet(),
            )
        }
    }
}
