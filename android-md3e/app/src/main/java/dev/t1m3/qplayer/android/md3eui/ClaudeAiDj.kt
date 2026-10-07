package dev.t1m3.qplayer.android.md3eui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import dev.t1m3.qplayer.netease.dto.NeteaseSong
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Stable
internal class ClaudeAiDjState {
    var shown by mutableStateOf(false)
    var dismissRequested by mutableStateOf(false)
    var session by mutableIntStateOf(0)
    var prompt by mutableStateOf("")
    var random by mutableStateOf(false)
    var count by mutableFloatStateOf(20f)
    var outputFinished by mutableStateOf(false)
    var ownsRequest by mutableStateOf(false)
    var requestGeneration by mutableLongStateOf(0L)
    var dockStretch by mutableFloatStateOf(0f)
    var phase by mutableStateOf("idle")
    var error by mutableStateOf("")
    var progress by mutableStateOf("")
    var output by mutableStateOf("")
    var songs by mutableStateOf(emptyList<NeteaseSong>())
    var source by mutableStateOf(Rect.Zero)
    var inputBounds by mutableStateOf(Rect.Zero)
    var logoBounds by mutableStateOf(Rect.Zero)
    fun open(text: String, randomMode: Boolean, initialCount: Int) {
        if (phase == "running" || shown) return
        prompt = text.trim(); random = randomMode
        count = initialCount.coerceIn(1, 40).toFloat()
        source = if (randomMode) logoBounds else inputBounds
        outputFinished = false; ownsRequest = false; error = ""; progress = ""; output = ""; songs = emptyList()
        phase = if (randomMode) "choosing" else "running"
        session++; dismissRequested = false; shown = true
    }
}
internal val LocalClaudeAiDj = staticCompositionLocalOf<ClaudeAiDjState?> { null }
private val DjOrange = Color(0xFFE36038)
private val DjCream = Color(0xFFFBF1E1)

@Composable
internal fun ClaudeAiDjInput(runtime: Md3eRuntime, modifier: Modifier = Modifier) {
    val dj = LocalClaudeAiDj.current ?: return
    var prompt by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val submit = {
        if (prompt.isNotBlank()) {
            keyboard?.hide(); focus.clearFocus()
            dj.open(prompt, false, 20)
        }
    }
    Row(modifier.fillMaxWidth().claudeEntrance(1), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(prompt, { prompt = it },
            Modifier.weight(1f).onGloballyPositioned { dj.inputBounds = it.boundsInRoot() }
                .graphicsLayer { alpha = if (dj.shown && !dj.random) 0f else 1f }
                .onPreviewKeyEvent {
                    if ((it.key == Key.Enter || it.key == Key.NumPadEnter) && it.type == KeyEventType.KeyUp) {
                        submit(); true
                    } else false
                }, enabled = dj.phase != "running", placeholder = { Text("想听什么？") },
            singleLine = true, shape = RoundedCornerShape(50),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }, onDone = { submit() }))
        Box(Modifier.size(48.dp).onGloballyPositioned { dj.logoBounds = it.boundsInRoot() }
            .graphicsLayer { alpha = if (dj.shown && dj.random) 0f else 1f }
            .claudeClickable(enabled = dj.phase != "running", role = Role.Button, onClickLabel = "随机推荐") {
                keyboard?.hide(); focus.clearFocus()
                dj.open("", true, runtime.settings.intOf("aiTasteCount").let { if (it > 0) it else 20 })
            }, contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_claude_ai_dj), "AI DJ 随机推荐", Modifier.size(44.dp))
        }
    }
}

/** A single retained surface: input -> editorial result card -> measured mini-player bounds. */
@Composable
internal fun ClaudeAiDjHost(runtime: Md3eRuntime, dj: ClaudeAiDjState,
    player: Md3ePlayerExpansionState, onDock: () -> Unit) {
    val currentDock by rememberUpdatedState(onDock)
    val animate = claudeMotionEnabled()
    val reveal = remember { Animatable(0f) }
    val tilt = remember { Animatable(0f) }
    val gather = remember { Animatable(0f) }
    val typing = remember { Animatable(0f) }
    var contactStart by remember { mutableFloatStateOf(1f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val paper = MaterialTheme.colorScheme.surfaceContainerLow
    val sliderInteraction = remember { MutableInteractionSource() }
    fun cancel() {
        if (dj.phase == "docking" || dj.dismissRequested) return
        if (dj.ownsRequest) runtime.controller.cancelAiPlaylistGeneration()
        dj.ownsRequest = false
        dj.phase = "cancelled"
        dj.dismissRequested = true
    }
    LaunchedEffect(dj.session, dj.phase) {
        if (dj.phase != "running") return@LaunchedEffect
        val controller = runtime.controller
        if (controller.aiLoading.peek()) {
            dj.error = "已有推荐正在生成，请完成后重试。"; dj.phase = "error"
            return@LaunchedEffect
        }
        val base = runtime.settings.str("aiBaseUrl")
        val key = runtime.settings.str("aiApiKey")
        val model = runtime.settings.str("aiModel")
        if (base.isBlank() || key.isBlank() || model.isBlank()) {
            dj.error = "请先在设置中填写 AI API 地址、Key 和模型名称。"; dj.phase = "error"
            return@LaunchedEffect
        }
        val count = if (dj.random) dj.count.roundToInt().coerceIn(1, 40) else
            (Regex("(\\d{1,3})\\s*(首|首歌|songs?)?", RegexOption.IGNORE_CASE)
                .find(dj.prompt)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 10).coerceIn(1, 100)
        val request = if (dj.random) "严格根据提供的听歌样本分析偏好，随机生成多种类型的歌曲，必须返回不同风格的真实歌曲，不要重复样本。" else dj.prompt
        dj.outputFinished = false
        dj.error = ""; dj.output = ""; dj.songs = emptyList()
        dj.progress = "正在准备 AI 推荐…"
        try {
            dj.ownsRequest = true
            dj.requestGeneration = controller.prepareAiPlaylist(base, key, model, request, count,
                if (dj.random) false else runtime.settings.bool("aiExcludeLiked"),
                runtime.settings.bool("aiForceKnowledge"), dj.random)
            do {
                delay(100)
                // The runtime owns controller pumping; read only its published state here.
                dj.progress = controller.aiProgress.peek().orEmpty()
                dj.output = listOf(controller.aiSummary.peek().orEmpty(), controller.aiDetails.peek().orEmpty())
                    .filter { it.isNotBlank() }.joinToString("\n\n")
            } while (controller.aiLoading.peek())
            dj.error = controller.aiError.peek().orEmpty()
            dj.songs = if (dj.error.isBlank()) controller.aiSongs.peek().orEmpty() else emptyList()
            if (dj.error.isBlank() && dj.songs.isEmpty()) dj.error = "没有匹配到可播放的歌曲，请换个描述再试。"
            dj.phase = if (dj.error.isBlank()) "result" else "error"
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            dj.error = error.message ?: "生成失败，请重试。"; dj.phase = "error"
        }
    }
    LaunchedEffect(dj.session, dj.shown) {
        if (dj.shown) {
            reveal.snapTo(0f); tilt.snapTo(0f); gather.snapTo(0f); typing.snapTo(0f); dj.dockStretch = 0f
            if (animate) reveal.animateTo(1f, tween(ClaudeOneTake.CARRY, easing = ClaudeOneTake.ExpoOut))
            else reveal.snapTo(1f)
        }
    }
    LaunchedEffect(dj.phase, dj.shown, dj.dismissRequested) {
        if (dj.dismissRequested) return@LaunchedEffect
        if (dj.phase == "result" && dj.shown) {
            currentDock()
            if (animate) typing.animateTo(1f, tween((dj.output.length * 12).coerceIn(700, 4_000), easing = androidx.compose.animation.core.LinearEasing))
            else typing.snapTo(1f)
            dj.outputFinished = true
        }
        if (dj.phase == "docking" && dj.shown) {
            if (animate) {
                // OM.flyThrough tilt establishes depth, then morphRect carries the card to the dock.
                tilt.animateTo(1f, ClaudeOneTake.snappy())
                try {
                    gather.animateTo(1f, tween(ClaudeOneTake.CARRY, easing = ClaudeOneTake.ExpoOut)) {
                        // Contact begins only within 3/4 of the measured dock height.
                        // Both halves use OM.expoOut and end with the card, without an independent timer.
                        val contact = ((value - contactStart) / (1f - contactStart).coerceAtLeast(.001f)).coerceIn(0f, 1f)
                        dj.dockStretch = if (contact < .4f) ClaudeOneTake.ExpoOut.transform(contact / .4f)
                            else 1f - ClaudeOneTake.ExpoOut.transform((contact - .4f) / .6f)
                    }
                } finally {
                    dj.dockStretch = 0f
                }
            } else gather.snapTo(1f)
            dj.dockStretch = 0f
            dj.shown = false; dj.phase = "idle"
        }
    }
    LaunchedEffect(dj.dismissRequested) {
        if (dj.dismissRequested && dj.shown) {
            if (animate) reveal.animateTo(0f, tween(ClaudeOneTake.TRAVEL, easing = ClaudeOneTake.ExpoOut))
            dj.shown = false
        }
    }
    if (!dj.shown) return
    // Completion has one explicit exit: the bottom button. Back cancels only unfinished work.
    BackHandler { if (dj.phase !in setOf("result", "docking")) cancel() }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }
        .semantics { paneTitle = "aidj"; isTraversalGroup = true }) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val inset = with(density) { 22.dp.toPx() }
        val cardWidth = (width - 2 * inset).coerceAtMost(with(density) { 460.dp.toPx() })
        val cardHeight = (height * .72f).coerceAtMost(with(density) { 610.dp.toPx() })
        val target = Rect((width - cardWidth) / 2f, (height - cardHeight) / 2f,
            (width + cardWidth) / 2f, (height + cardHeight) / 2f)
        val start = if (dj.source.width > 0) dj.source.translate(-origin) else target
        val dock = if (player.miniBounds.width > 0) player.miniBounds.translate(-origin) else
            Rect(inset, height - with(density) { 140.dp.toPx() }, width - inset, height - with(density) { 88.dp.toPx() })
        SideEffect {
            val travel = kotlin.math.abs(dock.top - target.top).coerceAtLeast(1f)
            contactStart = (1f - dock.height * .75f / travel).coerceIn(.8f, .97f)
        }
        fun mix(a: Rect, b: Rect, t: Float) = Rect(lerp(a.left,b.left,t), lerp(a.top,b.top,t), lerp(a.right,b.right,t), lerp(a.bottom,b.bottom,t))
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(Color(0xFF29251F).copy(alpha = .24f * reveal.value * (1f - gather.value)))
        }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* Modal scrim consumes taps without dismissing results. */ })
        Surface(Modifier.width(with(density) { cardWidth.toDp() }).height(with(density) { cardHeight.toDp() })
            .graphicsLayer {
                val g = gather.value
                val frame = mix(mix(start, target, reveal.value), dock, g)
                shape = RoundedCornerShape(28.dp)
                clip = true
                shadowElevation = 18.dp.toPx() * (1f - g)
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = frame.left; translationY = frame.top
                scaleX = frame.width / cardWidth; scaleY = frame.height / cardHeight
                rotationX = -18f * tilt.value * (1f - g)
                cameraDistance = 14f * density.density
                alpha = 1f - ((g - .88f) / .12f).coerceIn(0f, 1f)
            }.drawBehind {
                drawRect(androidx.compose.ui.graphics.lerp(
                    androidx.compose.ui.graphics.lerp(paper, DjOrange, reveal.value), paper, gather.value))
            }, shape = RoundedCornerShape(28.dp), color = Color.Transparent,
            contentColor = DjCream) {
            Column(Modifier.fillMaxSize().padding(22.dp).graphicsLayer {
                alpha = ((reveal.value - .2f) / .65f).coerceIn(0f, 1f) * (1f - gather.value)
            }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(30.dp).height(3.dp).background(DjCream.copy(alpha = .5f), RoundedCornerShape(2.dp)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("aidj", Modifier.weight(1f), fontFamily = ClaudeSerif, fontSize = 38.sp)
                }
                Text(if (dj.random) "交给偶然，也交给你的喜好。" else dj.prompt, maxLines = 2, fontSize = 13.sp)
                HorizontalDivider(color = DjCream.copy(alpha = .25f))
                if (dj.random) {
                    Text("${dj.count.roundToInt()} 首歌曲", fontFamily = ClaudeSerif, fontSize = 23.sp)
                    Slider(dj.count, { dj.count = it.roundToInt().toFloat() }, enabled = dj.phase == "choosing",
                        valueRange = 1f..40f, steps = 38, interactionSource = sliderInteraction,
                        colors = SliderDefaults.colors(thumbColor = DjCream, activeTrackColor = DjCream,
                            inactiveTrackColor = DjCream.copy(alpha = .25f), disabledThumbColor = DjCream,
                            disabledActiveTrackColor = DjCream.copy(alpha = .75f)))
                    if (dj.phase == "choosing") Text("选好数量后，点击开始生成", fontSize = 12.sp)
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (dj.phase == "running") {
                        SongChecklistWriting(Modifier.align(Alignment.CenterHorizontally).size(160.dp),
                            paperColor = DjCream, backdropColor = null, animate = animate)
                        Text(dj.progress.ifBlank { "正在生成…" }, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        // Keep this status line while matching; final prose is revealed once below.
                    }
                    if (dj.phase == "error") {
                        Text(dj.error, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        TextButton(onClick = { dj.phase = "running" }, colors = ButtonDefaults.textButtonColors(contentColor = DjCream)) { Text("重试") }
                    }
                    if (dj.phase == "result" || dj.phase == "docking") {
                        Text("已为你找到 ${dj.songs.size} 首歌曲", fontFamily = ClaudeSerif, fontSize = 22.sp)
                        if (dj.output.isNotBlank()) Text(dj.output.take((dj.output.length * typing.value).roundToInt()), fontSize = 14.sp)
                        dj.songs.forEachIndexed { index, song ->
                            Row(Modifier.claudeEntrance(index), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                Text((index + 1).toString().padStart(2, '0'), fontFamily = ClaudeMono, fontSize = 12.sp)
                                Text("${song.name.orEmpty()} · ${song.artist.orEmpty()}", fontSize = 13.sp)
                            }
                        }
                    }
                }
                val actionLabel = when {
                    dj.phase == "choosing" -> "开始生成"
                    dj.phase == "running" || (dj.phase == "result" && !dj.outputFinished) -> "取消"
                    else -> "完成"
                }
                Button(onClick = {
                    when {
                        dj.phase == "choosing" -> dj.phase = "running"
                        dj.phase == "running" || (dj.phase == "result" && !dj.outputFinished) -> cancel()
                        dj.phase == "result" -> {
                            var started = false
                            runtime.play { started = confirmAiPlaylistPlayback(dj.requestGeneration) }
                            if (started) {
                                dj.ownsRequest = false
                                currentDock(); dj.phase = "docking"
                            } else {
                                dj.error = "推荐结果已失效，请重新生成。"
                                dj.phase = "error"
                            }
                        }
                        dj.phase == "error" -> cancel()
                    }
                }, enabled = dj.phase !in setOf("docking", "cancelled") && !dj.dismissRequested,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = DjCream, contentColor = DjOrange,
                        disabledContainerColor = DjCream, disabledContentColor = DjOrange)) {
                    Text(actionLabel, Modifier.padding(vertical = 6.dp), fontSize = 14.sp)
                }
            }
        }
    }
}

