package com.monumentogram.dora.bootstrap.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class BootstrapNavigationLayoutTest {
    @Test
    fun usesCompactDockBelowMediumWidth() {
        assertEquals(
            BootstrapNavigationLayout.COMPACT_DOCK,
            BootstrapNavigationLayout.forWidth(599.dp),
        )
    }

    @Test
    fun usesWideRailFromMediumWidth() {
        assertEquals(
            BootstrapNavigationLayout.WIDE_RAIL,
            BootstrapNavigationLayout.forWidth(600.dp),
        )
    }
}
