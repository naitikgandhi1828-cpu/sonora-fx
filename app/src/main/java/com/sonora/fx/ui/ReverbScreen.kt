package com.sonora.fx.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.fx.FxApplication
import com.sonora.fx.engine.FxEngine
import com.sonora.fx.engine.ReverbModel
import com.sonora.fx.engine.Room
import com.sonora.fx.engine.Rooms
import java.util.Locale
import kotlin.math.exp

@Composable
fun ReverbScreen(onBack: () -> Unit) {
    val s = FxApplication.settings
    val version by s.version.collectAsState()

    @Suppress("UNUSED_VARIABLE") val v = version
    val on = s.reverbOn
    val spec = FxEngine.reverbSpec(s)
    val classic = spec.classic
    val room = spec.room
    val bassDist = s.reverbBassDistance
    val bassBackDb = 4.0 * (bassDist / 100.0) * room.bassScale

    Screen("Reverb", onBack) {
        SxCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Layers, null, tint = Sx.Text, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Reverb", color = Sx.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        (if (classic) "Classic" else "Studio") + " · " + room.name,
                        color = Sx.Sub, fontSize = 17.sp,
                    )
                }
                SxSwitch(on) { s.reverbOn = it }
            }
        }

        SxCard {
            Text(
                if (classic) "${room.name} · Android preset"
                else String.format(Locale.US, "Rings %.1f s · wet %d dB", spec.decayS, spec.mixDb.toInt()).replace("-", "−"),
                color = Sx.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            DecayView(spec.decayS, spec.preDelayMs, spec.trebleDecay, spec.earlyPct, on)
            Spacer(Modifier.height(12.dp))
            Text(
                String.format(
                    Locale.US, "Pre-delay %d ms · direct bass back ~%.0f dB%s",
                    spec.preDelayMs.toInt(), bassBackDb, if (bassBackDb > 0) ", rounded" else "",
                ),
                color = Sx.Sub, fontFamily = FontFamily.Monospace, fontSize = 15.sp,
            )
        }

        SxCard {
            Text("Engine", color = Sx.Text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Segmented(listOf("Studio", "Classic"), if (classic) 1 else 0) {
                s.reverbEngine = if (it == 1) "classic" else "studio"
            }
            Footnote(
                if (classic) "Android's built-in preset rooms. Light on battery; only the room choice applies."
                else "Android's environmental reverb with every control below. Best quality.",
                Modifier.padding(top = 10.dp),
            )
        }

        SxCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Room", color = Sx.Text, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(room.name, color = Sx.Sub, fontSize = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
            Rooms.all.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { r ->
                        RoomTile(r, r.id == room.id, Modifier.weight(1f)) {
                            s.loadRoom(r)
                            if (!s.reverbOn) s.reverbOn = true
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        SxCard {
            CardLabel("Space")
            ValueSlider("Bass Distance", pct(bassDist), (bassDist / 100).toFloat(),
                { s.reverbBassDistance = linVal(it, 0.0, 100.0, 1.0) }, { s.reverbBassDistance = 60.0 })
            Footnote("How far away the bass feels in big rooms.")
            Footnote(
                "The direct bass under 150 Hz steps back through Android's multi-band compressor: a few dB " +
                    "quieter and its hits rounded off over ~50 ms, scaled by how big the room is (a cathedral " +
                    "gets all of it, halls part, small rooms none).",
                Modifier.padding(top = 6.dp, bottom = 8.dp),
            )
            if (!classic) {
                ValueSlider("Clarity", pct(spec.clarityPct), (spec.clarityPct / 100).toFloat(),
                    { s.reverbClarity = linVal(it, 0.0, 100.0, 1.0) }, { s.reverbClarity = 60.0 })
                Footnote(
                    "Keeps instruments and voices clear while the room stays big. Sets the gap before the " +
                        "room answers (15–40 ms by room size). Android's reverb always leaves the music at full " +
                        "level, so that part of Clarity is built in.",
                    Modifier.padding(bottom = 8.dp),
                )
                ValueSlider("Mix (reverb amount)", "${spec.mixDb.toInt()} dB".replace("-", "−"), linFrac(spec.mixDb, -30.0, 0.0),
                    { s.reverbMixDB = linVal(it, -30.0, 0.0, 1.0) }, { s.reverbMixDB = -10.0 })
                Footnote("How loud the room is next to the music.", Modifier.padding(bottom = 8.dp))
                ValueSlider("Decay time", String.format(Locale.US, "%.2f s", spec.decayS), logFrac(spec.decayS, 0.2, 20.0),
                    { s.reverbDecayS = logVal(it, 0.2, 20.0, 0.01) }, { s.reverbDecayS = room.decayS })
                Footnote(
                    "How long the room rings, as you hear it. (Android's own \"decay time\" setting is about a " +
                        "third of this: its reverb rings 2–3× longer than it says.)",
                    Modifier.padding(bottom = 8.dp),
                )
                ValueSlider("Room size (density)", pct(spec.sizePct), (spec.sizePct / 100).toFloat(),
                    { s.reverbSize = linVal(it, 0.0, 100.0, 1.0) }, { s.reverbSize = room.sizePct })
                ValueSlider("Pre-delay", "${spec.preDelayMs.toInt()} ms", linFrac(spec.preDelayMs, 0.0, 150.0),
                    { s.reverbPreDelayMs = linVal(it, 0.0, 150.0, 1.0) }, { s.reverbPreDelayMs = room.preDelayMs })
            }
        }

        if (!classic) {
            SxCard {
                CardLabel("Tone")
                ValueSlider("Treble decay", String.format(Locale.US, "%.2f×", spec.trebleDecay), linFrac(spec.trebleDecay, 0.1, 2.0),
                    { s.reverbTrebleDecay = linVal(it, 0.1, 2.0, 0.05) }, { s.reverbTrebleDecay = room.trebleDecay })
                ValueSlider("High cut", fmtHz(spec.highCutHz), logFrac(spec.highCutHz, 1000.0, 20000.0),
                    { s.reverbHighCutHz = logVal(it, 1000.0, 20000.0, 100.0) }, { s.reverbHighCutHz = room.highCutHz })
            }
            SxCard {
                CardLabel("Character")
                ValueSlider("Diffusion", pct(spec.diffusionPct), (spec.diffusionPct / 100).toFloat(),
                    { s.reverbDiffusion = linVal(it, 0.0, 100.0, 1.0) }, { s.reverbDiffusion = room.diffusionPct })
                ValueSlider("Early reflections", pct(spec.earlyPct), (spec.earlyPct / 100).toFloat(),
                    { s.reverbEarly = linVal(it, 0.0, 100.0, 1.0) }, { s.reverbEarly = room.earlyPct })
                Footnote(
                    "Bass decay, low cut, modulation and width aren't offered: Android's reverb has no " +
                        "setting for them.",
                    Modifier.padding(top = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Sx.Tile).padding(4.dp),
    ) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (i == selected) Color(0xFF46474C) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(o, color = Sx.Text, fontSize = 18.sp, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun RoomTile(r: Room, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Sx.Accent else Sx.Tile)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val fg = if (selected) Color.White else Sx.Text
        Icon(roomIcon(r.id), null, tint = fg, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(r.name, color = fg, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Text(
            String.format(Locale.US, "%.1f s", r.decayS),
            color = if (selected) Color.White.copy(alpha = 0.85f) else Sx.Sub,
            fontFamily = FontFamily.Monospace, fontSize = 14.sp,
        )
    }
}

private fun roomIcon(id: String): ImageVector = when (id) {
    "booth" -> Icons.Filled.Mic
    "drum" -> Icons.Filled.MusicNote
    "small" -> Icons.Filled.CropSquare
    "ambience" -> Icons.Filled.AutoAwesome
    "chamber" -> Icons.Filled.AccountBalance
    "vplate" -> Icons.Filled.GraphicEq
    "bplate" -> Icons.Filled.WbSunny
    "concert" -> Icons.Filled.Theaters
    "warm" -> Icons.Filled.LocalFireDepartment
    "arena" -> Icons.Filled.Groups
    "cathedral" -> Icons.Filled.Museum
    "dark" -> Icons.Filled.NightsStay
    else -> Icons.Filled.AllInclusive
}

/** Sketch of the room's answer: early reflections, then bass and treble tails. */
@Composable
private fun DecayView(decayS: Double, preDelayMs: Double, trebleDecay: Double, earlyPct: Double, active: Boolean) {
    val accent = if (active) Sx.Accent else Sx.Thumb
    Canvas(
        Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)).background(Sx.Tile)
    ) {
        val pad = 14.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        val span = decayS * 1.2
        fun x(t: Double) = pad + (t / span).toFloat() * w
        val base = pad + h
        // direct sound
        drawLine(Sx.Sub, Offset(x(0.0), base), Offset(x(0.0), pad), strokeWidth = 2.dp.toPx())
        // early reflections
        val pre = preDelayMs / 1000.0
        for (k in 0 until 7) {
            val t = pre + k * 0.006
            val a = (earlyPct / 100.0) * exp(-k * 0.35)
            drawLine(accent, Offset(x(t), base), Offset(x(t), base - (a * h * 0.8).toFloat()), strokeWidth = 2.dp.toPx())
        }
        // tails: amplitude falls 60 dB over RT60
        fun tail(rt: Double, color: Color, dashed: Boolean) {
            val p = Path()
            val n = 80
            for (i in 0..n) {
                val t = pre + (span - pre) * i / n
                val amp = exp(-6.9 * (t - pre) / rt) * 0.55
                val px = x(t); val py = base - (amp * h).toFloat()
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            drawPath(p, color, style = Stroke(width = (if (dashed) 1.5 else 2.5).dp.toPx()))
        }
        tail(decayS, accent, false)
        tail(decayS * trebleDecay.coerceAtLeast(0.1), accent.copy(alpha = 0.5f), true)
        drawLine(Sx.Track, Offset(pad, base), Offset(pad + w, base), strokeWidth = 1.dp.toPx())
    }
}

private fun pct(v: Double) = "${v.toInt()}%"
