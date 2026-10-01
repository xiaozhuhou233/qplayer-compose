package dev.t1m3.qplayer.android.ui.motion

// Navigation recipes adapted from Seal/ui/common/AnimatedComposable.kt.
// Source: user-provided Seal-main.zip. See assets/licenses/Seal-GPL-3.0.txt.
// Shared-axis primitives retain their own SOUP Apache-2.0 notices.
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import dev.t1m3.qplayer.settings.SettingsCatalog

internal object SealMotion {
    const val DURATION_ENTER = 400
    const val DURATION_EXIT = 200
    const val DURATION_AXIS = 300

    fun page(forward: Boolean, preset: Int): ContentTransform {
        if (preset == SettingsCatalog.PAGE_TRANSITION_NONE)
            return EnterTransition.None togetherWith ExitTransition.None
        if (preset == SettingsCatalog.PAGE_TRANSITION_FADE) return fade()
        if (preset == SettingsCatalog.PAGE_TRANSITION_SLIDE_VERTICAL) {
            val direction = if (forward) 1 else -1
            return materialSharedAxisY({ direction * it / 10 }, { -direction * it / 10 })
        }
        // Ⓜ The ZOOM preset has its own branch again. The motion rework dropped it — preset 0,
        // the DEFAULT, fell into the seal shared-axis below, whose 10% slide is too subtle to
        // register as an animation, which is the listener's 「我还没有看到点击封面进入的动画和退出
        // 的动画」. A material zoom-through: the incoming page grows from 80% while fading in,
        // the outgoing one keeps growing past the camera (to 115%) while fading out — forward;
        // the reverse on the way back. Visible in both directions, on every screen.
        if (preset == SettingsCatalog.PAGE_TRANSITION_ZOOM) {
            return if (forward)
                (scaleIn(tween(350, easing = EmphasizedDecelerate), initialScale = 0.80f) +
                        fadeIn(tween(220))) togetherWith
                        (scaleOut(tween(350, easing = EmphasizedAccelerate), targetScale = 1.15f) +
                                fadeOut(tween(220)))
            else
                (scaleIn(tween(350, easing = EmphasizedDecelerate), initialScale = 1.15f) +
                        fadeIn(tween(220))) togetherWith
                        (scaleOut(tween(350, easing = EmphasizedAccelerate), targetScale = 0.80f) +
                                fadeOut(tween(220)))
        }
        // Seal navigation: short shared-axis movement, 35% outgoing fade,
        // then 65% incoming fade; no list/container remeasurement or overlay.
        if (forward) return materialSharedAxisXIn({ (it * if (Build.VERSION.SDK_INT >= 34) 0.15f else 0.10f).toInt() }) togetherWith
            materialSharedAxisXOut({ -(it * 0.10f).toInt() })
        val incoming = materialSharedAxisXIn({ -(it * 0.10f).toInt() })
        val outgoing = materialSharedAxisXOut({ (it * 0.10f).toInt() })
        return if (Build.VERSION.SDK_INT >= 34)
            (incoming + scaleIn(tween(350, easing = EmphasizedDecelerate), initialScale = 0.9f)) togetherWith
                (outgoing + scaleOut(tween(350, easing = EmphasizedAccelerate), targetScale = 0.9f))
        else incoming togetherWith outgoing
    }

    fun tab(forward: Boolean): ContentTransform {
        val direction = if (forward) 1 else -1
        return materialSharedAxisX({ direction * it / 4 }, { -direction * it / 4 })
    }
    fun fade(): ContentTransform = fadeIn(tween(DURATION_EXIT)) togetherWith fadeOut(tween(DURATION_EXIT))
    fun sheetIn(): EnterTransition = slideInVertically(
        tween(DURATION_ENTER, easing = EmphasizeEasing), initialOffsetY = { it }
    ) + fadeIn(tween(DURATION_EXIT))
    fun sheetOut(): ExitTransition = slideOutVertically(
        tween(DURATION_ENTER, easing = EmphasizeEasing), targetOffsetY = { it }
    ) + fadeOut(tween(DURATION_EXIT))
}
