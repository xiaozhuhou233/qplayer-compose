// AndroidLiquidGlass-android AdaptiveLuminanceGlassContent, Copyright 2025 Kyant,
// Apache-2.0. QPlayer adaptations: local 5x5 GPU crop, bounded readback and lifecycle.
package dev.t1m3.qplayer.android.ui

import android.graphics.Bitmap
import android.util.Log
import java.nio.IntBuffer
import kotlin.math.roundToInt
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal val LocalGlassContentColor = compositionLocalOf<Color?> { null }
internal val LocalGlassSamplingEnabled = compositionLocalOf { true }

// At most one tiny readback starts in each display frame, across ALL controls.
private val glassReadbackLock = Mutex()

@Stable
internal class IosAdaptiveGlassState(
    private val backdrop: Backdrop,
    private val layer: GraphicsLayer,
    initialLuminance: Float,
) {
    private val luminanceAnimation = Animatable(initialLuminance)
    private val inkAnimation = Animatable(glassContentColor(initialLuminance))
    val luminance: Float get() = luminanceAnimation.value
    val contentColor: Color get() = inkAnimation.value
    private var coordinates: LayoutCoordinates? = null
    val modifier: Modifier = Modifier.onGloballyPositioned { coordinates = it }
    private var reportedFailure = false

    /** Set by the sampling loop before it reads; the next draw then records a fresh
     *  thumbnail. Between beats — including every animation frame — `record` is a no-op. */
    @Volatile
    private var samplePending = false

    fun requestSample() {
        samplePending = true
    }

    /** Ⓜ The demo's `onDrawBackdrop` half, verbatim: record the backdrop — and because
     *  {@code LayerBackdrop} draws it through the inverse of the glass's own transform plus the
     *  glass's own offset, what lands here is exactly the pixels behind this control, at this
     *  control's own bounds. The demo hangs the same two lines off the glass's own draw pass
     *  (`onDrawBackdrop = { drawBackdrop -> drawBackdrop(); layer.record { drawBackdrop() } }`),
     *  which is the difference from this app's first version: it drew the backdrop a SECOND time
     *  into a 5x5 layer and rewrote `drawContext.size` to fake the crop. This costs no extra
     *  backdrop draw and needs no fake size.
     *
     *  <p>Ⓜ 2026-10-01 jank fix: the layer exists ONLY for {@link #sample()}, one 5×5 average per
     *  beat. Recording the full backdrop into it on every draw doubled every glass surface's
     *  fill rate during animations for nothing. It now records only when the sampling loop has
     *  asked for a fresh read ({@link #requestSample()}), and at ≤64px on the long side — the
     *  readback then touches a thumbnail instead of a control-sized texture. The offset the
     *  backdrop draws at comes from layout coordinates, not the canvas size, so scaling the
     *  canvas minifies the same region instead of moving it. */
    fun record(density: Density, direction: LayoutDirection, draw: DrawScope.() -> Unit) {
        val target = coordinates?.takeIf { it.isAttached } ?: return
        if (!samplePending) return
        samplePending = false
        val width = target.size.width.toFloat()
        val height = target.size.height.toFloat()
        if (width < 1f || height < 1f) return
        val scale = (GLASS_SAMPLE_MAX_SIDE / maxOf(width, height)).coerceAtMost(1f)
        layer.record(
            density, direction,
            IntSize((width * scale).roundToInt().coerceAtLeast(1),
                (height * scale).roundToInt().coerceAtLeast(1))
        ) {
            scale(scale, scale, Offset.Zero) { draw() }
        }
    }

    /** Read the raw background this control recorded, never its text/highlight/veil.
     *
     *  <p>The demo's own chain, verbatim: `toImageBitmap().asAndroidBitmap()` →
     *  `scale(GLASS_SAMPLE_SIDE, GLASS_SAMPLE_SIDE, false)` (nearest, no filter) →
     *  `copy(ARGB_8888, false)` → `copyPixelsToBuffer` → the Rec.709 average of those 25 pixels.
     *  Only the hardware-bitmap guard is this app's own: a `HARDWARE` bitmap cannot be scaled or
     *  read on the CPU, so it is copied once first. */
    suspend fun sample(): Float? = withContext(Dispatchers.IO) {
        if (coordinates?.isAttached != true) return@withContext null
        try {
            val image = layer.toImageBitmap().asAndroidBitmap()
            val software = if (image.config == Bitmap.Config.HARDWARE)
                image.copy(Bitmap.Config.ARGB_8888, false) ?: return@withContext null else image
            try {
                // The demo's `scale(5, 5, false)` — `Bitmap.scale` is `createScaledBitmap`
                // with the same arguments, and `filter = false` is the nearest-neighbour
                // downscale the 5x5 sample is defined by.
                val thumbnail = Bitmap.createScaledBitmap(software, GLASS_SAMPLE_SIDE,
                    GLASS_SAMPLE_SIDE, false)
                val buffer = IntBuffer.allocate(GLASS_SAMPLE_SIDE * GLASS_SAMPLE_SIDE)
                thumbnail.copyPixelsToBuffer(buffer)
                val pixels = GLASS_SAMPLE_SIDE * GLASS_SAMPLE_SIDE
                var sum = 0.0
                for (index in 0 until pixels) {
                    val color = buffer.get(index)
                    val r = (color shr 16 and 0xFF) / 255f
                    val g = (color shr 8 and 0xFF) / 255f
                    val b = (color and 0xFF) / 255f
                    sum += 0.2126 * r + 0.7152 * g + 0.0722 * b
                }
                (sum / pixels).toFloat()
            } finally {
                // The source is owned by Compose; only recycle our own copy.
                if (software !== image) software.recycle()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (!reportedFailure) Log.w("QPlayerGlass", "Local glass sampling unavailable", failure)
            reportedFailure = true
            null
        }
    }

    suspend fun animateLuminance(value: Float) =
        luminanceAnimation.animateTo(value, tween(GLASS_COLOR_DURATION_MS))

    suspend fun animateInk(value: Color) =
        inkAnimation.animateTo(value, tween(GLASS_COLOR_DURATION_MS))
}

@Composable
internal fun rememberIosAdaptiveGlass(backdrop: Backdrop): IosAdaptiveGlassState {
    val layer = rememberGraphicsLayer()
    val initial = LocalGlassLuminance.current
    val glass = remember(backdrop, layer) { IosAdaptiveGlassState(backdrop, layer, initial) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    // Ⓜ The listener: 「导航栏外围无法正确进行颜色自适应并马上应用」. The loop below samples on a
    // 500/1000ms beat, which is what "not immediately" is: a page or cover change could sit
    // unsampled for up to a second. `tint` is the per-page/per-track colour the app already
    // publishes (LocalGlassTint), so reading it HERE — in composition, not through
    // rememberUpdatedState — makes a change recompose this composable and restart the effect,
    // whose first iteration samples before it ever reaches its delay. The periodic beat stays as
    // the fallback for anything that moves without changing the tint (scrolling artwork).
    val tintKey = LocalGlassTint.current
    val fallback by rememberUpdatedState(LocalGlassTint.current)
    val samplingEnabled by rememberUpdatedState(LocalGlassSamplingEnabled.current)
    val motion by rememberUpdatedState(LocalGlassMotionActive.current)
    LaunchedEffect(glass, lifecycle, density, direction, tintKey) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var luminanceJob: Job? = null
            var inkJob: Job? = null
            var lastLuminance = Float.NaN
            var lastInk: Color? = null
            while (isActive) {
                // Ⓜ Skip the readback entirely while the surfaces move: a GPU readback
                // mid-animation stalls the render thread, which is exactly the frame
                // drop the listener sees. The tint catches up one beat after the motion
                // ends — the pause is invisible.
                if (samplingEnabled && !motion) {
                    glass.requestSample()
                    val sample = glassReadbackLock.withLock {
                        withFrameNanos { } // stagger components rather than burst GPU readbacks
                        glass.sample()
                    }
                    if (sample != null) {
                        // Quantise to 1/24 stops before comparing: an unquantised 5×5
                        // average drifts by a least-significant bit on every scroll
                        // tick, which restarted the tween (and so re-applied every GPU
                        // effect) sixty times a second while the page moved. A stop is
                        // ~0.25dp of blur and ~0.002 of surface alpha — invisible.
                        val quantised = (sample * 24f).roundToInt() / 24f
                        if (quantised != lastLuminance) {
                            lastLuminance = quantised
                            luminanceJob?.cancel()
                            luminanceJob = launch { glass.animateLuminance(quantised) }
                        }
                        val ink = glassContentColor(quantised)
                        if (ink != lastInk) {
                            lastInk = ink
                            inkJob?.cancel()
                            inkJob = launch { glass.animateInk(ink) }
                        }
                    }
                }
                delay(if (motion) 1000L else 500L)
            }
        }
    }
    return glass
}

@Composable
internal fun adaptiveGlassInk(): Color =
    LocalGlassContentColor.current ?: glassContentColor(LocalGlassLuminance.current)
