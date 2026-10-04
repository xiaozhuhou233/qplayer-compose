package dev.t1m3.qplayer.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.math.abs

fun main() {
    val clock = BroadcastFrameClock()
    runBlocking(clock) {
        val target = mutableFloatStateOf(0f)
        val motion = Animatable(0f)
        var now = 0L
        suspend fun frame() {
            now += 16_666_667L
            clock.sendFrame(now)
            yield()
        }
        suspend fun setTarget(value: Float) {
            Snapshot.withMutableSnapshot { target.floatValue = value }
            repeat(3) { yield() }
        }
        fun start() = launch(start = CoroutineStart.UNDISPATCHED) {
            animateLyricLineLiftTargets(snapshotFlow { target.floatValue }, motion)
        }
        var observer = start()
        yield()
        check(!clock.hasAwaiters) { "Idle line started a frame loop" }
        setTarget(1f)
        repeat(10) { frame() }
        val before = motion.value
        val velocity = motion.velocity
        check(before in 0f..1f && velocity > 0f) { "Line spring never started" }
        setTarget(0.9f)
        frame()
        check(motion.value >= before) { "Retargeting jumped the line backwards" }
        check(motion.velocity >= velocity * 0.5f) { "Retargeting discarded the current spring velocity" }
        repeat(180) { frame() }
        check(abs(motion.value - 0.9f) < 0.0001f && !motion.isRunning)
        check(!clock.hasAwaiters) { "Paused/stable target kept requesting frames" }

        // Finishing a sung line must settle at an exact zero and go idle.
        setTarget(0f)
        repeat(180) { frame() }
        check(motion.value == 0f && !clock.hasAwaiters)

        // Hiding/disabling the row cancels all owned animation children; the
        // caller can clear its layer with snapTo without a surviving frame job.
        setTarget(1f)
        repeat(7) { frame() }
        check(motion.isRunning)
        observer.cancelAndJoin()
        motion.snapTo(0f)
        check(motion.value == 0f && !motion.isRunning && !clock.hasAwaiters)
        observer = start()
        yield()
        repeat(180) { frame() }
        check(motion.value == 1f && !motion.isRunning && !clock.hasAwaiters) {
            "Visible row failed to recover the current clock target"
        }
        observer.cancelAndJoin()
    }
    println("PASS: real Compose line spring retargets continuously, settles, cancels on hide and resumes without idle frames")
}
