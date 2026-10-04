package dev.t1m3.qplayer.android.ui

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

private class NoUiApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}

private class CompositionCounts {
    var pages = 0
    var glass = 0
    var observedTint = Color.Unspecified
    var observedLuminance = -1f
    var observedMotion = false
}

@Composable
private fun GlassConsumer(counts: CompositionCounts) {
    counts.glass++
    counts.observedTint = LocalGlassTint.current
    counts.observedLuminance = LocalGlassLuminance.current
    counts.observedMotion = LocalGlassMotionActive.current
}

/** Runs the real Compose recomposer and the production palette locals. */
fun main(args: Array<String>) = runBlocking {
    val staticControl = "--static-control" in args
    // A control run reproduces the former declarations. It must invalidate
    // the page; otherwise the harness is not exercising the reported defect.
    val tintLocal = if (staticControl) staticCompositionLocalOf { Color.Black } else LocalGlassTint
    val luminanceLocal = if (staticControl) staticCompositionLocalOf { 0f } else LocalGlassLuminance
    val motionLocal = if (staticControl) staticCompositionLocalOf { false } else LocalGlassMotionActive
    val clock = BroadcastFrameClock()
    val recomposer = Recomposer(coroutineContext + clock)
    val runner = launch(clock, start = CoroutineStart.UNDISPATCHED) {
        recomposer.runRecomposeAndApplyChanges()
    }
    val composition = Composition(NoUiApplier(), recomposer)
    val tint = mutableStateOf(Color.Black)
    val luminance = mutableStateOf(0f)
    val motion = mutableStateOf(false)
    val counts = CompositionCounts()
    // Keep the same content lambda, as the page outlet does during a theme
    // animation. Its glass child reads locals; the page itself does not.
    val page: @Composable () -> Unit = {
        counts.pages++
        if (staticControl) {
            counts.glass++
            counts.observedTint = tintLocal.current
            counts.observedLuminance = luminanceLocal.current
            counts.observedMotion = motionLocal.current
        } else GlassConsumer(counts)
    }
    try {
        composition.setContent {
            CompositionLocalProvider(
                tintLocal provides tint.value,
                luminanceLocal provides luminance.value,
                motionLocal provides motion.value,
                content = page,
            )
        }
        check(counts.pages == 1 && counts.glass == 1)
        repeat(120) { frame ->
            val p = (frame + 1) / 120f
            Snapshot.withMutableSnapshot {
                tint.value = Color(p, p, p, 1f)
                luminance.value = p
                motion.value = frame < 119
            }
            Snapshot.sendApplyNotifications()
            yield()
            clock.sendFrame((frame + 1L) * 8_333_333L)
            yield()
            recomposer.awaitIdle()
            check(counts.observedTint == tint.value)
            check(counts.observedLuminance == luminance.value)
            check(counts.observedMotion == motion.value)
        }
        val expectedPages = if (staticControl) 121 else 1
        check(counts.pages == expectedPages) {
            "120 glass animation samples reran the unrelated page ${counts.pages - 1} times"
        }
        check(counts.glass == 121) {
            "The glass consumer missed frame updates: ${counts.glass - 1}/120"
        }
        if (staticControl) {
            println("PASS: former static-local control reproduces 120 unrelated page executions")
        } else {
            println("PASS: 120 theme/motion samples -> 0 unrelated page executions, 120 glass updates")
        }
    } finally {
        composition.dispose()
        recomposer.close()
        runner.join()
    }
}
