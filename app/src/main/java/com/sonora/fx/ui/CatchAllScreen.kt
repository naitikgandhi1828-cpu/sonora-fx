package com.sonora.fx.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sonora.fx.shizuku.ShizukuGrant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Catch Every App" setup. Prefers the one-tap Shizuku grant; falls back to a
 * short, plain manual path when Shizuku isn't present.
 */
@Composable
fun CatchAllScreen(refreshKey: Int, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pkg = context.packageName
    val command = "adb shell pm grant $pkg android.permission.DUMP"

    var granted by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    // Re-read permission whenever the activity bumps refreshKey.
    LaunchedEffect(refreshKey) {
        granted = context.checkSelfPermission("android.permission.DUMP") ==
            PackageManager.PERMISSION_GRANTED
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Catch Every App", style = MaterialTheme.typography.headlineSmall)

            Text(
                if (granted) "Permission given — Sonora FX can affect every app's sound."
                else "Permission not given yet. Grant it once; it stays until you uninstall Sonora FX.",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (granted) {
                Text(
                    "You're all set. Nothing else to do here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }

            when {
                ShizukuGrant.isRunning() && ShizukuGrant.hasPermission() -> {
                    Text(
                        "Shizuku is ready. One tap grants the permission — no PC needed.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { ShizukuGrant.grantDump(pkg) }
                            status = result.fold(
                                onSuccess = { "Done — checking…" },
                                onFailure = { "Shizuku couldn't grant it: ${it.message}" },
                            )
                            onRefresh()
                        }
                    }) { Text("Grant automatically with Shizuku") }
                }

                ShizukuGrant.isRunning() && !ShizukuGrant.hasPermission() -> {
                    Text(
                        "Shizuku is running. Allow Sonora FX in Shizuku, then grant.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = { ShizukuGrant.requestPermission() }) {
                        Text("Allow Sonora FX in Shizuku")
                    }
                }

                else -> {
                    Text(
                        "Easiest: install the free Shizuku app and start it once, then come " +
                            "back here for a one-tap grant. Or use the manual steps below.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.bodySmall)
            }

            // Manual fallback: the same short four steps as the current build.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Manual way (no Shizuku)", style = MaterialTheme.typography.titleMedium)
                    manualSteps.forEachIndexed { i, step ->
                        Text("${i + 1}. $step", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        command,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

private val manualSteps = listOf(
    "On the phone: open Settings › About phone and tap the version line (\"OS version\", " +
        "or \"MIUI version\" on older phones) 7 times, until it says you're a developer.",
    "Still on the phone: in Settings › Additional settings › Developer options, turn on " +
        "\"USB debugging\". On Xiaomi, also turn on \"USB debugging (Security settings)\" if it's there.",
    "On a PC: get \"Platform-Tools\" from developer.android.com/tools/releases/platform-tools, " +
        "unzip it, and open that folder. Plug the phone in by USB, click the folder's address bar, " +
        "type cmd and press Enter, then run the one command below. Tap \"Allow\" on the phone if it asks.",
    "That's it — this screen turns green on its own. You can switch USB debugging back off; " +
        "the permission stays until Sonora FX is uninstalled.",
)
