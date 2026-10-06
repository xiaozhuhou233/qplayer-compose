package dev.t1m3.qplayer.android.md3eui

import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class LyricRowMotion(active: Boolean) {
    val offset = Animatable(0f)
    val scale = Animatable(if (active) 1f else .98f)
    val alpha = Animatable(if (active) .85f else .175f)
}

internal suspend fun runLyricScroll(motions: Map<Int, LyricRowMotion>, scroll: suspend () -> Unit) {
    try {
        scroll()
    } finally {
        // A gesture can cancel scrollBy after compensation has moved the rows.
        // Animatable.snapTo is cancellable too, so cleanup must outlive that job.
        withContext(NonCancellable) {
            motions.values.toList().forEach { it.offset.snapTo(0f) }
        }
    }
}
