package com.kyant.backdrop.effects

import com.kyant.backdrop.internal.RoundedRectRefractionShaderString
import com.kyant.backdrop.internal.RoundedRectRefractionWithDispersionShaderString
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

// Numerical reference for the AGSL coordinate map; no Android/GPU is required.
// This does not replace a device test of RuntimeShader compilation or frame time.
private data class Point(val x: Double, val y: Double) {
    operator fun plus(p: Point) = Point(x + p.x, y + p.y)
    operator fun minus(p: Point) = Point(x - p.x, y - p.y)
    operator fun times(k: Double) = Point(x * k, y * k)
    fun length() = hypot(x, y)
    fun normal() = this * (1.0 / sqrt(max(x * x + y * y, 0.00000001)))
}

private data class LensCase(
    val name: String, val width: Double, val height: Double, val radius: Double,
    val requestedHeight: Double, val requestedAmount: Double, val convexity: Double,
)

private fun cap(u: Double): Double {
    val squared = u.coerceIn(0.0, 1.0).let { it * it }
    return GLASS_CAP_PROFILE_NUMERATOR * squared / (1.0 + sqrt(1.0 - GLASS_CAP_CURVATURE_SQUARED * squared))
}

private fun signedDistance(p: Point, lens: LensCase): Double {
    val x = abs(p.x) - (lens.width * 0.5 - lens.radius)
    val y = abs(p.y) - (lens.height * 0.5 - lens.radius)
    return hypot(max(x, 0.0), max(y, 0.0)) + min(max(x, y), 0.0) - lens.radius
}

private fun refract(p: Point, lens: LensCase, progress: Double = 1.0, tap: Double = 0.0): Point {
    val short = min(lens.width, lens.height)
    val bevel = glassBevelHeight((lens.requestedHeight * progress).toFloat(), short.toFloat()).toDouble()
    val amount = glassBevelDisplacement((lens.requestedAmount * progress).toFloat(), bevel.toFloat()).toDouble()
    if (bevel <= 0.0 || amount <= 0.0) return p
    val a = lens.width / 2.0
    val b = lens.height / 2.0
    val u = p.x / a
    val v = p.y / b
    val dome = max(1.0 - u * u - v * v, 0.0)
    val strength = (lens.convexity * progress).coerceIn(0.0, GLASS_MAX_CENTER_CONVEXITY.toDouble())
    val wx = min(a, b) / a
    val wy = min(a, b) / b
    val centre = p - Point(p.x * wx * wx, p.y * wy * wy) * (strength * dome * dome)
    val sd = signedDistance(p, lens)
    if (-sd >= bevel) return centre
    val qx = abs(p.x) - (a - lens.radius)
    val qy = abs(p.y) - (b - lens.radius)
    val outer = Point(max(qx, 0.0), max(qy, 0.0))
    val edgeNormal = if (outer.x * outer.x + outer.y * outer.y > 0.00000001) {
        Point(sign(p.x) * outer.x, sign(p.y) * outer.y).normal()
    } else if (qx >= qy) Point(sign(p.x), 0.0) else Point(0.0, sign(p.y))
    val domeNormal = Point(p.x / max(a * a, 0.000001), p.y / max(b * b, 0.000001)).normal()
    val normal = (edgeNormal + domeNormal * GLASS_DEPTH_NORMAL_WEIGHT.toDouble()).normal()
    val dispersion = 1.0 + tap * GLASS_DISPERSION_SPREAD * (u * v).coerceIn(-1.0, 1.0)
    return centre - normal * (amount * cap(1.0 + min(sd, 0.0) / bevel) * dispersion)
}

private fun near(a: Double, b: Double, tolerance: Double = 0.00001) {
    check(abs(a - b) < tolerance) { "$a != $b" }
}

fun main() {
    near(cap(0.0), 0.0)
    near(cap(1.0), 1.0)
    near(cap(0.75), 0.5)
    for (step in 1..10000) {
        val u = step / 10000.0
        check(cap(u).isFinite() && cap(u) >= cap(u - 0.0001))
        check((cap(u) - cap(u - 0.0001)) / 0.0001 < 8.0 / 3.0 + 0.001)
    }
    // Zero derivative at the inner join: no snap/ring as the bevel travels.
    check(cap(0.0001) / 0.0001 < 0.001)
    for (invalid in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
        check(glassBevelHeight(invalid, 48f) == 0f)
        check(glassBevelHeight(30f, invalid) == 0f)
        check(glassBevelDisplacement(invalid, 20f) == 0f)
    }
    val cases = listOf(
        LensCase("round button", 52.0, 52.0, 26.0, 30.0, 30.0, 0.135),
        LensCase("navigation", 300.0, 56.0, 28.0, 32.0, 46.0, 0.135),
        LensCase("compact MiniPlayer", 220.0, 44.0, 22.0, 30.0, 30.0, 0.135),
        LensCase("slider", 40.0, 24.0, 12.0, 13.0, 26.0, 0.085),
        LensCase("toggle", 60.0, 34.0, 17.0, 7.0, 20.0, 0.085),
        LensCase("selected tab", 76.0, 48.0, 24.0, 13.0, 26.0, 0.085),
        LensCase("tinted foreground", 300.0, 56.0, 28.0, 24.0, 30.0, 0.095),
        LensCase("dialog", 360.0, 500.0, 48.0, 30.0, 62.0, 0.10),
    )
    var checks = 0
    var minJacobian = Double.POSITIVE_INFINITY
    for (lens in cases) {
        val short = min(lens.width, lens.height)
        val height = glassBevelHeight(lens.requestedHeight.toFloat(), short.toFloat())
        val amount = glassBevelDisplacement(lens.requestedAmount.toFloat(), height)
        check(height <= short * 0.470001 && amount <= height * 0.320001)
        // Existing production controls all used the previous displacement cap.
        // Assert that this change actually increases their rim displacement.
        val oldHeight = min(lens.requestedHeight, short * 0.45)
        val oldAmount = min(lens.requestedAmount, oldHeight * 0.30)
        check(amount > oldAmount && amount < oldAmount * 1.15)
        // Dp/pixel density invariance, including fractional densities.
        for (density in listOf(0.75, 1.0, 2.0, 3.5)) {
            val scaled = lens.copy(width = lens.width * density, height = lens.height * density,
                radius = lens.radius * density, requestedHeight = lens.requestedHeight * density,
                requestedAmount = lens.requestedAmount * density)
            val p = Point(lens.width * 0.3, lens.height * 0.35)
            val actual = refract(p * density, scaled) * (1.0 / density)
            val expected = refract(p, lens)
            near(actual.x, expected.x); near(actual.y, expected.y)
        }
        for (progress in listOf(0.0, 0.001, 0.1, 0.5, 1.0, 1.08)) {
            fun checkCoordinate(p: Point) {
                for (tap in listOf(-1.0, 0.0, 1.0)) {
                    val result = refract(p, lens, progress, tap)
                    check(result.x.isFinite() && result.y.isFinite()) { "Non-finite: ${lens.name}" }
                    val epsilon = 0.0001
                    val dx = (refract(p + Point(epsilon, 0.0), lens, progress, tap) - result) * (1.0 / epsilon)
                    val dy = (refract(p + Point(0.0, epsilon), lens, progress, tap) - result) * (1.0 / epsilon)
                    val determinant = dx.x * dy.y - dx.y * dy.x
                    check(determinant > 0.01) { "Fold or insufficient margin: ${lens.name} $p progress=$progress tap=$tap determinant=$determinant" }
                    minJacobian = min(minJacobian, determinant)
                    checks++
                }
            }
            for (ix in -48..48) for (iy in -48..48) {
                val p = Point(ix * lens.width / 98.0, iy * lens.height / 98.0)
                if (signedDistance(p, lens) > -0.001) continue
                checkCoordinate(p)
            }
            // Stronger lenses need explicit near-rim coverage: an interior grid
            // alone can miss the largest derivative on rounded corners.
            for (inset in listOf(0.001, 0.01, 0.1)) {
                for (degree in 1 until 360) {
                    val angle = degree * kotlin.math.PI / 180.0
                    val cx = cos(angle)
                    val cy = sin(angle)
                    checkCoordinate(Point(
                        sign(cx) * (lens.width / 2.0 - lens.radius) + (lens.radius - inset) * cx,
                        sign(cy) * (lens.height / 2.0 - lens.radius) + (lens.radius - inset) * cy,
                    ))
                }
            }
        }
        // A pressure ramp must be continuous, and collapse to the identity at 0.
        val rim = Point(0.0, lens.height * 0.48)
        var previous = refract(rim, lens, 0.0)
        near((previous - rim).length(), 0.0)
        for (step in 1..1000) {
            val point = refract(rim, lens, step / 1000.0)
            check((point - previous).length() < short * 0.002)
            previous = point
        }
    }
    // Assert the exercised formula is in BOTH production shader paths, and
    // neither path added texture taps (one early return + one/seven edge taps).
    for (shader in listOf(RoundedRectRefractionShaderString, RoundedRectRefractionWithDispersionShaderString)) {
        check("$GLASS_CAP_CURVATURE_SQUARED * u2" in shader)
        check("glassBevelProfile(1.0 + sd / refractionHeight)" in shader)
        check("radiusAt(centeredCoord, cornerRadii)" in shader)
        check("axisWeight * axisWeight" in shader)
        check("circleMap" !in shader && "normalize(centeredCoord)" !in shader)
    }
    check(Regex("content\\.eval\\(").findAll(RoundedRectRefractionShaderString).count() == 2)
    check(Regex("content\\.eval\\(").findAll(RoundedRectRefractionWithDispersionShaderString).count() == 8)
    println("PASS: $checks coordinate/Jacobian checks across 8 controls, 6 press states and dispersion extremes; min determinant=$minJacobian; density scaling, finite cap slope, continuous activation, unchanged texture-tap counts.")
}
