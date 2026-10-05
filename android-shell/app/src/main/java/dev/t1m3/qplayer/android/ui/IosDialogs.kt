// Dialog material adapted from AndroidLiquidGlass-kmp DialogContent.kt.
// Original Copyright 2025 Kyant, Apache-2.0 (assets/licenses/Backdrop-Apache-2.0.txt).
package dev.t1m3.qplayer.android.ui

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.isRenderEffectSupported

internal enum class IosDialogAction { CONFIRM, DISMISS }
internal val LocalIosDialogAction = staticCompositionLocalOf<IosDialogAction?> { null }
internal val LocalIosDialogGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Stable
internal class DialogBackdropState(val backdrop: LayerBackdrop) {
    var clients by mutableIntStateOf(0)
}
private val LocalDialogBackdrop = staticCompositionLocalOf<DialogBackdropState?> { null }

/** Record only while a dialog is present. Native Dialog windows are not recorded. */
@Composable
internal fun QPlayerDialogBackdropHost(enabled: Boolean, content: @Composable () -> Unit) {
    val layer = rememberLayerBackdrop()
    val state = remember(layer) { DialogBackdropState(layer) }
    val tilt = if (enabled) rememberBiliPaiDeviceTilt() else null
    CompositionLocalProvider(LocalDialogBackdrop provides state, LocalBiliPaiDeviceTilt provides tilt) {
        Box(Modifier.fillMaxSize().then(
            if (enabled && state.clients > 0) Modifier.layerBackdrop(layer) else Modifier
        )) { content() }
    }
}

/** Common entry for every app-owned dialog; MD mode keeps the framework dialog. */
@Composable
internal fun IosAwareAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    scrollableContent: Boolean = true,
    showActions: Boolean = true,
) {
    if (!LocalIosDesign.current) {
        MaterialAlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton,
            modifier = modifier, dismissButton = dismissButton, icon = icon, title = title, text = text)
        return
    }
    val inherited = MaterialTheme.colorScheme
    val light = inherited.onBackground.luminance() < 0.5f
    // Layout/scrim remain the dialog's; its glass and ink use the adaptive APK.
    val plate = if (light) Color(0xFFFAFAFA) else Color(0xFF121212)
    val glassPlate = if (light) Color.White else Color(0xFF1C1C1C)
    val renderEffects = isRenderEffectSupported()
    val dim = if (light) Color(0xFF29293A).copy(alpha = 0.23f) else Color(0xFF121212).copy(alpha = 0.56f)
    val source = LocalDialogBackdrop.current
    val fallback = rememberCanvasBackdrop { drawRect(inherited.background) }
    val backdrop: Backdrop = source?.backdrop ?: fallback
    DisposableEffect(source) {
        if (source != null) source.clients++
        onDispose { if (source != null) source.clients = (source.clients - 1).coerceAtLeast(0) }
    }
    val refraction = LocalGlassRefraction.current
    Dialog(onDismissRequest, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false
    )) {
        SystemBarAppearance(dark = !light)
        // Create the sampler in the dialog's own window, not its Activity parent.
        val adaptive = rememberRestoredIosDialogGlass(backdrop)
        val ink = if (renderEffects) adaptive.contentColor else if (light) Color.Black else Color.White
        val dialogScheme = iosDesignColorScheme(!light).copy(
            background = plate, surface = plate, surfaceContainerHigh = plate,
            onBackground = ink, onSurface = ink, onSurfaceVariant = ink,
        )
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setDimAmount(0f) // The reference uses a coloured scrim below.
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
        }
        MaterialTheme(colorScheme = dialogScheme) {
            CompositionLocalProvider(LocalContentColor provides ink, LocalIosControls provides true,
                LocalGlassContentColor provides ink, LocalGlassDark provides !light,
                LocalIosDialogGlassBackdrop provides backdrop,
                LocalRestoredIosDialogGlass provides true,
                LocalGlassTint provides plate, LocalGlassLuminance provides plate.luminance()) {
                Box(Modifier.fillMaxSize().background(dim).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = onDismissRequest
                ), contentAlignment = Alignment.Center) {
                    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                        .imePadding().padding(horizontal = 24.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center) {
                        val bodyMaxHeight = (maxHeight - 176.dp).coerceAtLeast(48.dp)
                        val shape = remember { RoundedCornerShape(48.dp) }
                        Column(modifier.widthIn(max = 400.dp).fillMaxWidth().heightIn(max = maxHeight)
                            .then(adaptive.modifier)
                            .drawBackdrop(backdrop = backdrop, shape = { shape }, effects = {
                                restoredDialogGlassEffects(adaptive.luminance, refraction)
                            }, highlight = { RestoredIosDialogHighlight }, shadow = { RestoredIosDialogShadow },
                                innerShadow = null,
                                onDrawSurface = {
                                    // Retain a legible fallback on pre-RenderEffect devices only.
                                    if (!renderEffects) drawRect(glassPlate.copy(alpha = 0.96f))
                                })
                            .clip(shape).clickable(interactionSource = remember { MutableInteractionSource() },
                                indication = null, onClick = {})) {
                            if (icon != null) Box(Modifier.fillMaxWidth().padding(top = 20.dp),
                                contentAlignment = Alignment.Center) { icon() }
                            if (title != null) Box(Modifier.fillMaxWidth().padding(28.dp, 24.dp, 28.dp, 12.dp)) {
                                ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(
                                    color = ink, fontSize = 24.sp, fontWeight = FontWeight.Medium), title)
                            }
                            if (text != null) Box(Modifier.weight(1f, fill = false).heightIn(max = bodyMaxHeight)
                                .then(if (scrollableContent) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                                .padding(24.dp, 12.dp, 24.dp, 12.dp)) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = ink, fontSize = 15.sp), text)
                            }
                            if (showActions) Row(Modifier.fillMaxWidth().padding(24.dp, 12.dp, 24.dp, 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                if (dismissButton != null) Box(Modifier.weight(1f)) {
                                    CompositionLocalProvider(LocalIosDialogAction provides IosDialogAction.DISMISS, content = dismissButton)
                                }
                                Box(Modifier.weight(1f)) {
                                    CompositionLocalProvider(LocalIosDialogAction provides IosDialogAction.CONFIRM, content = confirmButton)
                                }
                            } else Spacer(Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
    }
}
