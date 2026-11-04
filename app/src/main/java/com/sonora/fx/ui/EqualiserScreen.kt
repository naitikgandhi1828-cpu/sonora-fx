package com.sonora.fx.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.fx.FxApplication
import com.sonora.fx.engine.EqCurve
import com.sonora.fx.settings.EQPreset
import kotlin.math.ln

@Composable
fun EqualiserScreen(onBack: () -> Unit) {
    val s = FxApplication.settings
    val version by s.version.collectAsState()

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
    val limiter = s.limiterOn
    val curve = EqCurve.gains(bands, bass, bassHz, treble, trebleHz)

    Screen("Equaliser", onBack) {
        SxCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tune, null, tint = Sx.Accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Equaliser", color = Sx.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(if (eqOn) preset else "Off", color = Sx.Sub, fontSize = 17.sp)
                }
                SxSwitch(eqOn) { s.eqOn = it }
            }
        }

        SxCard(padding = 14.dp) {
            CurveView(curve, eqOn)
            Spacer(Modifier.height(14.dp))
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
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                Text(
                    "Reset to Flat", color = Sx.Accent, fontSize = 16.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            s.eqBands = EQPreset.flatBands()
                            s.eqPresetName = EQPreset.flat.name
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        SxCard {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                Icon(Icons.Outlined.RadioButtonChecked, null, tint = Sx.Text, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text("Tone", color = Sx.Text, fontSize = 23.sp, fontWeight = FontWeight.Medium)
            }
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
            ValueSlider("Pre-amp", fmtDb(preamp), linFrac(preamp, -12.0, 12.0),
                { s.eqPreampDB = linVal(it, -12.0, 12.0, 0.5) }, { s.eqPreampDB = 0.0 })
            SwitchRow("Auto pre-amp (prevent clipping)", "Applies while the equalizer is on", auto) { s.autoPreamp = it }
            SwitchRow("Limiter", "Catches any peaks that are left", limiter) { s.limiterOn = it }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** The combined response (bands + tone), log frequency axis, ±15 dB. */
@Composable
private fun CurveView(gains: FloatArray, active: Boolean) {
    Canvas(Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 8.dp)) {
        val mid = size.height / 2
        val range = 15f
        drawLine(Sx.Divider, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
        val lnMin = ln(20.0); val lnMax = ln(20000.0)
        val path = Path()
        EqCurve.centres.forEachIndexed { i, f ->
            val x = ((ln(f) - lnMin) / (lnMax - lnMin)).toFloat() * size.width
            val y = mid - (gains[i] / range).coerceIn(-1f, 1f) * mid * 0.9f
            if (i == 0) path.moveTo(0f, y)
            path.lineTo(x, y)
        }
        path.lineTo(size.width, mid - (gains.last() / range).coerceIn(-1f, 1f) * mid * 0.9f)
        drawPath(path, if (active) Sx.Accent else Sx.Thumb, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun bandLabel(hz: Float): String =
    if (hz >= 1000f) {
        val k = hz / 1000f
        if (k % 1f == 0f) "${k.toInt()}k" else "${k}k"
    } else hz.toInt().toString()
