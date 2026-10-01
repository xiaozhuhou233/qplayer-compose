package dev.t1m3.qplayer.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Native safe dynamic artwork backdrop. The former WebView/WebGL host was
 * removed from the playback path because vendor WebView GPU implementations
 * could crash while opening or restoring the detail page. The native shader
 * keeps the low-resolution moving artwork, scrim and broad bloom without a JS
 * renderer or a second lifecycle.
 */
@Composable
internal fun SourceDynamicBackdrop(
    image: ImageBitmap,
    lyrics: Boolean,
    dark: Boolean = true,
    modifier: Modifier = Modifier
) {
    LyricDynamicBackdrop(image, dark = dark, modifier = modifier)
}
