package dev.t1m3.qplayer.android.md3eui

import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class LyricRowMotion(active: Boolean) {
    val offset = Animatable(0f)
    val scale = Animatable(if (active) 1f else .98f)
    val alpha = Animatable(if (active) .85f else .175f)
}

internal suspend fun runLyricScroll(
    motions: Map<Int, LyricRowMotion>,
    handoff: () -> Boolean = { false },
    scroll: suspend () -> Unit
) {
    try {
        scroll()
    } finally {
        // A gesture can cancel scrollBy after compensation has moved the rows.
        // Animatable.snapTo is cancellable too, so cleanup must outlive that job.
        if (!handoff()) withContext(NonCancellable) {
            motions.values.toList().forEach { it.offset.snapTo(0f) }
        }
    }
}

internal fun lyricBackgroundScale(position: Long, start: Long, end: Long): Float {
    fun entrance(at: Long): Float {
        val progress = ((at - start - 150L) / 460f).coerceIn(0f, 1f)
        val shifted = progress - 1f
        return 1f + 2.7f * shifted * shifted * shifted + 1.7f * shifted * shifted
    }
    if (position < end) return entrance(position)
    // A short backing vocal can end before the pop finishes. Fade from the
    // scale actually reached at that boundary, rather than jumping to 1 or 0.
    val out = ((position - end) / 280f).coerceIn(0f, 1f)
    return entrance(end) * (1f - out * out * (3f - 2f * out))
}
