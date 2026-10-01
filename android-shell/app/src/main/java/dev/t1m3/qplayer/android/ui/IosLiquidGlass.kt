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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * The app's single Backdrop glass surface.
 *
 * Shared adaptive material, using bounded background thumbnails rather than
 * full-resolution readback. Sampling pauses during dock/expansion motion.
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
    val adaptive = rememberIosAdaptiveGlass(backdrop)
    Box(
        modifier.then(
            iosLiquidGlassModifier(
                backdrop = backdrop,
                dark = dark,
                shape = shape,
                interaction = interaction,
                adaptive = adaptive,
            )
        ),
        // Icons share the glass's measured center at every size, including
        // compact dock controls. Box's default TopStart displaces them.
        contentAlignment = Alignment.Center,
    ) {
        val ink = adaptive.contentColor
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
    adaptive: IosAdaptiveGlassState = rememberIosAdaptiveGlass(backdrop),
): Modifier {
    val tint = LocalGlassTint.current
    val refraction = LocalGlassRefraction.current
    return Modifier
        .then(adaptive.modifier)
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                apiGlassEffects(
                    luminance = adaptive.luminance,
                    refraction = refraction,
                    // Deeper edge displacement on top of the lens pass's centre dome.
                    refractionHeight = 30.dp.toPx(),
                    refractionAmount = 30.dp.toPx(),
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
            innerShadow = { iosGlassInnerShadow(adaptive.luminance <= 0.5f) },
            highlight = { IosGlassHighlight },
            onDrawSurface = {
                drawRect(iosGlassSurface(dark, tint, adaptive.luminance))
            },
        )
        .then(interaction?.modifier ?: Modifier)
        .then(interaction?.gestureModifier ?: Modifier)
}
