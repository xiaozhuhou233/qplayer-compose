// Adapted from AndroidLiquidGlass-android AdaptiveLuminanceGlassContent.kt.
// Copyright 2025 Kyant, Apache-2.0. QPlayer: extracted pure functions for regression tests.
package dev.t1m3.qplayer.android.ui

import kotlin.math.sign

internal const val GLASS_SAMPLE_SIDE = 5
internal const val GLASS_COLOR_DURATION_MS = 1000

// Deliberately use the reference's encoded RGB weights, NOT Color.luminance()
// (which linearizes sRGB and would change the reference's 0.5 threshold).
internal fun androidGlassLuminance(red: Float, green: Float, blue: Float): Float =
    (0.2126 * red + 0.7152 * green + 0.0722 * blue).toFloat().coerceIn(0f, 1f)

internal fun androidGlassUsesDarkInk(luminance: Float): Boolean = luminance > 0.5f

internal data class AndroidGlassOptics(
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val blurDp: Float,
)

internal fun androidGlassOptics(luminance: Float): AndroidGlassOptics {
    val l = (luminance.coerceIn(0f, 1f) * 2f - 1f).let { sign(it) * it * it }
    fun lerp(start: Float, end: Float, amount: Float) = (1f - amount) * start + amount * end
    return AndroidGlassOptics(
        brightness = if (l > 0f) lerp(0.1f, 0.5f, l) else lerp(0.1f, -0.2f, -l),
        contrast = if (l > 0f) lerp(1f, 0f, l) else 1f,
        saturation = 1.5f,
        blurDp = if (l > 0f) lerp(8f, 16f, l) else lerp(8f, 2f, -l),
    )
}
