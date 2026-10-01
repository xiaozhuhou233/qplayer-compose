package dev.t1m3.qplayer.android.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.viewinterop.AndroidView

// Port of the three rotating artwork layers in ui.zip/dynamic_background.js.
// Pre-sampling the artwork at low resolution provides its broad blur without
// blurring the lyric text or allocating a full-screen offscreen buffer per frame.
private const val LYRIC_BACKDROP_SHADER = """
uniform shader artwork;
uniform float2 resolution;
uniform float2 artworkSize;
uniform float time;
uniform float darkOverlay;

half3 sampleArtwork(float2 coordinate) {
    return artwork.eval(coordinate * artworkSize).rgb;
}

half4 layer(float2 uv, float2 center, float size, float angle, float scrim) {
    float2 position = uv - center;
    float c = cos(angle);
    float s = sin(angle);
    float2 rotated = float2(position.x * c - position.y * s,
                            position.x * s + position.y * c);
    if (abs(rotated.x) > size * 0.5 || abs(rotated.y) > size * 0.5) {
        return half4(0.0);
    }
    half3 color = sampleArtwork(rotated / size - 0.5);
    color = mix(color, half3(0.0), half(scrim));
    color = mix(half3(0.5), color, half(1.08));
    return half4(color, 1.0);
}

half4 main(float2 position) {
    float2 coordinate = position / resolution;
    float2 centered = (coordinate - 0.5) * 2.0;
    // The source morphs a 9x9 mesh. Keep the same slow, low-amplitude
    // displacement here so the cover layers move without sharp edges.
    centered += float2(sin(centered.y * 2.0 + time * 0.19),
                       cos(centered.x * 2.0 - time * 0.16)) * 0.026;
    if (resolution.x >= resolution.y) {
        centered.y *= resolution.y / resolution.x;
    } else {
        centered.x *= resolution.x / resolution.y;
    }
    float angle0 = time * 6.2831853 / 120.0;
    float angle1 = time * 6.2831853 / 70.0;
    float angle2 = time * 6.2831853 / 90.0 + 3.1415926;
    half4 first = layer(centered, float2(0.0), 2.8, angle0, 0.35);
    half4 second = layer(centered, float2(-0.25, 0.15), 1.4, angle2, 0.3652);
    half4 third = layer(centered,
        float2(0.1 + cos(-angle1 * 0.5), sin(-angle1 * 0.5)),
        1.4, angle1, 0.3576);
    half3 color = first.rgb;
    color = mix(color, second.rgb, second.a);
    color = mix(color, third.rgb, third.a);
    color = mix(color, half3(1.0), half(0.095));
    return half4(color * half(1.0 - darkOverlay), 1.0);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class LyricDynamicBackdropView(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shader = runCatching { RuntimeShader(LYRIC_BACKDROP_SHADER) }.getOrNull()
    private var artwork: ImageBitmap? = null
    private var reducedArtwork: Bitmap? = null
    private val startedAt = SystemClock.uptimeMillis()
    private var running = true

    init {
        // ui.zip performs a broad two-pass blur after the animated mesh. A
        // native RenderEffect gives the same soft bloom on Android without
        // allocating extra full-screen Compose layers each frame.
        //
        // Ⓜ The listener, on the playback detail page's dynamic-background mode:
        // 「播放详情界面的动态背景模式下的那层磨砂模糊变得再强几倍，让人只能看到光晕」 — so the
        // radius is ~3x the 52f this started at. At 150f none of the artwork is legible any
        // more and what is left of it is its colour, as a glow, which is what that mode is
        // for. (150f is also the practical ceiling for a blur this wide: past it the render
        // engine clamps and the cost climbs without the picture changing.)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRenderEffect(RenderEffect.createBlurEffect(150f, 150f, Shader.TileMode.MIRROR))
        }
    }

    fun update(image: ImageBitmap?, dark: Boolean) {
        if (artwork !== image) {
            artwork = image
            reducedArtwork?.recycle()
            reducedArtwork = image?.asAndroidBitmap()?.let { source ->
                Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also { target ->
                    Canvas(target).drawBitmap(
                        source,
                        null,
                        Rect(0, 0, target.width, target.height),
                        Paint(Paint.FILTER_BITMAP_FLAG)
                    )
                }
            }
            reducedArtwork?.let { bitmap ->
                shader?.setInputShader(
                    "artwork",
                    BitmapShader(bitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
                )
                shader?.setFloatUniform("artworkSize", bitmap.width.toFloat(), bitmap.height.toFloat())
            }
        }
        shader?.setFloatUniform("darkOverlay", if (dark) 0.39f else 0.10f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (shader != null && reducedArtwork != null && width > 0 && height > 0) {
            shader.setFloatUniform("resolution", width.toFloat(), height.toFloat())
            shader.setFloatUniform("time", (SystemClock.uptimeMillis() - startedAt) * (0.13f / 60f))
            paint.shader = shader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            if (running && isShown) postInvalidateDelayed(50L)
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        running = visibility == VISIBLE
        if (running) invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        reducedArtwork?.recycle()
        reducedArtwork = null
        artwork = null
    }
}

@Composable
internal fun LyricDynamicBackdrop(image: ImageBitmap?, dark: Boolean, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { LyricDynamicBackdropView(it) },
        update = { it.update(image, dark) },
        modifier = modifier
    )
}
