package dev.t1m3.qplayer.android.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.t1m3.qplayer.netease.dto.NeteasePlaylist

/** Warm paper / ink components adapted from the user's 回响音乐 design. */
@Composable
internal fun ClaudeSectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(7.dp))
            Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ClaudeUnderline(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.width(52.dp).height(5.dp)) {
        val path = Path().apply {
            moveTo(0f, size.height / 2)
            quadraticTo(size.width * .25f, 0f, size.width * .5f, size.height * .6f)
            quadraticTo(size.width * .75f, size.height, size.width, size.height * .3f)
        }
        drawPath(path, color.copy(alpha = .75f), style = Stroke(1.5.dp.toPx()))
    }
}

@Composable
internal fun ClaudeBadge(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
        .border(.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = .25f), RoundedCornerShape(4.dp))
        .padding(horizontal = 7.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
}

@Composable
internal fun ClaudeGreeting(name: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("YOUR DAILY SOUNDTRACK", style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (name.isBlank()) "你好，今天想听什么？" else "你好，$name", style = MaterialTheme.typography.headlineMedium)
        Text("留一点时间，给喜欢的声音。", fontFamily = ClaudeSerif, fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ClaudeTopBar(title: String, canGoBack: Boolean, onBack: () -> Unit,
    onQueue: () -> Unit, onSettings: () -> Unit, onAccount: () -> Unit, onRecognize: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (canGoBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Column(Modifier.weight(1f).padding(start = if (canGoBack) 0.dp else 8.dp)) {
                Text(title, fontFamily = ClaudeSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                ClaudeUnderline()
            }
            IconButton(onClick = onRecognize) { Icon(Icons.Outlined.GraphicEq, "听歌识曲", Modifier.size(21.dp)) }
            IconButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "播放队列", Modifier.size(21.dp)) }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "设置", Modifier.size(21.dp)) }
            IconButton(onClick = onAccount) {
                Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                    .border(.8.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Person, "账户", Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
internal fun ClaudeBottomNav(screen: ComposeScreen, showLocal: Boolean, modifier: Modifier = Modifier,
    onScreen: (ComposeScreen) -> Unit) {
    val tabs = listOf(Triple(ComposeScreen.HOME, Icons.Outlined.Home, "主页"),
        Triple(ComposeScreen.LIBRARY, Icons.Outlined.QueueMusic, "歌单"),
        Triple(ComposeScreen.LOCAL, Icons.Outlined.LibraryMusic, "本地"),
        Triple(ComposeScreen.SEARCH, Icons.Outlined.Search, "搜索"))
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = .8.dp)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp)) {
            tabs.filter { showLocal || it.first != ComposeScreen.LOCAL }.forEach { (target, icon, title) ->
                val selected = target == screen
                val ink = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Tab, onClick = { onScreen(target) }).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box {
                        Icon(icon, null, Modifier.size(23.dp), tint = ink)
                        if (selected) Box(Modifier.size(5.dp).align(Alignment.TopEnd).background(ink, CircleShape))
                    }
                    Text(title, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = ink)
                }
            }
        }
    }
}

@Composable
internal fun ClaudeCover(bitmap: ImageBitmap?, title: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .border(.7.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else {
            val ink = MaterialTheme.colorScheme.primary
            Canvas(Modifier.matchParentSize()) {
                repeat(3) { drawCircle(ink.copy(alpha = .15f), size.minDimension * (.18f + it * .1f), style = Stroke(1.dp.toPx())) }
            }
            Icon(Icons.Outlined.Album, null, Modifier.size(28.dp), tint = ink)
        }
    }
}

@Composable
internal fun ClaudePlaylistCard(playlist: NeteasePlaylist, onClick: () -> Unit) {
    val bitmap = rememberCoverBitmap(null, playlist.coverThumbPath ?: playlist.coverUrl, 384)
    Surface(Modifier.width(166.dp).clickable(onClick = onClick), shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(.8.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(9.dp)) {
            Box {
                ClaudeCover(bitmap, "歌单封面", Modifier.fillMaxWidth().aspectRatio(1f).sharedCover("cover:playlist:${playlist.id}"))
                Text("CURATED", Modifier.align(Alignment.TopStart).padding(7.dp).rotate(-3f)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = .93f)).padding(horizontal = 6.dp, vertical = 3.dp),
                    fontFamily = ClaudeMono, fontSize = 8.sp, letterSpacing = 1.sp)
                Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(27.dp)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = .94f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(playlist.name ?: "未命名歌单", Modifier.padding(top = 10.dp), fontFamily = ClaudeSerif,
                fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Text("${playlist.trackCount} 首歌曲", Modifier.padding(top = 5.dp, bottom = 3.dp),
                fontFamily = ClaudeMono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ClaudeSongRow(title: String, artist: String, coverBytes: ByteArray?, coverPath: String?,
    onClick: () -> Unit, onEnqueue: (() -> Unit)?, modifier: Modifier = Modifier) {
    val bitmap = rememberCoverBitmap(coverBytes, coverPath, 256)
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Column {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .combinedClickable(onClick = onClick, onLongClick = { if (onEnqueue != null) menu = true })
                .padding(vertical = 9.dp, horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                ClaudeCover(bitmap, "专辑封面", Modifier.size(44.dp))
                Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(artist, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (onEnqueue != null) IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreHoriz, "歌曲操作", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f), thickness = .6.dp)
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("添加到播放队列") }, onClick = { menu = false; onEnqueue?.invoke() })
        }
    }
}

@Composable
internal fun ClaudeMiniPlayer(state: PlayerUiState, modifier: Modifier, onOpen: () -> Unit,
    onToggle: () -> Unit, onNext: () -> Unit) {
    Surface(modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 3.dp,
        border = BorderStroke(.8.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            Row(Modifier.padding(start = 10.dp, end = 5.dp, top = 8.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                ClaudeCover(rememberCoverBitmap(state.coverBytes, state.coverPath, 256), "正在播放", Modifier.size(40.dp))
                Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                    Text(state.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.artist, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onToggle) {
                    Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.playing) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "下一首", Modifier.size(23.dp)) }
            }
            val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant, drawStopIndicator = {})
        }
    }
}

@Composable
internal fun ClaudeChoice(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable(role = Role.Tab, onClick = onClick).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClaudeAwareAiDialog(onDismissRequest: () -> Unit, showActions: Boolean,
    title: @Composable () -> Unit, text: @Composable () -> Unit, confirmButton: @Composable () -> Unit) {
    if (!LocalClaudeDesign.current) {
        IosAwareAlertDialog(onDismissRequest = onDismissRequest, showActions = showActions,
            title = title, text = text, confirmButton = confirmButton)
        return
    }
    ModalBottomSheet(onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ClaudeBadge("YOUR MUSIC COMPANION")
            ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
            text()
            if (showActions) confirmButton()
            Spacer(Modifier.height(24.dp))
        }
    }
}
