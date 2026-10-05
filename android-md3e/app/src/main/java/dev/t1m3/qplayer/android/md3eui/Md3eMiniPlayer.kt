package dev.t1m3.qplayer.android.md3eui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

// Scroll content may end above the dock; the page itself remains behind it.
internal val LocalMd3eDockInset = staticCompositionLocalOf { 0.dp }

@Composable
internal fun rememberMd3eMiniScrollConnection(onVisibilityChange: (Boolean) -> Unit): NestedScrollConnection {
    val latestCallback = rememberUpdatedState(onVisibilityChange)
    val threshold = with(LocalDensity.current) { 24.dp.toPx() }
    return remember(threshold) {
        object : NestedScrollConnection {
            private var run = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val delta = available.y
                if (delta == 0f) return Offset.Zero
                run = if (run * delta < 0f) delta else run + delta
                if (run <= -threshold) {
                    latestCallback.value(false)
                    run = 0f
                } else if (run >= threshold) {
                    latestCallback.value(true)
                    run = 0f
                }
                return Offset.Zero
            }
        }
    }
}

@Composable
internal fun Md3eMiniPlayerDock(
    runtime: Md3eRuntime,
    visible: Boolean,
    onOpen: () -> Unit,
    onOpenLyrics: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    playerExpansion: Md3ePlayerExpansionState? = null,
) {
    AnimatedVisibility(
        visible = visible && runtime.playback.hasTrack,
        modifier = modifier,
        enter = if (Md3eLowSpecMode.current) androidx.compose.animation.EnterTransition.None else Md3eMotion.sheetIn(),
        exit = if (Md3eLowSpecMode.current) androidx.compose.animation.ExitTransition.None else Md3eMotion.sheetOut(),
    ) {
        if (LocalClaudeDesign.current) {
            Md3eMiniPlayer(runtime, onOpen,
                Modifier.fillMaxWidth().padding(horizontal = 24.dp), playerExpansion,
                onRoam = {
                    if (runtime.home.loggedIn) {
                        runtime.play { startPrivateFm() }
                        onOpenLyrics()
                    } else onLogin()
                })
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(40.dp).padding(end = 8.dp)) {
                    SmallFloatingActionButton(
                        onClick = {
                            if (runtime.home.loggedIn) {
                                runtime.play { startPrivateFm() }
                                onOpenLyrics()
                            } else onLogin()
                        },
                        modifier = Modifier.align(Alignment.TopEnd)
                            .then(if (playerExpansion != null) Modifier.md3ePlayerChromeExit(playerExpansion) else Modifier),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp,
                            focusedElevation = 0.dp, hoveredElevation = 0.dp),
                    ) { Icon(Md3eIcons.AutoAwesome, "私人漫游") }
                }
                Spacer(Modifier.height(8.dp))
                Md3eMiniPlayer(runtime, onOpen, Modifier.fillMaxWidth().padding(horizontal = 16.dp), playerExpansion)
            }
        }
    }
}

@Composable
internal fun Md3eMiniPlayer(
    runtime: Md3eRuntime,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    playerExpansion: Md3ePlayerExpansionState? = null,
    onRoam: () -> Unit = {},
) {
    val state = runtime.playback
    val claude = LocalClaudeDesign.current
    val view = LocalView.current
    val ink = MaterialTheme.colorScheme.onSurface
    val lowSpec = Md3eLowSpecMode.current
    val rendering = LocalMd3eRenderingActive.current && !LocalMd3eMotionActive.current


    Surface(
        modifier = modifier.height(if (claude) 52.dp else 64.dp)
            .then(if (playerExpansion != null) Modifier.md3ePlayerExpansionSource(playerExpansion) else Modifier)
            .clickable {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onOpen()
        },
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 1.dp,
        shadowElevation = if (claude) 4.dp else 7.dp,
        color = if (claude) MaterialTheme.colorScheme.surfaceContainerLow else androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!claude && state.cover.isNotBlank()) {
                Artwork(state.cover, Modifier.fillMaxSize().graphicsLayer { alpha = .78f })
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .72f)))
            }
            if (!claude) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .76f)))
            Row(
                Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(if (claude) 44.dp else 52.dp).pointerInput(state.duration, state.songId) {
                    detectTapGestures { offset ->
                        val angle = Math.toDegrees(atan2(
                            (offset.y - size.height / 2f).toDouble(),
                            (offset.x - size.width / 2f).toDouble(),
                        )).toFloat()
                        val fraction = ((angle + 90f + 360f) % 360f) / 360f
                        if (state.duration > 0) runtime.controller.seek((state.duration * fraction).roundToLong())
                    }
                }, contentAlignment = Alignment.Center) {
                    MiniCoverProgress(state)
                    if (lowSpec) MiniCover(state.cover)
                    else AnimatedContent(
                        targetState = state.cover,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                        label = "mini_cover_transition",
                    ) { cover -> MiniCover(cover) }
                }
                Spacer(Modifier.width(7.dp))
                MiniTrackInfo(state.title, state.artist, claude, ink, rendering)
                Spacer(Modifier.width(5.dp))
                MiniPlayPause(state.playing, claude, lowSpec, runtime::toggle)
                if (claude) {
                    Spacer(Modifier.width(5.dp))
                    MiniTransport(Md3eIcons.SkipPrevious, "上一首") { runtime.previous() }
                }
                Spacer(Modifier.width(5.dp))
                MiniTransport(Md3eIcons.SkipNext, "下一首") { runtime.next() }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MiniTrackInfo(
    title: String, artist: String, claude: Boolean,
    ink: androidx.compose.ui.graphics.Color, rendering: Boolean,
) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
        Text(
            title,
            maxLines = 1,
            fontSize = if (claude) 13.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            overflow = TextOverflow.Clip,
            // A B站 P title can be much longer than the dock. Keep the
            // complete string available on low-spec devices too; the
            // marquee is a single lightweight text layer and avoids
            // measuring/recomposing the whole dock to fit it.
            modifier = Modifier.graphicsLayer {}.basicMarquee(
                iterations = if (rendering) Int.MAX_VALUE else 0, initialDelayMillis = 600),
        )
        Text(
            artist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = if (claude) 11.sp else 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MiniPlayPause(playing: Boolean, claude: Boolean, lowSpec: Boolean, onToggle: () -> Unit) {
    val view = LocalView.current
    val playCorner = animateDpAsState(
        targetValue = if (playing) 16.dp else 20.dp,
        animationSpec = tween(if (lowSpec) 0 else 255),
        label = "mini_play_corner",
    )
    Box(
        Modifier.size(if (claude) 40.dp else 32.dp).graphicsLayer {
            shape = RoundedCornerShape(playCorner.value)
            clip = true
        }
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (lowSpec) Icon(
            if (playing) Md3eIcons.Pause else Md3eIcons.PlayArrow,
            if (playing) "暂停" else "播放",
            Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer,
        ) else AnimatedContent(
            targetState = playing,
            transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
            label = "mini_play_icon",
        ) { playing -> Icon(
            if (playing) Md3eIcons.Pause else Md3eIcons.PlayArrow,
            if (playing) "暂停" else "播放",
            Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer,
        ) }
    }
}

@Composable
private fun MiniCover(source: String) {
    Artwork(source, Modifier.size(if (LocalClaudeDesign.current) 40.dp else 42.dp).clip(CircleShape))
}

@Composable
private fun MiniTransport(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val view = LocalView.current
    Box(
        Modifier.size(if (LocalClaudeDesign.current) 40.dp else 32.dp).clip(CircleShape)
            .background(if (LocalClaudeDesign.current) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.onPrimary)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(20.dp), tint = if (LocalClaudeDesign.current) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MiniCoverProgress(state: PlaybackState) {
    val claude = LocalClaudeDesign.current
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    val lowSpec = Md3eLowSpecMode.current
    val rendering = LocalMd3eRenderingActive.current && !LocalMd3eMotionActive.current
    val rotation = if (rendering && state.loading && !lowSpec) rememberInfiniteTransition(label = "mini_cover_loading")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "mini_cover_loading_rotation") else null
    Canvas(Modifier.fillMaxSize()) {
        val strokeWidth = if (claude) 1.5.dp.toPx() else 2.dp.toPx()
        val inset = strokeWidth / 2f
        val diameter = minOf(size.width, size.height) - strokeWidth
        val progress = if (rendering) (state.position.toFloat() / state.duration.coerceAtLeast(1L)).coerceIn(0f, 1f) else 0f
        val rect = androidx.compose.ui.geometry.Size(diameter, diameter)
        drawArc(trackColor, 0f, 360f, false, topLeft = Offset(inset, inset), size = rect, style = Stroke(width = strokeWidth))
        if (state.loading) drawArc(progressColor, (rotation?.value ?: 0f) - 90f, 88f, false, topLeft = Offset(inset, inset), size = rect, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        else if (progress > 0f) drawArc(progressColor, -90f, progress * 360f, false, topLeft = Offset(inset, inset), size = rect, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    }
}
