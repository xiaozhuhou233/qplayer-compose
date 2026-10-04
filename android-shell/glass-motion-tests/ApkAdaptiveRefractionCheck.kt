package com.kyant.backdrop.effects

import com.kyant.backdrop.internal.ApkAdaptiveRefractionShaderString
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

private data class Vec(val x: Double, val y: Double) {
    operator fun plus(other: Vec) = Vec(x + other.x, y + other.y)
    operator fun times(amount: Double) = Vec(x * amount, y * amount)
    fun finite() = x.isFinite() && y.isFinite()
    fun normal(guard: Boolean): Vec {
        val length = sqrt(if (guard) max(x * x + y * y, 1e-8) else x * x + y * y)
        return Vec(x / length, y / length)
    }
}

// CPU transcription of the APK w0.g.h shader, plus the two safety guards
// used by QPlayer for small controls. This is a formula check, not a GPU test.
private fun refract(p: Vec, width: Double, height: Double, radius: Double,
    bevel: Double, amount: Double, guard: Boolean): Vec {
    val half = Vec(width / 2, height / 2)
    val q = Vec(abs(p.x) - (half.x - radius), abs(p.y) - (half.y - radius))
    var sd = hypot(max(q.x, 0.0), max(q.y, 0.0)) - radius + min(max(q.x, q.y), 0.0)
    if (-sd >= bevel) return p
    sd = min(sd, 0.0)
    val u = 1.0 + sd / bevel
    val radicand = 1.0 - u * u
    val d = (1.0 - sqrt(if (guard) max(radicand, 0.0) else radicand)) * -amount
    val gradRadius = min(radius * 1.5, min(half.x, half.y))
    val g = Vec(abs(p.x) - (half.x - gradRadius), abs(p.y) - (half.y - gradRadius))
    val edge = if (g.x >= 0.0 || g.y >= 0.0) {
        val n = Vec(max(g.x, 0.0), max(g.y, 0.0)).normal(guard)
        Vec(sign(p.x) * n.x, sign(p.y) * n.y)
    } else if (g.x >= g.y) Vec(sign(p.x), 0.0) else Vec(0.0, sign(p.y))
    val direction = (edge + p.normal(guard)).normal(guard)
    return p + direction * d
}

fun main() {
    check(APK_ADAPTIVE_REFRACTION_HEIGHT_DP == 24f)
    check(APK_ADAPTIVE_REFRACTION_SIZE_FRACTION == 0.5f)
    val shader = ApkAdaptiveRefractionShaderString
    check("1.0 - sqrt(max(1.0 - x * x, 0.0))" in shader)
    check("circleMap(1.0 + sd / refractionHeight) * refractionAmount" in shader)
    check("min(radius * 1.5, min(halfSize.x, halfSize.y))" in shader)
    check("+ depthEffect * referenceNormal(centeredCoord)" in shader)
    check("max(dot(v, v), 0.00000001)" in shader)
    check("centerConvexity" !in shader && "chromaticAberration" !in shader)
    check("glassBevelProfile" !in shader && "convexBackdropCoord" !in shader)
    // Two mutually exclusive returns, one texture evaluation per pixel.
    check(Regex("content\\.eval\\(").findAll(shader).count() == 2)
    var finitePoints = 0
    var matchedPoints = 0
    val shapes = listOf(40.0 to 24.0, 44.0 to 44.0, 320.0 to 48.0,
        280.0 to 64.0, 160.0 to 160.0, 400.0 to 300.0)
    for (density in listOf(0.75, 1.0, 1.5, 2.0, 3.0, 4.0)) {
        for ((widthDp, heightDp) in shapes) {
            val width = widthDp * density
            val height = heightDp * density
            val radius = min(min(width, height) / 2, if (heightDp >= 160) 24 * density else Double.MAX_VALUE)
            val bevel = APK_ADAPTIVE_REFRACTION_HEIGHT_DP * density
            val amount = min(width, height) * APK_ADAPTIVE_REFRACTION_SIZE_FRACTION
            for (ix in -50..50) for (iy in -50..50) {
                val p = Vec(width * ix / 100, height * iy / 100)
                val actual = refract(p, width, height, radius, bevel, amount, true)
                check(actual.finite()) { "Non-finite lens at $widthDp x $heightDp, $density, $p" }
                finitePoints++
                val original = refract(p, width, height, radius, bevel, amount, false)
                if (original.finite()) {
                    check(abs(original.x - actual.x) < 1e-7 && abs(original.y - actual.y) < 1e-7)
                    matchedPoints++
                }
            }
            val rim = refract(Vec(width / 2, 0.0), width, height, radius, bevel, amount, true)
            check(abs(rim.x - (width / 2 - amount)) < 1e-7 && abs(rim.y) < 1e-7)
        }
    }
    println("PASS: APK original lens: $finitePoints finite points; $matchedPoints equal to original at non-singular points; 6 densities, small controls/circles/pills/dialogs, 24dp bevel, half-short-side displacement, no dome/dispersion.")
}
