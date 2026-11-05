package com.sonora.fx.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.fx.FxApplication
import com.sonora.fx.engine.FxEngine
import com.sonora.fx.engine.SessionScanner

/**
 * "Catch every app". Nothing to install or set up: Sonora FX puts its
 * effects on the phone's whole output, so every app goes through them.
 */
@Composable
fun CatchAllScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s = FxApplication.settings
    val version by s.version.collectAsState()
    val status by FxEngine.status.collectAsState()

    @Suppress("UNUSED_VARIABLE") val v = version
    val catchAll = s.catchAll
    // If the phone ever grants per-app access on its own, use the precise mode.
    val precise = SessionScanner.dumpGranted(ctx)

    Screen("Catch every app", onBack) {
        SxCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, null, tint = Sx.Accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Catch every app", color = Sx.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(if (catchAll) "On" else "Off", color = Sx.Sub, fontSize = 17.sp)
                }
                SxSwitch(catchAll) { s.catchAll = it }
            }
            Footnote(
                "Reaches YouTube Music, browsers, games and every app that doesn't announce its sound. " +
                    "Nothing to install and nothing to set up.",
                Modifier.padding(top = 12.dp),
            )
        }

        status.note?.let { n -> SxCard { Footnote(n) } }

        SxCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = Sx.Accent, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Text("How it works", color = Sx.Text, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            }
            Footnote(
                if (precise) {
                    "Sonora FX finds each playing app and gives it the full effect chain, reverb included."
                } else {
                    "Sonora FX puts the equaliser, tone, pre-amp, limiter, bass distance and spatial on the " +
                        "phone's whole output, so every app goes through them. Android only runs reverb on " +
                        "apps that announce their sound (Spotify, Anghami and most players), so reverb " +
                        "applies to those."
                },
                Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Sx.Accent)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

fun startSafely(ctx: Context, intent: Intent): Boolean = try {
    ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}
