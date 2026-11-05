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
import androidx.compose.foundation.layout.height
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
import com.sonora.fx.engine.FxEngine
import com.sonora.fx.engine.SessionScanner
import com.sonora.fx.shizuku.ShizukuGrant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

/**
 * "Catch every app". Works with no setup: Sonora FX puts its effects on the
 * phone's whole output. A precise per-app mode via Shizuku stays available as
 * an option, for phones that allow it (many Xiaomi phones block the grant).
 */
@Composable
fun CatchAllScreen(refreshKey: Int, onRefresh: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = FxApplication.settings
    val version by s.version.collectAsState()
    val status by FxEngine.status.collectAsState()

    var granted by remember { mutableStateOf(SessionScanner.dumpGranted(ctx)) }
    var result by remember { mutableStateOf("") }
    var showPrecise by remember { mutableStateOf(false) }
    LaunchedEffect(refreshKey) { granted = SessionScanner.dumpGranted(ctx) }

    @Suppress("UNUSED_VARIABLE") val v = version
    val catchAll = s.catchAll
    @Suppress("UNUSED_VARIABLE") val k = refreshKey
    val running = ShizukuGrant.isRunning()
    val allowed = running && ShizukuGrant.hasPermission()
    val installed = isInstalled(ctx, SHIZUKU_PKG)
    val wireless = Build.VERSION.SDK_INT >= 30
    val xiaomi = Build.MANUFACTURER.lowercase().let { it.contains("xiaomi") || it.contains("redmi") || it.contains("poco") }

    Screen("Catch every app", onBack) {
        SxCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, null, tint = Sx.Accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Catch every app", color = Sx.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            !catchAll -> "Off"
                            granted -> "On · precise mode"
                            else -> "On · whole-phone mode"
                        },
                        color = Sx.Sub, fontSize = 17.sp,
                    )
                }
                SxSwitch(catchAll) { s.catchAll = it }
            }
            Footnote(
                "Reaches YouTube Music, browsers, games and every app that doesn't announce its sound. " +
                    "No PC, no permission, nothing to set up.",
                Modifier.padding(top = 12.dp),
            )
        }

        status.note?.let { n ->
            SxCard { Footnote(n) }
        }

        SxCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = Sx.Accent, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    if (granted) "Precise mode" else "Whole-phone mode",
                    color = Sx.Text, fontSize = 19.sp, fontWeight = FontWeight.Medium,
                )
            }
            Footnote(
                if (granted) {
                    "Sonora FX finds each playing app and gives it the full effect chain, reverb included."
                } else {
                    "Sonora FX puts the equaliser, tone, pre-amp, limiter, bass distance and spatial on the " +
                        "phone's whole output, so every app goes through them. Android only runs reverb on " +
                        "apps that announce their sound (Spotify, Anghami and most players), so there it " +
                        "applies to those."
                },
                Modifier.padding(top = 8.dp),
            )
        }

        if (!granted) {
            SxCard {
                Row(
                    Modifier.fillMaxWidth().clickable { showPrecise = !showPrecise },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Optional: precise mode", color = Sx.Text, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                        Text("Reverb on every app too. Needs Shizuku.", color = Sx.Sub, fontSize = 15.sp)
                    }
                    Text(if (showPrecise) "Hide" else "Show", color = Sx.Accent, fontSize = 16.sp)
                }
                if (showPrecise) {
                    Spacer(Modifier.height(12.dp))
                    if (xiaomi) {
                        Footnote(
                            "On Xiaomi, Redmi and POCO the phone blocks this grant (\"limited by the manufacturer\") " +
                                "unless Developer options › \"USB debugging (Security settings)\" is on. That " +
                                "switch needs a SIM card and a signed-in Xiaomi account. Whole-phone mode works " +
                                "without any of this.",
                            Modifier.padding(bottom = 12.dp),
                        )
                    }
                    when {
                        allowed -> PillButton("Grant with Shizuku") {
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { ShizukuGrant.grantDump(ctx.packageName) }
                                result = r.fold({ "Done — checking…" }, { "Shizuku couldn't grant it: ${it.message}" })
                                onRefresh()
                            }
                        }
                        running -> PillButton("Allow Sonora FX in Shizuku") { ShizukuGrant.requestPermission() }
                        installed -> PillButton("Open Shizuku") { openApp(ctx, SHIZUKU_PKG) }
                        else -> PillButton("Get Shizuku") { getShizuku(ctx) }
                    }
                    if (result.isNotBlank()) Footnote(result, Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(8.dp))
                    if (!wireless) {
                        Footnote("Starting Shizuku without a PC needs Android 11 or newer, or root.")
                    } else {
                        Step(1, "Get Shizuku (free) and open it.", if (installed) null else "Get Shizuku" to { getShizuku(ctx) }, done = installed)
                        Step(
                            2,
                            "Turn on Developer options: in Settings › About phone, tap the version line " +
                                "(\"OS version\" or \"Build number\") 7 times." +
                                (if (xiaomi) " Then turn on \"USB debugging (Security settings)\"." else ""),
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
                        Step(5, "Come back and tap the button above.", null, done = granted)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
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
