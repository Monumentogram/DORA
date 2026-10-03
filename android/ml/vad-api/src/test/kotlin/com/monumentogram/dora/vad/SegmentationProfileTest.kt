package com.monumentogram.dora.vad

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentationProfileTest {
    @Test
    fun implementationExactlyMatchesProspectivelyFrozenCalibrationProfile() {
        val frozen =
            checkNotNull(javaClass.getResourceAsStream("/profile.json"))
                .bufferedReader()
                .use { it.readText() }
                .trimEnd()
        val profile = SegmentationProfile.FROZEN
        assertEquals(frozen, profile.canonicalJson())
        // The prospectively frozen document includes one terminal LF in its byte identity.
        val digest =
            MessageDigest.getInstance("SHA-256").digest((frozen + "\n").toByteArray(Charsets.UTF_8))
        assertEquals(profile.sha256, digest.joinToString("") { "%02x".format(it) })
    }
}
