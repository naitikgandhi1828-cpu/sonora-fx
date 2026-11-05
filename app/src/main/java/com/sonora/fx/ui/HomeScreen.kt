package com.sonora.fx.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.fx.FxApplication
import com.sonora.fx.engine.FxEngine
import com.sonora.fx.engine.FxService
import com.sonora.fx.engine.SessionScanner

@Composable
fun HomeScreen(go: (String) -> Unit) {
    val ctx = LocalContext.current
    val s = FxApplication.settings
    val version by s.version.collectAsState()
    val status by FxEngine.status.collectAsState()

    // Read everything up front so this screen redraws on any settings change.
    @Suppress("UNUSED_VARIABLE") val v = version
    val on = s.enabled
    val eqOn = s.eqOn
    val preset = s.eqPresetName
    val catchAll = s.catchAll
    val rvOn = s.reverbOn
    val roomName = com.sonora.fx.engine.Rooms.byId(s.reverbRoom).name
    val spOn = s.spatialOn
    val spMode = s.spatialMode
    val dump = SessionScanner.dumpGranted(ctx)
    val sessions = status.sessions

    val subtitle = when {
        !on -> "Off. Other apps sound as usual."
        status.global -> "On. Every app sounds through Sonora FX."
        sessions == 0 -> "On. Play music in any app."
        sessions == 1 -> "On. 1 app sounds through Sonora FX."
        else -> "On. $sessions apps sound through Sonora FX."
    }
    val appsText = when {
        !on -> "Switch on to start. Then play music in Spotify, Anghami, YouTube Music…"
        status.note != null -> status.note!!
        status.global -> "Working on the whole phone: every app" +
            (if (sessions > 0) ", reverb on $sessions announcing " + (if (sessions == 1) "app." else "apps.") else ".")
        sessions == 0 -> "Waiting for music. Start playing in Spotify, Anghami, YouTube Music…"
        else -> "Working on $sessions playing " + (if (sessions == 1) "app" else "apps") +
            (if (catchAll && dump) ", catching every app." else ".")
    }
    val xiaomi = Build.MANUFACTURER.lowercase().let { it.contains("xiaomi") || it.contains("redmi") || it.contains("poco") }

    Screen(title = null, onBack = null) {
        Text(
            "Sonora FX",
            color = Sx.Text, fontSize = 44.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, top = 64.dp, bottom = 20.dp),
        )

        PowerCard(on, subtitle) {
            s.enabled = !on
            FxService.sync(ctx)
        }

        SectionHeader("Apps")
        ListCard { InfoRow(Icons.Outlined.Info, appsText) }

        SectionHeader("Effects")
        ListCard {
            NavRow(Icons.Filled.Layers, "Reverb", value = if (rvOn) roomName else "Off") { go("reverb") }
            RowDivider()
            NavRow(Icons.Filled.Tune, "Equaliser", value = if (eqOn) preset else "Off") { go("eq") }
            RowDivider()
            NavRow(Icons.Filled.Headphones, "Spatial", value = if (spOn) spatialModeName(spMode) else "Off") { go("spatial") }
        }
        Footnote(
            "Changes reach the playing app right away, so you can tune by ear.",
            Modifier.padding(horizontal = 24.dp),
        )

        SectionHeader("More apps")
        ListCard {
            NavRow(
                Icons.Filled.Search, "Catch every app",
                subtitle = "For YouTube Music and apps that don't announce their sound",
                value = if (catchAll) "On" else "Off",
            ) { go("catch") }
        }

        SectionHeader("How it works")
        ListCard {
            NavRow(
                Icons.Outlined.Info,
                if (xiaomi) "Keep it running on Xiaomi" else "Keep it running",
                subtitle = "Autostart and battery saver",
            ) { go("keep") }
            RowDivider()
            NavRow(Icons.Outlined.Description, "Activity log", subtitle = "See and share what Sonora FX is doing") { go("log") }
        }
        Footnote(
            "Android doesn't let one app process another app's sound directly. Sonora FX " +
                "attaches Android's own audio effects to each playing app, so it needs no root and no PC.",
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun PowerCard(on: Boolean, subtitle: String, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Sx.Card)
            .clickable(onClick = onToggle)
            .padding(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(if (on) Sx.Accent.copy(alpha = 0.15f) else Sx.Tile)
                .border(2.dp, if (on) Sx.Accent else Sx.Ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PowerSettingsNew, if (on) "Turn off" else "Turn on",
                tint = if (on) Sx.Accent else Sx.Sub, modifier = Modifier.size(42.dp),
            )
        }
        Spacer(Modifier.width(22.dp))
        Column {
            Text("Sound for other apps", color = Sx.Text, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Sx.Sub, fontSize = 17.sp, lineHeight = 22.sp)
        }
    }
}
