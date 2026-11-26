package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class VeilAccentTokens(
    val primary: Color = VeilAccentPresets.EmeraldGreen,
    val highlight: Color = Color(0xFF6EE7B7),
    val container: Color = Color(0x2610B981),
    val outgoingBubble: Color = Color(0x3010B981),
    val outgoingBorder: Color = Color(0x556EE7B7),
    val ambientGlow: Color = Color(0xFF0C1F1B)
)

val LocalVeilAccent = staticCompositionLocalOf { VeilAccentTokens() }

@Composable
fun VeilTheme(
    accentColor: Color = VeilAccentPresets.DefaultAccent,
    content: @Composable () -> Unit
) {
    val highlight = VeilAccentPresets.lightenForGlass(accentColor)
    val tokens = VeilAccentTokens(
        primary = accentColor,
        highlight = highlight,
        container = accentColor.copy(alpha = 0.16f),
        outgoingBubble = accentColor.copy(alpha = 0.20f),
        outgoingBorder = highlight.copy(alpha = 0.36f),
        ambientGlow = Color(
            red = (accentColor.red * 0.18f).coerceIn(0f, 1f),
            green = (accentColor.green * 0.18f).coerceIn(0f, 1f),
            blue = (accentColor.blue * 0.18f).coerceIn(0f, 1f),
            alpha = 1f
        )
    )

    val colorScheme = darkColorScheme(
        primary = highlight,
        onPrimary = ObsidianDeep,
        primaryContainer = tokens.container,
        onPrimaryContainer = highlight,
        secondary = IceCyan,
        onSecondary = ObsidianDeep,
        secondaryContainer = GlassSurfaceElevated,
        onSecondaryContainer = SnowPrimary,
        tertiary = SlateSecondary,
        onTertiary = ObsidianDeep,
        background = ObsidianBg,
        onBackground = SnowPrimary,
        surface = GlassSurface,
        onSurface = SnowPrimary,
        surfaceVariant = GlassSurfaceElevated,
        onSurfaceVariant = SlateSecondary,
        outline = GlassBorderTop,
        outlineVariant = GlassDivider,
        error = CoralError,
        onError = ObsidianDeep,
        errorContainer = CoralContainer,
        onErrorContainer = CoralError
    )

    CompositionLocalProvider(LocalVeilAccent provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VeilTypography,
            content = content
        )
    }
}
