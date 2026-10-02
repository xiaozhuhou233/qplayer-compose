// Container-open page motion: the composition locals that hand the navigation
// AnimatedContent's scopes to any card or header that claims a shared cover.
// No navigation library is added — the route stack + AnimatedContent remain the
// single navigation manager; shared elements ride on it.
package dev.t1m3.qplayer.android.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalPageSharedTransitionScope =
    staticCompositionLocalOf<SharedTransitionScope?> { null }

internal val LocalPageAnimatedScope =
    staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Ⓜ 2026-10-01: 「点击任何专辑歌单进入的新动画…….sharedBounds 实现容器共享」.
 *
 * Tags a cover with the container-shared element for the detail-stack transition.
 * Sources (home shelves' cards) and destinations (the pages' header covers) use the
 * same key — `cover:playlist:<id>` / `cover:album:<id>` — and AnimatedContent draws
 * the pair in its overlay, cross-fading inside the interpolated bounds. Keys are
 * claimed by at most one composed surface per transition: only the HOME shelves'
 * cards are tagged, so a home→library or home→artist switch can never double-claim.
 * Where the scopes are absent (MD3 dialogs, previews) this is the identity, and a
 * claim without a partner degrades to the plain enter/exit motion.
 *
 * The content cross-fade is quick (160/90ms) so the perceived motion is the
 * container growing, not a dissolve.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.sharedCover(key: String): Modifier {
    val shared = LocalPageSharedTransitionScope.current ?: return this
    val animated = LocalPageAnimatedScope.current ?: return this
    return with(shared) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(90)),
        )
    }
}
