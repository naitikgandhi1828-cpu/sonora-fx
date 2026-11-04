package com.sonora.fx.diag

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Drop-in for android.util.Log that also keeps the recent lines in memory, so
 * the app can show and share its own log — no PC or adb logcat needed.
 */
object Log {

    private const val MAX = 400
    private val lines = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    fun d(tag: String, msg: String) { android.util.Log.d(tag, msg); add("D", tag, msg, null) }
    fun i(tag: String, msg: String) { android.util.Log.i(tag, msg); add("I", tag, msg, null) }
    fun w(tag: String, msg: String, t: Throwable? = null) {
        android.util.Log.w(tag, msg, t); add("W", tag, msg, t)
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun clear() { lines.clear(); _version.value++ }

    @Synchronized
    private fun add(level: String, tag: String, msg: String, t: Throwable?) {
        val extra = t?.let { " — ${it.javaClass.simpleName}: ${it.message}" }.orEmpty()
        lines.addLast("${time.format(Date())} $level ${tag.removePrefix("SonoraFX.")}: $msg$extra")
        while (lines.size > MAX) lines.removeFirst()
        _version.value++
    }
}
