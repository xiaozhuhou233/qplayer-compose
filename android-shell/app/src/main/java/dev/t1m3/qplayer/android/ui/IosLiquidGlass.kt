package dev.t1m3.qplayer.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * The app's single Backdrop glass surface.
 *
 * Ⓜ 2026-10-01: the material is the navigation bar's
 * （「MiniPlayer 以及按钮组件都同步和导航栏一样」）— vibrancy + blur(1dp) + lens, the bar's
 * theme plate pair and the bar's fixed theme ink. The per-control adaptive sampler is
 * gone from this surface: no luminance sampling, no readbacks, no per-draw record.
 * The interaction transform, edge highlight, shadow and content stay in one
 * drawBackdrop node. Splitting these into sibling Boxes changes the coordinate
 * space and breaks the effect.
 */
@Composable
internal fun IosLiquidGlass(
    backdrop: Backdrop,
    dark: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    interaction: InteractiveHighlight? = null,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
) {
    Box(
        modifier.then(
            iosLiquidGlassModifier(
                backdrop = backdrop,
                dark = dark,
                shape = shape,
                interaction = interaction,
            )
        ),
        // Icons share the glass's measured center at every size, including
        // compact dock controls. Box's default TopStart displaces them.
        contentAlignment = Alignment.Center,
    ) {
        val ink = glassPlateInk(dark)
        androidx.compose.runtime.CompositionLocalProvider(
            LocalGlassContentColor provides ink,
            androidx.compose.material3.LocalContentColor provides ink,
        ) { content() }
    }
}

/** Apply the complete Backdrop material to a node that also owns its content. */
@Composable
internal fun iosLiquidGlassModifier(
    backdrop: Backdrop,
    dark: Boolean,
    shape: Shape = CircleShape,
    interaction: InteractiveHighlight? = null,
): Modifier {
    val tint = LocalGlassTint.current
    val refraction = LocalGlassRefraction.current
    return Modifier
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                apiGlassEffects(
                    luminance = 0.5f,
                    refraction = refraction,
                    // The bar refracts at 40dp; the listener set the controls about
                    // 30 below it（「折射率-30左右」）.
                    refractionHeight = 24.dp.toPx(),
                    refractionAmount = 10.dp.toPx(),
                )
            },
            layerBlock = interaction?.let { highlight ->
                {
                    if (size.minDimension > 0f) {
                        val offset = highlight.offset
                        val dragScale = 4.dp.toPx() / size.height.coerceAtLeast(1f)
                        val base = 1f + dragScale * highlight.pressProgress
                        val maxOffset = size.minDimension
                        translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
                        translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)
                        val angle = atan2(offset.y, offset.x)
                        scaleX = base + dragScale *
                            abs(cos(angle) * offset.x / size.maxDimension) *
                            (size.width / size.height).coerceAtMost(1f)
                        scaleY = base + dragScale *
                            abs(sin(angle) * offset.y / size.maxDimension) *
                            (size.height / size.width).coerceAtMost(1f)
                    }
                }
            },
            onDrawBackdrop = { drawBackdrop ->
                drawBackdrop()
            },
            shadow = { IosGlassShadow },
            highlight = { Highlight.Default },
            onDrawSurface = {
                drawRect(iosGlassSurface(dark, tint))
            },
        )
        .then(interaction?.modifier ?: Modifier)
        .then(interaction?.gestureModifier ?: Modifier)
}
