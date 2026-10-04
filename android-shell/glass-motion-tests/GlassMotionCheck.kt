package com.kyant.backdrop.catalog.utils

import androidx.compose.animation.core.FloatSpringSpec
import kotlin.math.abs

fun main() {
    val frame = 8_333_333L
    val start = 1_000_000_000L
    val spec = FloatSpringSpec(0.5f, 300f, 0.01f)
    val baseline = RetargetableFloatSpring(0.5f, 300f, 0.01f)
    check(baseline.sample(12f, start) == 0f)
    val duration = spec.getDurationNanos(0f, 12f, 0f)
    repeat(240) { index ->
        val elapsed = index * frame
        val actual = baseline.sample(12f, start + elapsed)
        val expected = if (elapsed >= duration) 12f else spec.getValueFromNanos(elapsed, 0f, 12f, 0f)
        check(abs(actual - expected) < 0.0001f) { "Reference spring mismatch at frame $index" }
    }
    check(baseline.isFinished)

    val stress = RetargetableFloatSpring(0.5f, 300f, 0.01f)
    var time = start
    var target = 0f
    repeat(20_000) { index ->
        time += frame
        val before = stress.sample(target, time)
        target = ((index * 37 % 101) - 50).toFloat()
        val after = stress.sample(target, time)
        check(after.isFinite() && abs(after) < 150f)
        check(abs(after - before) < 0.0001f) { "Retarget jumped at event $index" }
    }
    var settledAt = -1
    repeat(360) { frameIndex ->
        time += frame
        val value = stress.sample(0f, time)
        check(value.isFinite())
        if (stress.isFinished && settledAt < 0) settledAt = frameIndex
    }
    check(settledAt >= 0) { "Spring never stopped requesting frames" }
    repeat(20_000) {
        time += frame
        check(stress.sample(0f, time) == 0f && stress.isFinished) { "Idle spring restarted" }
    }
    println("PASS: original spring match, 20000 continuous retargets, finite settle, 20000 idle samples")
    println("Settled after $settledAt frames at 120 Hz")
}
