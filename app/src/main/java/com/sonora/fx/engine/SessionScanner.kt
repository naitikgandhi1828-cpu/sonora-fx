package com.sonora.fx.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.sonora.fx.diag.Log
import java.io.File

/**
 * "Catch Every App": finds the audio sessions of everything playing, using
 * the DUMP permission, if the phone has granted it.
 *
 * Reads the audio service's dump, whose "playback activity" section lists
 * one AudioPlaybackConfiguration per player with its uid, state and session
 * id. Two ways to get it: the binder dump straight into a file (fast, no
 * process), falling back to running `dumpsys audio`.
 */
object SessionScanner {

    private const val TAG = "SonoraFX.Scan"

    private val reSession = Regex("""\b(?:sessionId|session):(-?\d+)""")
    private val reUid = Regex("""u/pid:(\d+)/""")
    private val reState = Regex("""\bstate:(\w+)""")

    private var usedProcess = false

    fun dumpGranted(ctx: Context): Boolean =
        ctx.checkSelfPermission("android.permission.DUMP") == PackageManager.PERMISSION_GRANTED

    /** Active session ids of other apps' players. */
    fun scan(ctx: Context): Result<Set<Int>> = runCatching {
        val text = dumpViaBinder(ctx) ?: dumpViaProcess().also {
            if (!usedProcess) { Log.i(TAG, "reading sessions via dumpsys"); usedProcess = true }
        }
        if (text.contains("Permission Denial")) error("DUMP permission denied")
        parse(text, ctx.applicationInfo.uid)
    }

    internal fun parse(text: String, myUid: Int): Set<Int> {
        val out = HashSet<Int>()
        for (line in text.lineSequence()) {
            if (!line.contains("u/pid:")) continue
            val session = reSession.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (session <= 0) continue
            val uid = reUid.find(line)?.groupValues?.get(1)?.toIntOrNull()
            if (uid == myUid) continue
            val state = reState.find(line)?.groupValues?.get(1)?.lowercase()
            if (state == "released") continue
            // Leave calls alone.
            if (line.contains("VOICE_COMMUNICATION")) continue
            out += session
        }
        return out
    }

    private fun dumpViaBinder(ctx: Context): String? = try {
        val sm = Class.forName("android.os.ServiceManager")
        val binder = sm.getMethod("getService", String::class.java)
            .invoke(null, Context.AUDIO_SERVICE) as? IBinder
        if (binder == null) {
            null
        } else {
            val f = File(ctx.cacheDir, "audio.dump")
            ParcelFileDescriptor.open(
                f,
                ParcelFileDescriptor.MODE_WRITE_ONLY or
                    ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE,
            ).use { pfd -> binder.dump(pfd.fileDescriptor, arrayOf()) }
            f.readText().also { f.delete() }
        }
    } catch (t: Throwable) {
        Log.d(TAG, "binder dump unavailable: $t")
        null
    }

    private fun dumpViaProcess(): String {
        val p = ProcessBuilder("dumpsys", "audio").redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        return text
    }
}
