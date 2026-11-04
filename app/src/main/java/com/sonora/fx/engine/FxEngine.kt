package com.sonora.fx.engine

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.sonora.fx.diag.Log
import com.sonora.fx.FxApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The audio engine. Keeps one [SessionFx] on every session it knows about and
 * pushes the current settings to all of them.
 *
 * Sessions come from two places:
 *  - players that announce themselves (ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION),
 *    handled by [FxService]'s receiver;
 *  - "Catch Every App": [SessionScanner] polling the audio dump, when the
 *    DUMP permission is granted and the switch is on.
 *
 * All effect work happens on one background thread.
 */
object FxEngine {

    private const val TAG = "SonoraFX.Engine"
    private const val SCAN_MS = 2500L
    private const val RETRY_MS = 30_000L
    /** Scans a session may be missing before its effect is released. */
    private const val MISS_LIMIT = 3

    data class Status(
        val running: Boolean = false,
        val sessions: Int = 0,
        val catchAll: Boolean = false,
        val note: String? = null,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    private lateinit var app: Context
    private var thread: HandlerThread? = null
    private var h: Handler? = null

    private val effects = HashMap<Int, SessionFx>()
    private val announced = HashSet<Int>()
    private val scanned = HashMap<Int, Int>() // session -> consecutive misses
    private val failedAt = HashMap<Int, Long>()
    private var note: String? = null
    private var lastFound: Set<Int> = emptySet()

    private val onSettings: () -> Unit = { scheduleApply() }
    private val applyNow = Runnable { applyAll() }

    fun start(context: Context) {
        if (thread != null) return
        app = context.applicationContext
        val t = HandlerThread("SonoraFX.Engine").apply { start() }
        thread = t
        h = Handler(t.looper)
        FxApplication.settings.addListener(onSettings)
        h?.post(scan)
        publish()
        Log.i(TAG, "started (Android ${android.os.Build.VERSION.RELEASE}, DUMP " +
            (if (SessionScanner.dumpGranted(app)) "granted" else "not granted") + ")")
    }

    fun stop() {
        val handler = h ?: return
        FxApplication.settings.removeListener(onSettings)
        val t = thread
        thread = null
        h = null
        handler.removeCallbacksAndMessages(null)
        handler.post {
            effects.values.forEach { it.release() }
            effects.clear(); announced.clear(); scanned.clear(); failedAt.clear()
            lastFound = emptySet(); note = null
            _status.value = Status()
            t?.quitSafely()
        }
        Log.i(TAG, "stopped")
    }

    fun openSession(session: Int, pkg: String?) {
        if (session <= 0) return
        h?.post {
            Log.i(TAG, "open session $session from $pkg")
            announced += session
            failedAt.remove(session)
            attach(session)
            publish()
        }
    }

    fun closeSession(session: Int) {
        h?.post {
            announced -= session
            if (session !in scanned) detach(session)
            publish()
        }
    }

    private fun scheduleApply() {
        val handler = h ?: return
        handler.removeCallbacks(applyNow)
        handler.postDelayed(applyNow, 40L) // coalesce slider drags
    }

    private val scan: Runnable = object : Runnable {
        override fun run() {
            try {
                scanOnce()
            } catch (t: Throwable) {
                Log.w(TAG, "scan failed", t)
            }
            h?.postDelayed(this, SCAN_MS)
        }
    }

    private fun scanOnce() {
        val s = FxApplication.settings
        val wanted = s.enabled && s.catchAll
        if (!wanted || !SessionScanner.dumpGranted(app)) {
            note = if (wanted) "Catch Every App needs the permission (see its tab)" else null
            // Drop sessions that only scanning was keeping.
            scanned.keys.toList().forEach { id ->
                scanned.remove(id)
                if (id !in announced) detach(id)
            }
            publish()
            return
        }
        val found = SessionScanner.scan(app).getOrElse {
            val n = "Couldn't read audio sessions: ${it.message}"
            if (n != note) Log.w(TAG, n, it)
            note = n
            publish()
            return
        }
        note = null
        if (found != lastFound) {
            Log.i(TAG, "playing sessions: ${if (found.isEmpty()) "none" else found.sorted().joinToString()}")
            lastFound = found
        }
        for (id in found) {
            scanned[id] = 0
            attach(id)
        }
        for (id in scanned.keys.toList()) {
            if (id in found) continue
            val misses = (scanned[id] ?: 0) + 1
            if (misses >= MISS_LIMIT) {
                scanned.remove(id)
                if (id !in announced) detach(id)
            } else {
                scanned[id] = misses
            }
        }
        publish()
    }

    private fun attach(session: Int) {
        if (session in effects) return
        val failed = failedAt[session]
        if (failed != null && SystemClock.elapsedRealtime() - failed < RETRY_MS) return
        try {
            val fx = SessionFx(session)
            fx.apply(params())
            effects[session] = fx
            failedAt.remove(session)
            Log.i(TAG, "attached to session $session")
        } catch (t: Throwable) {
            failedAt[session] = SystemClock.elapsedRealtime()
            Log.w(TAG, "attach to $session failed: $t")
        }
    }

    private fun detach(session: Int) {
        effects.remove(session)?.let {
            it.release()
            Log.i(TAG, "released session $session")
        }
    }

    private fun applyAll() {
        val p = params()
        val dead = ArrayList<Int>()
        for ((id, fx) in effects) {
            try {
                fx.apply(p)
            } catch (t: Throwable) {
                Log.w(TAG, "apply to $id failed: $t")
                dead += id
            }
        }
        dead.forEach { detach(it); failedAt[it] = SystemClock.elapsedRealtime() }
        publish()
    }

    private fun params(): EngineParams {
        val s = FxApplication.settings
        val eqOn = s.eqOn
        // As in Sonora FX, the tone shelves ride on the equaliser (on with it).
        val gains = if (eqOn) {
            EqCurve.gains(s.eqBands, s.bassDB, s.bassFrequency, s.trebleDB, s.trebleFrequency)
        } else {
            FloatArray(EqCurve.BANDS)
        }
        val boost = gains.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        val input = if (eqOn) s.eqPreampDB.toFloat() - (if (s.autoPreamp) boost else 0f) else 0f
        return EngineParams(
            active = s.enabled,
            eqGains = gains,
            eqInUse = eqOn && gains.any { it != 0f },
            inputGainDb = input,
            limiter = s.limiterOn,
        )
    }

    private fun publish() {
        val s = FxApplication.settings
        _status.value = Status(
            running = thread != null,
            sessions = effects.size,
            catchAll = s.catchAll && SessionScanner.dumpGranted(app),
            note = note,
        )
    }
}
