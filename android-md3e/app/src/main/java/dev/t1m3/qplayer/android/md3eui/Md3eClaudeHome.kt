@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package dev.t1m3.qplayer.android.md3eui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.pager.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.t1m3.qplayer.netease.dto.*
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
        style = MaterialTheme.typography.labelSmall, fontFamily = RecommendationHandwriting,
        color = MaterialTheme.colorScheme.onPrimaryContainer)
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
internal fun ClaudePlaylistCard(playlist: NeteasePlaylist, onClick: () -> Unit) {
    Surface(Modifier.width(166.dp).clickable(onClick = onClick), shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(.8.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(9.dp)) {
            Box {
                Artwork(playlist.coverThumbPath ?: playlist.coverUrl.orEmpty(), Modifier.fillMaxWidth().aspectRatio(1f).md3eSharedCover("cover:playlist:${playlist.id}"), corner = 8.dp)
                Text("CURATED", Modifier.align(Alignment.TopStart).padding(7.dp).rotate(-3f)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = .93f)).padding(horizontal = 6.dp, vertical = 3.dp),
                    fontFamily = ClaudeMono, fontSize = 8.sp, letterSpacing = 1.sp)
                Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(27.dp)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = .94f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Md3eIcons.PlayArrow, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(playlist.name ?: "未命名歌单", Modifier.padding(top = 10.dp), fontFamily = ClaudeSerif,
                fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Text("${playlist.trackCount} 首歌曲", Modifier.padding(top = 5.dp, bottom = 3.dp),
                fontFamily = ClaudeMono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
                                Column(Modifier.weight(1f)) {
                                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .combinedClickable(onClick = { play(index) }, onLongClick = { enqueue(song) })
                                        .padding(vertical = 9.dp, horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Artwork(song.coverThumbPath ?: song.coverUrl.orEmpty(), Modifier.size(44.dp), corner = 8.dp)
                                        Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                                            Text(song.name.orEmpty(), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(song.artist.orEmpty(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            val reason = song.recommendReason.orEmpty().ifBlank { song.genre.orEmpty() }.trim()
                                            if (reason.isNotBlank()) ClaudeBadge(reason, Modifier.padding(top = 4.dp))
                                        }
                                        IconButton(onClick = { enqueue(song) }) {
                                            Icon(Md3eIcons.Playlist, "添加到播放队列", Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f), thickness = .6.dp)
                                }
                            }
                        } ?: Spacer(Modifier.height(64.dp))
                    }
                }
            }
        }
    }
}

