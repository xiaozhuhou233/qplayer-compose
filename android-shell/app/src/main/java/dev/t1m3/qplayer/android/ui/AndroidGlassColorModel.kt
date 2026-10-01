// Adapted from AndroidLiquidGlass-android AdaptiveLuminanceGlassContent.kt.
// Copyright 2025 Kyant, Apache-2.0. QPlayer: extracted pure functions for regression tests.
package dev.t1m3.qplayer.android.ui

import kotlin.math.sign

internal const val GLASS_SAMPLE_SIDE = 5
// The sampling layer is recorded at no more than this many pixels on its long
// side, so the GPU readback touches a thumbnail instead of a control-sized
// texture. A 5x5 average does not gain resolution beyond this.
internal const val GLASS_SAMPLE_MAX_SIDE = 64
// Short enough that an adaptation never overlaps the next sample beat, long
// enough to read as a fade rather than a pop.
internal const val GLASS_COLOR_DURATION_MS = 240

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
    // Ⓜ 2026-10-01: the listener asked for clearer, less frosted glass
    // （「减小磨砂感，增加通透和玻璃感」）. The blur band drops from 2–16dp to
    // 1–10dp, the brightening is capped lower so the page keeps showing through,
    // and contrast no longer flattens the backdrop to a uniform wash. The lens,
    // highlight and shadows — the glassy half — are untouched.
    return AndroidGlassOptics(
        brightness = if (l > 0f) lerp(0.08f, 0.35f, l) else lerp(0.08f, -0.15f, -l),
        contrast = if (l > 0f) lerp(1f, 0.15f, l) else 1f,
        saturation = 1.6f,
        blurDp = if (l > 0f) lerp(4f, 10f, l) else lerp(4f, 1f, -l),
    )
}
