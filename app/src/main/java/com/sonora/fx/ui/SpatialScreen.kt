package com.sonora.fx.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.fx.FxApplication
import kotlin.math.abs
import kotlin.math.roundToInt

private val modes = listOf(
    Triple("subtle", "Subtle", "A light widening that stays natural."),
    Triple("headphones", "Headphones", "Moves the sound out of your head. Best on headphones."),
    Triple("wide", "Wide", "Widens the phone's own speakers."),
    Triple("hall", "Concert Hall", "Headphone spatial with the room's reflections around it."),
)

fun spatialModeName(id: String) = modes.firstOrNull { it.first == id }?.second ?: "Headphones"

@Composable
fun SpatialScreen(onBack: () -> Unit) {
    val s = FxApplication.settings
    val version by s.version.collectAsState()

    @Suppress("UNUSED_VARIABLE") val v = version
    val on = s.spatialOn
    val mode = s.spatialMode
    val amount = s.spatialAmount
    val depth = s.spatialDepth
    val balance = s.balance

    Screen("Spatial", onBack) {
        SxCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Headphones, null, tint = Sx.Accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Spatial", color = Sx.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(if (on) spatialModeName(mode) else "Off", color = Sx.Sub, fontSize = 17.sp)
                }
                SxSwitch(on) { s.spatialOn = it }
            }
            Footnote(
                "Models listening to speakers in a room: each ear also hears the far speaker, and early " +
                    "reflections arrive around it. Built for headphones; on the phone's own speaker there is " +
                    "little room to place.",
                Modifier.padding(top = 12.dp),
            )
        }

        SxCard {
            Text("Mode", color = Sx.Text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            modes.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (id, name, _) ->
                        val sel = id == mode
                        Text(
                            name,
                            color = if (sel) Color.White else Sx.Text,
                            fontSize = 17.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (sel) Sx.Accent else Sx.Tile)
                                .clickable {
                                    s.spatialMode = id
                                    if (id == "hall" && s.spatialDepth < 40.0) s.spatialDepth = 50.0
                                    if (!s.spatialOn) s.spatialOn = true
                                }
                                .padding(vertical = 15.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
            Footnote(modes.firstOrNull { it.first == mode }?.third ?: "", Modifier.padding(top = 8.dp))
        }

        SxCard {
            ValueSlider("Amount", "${amount.toInt()}%", (amount / 100).toFloat(),
                { s.spatialAmount = linVal(it, 0.0, 100.0, 1.0) }, { s.spatialAmount = 55.0 })
            Footnote("How strongly the sound is placed around you.", Modifier.padding(bottom = 8.dp))
            ValueSlider("Room Depth", "${depth.toInt()}%", (depth / 100).toFloat(),
                { s.spatialDepth = linVal(it, 0.0, 100.0, 1.0) }, { s.spatialDepth = 30.0 })
            Footnote(
                "Early reflections around the sound. With Reverb on, they add to the room's own.",
                Modifier.padding(bottom = 4.dp),
            )
            Footnote(
                "Here Spatial uses Android's virtualizer. Some phones only apply it on headphones, " +
                    "and its width and crossfeed are fixed by the phone.",
                Modifier.padding(top = 8.dp),
            )
        }

        SxCard {
            CardTitle(Icons.Filled.SurroundSound, "Stereo Field")
            ValueSlider("Balance", balanceText(balance), ((balance + 1) / 2).toFloat(),
                { s.balance = linVal(it, -1.0, 1.0, 0.02).let { b -> if (abs(b) < 0.03) 0.0 else b } }, { s.balance = 0.0 })
            Footnote(
                "Stereo width, mono downmix and pitch need direct access to the samples, which Android " +
                    "doesn't give one app over another app's sound.",
                Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun balanceText(b: Double): String = when {
    abs(b) < 0.01 -> "Center"
    b < 0 -> "L ${(abs(b) * 100).roundToInt()}%"
    else -> "R ${(b * 100).roundToInt()}%"
}
