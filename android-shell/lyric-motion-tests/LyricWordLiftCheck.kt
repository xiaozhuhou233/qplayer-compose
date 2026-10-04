package dev.t1m3.qplayer.android.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

// Standalone regression checks against the actual UI helpers, without an
// emulator, Compose runtime or additional downloaded test dependencies.
fun main() {
    checkPlaybackClock()
    checkIndependentGlyphLift()
    for (wordTiming in listOf(false, true)) for (linear in listOf(false, true)) {
        for (spring in listOf(false, true)) {
            val policy = lyricRenderPolicy(wordTiming, linear, spring)
            check(policy.lift == spring) { "Spring depends on lyric format" }
            check(policy.sweep == (wordTiming || linear)) { "Invented word timing or ignored fallback setting" }
            check(policy.glyphs == (wordTiming || linear || spring)) { "Enabled spring bypasses animated renderer" }
        }
    }
    val plain = lyricRenderPolicy(false, false, true)
    check(plain.glyphs && plain.lift && !plain.sweep)
    val plainLine = LyricGlyphTiming(1000, 5000, 0f, 1f)
    val lineLifts = List(30) { LyricWordLift(plainLine) }
    for (t in 0L..10000L step 7) {
        check(lineLifts.all { it.at(t) == lineLifts.first().at(t) }) { "Untimed line was split into fake syllables" }
    }
    fun near(actual: Float, expected: Double, tolerance: Double = 0.00002) {
        check(abs(actual - expected) < tolerance) { "$actual != $expected" }
    }
    fun sourceStep(elapsed: Double, response: Double): Double {
        if (elapsed <= 0.0) return 0.0
        val phase = 2.0 * PI * elapsed / response
        return 1.0 - (1.0 + phase) * exp(-phase)
    }
    var samples = 0
    for (wordDuration in listOf(80L, 250L, 500L, 1000L, 1800L, 4200L, 12000L)) {
        val widths = listOf(5f, 27f, 9f, 32f, 7f, 20f)
        val word = LyricLineSweep(listOf(LyricSweepGroup(1000, 1000 + wordDuration, 100f)))
        var left = 0f
        val timings = widths.map { width ->
            word.glyphTiming(0, left, width).also { left += width }
        }
        val lifts = timings.map { LyricWordLift(it) }
        timings.forEachIndexed { index, timing ->
            val motion = lifts[index]
            val response = (wordDuration / 1000.0).coerceIn(0.45, 3.0)
            var previous = 0f
            for (t in 900L..motion.settledAtMs + 1) {
                val elapsed = (t - timing.startMs) / 1000.0
                val expected = 2 * sourceStep(elapsed, response * 1.25)
                val lift = motion.at(t)
                check(lift >= previous) { "Glyph fell back: duration=$wordDuration at $t: $previous -> $lift" }
                check(lift <= 2f) { "Glyph overshot its resting height: duration=$wordDuration at $t: $lift" }
                near(lift, expected)
                check(lift.isFinite() && lift in 0f..2f)
                if (timing.progressAt(t) == 0f) check(lift == 0f) { "Early glyph $index at $t" }
                check(abs(lift - previous) < 0.025f) { "Discontinuous lift at $t: $previous -> $lift" }
                previous = lift
                samples++
            }
            near(motion.at(motion.settledAtMs - 1), 2.0)
            check(motion.at(motion.settledAtMs) == 2f)
            val mid = (timing.startMs + timing.durationMs * .6).toLong()
            val expected = motion.at(mid)
            // Recomposition/seek/order/frame drops cannot alter any later sample.
            motion.at(motion.settledAtMs + 10000)
            repeat(12) { check(motion.at(mid) == expected) }
            for (hz in listOf(30, 60, 90, 120)) {
                for (frame in 0..hz * 4) motion.at(1000 + frame * 1000L / hz)
                check(motion.at(mid) == expected)
            }
        }
        check(lifts.last().at(kotlin.math.ceil(timings.first().startMs + timings.first().durationMs).toLong()) == 0f)
    }
    // Narrow letters must not move faster than wide letters in the same word.
    val narrow = LyricWordLift(LyricGlyphTiming(0, 900, 0f, .1f))
    val wide = LyricWordLift(LyricGlyphTiming(0, 900, .1f, .9f))
    for (elapsed in 0L..1500L) near(narrow.at(elapsed), wide.at(elapsed + 90).toDouble())
    check(narrow.at(180) < 1.5f) { "Narrow glyph snapped before its neighbour" }

    // Short independently timed Chinese syllables overlap without rising early.
    val cjk = (0 until 8).map { LyricWordLift(LyricGlyphTiming(it * 160L, 160, 0f, 1f)) }
    check(cjk[0].at(200) in .5f..1.5f && cjk[1].at(200) > 0f)
    check(cjk.last().at(200) == 0f)
    for (t in 0L..2500L) for (i in 1..cjk.lastIndex) {
        check(cjk[i - 1].at(t) >= cjk[i].at(t)) { "Later Chinese syllable overtook its neighbour" }
    }
    // Exact nonuniform source durations, rests and overlapping notes survive.
    val variable = LyricLineSweep(listOf(
        LyricSweepGroup(1000, 1100, 30f),
        LyricSweepGroup(1250, 3050, 30f),
        LyricSweepGroup(3000, 3250, 30f)
    ))
    val a = variable.glyphTiming(0, 0f, 30f)
    val b = variable.glyphTiming(1, 30f, 30f)
    val c = variable.glyphTiming(2, 60f, 30f)
    near(a.progressAt(1050), .5)
    check(a.progressAt(1200) == 1f && b.progressAt(1200) == 0f)
    near(b.progressAt(2150), .5)
    near(c.progressAt(3125), .5)
    check(LyricWordLift(b).at(1249) == 0f)
    check(LyricWordLift(b, false).at(999999) == 0f)
    check(LyricWordLift(LyricGlyphTiming(0, 0, 0f, 0f)).at(1).isFinite())
    println("PASS: $samples monotonic lift, onset and continuity samples; no overshoot or fallback")
    println("PASS: all 8 format/linear/spring combinations; untimed lines lift without a fake word sweep")
    println("PASS: overlapping word/CJK lift; width-independent response; no early final glyph")
    println("PASS: unchanged source durations/rests/overlaps; pause/seek; 30/60/90/120 Hz; disabled spring")
}

private fun checkIndependentGlyphLift() {
    // One source word expands into six independently timed letters. Exercise
    // the same factory the real Canvas path uses, including its whitespace rule.
    val lifts = "Bright".mapIndexed { index, letter ->
        lyricUnitLift(letter.toString(), 1000L + index * 200L, 1200L + index * 200L)
    }
    for (position in 0L..4000L step 7L) {
        lifts.forEachIndexed { index, lift ->
            val start = 1000L + index * 200L
            if (position <= start) check(lift.at(position) == 0f) { "Glyph $index rose before its own onset" }
            if (index > 0) check(lifts[index - 1].at(position) >= lift.at(position)) {
                "Glyph $index rose before its predecessor"
            }
        }
    }
    check(lifts[0].at(1100) > 0f && lifts.drop(1).all { it.at(1100) == 0f }) {
        "All letters still share the source word's lift clock"
    }
    check(lifts[0].at(1300) > lifts[1].at(1300) && lifts[1].at(1300) > 0f)
    check(lyricUnitLift(" \t", 1000L, 1200L).at(10000L) == 0f)
    check(lyricUnitLift("", 1000L, 1200L).at(10000L) == 0f)
    check(lyricUnitLift("字", 1000L, 1000L).at(1100L).isFinite())
    val preview = lifts.map { it.at(1500L) }
    repeat(3) { check(lifts.map { it.at(1500L) } == preview) }
    lifts.forEach { it.at(20000L) }
    check(lifts.map { it.at(1500L) } == preview) { "A backwards seek changed independent glyph lift" }
    println("PASS: production per-character lift factory; staggered source-word onsets, whitespace, pause and seek")
}

private fun checkPlaybackClock() {
    val origin = 1_000_000_000L
    fun ns(ms: Long) = origin + ms * 1_000_000L
    // Keep the page open for ten minutes. Irregular UI polling must neither
    // rewind the highlight nor accumulate drift against authoritative audio.
    for (hz in listOf(30, 60, 90, 120)) {
        val clock = LyricPlaybackClock()
        var sampleTime = 0L
        var nextSample = 100L
        var previous = 0L
        for (frame in 0..hz * 600) {
            val now = frame * 1000L / hz
            if (now >= nextSample) {
                sampleTime = now
                nextSample = now + if ((now / 2000) % 3 == 1L) 260L else 100L
            }
            val rendered = clock.positionAt(sampleTime, ns(sampleTime), true, 1, ns(now))
            check(rendered >= previous) { "Clock rewound at $hz Hz, t=$now: $previous -> $rendered" }
            check(abs(rendered - now) <= 180) { "Clock drift at t=$now: $rendered" }
            previous = rendered
        }
    }
    // No new audio position (buffering): do not invent unlimited progress or
    // rewind to the start of the last sample when extrapolation expires.
    val stalled = LyricPlaybackClock()
    var previous = 5000L
    for (now in 0L..2000L step 10) {
        val rendered = stalled.positionAt(5000, ns(now), true, 1, ns(now))
        check(rendered >= previous) { "Buffering caused reverse word motion: $previous -> $rendered" }
        check(rendered <= 5150) { "Buffering advanced lyrics without audio: $rendered" }
        previous = rendered
    }
    check(stalled.positionAt(5000, ns(2100), false, 1, ns(2100)) == 5000L)
    check(stalled.positionAt(1200, ns(2200), true, 2, ns(2200)) == 1200L)
    check(stalled.positionAt(60000, ns(2300), true, 3, ns(2300)) == 60000L)
    // Return from the background: the very first fresh sample wins immediately.
    check(stalled.positionAt(120000, ns(600000), true, 3, ns(600000)) == 120000L)
    // A small backwards seek is below the automatic discontinuity threshold;
    // its explicit seek revision must still bypass the monotonic hold.
    check(stalled.positionAt(119980, ns(600010), true, 3, ns(600010), 1) == 119980L)
    check(stalled.positionAt(120000, ns(600020), true, 3, ns(600020), 2) == 120000L)
    for (speed in listOf(0.75, 1.25, 2.0)) {
        val changingRate = LyricPlaybackClock()
        var sampleTime = 0L
        for (now in 0L..60000L step 10) {
            if (now % 100L == 0L) sampleTime = now
            val audio = (sampleTime * speed).toLong()
            val shown = changingRate.positionAt(audio, ns(sampleTime), true, 1, ns(now))
            check(abs(shown - now * speed) <= 160.0) { "Rate $speed drift at $now: $shown" }
        }
    }
    println("PASS: ten-minute lyric clocks at 30/60/90/120 Hz; delayed samples, buffering, pause, replay and resume")
    println("PASS: explicit small forward/backward seeks and audio rates 0.75x/1.25x/2x")
}
