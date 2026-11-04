package com.sonora.fx.ui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Xiaomi/HyperOS (and others) stop background apps unless told not to. */
@SuppressLint("BatteryLife")
@Composable
fun KeepRunningScreen(refreshKey: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val k = refreshKey
    val pm = ctx.getSystemService(PowerManager::class.java)
    val unrestricted = pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
    val pkgUri = Uri.parse("package:${ctx.packageName}")

    Screen("Keep it running", onBack) {
        Footnote(
            "Some phones, Xiaomi especially, stop apps in the background to save battery. " +
                "If the sound goes back to normal after a while, do these once.",
            Modifier.padding(horizontal = 8.dp),
        )
        SxCard {
            Text("1. Battery saver: No restrictions", color = Sx.Text, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            Footnote(
                if (unrestricted) "Done — Sonora FX can run in the background."
                else "Lets Sonora FX keep running while the screen is off.",
            )
            Spacer(Modifier.height(12.dp))
            if (!unrestricted) {
                PillButton("Allow running in background") {
                    if (!startSafely(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri))) {
                        startSafely(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
                    }
                }
            }
        }
        SxCard {
            Text("2. Autostart", color = Sx.Text, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            Footnote("On Xiaomi, turn on Autostart for Sonora FX so it comes back after a restart.")
            Spacer(Modifier.height(12.dp))
            PillButton("Open Autostart") {
                val miui = Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity",
                    )
                )
                if (!startSafely(ctx, miui)) startSafely(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
            }
        }
        SxCard {
            Text("3. Lock it in Recents", color = Sx.Text, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            Footnote("Open Recents, long-press Sonora FX and tap the lock, so \"clear all\" leaves it alone.")
        }
    }
}
