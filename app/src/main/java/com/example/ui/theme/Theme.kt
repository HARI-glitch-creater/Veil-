package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VeilDarkColorScheme = darkColorScheme(
    primary = GlacierMint,
    onPrimary = ObsidianDeep,
    primaryContainer = GlacierMintContainer,
    onPrimaryContainer = GlacierMint,
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

@Composable
fun VeilTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VeilDarkColorScheme,
        typography = VeilTypography,
        content = content
    )
}
