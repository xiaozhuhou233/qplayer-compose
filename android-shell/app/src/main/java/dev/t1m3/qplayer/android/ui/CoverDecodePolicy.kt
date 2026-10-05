package dev.t1m3.qplayer.android.ui

/** BitmapFactory rounds sampling to powers of two. Bound decoded pixels before allocation. */
internal fun coverDecodeSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    require(maxEdge > 0)
    val edge = maxOf(width, height).coerceAtLeast(1)
    var sample = 1
    while ((edge.toLong() + sample - 1L) / sample > maxEdge && sample < (1 shl 30)) {
        sample *= 2
    }
    return sample
}
