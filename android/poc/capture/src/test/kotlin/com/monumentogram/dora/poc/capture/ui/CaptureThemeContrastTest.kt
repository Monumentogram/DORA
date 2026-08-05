package com.monumentogram.dora.poc.capture.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureThemeContrastTest {
    @Test
    fun `dark semantic text pairs meet normal text contrast`() {
        val colors = CaptureSemanticColors.dark

        assertContrast(colors.onBackground, colors.background)
        assertContrast(colors.onSurface, colors.surface)
        assertContrast(colors.onSurfaceVariant, colors.surfaceVariant)
        assertContrast(colors.onPrimaryContainer, colors.primaryContainer)
        assertContrast(colors.onErrorContainer, colors.errorContainer)
    }

    private fun assertContrast(foreground: Color, background: Color) {
        val foregroundLuminance = foreground.luminance().toDouble()
        val backgroundLuminance = background.luminance().toDouble()
        val ratio =
            (maxOf(foregroundLuminance, backgroundLuminance) + 0.05) /
                (minOf(foregroundLuminance, backgroundLuminance) + 0.05)
        assertTrue("Expected contrast >= 4.5, found $ratio", ratio >= 4.5)
    }
}
