package com.example.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dynamic Theme-Reactive Veil Logo.
 *
 * Uses the obsidian hooded-hand & data-shield artwork (`R.drawable.veil_logo`) and applies
 * a luminance-preserving 3D RGB hue-rotation + saturation ColorMatrix relative to the base
 * emerald green (#10B981, ~156 deg). Because neutral grey/obsidian pixels lie on the
 * (R = G = B) invariant axis of both the hue-rotation and saturation matrices, the deep
 * obsidian black background remains intact while all glowing hood, shield, and data-network
 * highlights shift in real time to match ANY preset or custom HSV Color Wheel accent.
 */
@Composable
fun VeilDynamicLogo(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = "Veil Logo"
) {
    val colorMatrix = remember(accentColor.toArgb()) {
        buildVeilLogoColorMatrix(accentColor)
    }

    Image(
        painter = painterResource(id = R.drawable.veil_logo),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        colorFilter = ColorFilter.colorMatrix(colorMatrix),
        modifier = modifier
    )
}

/**
 * Builds a 4x5 ColorMatrix that rotates hue from the base emerald glow (~156 deg) to
 * [targetColor]'s hue and scales saturation accordingly while preserving the neutral
 * obsidian black background axis.
 */
fun buildVeilLogoColorMatrix(targetColor: Color): ColorMatrix {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(targetColor.toArgb(), hsv)
    val targetHue = hsv[0]
    val targetSat = hsv[1]

    // Base artwork emerald green glow sits around 156 degrees in HSV.
    val baseHue = 156f
    val deltaHueDeg = targetHue - baseHue
    val rad = deltaHueDeg * PI / 180.0
    val cosA = cos(rad).toFloat()
    val sinA = sin(rad).toFloat()

    // Luminance-preserving hue rotation matrix (row sums == 1.0, so neutral obsidian stays untouched).
    val lumR = 0.213f
    val lumG = 0.715f
    val lumB = 0.072f

    val hueValues = floatArrayOf(
        lumR + cosA * (1f - lumR) + sinA * (-lumR),
        lumG + cosA * (-lumG) + sinA * (-lumG),
        lumB + cosA * (-lumB) + sinA * (1f - lumB),
        0f, 0f,

        lumR + cosA * (-lumR) + sinA * 0.143f,
        lumG + cosA * (1f - lumG) + sinA * 0.140f,
        lumB + cosA * (-lumB) + sinA * (-0.283f),
        0f, 0f,

        lumR + cosA * (-lumR) + sinA * (-(1f - lumR)),
        lumG + cosA * (-lumG) + sinA * lumG,
        lumB + cosA * (1f - lumB) + sinA * lumB,
        0f, 0f,

        0f, 0f, 0f, 1f, 0f
    )
    val hueMatrix = ColorMatrix(hueValues)

    // Scale saturation smoothly so low-saturation accents (e.g. Monochrome Silver) render
    // as clean luminous silver without losing contrast against obsidian black.
    val satScale = if (targetSat < 0.18f) {
        (targetSat / 0.85f).coerceIn(0.04f, 0.22f)
    } else {
        (targetSat / 0.85f).coerceIn(0.55f, 1.28f)
    }
    val satMatrix = ColorMatrix().apply { setToSaturation(satScale) }

    // Apply saturation transform after hue rotation.
    satMatrix.timesAssign(hueMatrix)
    return satMatrix
}
