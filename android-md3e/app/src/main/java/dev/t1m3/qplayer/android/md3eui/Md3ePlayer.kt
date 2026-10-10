@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package dev.t1m3.qplayer.android.md3eui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import dev.t1m3.qplayer.android.md3eui.claudeClickable as clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.t1m3.qplayer.lyric.LyricLine
import dev.t1m3.qplayer.model.Track
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.sin


private class PlayerCoverTransition(
    val shared: SharedTransitionScope,
    val animated: AnimatedVisibilityScope
)
private val LocalPlayerCoverTransition = staticCompositionLocalOf<PlayerCoverTransition?> { null }

// This scope only joins the two player tabs, independent of page navigation.
@Composable
private fun Modifier.playerTabCover(restingAngle: Float = 0f): Modifier {
    val transition = LocalPlayerCoverTransition.current ?: return rotate(restingAngle)
    val claude = LocalClaudeDesign.current
    val otherAngle = if (claude && restingAngle == 0f) -5f else 0f
    val angle = transition.animated.transition.animateFloat(
        transitionSpec = { tween(if (claude) ClaudeOneTake.PLAYER_TAB else 360, easing = if (claude) ClaudeOneTake.ExpoOut else FastOutSlowInEasing) },
        label = "player_cover_rotation"
    ) { visibility -> if (visibility == EnterExitState.Visible) restingAngle else otherAngle }
    return with(transition.shared) {
        this@playerTabCover.sharedElement(
            rememberSharedContentState("player-tab-cover"),
            animatedVisibilityScope = transition.animated,
            boundsTransform = { _, _ -> tween(if (claude) ClaudeOneTake.PLAYER_TAB else 360, easing = if (claude) ClaudeOneTake.ExpoOut else FastOutSlowInEasing) },
            zIndexInOverlay = 0f
        ).graphicsLayer { rotationZ = angle.value }
    }
}

// Normal zIndex cannot occlude a shared element drawn in the root overlay.
// Lift the entire disc (including its label and shadow) into that SAME overlay,
// above the cover, and restore both to their normal order when it finishes.
@Composable
private fun Modifier.playerTabForeground(): Modifier {
    val transition = LocalPlayerCoverTransition.current ?: return zIndex(2f)
    val claude = LocalClaudeDesign.current
    val opacity = transition.animated.transition.animateFloat(
        transitionSpec = {
            if (claude) tween(ClaudeOneTake.PLAYER_TAB, easing = ClaudeOneTake.ExpoOut)
            else if (targetState == EnterExitState.Visible) tween(360) else tween(220)
        }, label = "vinyl_overlay_opacity"
    ) { visibility -> if (visibility == EnterExitState.Visible) 1f else 0f }
    return with(transition.shared) {
        this@playerTabForeground.zIndex(2f).renderInSharedTransitionScopeOverlay(
            renderInOverlay = { isTransitionActive },
            zIndexInOverlay = 2f
        ).graphicsLayer {
            // The overlay escapes the page's fade layer, so carry its fade here.
            alpha = if (isTransitionActive) opacity.value else 1f
        }
    }
}
@Composable
internal fun Md3ePlayerScreen(runtime: Md3eRuntime, onBack: () -> Unit,
    initialLyrics: Boolean = false, onQueue: () -> Unit,
    onOpenAlbum: (Long) -> Unit = {}, onOpenArtist: (Long) -> Unit = {}) {
    val state = runtime.playback
    var tab by remember(initialLyrics) { mutableIntStateOf(if (initialLyrics) 1 else 0) }
    val expansion = LocalClaudePlayerExpansion.current
    SideEffect { expansion?.detailShowsDisc = tab == 0 && !runtime.bili.playing }
    LaunchedEffect(runtime.bili.playing) { if (runtime.bili.playing) tab = 0 }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize()
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { dragX = 0f },
                onHorizontalDrag = { change, amount -> change.consume(); dragX += amount },
                onDragEnd = {
                    if (dragX < -72f && !runtime.bili.playing) tab = 1
                    if (dragX > 72f) tab = 0
                    dragX = 0f
                },
                onDragCancel = { dragX = 0f },
            )
        }
        .pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { dragY = 0f },
                onVerticalDrag = { change, amount -> change.consume(); dragY += amount },
                onDragEnd = {
                    if (dragY < -72f) onQueue()
                    if (dragY > 72f) onBack()
                    dragY = 0f
                },
                onDragCancel = { dragY = 0f },
            )
        }
        .background(MaterialTheme.colorScheme.surfaceContainer)) {
        val claude = LocalClaudeDesign.current
        val coverBackground = runtime.settings.bool("lyricCoverBackground") && state.cover.isNotBlank()
        if (coverBackground) {
            Crossfade(tab, animationSpec = tween(if (claude) ClaudeOneTake.PLAYER_TAB else 360, easing = if (claude) ClaudeOneTake.ExpoOut else FastOutSlowInEasing), label = "player_background") { backgroundTab ->
                if (backgroundTab == 1) {
                    Md3eLyricDynamicBackdrop(runtime, state.cover, Modifier.fillMaxSize())
                } else if (!claude) {
                    Md3eSoftArtworkBackdrop(state.cover, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = .76f)))
                }
            }
        }
        val dynamicLyrics = tab == 1 && coverBackground && !runtime.settings.bool("lowSpecMode")
        val scheme = MaterialTheme.colorScheme
        val displayScheme = if (dynamicLyrics) scheme.copy(
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFDDDDDD),
            onBackground = Color.White,
            surfaceContainerHigh = Color(0xD9252830),
        ) else scheme
        MaterialTheme(colorScheme = displayScheme) {
        CompositionLocalProvider(LocalContentColor provides displayScheme.onSurface) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(PlayerIcons.Back, "返回")
                }
                Spacer(Modifier.weight(1f))
                if (!runtime.bili.playing) Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.padding(3.dp)) {
                        PlayerTab("播放详情", tab == 0) { tab = 0 }
                        PlayerTab("歌词", tab == 1) { tab = 1 }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    if (runtime.bili.playing && runtime.bili.bvid.isNotBlank()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("视频链接", "https://www.bilibili.com/video/${runtime.bili.bvid}"))
                        Toast.makeText(context, "已复制分享链接", Toast.LENGTH_SHORT).show()
                    } else if (state.songId == 0L) {
                        Toast.makeText(context, "本地歌曲暂时无法生成分享链接", Toast.LENGTH_SHORT).show()
                    } else {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("歌曲链接", "https://music.163.com/song?id=${state.songId}"))
                        Toast.makeText(context, "已复制分享链接", Toast.LENGTH_SHORT).show()
                    }
                }, modifier = Modifier.size(40.dp)) { Icon(PlayerIcons.Share, "分享") }
                IconButton(onClick = onQueue, enabled = state.queueSize > 0, modifier = Modifier.size(40.dp)) {
                    Icon(PlayerIcons.Queue, "播放队列")
                }
            }
            Spacer(Modifier.height(12.dp))
            SharedTransitionLayout(Modifier.fillMaxWidth().weight(1f)) {
                val coverScope = this
                val sharedBackdrop = remember { androidx.compose.animation.core.Animatable(0f) }
                ClaudeSharedBackdropFocus(isTransitionActive, tab, sharedBackdrop, ClaudeOneTake.PLAYER_TAB)
                AnimatedContent(
                    targetState = tab,
                    modifier = Modifier.fillMaxSize().claudeTransitionBackdrop { sharedBackdrop.value },
                    transitionSpec = {
                        (if (claude) (fadeIn(tween(ClaudeOneTake.PLAYER_TAB, easing = ClaudeOneTake.ExpoOut)) togetherWith
                            fadeOut(tween(ClaudeOneTake.PLAYER_TAB, easing = ClaudeOneTake.ExpoOut)))
                        else (fadeIn(tween(360)) togetherWith fadeOut(tween(220)))).using(null)
                    },
                    label = "player_detail_tab",
                ) { page ->
                    CompositionLocalProvider(LocalPlayerCoverTransition provides
                        PlayerCoverTransition(coverScope, this)) {
                        if (page == 0) PlayerDetailTab(runtime, onLyrics = { tab = 1 },
                            onOpenAlbum = onOpenAlbum, onOpenArtist = onOpenArtist)
                        else PlayerLyricsTab(runtime, onDetail = { tab = 0 })
                    }
                }
            }
        }
        }
        }
    }
}

@Composable
private fun PlayerTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val color = animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "player_tab_color",
    )
    Box(
        Modifier.clip(RoundedCornerShape(27.dp)).drawBehind { drawRect(color.value) }.clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlayerDetailTab(runtime: Md3eRuntime, onLyrics: () -> Unit,
    onOpenAlbum: (Long) -> Unit, onOpenArtist: (Long) -> Unit) {
    if (LocalClaudeDesign.current) {
        ClaudePlayerDetail(runtime, onLyrics, onOpenAlbum, onOpenArtist)
        return
    }
    val state = runtime.playback
    Column(
        Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val coverSize = minOf(maxWidth, maxHeight)
            val scale = animateFloatAsState(if (state.playing) 1f else .95f, tween(260), label = "detail_cover_scale")
            if (runtime.bili.playing && runtime.videoVisible) {
                val aspect = if (runtime.videoHeight > 0) runtime.videoWidth.toFloat() / runtime.videoHeight else 16f / 9f
                val videoWidth = minOf(maxWidth, maxHeight * aspect)
                Md3eVideoSlot(runtime, Modifier.width(videoWidth).height(videoWidth / aspect))
            } else Artwork(state.cover, Modifier.size(coverSize).playerTabCover().graphicsLayer {
                scaleX = scale.value; scaleY = scale.value
            }.clickable {
                if (runtime.bili.playing) runtime.videoVisible = true else onLyrics()
            })
            if (state.loading) CircularProgressIndicator(Modifier.size(48.dp))
        }
        Column(
            Modifier.fillMaxWidth().height(58.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val size = when {
                state.title.length > 48 -> 18.sp
                state.title.length > 30 -> 21.sp
                else -> 24.sp
            }
            Text(state.title, Modifier.fillMaxWidth().height(34.dp)
                .clickable(enabled = state.albumId != 0L || state.album.isNotBlank()) {
                    if (state.albumId != 0L) onOpenAlbum(state.albumId)
                    else runtime.controller.openAlbumByName(state.album)
                }, fontSize = size,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Md3ePlayingArtistLinks(state, onOpenArtist)
        }
        if (!state.hasTrack && !state.loading && runtime.bili.error.isNotBlank()) Text(runtime.bili.error,
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        if (runtime.bili.playing) {
            if (runtime.bili.error.isNotBlank()) Text(runtime.bili.error,
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { runtime.videoVisible = !runtime.videoVisible }) {
                    Text(if (runtime.videoVisible) "显示封面" else "显示视频")
                }
                TextButton(onClick = { runtime.videoVisible = true; runtime.videoFullscreen = true }) { Text("全屏") }
                TextButton(onClick = runtime::cacheCurrentBili) { Text("缓存") }
            }
            BiliProgress(runtime)
        } else PlayerProgress(state, wavy = runtime.settings.intOf("lyricProgressStyle") == 0,
            onSeek = { runtime.seekDisplayed(state, it) }, modifier = Modifier.fillMaxWidth())
        PlayerTransport(runtime)
    }
}

@Composable
private fun ClaudePlayerDetail(runtime: Md3eRuntime, onLyrics: () -> Unit,
    onOpenAlbum: (Long) -> Unit, onOpenArtist: (Long) -> Unit) {
    val state = runtime.playback
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (runtime.bili.playing && runtime.videoVisible) {
                val aspect = if (runtime.videoHeight > 0) runtime.videoWidth.toFloat() / runtime.videoHeight else 16f / 9f
                val width = minOf(maxWidth, maxHeight * aspect)
                Md3eVideoSlot(runtime, Modifier.width(width).height(width / aspect))
            } else {
                ClaudeVinylStageMd3e(state.cover, state.title, state.playing,
                    Modifier.fillMaxSize().clickable { if (runtime.bili.playing) runtime.videoVisible = true else onLyrics() })
            }
            if (state.loading) CircularProgressIndicator(Modifier.size(36.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.title.ifBlank { "尚未播放" }, fontFamily = ClaudeSerif,
                    fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = state.albumId != 0L || state.album.isNotBlank()) {
                        if (state.albumId != 0L) onOpenAlbum(state.albumId)
                        else runtime.controller.openAlbumByName(state.album)
                    })
                Md3ePlayingArtistLinks(state, onOpenArtist,
                    Modifier.padding(vertical = 4.dp), centered = false)
            }
            IconButton(enabled = state.likeable, onClick = { runtime.likeDisplayed(state) }) {
                Icon(if (state.liked) PlayerIcons.Favorite else PlayerIcons.FavoriteBorder,
                    "收藏歌曲", tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (runtime.bili.playing) BiliProgress(runtime) else PlayerProgress(state,
            wavy = false, onSeek = { runtime.seekDisplayed(state, it) }, modifier = Modifier.fillMaxWidth())
        PlayerTransport(runtime)
    }
}

@Composable
private fun ClaudeVinylStageMd3e(cover: String, album: String, playing: Boolean, modifier: Modifier) {
    val expansion = LocalClaudePlayerExpansion.current
    val coverTransition = LocalPlayerCoverTransition.current
    // Move the disc concurrently with the cover; no delayed sleeve return.
    // Joining the visibility transition also supports reversal without a timer
    // from a previous navigation completing against the new page.
    val separation = coverTransition?.animated?.transition?.animateFloat(
        transitionSpec = { tween(ClaudeOneTake.VINYL_MOVE, easing = ClaudeOneTake.ExpoOut) },
        label = "vinyl_sleeve_separation"
    ) { visibility -> if (visibility == EnterExitState.Visible) 0f else 1f }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val edge = minOf(maxWidth * .66f, maxHeight * .82f, 270.dp)
        val accent = MaterialTheme.colorScheme.primary
        Box(Modifier.width(edge * 1.36f).height(edge * 1.08f)) {
            // Keep the tilted sleeve decoration separate from the shared image:
            // ancestor rotation is not carried into the shared-element overlay.
            Box(Modifier.size(edge).align(Alignment.CenterStart).rotate(-5f)
                .shadow(18.dp, RoundedCornerShape(8.dp), clip = false,
                    ambientColor = Color.Black.copy(alpha = .22f),
                    spotColor = Color.Black.copy(alpha = .38f))
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)))
            Column(Modifier.size(edge).align(Alignment.CenterStart).padding(12.dp)) {
                Text("VINYL ARCHIVE", fontFamily = ClaudeMono, fontSize = 8.sp, letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Artwork(cover, Modifier.fillMaxWidth().weight(1f).playerTabCover(restingAngle = -5f), corner = 4.dp)
                Text(album, Modifier.padding(top = 8.dp), fontFamily = ClaudeSerif, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(edge * .9f).align(Alignment.CenterEnd)
                .onGloballyPositioned { expansion?.targetDiscBounds = it.boundsInRoot() }
                .playerTabForeground()
                .graphicsLayer {
                    val open = separation?.value ?: 0f
                    translationX = edge.toPx() * .54f * open
                    rotationZ = 12f * open
                    scaleX = 1f - .08f * open
                    scaleY = 1f - .08f * open
                }
                .shadow(14.dp, CircleShape, clip = false), contentAlignment = Alignment.Center) {
                ClaudeMovingDisc(cover, Modifier.fillMaxSize().graphicsLayer {
                    alpha = if (expansion?.moving == true) 0f else 1f
                })
            }
        }
    }
}

@Composable
private fun PlayerTransport(runtime: Md3eRuntime) {
    val state = runtime.playback
    val context = LocalContext.current
    var biliFavoriteOpen by remember { mutableStateOf(false) }
    var playlistPickOpen by remember { mutableStateOf(false) }
    var biliLoginOpen by remember { mutableStateOf(false) }
    if (biliFavoriteOpen) Md3eBiliFavPickerDialog(runtime) { biliFavoriteOpen = false }
    // The old app's 添加到歌单 dialog: your playlists; picking one adds the
    // current song to it.
    if (playlistPickOpen) AlertDialog(
        onDismissRequest = { playlistPickOpen = false },
        title = { Text("添加到歌单") },
        text = {
            if (runtime.playlists.mine.isEmpty()) {
                Text("还没有自己的歌单（或未登录）", Modifier.padding(16.dp))
            } else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(runtime.playlists.mine, key = { it.id }) { playlist ->
                    Text(playlist.name ?: "未命名歌单",
                        modifier = Modifier.fillMaxWidth().clickable {
                            runtime.controller.addToPlaylist(playlist.id, runtime.playback.songId)
                            Toast.makeText(context, "已添加到：${playlist.name ?: "未命名歌单"}",
                                Toast.LENGTH_SHORT).show()
                            playlistPickOpen = false
                        }.padding(16.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { playlistPickOpen = false }) { Text("取消") } }
    )
    if (biliLoginOpen) Md3eBiliLoginDialog(runtime) { biliLoginOpen = false }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PlayerTransportButtons(
            playing = runtime.transportState.playing,
            hasTrack = runtime.transportState.hasTrack,
            privateFm = runtime.transportState.privateFm,
            onPrevious = runtime::previous,
            onToggle = runtime::toggle,
            onNext = runtime::next,
            onMotionChanged = runtime::setTransportMotionActive,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.width(264.dp).height(56.dp).clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerToggle(PlayerIcons.Shuffle, "随机", state.playMode == 1,
                MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary,
                Modifier.weight(1f)) {
                runtime.play { setPlayMode(if (state.playMode == 1) 0 else 1) }
            }
            PlayerToggleDivider()
            PlayerToggle(if (state.playMode == 2) PlayerIcons.RepeatOne else PlayerIcons.Repeat,
                "循环", state.playMode == 2, MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer, Modifier.weight(1f)) {
                runtime.play { setPlayMode(if (state.playMode == 2) 0 else 2) }
            }
            PlayerToggleDivider()
            PlayerToggle(if (state.liked) PlayerIcons.Favorite else PlayerIcons.FavoriteBorder,
                "收藏", state.liked, MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.onTertiary, Modifier.weight(1f),
                enabled = runtime.bili.playing || state.likeable,
                // The old app's long-press heart: pick which of your playlists the
                // current song is collected into.
                onLongClick = {
                    if (!runtime.bili.playing && runtime.playback.songId != 0L
                        && runtime.playlists.loggedIn) playlistPickOpen = true
                }) {
                if (runtime.bili.playing) {
                    if (runtime.bili.loggedIn) biliFavoriteOpen = true else biliLoginOpen = true
                } else runtime.likeDisplayed(state)
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun PlayerToggle(icon: ImageVector, label: String, active: Boolean,
    activeColor: Color, activeContent: Color, modifier: Modifier,
    enabled: Boolean = true, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val background = animateColorAsState(
        if (active) activeColor else Color.Transparent,
        label = "player_toggle_background",
    )
    Box(modifier.fillMaxHeight().clip(CircleShape).drawBehind { drawRect(background.value) }
        .combinedClickable(enabled = enabled, onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(24.dp),
            tint = if (active) activeContent
                else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlayerToggleDivider() {
    Box(Modifier.width(1.dp).height(24.dp)
        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)))
}

@Composable
private fun PlayerLyricsTab(runtime: Md3eRuntime, onDetail: () -> Unit) {
    if (LocalClaudeDesign.current) {
        ClaudeLyricsTab(runtime, onDetail)
        return
    }
    val state = runtime.playback
    val lyrics = runtime.lyricState
    val coverOnly = lyrics.coverOnly || lyrics.coverModeManual
    var offsetOpen by remember(state.songId) { mutableStateOf(false) }
    var offsetValue by remember(state.songId, lyrics.offsetMs) { mutableFloatStateOf(lyrics.offsetMs.toFloat()) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Artwork(state.cover, Modifier.size(56.dp).playerTabCover().clickable(onClick = onDetail))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(state.title, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Md3ePlayingArtistLinks(state, runtime::openArtist, centered = false)
            }
            if (!coverOnly) IconButton(onClick = { runtime.controller.setCoverMode(true) }) {
                Icon(PlayerIcons.Album, "显示封面")
            }
            IconButton(onClick = { offsetOpen = !offsetOpen }) {
                Icon(PlayerIcons.Sync, "歌词偏移")
            }
        }
        if (offsetOpen) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("歌词偏移", fontWeight = FontWeight.Medium)
                        Text(if (offsetValue > 0f) "+${offsetValue.toInt()} ms" else "${offsetValue.toInt()} ms",
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("仅对当前歌曲生效 · 负值提前，正值延后", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = offsetValue.coerceIn(-5000f, 5000f),
                        onValueChange = { offsetValue = (it / 50f).toInt() * 50f },
                        valueRange = -5000f..5000f, steps = 199,
                        onValueChangeFinished = { runtime.controller.setLyricOffset(offsetValue.toInt()) })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("提前 5 秒", fontSize = 11.sp)
                        Text("0", fontSize = 11.sp)
                        Text("延后 5 秒", fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        offsetValue = 0f
                        runtime.controller.resetLyricOffset()
                    }, enabled = offsetValue != 0f, modifier = Modifier.align(Alignment.End)) {
                        Text("重置为 0")
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Crossfade(coverOnly, animationSpec = tween(250), label = "lyric_cover_mode") { showCover ->
                when {
                    lyrics.loading -> CircularProgressIndicator(Modifier.size(48.dp))
                    showCover -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            if (lyrics.lines.isEmpty()) {
                                PlayerCoverPlaceholder()
                            } else {
                                Artwork(state.cover,
                                    Modifier.size(minOf(maxWidth, maxHeight).coerceAtMost(360.dp))
                                        .clickable(enabled = !lyrics.coverOnly) {
                                            runtime.controller.setCoverMode(false)
                                        })
                            }
                        }
                        Text(state.title, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Md3ePlayingArtistLinks(state, runtime::openArtist)
                    }
                    else -> Md3eLyricColumn(runtime)
                }
            }
        }
        // 2026-10-10：用户要求「歌词界面删除滑动条及下面按钮」——进度条与播放键整块移除，
        // 歌词占满余下空间；播放控制回唱片页（点封面）。
    }
}

@Composable
private fun ClaudeLyricsTab(runtime: Md3eRuntime, onDetail: () -> Unit) {
    val state = runtime.playback
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(state.cover, Modifier.size(56.dp).playerTabCover().clickable(onClick = onDetail), corner = 6.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(state.title, fontFamily = ClaudeSerif, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Md3ePlayingArtistLinks(state, runtime::openArtist, centered = false)
            }
            IconButton(onClick = onDetail) { Icon(PlayerIcons.Album, "返回唱片") }
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (runtime.lyricState.loading) Md3eLoadingIndicator(Modifier.size(36.dp))
            else if (runtime.lyricState.lines.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("此刻，听音乐就好", fontFamily = ClaudeSerif, fontSize = 22.sp)
                    Text("这首歌暂时没有歌词。", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
            else Md3eLyricColumn(runtime)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerCoverPlaceholder() {
    Md3eLoadingIndicator(Modifier.size(96.dp), contained = true)
}

@Composable
private fun PlayerLyricLines(runtime: Md3eRuntime) {
    val state = runtime.playback
    val lyricState = runtime.lyricState
    val lines = lyricState.lines
    val rows = remember(lyricState.revision, lines) { buildPlayerLyricRows(lines) }
    val listState = rememberLazyListState()
    val positionMs = state.position - lyricState.offsetMs
    val active = remember(rows, positionMs) { playerLyricIndex(rows, positionMs) }
    val fontSize = runtime.settings.intOf("lyricFontSize").coerceIn(14, 40)
    val spacing = (runtime.settings.intOf("lyricLineSpacing").coerceIn(100, 250) * 24f / 200f).dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val centerPadding = (maxHeight * .42f).coerceAtLeast(32.dp)
        LaunchedEffect(state.songId, lyricState.revision, active) {
            if (active >= 0) {
                if (kotlin.math.abs(listState.firstVisibleItemIndex - active) > 4) {
                    listState.scrollToItem(active)
                } else {
                    listState.animateScrollToItem(active)
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(), state = listState,
            contentPadding = PaddingValues(vertical = centerPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.coerceAtLeast(10.dp)),
        ) {
            itemsIndexed(rows, key = { index, row -> "${row.startMs}_$index" }) { index, row ->
                when (row) {
                    is PlayerLyricRow.Line -> PlayerLyricLine(row.lyric, index == active, index, active,
                        positionMs, fontSize, runtime, Modifier.fillMaxWidth()) {
                        runtime.seekDisplayed(state, row.startMs)
                    }
                    is PlayerLyricRow.Intro -> PlayerLyricIndicator(
                        positionMs, row.startMs, row.endMs, index == active,
                        state.playing, fontSize, false)
                    is PlayerLyricRow.Instrumental -> PlayerLyricIndicator(
                        positionMs, row.startMs, row.endMs, index == active,
                        state.playing, fontSize, true) {
                        runtime.seekDisplayed(state, row.startMs)
                    }
                }
            }
            if (rows.isEmpty()) item {
                Text("暂无歌词", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private sealed interface PlayerLyricRow {
    val startMs: Long
    val endMs: Long
    data class Intro(override val endMs: Long) : PlayerLyricRow {
        override val startMs: Long = 0L
    }
    data class Line(val lyric: LyricLine, override val startMs: Long,
        override val endMs: Long) : PlayerLyricRow
    data class Instrumental(override val startMs: Long,
        override val endMs: Long) : PlayerLyricRow
}

private fun buildPlayerLyricRows(lines: List<LyricLine>): List<PlayerLyricRow> {
    if (lines.isEmpty()) return emptyList()
    val result = ArrayList<PlayerLyricRow>(lines.size + 4)
    val first = lines.first().startMs().coerceAtLeast(0L)
    if (first >= 2_000L) result += PlayerLyricRow.Intro(first)
    var previousEnd = 0L
    lines.forEachIndexed { index, line ->
        val start = line.startMs().coerceAtLeast(0L)
        if (index > 0 && start - previousEnd > 7_000L) {
            val breakStart = previousEnd + 500L
            val previousLine = result.lastOrNull() as? PlayerLyricRow.Line
            if (previousLine != null && previousLine.endMs > breakStart) {
                result[result.lastIndex] = previousLine.copy(endMs = breakStart)
            }
            result += PlayerLyricRow.Instrumental(breakStart, start)
        }
        val explicitEnd = line.endMs().takeIf { it > start }
        val displayEnd = explicitEnd ?: lines.getOrNull(index + 1)?.startMs() ?: start + 960L
        result += PlayerLyricRow.Line(line, start, displayEnd.coerceAtLeast(start + 1L))
        previousEnd = explicitEnd ?: start + 960L
    }
    return result
}

private fun playerLyricIndex(rows: List<PlayerLyricRow>, positionMs: Long): Int {
    var latest = -1
    rows.forEachIndexed { index, row ->
        if (positionMs < row.startMs) return latest
        latest = index
        if (positionMs < row.endMs) return index
    }
    return latest
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerLyricIndicator(positionMs: Long, startMs: Long, endMs: Long,
    active: Boolean, playing: Boolean, fontSize: Int, compact: Boolean,
    onClick: (() -> Unit)? = null) {
    val elapsed = (positionMs - startMs).coerceAtLeast(0L)
    val duration = (endMs - startMs).coerceAtLeast(1L)
    val beat = ((elapsed.toFloat() / duration) * 3f).toInt().coerceIn(0, 3)
    val size = (fontSize * if (compact) 1.04f else 1.6f).dp
    val colors = listOf(MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary)
    Row(Modifier.fillMaxWidth().then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
        .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(size * .25f),
        verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            if (active && playing && index <= beat) {
                Md3eLoadingIndicator(Modifier.size(size), color = colors[index])
            } else {
                Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(size * .52f).clip(CircleShape)
                        .background(if (index < beat) colors[index]
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)))
                }
            }
        }
        if (compact) Text("间奏", fontSize = (fontSize * .7f).sp,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlayerLyricLine(line: LyricLine, focused: Boolean, rowIndex: Int, activeIndex: Int,
    positionMs: Long,
    fontSize: Int, runtime: Md3eRuntime, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val backgroundVocal = line.vocalChannel == LyricLine.VocalChannel.BACKGROUND ||
        line.vocalChannel == LyricLine.VocalChannel.BACKGROUND_LEFT ||
        line.vocalChannel == LyricLine.VocalChannel.BACKGROUND_RIGHT
    val rightAligned = line.vocalChannel == LyricLine.VocalChannel.DUET_RIGHT ||
        line.vocalChannel == LyricLine.VocalChannel.BACKGROUND_RIGHT
    val align = if (rightAligned) TextAlign.Right else TextAlign.Left
    val springEnabled = runtime.settings.bool("lyricSpring")
    val lift = remember(line) { Animatable(0f) }
    LaunchedEffect(activeIndex, springEnabled) {
        if (activeIndex < 0) return@LaunchedEffect
        val distance = kotlin.math.abs(rowIndex - activeIndex)
        if (distance > 4) {
            lift.snapTo(0f)
            return@LaunchedEffect
        }
        lift.snapTo(if (rowIndex >= activeIndex) 16f else -16f)
        kotlinx.coroutines.delay((distance * 50L).coerceAtMost(200L))
        lift.animateTo(0f,
            if (springEnabled) spring(dampingRatio = .82f, stiffness = 100f) else tween(320))
    }
    val scale by animateFloatAsState(
        if (focused && runtime.settings.bool("lyricScale")) 1.12f else .98f,
        if (springEnabled) spring(dampingRatio = .8f, stiffness = 100f) else tween(320),
        label = "lyric_line_scale",
    )
    val alpha by animateFloatAsState(if (focused) .85f else .175f,
        tween(120), label = "lyric_line_opacity")
    val foreground = if (runtime.settings.bool("lyricMd3Color"))
        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val text = remember(line, positionMs, foreground, focused,
        runtime.settings.bool("lyricLinearAnim"), runtime.settings.bool("lyricGlow")) {
        buildAnnotatedString {
            val singleLineTiming = line.syllables.size == 1
            for (syllable in line.syllables) {
                val content = syllable.text
                val timePerChar = if (content.isNotEmpty()) syllable.durationMs / content.length else 0L
                content.forEachIndexed { charIndex, char ->
                    val lit = focused && (!singleLineTiming || runtime.settings.bool("lyricLinearAnim")) &&
                        positionMs >= syllable.startMs + timePerChar * charIndex
                    withStyle(SpanStyle(
                        color = if (lit) foreground else foreground.copy(alpha = if (focused) .58f else 1f),
                        shadow = if (lit && runtime.settings.bool("lyricGlow"))
                            Shadow(foreground.copy(alpha = .55f), Offset.Zero, 12f) else null,
                    )) { append(char) }
                }
            }
        }
    }
    Column(modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        .graphicsLayer {
            scaleX = scale; scaleY = scale; this.alpha = alpha
            translationY = lift.value
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (rightAligned) 1f else 0f, .5f)
        }.clickable(onClick = onClick),
        horizontalAlignment = if (rightAligned) Alignment.End else Alignment.Start) {
        Text(text, Modifier.fillMaxWidth(), fontSize = (if (backgroundVocal) fontSize * .82f else fontSize.toFloat()).sp,
            lineHeight = (fontSize * 1.22f).sp, fontWeight = FontWeight.Bold, textAlign = align,
            style = TextStyle(shadow = if (runtime.settings.bool("lyricShadow"))
                Shadow(Color.Black.copy(alpha = .42f), Offset(0f, 2f), 4f) else null))
        line.translation?.takeIf { it.isNotBlank() }?.let {
            Text(it, Modifier.fillMaxWidth(), fontSize = (fontSize * .52f).sp,
                color = foreground, textAlign = align)
        }
        line.romaji?.takeIf { it.isNotBlank() }?.let {
            Text(it, Modifier.fillMaxWidth(), fontSize = (fontSize * .46f).sp,
                color = foreground, textAlign = align)
        }
    }
}

@Composable
private fun PlayerProgress(state: PlaybackState, wavy: Boolean,
    onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var dragging by remember(state.songId) { mutableStateOf(false) }
    var fraction by remember(state.songId) { mutableFloatStateOf(0f) }
    val duration = state.duration.coerceAtLeast(1L)
    val rendering = LocalMd3eRenderingActive.current && !LocalMd3eMotionActive.current
    val animated = remember(state.songId) { Animatable(progress(state.position, duration)) }
    LaunchedEffect(state.clock, state.songId, state.playing, duration, state.seekRevision, rendering) {
        if (rendering) snapshotFlow { progress(state.position, duration) }.collectLatest { target ->
            if (state.playing) animated.animateTo(target, tween(100)) else animated.snapTo(target)
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier.widthIn(max = 600.dp)) {
        Box(Modifier.fillMaxWidth().height(32.dp)
            .pointerInput(duration) {
                detectTapGestures { point -> onSeek((duration * (point.x / size.width).coerceIn(0f, 1f)).toLong()) }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { point -> dragging = true; fraction = (point.x / size.width).coerceIn(0f, 1f) },
                    onHorizontalDrag = { change, _ -> change.consume(); fraction = (change.position.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { onSeek((duration * fraction).toLong()); dragging = false },
                    onDragCancel = { dragging = false },
                )
            }.drawWithCache {
                val center = size.height / 2f
                val amplitude = if (dragging) 4.dp.toPx() else 2.5.dp.toPx()
                val stroke = Stroke(width = if (dragging) 5.dp.toPx() else 3.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round)
                val gap = 10.dp.toPx()
                val height = if (dragging) 14.dp.toPx() else 10.dp.toPx()
                // Geometry changes only with size or drag mode, never with time.
                val wave = Path()
                if (wavy && size.width > 0f) {
                    val step = 2.dp.toPx().coerceAtLeast(1f)
                    wave.moveTo(0f, center)
                    var x = step
                    while (x < size.width) {
                        wave.lineTo(x, center + sin(x / size.width * (4f * PI).toFloat()) * amplitude)
                        x += step
                    }
                    wave.lineTo(size.width, center)
                }
                onDrawBehind {
                    val shown = if (dragging) fraction else animated.value
                    val thumbX = (size.width * shown).coerceIn(gap, (size.width - gap).coerceAtLeast(gap))
                    if (wavy) {
                        clipRect(right = (thumbX - gap).coerceAtLeast(0f)) { drawPath(wave, primary, style = stroke) }
                        clipRect(left = (thumbX + gap).coerceAtMost(size.width)) { drawPath(wave, track, style = stroke) }
                    } else {
                        drawRoundRect(primary, topLeft = Offset(0f, center - height / 2f),
                            size = Size((thumbX - gap).coerceAtLeast(0f), height), cornerRadius = CornerRadius(height / 2f))
                        drawRoundRect(track, topLeft = Offset((thumbX + gap).coerceAtMost(size.width), center - height / 2f),
                            size = Size((size.width - thumbX - gap).coerceAtLeast(0f), height), cornerRadius = CornerRadius(height / 2f))
                    }
                    if (!state.loading) drawRoundRect(primary,
                        topLeft = Offset(thumbX - 2.dp.toPx(), center - if (dragging) 14.dp.toPx() else 11.dp.toPx()),
                        size = Size(4.dp.toPx(), if (dragging) 28.dp.toPx() else 22.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()))
                }
            })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PlayerElapsedTime(state, if (dragging) (duration * fraction).toLong() else null)
            Text(playerTime(state.duration), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlayerElapsedTime(state: PlaybackState, draggedPosition: Long?) {
    val seconds by remember(state.clock, draggedPosition) {
        derivedStateOf { (draggedPosition ?: state.position) / 1000L }
    }
    Text(playerTime(seconds * 1000L), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun Md3eQueueScreen(runtime: Md3eRuntime, onBack: () -> Unit,
    timer: Md3eSleepTimerState) {
    val state = runtime.playback
    val queue = runtime.queue
    val entryIds = remember(runtime) { AtomicLong() }
    var entries by remember(runtime) {
        mutableStateOf(queue.map { QueueEntry(entryIds.getAndIncrement(), it) })
    }
    var playingEntryId by remember(runtime) {
        mutableStateOf(entries.getOrNull(state.queueIndex)?.id)
    }
    // The controller is polled; mirror each move immediately so the handle stays
    // attached during a continuous drag. Reuse each occurrence's key when the
    // controller catches up, including repeated instances of the same song.
    LaunchedEffect(queue, state.queueIndex) {
        entries = reconcileQueueEntries(entries, queue, entryIds)
        playingEntryId = entries.getOrNull(state.queueIndex)?.id
    }
    fun moveEntry(fromId: Long, toId: Long) {
        val from = entries.indexOfFirst { it.id == fromId }
        val to = entries.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0 || from == to) return
        runtime.controller.moveInQueue(from, to)
        entries = entries.toMutableList().apply { add(to, removeAt(from)) }
    }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            val fromId = from.key as? Long
            val toId = to.key as? Long
            if (fromId != null && toId != null) moveEntry(fromId, toId)
        },
    )
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(PlayerIcons.Back, "返回") }
            Text("播放队列", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${queue.size} 首", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
        }
        if (queue.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("队列中还没有歌曲", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(entries, key = { _, entry -> entry.id }) { _, entry ->
                    // Reorderable 2.2 defaults to the removed Compose 1.6
                    // animateItemPlacement API. Pass the current API explicitly.
                    ReorderableItem(reorderState, key = entry.id,
                        animateItemModifier = Modifier.animateItem()) { dragging ->
                        val scale by animateFloatAsState(if (dragging) 1.02f else 1f,
                            animationSpec = if (LocalClaudeDesign.current) ClaudeOneTake.snappy() else spring(stiffness = Spring.StiffnessMediumLow),
                            label = "queue_drag_scale")
                        QueueRow(entry.track, selected = entry.id == playingEntryId,
                            dragging = dragging,
                            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
                            onPlay = {
                                val index = entries.indexOfFirst { it.id == entry.id }
                                if (index >= 0) runtime.play { playQueueIndex(index) }
                            },
                            onRemove = {
                                val index = entries.indexOfFirst { it.id == entry.id }
                                if (index >= 0) {
                                    runtime.controller.removeFromQueue(index)
                                    entries = entries.filterNot { it.id == entry.id }
                                }
                            },
                            dragHandle = {
                                Icon(PlayerIcons.DragHandle, "拖拽调整歌曲顺序",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                        .draggableHandle(interactionSource = remember { MutableInteractionSource() })
                                        .semantics {
                                            customActions = listOf(
                                                CustomAccessibilityAction("向上移动") {
                                                    val index = entries.indexOfFirst { it.id == entry.id }
                                                    val target = entries.getOrNull(index - 1)
                                                    if (target != null) moveEntry(entry.id, target.id)
                                                    target != null
                                                },
                                                CustomAccessibilityAction("向下移动") {
                                                    val index = entries.indexOfFirst { it.id == entry.id }
                                                    val target = if (index >= 0) entries.getOrNull(index + 1) else null
                                                    if (target != null) moveEntry(entry.id, target.id)
                                                    target != null
                                                },
                                            )
                                        })
                            })
                    }
                }
            }
        }
        // 定时停止播放（老版本的「定时播放」）—— 与老 app 一样放在列表下方。
        Md3eSleepTimerControl(timer)
    }
}

@Composable
private fun QueueRow(track: Track, selected: Boolean, dragging: Boolean,
    modifier: Modifier = Modifier, onPlay: () -> Unit, onRemove: () -> Unit,
    dragHandle: @Composable () -> Unit) {
    Surface(onClick = onPlay, modifier = modifier, shape = RoundedCornerShape(20.dp),
        shadowElevation = if (dragging) 6.dp else 0.dp,
        color = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            dragging -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> Color.Transparent
        }) {
        Row(Modifier.fillMaxWidth().height(68.dp).padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Artwork(track.coverThumbPath ?: track.coverUrl.orEmpty(), Modifier.size(48.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title.orEmpty(), style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist.orEmpty(), style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Md3eIcons.MusicNote, "当前播放", Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary)
            dragHandle()
            IconButton(onClick = onRemove) { Icon(PlayerIcons.Remove, "从队列移除") }
        }
    }
}

private data class QueueEntry(val id: Long, val track: Track)

private fun reconcileQueueEntries(previous: List<QueueEntry>, tracks: List<Track>, ids: AtomicLong): List<QueueEntry> {
    val remaining = previous.toMutableList()
    return tracks.map { track ->
        val existing = remaining.indexOfFirst { it.track === track }
        if (existing >= 0) remaining.removeAt(existing) else QueueEntry(ids.getAndIncrement(), track)
    }
}

private fun progress(position: Long, duration: Long): Float =
    if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

private fun playerTime(ms: Long): String =
    "${ms.coerceAtLeast(0) / 60000}:${((ms.coerceAtLeast(0) / 1000) % 60).toString().padStart(2, '0')}"

private object PlayerIcons {
    val Back = icon("Back", "M20,11H7.83l5.59,-5.59L12,4l-8,8l8,8l1.41,-1.41L7.83,13H20z")
    val Queue = icon("Queue", "M4,6h16v2H4zM4,11h16v2H4zM4,16h10v2H4zM17,15l5,3l-5,3z")
    val Share = icon("Share", "M18,16.08c-.76,0 -1.44,.3 -1.96,.77L8.91,12.7a3.25,3.25 0,0 0,0 -1.4l7.05,-4.11A3,3 0,1 0,15,5c0,.24 .03,.47 .09,.7L8.04,9.81a3,3 0,1 0,0 4.38l7.12,4.16c-.05,.2 -.08,.42 -.08,.65a2.92,2.92 0,1 0,2.92 -2.92z")
    val Album = icon("Album", "M12,2a10,10 0,1 0,0 20a10,10 0,0 0,0 -20zM12,14a2,2 0,1 1,0 -4a2,2 0,0 1,0 4z")
    val Sync = icon("Sync", "M12,4V1L8,5l4,4V6a6,6 0,0 1,5.65 4H19a8,8 0,0 0,-7 -6zM6.35,14H5a8,8 0,0 0,7 6v3l4,-4l-4,-4v3a6,6 0,0 1,-5.65 -4z")
    val Remove = icon("Remove", "M19,13H5v-2h14z")
    val DragHandle = icon("DragHandle", "M4,9h16v2H4zM4,13h16v2H4z")
    val Favorite = icon("Favorite", "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5A5.5,5.5 0,0 1,7.5 3c1.74,0 3.41,.81 4.5,2.09A5.48,5.48 0,0 1,16.5 3A5.5,5.5 0,0 1,22 8.5c0,3.78 -3.4,6.86 -8.55,11.54z")
    val FavoriteBorder = icon("FavoriteBorder", "M16.5,3A5.48,5.48 0,0 0,12 5.09A5.48,5.48 0,0 0,7.5 3A5.5,5.5 0,0 0,2 8.5c0,3.78 3.4,6.86 8.55,11.54L12,21.35l1.45,-1.32C18.6,15.36 22,12.28 22,8.5A5.5,5.5 0,0 0,16.5 3zM12.1,18.55L12,18.65l-0.1,-0.1C7.14,14.24 4,11.39 4,8.5A3.5,3.5 0,0 1,7.5 5c1.35,0 2.66,.87 3.12,2.06h2.77A3.35,3.35 0,0 1,16.5 5A3.5,3.5 0,0 1,20 8.5c0,2.89 -3.14,5.74 -7.9,10.05z")
    val Repeat = icon("Repeat", "M7,7h10V4l4,4l-4,4V9H7a2,2 0,0 0,-2 2v1H3v-1a4,4 0,0 1,4 -4zM17,17H7v3l-4,-4l4,-4v3h10a2,2 0,0 0,2 -2v-1h2v1a4,4 0,0 1,-4 4z")
    val RepeatOne = icon("RepeatOne", "M7,7h10V4l4,4l-4,4V9H7a2,2 0,0 0,-2 2v1H3v-1a4,4 0,0 1,4 -4zM17,17H7v3l-4,-4l4,-4v3h10a2,2 0,0 0,2 -2v-1h2v1a4,4 0,0 1,-4 4zM11,10h2v4h-2z")
    val Shuffle = icon("Shuffle", "M10.6,9.17L5.41,4L4,5.41l5.17,5.17zM14.5,4l2.04,2.04L4,18.59L5.41,20L17.96,7.46L20,9.5V4zM14.83,13.42l-1.41,1.41l3.12,3.13L14.5,20H20v-5.5l-2.04,2.04z")

    private fun icon(name: String, path: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(path), fill = SolidColor(Color.Black)).build()
}
