package com.sonora.fx.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.fx.BuildConfig
import com.sonora.fx.diag.Log

/** Sonora FX's own log, viewable and shareable on the phone — no PC needed. */
@Composable
fun LogScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val version by Log.version.collectAsState()
    @Suppress("UNUSED_VARIABLE") val v = version
    val lines = Log.snapshot()

    fun fullText(): String =
        "Sonora FX ${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · " +
            "Android ${Build.VERSION.RELEASE}\n" + lines.joinToString("\n")

    Screen("Activity log", onBack) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                PillButton("Share") {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Sonora FX log")
                        .putExtra(Intent.EXTRA_TEXT, fullText())
                    startSafely(ctx, Intent.createChooser(send, "Share log"))
                }
            }
            Box(Modifier.weight(1f)) {
                PillButton("Copy") {
                    ctx.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Sonora FX log", fullText()))
                    Toast.makeText(ctx, "Log copied", Toast.LENGTH_SHORT).show()
                }
            }
        }
        SxCard(padding = 16.dp) {
            if (lines.isEmpty()) {
                Footnote("Nothing yet. Switch Sonora FX on and play some music.")
            } else {
                lines.asReversed().forEach {
                    Text(
                        it, color = Sx.Sub, fontSize = 12.sp, lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
        Text(
            "Clear", color = Sx.Accent, fontSize = 16.sp,
            modifier = Modifier
                .clickable { Log.clear() }
                .padding(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 24.dp),
        )
    }
}
