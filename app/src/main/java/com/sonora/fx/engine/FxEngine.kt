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
 * Sessions come from:
 *  - players that announce themselves (ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION),
 *    handled by [FxService]'s receiver;
 *  - "Catch every app", two ways:
 *     - whole-phone mode (default, no setup): one effect chain on the output
 *       mix (session 0), so every app is covered. App sessions then only get
 *       reverb, which can't run on the output mix.
 *     - precise mode: [SessionScanner] polling the audio dump, when the DUMP
 *       permission was granted (Shizuku). Some phones (e.g. Xiaomi) block that.
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
        /** Whole-phone mode: effects on the output mix. */
        val global: Boolean = false,
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

    private var globalFx: SessionFx? = null
    private var inGlobal = false
    private var globalFailed = false

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
            globalFx?.release(); globalFx = null; inGlobal = false; globalFailed = false
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
        val dump = SessionScanner.dumpGranted(app)
        if (!wanted) globalFailed = false // try again next time it's switched on
        setGlobal(wanted && !dump && !globalFailed)
        if (!wanted || !dump) {
            note = if (wanted && globalFailed) {
                "This phone blocks effects on its whole output. Apps that announce their sound still work."
            } else null
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

    /** Switches whole-phone mode on or off and rebuilds app sessions to match. */
    private fun setGlobal(on: Boolean) {
        if (on == inGlobal) return
        if (on) {
            try {
                val fx = SessionFx(0, full = true)
                fx.apply(params())
                globalFx = fx
                inGlobal = true
                Log.i(TAG, "whole-phone mode on (output mix)")
            } catch (t: Throwable) {
                globalFailed = true
                Log.w(TAG, "whole-phone mode unavailable: $t")
                return
            }
        } else {
            globalFx?.release()
            globalFx = null
            inGlobal = false
            Log.i(TAG, "whole-phone mode off")
        }
        // App sessions switch between full chains and reverb-only.
        val ids = effects.keys.toList()
        ids.forEach { detach(it) }
        failedAt.clear()
        ids.forEach { attach(it) }
    }

    private fun attach(session: Int) {
        if (session in effects) return
        val failed = failedAt[session]
        if (failed != null && SystemClock.elapsedRealtime() - failed < RETRY_MS) return
        try {
            val fx = SessionFx(session, full = !inGlobal)
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
        globalFx?.let { g ->
            try {
                g.apply(p)
            } catch (t: Throwable) {
                Log.w(TAG, "whole-phone apply failed: $t")
                g.release()
                globalFx = null
                inGlobal = false
                globalFailed = true
            }
        }
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
        val input = if (eqOn) s.eqPreampDB.toFloat() + (if (s.autoPreamp) EqCurve.autoCut(gains) else 0f) else 0f
        // Stereo Field balance: turn the far side down.
        val b = s.balance
        val balL = if (b > 0) db(1 - b) else 0f
        val balR = if (b < 0) db(1 + b) else 0f

        // Reverb + Spatial.
        val rvOn = s.reverbOn
        val spec = if (rvOn) reverbSpec(s) else null
        val spOn = s.spatialOn
        val depth = if (spOn) s.spatialDepth else 0.0
        var env: android.media.audiofx.EnvironmentalReverb.Settings? = null
        var preset: Short? = null
        if (spec != null && !spec.classic) {
            // Room Depth adds to the room's own early reflections.
            env = ReverbModel.settings(spec.copy(earlyPct = (spec.earlyPct + depth * 0.5).coerceAtMost(100.0)))
        } else {
            if (spec != null) preset = spec.room.classic
            if (depth > 0) env = ReverbModel.depthOnly(depth)
        }

        // Bass Distance, scaled by how big the room is.
        val bassAmt = if (spec != null) (s.reverbBassDistance / 100.0) * spec.room.bassScale else 0.0

        val mode = s.spatialMode
        val virtScale = when (mode) { "subtle" -> 0.5; "hall" -> 0.8; else -> 1.0 }
        val virtMode = when (mode) {
            "headphones", "hall" -> android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_BINAURAL
            "wide" -> android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL
            else -> android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_AUTO
        }

        return EngineParams(
            active = s.enabled,
            eqGains = gains,
            eqInUse = eqOn && gains.any { it != 0f },
            inputGainDb = input,
            limiter = s.limiterOn,
            ceilingDb = s.limiterCeilingDB.toFloat(),
            releaseMs = s.limiterReleaseMs.toFloat(),
            balanceL = balL,
            balanceR = balR,
            bassGainDb = (-4.0 * bassAmt).toFloat(),
            bassRatio = (1.0 + 2.0 * bassAmt).toFloat(),
            envReverb = env,
            presetReverb = preset,
            virtStrength = if (spOn) (s.spatialAmount * 10 * virtScale).toInt().coerceIn(0, 1000).toShort() else null,
            virtMode = virtMode,
        )
    }

    private fun db(lin: Double): Float =
        if (lin <= 0.001) -60f else (20 * kotlin.math.log10(lin)).toFloat()

    fun reverbSpec(s: com.sonora.fx.settings.FxSettings) = ReverbSpec(
        classic = s.reverbEngine == "classic",
        room = Rooms.byId(s.reverbRoom),
        mixDb = s.reverbMixDB,
        decayS = s.reverbDecayS,
        sizePct = s.reverbSize,
        preDelayMs = s.reverbPreDelayMs,
        clarityPct = s.reverbClarity,
        trebleDecay = s.reverbTrebleDecay,
        highCutHz = s.reverbHighCutHz,
        diffusionPct = s.reverbDiffusion,
        earlyPct = s.reverbEarly,
    )

    private fun publish() {
        val s = FxApplication.settings
        _status.value = Status(
            running = thread != null,
            sessions = effects.size,
            catchAll = s.catchAll && (inGlobal || SessionScanner.dumpGranted(app)),
            global = inGlobal,
            note = note,
        )
    }
}
