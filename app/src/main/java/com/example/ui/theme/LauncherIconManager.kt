package com.example.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs

enum class VeilLauncherPreset(
    val aliasClassName: String,
    val referenceHue: Float,
    val isAchromatic: Boolean = false
) {
    GREEN("com.example.MainActivityGreen", 160f),
    CYAN("com.example.MainActivityCyan", 189f),
    COBALT("com.example.MainActivityCobalt", 217f),
    VIOLET("com.example.MainActivityViolet", 258f),
    CRIMSON("com.example.MainActivityCrimson", 350f),
    AMBER("com.example.MainActivityAmber", 38f),
    SILVER("com.example.MainActivitySilver", 0f, isAchromatic = true)
}

/**
 * Switches the active Android home screen launcher icon via <activity-alias> components
 * whenever the user changes their theme preset or selects a custom HSV Color Wheel accent.
 */
object LauncherIconManager {

    fun resolveClosestPreset(accentColor: Color): VeilLauncherPreset {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(accentColor.toArgb(), hsv)
        val hue = hsv[0]
        val saturation = hsv[1]

        // Low-saturation / neutral accents map to Monochrome Silver.
        if (saturation < 0.20f) {
            return VeilLauncherPreset.SILVER
        }

        return VeilLauncherPreset.entries
            .filter { !it.isAchromatic }
            .minByOrNull { preset -> circularHueDistance(hue, preset.referenceHue) }
            ?: VeilLauncherPreset.GREEN
    }

    fun applyLauncherIconForColor(context: Context, accentColor: Color) {
        val target = resolveClosestPreset(accentColor)
        applyPreset(context, target)
    }

    fun applyPreset(context: Context, target: VeilLauncherPreset) {
        val appContext = context.applicationContext
        val pm = appContext.packageManager
        val pkg = appContext.packageName

        runCatching {
            val targetComponent = ComponentName(pkg, target.aliasClassName)
            val currentState = pm.getComponentEnabledSetting(targetComponent)

            // Skip work if this exact alias is already active and all others are disabled.
            val allOthersDisabled = VeilLauncherPreset.entries
                .filter { it != target }
                .all { other ->
                    val st = pm.getComponentEnabledSetting(ComponentName(pkg, other.aliasClassName))
                    st == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                        (other != VeilLauncherPreset.GREEN && st == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
                }

            val targetAlreadyEnabled = currentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (target == VeilLauncherPreset.GREEN && currentState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

            if (targetAlreadyEnabled && allOthersDisabled) {
                return
            }

            // Enable the target alias FIRST so the launcher never briefly sees zero launcher icons.
            pm.setComponentEnabledSetting(
                targetComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            // Disable all other launcher aliases cleanly.
            VeilLauncherPreset.entries
                .filter { it != target }
                .forEach { other ->
                    pm.setComponentEnabledSetting(
                        ComponentName(pkg, other.aliasClassName),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
        }
    }

    private fun circularHueDistance(h1: Float, h2: Float): Float {
        val diff = abs(h1 - h2) % 360f
        return if (diff > 180f) 360f - diff else diff
    }
}
