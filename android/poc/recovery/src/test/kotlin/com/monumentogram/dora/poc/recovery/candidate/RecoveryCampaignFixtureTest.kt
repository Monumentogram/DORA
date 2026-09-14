package com.monumentogram.dora.poc.recovery.candidate

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryCampaignFixtureTest {
    @Test
    fun `fixture slices preserve absolute byte positions across period boundary`() {
        val seed = 123456789
        val bytes = RecoveryCampaignFixture.bytes(seed, 65530, 30)
        assertArrayEquals(
            ByteArray(30) { n ->
                val i = n + 65530
                ((seed.toLong() + i * 31L + (i shr 8) * 17L) and 255).toByte()
            },
            bytes,
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            RecoveryCampaignFixture.digest(0, 0).toLowercaseHex(),
        )
    }

    @Test
    fun `fixture rejects overflow and negative extent before allocation`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryCampaignFixture.bytes(-1, 0, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryCampaignFixture.bytes(0, Int.MAX_VALUE, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryCampaignFixture.bytes(0, 115200000, 1)
        }
    }
}
