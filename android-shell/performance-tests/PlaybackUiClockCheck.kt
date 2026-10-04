package dev.t1m3.qplayer.android.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver

/** Uses the APK's actual PlayerUiState, including its generated equals/copy. */
fun main() {
    check(coverDecodeSampleSize(8000, 6000, 256) == 32)
    check(coverDecodeSampleSize(8000, 6000, 1024) == 8)
    check(coverDecodeSampleSize(128, 128, 256) == 1)
    check(coverDecodeSampleSize(0, -1, 256) == 1)
    check(coverDecodeSampleSize(Int.MAX_VALUE, 1, 1024) == 2_097_152)
    println("PASS: cover sampling bounds large, thumbnail, small and invalid dimensions")
    val stateType = Class.forName("dev.t1m3.qplayer.android.ui.PlayerUiState")
    val initial = stateType.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
    val clock = stateType.getDeclaredMethod("getPlaybackClock").apply { isAccessible = true }
        .invoke(initial) as PlaybackUiClock
    val copy = stateType.declaredMethods.single { it.name == "copy\$default" }
        .apply { isAccessible = true }
    val fieldCount = stateType.declaredMethods.single { it.name == "copy" }.parameterCount
    fun copyState(source: Any): Any {
        val args = arrayOfNulls<Any>(copy.parameterCount)
        args[0] = source
        for (i in 1..fieldCount) args[i] = when (copy.parameterTypes[i]) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            else -> null
        }
        for (i in fieldCount + 1 until args.lastIndex) args[i] = -1
        return copy.invoke(null, *args)
    }
    val rootState = mutableStateOf(initial)
    var rootInvalidations = 0
    var clockInvalidations = 0
    val rootScope = Any()
    val progressScope = Any()
    val observer = SnapshotStateObserver { it() }
    val onRootChanged: (Any) -> Unit = { rootInvalidations++ }
    val onProgressChanged: (Any) -> Unit = { clockInvalidations++ }
    fun observe() {
        observer.observeReads(rootScope, onRootChanged) { rootState.value }
        observer.observeReads(progressScope, onProgressChanged) { clock.sample }
    }
    observer.start()
    try {
        Snapshot.sendApplyNotifications()
        observe()
        repeat(600) { tick ->
            Snapshot.withMutableSnapshot {
                clock.publish(PlaybackClockSample(
                    positionMs = tick * 100L,
                    sampledAtNanos = (tick + 1L) * 100_000_000L,
                    running = true,
                ))
                rootState.value = copyState(rootState.value)
            }
            Snapshot.sendApplyNotifications()
            observe()
        }
        check(rootInvalidations == 0) { "600 playback samples invalidated the app root $rootInvalidations times" }
        check(clockInvalidations == 600) { "The live progress clock stopped updating: $clockInvalidations" }
        println("PASS: 600 playing samples -> 0 root invalidations, 600 progress updates")

        clock.publish(clock.sample.copy(running = false))
        Snapshot.sendApplyNotifications()
        observe()
        val beforeIdle = clockInvalidations
        repeat(150) { tick ->
            clock.publish(clock.sample.copy(sampledAtNanos = 90_000_000_000L + tick))
            rootState.value = copyState(rootState.value)
            Snapshot.sendApplyNotifications()
            observe()
        }
        check(clockInvalidations == beforeIdle)
        check(rootInvalidations == 0)
        println("PASS: 150 paused samples -> 0 root or clock invalidations")

        val beforeSeek = clockInvalidations
        clock.publish(clock.sample.copy(positionMs = 12_000L, lyricPositionMs = 11_750L, seekRevision = 1L))
        Snapshot.sendApplyNotifications()
        observe()
        check(clockInvalidations == beforeSeek + 1)
        check(clock.sample.lyricPositionMs == 11_750L)
        clock.publish(clock.sample.copy(lyricPositionMs = -250L, playbackRevision = 1L))
        Snapshot.sendApplyNotifications()
        check(clockInvalidations == beforeSeek + 2)
        check(rootInvalidations == 0)
        println("PASS: paused seek, lyric offset and track revision still update consumers")
    } finally {
        observer.stop()
        observer.clear()
    }
}
