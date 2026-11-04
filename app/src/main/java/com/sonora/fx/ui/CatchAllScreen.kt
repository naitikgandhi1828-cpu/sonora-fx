package com.sonora.fx.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.fx.FxApplication
import com.sonora.fx.engine.SessionScanner
import com.sonora.fx.shizuku.ShizukuGrant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

/**
 * "Catch every app" — everything happens on the phone. The DUMP permission is
 * granted with one tap through Shizuku, and Shizuku itself is started with
 * Android's Wireless debugging (Android 11+), so no PC is ever needed.
 */
@Composable
fun CatchAllScreen(refreshKey: Int, onRefresh: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = FxApplication.settings
    val version by s.version.collectAsState()

    var granted by remember { mutableStateOf(SessionScanner.dumpGranted(ctx)) }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(refreshKey) { granted = SessionScanner.dumpGranted(ctx) }

    @Suppress("UNUSED_VARIABLE") val v = version
    val catchAll = s.catchAll
    @Suppress("UNUSED_VARIABLE") val k = refreshKey
    val running = ShizukuGrant.isRunning()
    val allowed = running && ShizukuGrant.hasPermission()
    val installed = isInstalled(ctx, SHIZUKU_PKG)
    val wireless = Build.VERSION.SDK_INT >= 30

    Screen("Catch every app", onBack) {
        SxCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, null, tint = Sx.Accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Catch every app", color = Sx.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (granted) (if (catchAll) "On" else "Off") else "Needs a one-time setup",
                        color = Sx.Sub, fontSize = 17.sp,
                    )
                }
                SxSwitch(catchAll && granted, enabled = granted) { s.catchAll = it }
            }
            Footnote(
                "Spotify, Anghami and most players announce their sound, so they work without this. " +
                    "Catch every app also reaches YouTube Music, browsers, games and the rest.",
                Modifier.padding(top = 12.dp),
            )
        }

        if (granted) {
            SxCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Sx.Accent, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Permission given", color = Sx.Text, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                }
                Footnote(
                    "It stays until you uninstall Sonora FX, even after Shizuku stops or the phone restarts. " +
                        "Nothing else to set up.",
                    Modifier.padding(top = 8.dp),
                )
            }
            return@Screen
        }

        // The one thing to do next, as a big button.
        SxCard {
            CardLabel("Next step")
            when {
                allowed -> {
                    Footnote("Shizuku is ready. One tap gives Sonora FX the permission.")
                    Spacer(Modifier.size(12.dp))
                    PillButton("Grant with Shizuku") {
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { ShizukuGrant.grantDump(ctx.packageName) }
                            status = r.fold({ "Done — checking…" }, { "Shizuku couldn't grant it: ${it.message}" })
                            onRefresh()
                        }
                    }
                }
                running -> {
                    Footnote("Shizuku is running. Allow Sonora FX in Shizuku's prompt.")
                    Spacer(Modifier.size(12.dp))
                    PillButton("Allow Sonora FX in Shizuku") { ShizukuGrant.requestPermission() }
                }
                installed -> {
                    Footnote("Shizuku is installed but not started. Start it with the steps below, then come back.")
                    Spacer(Modifier.size(12.dp))
                    PillButton("Open Shizuku") { openApp(ctx, SHIZUKU_PKG) }
                }
                else -> {
                    Footnote("Get Shizuku, a free helper app. It gives Sonora FX the permission, all on this phone.")
                    Spacer(Modifier.size(12.dp))
                    PillButton("Get Shizuku") { getShizuku(ctx) }
                }
            }
            if (status.isNotBlank()) Footnote(status, Modifier.padding(top = 10.dp))
        }

        SxCard {
            CardLabel("Set up on this phone — no PC")
            if (!wireless) {
                Footnote(
                    "This phone runs Android ${Build.VERSION.RELEASE}. Starting Shizuku without a PC needs " +
                        "Android 11 or newer (Wireless debugging), or a rooted phone. Players that announce " +
                        "their sound still work without this.",
                )
            } else {
                Step(1, "Get Shizuku (free) and open it.", if (installed) null else "Get Shizuku" to { getShizuku(ctx) }, done = installed)
                Step(
                    2,
                    "Turn on Developer options: in Settings › About phone, tap the version line " +
                        "(\"OS version\" or \"Build number\") 7 times.",
                    "Open About phone" to { startSafely(ctx, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) },
                )
                Step(
                    3,
                    "In Shizuku, tap \"Pairing\" under Start via Wireless debugging. Then in Developer " +
                        "options › Wireless debugging, turn it on, tap \"Pair device with pairing code\", " +
                        "and type the code into Shizuku's notification.",
                    "Open Developer options" to { startSafely(ctx, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) },
                )
                Step(4, "Back in Shizuku, tap \"Start\".", if (installed) "Open Shizuku" to { openApp(ctx, SHIZUKU_PKG) } else null, done = running)
                Step(5, "Come back here and tap the button above. Done — it stays given for good.", null, done = granted)
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String, action: Pair<String, () -> Unit>?, done: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (done) Sx.Accent else Sx.Tile),
            contentAlignment = Alignment.Center,
        ) {
            Text(n.toString(), color = if (done) Color.Black else Sx.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, color = if (done) Sx.Sub else Sx.Text, fontSize = 16.sp, lineHeight = 22.sp)
            if (action != null) {
                Text(
                    action.first, color = Sx.Accent, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Sx.Tile)
                        .clickable(onClick = action.second)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
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

private fun isInstalled(ctx: Context, pkg: String): Boolean = try {
    ctx.packageManager.getPackageInfo(pkg, 0); true
} catch (t: Throwable) {
    false
}

private fun openApp(ctx: Context, pkg: String) {
    ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { startSafely(ctx, it) }
}

private fun getShizuku(ctx: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PKG"))
    if (!startSafely(ctx, market)) {
        startSafely(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases")))
    }
}

fun startSafely(ctx: Context, intent: Intent): Boolean = try {
    ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}
