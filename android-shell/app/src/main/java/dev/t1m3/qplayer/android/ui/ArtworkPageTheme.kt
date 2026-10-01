package dev.t1m3.qplayer.android.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val LocalArtworkPage = staticCompositionLocalOf { false }

private data class ArtworkSample(val seed: Color, val frost: ImageBitmap)
// Only tiny, pre-blurred textures are retained, never full-size artwork.
private val artworkSamples = LruCache<String, ArtworkSample>(24)

@Composable
private fun rememberArtworkSample(image: ImageBitmap?, sourceKey: String): ArtworkSample? {
    val cacheKey = remember(image, sourceKey) {
        if (image == null) null else "$sourceKey:${image.asAndroidBitmap().generationId}"
    }
    var sample by remember(cacheKey) {
        mutableStateOf(cacheKey?.let { synchronized(artworkSamples) { artworkSamples.get(it) } })
    }
    LaunchedEffect(image, cacheKey) {
        if (image == null || cacheKey == null || sample != null) return@LaunchedEffect
        val result = withContext(Dispatchers.Default) {
            sampleArtwork(image)?.also { synchronized(artworkSamples) { artworkSamples.put(cacheKey, it) } }
        }
        sample = result
    }
    return sample
}

/** A page owns its colours; playback, scrolling and timers never resample the image. */
@Composable
internal fun ArtworkPageSurface(
    image: ImageBitmap?,
    sourceKey: String,
    dark: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!enabled) { Box(modifier, content = content); return }
    // Ⓜ The listener: 「歌单界面和歌手界面的动态专辑封面取色仅用于 ios 模式，md3 模式依然保持原本的
    // 当前播放歌曲取色」. In MD3 the route keeps the app's own theme — which is already tinted from
    // the currently playing song — so neither the artwork scheme nor the frost texture is applied
    // here, and LocalArtworkPage is not raised (the Scaffold's transparent-container branch keys
    // on it, and MD3 wants its normal background back).
    val ios = LocalIosDesign.current
    if (!ios) {
        Box(modifier, content = content); return
    }
    val sample = rememberArtworkSample(image, sourceKey)
    val base = MaterialTheme.colorScheme
    val target = remember(sample?.seed, base, dark) {
        sample?.let { artworkPageScheme(base, it.seed, dark) } ?: base.withReadableContent()
    }
    val scheme = rememberAnimatedQPlayerColorScheme(target).withReadableContent()
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalContentColor provides scheme.onBackground,
            LocalGlassTint provides scheme.background,
            LocalGlassLuminance provides scheme.background.luminance(),
            LocalArtworkPage provides true,
        ) {
            Box(modifier.background(scheme.background)) {
                sample?.let {
                    // A CPU-preblurred 40px texture is enough for broad frosted colour
                    // variation, including on Android 8–11. No full-screen blur pass.
                    Image(it.frost, null, Modifier.matchParentSize().alpha(0.08f),
                        contentScale = ContentScale.Crop)
                }
                content()
            }
        }
    }
}

internal fun artworkPageScheme(base: ColorScheme, seed: Color, dark: Boolean): ColorScheme {
    // Ⓜ The listener: 「专辑和歌手界面的动态取色被磨砂改的太暗淡了」 — and the measurement is this
    // line: the artwork's own colour was mixed in at only 22% (dark) / 18% (light), so the page
    // read as near-black or near-white with a hint of the cover rather than AS the cover. The
    // weights are raised to 38% / 32%, which lets the extracted colour actually name the page
    // while the mix's other end still keeps text legible. (The frost that sits on top is a
    // preblurred 40px texture at 8% alpha — it was never what dimmed this.)
    val background = lerp(if (dark) Color(0xFF101114) else Color(0xFFF9F9FA),
        seed, if (dark) 0.38f else 0.32f)
    fun surface(amount: Float) = lerp(background, if (dark) Color.White else Color.Black, amount)
    val container = surface(0.04f)
    return base.copy(
        background = background, surface = background, surfaceTint = Color.Transparent,
        surfaceContainerLowest = background, surfaceContainerLow = surface(0.018f),
        surfaceContainer = container, surfaceContainerHigh = surface(0.065f),
        surfaceContainerHighest = surface(0.09f), surfaceVariant = container,
        surfaceBright = surface(0.06f), surfaceDim = background,
        primaryContainer = surface(0.085f), secondaryContainer = surface(0.065f),
        tertiaryContainer = surface(0.085f),
        outline = readableInk(background).copy(alpha = 0.55f),
        outlineVariant = readableInk(background).copy(alpha = 0.15f),
    ).withReadableContent()
}

/** Population-based colour extraction + three box passes approximating a Gaussian. */
private fun sampleArtwork(image: ImageBitmap): ArtworkSample? {
    val source = image.asAndroidBitmap()
    if (source.isRecycled) return null
    val size = 40
    // Software Canvas cannot draw a hardware bitmap; copy only for that case.
    val readable = if (source.config == Bitmap.Config.HARDWARE)
        source.copy(Bitmap.Config.ARGB_8888, false) ?: return null else source
    val small = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    try {
        android.graphics.Canvas(small).drawBitmap(readable, null,
            android.graphics.Rect(0, 0, size, size), android.graphics.Paint(3))
        val pixels = IntArray(size * size)
        small.getPixels(pixels, 0, size, 0, 0, size, size)
        val counts = IntArray(512)
        val reds = IntArray(512); val greens = IntArray(512); val blues = IntArray(512)
        val satSum = IntArray(512)
        for (pixel in pixels) {
            if ((pixel ushr 24) < 128) continue
            val r = (pixel ushr 16) and 255; val g = (pixel ushr 8) and 255; val b = pixel and 255
            val bin = ((r shr 5) shl 6) or ((g shr 5) shl 3) or (b shr 5)
            counts[bin]++; reds[bin] += r; greens[bin] += g; blues[bin] += b
            // Ⓜ Saturation is accumulated per bin as well, because the seed used to be the most
            // POPULATED bucket — which on most covers is a background or a neutral, i.e. grey,
            // and the listener's 「现在只能取到灰色」 is exactly that. The winner is now the
            // population weighted by the bucket's own mean saturation (+16 so a truly
            // black-and-white cover still resolves to its dominant grey rather than to nothing).
            satSum[bin] += maxOf(r, g, b) - minOf(r, g, b)
        }
        val bin = counts.indices.maxByOrNull {
            counts[it] * (satSum[it] / counts[it].coerceAtLeast(1) + 16)
        } ?: return null
        val count = counts[bin]
        if (count == 0) return null
        val seed = Color(android.graphics.Color.rgb(reds[bin] / count, greens[bin] / count, blues[bin] / count))
        var data = pixels
        repeat(3) {
            for (horizontal in listOf(true, false)) {
                val output = IntArray(data.size)
                for (y in 0 until size) for (x in 0 until size) {
                    var r = 0; var g = 0; var b = 0
                    for (delta in -6..6) {
                        val px = if (horizontal) (x + delta).coerceIn(0, size - 1) else x
                        val py = if (horizontal) y else (y + delta).coerceIn(0, size - 1)
                        val color = data[py * size + px]
                        r += (color ushr 16) and 255; g += (color ushr 8) and 255; b += color and 255
                    }
                    output[y * size + x] = android.graphics.Color.rgb(r / 13, g / 13, b / 13)
                }
                data = output
            }
        }
        return ArtworkSample(seed, Bitmap.createBitmap(data, size, size, Bitmap.Config.ARGB_8888).asImageBitmap())
    } finally {
        small.recycle()
        if (readable !== source) readable.recycle()
    }
}
