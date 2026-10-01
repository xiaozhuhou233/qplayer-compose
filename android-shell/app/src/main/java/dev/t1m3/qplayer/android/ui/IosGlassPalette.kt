// AndroidLiquidGlass-android colour/effect adaptation, Copyright 2025 Kyant,
// Apache-2.0. QPlayer uses each control's sampled luminance in the Android demo formula.
package dev.t1m3.qplayer.android.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.shadow.Shadow

/**
 * Ⓜ The shadow every liquid glass in this app draws under itself, so a control stays legible
 * over a busy artwork instead of dissolving into it.
 *
 * The library's own default ({@code Shadow.Default}) is Black at 0.1 alpha with a 24dp radius,
 * which over a bright cover is barely there — the listener's report was exactly that:
 * 「给所有液态玻璃下面组件覆盖上阴影保持可视化」. This is the same shape, harder: three times the
 * alpha and a little more radius and drop. It is one value shared by every glass call site on
 * purpose — the plates, the buttons, the tabs and the dialogs have to read as ONE material.
 */
/**
 * Ⓜ The shadow every glass in this app casts, and the listener's own instruction for it:
 * 「让下部分组件覆盖更深的阴影……其他按钮也这样，注意不只是在里面填充阴影」.
 *
 * <p>Three things that sentence fixes, and all three are properties of THIS value rather than of a
 * fill or a stroke:
 * <ul>
 *   <li><b>Deeper.</b> Black at 0.30 alpha, against the 0.04 the faint pair used — the point is a
 *       shadow you can see on a white page, not a hint of one;</li>
 *   <li><b>Below.</b> The offset is pushed DOWN (14dp on top of the radius), so what the page
 *       receives is a dark cast under the component's lower edge rather than an even halo around
 *       it;</li>
 *   <li><b>Outside, following the outline.</b> It is the library's own {@code Shadow}, drawn by
 *       {@code drawBackdrop} BEHIND the glass with the component's shape — so it is never painted
 *       inside the material, and because the shape is evaluated as the component draws, a bar that
 *       expands or collapses changes the shadow's own extent with it. That is the 「组件扩展收缩自动
 *       改外围范围」 half of the same instruction.</li>
 * </ul>
 *
 * <p>It is one value shared by every glass call site (the dock's two capsules, the round buttons,
 * the dialogs), which is what makes 「其他按钮也这样」 true without a per-site number to drift.
 */
internal val IosGlassShadow = Shadow(
    radius = 16.dp,
    offset = DpOffset(0.dp, 3.dp),
    color = Color.Black.copy(alpha = 0.14f),
)

/**
 * Ⓜ The contact half of that pair — a 6dp shadow at 6% alpha, drawn by the host where the glass
 * is placed (see {@link IosLiquidGlass}). Two very diffuse layers are what make the glass read as
 * floating a few millimetres above the page instead of lying on it; one shadow at any strength
 * reads as a hard edge on a white page. The listener's own diagnosis asked for exactly this pair.
 */
internal val IosGlassContactShadow = Shadow(
    radius = 6.dp,
    offset = DpOffset(0.dp, 2.dp),
    color = Color.Black.copy(alpha = 0.06f),
)

/** Initial fallback only; each visible glass samples its own raw background. */
internal val LocalGlassTint = staticCompositionLocalOf { Color(0xFF808080) }
internal val LocalGlassLuminance = staticCompositionLocalOf { 0.5f }
internal val LocalGlassMotionActive = staticCompositionLocalOf { false }
internal val LocalGlassDark = staticCompositionLocalOf { true }
internal fun glassContentColor(luminance: Float): Color =
    if (androidGlassUsesDarkInk(luminance)) Color.Black else Color.White

internal fun iosGlassSurface(
    dark: Boolean,
    tint: Color,
    sampledLuminance: Float = androidGlassLuminance(tint.red, tint.green, tint.blue)
): Color {
    // A white-glass plate is intentionally neutral: the sampled artwork supplies the
    // changing colour through the blurred backdrop, while this layer supplies the milky
    // body seen in the reference. It becomes slightly stronger over dark samples so the
    // controls remain legible, but stays translucent over bright samples.
    val luminance = sampledLuminance.coerceIn(0f, 1f)
    val alpha = when {
        luminance < 0.22f -> 0.22f
        luminance < 0.55f -> 0.19f
        else -> 0.16f
    }
    val neutralWhite = if (luminance > 0.82f) Color(0xFFF5F5F7) else Color.White
    return neutralWhite.copy(alpha = alpha)
}

/**
 * The luminance-adaptive part of AndroidLiquidGlass-android's material. It is
 * deliberately applied to the sampled backdrop, not to the app's content or
 * an extra surface veil. Consequently a new page/Monet colour changes the
 * material immediately without carrying the previous cover's hue forward.
 */
internal fun BackdropEffectScope.apiGlassEffects(
    luminance: Float,
    refraction: Boolean,
    refractionHeight: Float,
    refractionAmount: Float,
) {
    val optics = androidGlassOptics(luminance)
    colorControls(
        brightness = optics.brightness,
        contrast = optics.contrast,
        saturation = optics.saturation,
    )
    blur(optics.blurDp * density)
    if (refraction) lens(refractionHeight, refractionAmount, depthEffect = true)
}
