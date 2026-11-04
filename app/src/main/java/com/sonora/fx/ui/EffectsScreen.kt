package com.sonora.fx.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sonora.fx.FxApplication
import com.sonora.fx.engine.FxEngine
import com.sonora.fx.engine.FxService
import com.sonora.fx.settings.EQPreset
import java.util.Locale

/**
 * Stage 2 controls: enough to drive and test the engine. The full EQ screen
 * with the live curve comes in Stage 3.
 */
@Composable
fun EffectsScreen() {
    val context = LocalContext.current
    val s = FxApplication.settings
    // Re-read everything whenever any setting changes (incl. per-device EQ switches).
    val version by s.version.collectAsState()
    val status by FxEngine.status.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Reading the state here ties this scope to every settings change.
            if (version >= 0) {
                SwitchRow("Sonora FX", s.enabled) {
                    s.enabled = it
                    FxService.sync(context)
                }
                Text(
                    when {
                        !s.enabled -> "Off"
                        !status.running -> "Starting…"
                        else -> "Running · ${status.sessions} audio session" +
                            (if (status.sessions == 1) "" else "s") +
                            (if (status.catchAll) " · catching every app" else "")
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                status.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                SwitchRow("Catch Every App", s.catchAll) { s.catchAll = it }
                SwitchRow("Limiter", s.limiterOn) { s.limiterOn = it }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Tone", style = MaterialTheme.typography.titleMedium)
                        DbSlider("Bass", s.bassDB, -18.0, 18.0) { s.bassDB = it }
                        DbSlider("Treble", s.trebleDB, -18.0, 18.0) { s.trebleDB = it }
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SwitchRow("Equalizer · ${s.eqPresetName}", s.eqOn) { s.eqOn = it }
                        DbSlider("Preamp", s.eqPreampDB, -12.0, 12.0) { s.eqPreampDB = it }
                        val bands = s.eqBands
                        bands.forEachIndexed { i, b ->
                            DbSlider(freqLabel(b.frequency), b.gain.toDouble(), -12.0, 12.0) { v ->
                                val cur = s.eqBands.toMutableList()
                                cur[i] = cur[i].copy(gain = v.toFloat())
                                s.eqBands = cur
                                s.eqPresetName = "Custom"
                            }
                        }
                        OutlinedButton(onClick = {
                            s.eqBands = EQPreset.flatBands()
                            s.eqPreampDB = 0.0
                            s.eqPresetName = EQPreset.flat.name
                        }) { Text("Reset to Flat") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DbSlider(label: String, value: Double, min: Double, max: Double, onChange: (Double) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange((Math.round(it * 2) / 2.0)) },
            valueRange = min.toFloat()..max.toFloat(),
            modifier = Modifier.weight(1f),
        )
        Text(
            String.format(Locale.US, "%+.1f dB", value),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(64.dp),
        )
    }
}

private fun freqLabel(hz: Float): String =
    if (hz >= 1000f) "${(hz / 1000f).let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }} kHz"
    else "${hz.toInt()} Hz"
