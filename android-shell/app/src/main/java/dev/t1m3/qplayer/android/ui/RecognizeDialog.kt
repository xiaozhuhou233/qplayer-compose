package dev.t1m3.qplayer.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.t1m3.qplayer.android.recognize.AfpFingerprint
import dev.t1m3.qplayer.android.recognize.SongRecognizer
import dev.t1m3.qplayer.netease.AudioMatchClient
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 听歌识曲 — the dialog, its two animations and the engine's lifecycle (round 37).
 *
 * <p>The user's brief: 「点击即可弹出对话框，然后对话框里有简介和开始录音按钮，点击就获取手机麦克风权限识别附近
 * 音乐，识别时显示三条竖杠来回伸缩那个 md 加载动画，找到了就自动停止录音，然后使用 md 那个变化图形的加载图像
 * 加载，直到找到并播放识别出来的歌」. So the dialog has four states and they are what the UI shows:
 *
 * <ul>
 *   <li>{@link Phase#INTRO} — the blurb and 开始录音. The permission is requested on that click, not when
 *   the dialog opens (a permission prompt in front of an explanation is what gets denied).</li>
 *   <li>{@link Phase#LISTENING} — recording, with {@link ListeningBars}: three bars stretching back and
 *   forth, which is the animation the user asked for by name.</li>
 *   <li>{@link Phase#FOUND} — the match landed, the recording has already stopped, and {@link MorphLoader}
 *   (the shape-changing Material loader) runs while the song is fetched and started.</li>
 *   <li>then the dialog closes itself, because the song is playing.</li>
 * </ul>
 *
 * <p>All the machinery is in {@code android/recognize} and {@code netease/AudioMatchClient}; this file is
 * only the surface. The engine is built when listening starts and released when the dialog goes away —
 * including on a configuration change, which is what `DisposableEffect` is for.
 */
private enum class Phase { INTRO, LISTENING, FOUND }

@Composable
fun RecognizeDialogHost(visible: Boolean, onDismiss: () -> Unit, playById: (Long) -> Unit) {
    if (!visible) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(Phase.INTRO) }
    var status by remember { mutableStateOf("") }
    var match by remember { mutableStateOf<AudioMatchClient.Match?>(null) }
    var engine by remember { mutableStateOf<AfpFingerprint?>(null) }
    var recognizer by remember { mutableStateOf<SongRecognizer?>(null) }
    var wantStart by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            recognizer?.cancel()
            recognizer = null
            val fp = engine
            engine = null
            // The WebView has to be destroyed on the main thread, and this dispose may run off it.
            if (fp != null) Handler(Looper.getMainLooper()).post { fp.close() }
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            wantStart = true
        } else {
            phase = Phase.INTRO
            status = "没有麦克风权限：在系统设置里允许后再点一次"
        }
    }

    fun stopListening() {
        recognizer?.cancel()
        recognizer = null
    }

    fun beginListening() {
        status = "正在听…把手机靠近音源"
        phase = Phase.LISTENING
        scope.launch {
            val fp = withContext(Dispatchers.Main) { engine ?: AfpFingerprint(context).also { engine = it } }
            val listener = object : SongRecognizer.Listener {
                override fun onListening(seconds: Float, note: String?) {
                    scope.launch(Dispatchers.Main) {
                        if (phase == Phase.LISTENING) {
                            status = note ?: "正在听…%.0f 秒".format(seconds)
                        }
                    }
                }

                override fun onMatching(seconds: Float) {
                    scope.launch(Dispatchers.Main) {
                        if (phase == Phase.LISTENING) status = "听到了一段，正在识别…"
                    }
                }

                override fun onFound(found: AudioMatchClient.Match) {
                    scope.launch(Dispatchers.Main) {
                        // The recogniser has already stopped and released the microphone by now.
                        recognizer = null
                        match = found
                        phase = Phase.FOUND
                        status = "找到了：${found}"
                        playById(found.id)
                        // Long enough for the loader to be seen and for the track to start.
                        kotlinx.coroutines.delay(900)
                        onDismiss()
                    }
                }

                override fun onFailed(why: String) {
                    scope.launch(Dispatchers.Main) {
                        recognizer = null
                        phase = Phase.INTRO
                        status = why
                    }
                }
            }
            val rec = SongRecognizer(fp, listener)
            recognizer = rec
            rec.start()
        }
    }

    LaunchedEffect(wantStart) {
        if (wantStart) {
            wantStart = false
            beginListening()
        }
    }

    AlertDialog(
        onDismissRequest = { stopListening(); onDismiss() },
        title = { Text("听歌识曲") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "放一段附近的音乐，识别出来就直接播。识别在手机上完成指纹、只把指纹发给网易云，" +
                        "不录音保存，也不上传原始音频。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(14.dp))
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    when (phase) {
                        Phase.INTRO -> Unit
                        Phase.LISTENING -> ListeningBars(color = MaterialTheme.colorScheme.primary)
                        Phase.FOUND -> MorphLoader(color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (status.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
                match?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("${it.title} — ${it.artist}", style = MaterialTheme.typography.titleSmall)
                }
            }
        },
        confirmButton = {
            when (phase) {
                Phase.INTRO -> TextButton(onClick = {
                    status = ""
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        wantStart = true
                    } else {
                        permission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }) { Text("开始录音") }

                Phase.LISTENING -> TextButton(onClick = { stopListening(); phase = Phase.INTRO; status = "已停止" }) {
                    Text("停止")
                }

                Phase.FOUND -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = { stopListening(); onDismiss() }) { Text("关闭") }
        }
    )
}

/** The 「三条竖杠来回伸缩」 the user asked for: three bars whose heights run a travelling sine. */
@Composable
private fun ListeningBars(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bars")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    Canvas(modifier.fillMaxWidth().height(44.dp)) {
        val bar = size.width / 7f
        val gap = bar * 0.5f
        val maxHeight = size.height
        for (i in 0..2) {
            val wave = (sin(phase + i * 0.85f) + 1f) / 2f
            val height = maxHeight * (0.30f + 0.70f * wave)
            val left = bar * (1 + i * 2) + gap * i
            drawRoundRect(
                color = color,
                topLeft = Offset(left, (maxHeight - height) / 2f),
                size = Size(bar, height),
                cornerRadius = CornerRadius(bar / 2f)
            )
        }
    }
}

/**
 * The Material shape-morph loader: one form rotating and changing between a circle, a rounded square
 * and a four-lobed flower, which is what the user meant by 「md 那个变化图形」.
 *
 * <p>Drawn rather than animated as a path list on purpose: the morph is one polar radius whose lobes
 * come and go with the same clock that turns it, so there is nothing to interpolate between and no
 * keyframes to keep in sync.
 */
@Composable
private fun MorphLoader(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "morph")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    Canvas(modifier.fillMaxWidth().height(44.dp)) {
        val radius = size.minDimension / 2f * 0.9f
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val lobes = (sin(phase * 2f) + 1f) / 2f          // 0 = round, 1 = four-lobed
        val path = Path()
        val steps = 96
        for (i in 0..steps) {
            val angle = i / steps.toFloat() * 2f * PI.toFloat()
            val r = radius * (1f - 0.18f * lobes * (0.5f + 0.5f * cos(4f * angle)))
            val x = centerX + r * cos(angle + phase)
            val y = centerY + r * sin(angle + phase)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color = color)
    }
}
