package com.monumentogram.dora.bootstrap.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DoraDesignTokensTest {
    @Test
    fun mapsLightAndDarkSemanticThemeRoles() {
        val light = DoraDesignTokens.paletteFor(DoraThemeMode.LIGHT)
        val dark = DoraDesignTokens.paletteFor(DoraThemeMode.DARK)

        assertEquals(0xFFF5F8FC.toInt(), light.canvas.background)
        assertEquals(0xFF0C5193.toInt(), light.action.primary)
        assertEquals(0xFF020A15.toInt(), dark.canvas.background)
        assertEquals(0xFF66C6F0.toInt(), dark.action.primary)
        assertNotEquals(light.canvas.background, dark.canvas.background)
    }

    @Test
    fun preservesKeySemanticColorAndDimensionTokens() {
        val light = DoraDesignTokens.lightPalette
        val dark = DoraDesignTokens.darkPalette

        assertEquals(0xFF061A35.toInt(), light.canvas.surfaceDeep)
        assertEquals(light.canvas.surfaceDeep, dark.canvas.surfaceDeep)
        assertEquals(0xFF6E56CF.toInt(), light.feedback.review.foreground)
        assertEquals(0xFFC9B8FF.toInt(), dark.feedback.review.foreground)
        assertEquals(48.dp, DoraDimensions.touchMinimum)
        assertEquals(68.dp, DoraDimensions.recordControl)
        assertEquals(72.dp, DoraDimensions.dockVisibleHeight)
        assertEquals(720.dp, DoraDimensions.readingColumnMaximum)
        assertEquals(600.dp, DoraDimensions.mediumWindowMinimum)
    }
}
