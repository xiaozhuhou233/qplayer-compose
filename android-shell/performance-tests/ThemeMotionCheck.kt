package dev.t1m3.qplayer.android.ui

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

private class ThemeApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}

private val colorGetters = ColorScheme::class.java.declaredMethods.filter {
    it.parameterCount == 0 && it.returnType == java.lang.Long.TYPE && it.name.startsWith("get")
}.sortedBy { it.name }

private fun ColorScheme.colors(): List<Long> = colorGetters.map { it.invoke(this) as Long }

private fun colorDistance(a: Color, b: Color): Float {
    val x = a.convert(ColorSpaces.Oklab)
    val y = b.convert(ColorSpaces.Oklab)
    return kotlin.math.sqrt(
        (x.red - y.red) * (x.red - y.red) +
            (x.green - y.green) * (x.green - y.green) +
            (x.blue - y.blue) * (x.blue - y.blue),
    )
}

/** Exercises production pause/resume with the real Compose transition driver. */
fun main() = runBlocking {
    val holds = ArtworkPageMotionState()
    val page = Any()
    val player = Any()
    holds.update(page, true)
    holds.update(player, true)
    holds.update(page, false)
    check(holds.active) { "Finishing page motion released the active player hold" }
    holds.update(page, false)
    check(holds.active)
    holds.update(player, false)
    check(!holds.active)
    println("PASS: overlapping motion sources release only their own hold")

    val first = lightColorScheme(primary = Color.Red, background = Color.White, surface = Color.White)
    val second = lightColorScheme(primary = Color.Blue, background = Color.Black, surface = Color.Black)
    val third = lightColorScheme(primary = Color.Green, background = Color.Gray, surface = Color.Gray)
    val target = mutableStateOf(first)
    val paused = mutableStateOf(false)
    var displayed = first
    var compositions = 0
    val clock = BroadcastFrameClock()
    val recomposer = Recomposer(coroutineContext + clock)
    val runner = launch(clock, start = CoroutineStart.UNDISPATCHED) {
        recomposer.runRecomposeAndApplyChanges()
    }
    val composition = Composition(ThemeApplier(), recomposer)
    var now = 0L
    suspend fun frames(count: Int) {
        repeat(count) {
            Snapshot.sendApplyNotifications()
            repeat(4) { yield() }
            now += 16_666_667L
            clock.sendFrame(now)
            repeat(4) { yield() }
        }
    }
    try {
        composition.setContent {
            displayed = rememberAnimatedQPlayerColorScheme(target.value, paused.value)
            compositions++
        }
        frames(2)
        check(displayed.colors() == first.colors()) { "Initial theme flashed" }
        target.value = second
        frames(20)
        check(displayed.primary != first.primary && displayed.primary != second.primary) {
            "Expected an in-flight colour after 20 frames: ${displayed.primary}"
        }
        paused.value = true
        frames(2)
        val frozen = displayed.colors()
        val frozenCompositions = compositions
        frames(60)
        check(displayed.colors() == frozen) { "Cancelling the driver changed the held colour" }
        check(compositions == frozenCompositions) { "Paused theme continued requesting composition" }
        println("PASS: cancelling in-flight theme holds all ${colorGetters.size} roles for 60 frames")

        val heldPrimary = displayed.primary
        paused.value = false
        frames(2)
        check(colorDistance(displayed.primary, heldPrimary) < colorDistance(heldPrimary, first.primary) * 0.5f) {
            "Resuming the same target jumped back toward the old palette"
        }
        check(colorDistance(displayed.primary, second.primary) <= colorDistance(heldPrimary, second.primary)) {
            "Resuming the same target moved away from its destination"
        }
        paused.value = true
        frames(2)
        val heldBeforeRetarget = displayed.colors()
        println("PASS: resuming the same target continues from the held fraction without a colour jump")

        target.value = third
        frames(3)
        check(displayed.colors() == heldBeforeRetarget) { "A queued artwork palette jumped while navigation was active" }
        val heldBeforeRetargetPrimary = displayed.primary
        paused.value = false
        frames(2)
        check(colorDistance(displayed.primary, heldBeforeRetargetPrimary) <
            colorDistance(heldBeforeRetargetPrimary, third.primary) * 0.2f) {
            "Retargeting after pause jumped away from the displayed colour"
        }
        frames(70)
        check(displayed.colors() == third.colors()) { "Resume did not reach the latest palette" }
        println("PASS: palette update while held resumes from displayed colours and reaches the latest target")

        target.value = second
        frames(12)
        paused.value = true
        frames(2)
        val reversedFrom = displayed.colors()
        target.value = first
        frames(12)
        check(displayed.colors() == reversedFrom) { "Rapid reversal escaped the motion hold" }
        paused.value = false
        frames(70)
        check(displayed.colors() == first.colors()) { "Rapid reversal ended on a stale palette" }
        println("PASS: rapid reversal retains the current colours and ends on the requested palette")
    } finally {
        composition.dispose()
        recomposer.close()
        runner.join()
    }
}
