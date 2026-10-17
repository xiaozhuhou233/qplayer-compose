package dev.t1m3.qplayer.android.md3eui

import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

fun main(args: Array<String>) {
    val w = 256
    val h = 144
    fun frame(top: Int, bottom: Int, left: Int = 0, right: Int = 0, dark: Boolean = false): IntArray =
        IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            when {
                y < top || y >= h - bottom || x < left || x >= w - right -> 0xFF030303.toInt()
                !dark || (x in 120..144 && y in 60..80) -> 0xFF797979.toInt()
                else -> 0xFF161616.toInt()
            }
        }
    fun near(a: Float, b: Float) = check(abs(a - b) < .0001f) { "$a != $b" }
    val dim = mvLetterbox(frame(26, 26, dark = true), w, h)
    near(dim.top, 26f / h); near(dim.bottom, 26f / h)
    val asymmetric = mvLetterbox(frame(39, 44, 8, 3), w, h)
    near(asymmetric.top, 39f / h); near(asymmetric.bottom, 44f / h)
    near(asymmetric.left, 8f / w); near(asymmetric.right, 3f / w)
    check(mvLetterbox(IntArray(w * h) { 0xFF080808.toInt() }, w, h) == MvLetterbox())
    check(mvLetterbox(frame(0, 0), w, h) == MvLetterbox())
    check(mvLetterbox(IntArray(3), w, h) == MvLetterbox())
    var cases = 0
    for ((vw, vh) in listOf(1928 to 698, 1920 to 1080, 3840 to 1600, 720 to 1280, 640 to 480)) {
        for ((pw, ph) in listOf(1080 to 2400, 1440 to 3200, 2400 to 1080, 1800 to 1800)) {
            for (bars in listOf(MvLetterbox(), dim, asymmetric)) for (pan in listOf(-.18f, 0f, .18f)) {
                val t = checkNotNull(mvFrameTransform(vw, vh, pw, ph, bars, panFraction = pan))
                val sw = t.scaleX * pw; val sh = t.scaleY * ph
                // Uniform scaling in decoded coordinates; no squeeze/stretch.
                near(sw / vw, sh / vh)
                val x0 = t.translateX + bars.left * sw
                val x1 = t.translateX + (1 - bars.right) * sw
                val y0 = t.translateY + bars.top * sh
                val y1 = t.translateY + (1 - bars.bottom) * sh
                check(x0 <= .01f && y0 <= .01f && x1 >= pw - .01f && y1 >= ph - .01f) {
                    "Uncovered edge: $vw x $vh -> $pw x $ph, $bars, $pan: $x0,$y0 .. $x1,$y1"
                }
                cases++
            }
        }
    }
    if (args.isNotEmpty()) {
        val source = ImageIO.read(File(args[0]))
        val sample = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
        sample.createGraphics().apply { drawImage(source, 0, 0, w, h, null); dispose() }
        val found = mvLetterbox(sample.getRGB(0, 0, w, h, null, 0, w), w, h)
        println("User image: ${source.width} x ${source.height}; encoded borders: $found")
    }
    println("PASS: dark/ultra-wide encoded borders, solid-dark rejection, $cases fill/pan/aspect cases")
}
