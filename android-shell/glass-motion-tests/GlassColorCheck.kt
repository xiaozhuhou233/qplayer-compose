package dev.t1m3.qplayer.android.ui

import kotlin.math.abs

private fun near(actual: Float, expected: Float) {
    check(abs(actual - expected) < 0.00001f) { "$actual != $expected" }
}

fun main() {
    // Golden values verified against base(1).apk (u3.c / w3.a), not the old
    // QPlayer texture-preservation, 1-2dp blur or white-veil customisations.
    val black = androidGlassOptics(0f)
    near(black.brightness, -0.2f); near(black.contrast, 1f); near(black.blurDp, 2f)
    val middle = androidGlassOptics(0.5f)
    near(middle.brightness, 0.1f); near(middle.contrast, 1f); near(middle.blurDp, 8f)
    val white = androidGlassOptics(1f)
    near(white.brightness, 0.5f); near(white.contrast, 0f); near(white.blurDp, 16f)
    val darkQuarter = androidGlassOptics(0.25f)
    near(darkQuarter.brightness, 0.025f); near(darkQuarter.contrast, 1f); near(darkQuarter.blurDp, 6.5f)
    val lightQuarter = androidGlassOptics(0.75f)
    near(lightQuarter.brightness, 0.2f); near(lightQuarter.contrast, 0.75f); near(lightQuarter.blurDp, 10f)
    check(GLASS_COLOR_DURATION_MS == 1000 && GLASS_INK_DURATION_MS == 1000)
    near(androidGlassLuminance(1f, 0f, 0f), 0.2126f)
    near(androidGlassLuminance(0f, 1f, 0f), 0.7152f)
    near(androidGlassLuminance(0f, 0f, 1f), 0.0722f)
    // #808080 is light in the reference's encoded-RGB algorithm. Accidentally
    // using linear sRGB luminance classifies it as dark and breaks this test.
    check(androidGlassUsesDarkInk(androidGlassLuminance(128f / 255, 128f / 255, 128f / 255)))
    check(!androidGlassUsesDarkInk(0.5f))
    check(androidGlassUsesDarkInk(0.50001f))
    for (step in 0..1000) {
        val p = androidGlassOptics(step / 1000f)
        check(p.brightness.isFinite() && p.brightness in -0.20001f..0.50001f)
        check(p.contrast in 0f..1f && p.blurDp in 2f..16f)
        near(p.saturation, 1.5f)
        if (step > 0) {
            val previous = androidGlassOptics((step - 1) / 1000f)
            check(p.brightness >= previous.brightness)
            check(p.contrast <= previous.contrast)
            check(p.blurDp >= previous.blurDp)
        }
    }
    // Match the vendored ColorFilter.kt grayscale matrix, including its clamp.
    // No added surface veil in the APK.
    fun throughGlass(pixel: Float, sampledLuminance: Float): Float {
        val optics = androidGlassOptics(sampledLuminance)
        val filtered = (pixel * optics.contrast + 0.5f - optics.contrast * 0.5f + optics.brightness).coerceIn(0f, 1f)
        val veil = androidGlassWhiteVeil(sampledLuminance)
        return filtered * (1f - veil) + veil
    }
    // User explicitly chose the APK: at L=1 contrast becomes zero and any
    // underlying grayscale becomes white. Do not silently restore the old guard.
    for (step in 0..100) near(throughGlass(step / 100f, 1f), 1f)
    for (darkCells in 0..25) {
        val samplePixels = IntArray(25) { if (it < darkCells) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val sampled = sampledGlassLuminance(samplePixels, 5, 5)!!
        near(sampled, (25 - darkCells) / 25f)
    }
    near(throughGlass(1f, 1f), 1f)
    near(throughGlass(0f, 1f), 1f)
    near(androidGlassWhiteVeil(Float.NaN), 0f)
    near(androidGlassWhiteVeil(-1f), 0f)
    near(androidGlassWhiteVeil(2f), 0f)
    for (step in 0..1000) {
        val luminance = step / 1000f
        near(androidGlassWhiteVeil(luminance), 0f)
        near(androidGlassInnerRingAlpha(luminance), 0f)
        near(androidGlassInnerLightAlpha(luminance), 0f)
    }
    near(androidGlassInnerRingAlpha(Float.NaN), 0f)
    near(androidGlassInnerLightAlpha(Float.NaN), 0f)
    near(androidGlassOptics(Float.NaN).brightness, middle.brightness)
    near(androidGlassOptics(-1f).blurDp, black.blurDp)
    near(androidGlassOptics(2f).blurDp, white.blurDp)
    near(androidGlassOptics(0.25f).brightness, 0.025f)
    near(androidGlassOptics(0.25f).contrast, 1f)
    near(androidGlassOptics(0.49999f).brightness, androidGlassOptics(0.50001f).brightness)
    near(androidGlassOptics(0.49999f).contrast, androidGlassOptics(0.50001f).contrast)
    // Repeated dark/light backgrounds must not reuse the previous theme's ink.
    repeat(1000) {
        check(!androidGlassUsesDarkInk(androidGlassLuminance(0f, 0f, 0f)))
        check(androidGlassUsesDarkInk(androidGlassLuminance(1f, 1f, 1f)))
    }
    check(GLASS_SAMPLE_SIDE == 5 && GLASS_SAMPLE_MAX_SIDE == 5)
    for (width in 1..GLASS_SAMPLE_MAX_SIDE) for (height in 1..GLASS_SAMPLE_MAX_SIDE) {
        near(sampledGlassLuminance(IntArray(width * height) { 0xFF808080.toInt() }, width, height)!!, 128f / 255f)
    }
    // The APK averages RGB of all 25 pixels, including transparent pixels.
    near(sampledGlassLuminance(IntArray(25), 5, 5)!!, 0f)
    near(sampledGlassLuminance(IntArray(25) { 0x00FFFFFF }, 5, 5)!!, 1f)
    check(sampledGlassLuminance(IntArray(0), 0, 5) == null)
    check(sampledGlassLuminance(IntArray(1), Int.MAX_VALUE, Int.MAX_VALUE) == null)
    check(sampledGlassLuminance(intArrayOf(0xFFFFFFFF.toInt()), 32, 32) == null)
    near(sampledGlassLuminance(IntArray(25) { if (it % 5 < 2) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }, 5, 5)!!, 0.4f)
    println("PASS: APK golden optics, 1001 luminance levels, 5x5 encoded RGB (including alpha=0), 1000ms transitions, black/white threshold, no veil/inner shadow, original white endpoint.")
}
