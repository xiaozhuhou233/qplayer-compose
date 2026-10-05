package dev.t1m3.qplayer.android.md3eui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.t1m3.qplayer.settings.SettingSpec
import dev.t1m3.qplayer.settings.SettingsCatalog
import kotlin.math.roundToInt

// Only rows with an MD3E consumer are presented. The layout and controls follow
// the old SettingsCatalog-driven Android screen.
private val md3eSettingKeys = setOf(
    SettingsCatalog.LOW_SPEC_MODE_KEY, SettingsCatalog.PAGE_TRANSITION_KEY,
    "claudeDesign", "darkMode", "showLocalTab", "monet", "paletteStyle", "paletteChroma", "maxCacheSizeMB",
    "lyricFontSize", "lyricLineSpacing", "lyricSpring", "lyricScale",
    "lyricGlow", "lyricShadow", "lyricLinearAnim", "lyricMd3Color",
    "lyricProgressStyle", SettingsCatalog.COVER_BACKGROUND_KEY,
    "action:clearCache", "action:openRepo", "action:checkUpdate",
    "logCaptureEnabled", "action:logExport",
)

private fun supportedSetting(spec: SettingSpec): Boolean = spec.key in md3eSettingKeys ||
    spec.category == SettingsCatalog.PLAYBACK || spec.category == SettingsCatalog.AI ||
    spec.category == SettingsCatalog.ACE_STEP

@Composable
internal fun Md3eSettingsPage(runtime: Md3eRuntime, modifier: Modifier = Modifier) {
    val settings = runtime.settings
    val revision = runtime.settingsRevision
    val categories = remember { settings.categories().filter { category ->
        settings.rows(category).any(::supportedSetting)
    } }
    var category by remember { mutableStateOf(categories.firstOrNull().orEmpty()) }
    Column(modifier) {
        Row(Modifier.horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { item ->
                if (item == category) Button(onClick = { category = item }) { Text(item) }
                else OutlinedButton(onClick = { category = item }) { Text(item) }
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(settings.rows(category).filter(::supportedSetting), key = { it.key }) { spec ->
                Md3eSettingRow(spec, runtime, revision)
            }
        }
    }
}

@Composable
private fun Md3eSettingRow(spec: SettingSpec, runtime: Md3eRuntime,
    @Suppress("UNUSED_PARAMETER") revision: Int) {
    val settings = runtime.settings
    if (spec.dependsOn.isNotBlank() && !settings.bool(spec.dependsOn)) return
    val current = settings.intOf(spec.key).coerceIn(spec.min, spec.max)
    val description = spec.provider.takeIf { it.isNotBlank() }?.let(settings::info)
        ?.takeIf { it.isNotBlank() } ?: spec.desc
    Card(shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(spec.title, fontWeight = FontWeight.Medium)
                    if (description.isNotBlank()) Text(description, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (spec.type) {
                    SettingSpec.SWITCH -> Switch(settings.bool(spec.key),
                        { runtime.updateSetting(spec.key, it) })
                    SettingSpec.STEPPER -> {
                        IconButton(onClick = { runtime.updateSetting(spec.key, current - spec.step) },
                            enabled = current > spec.min) { Text("−", fontSize = 22.sp) }
                        Text(formatMd3eSettingValue(spec, current),
                            Modifier.widthIn(min = 52.dp), textAlign = TextAlign.Center)
                        IconButton(onClick = { runtime.updateSetting(spec.key, current + spec.step) },
                            enabled = current < spec.max) { Text("+", fontSize = 22.sp) }
                    }
                    SettingSpec.SLIDER -> Text(formatMd3eSettingValue(spec, current), fontSize = 12.sp)
                    SettingSpec.ACTION -> TextButton(onClick = { runtime.invokeSetting(spec.action) }) {
                        Text(spec.button.ifBlank { "打开" })
                    }
                }
            }
            when (spec.type) {
                SettingSpec.SLIDER -> Slider(current.toFloat(), onValueChange = { value ->
                    val step = spec.step.coerceAtLeast(1)
                    val snapped = spec.min + ((value - spec.min) / step).roundToInt() * step
                    runtime.updateSetting(spec.key, snapped.coerceIn(spec.min, spec.max))
                }, valueRange = spec.min.toFloat()..spec.max.toFloat(),
                    steps = ((spec.max - spec.min) / spec.step.coerceAtLeast(1) - 1).coerceAtLeast(0))
                SettingSpec.SEGMENTED, SettingSpec.RADIO, SettingSpec.DROPDOWN ->
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        spec.options.forEachIndexed { index, option ->
                            if (index == settings.intOf(spec.key))
                                Button(onClick = {}, enabled = false) { Text(option, maxLines = 1) }
                            else OutlinedButton(onClick = { runtime.updateSetting(spec.key, index) }) {
                                Text(option, maxLines = 1)
                            }
                        }
                    }
                SettingSpec.TEXT -> {
                    var value by remember(spec.key, revision) { mutableStateOf(settings.str(spec.key)) }
                    OutlinedTextField(value = value, onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text(spec.hint) })
                    Button(onClick = { runtime.updateSetting(spec.key, value) }) {
                        Text(spec.button.ifBlank { "应用" })
                    }
                }
            }
        }
    }
}

private fun formatMd3eSettingValue(spec: SettingSpec, value: Int): String {
    val shown = if (spec.scale == 1) value.toString() else
        String.format(java.util.Locale.US, "%.2f", value.toFloat() / spec.scale)
    return shown + spec.unit
}
