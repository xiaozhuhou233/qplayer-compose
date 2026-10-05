package dev.t1m3.qplayer.android.md3eui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private val artworkCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}
private val artworkRequests = Semaphore(3)

@Composable
internal fun Artwork(url: String, modifier: Modifier = Modifier, corner: androidx.compose.ui.unit.Dp = 24.dp) {
    val bitmap by produceState<Bitmap?>(null, url) {
        value = null
        if (url.isNotBlank()) value = withContext(Dispatchers.IO) {
            artworkCache.get(url) ?: artworkRequests.withPermit {
                loadArtwork(url)?.also { artworkCache.put(url, it) }
            }
        }
    }
    // Seal AsyncImageImpl crossfade(true): reveal an arriving image over its placeholder.
    val imageAlpha by animateFloatAsState(if (bitmap != null) 1f else 0f,
        animationSpec = tween(100), label = "seal_artwork_crossfade")
    Surface(modifier, shape = RoundedCornerShape(corner), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        val image = bitmap
        Box(contentAlignment = Alignment.Center) {
            if (imageAlpha < 1f) Icon(Md3eIcons.MusicNote, null, Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 1f - imageAlpha))
            if (image != null) Image(image.asImageBitmap(), null, Modifier.matchParentSize(),
                contentScale = ContentScale.Crop, alpha = imageAlpha)
        }
    }
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
    // Always request a thumbnail, including the now-playing full-size cover URL.
    val url = source.substringBefore('?').replace("http://", "https://") + "?param=256y256"
    val connection = try { URL(url).openConnection() as HttpURLConnection } catch (_: Exception) { return null }
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

