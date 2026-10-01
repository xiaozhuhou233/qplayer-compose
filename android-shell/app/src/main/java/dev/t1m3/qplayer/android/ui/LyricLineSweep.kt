package dev.t1m3.qplayer.android.ui

import kotlin.math.abs
import kotlin.math.exp

internal data class LyricSweepGroup(val startMs: Long, val endMs: Long, val width: Float)

/** Map measured glyph widths onto each original source syllable interval.
 * Neither short rests nor simultaneous/overlapping syllables are retimed.
 * Interpolation is only inside a provider's word, never across word boundaries.
 */
internal class LyricLineSweep(private val groups: List<LyricSweepGroup>) {
    private data class Segment(
        val startMs: Long,
        val endMs: Long,
        val left: Float,
        val right: Float
    )

    private val segments = ArrayList<Segment>()
    private val groupSegments = IntArray(groups.size) { -1 }
    val totalWidth: Float

    init {
        var advance = 0f
        groups.forEachIndexed { index, group ->
            val width = group.width.coerceAtLeast(0f)
            if (width <= 0f) return@forEachIndexed
            val start = group.startMs.coerceAtLeast(0L)
            val end = group.endMs.coerceAtLeast(start + 1L)
            segments += Segment(start, end, advance, advance + width)
            groupSegments[index] = segments.lastIndex
            advance += width
        }
        totalWidth = advance
    }

    fun advanceAt(positionMs: Long): Float {
        var low = 0
        var high = segments.lastIndex
        var active = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (segments[mid].startMs <= positionMs) {
                active = mid
                low = mid + 1
            } else high = mid - 1
        }
        if (active < 0) return 0f
        val segment = segments[active]
        val progress = ((positionMs - segment.startMs).toDouble() /
            (segment.endMs - segment.startMs).coerceAtLeast(1L)).coerceIn(0.0, 1.0)
        return segment.left + (segment.right - segment.left) * progress.toFloat()
    }

    fun glyphTiming(groupIndex: Int, left: Float, width: Float): LyricGlyphTiming {
        val segmentIndex = groupSegments[groupIndex]
        if (segmentIndex < 0) {
            val group = groups[groupIndex]
            return LyricGlyphTiming(group.startMs, (group.endMs - group.startMs).coerceAtLeast(1L), 0f, 1f)
        }
        val segment = segments[segmentIndex]
        val span = (segment.right - segment.left).coerceAtLeast(0.001f)
        return LyricGlyphTiming(
            segment.startMs,
            segment.endMs - segment.startMs,
            (left - segment.left) / span,
            (left + width - segment.left) / span
        )
    }
}

/** Tracks timestamped audio samples with signed phase correction. In contrast
 * to max(predicted, reported), an early frame cannot permanently advance the
 * baseline. All interpolation is bounded when the backend stops reporting.
 */
internal class LyricPlaybackClock {
    private var initialized = false
    private var lastSampleNanos = 0L
    private var lastSamplePosition = 0L
    private var lastAdvanceNanos = 0L
    private var lastFrameNanos = 0L
    private var revision = 0L
    private var seekRevision = 0L
    private var rateBaseNanos = 0L
    private var rateBasePosition = 0L
    private var rate = 1.0
    private var rendered = 0.0

    fun positionAt(positionMs: Long, sampleNanos: Long, running: Boolean,
                   playbackRevision: Long, frameNanos: Long, seekRevision: Long = 0L): Long {
        if (sampleNanos <= 0L) return positionMs
        val ageMs = ((frameNanos - sampleNanos) / 1_000_000.0).coerceAtLeast(0.0)
        val newSample = sampleNanos != lastSampleNanos
        val sampleDeltaMs = (sampleNanos - lastSampleNanos) / 1_000_000.0
        val discontinuity = initialized && newSample && (
            positionMs < lastSamplePosition - 60L ||
                abs(positionMs - lastSamplePosition - sampleDeltaMs * rate) > 300.0
            )
        if (!initialized || !running || playbackRevision != revision || this.seekRevision != seekRevision ||
            (newSample && sampleDeltaMs > 500.0) ||
            sampleNanos < lastSampleNanos || discontinuity) {
            initialized = true
            lastSampleNanos = sampleNanos
            lastSamplePosition = positionMs
            lastAdvanceNanos = sampleNanos
            lastFrameNanos = frameNanos
            revision = playbackRevision
            this.seekRevision = seekRevision
            rateBaseNanos = sampleNanos
            rateBasePosition = positionMs
            rate = 1.0
            rendered = positionMs + if (running) ageMs.coerceAtMost(150.0) else 0.0
            return rendered.toLong()
        }
        if (newSample) {
            val advanced = positionMs > lastSamplePosition
            val advanceGapMs = (sampleNanos - lastAdvanceNanos) / 1_000_000.0
            val rateWindowMs = (sampleNanos - rateBaseNanos) / 1_000_000.0
            // Repeated positions during buffering are not a slower playback
            // speed. Do not poison the rate estimate with stopped audio or a
            // suspended UI poll; reacquire it from subsequent advancing samples.
            if (advanced && advanceGapMs <= 350.0 && rateWindowMs in 200.0..500.0) {
                val measuredRate = ((positionMs - rateBasePosition) / rateWindowMs).coerceIn(0.0, 4.0)
                rate += (measuredRate - rate) * 0.35
            }
            if (rateWindowMs >= 200.0) {
                rateBaseNanos = sampleNanos
                rateBasePosition = positionMs
            }
            if (advanced) lastAdvanceNanos = sampleNanos
            lastSampleNanos = sampleNanos
            lastSamplePosition = positionMs
        }
        val frameMs = ((frameNanos - lastFrameNanos) / 1_000_000.0).coerceIn(0.0, 100.0)
        lastFrameNanos = frameNanos
        val stalled = ageMs > 180.0 || frameNanos - lastAdvanceNanos > 180_000_000L
        val effectiveRate = if (stalled) 0.0 else rate
        val target = positionMs + ageMs.coerceAtMost(150.0) * effectiveRate
        val predicted = rendered + frameMs * effectiveRate
        val error = target - predicted
        val corrected = if (abs(error) > 300.0) target else
            predicted + error * (1.0 - exp(-frameMs / 70.0))
        // Phase smoothing must not carry prediction past the same extrapolation
        // horizon as the target when a backend sample goes stale.
        // Expiring a prediction used to target the OLD audio position and pull
        // the fill/lift backwards every time polling exceeded 180ms. Hold the
        // last rendered position until audio catches up; explicit seeks, replay
        // and authoritative discontinuities are handled by the reset above.
        rendered = maxOf(rendered, minOf(corrected, positionMs + 150.0 * rate))
        return rendered.toLong()
    }
}
