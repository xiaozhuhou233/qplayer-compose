package dev.t1m3.qplayer.android.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

/** One application-wide transition of the finished Monet palette. All roles
 * share a clock, so navigation, dialogs, lyrics and player surfaces change
 * together. A new cover arriving mid-transition retargets the currently
 * displayed colours rather than restarting from the previous song's palette.
 * Initial composition already has its target: no startup colour flash.
 */
@Composable
internal fun rememberAnimatedQPlayerColorScheme(target: ColorScheme): ColorScheme {
    val transition = updateTransition(target, label = "qplayer_theme")
    return target.copy(
        primary = transition.themeColor("primary") { it.primary },
        onPrimary = transition.themeColor("onPrimary") { it.onPrimary },
        primaryContainer = transition.themeColor("primaryContainer") { it.primaryContainer },
        onPrimaryContainer = transition.themeColor("onPrimaryContainer") { it.onPrimaryContainer },
        inversePrimary = transition.themeColor("inversePrimary") { it.inversePrimary },
        secondary = transition.themeColor("secondary") { it.secondary },
        onSecondary = transition.themeColor("onSecondary") { it.onSecondary },
        secondaryContainer = transition.themeColor("secondaryContainer") { it.secondaryContainer },
        onSecondaryContainer = transition.themeColor("onSecondaryContainer") { it.onSecondaryContainer },
        tertiary = transition.themeColor("tertiary") { it.tertiary },
        onTertiary = transition.themeColor("onTertiary") { it.onTertiary },
        tertiaryContainer = transition.themeColor("tertiaryContainer") { it.tertiaryContainer },
        onTertiaryContainer = transition.themeColor("onTertiaryContainer") { it.onTertiaryContainer },
        background = transition.themeColor("background") { it.background },
        onBackground = transition.themeColor("onBackground") { it.onBackground },
        surface = transition.themeColor("surface") { it.surface },
        onSurface = transition.themeColor("onSurface") { it.onSurface },
        surfaceVariant = transition.themeColor("surfaceVariant") { it.surfaceVariant },
        onSurfaceVariant = transition.themeColor("onSurfaceVariant") { it.onSurfaceVariant },
        surfaceTint = transition.themeColor("surfaceTint") { it.surfaceTint },
        inverseSurface = transition.themeColor("inverseSurface") { it.inverseSurface },
        inverseOnSurface = transition.themeColor("inverseOnSurface") { it.inverseOnSurface },
        error = transition.themeColor("error") { it.error },
        onError = transition.themeColor("onError") { it.onError },
        errorContainer = transition.themeColor("errorContainer") { it.errorContainer },
        onErrorContainer = transition.themeColor("onErrorContainer") { it.onErrorContainer },
        outline = transition.themeColor("outline") { it.outline },
        outlineVariant = transition.themeColor("outlineVariant") { it.outlineVariant },
        scrim = transition.themeColor("scrim") { it.scrim },
        surfaceBright = transition.themeColor("surfaceBright") { it.surfaceBright },
        surfaceDim = transition.themeColor("surfaceDim") { it.surfaceDim },
        surfaceContainer = transition.themeColor("surfaceContainer") { it.surfaceContainer },
        surfaceContainerHigh = transition.themeColor("surfaceContainerHigh") { it.surfaceContainerHigh },
        surfaceContainerHighest = transition.themeColor("surfaceContainerHighest") { it.surfaceContainerHighest },
        surfaceContainerLow = transition.themeColor("surfaceContainerLow") { it.surfaceContainerLow },
        surfaceContainerLowest = transition.themeColor("surfaceContainerLowest") { it.surfaceContainerLowest },
        primaryFixed = transition.themeColor("primaryFixed") { it.primaryFixed },
        primaryFixedDim = transition.themeColor("primaryFixedDim") { it.primaryFixedDim },
        onPrimaryFixed = transition.themeColor("onPrimaryFixed") { it.onPrimaryFixed },
        onPrimaryFixedVariant = transition.themeColor("onPrimaryFixedVariant") { it.onPrimaryFixedVariant },
        secondaryFixed = transition.themeColor("secondaryFixed") { it.secondaryFixed },
        secondaryFixedDim = transition.themeColor("secondaryFixedDim") { it.secondaryFixedDim },
        onSecondaryFixed = transition.themeColor("onSecondaryFixed") { it.onSecondaryFixed },
        onSecondaryFixedVariant = transition.themeColor("onSecondaryFixedVariant") { it.onSecondaryFixedVariant },
        tertiaryFixed = transition.themeColor("tertiaryFixed") { it.tertiaryFixed },
        tertiaryFixedDim = transition.themeColor("tertiaryFixedDim") { it.tertiaryFixedDim },
        onTertiaryFixed = transition.themeColor("onTertiaryFixed") { it.onTertiaryFixed },
        onTertiaryFixedVariant = transition.themeColor("onTertiaryFixedVariant") { it.onTertiaryFixedVariant }
    )
}

@Composable
private fun Transition<ColorScheme>.themeColor(
    name: String,
    color: (ColorScheme) -> Color
): Color {
    val animated by animateColor(
        transitionSpec = { tween(durationMillis = 700, easing = FastOutSlowInEasing) },
        label = name
    ) { color(it) }
    return animated
}
