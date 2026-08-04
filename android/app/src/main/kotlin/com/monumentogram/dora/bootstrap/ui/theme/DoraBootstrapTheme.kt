package com.monumentogram.dora.bootstrap.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sole Android mapping for the semantic handoff in `docs/design/DORA_MVP1_DESIGN_TOKENS.json`
 * version `1.0.0-proposal`.
 *
 * Stage 00 keeps this mapping manual and reviewable. UI components consume semantic roles from
 * [MaterialTheme] or the dimension objects below; raw brand colors must not be redeclared in
 * components. Automated JSON generation may replace this file later through a scoped ADR/PR.
 */
internal object DoraDesignTokens {
    val lightPalette: DoraPalette =
        DoraPalette(
            canvas =
                DoraCanvasColors(
                    background = argb(LightColorValues.BACKGROUND),
                    surface = argb(LightColorValues.SURFACE),
                    surfaceAlt = argb(LightColorValues.SURFACE_ALT),
                    surfaceElevated = argb(LightColorValues.SURFACE_ELEVATED),
                    surfaceDeep = argb(LightColorValues.SURFACE_DEEP),
                ),
            text =
                DoraTextColors(
                    primary = argb(LightColorValues.TEXT_PRIMARY),
                    secondary = argb(LightColorValues.TEXT_SECONDARY),
                    disabled = argb(LightColorValues.TEXT_DISABLED),
                    onDeep = argb(LightColorValues.TEXT_ON_DEEP),
                ),
            boundary =
                DoraBoundaryColors(
                    outline = argb(LightColorValues.OUTLINE),
                    outlineStrong = argb(LightColorValues.OUTLINE_STRONG),
                    scrim = argb(LightColorValues.SCRIM),
                ),
            action =
                DoraActionColors(
                    primary = argb(LightColorValues.PRIMARY),
                    onPrimary = argb(LightColorValues.ON_PRIMARY),
                    primaryContainer = argb(LightColorValues.PRIMARY_CONTAINER),
                    onPrimaryContainer = argb(LightColorValues.ON_PRIMARY_CONTAINER),
                ),
            feedback =
                DoraFeedbackColors(
                    success =
                        DoraStatusColors(
                            foreground = argb(LightColorValues.SUCCESS),
                            container = argb(LightColorValues.SUCCESS_CONTAINER),
                        ),
                    warning =
                        DoraStatusColors(
                            foreground = argb(LightColorValues.WARNING),
                            container = argb(LightColorValues.WARNING_CONTAINER),
                        ),
                    error =
                        DoraStatusColors(
                            foreground = argb(LightColorValues.ERROR),
                            container = argb(LightColorValues.ERROR_CONTAINER),
                        ),
                    review =
                        DoraStatusColors(
                            foreground = argb(LightColorValues.REVIEW),
                            container = argb(LightColorValues.REVIEW_CONTAINER),
                        ),
                ),
            wave =
                DoraWaveColors(
                    active = argb(LightColorValues.WAVE_ACTIVE),
                    quiet = argb(LightColorValues.WAVE_QUIET),
                    paused = argb(LightColorValues.WAVE_PAUSED),
                    recordIndicator = argb(LightColorValues.RECORD_INDICATOR),
                ),
        )

    val darkPalette: DoraPalette =
        DoraPalette(
            canvas =
                DoraCanvasColors(
                    background = argb(DarkColorValues.BACKGROUND),
                    surface = argb(DarkColorValues.SURFACE),
                    surfaceAlt = argb(DarkColorValues.SURFACE_ALT),
                    surfaceElevated = argb(DarkColorValues.SURFACE_ELEVATED),
                    surfaceDeep = argb(DarkColorValues.SURFACE_DEEP),
                ),
            text =
                DoraTextColors(
                    primary = argb(DarkColorValues.TEXT_PRIMARY),
                    secondary = argb(DarkColorValues.TEXT_SECONDARY),
                    disabled = argb(DarkColorValues.TEXT_DISABLED),
                    onDeep = argb(DarkColorValues.TEXT_ON_DEEP),
                ),
            boundary =
                DoraBoundaryColors(
                    outline = argb(DarkColorValues.OUTLINE),
                    outlineStrong = argb(DarkColorValues.OUTLINE_STRONG),
                    scrim = argb(DarkColorValues.SCRIM),
                ),
            action =
                DoraActionColors(
                    primary = argb(DarkColorValues.PRIMARY),
                    onPrimary = argb(DarkColorValues.ON_PRIMARY),
                    primaryContainer = argb(DarkColorValues.PRIMARY_CONTAINER),
                    onPrimaryContainer = argb(DarkColorValues.ON_PRIMARY_CONTAINER),
                ),
            feedback =
                DoraFeedbackColors(
                    success =
                        DoraStatusColors(
                            foreground = argb(DarkColorValues.SUCCESS),
                            container = argb(DarkColorValues.SUCCESS_CONTAINER),
                        ),
                    warning =
                        DoraStatusColors(
                            foreground = argb(DarkColorValues.WARNING),
                            container = argb(DarkColorValues.WARNING_CONTAINER),
                        ),
                    error =
                        DoraStatusColors(
                            foreground = argb(DarkColorValues.ERROR),
                            container = argb(DarkColorValues.ERROR_CONTAINER),
                        ),
                    review =
                        DoraStatusColors(
                            foreground = argb(DarkColorValues.REVIEW),
                            container = argb(DarkColorValues.REVIEW_CONTAINER),
                        ),
                ),
            wave =
                DoraWaveColors(
                    active = argb(DarkColorValues.WAVE_ACTIVE),
                    quiet = argb(DarkColorValues.WAVE_QUIET),
                    paused = argb(DarkColorValues.WAVE_PAUSED),
                    recordIndicator = argb(DarkColorValues.RECORD_INDICATOR),
                ),
        )

    fun paletteFor(mode: DoraThemeMode): DoraPalette =
        when (mode) {
            DoraThemeMode.LIGHT -> lightPalette
            DoraThemeMode.DARK -> darkPalette
        }
}

internal enum class DoraThemeMode {
    LIGHT,
    DARK,
}

@Immutable
internal data class DoraPalette(
    val canvas: DoraCanvasColors,
    val text: DoraTextColors,
    val boundary: DoraBoundaryColors,
    val action: DoraActionColors,
    val feedback: DoraFeedbackColors,
    val wave: DoraWaveColors,
)

@Immutable
internal data class DoraCanvasColors(
    val background: Int,
    val surface: Int,
    val surfaceAlt: Int,
    val surfaceElevated: Int,
    val surfaceDeep: Int,
)

@Immutable
internal data class DoraTextColors(
    val primary: Int,
    val secondary: Int,
    val disabled: Int,
    val onDeep: Int,
)

@Immutable
internal data class DoraBoundaryColors(
    val outline: Int,
    val outlineStrong: Int,
    val scrim: Int,
)

@Immutable
internal data class DoraActionColors(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
)

@Immutable internal data class DoraStatusColors(val foreground: Int, val container: Int)

@Immutable
internal data class DoraFeedbackColors(
    val success: DoraStatusColors,
    val warning: DoraStatusColors,
    val error: DoraStatusColors,
    val review: DoraStatusColors,
)

@Immutable
internal data class DoraWaveColors(
    val active: Int,
    val quiet: Int,
    val paused: Int,
    val recordIndicator: Int,
)

internal object DoraDimensions {
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 20.dp
    val space6 = 24.dp
    val space8 = 32.dp
    val touchMinimum = 48.dp
    val buttonPrimaryHeight = 52.dp
    val recordControl = 68.dp
    val listRowMinimum = 64.dp
    val dockVisibleHeight = 72.dp
    val readingColumnMaximum = 720.dp
    val compactMargin = 16.dp
    val mediumMargin = 24.dp
    val expandedMargin = 32.dp
    val mediumWindowMinimum = 600.dp
    val radiusFull = 999.dp
}

internal val DoraShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

internal val DoraTypography =
    Typography(
        displayLarge = tokenTextStyle(sizeSp = 40, lineHeightSp = 44, weight = FontWeight.SemiBold),
        headlineLarge =
            tokenTextStyle(sizeSp = 32, lineHeightSp = 38, weight = FontWeight.SemiBold),
        headlineMedium =
            tokenTextStyle(sizeSp = 28, lineHeightSp = 34, weight = FontWeight.SemiBold),
        headlineSmall =
            tokenTextStyle(sizeSp = 24, lineHeightSp = 30, weight = FontWeight.SemiBold),
        titleLarge = tokenTextStyle(sizeSp = 20, lineHeightSp = 26, weight = FontWeight.SemiBold),
        titleMedium = tokenTextStyle(sizeSp = 18, lineHeightSp = 24, weight = FontWeight.SemiBold),
        bodyLarge = tokenTextStyle(sizeSp = 16, lineHeightSp = 24, weight = FontWeight.Normal),
        bodyMedium = tokenTextStyle(sizeSp = 14, lineHeightSp = 20, weight = FontWeight.Normal),
        labelLarge = tokenTextStyle(sizeSp = 14, lineHeightSp = 20, weight = FontWeight.SemiBold),
        labelMedium = tokenTextStyle(sizeSp = 12, lineHeightSp = 16, weight = FontWeight.SemiBold),
        bodySmall = tokenTextStyle(sizeSp = 11, lineHeightSp = 16, weight = FontWeight.Medium),
    )

@Composable
internal fun DoraBootstrapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val mode = if (darkTheme) DoraThemeMode.DARK else DoraThemeMode.LIGHT
    val palette = DoraDesignTokens.paletteFor(mode)
    MaterialTheme(
        colorScheme = palette.toMaterialColorScheme(mode),
        typography = DoraTypography,
        shapes = DoraShapes,
        content = content,
    )
}

private fun tokenTextStyle(sizeSp: Int, lineHeightSp: Int, weight: FontWeight): TextStyle =
    TextStyle(
        fontSize = sizeSp.sp,
        lineHeight = lineHeightSp.sp,
        fontWeight = weight,
    )

private fun DoraPalette.toMaterialColorScheme(mode: DoraThemeMode) =
    when (mode) {
        DoraThemeMode.LIGHT ->
            lightColorScheme(
                primary = Color(action.primary),
                onPrimary = Color(action.onPrimary),
                primaryContainer = Color(action.primaryContainer),
                onPrimaryContainer = Color(action.onPrimaryContainer),
                background = Color(canvas.background),
                onBackground = Color(text.primary),
                surface = Color(canvas.surface),
                onSurface = Color(text.primary),
                surfaceVariant = Color(canvas.surfaceAlt),
                onSurfaceVariant = Color(text.secondary),
                outline = Color(boundary.outline),
                error = Color(feedback.error.foreground),
                onError = Color(text.onDeep),
                errorContainer = Color(feedback.error.container),
                onErrorContainer = Color(text.primary),
                scrim = Color(boundary.scrim),
            )
        DoraThemeMode.DARK ->
            darkColorScheme(
                primary = Color(action.primary),
                onPrimary = Color(action.onPrimary),
                primaryContainer = Color(action.primaryContainer),
                onPrimaryContainer = Color(action.onPrimaryContainer),
                background = Color(canvas.background),
                onBackground = Color(text.primary),
                surface = Color(canvas.surface),
                onSurface = Color(text.primary),
                surfaceVariant = Color(canvas.surfaceAlt),
                onSurfaceVariant = Color(text.secondary),
                outline = Color(boundary.outline),
                error = Color(feedback.error.foreground),
                onError = Color(canvas.background),
                errorContainer = Color(feedback.error.container),
                onErrorContainer = Color(text.primary),
                scrim = Color(boundary.scrim),
            )
    }

private fun argb(value: Long): Int = value.toInt()

private object LightColorValues {
    const val BACKGROUND = 0xFFF5F8FCL
    const val SURFACE = 0xFFFFFFFFL
    const val SURFACE_ALT = 0xFFE9EFF6L
    const val SURFACE_ELEVATED = 0xFFFFFFFFL
    const val SURFACE_DEEP = 0xFF061A35L
    const val TEXT_PRIMARY = 0xFF0B1525L
    const val TEXT_SECONDARY = 0xFF536176L
    const val TEXT_DISABLED = 0xFF8895A7L
    const val TEXT_ON_DEEP = 0xFFF7FBFFL
    const val OUTLINE = 0xFFC7D1DEL
    const val OUTLINE_STRONG = 0xFF8796A9L
    const val PRIMARY = 0xFF0C5193L
    const val ON_PRIMARY = 0xFFFFFFFFL
    const val PRIMARY_CONTAINER = 0xFFD8F1FCL
    const val ON_PRIMARY_CONTAINER = 0xFF031126L
    const val SUCCESS = 0xFF2E7D5BL
    const val SUCCESS_CONTAINER = 0xFFDDF3E8L
    const val WARNING = 0xFFA55B00L
    const val WARNING_CONTAINER = 0xFFFFF0D8L
    const val ERROR = 0xFFB3261EL
    const val ERROR_CONTAINER = 0xFFF9DEDCL
    const val REVIEW = 0xFF6E56CFL
    const val REVIEW_CONTAINER = 0xFFEEE8FFL
    const val WAVE_ACTIVE = 0xFF66C6F0L
    const val WAVE_QUIET = 0xFF2C709DL
    const val WAVE_PAUSED = 0xFF7F91A9L
    const val RECORD_INDICATOR = 0xFFE45C54L
    const val SCRIM = 0xB8031126L
}

private object DarkColorValues {
    const val BACKGROUND = 0xFF020A15L
    const val SURFACE = 0xFF071426L
    const val SURFACE_ALT = 0xFF0E2037L
    const val SURFACE_ELEVATED = 0xFF122A47L
    const val SURFACE_DEEP = 0xFF061A35L
    const val TEXT_PRIMARY = 0xFFF2F6FBL
    const val TEXT_SECONDARY = 0xFFA8B6C9L
    const val TEXT_DISABLED = 0xFF6F8198L
    const val TEXT_ON_DEEP = 0xFFF7FBFFL
    const val OUTLINE = 0xFF2F425CL
    const val OUTLINE_STRONG = 0xFF7890ADL
    const val PRIMARY = 0xFF66C6F0L
    const val ON_PRIMARY = 0xFF031126L
    const val PRIMARY_CONTAINER = 0xFF0B3B6FL
    const val ON_PRIMARY_CONTAINER = 0xFFD8F1FCL
    const val SUCCESS = 0xFF7BE0ADL
    const val SUCCESS_CONTAINER = 0xFF123B2BL
    const val WARNING = 0xFFFFC56BL
    const val WARNING_CONTAINER = 0xFF4A2C00L
    const val ERROR = 0xFFFFB4ABL
    const val ERROR_CONTAINER = 0xFF601410L
    const val REVIEW = 0xFFC9B8FFL
    const val REVIEW_CONTAINER = 0xFF30265DL
    const val WAVE_ACTIVE = 0xFF66C6F0L
    const val WAVE_QUIET = 0xFF2C709DL
    const val WAVE_PAUSED = 0xFF7F91A9L
    const val RECORD_INDICATOR = 0xFFFF8A82L
    const val SCRIM = 0xB8000000L
}
