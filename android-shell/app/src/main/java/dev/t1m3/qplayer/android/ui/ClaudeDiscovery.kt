package dev.t1m3.qplayer.android.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.t1m3.qplayer.netease.dto.NeteaseSong

@Composable
internal fun ClaudeSearchDiscovery(onSearch: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ClaudeSectionTitle("从一种心情开始", "DISCOVER YOUR NEXT FAVORITE")
        listOf(listOf("爵士", "民谣"), listOf("独立音乐", "电子"), listOf("古典", "轻音乐")).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { i, genre ->
                    Surface(onClick = { onSearch(genre) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                        color = if (i == 0) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(.8.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Default.Album, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(genre, Modifier.weight(1f), fontFamily = ClaudeSerif, fontSize = 18.sp)
                                Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        Text("每次寻找，都是一次新的相遇。", fontFamily = ClaudeSerif, fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
internal fun ClaudeSongShelf(title: String, songs: List<NeteaseSong>, play: (Int) -> Unit, enqueue: (NeteaseSong) -> Unit) {
    if (songs.isEmpty()) return
    val count = (songs.size + 2) / 3
    val pager = rememberPagerState(pageCount = { count })
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ClaudeSectionTitle(title) }
            Text("${pager.currentPage + 1} / $count", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalPager(pager, pageSpacing = 12.dp, verticalAlignment = Alignment.Top) { page ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(.8.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                    repeat(3) { slot ->
                        val index = page * 3 + slot
                        songs.getOrNull(index)?.let { song ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text((index + 1).toString().padStart(2, '0'), Modifier.width(24.dp),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ClaudeSongRow(song.name ?: "未知歌曲", song.artist.orEmpty(), null, song.coverThumbPath ?: song.coverUrl,
                                    { play(index) }, { enqueue(song) }, Modifier.weight(1f))
                            }
                        } ?: Spacer(Modifier.height(64.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ClaudeCollection(title: String, label: String, subtitle: String, coverPath: String,
    songs: List<NeteaseSong>, loading: Boolean, sharedKey: String, play: (Int) -> Unit, enqueue: (NeteaseSong) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.widthIn(max = 240.dp).fillMaxWidth().padding(vertical = 16.dp)) {
                    ClaudeCover(rememberCoverBitmap(null, coverPath, 768), "封面", Modifier.fillMaxWidth().aspectRatio(1f).sharedCover(sharedKey))
                    Text(label, Modifier.align(Alignment.TopStart).padding(start = 10.dp).rotate(-3f)
                        .background(MaterialTheme.colorScheme.background).border(.8.dp, MaterialTheme.colorScheme.outlineVariant)
                        .padding(horizontal = 8.dp, vertical = 5.dp), fontFamily = ClaudeMono, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                Text(title, style = MaterialTheme.typography.headlineMedium)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 7.dp))
                Text(if (loading && songs.isEmpty()) "正在整理曲目…" else "${songs.size} 首歌曲", fontFamily = ClaudeMono,
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
                Button(onClick = { play(0) }, enabled = songs.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(7.dp)); Text("播放全部")
                }
            }
        }
        if (loading && songs.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 16.dp)) }
        if (!loading && songs.isEmpty()) item { ClaudeEmpty("这里还没有歌曲", "稍后重新打开，或向歌单添加歌曲。") }
        itemsIndexed(songs, key = { index, song -> "${song.id}_$index" }) { index, song ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text((index + 1).toString().padStart(2, '0'), Modifier.width(25.dp), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                ClaudeSongRow(song.name ?: "未知歌曲", song.artist ?: "未知歌手", null, song.coverThumbPath ?: song.coverUrl,
                    { play(index) }, { enqueue(song) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun ClaudeAppearancePreview() {
    Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(.8.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ClaudeBadge("CLAUDE DESIGN")
            Text("给音乐一点留白。", fontFamily = ClaudeSerif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp)
            ClaudeUnderline()
            Text("暖纸色 · 陶土橙 · 黑胶唱片", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Serif / Sans / Mono", fontFamily = ClaudeMono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
