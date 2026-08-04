package com.monumentogram.dora.bootstrap.ui

import androidx.compose.ui.unit.Dp
import com.monumentogram.dora.bootstrap.ui.theme.DoraDimensions

internal enum class BootstrapNavigationLayout {
    COMPACT_DOCK,
    WIDE_RAIL;

    companion object {
        fun forWidth(width: Dp): BootstrapNavigationLayout =
            if (width >= DoraDimensions.mediumWindowMinimum) WIDE_RAIL else COMPACT_DOCK
    }
}
