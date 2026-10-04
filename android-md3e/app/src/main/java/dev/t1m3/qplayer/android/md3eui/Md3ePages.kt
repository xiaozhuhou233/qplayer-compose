@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.t1m3.qplayer.android.md3eui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.t1m3.qplayer.model.Track
import dev.t1m3.qplayer.netease.dto.NeteaseAlbum
import dev.t1m3.qplayer.netease.dto.NeteaseArtist
import dev.t1m3.qplayer.netease.dto.NeteasePlaylist
import dev.t1m3.qplayer.netease.dto.NeteaseSong

@Composable
internal fun Md3eNavigationBar(selected: String, showLocalTab: Boolean = true,
    onSelect: (String) -> Unit) {
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
private fun PlaylistRow(playlist: NeteasePlaylist, onOpen: () -> Unit) {
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
    HorizontalDivider(Modifier.padding(start = 94.dp, end = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun Md3ePlaylistDetail(state: PlaylistState, id: Long, onBack: () -> Unit, onRefresh: () -> Unit,
    onPlay: PlayAction, modifier: Modifier = Modifier) {
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
internal fun Md3eLocalPage(state: LocalState, onPermission: () -> Unit, onRefresh: () -> Unit,
    onPlay: PlayAction, modifier: Modifier = Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp + LocalMd3eDockInset.current)) {
        item { PageHeading("本地音乐", "${state.tracks.size} 首歌曲", onRefresh, enabled = state.permissionGranted && !state.scanning) }
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
            LocalSongRow(track, index + 1) { onPlay { play(index) } }
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
                Text("搜索", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, placeholder = { Text(if (mode == "bili") "B站视频" else "歌曲、专辑或歌手") },
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
    onPlay: PlayAction, modifier: Modifier = Modifier) {
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
            NeteaseSongRow(song, index + 1) { onPlay { playArtistSong(index) } }
        }
        if (state.albums.isNotEmpty()) item { ListHeading("专辑", state.albums.size) }
        items(state.albums, key = { it.id }) { album -> AlbumRow(album) { onAlbum(album.id) } }
        if (!state.loading && state.songs.isEmpty() && state.albums.isEmpty()) item { EmptyPage("暂无歌手内容") }
    }
}

@Composable
internal fun Md3eAlbumDetail(state: AlbumDetailState, id: Long, onBack: () -> Unit,
    onPlay: PlayAction, modifier: Modifier = Modifier) {
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
private fun NeteaseSongRow(song: NeteaseSong, number: Int?, onPlay: () -> Unit) {
    SongResultRow(song.coverThumbPath ?: song.coverUrl.orEmpty(), song.name.orEmpty(), song.artist.orEmpty(), number, onPlay)
}

@Composable
private fun LocalSongRow(track: Track, number: Int, onPlay: () -> Unit) {
    SongResultRow(track.coverThumbPath ?: track.coverLocalPath.orEmpty(), track.title.orEmpty(), track.artist.orEmpty(), number, onPlay)
}

@Composable
private fun SongResultRow(cover: String, title: String, artist: String, number: Int?, onPlay: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onPlay).heightIn(min = 64.dp)
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
