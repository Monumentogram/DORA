package com.monumentogram.dora.common

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildMetadataTest {
    @Test
    fun exposesStageIdentifier() {
        assertEquals("Stage 00", BuildMetadata.STAGE)
    }
}
