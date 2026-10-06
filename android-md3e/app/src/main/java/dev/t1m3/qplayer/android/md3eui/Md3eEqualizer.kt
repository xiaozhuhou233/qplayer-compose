// Ⓜ 2026-10-06: the listener's EQ（「加入 eq 调音和均衡器设置，放到设置中新加一个音效里」）.
// A device android.media.audiofx.Equalizer (+ BassBoost) attached to the PLAYING
// audio session; the backend fires a session hook per new MediaPlayer so the chain
// re-attaches on every track. Band levels live in SettingsCore as a CSV of
// millibels under "eqBands" (bass strength under "eqBass"), applied and persisted
// from the editor dialog.
package dev.t1m3.qplayer.android.md3eui

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.t1m3.qplayer.util.Logger

/** Owns the audiofx chain for the MD3E app. One Equalizer (+ BassBoost) on the
 *  live audio session; detached and released whenever the session goes away or
 *  the toggle is off. The transition engine's own low-end hand-over uses its own
 *  instances and is untouched. */
internal class Md3eEqualizer {
    private var equalizer: Equalizer? = null
    private var bass: BassBoost? = null

    var bandCount: Int = 0
        private set
    var lowerMillibels: Int = -1500
        private set
    var upperMillibels: Int = 1500
        private set
    var centerFreqsMilliHz: List<Int> = emptyList()
        private set

    /** Read the band geometry off a session (the playing one, or the output mix
     *  as a fallback). Fills the defaults the editor dialog needs even before
     *  anything plays: 5 bands ±15 dB is what every real device reports, but the
     *  range is only trusted once a probe succeeds. */
    @Synchronized
    fun probe(sessionId: Int): Boolean {
        if (bandCount > 0) return true
        return try {
            val eq = Equalizer(0, sessionId)
            try {
                bandCount = eq.numberOfBands.toInt()
                val range = eq.bandLevelRange
                lowerMillibels = range[0].toInt()
                upperMillibels = range[1].toInt()
                centerFreqsMilliHz = (0 until bandCount).map { eq.getCenterFreq(it.toShort()) }
            } finally {
                eq.release()
            }
            true
        } catch (failure: Throwable) {
            Logger.warn("eq probe failed on session {}: {}", sessionId, failure.toString())
            false
        }
    }

    @Synchronized
    fun attach(sessionId: Int, levels: List<Int>, bassStrength: Int) {
        detach()
        try {
            val eq = Equalizer(0, sessionId)
            equalizer = eq
            val count = eq.numberOfBands.toInt()
            val lower = eq.bandLevelRange[0].toInt()
            val upper = eq.bandLevelRange[1].toInt()
            for (index in 0 until count) {
                val millibels = levels.getOrNull(index)?.coerceIn(lower, upper) ?: continue
                eq.setBandLevel(index.toShort(), millibels.toShort())
            }
            eq.enabled = true
            if (bassStrength > 0) {
                try {
                    val boost = BassBoost(0, sessionId)
                    bass = boost
                    boost.setStrength(bassStrength.coerceIn(0, 1000).toShort())
                    boost.enabled = true
                } catch (failure: Throwable) {
                    Logger.warn("bass boost unavailable: {}", failure.toString())
                }
            }
        } catch (failure: Throwable) {
            Logger.warn("eq attach failed on session {}: {}", sessionId, failure.toString())
            detach()
        }
    }

    @Synchronized
    fun detach() {
        runCatching { equalizer?.enabled = false }
        runCatching { equalizer?.release() }
        equalizer = null
        runCatching { bass?.release() }
        bass = null
    }
}

/** The editor behind 关于/音效/「均衡器调节」: one slider per band (applied live
 *  through the runtime, persisted per change), a bass slider, a reset. */
@Composable
internal fun Md3eEqualizerDialog(runtime: Md3eRuntime, onDismiss: () -> Unit) {
    val sessionId = runtime.audioBackend.currentAudioSessionId()
    LaunchedEffect(sessionId) { runtime.equalizer.probe(sessionId) }
    val bandCount = runtime.equalizer.bandCount.takeIf { it > 0 } ?: 5
    val lower = runtime.equalizer.lowerMillibels
    val upper = runtime.equalizer.upperMillibels
    val centers = runtime.equalizer.centerFreqsMilliHz
    var levels by remember {
        mutableStateOf(runtime.eqBandLevels().toMutableList())
    }
    var bass by remember { mutableIntStateOf(runtime.eqBassStrength()) }

    fun bandLabel(index: Int): String {
        val milliHz = centers.getOrNull(index) ?: return "频段 ${index + 1}"
        val hz = milliHz / 1000
        return if (hz >= 1000) "${hz / 1000}kHz" else "${hz}Hz"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("均衡器") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!runtime.settings.bool("eqEnabled")) {
                    Text("均衡器开关当前是关的：滑杆会保存数值，打开「均衡器」开关后生效。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                repeat(bandCount) { index ->
                    val value = (levels.getOrNull(index) ?: 0)
                        .coerceIn(lower, upper).toFloat()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bandLabel(index), Modifier.width(52.dp),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center)
                        Slider(
                            value = value,
                            onValueChange = { newValue ->
                                val millibels = newValue.toInt()
                                if (index < levels.size) levels[index] = millibels
                                else while (levels.size <= index) levels.add(0)
                                if (index < levels.size) levels[index] = millibels
                                runtime.setEqBandLevel(index, millibels)
                            },
                            valueRange = lower.toFloat()..upper.toFloat(),
                            modifier = Modifier.weight(1f))
                        Text("${value.toInt() / 100.0}dB", Modifier.width(64.dp),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.End)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("低音", Modifier.width(52.dp),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center)
                    Slider(
                        value = bass.toFloat(),
                        onValueChange = { newValue ->
                            bass = newValue.toInt()
                            runtime.setEqBass(bass)
                        },
                        valueRange = 0f..1000f,
                        modifier = Modifier.weight(1f))
                    Text("${bass * 100 / 1000}%", Modifier.width(64.dp),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.End)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        runtime.resetEq()
                        levels = mutableListOf()
                        bass = 0
                    }) { Text("全部归零") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}
