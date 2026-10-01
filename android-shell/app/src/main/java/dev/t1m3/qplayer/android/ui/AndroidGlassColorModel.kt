// Adapted from AndroidLiquidGlass-android AdaptiveLuminanceGlassContent.kt.
// Copyright 2025 Kyant, Apache-2.0. QPlayer: extracted pure functions for regression tests.
package dev.t1m3.qplayer.android.ui

import kotlin.math.sign

internal const val GLASS_SAMPLE_SIDE = 5
// The sampling layer is recorded at no more than this many pixels on its long
// side, so the GPU readback touches a thumbnail instead of a control-sized
// texture. A 5x5 average does not gain resolution beyond this.
internal const val GLASS_SAMPLE_MAX_SIDE = 24
// Short enough that an adaptation never overlaps the next sample beat, long
// enough to read as a fade rather than a pop.
internal const val GLASS_COLOR_DURATION_MS = 80

/** Five-by-five centre samples without allocating a second resized bitmap. */
internal fun sampledGlassLuminance(pixels: IntArray, width: Int, height: Int): Float? {
    if (width < 1 || height < 1 || pixels.size < width * height) return null
    var total = 0.0
    var count = 0
    for (y in 0 until GLASS_SAMPLE_SIDE) {
        val py = ((y + 0.5f) * height / GLASS_SAMPLE_SIDE).toInt().coerceAtMost(height - 1)
        for (x in 0 until GLASS_SAMPLE_SIDE) {
            val px = ((x + 0.5f) * width / GLASS_SAMPLE_SIDE).toInt().coerceAtMost(width - 1)
            val pixel = pixels[py * width + px]
            if ((pixel ushr 24) == 0) continue
            total += androidGlassLuminance((pixel shr 16 and 255) / 255f,
                (pixel shr 8 and 255) / 255f, (pixel and 255) / 255f)
            count++
        }
    }
    return if (count == 0) null else (total / count).toFloat()
}

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
    // Original luminance response, with the requested lighter 1–2dp blur band.
    return AndroidGlassOptics(
        brightness = if (l > 0f) lerp(0.1f, 0.5f, l) else lerp(0.1f, -0.2f, -l),
        contrast = if (l > 0f) lerp(1f, 0f, l) else 1f,
        saturation = 1.5f,
        blurDp = if (l > 0f) lerp(1.5f, 2f, l) else lerp(1.5f, 1f, -l),
    )
}
