package dev.t1m3.qplayer.android.ui

import com.kyant.backdrop.internal.BLOOM_STROKE_SHADER_DUAL
import com.kyant.backdrop.internal.BLOOM_STROKE_SHADER_SINGLE
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private fun biliNear(actual: Float, expected: Float) {
    check(abs(actual - expected) < 0.00001f) { "$actual != $expected" }
}

fun main() {
    val p = BiliPaiGlassParameters
    biliNear(p.progress, 0.5f)
    biliNear(p.brightness, 0f); biliNear(p.contrast, 1f)
    biliNear(p.saturation, 1.5f); biliNear(p.blurDp, 4f)
    biliNear(p.surfaceAlpha, 0.4f)
    check(p.darkSurfaceArgb == 0xFF242424)
    check(!p.readabilityAdaptive && p.inkAnimationMillis == 240)
    biliNear(p.contentReadability, 0.62f)
    biliNear(p.contentDistortion, 0.45f)
    biliNear(p.chromaticControl, 0.56f)
    val protection = 0.62f * 0.62f * 0.62f * 0.62f
    biliNear(p.readabilityProtection, protection)
    biliNear(p.contentReadabilityBoost, 0.6f * protection)
    biliNear(p.readabilityScrimAlpha, 0.6f * protection * (0.12f + protection * 0.22f))
    biliNear(p.shellLensHeightDp, 24f); biliNear(p.shellLensAmountDp, 24f)
    biliNear(p.indicatorLensHeightDp, 10f); biliNear(p.indicatorLensAmountDp, 14f)
    biliNear(p.shellChromaticAberration, 0f); biliNear(p.indicatorChromaticAberration, 0.5f)
    biliNear(p.geometryScale(64f), 1f); biliNear(p.geometryScale(48f), .75f)
    biliNear(p.geometryScale(44f), .6875f); biliNear(p.geometryScale(24f), .375f)
    biliNear(p.geometryScale(400f), 1f); biliNear(p.geometryScale(0f), 0f)
    biliNear(p.shellLensHeightDp * p.geometryScale(48f), 18f)
    biliNear(p.shellLensHeightDp * p.geometryScale(44f), 16.5f)
    biliNear(p.effectPaddingDp(64f), 40f); biliNear(p.effectPaddingDp(48f), 30f)
    biliNear(p.innerShadowRadiusDp, 8f); biliNear(p.innerShadowAlpha, .15f)
    biliNear(p.outerShadowRadiusDp, 10f); biliNear(p.outerShadowOffsetDp, 0f)
    biliNear(p.outerShadowDarkAlpha, .2f); biliNear(p.outerShadowLightAlpha, .1f)
    biliNear(p.shellHighlightAlpha, .55f); biliNear(p.indicatorHighlightAlpha, .75f)
    biliNear(p.highlightWidthDp, 1f)
    biliNear(p.highlightStrokeAlpha, .08f); biliNear(p.highlightInnerBlurDp, 2f)
    biliNear(p.primaryLightIntensity, 1f); biliNear(p.secondaryLightIntensity, .4f)
    biliNear(p.indicatorHighlightWidthDp(135f, 100f), 1f)
    biliNear(p.indicatorHighlightWidthDp(935f, 100f), 2f)
    biliNear(p.indicatorHighlightWidthDp(56f, 56f), 1f)
    biliNear(p.sensorSmoothing, .15f); biliNear(p.gravityQuantizeDegrees, 3f)
    check(p.gravityDirection(0f, 0f) == (0f to -1f))
    repeat(720) { step ->
        val angle = step * Math.PI / 360.0
        val direction = p.gravityDirection(cos(angle).toFloat(), sin(angle).toFloat())
        biliNear(sqrt(direction.first * direction.first + direction.second * direction.second), 1f)
    }
    // Exact dual-peak structure, not the old Plain/ambient edge shader.
    check("l1 * l1 * lightIntensity1" in BLOOM_STROKE_SHADER_DUAL)
    check("l2 * l2 * lightIntensity2" in BLOOM_STROKE_SHADER_DUAL)
    check("uniform float2 axis1" !in BLOOM_STROKE_SHADER_DUAL)
    check("uniform float2 axis1" in BLOOM_STROKE_SHADER_SINGLE)
    check("rgb * half(highlightAlpha)" in BLOOM_STROKE_SHADER_DUAL)
    check("innerBlurRadiusSq" in BLOOM_STROKE_SHADER_DUAL)
    println("PASS: BiliPai BALANCED optics with softened shell/indicator rims, short-shell scaling, separate indicator dispersion, surface/scrim, shadows, 720 gravity directions, dual-light BloomStroke and stable 240ms foreground.")
}
