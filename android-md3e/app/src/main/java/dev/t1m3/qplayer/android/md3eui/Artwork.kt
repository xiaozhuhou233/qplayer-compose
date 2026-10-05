package dev.t1m3.qplayer.android.md3eui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI

private val artworkCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}
private val artworkRequests = Semaphore(3)
private val artworkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val artworkInFlight = mutableMapOf<String, Deferred<Bitmap?>>()

@Composable
internal fun Artwork(url: String, modifier: Modifier = Modifier, corner: androidx.compose.ui.unit.Dp = 24.dp) {
    val source = remember(url) { artworkRequestSource(url) }
    // Read memory before the first draw. A new destination composition must display
    // the same cached cover immediately, without a placeholder frame or another fade.
    var bitmap by remember(source) { mutableStateOf(artworkCache.get(source)) }
    LaunchedEffect(source) {
        if (source.isNotBlank() && bitmap == null) bitmap = awaitArtwork(source)
    }
    // Seal AsyncImageImpl crossfade(true): reveal an arriving image over its placeholder.
    val imageAlpha = animateFloatAsState(if (bitmap != null) 1f else 0f,
        animationSpec = tween(if (Md3eLowSpecMode.current || !LocalMd3eRenderingActive.current ||
            LocalMd3eMotionActive.current) 0 else 100), label = "seal_artwork_crossfade")
    Surface(modifier, shape = RoundedCornerShape(corner), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        val image = bitmap
        Box(contentAlignment = Alignment.Center) {
            if (bitmap == null) Icon(Md3eIcons.MusicNote, null, Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary)
            if (image != null) {
                val imageBitmap = remember(image) { image.asImageBitmap() }
                Image(imageBitmap, null, Modifier.matchParentSize().graphicsLayer {
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                    alpha = imageAlpha.value
                }, contentScale = ContentScale.Crop)
            }
        }
    }
}

/** A list thumbnail and its detail cover share one download and decoded bitmap. */
internal fun artworkRequestSource(source: String): String {
    if (!source.startsWith("https://") && !source.startsWith("http://")) return source.removePrefix("file://")
    val uri = runCatching { URI(source) }.getOrNull() ?: return source
    val host = uri.host.orEmpty().lowercase()
    if (host != "music.126.net" && !host.endsWith(".music.126.net")) return source
    val query = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() && it.substringBefore('=') != "param" }
    val suffix = (query + "param=256y256").joinToString("&")
    return "https://${uri.rawAuthority}${uri.rawPath}?$suffix"
}

internal suspend fun awaitArtwork(source: String): Bitmap? {
    artworkCache.get(source)?.let { return it }
    val request = synchronized(artworkInFlight) {
        artworkInFlight[source] ?: artworkScope.async(start = CoroutineStart.LAZY) {
            artworkRequests.withPermit {
                artworkCache.get(source) ?: loadArtwork(source)?.also { artworkCache.put(source, it) }
            }
        }.also { deferred ->
            artworkInFlight[source] = deferred
            deferred.invokeOnCompletion {
                synchronized(artworkInFlight) {
                    if (artworkInFlight[source] === deferred) artworkInFlight.remove(source)
                }
            }
        }
    }
    return request.await()
}

private fun loadArtwork(source: String): Bitmap? {
    if (!source.startsWith("https://") && !source.startsWith("http://")) {
        val file = File(source.removePrefix("file://"))
        if (!file.isFile) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 512) inSampleSize *= 2
            }
            BitmapFactory.decodeFile(file.path, options)
        } catch (_: Exception) { null }
    }
    // Only NetEase URLs have been normalized above. Other providers' query strings
    // may contain signatures and must survive unchanged.
    val connection = try { URL(source).openConnection() as HttpURLConnection } catch (_: Exception) { return null }
    return try {
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.inputStream.use { stream ->
            // Bounds both download and decode if a CDN ignores its thumbnail parameter.
            val bytes = stream.readBytesBounded(2 * 1024 * 1024) ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 512) inSampleSize *= 2
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    } catch (_: Exception) { null } finally { connection.disconnect() }
}

private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray? {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) return output.toByteArray()
        if (output.size() + count > limit) return null
        output.write(buffer, 0, count)
    }
}


/** Blurred backgrounds need a soft texture, not a full-resolution offscreen buffer. */
@Composable
internal fun Md3eSoftArtworkBackdrop(cover: String, modifier: Modifier = Modifier,
    radius: androidx.compose.ui.unit.Dp = 72.dp) {
    BoxWithConstraints(modifier.clipToBounds()) {
        Artwork(cover, Modifier.size(maxWidth / 4, maxHeight / 4).graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = 4f
            scaleY = 4f
        }.blur(radius / 4), corner = 0.dp)
    }
}
