package dev.t1m3.qplayer.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** A retained page can be mounted for navigation while completely covered. */
internal val LocalUiRenderingActive = compositionLocalOf { true }

@Composable
internal fun rememberUiRenderingActive(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var started by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return started && LocalUiRenderingActive.current
}
