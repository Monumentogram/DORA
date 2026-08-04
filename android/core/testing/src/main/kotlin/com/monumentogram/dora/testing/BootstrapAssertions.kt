package com.monumentogram.dora.testing

import com.monumentogram.dora.common.BuildMetadata
import org.junit.Assert.assertEquals

fun assertStage00() {
    assertEquals("Stage 00", BuildMetadata.STAGE)
}
