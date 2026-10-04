@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.t1m3.qplayer.android.md3eui

import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import dev.t1m3.qplayer.netease.dto.NeteaseAlbum
import dev.t1m3.qplayer.netease.dto.NeteasePlaylist
import dev.t1m3.qplayer.netease.dto.NeteaseSong
import kotlinx.coroutines.delay

private const val HOME_HEAD_SHELF = 6
private const val HOME_SHELF_CHUNK = 12
private const val HOME_ALBUM_CHUNK = 12
private const val HOME_PLAYLIST_CHUNK = 10

@Composable
internal fun Md3eMainTopBar(tab: String, detail: String, title: String, loggedIn: Boolean,
    onBack: () -> Unit, onQueue: () -> Unit, onSettings: () -> Unit, onAccount: () -> Unit) {
    val displayTitle = when (detail) {
        "playlist" -> title.ifBlank { "歌单" }
        "album" -> title.ifBlank { "专辑" }
        "artist" -> title.ifBlank { "歌手" }
        "settings" -> "设置"
        "biliFolders" -> "B站收藏夹"
        "biliFolder" -> title.ifBlank { "B站收藏夹" }
        else -> when (tab) {
            "home" -> "推荐"
            "playlists" -> "我的"
            "local" -> "本地"
            "search" -> "搜索"
            "settings" -> "设置"
            else -> "QPlayer"
        }
    }
    TopAppBar(title = { Text(displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        navigationIcon = {
            if (detail.isNotEmpty() || tab == "settings") IconButton(onClick = onBack) {
                Icon(Md3eIcons.Back, "返回")
            }
        },
        actions = {
            IconButton(onClick = onQueue) { Icon(Md3eIcons.Playlist, "播放队列") }
            IconButton(onClick = onSettings) { Icon(Md3eIcons.Settings, "设置") }
            IconButton(onClick = onAccount) {
                Icon(Md3eIcons.Person, if (loggedIn) "账户" else "登录")
            }
        })
}

@Composable
internal fun Md3eHomePage(runtime: Md3eRuntime, onLogin: () -> Unit, onPlay: PlayAction,
    onOpenPlaylist: (Long) -> Unit, onOpenAlbum: (Long) -> Unit, onRefresh: () -> Unit,
    modifier: Modifier = Modifier) {
    val state = runtime.home
    val dockInset = LocalMd3eDockInset.current
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // Seal DownloadPageV2 uses a 28 dp compact header spacer.
    val compactWindow = LocalConfiguration.current.screenWidthDp < 600
    val headerHeight = with(density) { if (compactWindow) 28.dp.toPx() else 0f }
    var headerOffset by remember(headerHeight) { mutableFloatStateOf(headerHeight) }
    val flingSpec = rememberSplineBasedDecay<Float>()
    val headerScroll = remember(headerHeight, flingSpec) {
        Md3eSealTopBarScroll(headerHeight, flingSpec,
            offset = { headerOffset }, onOffsetUpdate = { headerOffset = it })
    }
    val firstPlaylists = remember(state.playlists, state.playlistSections) {
        state.playlists.ifEmpty { state.playlistSections.firstOrNull()?.playlists.orEmpty() }
    }
    val morePlaylists = remember(state.playlistSections, firstPlaylists) {
        val seen = firstPlaylists.mapTo(mutableSetOf()) { it.id }
        state.playlistSections.mapNotNull { section ->
            section.playlists.filter { seen.add(it.id) }.takeIf { it.isNotEmpty() }?.let { section.title to it }
        }
    }
    val albums = remember(state.albums, state.daily) {
        state.albums.ifEmpty {
            state.daily.asSequence().filter { it.albumId > 0 && !it.album.isNullOrBlank() }
                .distinctBy { it.albumId }.take(12).map { song ->
                    NeteaseAlbum().apply {
                        id = song.albumId; name = song.album; artistName = song.artist
                        coverUrl = song.coverUrl; coverThumbPath = song.coverThumbPath
                    }
                }.toList()
        }
    }
    val songShelves = remember(state.sections) {
        state.sections.flatMap { section ->
            section.songs.chunked(HOME_SHELF_CHUNK).mapIndexed { index, songs -> Triple(section, index, songs) }
        }
    }
    val dailyShelves = remember(state.daily) { state.daily.chunked(HOME_SHELF_CHUNK) }
    var aiPrompt by remember { mutableStateOf("") }
    var aiOpen by remember { mutableStateOf(false) }
    var aiMode by remember { mutableStateOf<Boolean?>(null) }
    var aiPreference by remember { mutableStateOf(false) }
    var aiCount by remember { mutableFloatStateOf(20f) }
    var aiLoading by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf("") }
    var aiSongs by remember { mutableStateOf(emptyList<NeteaseSong>()) }
    var aiProgress by remember { mutableStateOf("") }
    var aiSummary by remember { mutableStateOf("") }
    var aiDetails by remember { mutableStateOf("") }
    LaunchedEffect(aiOpen) {
        while (aiOpen) {
            runtime.controller.pump()
            aiLoading = runtime.controller.aiLoading.peek()
            aiError = runtime.controller.aiError.peek().orEmpty()
            aiSongs = runtime.controller.aiSongs.peek().orEmpty()
            aiProgress = runtime.controller.aiProgress.peek().orEmpty()
            aiSummary = runtime.controller.aiSummary.peek().orEmpty()
            aiDetails = runtime.controller.aiDetails.peek().orEmpty()
            delay(150)
        }
    }
    Column(modifier.nestedScroll(headerScroll)) {
    Spacer(Modifier.height(with(density) { headerOffset.toDp() }))
    if (headerOffset <= 0.1f && headerHeight > 0f) HorizontalDivider(thickness = Dp.Hairline)
    LazyColumn(Modifier.weight(1f), state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp + dockInset),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "greeting") {
            Text(if (state.userName.isBlank()) "你好" else "你好，${state.userName}",
                fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        }
        item(key = "ai") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(aiPrompt, { aiPrompt = it }, Modifier.weight(1f),
                    placeholder = { Text("今天想听点什么呢？告诉你的ai吧！") },
                    singleLine = true, shape = RoundedCornerShape(18.dp))
                Box(Modifier.size(48.dp).clip(CircleShape).combinedClickable(
                    onClick = { if (aiPrompt.isNotBlank()) {
                        aiPreference = false; aiMode = null; aiLoading = false
                        aiError = ""; aiSongs = emptyList(); aiProgress = ""
                        aiSummary = ""; aiDetails = ""; aiOpen = true
                    } },
                    onLongClick = {
                        aiPreference = true
                        aiMode = true
                        aiLoading = false
                        aiCount = runtime.settings.intOf("aiTasteCount").let { if (it <= 0) 20 else it }
                            .coerceIn(1, 40).toFloat()
                        aiError = ""; aiSongs = emptyList(); aiProgress = ""
                        aiSummary = ""; aiDetails = ""
                        aiOpen = true
                    }
                ), contentAlignment = Alignment.Center) {
                    Icon(Md3eIcons.AutoAwesome, "打开 AI DJ")
                }
            }
        }
        if (firstPlaylists.isEmpty() && state.loading) item(key = "playlist_loading") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeTitle("推荐歌单")
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        if (firstPlaylists.isNotEmpty()) item(key = "playlists") {
            HomePlaylistShelf("推荐歌单", firstPlaylists.take(HOME_HEAD_SHELF), onOpenPlaylist)
        }
        dailyShelves.forEachIndexed { shelf, songs ->
            item(key = "daily_$shelf") {
                HomeSongPager(if (shelf == 0) "每日推荐" else "每日推荐 · ${shelf + 1}", songs,
                    { index -> onPlay { playRecommendation(shelf * HOME_SHELF_CHUNK + index) } },
                    { runtime.controller.enqueueNeteaseSong(it) })
            }
        }
        albums.chunked(HOME_ALBUM_CHUNK).forEachIndexed { shelf, chunk ->
            item(key = "albums_$shelf") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeTitle(if (shelf == 0) "推荐新碟" else "更多新碟")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(chunk, key = { it.id }) { album -> HomeAlbumCard(album) { onOpenAlbum(album.id) } }
                    }
                }
            }
        }
        items(songShelves, key = { "shelf_${it.first.id}_${it.second}" }) { (section, shelf, songs) ->
            HomeSongPager(if (shelf == 0) section.title else "${section.title} · ${shelf + 1}", songs,
                { index -> songs.getOrNull(index)?.let { song -> onPlay { playHomeRecommendation(section.id, song.id) } } },
                { runtime.controller.enqueueNeteaseSong(it) })
        }
        items(morePlaylists, key = { "playlist_section_${it.first}" }) { (title, playlists) ->
            HomePlaylistShelf(title, playlists, onOpenPlaylist)
        }
        firstPlaylists.drop(HOME_HEAD_SHELF).chunked(HOME_PLAYLIST_CHUNK).forEachIndexed { shelf, chunk ->
            item(key = "playlist_tail_$shelf") {
                HomePlaylistShelf(if (shelf == 0) "更多歌单" else "更多歌单 · ${shelf + 1}", chunk, onOpenPlaylist)
            }
        }
        if (!state.loading && firstPlaylists.isEmpty() && state.daily.isEmpty() &&
            state.sections.isEmpty() && albums.isEmpty()) item(key = "empty") {
            TextButton(onClick = onRefresh) { Text(state.error.ifBlank { "暂无推荐内容，点击刷新" }) }
        }
        if (state.loading && firstPlaylists.isNotEmpty()) item(key = "loading_more") {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (!state.loading && state.error.isNotBlank()) item(key = "retry") {
            TextButton(onClick = onRefresh) { Text(state.error) }
        }
        if (!state.loggedIn && state.loginError.isNotBlank()) item(key = "login") {
            TextButton(onClick = onLogin) { Text(state.loginError) }
        }
    }
    }
    if (aiOpen) AlertDialog(onDismissRequest = { aiOpen = false; aiPreference = false },
        title = { Text("AI DJ") },
        text = {
            Column(Modifier.widthIn(max = 360.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (aiPreference) {
                    Text("随机偏好推荐")
                    Text("推荐数量：${aiCount.toInt()} 首")
                    Slider(aiCount, { aiCount = it }, valueRange = 1f..40f, steps = 38)
                } else {
                    Text("请选择生成方式")
                    Row(Modifier.fillMaxWidth().selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Md3eSealSelectionItem(selected = aiMode == false, onClick = { aiMode = false },
                            modifier = Modifier.weight(1f)) { Text("创建歌单") }
                        Md3eSealSelectionItem(selected = aiMode == true, onClick = { aiMode = true },
                            modifier = Modifier.weight(1f)) { Text("生成播放列表") }
                    }
                }
                Button(onClick = {
                    val baseUrl = runtime.settings.str("aiBaseUrl")
                    val apiKey = runtime.settings.str("aiApiKey")
                    val model = runtime.settings.str("aiModel")
                    if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
                        aiError = "请先在设置中填写 AI API 地址、Key 和模型名称"
                        return@Button
                    }
                    aiLoading = true
                    aiError = ""; aiProgress = ""; aiSongs = emptyList()
                    val generate: dev.t1m3.qplayer.bridge.PlayerController.() -> Unit = {
                        generateAiPlaylist(baseUrl, apiKey, model,
                            if (aiPreference) "严格根据提供的听歌样本分析偏好，随机生成多种类型的歌曲，必须返回不同风格的真实歌曲，不要重复样本。" else aiPrompt,
                            if (aiPreference) aiCount.toInt().coerceIn(1, 40) else extractHomeAiCount(aiPrompt),
                            aiMode == true, if (aiPreference) false else runtime.settings.bool("aiExcludeLiked"),
                            false, "", "", runtime.settings.bool("aiForceKnowledge"), aiPreference)
                    }
                    if (aiMode == true) runtime.play(generate) else runtime.controller.generate()
                }, enabled = !aiLoading && (aiPreference || aiMode != null)) { Text("开始生成") }
                val statusKey = when {
                    aiError.isNotBlank() -> "error"
                    aiLoading -> "running"
                    aiSongs.isNotEmpty() -> "completed"
                    else -> "status"
                }
                Md3eSealStage(statusKey, Modifier.fillMaxWidth()) { visibleStatus ->
                    Column(Modifier.fillMaxWidth().animateContentSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (visibleStatus == "running") {
                            Md3eSealProgressRing(progress = -1f) {
                                Icon(Md3eIcons.AutoAwesome, null, Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.secondaryFixed)
                            }
                        }
                        if (visibleStatus != "status" || aiProgress.isNotBlank()) {
                            val statusText = when (visibleStatus) {
                                "error" -> aiError
                                "running" -> aiProgress.ifBlank { "正在生成" }
                                "completed" -> "已匹配 ${aiSongs.size} 首真实歌曲"
                                else -> aiProgress
                            }
                            Md3eSealStatusText(visibleStatus, statusText,
                                color = if (visibleStatus == "error") MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                showIndicator = true)
                        }
                        if (aiSummary.isNotBlank()) Text("AI 推荐说明：\n$aiSummary")
                        if (aiDetails.isNotBlank()) {
                            Text("AI 返回的推荐理由：")
                            Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                aiDetails.lines().forEach { line ->
                                    Text(line, fontWeight = if (line.isNotBlank() && !line.startsWith("  "))
                                        FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                        if (runtime.settings.bool("aiShowOutput") && aiError.isNotBlank()) {
                            Text("AI 原始输出/错误信息：", fontWeight = FontWeight.Bold)
                            Text(aiError, Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState()))
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { aiOpen = false }) { Text("关闭") } })
}

private fun extractHomeAiCount(request: String): Int =
    (Regex("(\\d{1,3})\\s*(首|首歌|songs?)?", RegexOption.IGNORE_CASE)
        .find(request)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 10).coerceIn(1, 100)

@Composable
private fun HomeTitle(title: String) {
    Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun HomePlaylistShelf(title: String, playlists: List<NeteasePlaylist>, onOpen: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)) {
            items(playlists, key = { it.id }) { playlist ->
                Column(Modifier.width(164.dp).clip(RoundedCornerShape(16.dp))
                    .clickable { onOpen(playlist.id) }) {
                    Artwork(playlist.coverThumbPath ?: playlist.coverUrl.orEmpty(),
                        Modifier.fillMaxWidth().aspectRatio(1f).md3eSharedCover("cover:playlist:${playlist.id}"))
                    Text(playlist.name ?: "未命名歌单", Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp),
                        maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text("${playlist.trackCount} 首歌曲", Modifier.padding(10.dp), fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HomeAlbumCard(album: NeteaseAlbum, onOpen: () -> Unit) {
    Column(Modifier.width(132.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Artwork(album.coverThumbPath ?: album.coverUrl.orEmpty(),
            Modifier.fillMaxWidth().aspectRatio(1f).md3eSharedCover("cover:album:${album.id}"))
        Text(album.name ?: "未知专辑", maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(album.artistName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeSongPager(title: String, songs: List<NeteaseSong>, onPlay: (Int) -> Unit,
    onEnqueue: (NeteaseSong) -> Unit) {
    if (songs.isEmpty()) return
    val pageCount = (songs.size + 2) / 3
    val pager = rememberPagerState(pageCount = { pageCount })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { HomeTitle(title) }
            if (pageCount > 1) Text("${pager.currentPage + 1} / $pageCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalPager(pager, modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(end = if (pageCount > 1) 16.dp else 0.dp),
            pageSpacing = 12.dp, key = { page -> "${songs.getOrNull(page * 3)?.id}_$page" }) { page ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { slot ->
                    val index = page * 3 + slot
                    val song = songs.getOrNull(index)
                    if (song == null) Spacer(Modifier.height(64.dp)) else {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .combinedClickable(onClick = { onPlay(index) }, onLongClick = { onEnqueue(song) })
                            .heightIn(min = 64.dp).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Artwork(song.coverThumbPath ?: song.coverUrl.orEmpty(), Modifier.size(48.dp))
                            Column(Modifier.weight(1f)) {
                                Text(song.name ?: "未知歌曲", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artist ?: "未知歌手", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Md3eIcons.ChevronRight, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
