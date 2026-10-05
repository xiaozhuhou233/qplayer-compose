// BiliPai default readabilityMode=STABLE. The prior APK readback loop is not used.
// Theme roles and the original contrast guard drive the 240ms foreground transition.
package dev.t1m3.qplayer.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kyant.backdrop.Backdrop

internal val LocalGlassContentColor = compositionLocalOf<Color?> { null }
// Retained for existing hosts; STABLE mode performs no pixel-copy/readback.
internal val LocalGlassSamplingEnabled = compositionLocalOf { true }

@Stable
internal class IosAdaptiveGlassState(
    private val themeLuminance: State<Float>,
    private val ink: State<Color>,
    val modifier: Modifier = Modifier,
    val restoredDialog: Boolean = false,
) {
    val luminance: Float get() = themeLuminance.value
    val contentColor: Color get() = ink.value
}

@Composable
internal fun rememberRegionAdaptiveGlass(backdrop: Backdrop): IosAdaptiveGlassState =
    rememberIosAdaptiveGlass(backdrop)

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun rememberIosAdaptiveGlass(backdrop: Backdrop): IosAdaptiveGlassState {
    if (LocalRestoredIosDialogGlass.current) return rememberRestoredIosDialogGlass(backdrop)
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val stable = scheme.onSurface
    val surface = iosGlassSurface(dark, Color.Unspecified)
    val target = biliPaiContrastGuardedForeground(stable, stable, surface)
    val ink = animateColorAsState(target,
        tween(BiliPaiGlassParameters.inkAnimationMillis), label = "biliPaiGlassContentColor")
    val luminance = rememberUpdatedState(if (dark) 0f else 1f)
    return remember(ink, luminance) { IosAdaptiveGlassState(luminance, ink) }
}

// LiquidGlassAdaptiveReadability.kt: same 3:1 guard, also active in STABLE mode.
private fun biliPaiContrastGuardedForeground(
    sampledColor: Color,
    stableColor: Color,
    background: Color,
    minimumContrastRatio: Float = 3f,
): Color {
    fun contrast(color: Color): Float {
        val lighter = maxOf(color.luminance(), background.luminance())
        val darker = minOf(color.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
    if (contrast(sampledColor) >= minimumContrastRatio) return sampledColor
    if (contrast(stableColor) >= minimumContrastRatio) return stableColor
    return listOf(Color.Black, Color.White).maxBy { contrast(it) }
}

@Composable
internal fun adaptiveGlassInk(): Color =
    LocalGlassContentColor.current ?: MaterialTheme.colorScheme.onSurface
