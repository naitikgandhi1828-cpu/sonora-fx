package com.sonora.fx.shizuku

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * One-tap grant of android.permission.DUMP through Shizuku, so "Catch Every
 * App" needs no PC and no adb command.
 *
 * Flow the UI drives:
 *   1. isRunning()        -> is the Shizuku service up?
 *   2. hasPermission()    -> has the user allowed Sonora FX in Shizuku?
 *   3. requestPermission()-> ask (shows Shizuku's own dialog)
 *   4. grantDump()        -> run `pm grant <pkg> android.permission.DUMP`
 */
object ShizukuGrant {

    private const val TAG = "SonoraFX.Shizuku"
    const val REQUEST_CODE = 4001

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        if (Shizuku.isPreV11()) false
        else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun shouldShowRationale(): Boolean = try {
        !Shizuku.isPreV11() && Shizuku.shouldShowRequestPermissionRationale()
    } catch (t: Throwable) {
        false
    }

    fun requestPermission() {
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (t: Throwable) {
            Log.w(TAG, "requestPermission failed", t)
        }
    }

    /**
     * Runs `pm grant <pkg> android.permission.DUMP` as the shell UID via
     * Shizuku. Returns a short message; success is an empty [Result].
     */
    fun grantDump(pkg: String): Result<Unit> = try {
        // Shizuku.newProcess is private in API 13.x (public in older versions),
        // so look it up with getDeclaredMethod and make it accessible. The
        // returned ShizukuRemoteProcess extends java.lang.Process.
        val cmd = arrayOf("sh", "-c", "pm grant $pkg android.permission.DUMP")
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        ).apply { isAccessible = true }
        val process = method.invoke(null, cmd, null, null) as Process
        val err = process.errorStream.bufferedReader().use { it.readText() }
        val out = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        if (code == 0) {
            Result.success(Unit)
        } else {
            Result.failure(RuntimeException("pm grant exited $code: ${err.ifBlank { out }.trim()}"))
        }
    } catch (t: Throwable) {
        Log.w(TAG, "grantDump failed", t)
        Result.failure(t)
    }
}
