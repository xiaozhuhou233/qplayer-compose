package dev.t1m3.qplayer.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.t1m3.qplayer.bridge.PlayerController
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClaudePlayer(state: PlayerUiState, controller: PlayerController,
    openAlbum: (Long) -> Unit, openArtist: (Long) -> Unit, close: () -> Unit,
    sleepMinutes: Int, sleepRemaining: Long, sleepArmed: Boolean, onMinutes: (Int) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var options by rememberSaveable { mutableStateOf(false) }
    var biliFavorites by remember { mutableStateOf(false) }
    var playlistPicker by remember { mutableStateOf(false) }
    val cover = rememberCoverBitmap(state.coverBytes, state.coverPath, 768)
    val context = LocalContext.current
    LaunchedEffect(state.biliPlaying) { if (state.biliPlaying && tab == 1) tab = 0 }
    BackHandler { if (options) options = false else close() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close) { Icon(Icons.Default.KeyboardArrowDown, "收起播放器", Modifier.size(28.dp)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("QPLAYER · 声学档案", style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ClaudeBadge(if (state.biliPlaying) "BILIBILI" else if (state.songId > 0) "NETEASE MUSIC" else "MUSIC ARCHIVE")
            }
            IconButton(onClick = { options = true }) { Icon(Icons.Default.MoreHoriz, "更多播放选项") }
        }
        Row(Modifier.padding(horizontal = 24.dp)) {
            ClaudeChoice(if (state.biliPlaying) "视频" else "唱片", tab == 0, { tab = 0 }, Modifier.weight(1f))
            if (!state.biliPlaying) ClaudeChoice("歌词", tab == 1, { tab = 1 }, Modifier.weight(1f))
            ClaudeChoice("队列", tab == 2, { tab = 2 }, Modifier.weight(1f))
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Scroll on short/landscape windows so every transport action stays reachable.
            val compact = maxHeight < 480.dp
            Column(Modifier.fillMaxSize().then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().then(if (compact) Modifier.height(260.dp) else Modifier.weight(1f))
                    .padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    when (tab) {
                        2 -> DraggableQueueList(state, controller, Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp), staggeredEntry = false,
                            emptyContent = { ClaudeEmpty("队列还是空的", "从喜欢的歌曲开始。") })
                        1 -> when {
                            state.lyricsLoading && state.lyrics.isEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.height(16.dp)); Text("正在翻开歌词…")
                            }
                            state.lyrics.isEmpty() -> ClaudeEmpty("此刻，听音乐就好", "这首歌暂时没有歌词。")
                            else -> CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.headlineSmall) {
                                QmlLyricColumnRestored(state.copy(lyricMd3Color = true), Modifier.fillMaxSize(), onLineClick = { controller.seek(it) })
                            }
                        }
                        else -> if (state.biliPlaying) {
                            ClaudeVideoSlot(options || biliFavorites || playlistPicker, Modifier.fillMaxWidth())
                        } else ClaudeVinylStage(state, cover)
                    }
                    if (state.loading && tab == 0) CircularProgressIndicator(Modifier.size(30.dp).align(Alignment.BottomCenter))
                }
                Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(state.title.ifBlank { "尚未播放" }, fontFamily = ClaudeSerif, fontWeight = FontWeight.SemiBold,
                                fontSize = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(state.artist.ifBlank { "选一首喜欢的歌" }, Modifier.clickable(enabled = state.artistId != 0L) { openArtist(state.artistId) }
                                .padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(enabled = state.likeable || state.biliPlaying, onClick = {
                            if (state.biliPlaying) biliFavorites = true else controller.toggleLike()
                        }) { Icon(if (state.liked) Icons.Default.Star else Icons.Outlined.StarBorder, "收藏歌曲",
                            tint = if (state.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    ClaudeProgress(state, controller::seek)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { controller.cyclePlayMode() }) {
                            Icon(when (state.playMode) { 1 -> Icons.Default.Shuffle; 2 -> Icons.Default.RepeatOne; else -> Icons.Default.Repeat },
                                when (state.playMode) { 1 -> "随机播放，点击切换"; 2 -> "单曲循环，点击切换"; else -> "列表循环，点击切换" },
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(enabled = !state.privateFmMode, onClick = { controller.prev() }) { Icon(Icons.Default.SkipPrevious, "上一首", Modifier.size(32.dp)) }
                        FilledIconButton(onClick = { controller.toggle() }, Modifier.size(64.dp), shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.onBackground,
                                contentColor = MaterialTheme.colorScheme.background)) {
                            Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.playing) "暂停" else "播放", Modifier.size(32.dp))
                        }
                        IconButton(onClick = { controller.next() }) { Icon(Icons.Default.SkipNext, "下一首", Modifier.size(32.dp)) }
                        IconButton(onClick = { tab = if (tab == 2) 0 else 2 }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "播放队列") }
                    }
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (state.privateFmMode) "PRIVATE FM" else "LISTEN SLOWLY", style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { options = true }) {
                            Icon(Icons.Outlined.Timer, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp))
                            Text(if (sleepMinutes > 0) "${sleepRemaining / 60} 分钟" else "定时", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
    if (options) ModalBottomSheet(onDismissRequest = { options = false }, containerColor = MaterialTheme.colorScheme.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            ClaudeSectionTitle("慢慢听", "为这段聆听留一点空间")
            Spacer(Modifier.height(16.dp))
            SleepTimerControl(sleepMinutes, sleepRemaining, onMinutes, sleepArmed)
            if (state.albumId != 0L) TextButton(onClick = { options = false; openAlbum(state.albumId) }) { Text("查看专辑 · ${state.album}") }
            if (state.songId > 0L) TextButton(onClick = {
                controller.loadMyPlaylists(); options = false; playlistPicker = true
            }) { Text("添加到歌单") }
            PlayingArtistLinks(state, openArtist = { options = false; openArtist(it) }, fontSize = 14.sp, centered = false)
            TextButton(enabled = state.songId > 0L, onClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(state.title, "https://music.163.com/song?id=${state.songId}"))
                android.widget.Toast.makeText(context, "已复制歌曲链接", android.widget.Toast.LENGTH_SHORT).show()
                options = false
            }) { Text("复制歌曲链接") }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (biliFavorites) BiliFavPickerDialog(state, controller,
        state.queueTracks.getOrNull(state.queueIndex)?.biliBvid.orEmpty(), onDismiss = { biliFavorites = false })
    if (playlistPicker) AlertDialog(onDismissRequest = { playlistPicker = false }, title = { Text("添加到歌单") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                val owned = state.myPlaylists.filter { it.owned }
                if (owned.isEmpty()) item { Text("还没有可添加的歌单，请先登录并创建歌单。") }
                items(owned, key = { it.id }) { playlist ->
                    Text(playlist.name.orEmpty(), Modifier.fillMaxWidth().clickable {
                        controller.addToPlaylist(playlist.id, state.songId); playlistPicker = false
                    }.padding(16.dp))
                }
            }
        }, confirmButton = { TextButton(onClick = { playlistPicker = false }) { Text("关闭") } })
}

@Composable
private fun ClaudeProgress(state: PlayerUiState, seek: (Long) -> Unit) {
    var dragging by remember(state.trackKey) { mutableStateOf(false) }
    var fraction by remember(state.trackKey) { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(0L)
    val actual = if (duration > 0) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Slider(value = if (dragging) fraction else actual, onValueChange = { dragging = true; fraction = it },
        onValueChangeFinished = { seek((fraction * duration).toLong()); dragging = false }, enabled = duration > 0,
        modifier = Modifier.fillMaxWidth().height(32.dp),
        colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant, thumbColor = MaterialTheme.colorScheme.primary))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(claudeTime(if (dragging) (fraction * duration).toLong() else state.positionMs), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(claudeTime(duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun claudeTime(ms: Long): String = (ms.coerceAtLeast(0) / 1000).let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }

@Composable
private fun ClaudeVinylStage(state: PlayerUiState, cover: ImageBitmap?) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val edge = minOf(maxWidth * .67f, maxHeight * .85f, 260.dp)
        Box(Modifier.width(edge * 1.35f).height(edge * 1.1f)) {
            Column(Modifier.size(edge).align(Alignment.CenterStart).rotate(-5f)
                .shadow(8.dp, RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(12.dp)) {
                Text("VINYL ARCHIVE", fontFamily = ClaudeMono, fontSize = 8.sp, letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                ClaudeCover(cover, "唱片封套", Modifier.fillMaxWidth().weight(1f))
                Text(state.album.ifBlank { state.title }, Modifier.padding(top = 8.dp), fontFamily = ClaudeSerif,
                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ClaudeVinylDisc(state.playing, cover, Modifier.size(edge * .9f).align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun ClaudeVinylDisc(playing: Boolean, cover: ImageBitmap?, modifier: Modifier) {
    var angle by remember { mutableFloatStateOf(0f) }
    val renderingActive = rememberUiRenderingActive()
    val lowSpec = LocalLowSpecMode.current
    LaunchedEffect(playing, renderingActive, lowSpec) {
        if (!playing || !renderingActive || lowSpec) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (isActive) withFrameNanos { now ->
            angle = (angle + ((now - previous) / 1_000_000_000f).coerceAtMost(.1f) * 30f) % 360f
            previous = now
        }
    }
    val paper = MaterialTheme.colorScheme.background
    val terracotta = MaterialTheme.colorScheme.primary
    Box(modifier.shadow(8.dp, CircleShape).graphicsLayer { rotationZ = angle }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            drawCircle(Color(0xFF141413))
            drawCircle(Color(0xFF34322E), radius - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
            drawCircle(Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = .14f), Color.Transparent,
                Color.Transparent, Color.White.copy(alpha = .12f), Color.Transparent)), radius * .97f)
            repeat(12) { i -> drawCircle(Color.White.copy(alpha = .09f), radius * (.43f + .042f * i), style = Stroke(.6.dp.toPx())) }
            drawCircle(terracotta, radius * .37f)
        }
        Box(Modifier.fillMaxSize(.3f).clip(CircleShape), contentAlignment = Alignment.Center) {
            if (cover != null) Image(cover, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else Text("Q", color = paper, fontFamily = ClaudeSerif, fontSize = 25.sp)
        }
        Box(Modifier.size(9.dp).background(paper, CircleShape))
    }
}
