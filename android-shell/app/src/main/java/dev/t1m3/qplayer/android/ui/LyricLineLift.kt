package dev.t1m3.qplayer.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Observe changing line targets without subscribing composition to a frame clock. */
internal suspend fun animateLyricLineLiftTargets(
    targets: Flow<Float>,
    motion: Animatable<Float, AnimationVector1D>
) = coroutineScope {
    targets.collect { target ->
        // Animatable captures the current velocity before replacing its prior
        // animation. collectLatest would cancel it first and lose that velocity.
        launch {
            if (target != motion.targetValue || (!motion.isRunning && motion.value != target)) {
                motion.animateTo(target, spring(dampingRatio = 0.9f, stiffness = 100f))
            }
        }
    }
}
