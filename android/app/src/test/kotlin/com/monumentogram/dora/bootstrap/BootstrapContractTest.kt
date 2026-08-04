package com.monumentogram.dora.bootstrap

import com.monumentogram.dora.common.BuildMetadata
import com.monumentogram.dora.model.BootstrapState
import org.junit.Assert.assertEquals
import org.junit.Test

class BootstrapContractTest {
    @Test
    fun stageMetadataMatchesBootstrapState() {
        assertEquals("Stage 00", BuildMetadata.STAGE)
        assertEquals(BootstrapState.READY, BootstrapState.current())
    }
}
