package com.example.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassSurfaceSubtle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SnowPrimary
import com.example.ui.theme.VeilAccentPresets
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VeilColorThemeCustomizer(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialHsv = remember(currentColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(currentColor.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2].coerceAtLeast(0.45f)) }
    var hexInput by remember(currentColor) { mutableStateOf(VeilAccentPresets.toHexString(currentColor)) }

    LaunchedEffect(currentColor) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(currentColor.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2].coerceAtLeast(0.45f)
        hexInput = VeilAccentPresets.toHexString(currentColor)
    }

    fun emitFromHsv(h: Float, s: Float, v: Float) {
        val clampedV = v.coerceIn(0.42f, 1f)
        val argb = AndroidColor.HSVToColor(floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), clampedV))
        val color = Color(argb)
        hexInput = VeilAccentPresets.toHexString(color)
        onColorSelected(color)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 7 Curated Obsidian + Accent Presets
        Text(
            text = "Curated Obsidian Presets",
            style = MaterialTheme.typography.titleSmall.copy(color = SnowPrimary)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VeilAccentPresets.all.forEach { preset ->
                val isSelected = VeilAccentPresets.toHexString(preset.color) == VeilAccentPresets.toHexString(currentColor)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) preset.color.copy(alpha = 0.22f) else GlassSurfaceSubtle)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) preset.color else GlassBorderTop,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onColorSelected(preset.color) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("theme_preset_${preset.name.lowercase().replace(" ", "_")}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(preset.color),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = ObsidianDeep,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.labelMedium.copy(color = SnowPrimary)
                    )
                }
            }
        }

        // Interactive HSV Color Wheel
        Text(
            text = "Custom Color Wheel",
            style = MaterialTheme.typography.titleSmall.copy(color = SnowPrimary)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val hueSpectrum = remember {
                listOf(
                    Color(0xFFFF0000),
                    Color(0xFFFFFF00),
                    Color(0xFF00FF00),
                    Color(0xFF00FFFF),
                    Color(0xFF0000FF),
                    Color(0xFFFF00FF),
                    Color(0xFFFF0000)
                )
            }
            Canvas(
                modifier = Modifier
                    .size(204.dp)
                    .pointerInput(brightness) {
                        detectTapGestures { pos ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.width / 2f
                            val dx = pos.x - center.x
                            val dy = pos.y - center.y
                            val dist = hypot(dx, dy).coerceAtMost(radius)
                            val angle = ((atan2(dy, dx) * 180.0 / PI) + 360.0) % 360.0
                            hue = angle.toFloat()
                            saturation = (dist / radius).coerceIn(0f, 1f)
                            emitFromHsv(hue, saturation, brightness)
                        }
                    }
                    .pointerInput(brightness) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val pos = change.position
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.width / 2f
                            val dx = pos.x - center.x
                            val dy = pos.y - center.y
                            val dist = hypot(dx, dy).coerceAtMost(radius)
                            val angle = ((atan2(dy, dx) * 180.0 / PI) + 360.0) % 360.0
                            hue = angle.toFloat()
                            saturation = (dist / radius).coerceIn(0f, 1f)
                            emitFromHsv(hue, saturation, brightness)
                        }
                    }
                    .testTag("theme_hsv_color_wheel")
            ) {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    brush = Brush.sweepGradient(hueSpectrum, center = center),
                    radius = radius,
                    center = center
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
                drawCircle(
                    color = Color.Black.copy(alpha = (1f - brightness).coerceIn(0f, 0.58f)),
                    radius = radius,
                    center = center
                )
                drawCircle(
                    color = GlassBorderTop,
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                val rad = hue * PI / 180.0
                val thumbX = center.x + (saturation * radius * cos(rad)).toFloat()
                val thumbY = center.y + (saturation * radius * sin(rad)).toFloat()
                drawCircle(
                    color = currentColor,
                    radius = 11.dp.toPx(),
                    center = Offset(thumbX, thumbY)
                )
                drawCircle(
                    color = SnowPrimary,
                    radius = 11.dp.toPx(),
                    center = Offset(thumbX, thumbY),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }

        // Luminance / Brightness Slider (clamped to keep high legibility on Obsidian Black)
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Accent Brightness",
                    style = MaterialTheme.typography.labelMedium.copy(color = SlateSecondary)
                )
                Text(
                    text = "${(brightness * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        color = SnowPrimary
                    )
                )
            }
            Slider(
                value = brightness,
                onValueChange = { nextV ->
                    brightness = nextV
                    emitFromHsv(hue, saturation, nextV)
                },
                valueRange = 0.45f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = currentColor,
                    activeTrackColor = currentColor,
                    inactiveTrackColor = GlassSurfaceSubtle
                ),
                modifier = Modifier.testTag("theme_brightness_slider")
            )
        }

        // Hex Input + Live Swatch Preview
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(currentColor)
                    .border(1.5.dp, SnowPrimary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            )
            GlassTextField(
                value = hexInput,
                onValueChange = { raw ->
                    val cleaned = raw.uppercase().filter { it == '#' || it in '0'..'9' || it in 'A'..'F' }.take(7)
                    hexInput = cleaned
                    VeilAccentPresets.parseHexColor(cleaned)?.let { parsed ->
                        onColorSelected(parsed)
                    }
                },
                label = "Hex Accent (#RRGGBB)",
                placeholder = "#10B981",
                monospace = true,
                modifier = Modifier.weight(1f),
                tag = "theme_hex_input"
            )
        }
    }
}
