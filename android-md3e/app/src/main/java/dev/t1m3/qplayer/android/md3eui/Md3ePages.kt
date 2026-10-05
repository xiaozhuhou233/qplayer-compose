@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.t1m3.qplayer.android.md3eui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.t1m3.qplayer.model.Track
import dev.t1m3.qplayer.netease.dto.NeteaseAlbum
import dev.t1m3.qplayer.netease.dto.NeteaseArtist
import dev.t1m3.qplayer.netease.dto.NeteasePlaylist
import dev.t1m3.qplayer.netease.dto.NeteaseSong

@Composable
internal fun Md3eNavigationBar(selected: String, showLocalTab: Boolean = true,
    onSelect: (String) -> Unit) {
    if (LocalClaudeDesign.current) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
            HorizontalDivider(thickness = .8.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp)) {
                listOf(Triple("home", Md3eIcons.Home, "主页"),
                    Triple("playlists", Md3eIcons.Playlist, "歌单"),
                    Triple("local", Md3eIcons.Library, "本地"),
                    Triple("search", Md3eIcons.Search, "搜索"))
                    .filter { it.first != "local" || showLocalTab }.forEach { (route, icon, title) ->
                        val active = route == selected
                        val ink = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(24.dp))
                            .clickable(role = Role.Tab) { onSelect(route) }.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(icon, null, Modifier.size(23.dp), tint = ink)
                            Text(title, color = ink, style = MaterialTheme.typography.labelMedium)
                        }
                    }
            }
        }
        return
    }
    NavigationBar(modifier = Modifier.height(80.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        listOf(
            Triple("home", Md3eIcons.Home, "主页"),
            Triple("playlists", Md3eIcons.Playlist, "歌单"),
            Triple("local", Md3eIcons.Library, "本地"),
            Triple("search", Md3eIcons.Search, "搜索"),
        ).filter { (route, _, _) -> route != "local" || showLocalTab }
            .forEach { (route, icon, label) ->
            NavigationBarItem(selected = selected == route, onClick = { onSelect(route) },
                icon = { Icon(icon, label) }, label = { Text(label, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}

@Composable
internal fun Md3ePlaylistsPage(state: PlaylistState, onLogin: () -> Unit, onRefresh: () -> Unit,
    onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    if (LocalClaudeDesign.current) {
        ClaudePlaylistsPage(state, onLogin, onRefresh, onOpen, modifier)
        return
    }
    var category by rememberSaveable { mutableIntStateOf(0) }
    val shown = state.mine.filter { if (category == 0) it.owned else !it.owned }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp + LocalMd3eDockInset.current)) {
        item { PageHeading("我的歌单", "网易云音乐", onRefresh, enabled = state.loggedIn) }
        if (!state.loggedIn) {
            item {
                EmptyPage("登录后查看创建和收藏的歌单") {
                    Button(onClick = onLogin) { Icon(Md3eIcons.Person, null); Spacer(Modifier.width(8.dp)); Text("登录网易云音乐") }
                }
            }
        } else {
            item {
                PrimaryTabRow(selectedTabIndex = category) {
                    Tab(selected = category == 0, onClick = { category = 0 }, text = { Text("我创建的") })
                    Tab(selected = category == 1, onClick = { category = 1 }, text = { Text("我收藏的") })
                }
            }
            if (shown.isEmpty()) item { EmptyPage(if (category == 0) "还没有创建的歌单" else "还没有收藏的歌单") }
            items(shown, key = { it.id }) { playlist -> PlaylistRow(playlist) { onOpen(playlist.id) } }
        }
    }
}

@Composable
private fun ClaudePlaylistsPage(state: PlaylistState, onLogin: () -> Unit, onRefresh: () -> Unit,
    onOpen: (Long) -> Unit, modifier: Modifier) {
    var category by rememberSaveable { mutableIntStateOf(0) }
    val shown = state.mine.filter { if (category == 0) it.owned else !it.owned }
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp)) {
        item {
            ClaudeSectionTitle("我的歌单", "收藏、整理，再慢慢听")
            ClaudeUnderline(Modifier.padding(top = 8.dp, bottom = 18.dp))
            ClaudeBadge(if (state.loggedIn) "网易云音乐 · 已登录" else "网易云音乐 · 未登录")
            Spacer(Modifier.height(18.dp))
        }
        if (!state.loggedIn) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("登录后查看你的歌单", fontFamily = ClaudeSerif, fontSize = 20.sp)
                Text("创建和收藏的音乐会在这里出现。", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
                Button(onClick = onLogin, modifier = Modifier.padding(top = 18.dp)) { Text("登录网易云音乐") }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = category == 0, onClick = { category = 0 }, label = { Text("我创建的") })
                    FilterChip(selected = category == 1, onClick = { category = 1 }, label = { Text("我收藏的") })
                    IconButton(onClick = onRefresh, enabled = !state.loading) { Icon(Md3eIcons.Refresh, "刷新歌单") }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (shown.isEmpty()) item {
                Text(if (category == 0) "还没有创建的歌单" else "还没有收藏的歌单",
                    fontFamily = ClaudeSerif, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 28.dp))
            }
            items(shown, key = { it.id }) { playlist -> ClaudePlaylistRow(playlist) { onOpen(playlist.id) } }
        }
    }
}

@Composable
private fun ClaudePlaylistRow(playlist: NeteasePlaylist, onOpen: () -> Unit) {
    Md3eCollectionContainer("cover:playlist:${playlist.id}", Modifier.fillMaxWidth(), corner = 8.dp) {
Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Artwork(playlist.coverThumbPath ?: playlist.coverUrl.orEmpty(),
            Modifier.size(62.dp).md3eSharedCover("cover:playlist:${playlist.id}"), corner = 8.dp)
        Column(Modifier.weight(1f)) {
            Text(playlist.name.orEmpty(), fontFamily = ClaudeSerif, fontSize = 16.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${playlist.trackCount} 首歌曲", fontFamily = ClaudeMono, fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        Icon(Md3eIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f))
}

@Composable
private fun PlaylistRow(playlist: NeteasePlaylist, onOpen: () -> Unit) {
    Md3eCollectionContainer("cover:playlist:${playlist.id}", Modifier.fillMaxWidth(), corner = 16.dp) {
Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).heightIn(min = 76.dp)
        .padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Artwork(playlist.coverThumbPath ?: playlist.coverUrl.orEmpty(),
            Modifier.size(60.dp).md3eSharedCover("cover:playlist:${playlist.id}"))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(playlist.name.orEmpty(), style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${playlist.trackCount} 首${playlist.creatorNickname?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Md3eIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    }
    HorizontalDivider(Modifier.padding(start = 94.dp, end = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun Md3ePlaylistDetail(state: PlaylistState, id: Long, onBack: () -> Unit, onRefresh: () -> Unit,
    onPlay: PlayAction, modifier: Modifier = Modifier) {
    if (LocalClaudeDesign.current) {
        ClaudePlaylistDetail(state, id, onRefresh, onPlay, modifier)
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp + LocalMd3eDockInset.current)) {
        item {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Artwork(state.cover, Modifier.size(108.dp).md3eSharedCover("cover:playlist:$id"))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.title.ifBlank { "正在加载歌单" }, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text("${state.tracks.size} 首歌曲", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { onPlay { playPlaylistTrack(0) } }, enabled = state.tracks.isNotEmpty()) {
                            Icon(Md3eIcons.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("播放全部")
                        }
                        IconButton(onClick = onRefresh, enabled = !state.loading) {
                            Icon(Md3eIcons.Refresh, "刷新歌单")
                        }
                    }
                }
            }
        }
        if (state.loading) item { CenterLoading("正在加载歌单") }
        if (!state.loading && state.tracks.isEmpty()) item { EmptyPage("歌单中还没有歌曲") }
        itemsIndexed(state.tracks, key = { index, song -> "$index-${song.id}" }) { index, song ->
            NeteaseSongRow(song, null) { onPlay { playPlaylistTrack(index) } }
        }
    }
}

@Composable
private fun ClaudePlaylistDetail(state: PlaylistState, id: Long, onRefresh: () -> Unit,
    onPlay: PlayAction, modifier: Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(vertical = 8.dp)) {
                    Artwork(state.cover, Modifier.fillMaxSize().md3eSharedCover("cover:playlist:$id"), corner = 8.dp)
                    Text("PLAYLIST ARCHIVE", Modifier.align(Alignment.TopStart).padding(start = 10.dp).rotate(-3f)
                        .background(MaterialTheme.colorScheme.background)
                        .border(.8.dp, MaterialTheme.colorScheme.outlineVariant)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                        fontFamily = ClaudeMono, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                Text(state.title.ifBlank { "正在加载歌单" }, style = MaterialTheme.typography.headlineMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(if (state.loading && state.tracks.isEmpty()) "正在整理曲目…" else "${state.tracks.size} 首歌曲",
                    fontFamily = ClaudeMono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp))
                Button(onClick = { onPlay { playPlaylistTrack(0) } }, enabled = state.tracks.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Icon(Md3eIcons.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("播放全部")
                }
            }
            ClaudeUnderline(Modifier.padding(top = 16.dp, bottom = 10.dp))
        }
        if (state.loading) item { CenterLoading("正在加载歌单") }
        if (!state.loading && state.tracks.isEmpty()) item { Text("歌单中还没有歌曲", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        itemsIndexed(state.tracks, key = { index, song -> "$index-${song.id}" }) { index, song ->
            Row(Modifier.fillMaxWidth().clickable { onPlay { playPlaylistTrack(index) } }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Artwork(song.coverThumbPath ?: song.coverUrl.orEmpty(), Modifier.size(48.dp), corner = 6.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(song.name.orEmpty(), fontFamily = ClaudeSerif, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist.orEmpty(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${index + 1}".padStart(2, '0'), fontFamily = ClaudeMono, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
        }
    }
}

@Composable
internal fun Md3eLocalPage(state: LocalState, onPermission: () -> Unit, onRefresh: () -> Unit,
    onPlay: PlayAction, onPlayBili: (Track) -> Unit = {}, modifier: Modifier = Modifier) {
    var category by rememberSaveable { mutableIntStateOf(0) }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp + LocalMd3eDockInset.current)) {
        item { PageHeading(if (category == 0) "本地音乐" else "B站缓存",
            "${if (category == 0) state.tracks.size else state.bili.size} 首", onRefresh,
            enabled = category == 0 && state.permissionGranted && !state.scanning) }
        item {
            PrimaryTabRow(selectedTabIndex = category) {
                Tab(selected = category == 0, onClick = { category = 0 }, text = { Text("本地音乐") })
                Tab(selected = category == 1, onClick = { category = 1 }, text = { Text("B站") })
            }
        }
        if (category == 1) {
            if (state.bili.isEmpty()) item { EmptyPage("还没有缓存 B 站视频") }
            itemsIndexed(state.bili, key = { _, track -> "bili-${track.biliBvid}-${track.biliCid}" }) { _, track ->
                LocalSongRow(track, null) { onPlayBili(track) }
            }
            return@LazyColumn
        }
        when {
            !state.permissionGranted -> item {
                EmptyPage("允许访问音频后显示本机歌曲") {
                    Button(onClick = onPermission) { Icon(Md3eIcons.Library, null); Spacer(Modifier.width(8.dp)); Text("允许访问音乐") }
                }
            }
            state.scanning -> item { CenterLoading("正在扫描本地音乐") }
            state.tracks.isEmpty() -> item { EmptyPage(state.error.ifBlank { "没有找到本地歌曲" }) {
                TextButton(onClick = onRefresh) { Text("重新扫描") }
            } }
        }
        if (state.error.isNotBlank() && state.tracks.isNotEmpty()) item {
            Text(state.error, Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        itemsIndexed(state.tracks, key = { index, track -> "$index-${track.contentUri ?: track.filePath}" }) { index, track ->
            LocalSongRow(track, null) { onPlay { play(index) } }
        }
    }
}

@Composable
internal fun Md3eSearchPage(state: SearchState, onSearch: (String, String) -> Unit,
    onLoadMore: () -> Unit, onClearHistory: () -> Unit, onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit, onPlay: PlayAction, modifier: Modifier = Modifier,
    onOpenPlayer: () -> Unit = {}, onBiliLogin: () -> Unit = {}, onBiliFavorites: () -> Unit = {},
    biliLoggedIn: Boolean = false) {
    var query by rememberSaveable { mutableStateOf(state.query) }
    var mode by rememberSaveable { mutableStateOf(state.mode) }
    val submit = { term: String ->
        query = term
        if (term.isNotBlank()) onSearch(term.trim(), mode)
    }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp + LocalMd3eDockInset.current)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline),
                    placeholder = { Text(if (mode == "bili") "搜索 B站" else "搜索歌曲、专辑、歌手") },
                    leadingIcon = { Icon(Md3eIcons.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(Md3eIcons.Close, "清空搜索")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit(query) }))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { submit(query) }, enabled = query.isNotBlank()) {
                        Icon(Md3eIcons.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("搜索")
                    }
                }
            }
        }
        item {
            val modes = listOf("song" to "歌曲", "album" to "专辑", "artist" to "歌手", "bili" to "B站")
            PrimaryTabRow(selectedTabIndex = modes.indexOfFirst { it.first == mode }.coerceAtLeast(0)) {
                modes.forEach { (value, label) ->
                    Tab(selected = mode == value, onClick = {
                        mode = value
                        if (query.isNotBlank()) onSearch(query.trim(), value)
                    }, text = { Text(label) })
                }
            }
        }
        if (mode == "bili") item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onBiliLogin) { Text(if (biliLoggedIn) "B站账号" else "登录 B站") }
                TextButton(onClick = onBiliFavorites) { Icon(Md3eIcons.Playlist, null, Modifier.size(18.dp)); Text("收藏夹") }
            }
        }
        if (query.isBlank()) {
            if (state.history.isNotEmpty()) {
                item { SearchSuggestions("最近搜索", state.history, onClearHistory) { submit(it) } }
            }
            if (state.hot.isNotEmpty()) item {
                SearchSuggestions("热门搜索", state.hot, null) { submit(it) }
            }
        } else if (state.query.isNotBlank()) {
            if (state.loading && (mode == "bili" || state.songs.isEmpty() && state.albums.isEmpty() && state.artists.isEmpty())) {
                item { CenterLoading("正在搜索") }
            }
            when (mode) {
                "bili" -> {
                    if (state.videoError.isNotBlank()) item {
                        Text(state.videoError, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
                    }
                    itemsIndexed(state.videos, key = { index, video -> "bili-$index-${video.bvid}" }) { _, video ->
                        Md3eBiliVideoRow(video) { onPlay { playBiliCollection(video) }; onOpenPlayer() }
                    }
                }
                "song" -> {
                    if (state.songs.isNotEmpty()) item { ListHeading("网易云歌曲", state.songs.size) }
                    itemsIndexed(state.songs, key = { index, song -> "song-$index-${song.id}" }) { index, song ->
                        NeteaseSongRow(song, index + 1) { onPlay { playSearchResult(index) } }
                    }
                    if (state.hasMore) item {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            TextButton(onClick = onLoadMore, enabled = !state.loading) {
                                Text(if (state.loading) "正在加载" else "加载更多")
                            }
                        }
                    }
                    if (state.local.isNotEmpty()) item { ListHeading("本地匹配", state.local.size) }
                    itemsIndexed(state.local, key = { index, track -> "local-$index-${track.contentUri ?: track.filePath}" }) { index, track ->
                        LocalSongRow(track, index + 1) { onPlay { playLocalSearchResult(index) } }
                    }
                }
                "album" -> items(state.albums, key = { it.id }) { album -> AlbumRow(album) { onOpenAlbum(album.id) } }
                "artist" -> items(state.artists, key = { it.id }) { artist -> ArtistRow(artist) { onOpenArtist(artist.id) } }
            }
            val count = when (mode) { "bili" -> state.videos.size; "album" -> state.albums.size; "artist" -> state.artists.size; else -> state.songs.size + state.local.size }
            if (!state.loading && count == 0) item { EmptyPage("没有找到相关结果") }
        }
    }
}

@Composable
private fun SearchSuggestions(title: String, terms: List<String>, onClear: (() -> Unit)?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (onClear != null) IconButton(onClick = onClear) { Icon(Md3eIcons.Delete, "清除搜索历史") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(terms) { term -> SuggestionChip(onClick = { onSelect(term) }, label = { Text(term) }) }
        }
    }
}

@Composable
internal fun Md3eArtistDetail(state: ArtistDetailState, onBack: () -> Unit, onAlbum: (Long) -> Unit,
    onPlay: PlayAction, onEnqueue: (NeteaseSong) -> Unit, modifier: Modifier = Modifier) {
    var menuSong by remember(state.id) { mutableStateOf<NeteaseSong?>(null) }
    menuSong?.let { song ->
        ModalBottomSheet(onDismissRequest = { menuSong = null }) {
            Text(song.name.orEmpty(), Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ListItem(
                headlineContent = { Text("添加到播放列表") },
                leadingContent = { Icon(Md3eIcons.Playlist, null) },
                modifier = Modifier.fillMaxWidth().clickable {
                    menuSong = null
                    onEnqueue(song)
                },
            )
            Spacer(Modifier.height(20.dp))
        }
    }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp + LocalMd3eDockInset.current)) {
        item {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Artwork(state.cover, Modifier.size(96.dp))
                Column {
                    Text(state.name.ifBlank { "正在加载歌手" }, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold)
                    if (state.description.isNotBlank()) Text(state.description, maxLines = 3,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.loading) item { CenterLoading("正在加载歌手") }
        if (state.songs.isNotEmpty()) item { ListHeading("热门歌曲", state.songs.size) }
        itemsIndexed(state.songs, key = { index, song -> "$index-${song.id}" }) { index, song ->
            NeteaseSongRow(song, index + 1, onLongClick = { menuSong = song }) { onPlay { playArtistSong(index) } }
        }
        if (state.albums.isNotEmpty()) item { ListHeading("专辑", state.albums.size) }
        items(state.albums, key = { it.id }) { album -> AlbumRow(album) { onAlbum(album.id) } }
        if (!state.loading && state.songs.isEmpty() && state.albums.isEmpty()) item { EmptyPage("暂无歌手内容") }
    }
}

@Composable
internal fun Md3eAlbumDetail(state: AlbumDetailState, id: Long, onBack: () -> Unit,
    onPlay: PlayAction, modifier: Modifier = Modifier) {
    if (LocalClaudeDesign.current) {
        ClaudeAlbumCollection(state, id, onPlay, modifier)
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp + LocalMd3eDockInset.current)) {
        item {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Artwork(state.cover, Modifier.size(108.dp).md3eSharedCover("cover:album:$id"))
                Column {
                    Text(state.name.ifBlank { "正在加载专辑" }, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                    Text(state.artistName, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.loading) item { CenterLoading("正在加载专辑") }
        itemsIndexed(state.tracks, key = { index, song -> "$index-${song.id}" }) { index, song ->
            NeteaseSongRow(song, index + 1) { onPlay { playAlbumTrack(index) } }
        }
        if (!state.loading && state.tracks.isEmpty()) item { EmptyPage("专辑中没有歌曲") }
    }
}

@Composable
private fun ClaudeAlbumCollection(state: AlbumDetailState, id: Long, onPlay: PlayAction, modifier: Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(vertical = 8.dp)) {
                    Artwork(state.cover, Modifier.fillMaxSize().md3eSharedCover("cover:album:$id"), corner = 8.dp)
                    Text("ALBUM ARCHIVE", Modifier.align(Alignment.TopStart).padding(start = 10.dp).rotate(-3f)
                        .background(MaterialTheme.colorScheme.background)
                        .border(.8.dp, MaterialTheme.colorScheme.outlineVariant)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                        fontFamily = ClaudeMono, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                Text(state.name.ifBlank { "专辑" }, style = MaterialTheme.typography.headlineMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                if (state.artistName.isNotBlank()) Text(state.artistName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 7.dp))
                Text(if (state.loading && state.tracks.isEmpty()) "正在整理曲目…" else "${state.tracks.size} 首歌曲",
                    fontFamily = ClaudeMono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp))
                Button(onClick = { onPlay { playAlbumTrack(0) } }, enabled = state.tracks.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Icon(Md3eIcons.PlayArrow, null); Spacer(Modifier.width(7.dp)); Text("播放全部")
                }
            }
        }
        if (state.loading && state.tracks.isEmpty()) item {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 16.dp))
        }
        itemsIndexed(state.tracks, key = { index, song -> "${song.id}_$index" }) { index, song ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text((index + 1).toString().padStart(2, '0'), Modifier.width(25.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.weight(1f).clickable { onPlay { playAlbumTrack(index) } }
                    .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(song.coverThumbPath ?: song.coverUrl.orEmpty(), Modifier.size(48.dp), corner = 6.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                        Text(song.name.orEmpty(), fontFamily = ClaudeSerif, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist.orEmpty(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Icon(Md3eIcons.Playlist, "加入队列", Modifier.size(20.dp).clickable { onPlay { enqueueNeteaseSong(song) } },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
        }
        if (!state.loading && state.tracks.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("暂无专辑歌曲", fontFamily = ClaudeSerif, fontSize = 20.sp)
                Text("专辑内容加载失败或为空", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String, onRefresh: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRefresh, enabled = enabled) { Icon(Md3eIcons.Refresh, "刷新") }
    }
}

@Composable
private fun DetailBar(title: String, onBack: () -> Unit, onRefresh: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Md3eIcons.Back, "返回") }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onRefresh != null) IconButton(onClick = onRefresh) { Icon(Md3eIcons.Refresh, "刷新") }
    }
}

@Composable
private fun ListHeading(title: String, count: Int) {
    Text("$title · $count", Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun NeteaseSongRow(song: NeteaseSong, number: Int?, onLongClick: (() -> Unit)? = null, onPlay: () -> Unit) {
    SongResultRow(song.coverThumbPath ?: song.coverUrl.orEmpty(), song.name.orEmpty(), song.artist.orEmpty(), number, onPlay, onLongClick)
}

@Composable
private fun LocalSongRow(track: Track, number: Int?, onPlay: () -> Unit) {
    SongResultRow(track.coverThumbPath ?: track.coverLocalPath.orEmpty(), track.title.orEmpty(), track.artist.orEmpty(), number, onPlay)
}

@Composable
private fun SongResultRow(cover: String, title: String, artist: String, number: Int?, onPlay: () -> Unit,
    onLongClick: (() -> Unit)? = null) {
    val interaction = if (onLongClick == null) Modifier.clickable(onClick = onPlay)
        else Modifier.combinedClickable(onClick = onPlay, onLongClick = onLongClick, onLongClickLabel = "歌曲选项")
    Row(Modifier.fillMaxWidth().then(interaction).heightIn(min = 64.dp)
        .padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (number != null) Text(number.toString(), Modifier.width(24.dp), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Artwork(cover, Modifier.size(48.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Md3eIcons.PlayArrow, "播放 $title", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun AlbumRow(album: NeteaseAlbum, onOpen: () -> Unit) {
    Md3eCollectionContainer("cover:album:${album.id}", Modifier.fillMaxWidth(), corner = 16.dp) {
Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Artwork(album.coverThumbPath ?: album.coverUrl.orEmpty(),
            Modifier.size(60.dp).md3eSharedCover("cover:album:${album.id}"))
        Column(Modifier.weight(1f)) {
            Text(album.name.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(album.artistName.orEmpty(), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Md3eIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    }
}

@Composable
private fun ArtistRow(artist: NeteaseArtist, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Artwork(artist.coverThumbPath ?: artist.coverUrl.orEmpty(), Modifier.size(60.dp))
        Text(artist.name.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Md3eIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CenterLoading(text: String) {
    Row(Modifier.fillMaxWidth().padding(28.dp), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyPage(message: String, action: @Composable (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}
