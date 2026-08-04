package com.monumentogram.dora.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BootstrapStateTest {
    @Test
    fun startsReadyForImplementation() {
        assertEquals(BootstrapState.READY, BootstrapState.current())
    }
}
