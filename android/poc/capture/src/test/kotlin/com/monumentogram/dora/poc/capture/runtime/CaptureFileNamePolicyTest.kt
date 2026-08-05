package com.monumentogram.dora.poc.capture.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureFileNamePolicyTest {
    @Test
    fun `generated run filename accepts uppercase UTC delimiters`() {
        assertTrue(
            CaptureFileNamePolicy.accepts("capture-run-a-run-a-20260805T152700Z-1234abcd.wav.part")
        )
    }

    @Test
    fun `finalized generated run filename remains accepted`() {
        assertTrue(
            CaptureFileNamePolicy.accepts("capture-run-a-run-a-20260805T152700Z-1234abcd.wav")
        )
    }

    @Test
    fun `path traversal and unsupported suffixes remain rejected`() {
        assertFalse(CaptureFileNamePolicy.accepts("../capture-run-a.wav.part"))
        assertFalse(CaptureFileNamePolicy.accepts("capture/run-a.wav.part"))
        assertFalse(CaptureFileNamePolicy.accepts("capture-run-a.wav.part.tmp"))
    }
}
