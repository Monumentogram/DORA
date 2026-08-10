@file:Suppress("MagicNumber") // Central semantic token mapping; components use roles only.

package com.monumentogram.dora.poc.capture.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal object CaptureSemanticColors {
    val light =
        lightColorScheme(
            primary = Color(0xFF0C5193),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD8F1FC),
            onPrimaryContainer = Color(0xFF031126),
            background = Color(0xFFF5F8FC),
            onBackground = Color(0xFF0B1525),
            surface = Color.White,
            onSurface = Color(0xFF0B1525),
            surfaceVariant = Color(0xFFE9EFF6),
            onSurfaceVariant = Color(0xFF536176),
            tertiary = Color(0xFF176B3A),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFD5F5DF),
            onTertiaryContainer = Color(0xFF092F1A),
            outline = Color(0xFFC7D1DE),
            error = Color(0xFFB3261E),
            errorContainer = Color(0xFFF9DEDC),
        )
    val dark =
        darkColorScheme(
            primary = Color(0xFF66C6F0),
            onPrimary = Color(0xFF031126),
            primaryContainer = Color(0xFF0B3B6F),
            onPrimaryContainer = Color(0xFFD8F1FC),
            background = Color(0xFF020A15),
            onBackground = Color(0xFFF2F6FB),
            surface = Color(0xFF071426),
            onSurface = Color(0xFFF2F6FB),
            surfaceVariant = Color(0xFF0E2037),
            onSurfaceVariant = Color(0xFFA8B6C9),
            tertiary = Color(0xFF7DDA9D),
            onTertiary = Color(0xFF06391D),
            tertiaryContainer = Color(0xFF18522F),
            onTertiaryContainer = Color(0xFFD5F5DF),
            outline = Color(0xFF2F425C),
            error = Color(0xFFFFB4AB),
            errorContainer = Color(0xFF601410),
        )
}

internal object CaptureDimensions {
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space6 = 24.dp
    val cardRadius = 16.dp
    val readingWidth = 720.dp
}

@Composable
internal fun CaptureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) CaptureSemanticColors.dark else CaptureSemanticColors.light,
        shapes =
            Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(CaptureDimensions.cardRadius),
                large = RoundedCornerShape(24.dp),
            ),
        content = content,
    )
}
