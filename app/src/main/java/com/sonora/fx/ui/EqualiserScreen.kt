package com.sonora.fx.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.fx.FxApplication
import com.sonora.fx.engine.EqCurve
import com.sonora.fx.perdevice.PerDeviceEq
import com.sonora.fx.settings.EQPreset
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

@Composable
fun EqualiserScreen(onBack: () -> Unit) {
    val s = FxApplication.settings
    val version by s.version.collectAsState()
    val outputLabel by PerDeviceEq.label.collectAsState()

    @Suppress("UNUSED_VARIABLE") val v = version
    val eqOn = s.eqOn
    val preset = s.eqPresetName
    val bands = s.eqBands
    val preamp = s.eqPreampDB
    val bass = s.bassDB
    val treble = s.trebleDB
    val bassHz = s.bassFrequency
    val trebleHz = s.trebleFrequency
    val auto = s.autoPreamp
    val perDevice = s.perDeviceEq
    val limiter = s.limiterOn
    val ceiling = s.limiterCeilingDB
    val release = s.limiterReleaseMs
    val userPresets = s.userPresets
    val autoCut = EqCurve.autoCut(EqCurve.gains(bands, bass, bassHz, treble, trebleHz))

    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<EQPreset?>(null) }

    Screen("Equaliser", onBack) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Equaliser", color = Sx.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(if (eqOn) preset else "Off", color = Sx.Sub, fontSize = 17.sp)
            }
            SxSwitch(eqOn) { s.eqOn = it }
        }

        SxCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Speaker, null, tint = Sx.Accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Output", color = Sx.Text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (perDevice) "EQ for: $outputLabel" else "One EQ for every output",
                        color = Sx.Sub, fontSize = 16.sp,
                    )
                }
            }
            SwitchRow("Separate EQ per device", checked = perDevice) { s.perDeviceEq = it }
        }

        SxCard(padding = 0.dp) {
            CurveView(bands.map { it.frequency.toDouble() }, bands.map { it.gain }, bass, bassHz, treble, trebleHz, bands, eqOn)
        }

        VerticalEq(
            gains = bands.map { it.gain },
            labels = bands.map { bandLabel(it.frequency) },
            active = eqOn,
            range = 12f,
        ) { i, g ->
            val cur = s.eqBands.toMutableList()
            if (i in cur.indices && cur[i].gain != g) {
                cur[i] = cur[i].copy(gain = g)
                s.eqBands = cur
                s.eqPresetName = "Custom"
            }
        }

        SxCard {
            ValueSlider("Pre-amp", fmtDb(preamp), linFrac(preamp, -12.0, 12.0),
                { s.eqPreampDB = linVal(it, -12.0, 12.0, 0.5) }, { s.eqPreampDB = 0.0 })
            SwitchRow(
                "Auto pre-amp (prevent clipping)",
                when {
                    !auto -> "Off"
                    !eqOn -> "Applies while the equalizer is on"
                    autoCut == 0f -> "No cut needed"
                    else -> String.format(Locale.US, "%.1f dB applied", autoCut).replace("-", "−")
                },
                auto,
            ) { s.autoPreamp = it }
        }

        SxCard {
            CardTitle(Icons.Outlined.RadioButtonChecked, "Tone")
            ValueSlider("Bass", fmtDb(bass), linFrac(bass, -18.0, 18.0),
                { s.bassDB = linVal(it, -18.0, 18.0, 0.5) }, { s.bassDB = 0.0 })
            ValueSlider("Bass Corner", fmtHz(bassHz), logFrac(bassHz, 40.0, 400.0),
                { s.bassFrequency = logVal(it, 40.0, 400.0, 5.0) }, { s.bassFrequency = 120.0 })
            ValueSlider("Treble", fmtDb(treble), linFrac(treble, -18.0, 18.0),
                { s.trebleDB = linVal(it, -18.0, 18.0, 0.5) }, { s.trebleDB = 0.0 })
            ValueSlider("Treble Corner", fmtHz(trebleHz), logFrac(trebleHz, 2000.0, 14000.0),
                { s.trebleFrequency = logVal(it, 2000.0, 14000.0, 100.0) }, { s.trebleFrequency = 6000.0 })
            Footnote(
                "Sonora's Tone shelves, drawn into the curve above. Here they ride on the " +
                    "equaliser (on with it), and the auto pre-amp makes room for them too.",
                Modifier.padding(top = 8.dp),
            )
        }

        SxCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { CardTitle(Icons.Filled.Compress, "Limiter") }
                SxSwitch(limiter) { s.limiterOn = it }
            }
            Column(Modifier.alpha(if (limiter) 1f else 0.45f)) {
                ValueSlider("Ceiling", String.format(Locale.US, "%.1f dBFS", ceiling), linFrac(ceiling, -6.0, 0.0),
                    { s.limiterCeilingDB = linVal(it, -6.0, 0.0, 0.1) }, { s.limiterCeilingDB = -0.5 })
                ValueSlider("Release", "${release.toInt()} ms", logFrac(release, 10.0, 1000.0),
                    { s.limiterReleaseMs = logVal(it, 10.0, 1000.0, 5.0) }, { s.limiterReleaseMs = 120.0 })
                Footnote(
                    "Catches the peaks that heavy EQ or a positive pre-amp would otherwise clip. Leave this on.",
                    Modifier.padding(top = 8.dp),
                )
            }
        }

        SxCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Presets", color = Sx.Text, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                HeaderAction(Icons.Outlined.AddCircleOutline, "Save") { saving = true }
                Spacer(Modifier.width(8.dp))
                HeaderAction(Icons.Filled.Replay, "Flat") { s.applyPreset(EQPreset.flat) }
            }
            Spacer(Modifier.height(14.dp))
            val all = EQPreset.builtIn + userPresets
            all.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { p ->
                        val mine = p in userPresets
                        PresetTile(
                            name = p.name,
                            selected = p.name == preset,
                            modifier = Modifier.weight(1f),
                            onTap = {
                                s.applyPreset(p)
                                if (!s.eqOn) s.eqOn = true
                            },
                            onLongPress = if (mine) ({ deleting = p }) else null,
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (userPresets.isNotEmpty()) {
                Footnote("Long-press one of your presets to delete it.", Modifier.padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (saving) {
        SavePresetDialog(
            onDismiss = { saving = false },
            onSave = { name ->
                val p = EQPreset(name = name, bands = s.eqBands)
                s.userPresets = s.userPresets.filter { it.name != name } + p
                s.eqPresetName = name
                saving = false
            },
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Sx.Card,
            title = { Text("Delete \"${p.name}\"?", color = Sx.Text) },
            confirmButton = {
                TextButton(onClick = {
                    s.userPresets = s.userPresets.filter { it.id != p.id }
                    deleting = null
                }) { Text("Delete", color = Sx.Accent) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = Sx.Sub) } },
        )
    }
}

@Composable
fun CardTitle(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        Icon(icon, null, tint = Sx.Text, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = Sx.Text, fontSize = 23.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HeaderAction(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Sx.Accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Sx.Accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PresetTile(
    name: String,
    selected: Boolean,
    modifier: Modifier,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)?,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Sx.Accent else Sx.Tile)
            .pointerInput(name, onLongPress != null) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress?.invoke() },
                )
            }
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name, color = if (selected) Color.Black else Sx.Text, fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SavePresetDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Sx.Card,
        title = { Text("Save preset", color = Sx.Text) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                singleLine = true,
                placeholder = { Text("Name", color = Sx.Sub) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Sx.Text,
                    unfocusedTextColor = Sx.Text,
                    focusedBorderColor = Sx.Accent,
                    unfocusedBorderColor = Sx.Track,
                    cursorColor = Sx.Accent,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Save", color = if (name.isNotBlank()) Sx.Accent else Sx.Sub)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Sx.Sub) } },
    )
}

/** Response curve with a grid line and a dot per band, log axis 20 Hz – 20 kHz, ±15 dB. */
@Composable
private fun CurveView(
    bandHz: List<Double>,
    bandGain: List<Float>,
    bass: Double, bassHz: Double, treble: Double, trebleHz: Double,
    bands: List<com.sonora.fx.settings.EQBand>,
    active: Boolean,
) {
    val n = 160
    val freqs = remember { DoubleArray(n) { i -> 20.0 * 1000.0.pow(i / (n - 1.0)) } }
    val curve = EqCurve.response(bands, bass, bassHz, treble, trebleHz, freqs)
    val atBands = EqCurve.response(bands, bass, bassHz, treble, trebleHz, bandHz.toDoubleArray())
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val lnMin = ln(20.0); val lnMax = ln(20000.0)
        fun x(f: Double) = ((ln(f) - lnMin) / (lnMax - lnMin)).toFloat() * size.width
        val mid = size.height / 2
        val range = 15f
        fun y(db: Float) = mid - (db / range).coerceIn(-1f, 1f) * (mid - 12.dp.toPx())
        val line = if (active) Sx.Accent else Sx.Thumb

        bandHz.forEach { f ->
            drawLine(Sx.Divider, Offset(x(f), 12.dp.toPx()), Offset(x(f), size.height - 12.dp.toPx()), strokeWidth = 1.dp.toPx())
        }
        drawLine(Sx.Track, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())

        val path = Path()
        val fill = Path().apply { moveTo(0f, mid) }
        freqs.forEachIndexed { i, f ->
            val px = x(f); val py = y(curve[i])
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            fill.lineTo(px, py)
        }
        fill.lineTo(size.width, mid); fill.close()
        drawPath(fill, line.copy(alpha = 0.15f))
        drawPath(path, line, style = Stroke(width = 2.5.dp.toPx()))

        bandHz.forEachIndexed { i, f ->
            // Dot sits at the band's own gain; the line shows what you actually hear.
            val g = bandGain.getOrElse(i) { 0f }
            drawCircle(line, 5.dp.toPx(), Offset(x(f), y(if (g == 0f) atBands[i] else g)))
        }
    }
}

private fun bandLabel(hz: Float): String =
    if (hz >= 1000f) {
        val k = hz / 1000f
        if (k % 1f == 0f) "${k.toInt()}k" else "${k}k"
    } else hz.toInt().toString()
