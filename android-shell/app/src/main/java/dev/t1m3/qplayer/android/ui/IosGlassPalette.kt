// AndroidLiquidGlass-android colour/effect adaptation, Copyright 2025 Kyant,
// Apache-2.0. Shared QPlayer glass material and theme colours.
package dev.t1m3.qplayer.android.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

// Keep a fine reflective rim, but let refraction and the cast shadow describe depth.
// Scaling layer alpha also preserves the highlight node's cached drawing.
internal val IosGlassHighlight = Highlight.Default.copy(alpha = 0.42f)
internal val IosGlassDialogHighlight = Highlight.Plain.copy(alpha = 0.42f)
internal val IosGlassThumbHighlight = Highlight.Ambient.copy(
    width = Highlight.Ambient.width / 1.5f,
    blurRadius = Highlight.Ambient.blurRadius / 1.5f,
    alpha = 0.45f,
)

/** One soft, downward cast shadow, not a second outline or duplicate glass layer. */
internal val IosGlassShadow = Shadow(
    radius = 12.dp,
    offset = DpOffset(0.dp, 3.dp),
    color = Color.Black.copy(alpha = 0.10f),
)

/** Small moving thumbs need a tighter shadow than the surrounding glass plate. */
internal val IosGlassThumbShadow = Shadow(
    radius = 4.dp,
    offset = DpOffset(0.dp, 1.5f.dp),
    color = Color.Black.copy(alpha = 0.08f),
)

// A short lower inner bevel, not a dark fill over the glass centre. The offset is
// comparable to the blur radius so the bevel remains visible instead of washing out.
// Fixed geometry lets Backdrop cache its mask; moving pills only animate opacity.
private val IosGlassLightInnerShadow = InnerShadow(
    radius = 5.dp,
    offset = DpOffset(0.dp, (-2.5f).dp),
    color = Color.Black.copy(alpha = 0.20f),
)
private val IosGlassDarkInnerShadow = IosGlassLightInnerShadow.copy(
    color = Color.Black.copy(alpha = 0.28f),
)

/** The theme-keyed bevel (dialogs, and any control outside a sampled region). */
internal fun iosGlassInnerShadow(dark: Boolean): InnerShadow =
    if (dark) IosGlassDarkInnerShadow else IosGlassLightInnerShadow

/** Ⓜ 2026-10-01: 「增强玻璃立体感，可以利用玻璃内部阴影参数」 and 「加入过渡颜色的动画」.
 *  The bevel now deepens continuously with the SAMPLED luminance instead of flipping on a
 *  boolean, so a backdrop crossing mid-grey animates the inner edge in and out — a visible
 *  transition, no pop. The band is deliberately wide (0.35–0.65) so the change is gradual. */
internal fun iosGlassInnerShadow(luminance: Float): InnerShadow {
    val t = ((luminance.coerceIn(0f, 1f) - 0.35f) / 0.30f).coerceIn(0f, 1f)
    val light = IosGlassLightInnerShadow.color.alpha
    val dark = IosGlassDarkInnerShadow.color.alpha
    return IosGlassLightInnerShadow.copy(color = Color.Black.copy(alpha = light + (dark - light) * t))
}

internal val IosGlassThumbInnerShadow = InnerShadow(
    radius = 2.dp,
    offset = DpOffset(0.dp, (-1).dp),
    color = Color.Black.copy(alpha = 0.08f),
)

/** Initial fallback only; each visible glass samples its own raw background. */
internal val LocalGlassTint = staticCompositionLocalOf { Color(0xFF808080) }
internal val LocalGlassLuminance = staticCompositionLocalOf { 0.5f }
internal val LocalGlassMotionActive = staticCompositionLocalOf { false }
internal val LocalGlassDark = staticCompositionLocalOf { true }
internal fun glassContentColor(luminance: Float): Color =
    if (androidGlassUsesDarkInk(luminance)) Color.Black else Color.White

/** Ⓜ 2026-10-01: the plate ink is the navigation bar's own pair, not a sampled one.
 *  Every glass that syncs to the bar's material（「MiniPlayer 以及按钮组件都同步和导航栏一样」）
 *  inks from the theme flag exactly as LiquidBottomTabs does. */
internal fun glassPlateInk(dark: Boolean): Color =
    if (dark) Color.White else Color(0xFF1B1B1B)

internal fun iosGlassSurface(
    dark: Boolean,
    tint: Color,
    sampledLuminance: Float = androidGlassLuminance(tint.red, tint.green, tint.blue)
): Color {
    // A restrained white veil; the sampled backdrop drives the optical filters.
    val l = sampledLuminance.coerceIn(0f, 1f)
    return Color.White.copy(alpha = 0.035f + 0.055f * l)
}

/**
 * Shared adaptive colour filters and restrained convex refraction in one lens pass.
 */
internal fun BackdropEffectScope.apiGlassEffects(
    luminance: Float,
    refraction: Boolean,
    refractionHeight: Float,
    refractionAmount: Float,
) {
    adaptiveGlassColorEffects(luminance)
    if (refraction) lens(
        refractionHeight = refractionHeight.coerceAtMost(size.minDimension * 0.45f),
        refractionAmount = refractionAmount,
        depthEffect = true,
        // The shader caps the dome at 0.15; this is as round as the material allows.
        centerConvexity = 0.145f,
    )
}

internal fun BackdropEffectScope.adaptiveGlassColorEffects(luminance: Float) {
    val optics = androidGlassOptics(luminance)
    colorControls(brightness = optics.brightness, contrast = optics.contrast,
        saturation = optics.saturation)
    blur(optics.blurDp * density)
}
