@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.t1m3.qplayer.android.md3eui

import dev.t1m3.qplayer.android.md3eui.Md3eAdaptiveSheet as ModalBottomSheet

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The original song options sheet, shared by all song lists. */
@Composable
internal fun Modifier.songQueueMenu(title: String, onPlay: () -> Unit, onEnqueue: () -> Unit): Modifier {
    var open by remember(title) { mutableStateOf(false) }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            Text(title, Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ListItem(
                headlineContent = { Text("添加到播放列表") },
                leadingContent = { Icon(Md3eIcons.Playlist, null) },
                modifier = Modifier.fillMaxWidth().claudeClickable {
                    open = false
                    onEnqueue()
                },
            )
            Spacer(Modifier.height(20.dp))
        }
    }
    return claudeClickable(onClick = onPlay, onLongClick = { open = true }, onLongClickLabel = "歌曲选项")
}
