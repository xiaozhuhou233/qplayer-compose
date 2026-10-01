// AndroidLiquidGlass-android AdaptiveLuminanceGlassContent, Copyright 2025 Kyant,
// Apache-2.0. QPlayer: bounded thumbnails, serialized readback and lifecycle.
package dev.t1m3.qplayer.android.ui

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal val LocalGlassContentColor = compositionLocalOf<Color?> { null }
internal val LocalGlassSamplingEnabled = compositionLocalOf { true }

/** Ⓜ 2026-10-01: 「导航栏、miniplayer 以及搜索组件这一块整体根据此区域整体背景采样，要不然
 *  各个组件自适应各个的有点割裂」. A chrome region publishes ONE sample here; every glass
 *  inside it reads that luminance instead of sampling its own patch of page, so the whole
 *  block adapts as a single piece of material. */
internal val LocalSharedGlassSample = staticCompositionLocalOf<IosAdaptiveGlassState?> { null }

/** The region's shared sample when there is one, else this control's own. */
@Composable
internal fun rememberRegionAdaptiveGlass(backdrop: Backdrop): IosAdaptiveGlassState {
    val shared = LocalSharedGlassSample.current
    return shared ?: rememberIosAdaptiveGlass(backdrop)
}
private val glassReadbackLock = Mutex()
private var lastGlassReadbackMs = 0L
private var smoothedGlassReadbackCostMs = 0f

private suspend fun readGlassSample(glass: IosAdaptiveGlassState, density: Density,
    direction: LayoutDirection): Float? = glassReadbackLock.withLock {
    val remaining = glassReadbackGapMs(smoothedGlassReadbackCostMs) -
        (SystemClock.uptimeMillis() - lastGlassReadbackMs)
    if (remaining > 0L) delay(remaining)
    withFrameNanos { } // no simultaneous readbacks, even on high-refresh devices
    val started = SystemClock.uptimeMillis()
    lastGlassReadbackMs = started
    try {
        glass.sample(density, direction)
    } finally {
        val elapsed = (SystemClock.uptimeMillis() - started).toFloat()
        smoothedGlassReadbackCostMs = smoothedGlassReadbackCostMs * 0.75f + elapsed * 0.25f
    }
}

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
    val modifier = Modifier.onGloballyPositioned { coordinates = it }
    private var reportedFailure = false
    private val pixels = IntArray(GLASS_SAMPLE_MAX_SIDE * GLASS_SAMPLE_MAX_SIDE)

    fun sampleStamp(): GlassSampleStamp? {
        val target = coordinates?.takeIf { it.isAttached } ?: return null
        val source = backdrop as? LayerBackdrop
        if (source != null && (source.samplingRevision == 0L || source.layerCoordinates?.isAttached != true)) return null
        val visible = target.boundsInWindow()
        if (visible.width < 1f || visible.height < 1f ||
            !visible.overlaps(target.findRootCoordinates().boundsInWindow())) return null
        val position = target.positionInWindow()
        return GlassSampleStamp(source?.samplingRevision,
            position.x.roundToInt(), position.y.roundToInt(), target.size.width, target.size.height)
    }

    suspend fun sample(density: Density, direction: LayoutDirection): Float? {
        val target = coordinates?.takeIf { it.isAttached } ?: return null
        val width = target.size.width.toFloat()
        val height = target.size.height.toFloat()
        if (width < 1f || height < 1f) return null
        try {
            // Capture RAW background even when the glass draw node is idle.
            // A pending-draw flag alone could leave its sampled image stale.
            val factor = (GLASS_SAMPLE_MAX_SIDE / maxOf(width, height)).coerceAtMost(1f)
            layer.record(density, direction, IntSize(
                (width * factor).roundToInt().coerceAtLeast(1),
                (height * factor).roundToInt().coerceAtLeast(1),
            )) {
                if (backdrop.isCoordinatesDependent) scale(factor, factor, Offset.Zero) {
                    with(backdrop) { drawBackdrop(density, target, null) }
                } else {
                    // Canvas backdrops already draw into this thumbnail's bounds.
                    with(backdrop) { drawBackdrop(density, target, null) }
                }
            }
            return withContext(Dispatchers.IO) {
                val image = layer.toImageBitmap().asAndroidBitmap()
                val software = if (image.config == Bitmap.Config.HARDWARE)
                    image.copy(Bitmap.Config.ARGB_8888, false) ?: return@withContext null else image
                try {
                    // Reuse one pixel buffer; no per-sample scaled bitmap allocation.
                    software.getPixels(pixels, 0, software.width, 0, 0, software.width, software.height)
                    sampledGlassLuminance(pixels, software.width, software.height)
                } finally {
                    if (software !== image) software.recycle()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (!reportedFailure) Log.w("QPlayerGlass", "Local glass sampling unavailable", failure)
            reportedFailure = true
            return null
        }
    }

    suspend fun animateLuminance(value: Float) = luminanceAnimation.animateTo(value, tween(GLASS_COLOR_DURATION_MS))
    suspend fun animateInk(value: Color) = inkAnimation.animateTo(value, tween(GLASS_INK_DURATION_MS))
}

@Composable
internal fun rememberIosAdaptiveGlass(backdrop: Backdrop): IosAdaptiveGlassState {
    val layer = rememberGraphicsLayer()
    val initial = LocalGlassLuminance.current
    val glass = remember(backdrop, layer) { IosAdaptiveGlassState(backdrop, layer, initial) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val tintKey = LocalGlassTint.current
    val fallback by rememberUpdatedState(initial)
    val samplingEnabled = LocalGlassSamplingEnabled.current
    val motion = LocalGlassMotionActive.current
    LaunchedEffect(glass, lifecycle, density, direction, tintKey, samplingEnabled, motion) {
        if (!samplingEnabled || motion) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val policy = GlassSamplingPolicy()
            var luminanceJob: Job? = null
            var inkJob: Job? = null
            var lastLuminance = Float.NaN
            var lastInk: Color? = null
            while (isActive) {
                val stamp = glass.sampleStamp()
                if (stamp == null || !policy.shouldSample(SystemClock.uptimeMillis(), stamp)) {
                    delay(GLASS_SAMPLE_POLL_MS)
                    continue
                }
                val measured = readGlassSample(glass, density, direction)
                policy.completed(SystemClock.uptimeMillis(), stamp, measured != null)
                val sample = measured ?: fallback
                val quantised = (sample * 24f).roundToInt() / 24f
                if (quantised != lastLuminance) {
                    lastLuminance = quantised
                    luminanceJob?.cancel()
                    luminanceJob = launch { glass.animateLuminance(quantised) }
                }
                // Do not quantise the reference's black/white threshold: #808080
                // is just above 0.5 and must retain black ink, not round to white.
                val ink = glassContentColor(sample)
                if (ink != lastInk) {
                    lastInk = ink
                    inkJob?.cancel()
                    inkJob = launch { glass.animateInk(ink) }
                }
                delay(GLASS_SAMPLE_POLL_MS)
            }
        }
    }
    return glass
}

@Composable
internal fun adaptiveGlassInk(): Color =
    LocalGlassContentColor.current ?: glassContentColor(LocalGlassLuminance.current)
