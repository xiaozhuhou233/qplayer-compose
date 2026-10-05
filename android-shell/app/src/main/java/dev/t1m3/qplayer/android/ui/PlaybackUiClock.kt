package dev.t1m3.qplayer.android.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The high-frequency play head is independent of library, navigation and theme state. */
@Immutable
internal data class PlaybackClockSample(
    val positionMs: Long = 0L,
    val lyricPositionMs: Long = positionMs,
    val sampledAtNanos: Long = 0L,
    val running: Boolean = false,
    val playbackRevision: Long = 0L,
    val seekRevision: Long = 0L,
)

@Stable
internal class PlaybackUiClock(initialPositionMs: Long = 0L) {
    var sample by mutableStateOf(PlaybackClockSample(positionMs = initialPositionMs))
        private set

    fun publish(next: PlaybackClockSample) {
        val previous = sample
        // A new wall-clock sample is not a UI change while playback is stationary.
        // Keep seeks, offsets and track/revision changes observable even when paused.
        if (!next.running && !previous.running &&
            next.copy(sampledAtNanos = previous.sampledAtNanos) == previous) return
        sample = next
    }
}
