package dev.t1m3.qplayer.android.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.t1m3.qplayer.bridge.PlayerController

@Composable
internal fun ClaudeLibrary(state: PlayerUiState, controller: PlayerController, onLogin: () -> Unit,
    openPlaylist: (Long, String) -> Unit, openBili: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    val playlists = remember(state.myPlaylists, tab) { state.myPlaylists.filter { if (tab == 0) it.owned else !it.owned } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    ClaudeSectionTitle("我的歌单", "共收录 ${state.myPlaylists.size} 个歌单")
                    Spacer(Modifier.height(8.dp))
                    ClaudeBadge(if (state.loggedIn) "网易云音乐 · 已登录" else "你的私人音乐收藏")
                }
                IconButton(onClick = { if (state.loggedIn) controller.loadMyPlaylists() else onLogin() }) { Icon(Icons.Default.Refresh, "同步歌单") }
                IconButton(onClick = { if (state.loggedIn) creating = true else onLogin() }) { Icon(Icons.Default.Add, "新建歌单") }
            }
        }
        item {
            Surface(onClick = openBili, shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer, border = BorderStroke(.8.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VideoLibrary, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("B站收藏夹")
                        Text(if (state.biliLoggedIn) "收藏的声音与画面" else "登录 B站 后查看收藏", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
        item {
            Row {
                ClaudeChoice("我创建的 · ${state.myPlaylists.count { it.owned }}", tab == 0, { tab = 0 }, Modifier.weight(1f))
                ClaudeChoice("我收藏的 · ${state.myPlaylists.count { !it.owned }}", tab == 1, { tab = 1 }, Modifier.weight(1f))
            }
        }
        if (!state.loggedIn) item {
            ClaudeEmpty("收藏，让好音乐有迹可循", "登录网易云账号，找回你的歌单。", "登录账号", onLogin)
        } else if (playlists.isEmpty()) item {
            ClaudeEmpty(if (tab == 0) "还没有创建歌单" else "还没有收藏歌单", "把喜欢的声音放在一起。",
                if (tab == 0) "创建歌单" else "重新同步", { if (tab == 0) creating = true else controller.loadMyPlaylists() })
        }
        items(playlists, key = { it.id }) { playlist ->
            PlaylistListRow(playlist, onClick = { openPlaylist(playlist.id, playlist.coverThumbPath ?: playlist.coverUrl ?: "") },
                onDelete = { controller.deletePlaylist(playlist.id) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = .6.dp)
        }
    }
    if (creating) AlertDialog(onDismissRequest = { creating = false }, title = { Text("创建一张歌单") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("给这段声音起个名字") }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            controller.createPlaylist(name.trim()); name = ""; creating = false
        }) { Text("创建") } }, dismissButton = { TextButton(onClick = { creating = false }) { Text("取消") } })
}

@Composable
internal fun ClaudeEmpty(title: String, detail: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.Album, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) OutlinedButton(onClick = onAction) { Text(action) }
    }
}

@Composable
internal fun ClaudeLocal(state: PlayerUiState, controller: PlayerController) {
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    val context = LocalContext.current
    val activity = remember(context) {
        var current = context
        while (current is android.content.ContextWrapper && current !is ComposeQPlayerActivity) current = current.baseContext
        current as? ComposeQPlayerActivity
    }
    val indexed = remember(state.tracks, query, tab, group) {
        state.tracks.withIndex().filter { (_, track) ->
            val matches = query.isBlank() || "${track.title} ${track.artist} ${track.album}".contains(query, ignoreCase = true)
            matches && (group == null || (if (tab == 1) track.album.orEmpty().ifBlank { "未知专辑" }
                else track.artist.orEmpty().ifBlank { "未知歌手" }) == group)
        }
    }
    val groups = remember(state.tracks, query, tab) {
        state.tracks.withIndex().filter { (_, t) -> query.isBlank() || "${t.title} ${t.artist} ${t.album}".contains(query, true) }
            .groupBy { (_, t) -> if (tab == 1) t.album.orEmpty().ifBlank { "未知专辑" } else t.artist.orEmpty().ifBlank { "未知歌手" } }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    ClaudeSectionTitle("本地音乐", "${state.tracks.size} 首歌曲 · 留在身边的声音")
                }
                IconButton(onClick = { activity?.requestAudioPermission() }) { Icon(Icons.Default.Refresh, "重新扫描本地音乐") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = indexed.isNotEmpty(), onClick = { controller.playLocalTracks(indexed.map { it.value }, 0) }) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Text("播放全部")
                }
                OutlinedButton(onClick = { selecting = !selecting; selected = emptySet() }) { Text(if (selecting) "取消多选" else "多选") }
                if (selecting) TextButton(enabled = selected.isNotEmpty(), onClick = {
                    selected.sorted().forEach { state.tracks.getOrNull(it)?.let(controller::enqueueTrack) }
                    selected = emptySet(); selecting = false
                }) { Text("入队 ${selected.size}") }
            }
        }
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("搜索本地歌曲、专辑或歌手") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(12.dp))
            Row {
                listOf("歌曲", "专辑", "歌手").forEachIndexed { i, title ->
                    ClaudeChoice(title, tab == i, { tab = i; group = null }, Modifier.weight(1f))
                }
            }
        }
        if (group != null) item { TextButton(onClick = { group = null }) { Text("‹  全部${if (tab == 1) "专辑" else "歌手"} / $group") } }
        if (state.tracks.isEmpty()) item {
            ClaudeEmpty("让音乐回到身边", "授权后扫描设备上的音乐文件。", "授权并扫描", { activity?.requestAudioPermission() })
        } else if (indexed.isEmpty()) item { ClaudeEmpty("没有找到这段声音", "换一个关键词试试。") }
        if (tab != 0 && group == null) {
            items(groups.entries.toList(), key = { it.key }) { entry ->
                Surface(onClick = { group = entry.key }, color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp), border = BorderStroke(.8.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val track = entry.value.first().value
                        ClaudeCover(rememberCoverBitmap(track.coverBytes, track.coverThumbPath ?: track.coverLocalPath, 256), entry.key, Modifier.size(52.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(entry.key, style = MaterialTheme.typography.titleMedium)
                            Text("${entry.value.size} 首歌曲", fontFamily = ClaudeMono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        } else items(indexed, key = { "${it.value.contentUri ?: it.value.filePath}_${it.index}" }) { (index, track) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) Checkbox(index in selected, { checked -> selected = if (checked) selected + index else selected - index })
                ClaudeSongRow(track.title ?: "未知歌曲", track.artist ?: "未知歌手", track.coverBytes,
                    track.coverThumbPath ?: track.coverLocalPath,
                    onClick = { if (selecting) selected = if (index in selected) selected - index else selected + index
                        else controller.playLocalTracks(indexed.map { it.value }, indexed.indexOfFirst { it.index == index }) },
                    onEnqueue = { controller.enqueueTrack(track) }, modifier = Modifier.weight(1f))
            }
        }
    }
    LaunchedEffect(state.tracks) { selected = emptySet() }
}
