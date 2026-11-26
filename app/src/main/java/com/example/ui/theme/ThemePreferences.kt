package com.example.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.veilThemeDataStore by preferencesDataStore(name = "veil_theme_prefs")

data class AccentPreset(
    val name: String,
    val color: Color
)

object VeilAccentPresets {
    val EmeraldGreen = Color(0xFF10B981)
    val DefaultAccent = EmeraldGreen

    val all: List<AccentPreset> = listOf(
        AccentPreset("Emerald Green", Color(0xFF10B981)),
        AccentPreset("Electric Cyan", Color(0xFF06B6D4)),
        AccentPreset("Royal Violet", Color(0xFF8B5CF6)),
        AccentPreset("Amber Gold", Color(0xFFF59E0B)),
        AccentPreset("Crimson Rose", Color(0xFFF43F5E)),
        AccentPreset("Cobalt Blue", Color(0xFF3B82F6)),
        AccentPreset("Monochrome Silver", Color(0xFFCBD5E1))
    )

    fun lightenForGlass(accent: Color): Color {
        val r = (accent.red + (1f - accent.red) * 0.35f).coerceIn(0f, 1f)
        val g = (accent.green + (1f - accent.green) * 0.35f).coerceIn(0f, 1f)
        val b = (accent.blue + (1f - accent.blue) * 0.35f).coerceIn(0f, 1f)
        return Color(r, g, b, 1f)
    }

    fun toHexString(color: Color): String {
        val argb = color.toArgb()
        return "#%06X".format(0xFFFFFF and argb)
    }

    fun parseHexColor(hex: String): Color? {
        val clean = hex.trim().removePrefix("#")
        if (clean.length != 6) return null
        val rgb = clean.toLongOrNull(16) ?: return null
        return Color((0xFF000000L or rgb).toInt())
    }
}

class ThemePreferences(private val context: Context) {

    private val accentArgbKey = longPreferencesKey("accent_color_argb")

    val accentColorFlow: Flow<Color> = context.veilThemeDataStore.data.map { prefs ->
        val stored = prefs[accentArgbKey]
        if (stored != null) Color(stored.toInt()) else VeilAccentPresets.DefaultAccent
    }

    suspend fun saveAccentColor(color: Color) {
        context.veilThemeDataStore.edit { prefs ->
            prefs[accentArgbKey] = color.toArgb().toLong() and 0xFFFFFFFFL
        }
    }
}
