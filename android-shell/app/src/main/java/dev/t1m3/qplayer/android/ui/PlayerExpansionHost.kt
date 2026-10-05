package dev.t1m3.qplayer.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import dev.t1m3.qplayer.android.ui.motion.EmphasizeEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/** One reversible clock for the container, its contents and the navigation. */
@Stable
internal class PlayerExpansionState(val miniLayer: GraphicsLayer) {
    val progress = Animatable(0f)
    val settle = Animatable(0f)
    var expanded by mutableStateOf(false)
    var miniBounds by mutableStateOf(Rect.Zero)
    var flightBounds by mutableStateOf(Rect.Zero)
    var hostOrigin by mutableStateOf(Offset.Zero)
    var hostSize by mutableStateOf(IntSize.Zero)
    var refreshSnapshot by mutableStateOf(false)
    var hasSnapshot by mutableStateOf(false)
    val active: Boolean by derivedStateOf { expanded || progress.value > 0f }

    fun prepareOpen() {
        if (!active) flightBounds = miniBounds
        refreshSnapshot = true
        expanded = true
    }

    fun source(size: Size, density: Float): Rect {
        val source = flightBounds
        if (source.width > 0f && source.height > 0f) {
            val width = source.width.coerceAtMost(size.width)
            val height = source.height.coerceAtMost(size.height)
            val left = (source.left - hostOrigin.x).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
            val top = (source.top - hostOrigin.y).coerceIn(0f, (size.height - height).coerceAtLeast(0f))
            return Rect(left, top, left + width, top + height)
        }
        val inset = (20f * density).coerceAtMost(size.width / 4f)
        val bottom = (size.height - 84f * density).coerceAtLeast(0f)
        return Rect(inset, (bottom - 52f * density).coerceAtLeast(0f), size.width - inset, bottom)
    }

    fun bounds(size: Size, density: Float): Rect {
        val start = source(size, density)
        val p = progress.value
        return Rect(lerp(start.left, 0f, p), lerp(start.top, 0f, p),
            lerp(start.right, size.width, p), lerp(start.bottom, size.height, p))
    }
}

@Composable
internal fun rememberPlayerExpansionState(): PlayerExpansionState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { PlayerExpansionState(layer) }
}

/** Record the MiniPlayer only when a transition needs its snapshot. */
internal fun Modifier.playerExpansionSource(state: PlayerExpansionState): Modifier =
    onGloballyPositioned { coordinates ->
        if (!state.active) state.miniBounds = coordinates.boundsInRoot()
    }.drawWithContent {
        if (state.refreshSnapshot) {
            state.miniLayer.record { this@drawWithContent.drawContent() }
            state.hasSnapshot = true
            state.refreshSnapshot = false
        }
        if (!state.active) drawContent()
    }

/** Slides dock chrome offscreen while keeping its layout/source anchor alive. */
internal fun Modifier.playerChromeExit(state: PlayerExpansionState): Modifier = graphicsLayer {
    val p = state.progress.value
    translationY = (size.height + 40.dp.toPx()) * p
    alpha = 1f - p
}

// An immutable outline per frame is intentional: a single Shape closing over
// mutable bounds can be cached by RenderNode and leave the page clipped to the
// first (MiniPlayer-sized) rectangle throughout the entire animation.
private data class PlayerRevealShape(val bounds: Rect, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(bounds, CornerRadius(radius)))
}

@Composable
internal fun PlayerExpansionHost(
    expanded: Boolean,
    state: PlayerExpansionState,
    durationMillis: Int,
    simpleSlide: Boolean = false,
    content: @Composable (isDetail: Boolean) -> Unit,
) {
    if (simpleSlide) {
        LaunchedEffect(state) {
            state.expanded = false
            state.progress.snapTo(0f)
            state.settle.snapTo(0f)
        }
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalUiRenderingActive provides !expanded) {
                content(false)
            }
            AnimatedVisibility(
                visible = expanded,
                enter = slideInVertically(tween(250), initialOffsetY = { it }) + fadeIn(tween(180)),
                exit = slideOutVertically(tween(250), targetOffsetY = { it }) + fadeOut(tween(180)),
            ) {
                content(true)
            }
        }
        return
    }
    val density = LocalDensity.current
    LaunchedEffect(expanded, durationMillis) {
        state.settle.snapTo(0f)
        if (expanded) {
            if (!state.expanded) state.prepareOpen()
        } else {
            // Refresh the source before closing: playback may have advanced
            // while detail was open, so never fly back into an old song cover.
            if (state.active) state.refreshSnapshot = true
            state.expanded = false
        }
        // A fast reversal continues at the current bounds; never reset to 0/1.
        if (durationMillis == 0) {
            state.progress.snapTo(if (expanded) 1f else 0f)
        } else {
            state.progress.animateTo(if (expanded) 1f else 0f,
                tween(durationMillis, easing = EmphasizeEasing))
            if (expanded) state.settle.animateTo(0f,
                spring(dampingRatio = 0.68f, stiffness = 500f), initialVelocity = 38f)
        }
    }
    Box(Modifier.fillMaxSize().onSizeChanged { state.hostSize = it }
        .onGloballyPositioned { state.hostOrigin = it.positionInRoot() }) {
        // Keep the base composition/scroll state. It must not become the LYRICS
        // route, which used to unmount the dock in the first animation frame.
        Box(Modifier.fillMaxSize()
            .then(if (expanded || state.active) Modifier.clearAndSetSemantics {} else Modifier)) {
            val baseVisible by remember(state) {
                // The outer progress ring must be present in the exit snapshot.
                derivedStateOf { state.refreshSnapshot || !state.active }
            }
            CompositionLocalProvider(LocalUiRenderingActive provides baseVisible) {
                content(false)
            }
        }
        if (expanded || state.active) {
            Box(Modifier.fillMaxSize()
                .graphicsLayer {
                    // Read the clock here, so outline invalidation stays in
                    // rendering rather than remeasuring the detail/WebView.
                    val p = state.progress.value
                    shape = if (p >= 1f) androidx.compose.ui.graphics.RectangleShape else PlayerRevealShape(
                        state.bounds(size, density.density),
                        state.source(size, density.density).height * 0.5f * (1f - p)
                    )
                    clip = true
                    // A sub-dp settle on the container only: artwork, controls and
                    // background stay together. Reversal cancels this spring.
                    val settlePx = state.settle.value.dp.toPx()
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleX = 1f + settlePx / size.width.coerceAtLeast(1f)
                    scaleY = 1f + settlePx / size.height.coerceAtLeast(1f)
                }
                .pointerInput(Unit) {
                    // Stop touches reaching the underlying dock, without
                    // stealing the detail page's gestures or button events.
                    awaitEachGesture {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            // Merely participate in hit testing; consuming the
                            // Final pass cancels child clicks after finger jitter.
                        } while (event.changes.any { it.pressed })
                    }
                }) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    val bounds = state.bounds(size, density.density)
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = bounds.left
                    translationY = bounds.top
                    scaleX = if (size.width > 0f) bounds.width / size.width else 1f
                    // Reveal within the growing container without vertically
                    // squashing its artwork, text or controls. Layout stays fixed.
                    scaleY = if (size.height > 0f) bounds.height / size.height else 1f
                    alpha = ((state.progress.value - 0.06f) / 0.48f).coerceIn(0f, 1f)
                }) { content(true) }
                val sourceWidth = state.flightBounds.width
                val sourceHeight = state.flightBounds.height
                if (state.hasSnapshot && sourceWidth > 0f && sourceHeight > 0f) {
                    Box(Modifier.size(with(density) { sourceWidth.toDp() }, with(density) { sourceHeight.toDp() })
                        .clearAndSetSemantics {}
                        .graphicsLayer {
                            val hostSize = Size(state.hostSize.width.toFloat(), state.hostSize.height.toFloat())
                            val bounds = state.bounds(hostSize, density.density)
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationX = bounds.left
                            scaleX = bounds.width / sourceWidth
                            scaleY = scaleX
                            translationY = bounds.center.y - sourceHeight * scaleY * 0.5f
                            alpha = (1f - (state.progress.value - 0.06f) / 0.48f).coerceIn(0f, 1f)
                        }.drawWithContent { drawLayer(state.miniLayer) })
                }
            }
        }
    }
}
