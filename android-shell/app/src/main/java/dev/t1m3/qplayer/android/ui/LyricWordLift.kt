package dev.t1m3.qplayer.android.ui

import kotlin.math.PI
import kotlin.math.exp

/** Spring enablement is independent of colour timing. Plain LRC can lift as
 * a whole line without pretending it contains real per-letter timestamps. */
internal data class LyricRenderPolicy(val sweep: Boolean, val lift: Boolean) {
    val glyphs: Boolean get() = sweep || lift
}

internal fun lyricRenderPolicy(
    hasWordTiming: Boolean,
    linearFallback: Boolean,
    springEnabled: Boolean
): LyricRenderPolicy = LyricRenderPolicy(hasWordTiming || linearFallback, springEnabled)

/** One measured glyph's interval in the same sweep used to paint its colour. */
internal class LyricGlyphTiming(
    groupStartMs: Long,
    groupDurationMs: Long,
    startFraction: Float,
    endFraction: Float
) {
    // Painting needs the glyph interval, but the spring response belongs to
    // the complete source syllable. Dividing both by glyph width makes narrow
    // letters snap independently instead of travelling in an overlapping wave.
    val sourceDurationMs = groupDurationMs.coerceAtLeast(1L).toDouble()
    private val span = sourceDurationMs
    private val from = startFraction.coerceIn(0f, 1f).toDouble()
    private val to = endFraction.coerceIn(from.toFloat(), 1f).toDouble()
    val startMs = groupStartMs + span * from
    val durationMs = (span * (to - from)).coerceAtLeast(0.001)

    fun progressAt(positionMs: Long): Float =
        ((positionMs - startMs) / durationMs).coerceIn(0.0, 1.0).toFloat()
}

/** ui.zip WordGlyphMotion's word-wide, critically damped auxiliary lift.
 * The optional long-note pulse is intentionally omitted: it rises past the
 * resting height and then falls back, making already-sung letters move again.
 * The trigger follows the actual measured colour edge, not an index-based lead.
 * Sampling is history-free: no frame integration, retargeting or layout work.
 */
internal class LyricWordLift(
    private val timing: LyricGlyphTiming,
    private val enabled: Boolean = true
) {
    private val duration = timing.sourceDurationMs / 1000.0
    // Preserve ui.zip's shared response (cap 3s / auxiliary factor 1.25).
    // A 450ms floor only softens very short source syllables: adjacent Chinese
    // characters keep moving together instead of finishing in 2-3 phone frames.
    // This does NOT change their colour timing or trigger subsequent letters.
    private val response = duration.coerceIn(0.45, 3.0)
    private val auxResponse = response * 1.25
    private val liftRate = 2.0 * PI / auxResponse

    // Beyond three response periods, the residual is far below a pixel. Capping
    // observation here lets finished rows stop redrawing without resetting lift.
    val settledAtMs: Long =
        kotlin.math.ceil(timing.startMs + 3.0 * auxResponse * 1000.0).toLong()

    fun at(positionMs: Long): Float {
        if (!enabled || positionMs <= timing.startMs) return 0f
        if (positionMs >= settledAtMs) return 2f
        val localTime = (positionMs - timing.startMs) / 1000.0
        val lift = step(localTime, liftRate)
        return (2.0 * lift).toFloat()
    }

    private fun step(elapsed: Double, angularRate: Double): Double {
        if (elapsed <= 0.0) return 0.0
        val phase = angularRate * elapsed
        return 1.0 - (1.0 + phase) * exp(-phase)
    }
}

internal fun lyricCascadeDelayMs(springEnabled: Boolean, manual: Boolean, distance: Int): Long =
    if (!springEnabled || manual) 0L else (distance - 1).coerceAtLeast(0) * 50L

/** Shared by intro and interlude list items; -1/3 are inactive endpoints. */
internal fun lyricLoadingBeat(positionMs: Long, startMs: Long, endMs: Long, lastBeatMs: Long): Int {
    val duration = (endMs - startMs).coerceAtLeast(1L)
    val elapsed = positionMs - startMs
    val thirdWindow = (duration / 3).coerceAtLeast(lastBeatMs)
    val thirdStart = (duration - thirdWindow).coerceAtLeast(0L)
    return when {
        elapsed < 0L -> -1
        elapsed >= duration -> 3
        elapsed >= thirdStart -> 2
        elapsed >= thirdStart / 2 -> 1
        else -> 0
    }
}

/** A wrapped glyph ends at its own row edge, not the next row's zero cursor. */
internal fun lyricGlyphAdvance(
    startX: Float,
    endCursorX: Float,
    lineRight: Float,
    endsOnNextLine: Boolean,
    fallbackWidth: Float
): Float {
    val endX = if (endsOnNextLine) lineRight else endCursorX
    return kotlin.math.abs(endX - startX).takeIf { it > 0.01f }
        ?: fallbackWidth.coerceAtLeast(0.001f)
}
