package com.sonora.fx.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Persistent settings, backed by SharedPreferences.
 *
 * Stage 1 covers the equalizer fields that per-device EQ needs. Reverb,
 * spatial and the rest arrive as the audio engine is ported (Stage 2+).
 * The preference KEYS match the original FX file ("sonora.fx.settings") so a
 * future full build reads the same saved data.
 */
class FxSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _version = MutableStateFlow(0)
    /** Bumped on every change, so the UI can re-read after e.g. a per-device switch. */
    val version: StateFlow<Int> = _version

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    // Held strongly: SharedPreferences only keeps a weak reference.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _version.value++
        listeners.forEach { runCatching(it) }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    var enabled: Boolean
        get() = prefs.getBoolean("fx.enabled", false)
        set(v) = prefs.edit().putBoolean("fx.enabled", v).apply()

    var catchAll: Boolean
        get() = prefs.getBoolean("fx.catchAll", true)
        set(v) = prefs.edit().putBoolean("fx.catchAll", v).apply()

    /** Output limiter (Stage 2 key; not in the original FX file). */
    var limiterOn: Boolean
        get() = prefs.getBoolean("fx.limiter", true)
        set(v) = prefs.edit().putBoolean("fx.limiter", v).apply()

    /** Separate EQ per output device (speaker, wired, each Bluetooth device). */
    var perDeviceEq: Boolean
        get() = prefs.getBoolean("eq.perDevice", true)
        set(v) = prefs.edit().putBoolean("eq.perDevice", v).apply()

    /** Limiter ceiling in dBFS (-6 … 0). */
    var limiterCeilingDB: Double
        get() = dbl("fx.limiterCeiling", -0.5, -6.0, 0.0)
        set(v) = putDbl("fx.limiterCeiling", v)

    /** Limiter release in ms (10 … 1000). */
    var limiterReleaseMs: Double
        get() = dbl("fx.limiterRelease", 120.0, 10.0, 1000.0)
        set(v) = putDbl("fx.limiterRelease", v)

    /** Presets the user saved, as JSON under "eq.userPresets". */
    var userPresets: List<EQPreset>
        get() {
            val raw = prefs.getString("eq.userPresets", null) ?: return emptyList()
            return try {
                json.decodeFromString<List<EQPreset>>(raw).filter { validBands(it.bands) }
            } catch (t: Throwable) {
                emptyList()
            }
        }
        set(v) = prefs.edit().putString("eq.userPresets", json.encodeToString(v)).apply()

    /** Loads [p]'s bands and name. */
    fun applyPreset(p: EQPreset) {
        eqBands = p.bands.map { it.copy() }
        eqPresetName = p.name
    }

    // --- Reverb ------------------------------------------------------------

    var reverbOn: Boolean
        get() = prefs.getBoolean("rv.on", false)
        set(v) = prefs.edit().putBoolean("rv.on", v).apply()

    /** "studio" (EnvironmentalReverb, every control) or "classic" (PresetReverb). */
    var reverbEngine: String
        get() = prefs.getString("rv.engine", "studio") ?: "studio"
        set(v) = prefs.edit().putString("rv.engine", v).apply()

    var reverbRoom: String
        get() = prefs.getString("rv.room", "chamber") ?: "chamber"
        set(v) = prefs.edit().putString("rv.room", v).apply()

    var reverbMixDB: Double
        get() = dbl("rv.mix", -10.0, -30.0, 0.0)
        set(v) = putDbl("rv.mix", v)
    var reverbDecayS: Double
        get() = dbl("rv.decay", 1.9, 0.2, 20.0)
        set(v) = putDbl("rv.decay", v)
    var reverbSize: Double
        get() = dbl("rv.size", 55.0, 0.0, 100.0)
        set(v) = putDbl("rv.size", v)
    var reverbPreDelayMs: Double
        get() = dbl("rv.predelay", 26.0, 0.0, 300.0)
        set(v) = putDbl("rv.predelay", v)
    var reverbClarity: Double
        get() = dbl("rv.clarity", 60.0, 0.0, 100.0)
        set(v) = putDbl("rv.clarity", v)
    var reverbBassDistance: Double
        get() = dbl("rv.bassDistance", 60.0, 0.0, 100.0)
        set(v) = putDbl("rv.bassDistance", v)
    var reverbTrebleDecay: Double
        get() = dbl("rv.trebleDecay", 0.7, 0.1, 2.0)
        set(v) = putDbl("rv.trebleDecay", v)
    var reverbHighCutHz: Double
        get() = dbl("rv.highCut", 10000.0, 1000.0, 20000.0)
        set(v) = putDbl("rv.highCut", v)
    var reverbDiffusion: Double
        get() = dbl("rv.diffusion", 85.0, 0.0, 100.0)
        set(v) = putDbl("rv.diffusion", v)
    var reverbEarly: Double
        get() = dbl("rv.early", 40.0, 0.0, 100.0)
        set(v) = putDbl("rv.early", v)

    /** Picks a room and loads its shape into the controls (Mix, Clarity and Bass Distance stay). */
    fun loadRoom(r: com.sonora.fx.engine.Room) {
        prefs.edit()
            .putString("rv.room", r.id)
            .putString("rv.decay", r.decayS.toString())
            .putString("rv.size", r.sizePct.toString())
            .putString("rv.predelay", r.preDelayMs.toString())
            .putString("rv.trebleDecay", r.trebleDecay.toString())
            .putString("rv.highCut", r.highCutHz.toString())
            .putString("rv.diffusion", r.diffusionPct.toString())
            .putString("rv.early", r.earlyPct.toString())
            .apply()
    }

    // --- Spatial & stereo --------------------------------------------------

    var spatialOn: Boolean
        get() = prefs.getBoolean("sp.on", false)
        set(v) = prefs.edit().putBoolean("sp.on", v).apply()

    /** "subtle", "headphones", "wide" or "hall". */
    var spatialMode: String
        get() = prefs.getString("sp.mode", "headphones") ?: "headphones"
        set(v) = prefs.edit().putString("sp.mode", v).apply()

    var spatialAmount: Double
        get() = dbl("sp.amount", 55.0, 0.0, 100.0)
        set(v) = putDbl("sp.amount", v)
    var spatialDepth: Double
        get() = dbl("sp.depth", 30.0, 0.0, 100.0)
        set(v) = putDbl("sp.depth", v)

    /** -1 (left) … 0 (centre) … +1 (right). */
    var balance: Double
        get() = dbl("st.balance", 0.0, -1.0, 1.0)
        set(v) = putDbl("st.balance", v)

    /** Lower the input by the curve's biggest boost so the EQ can't clip. */
    var autoPreamp: Boolean
        get() = prefs.getBoolean("eq.autoPreamp", true)
        set(v) = prefs.edit().putBoolean("eq.autoPreamp", v).apply()

    var eqOn: Boolean
        get() = prefs.getBoolean("eq.on", false)
        set(v) = prefs.edit().putBoolean("eq.on", v).apply()

    var eqPresetName: String
        get() = prefs.getString("eq.preset", EQPreset.flat.name) ?: EQPreset.flat.name
        set(v) = prefs.edit().putString("eq.preset", v).apply()

    var eqPreampDB: Double
        get() = dbl("eq.preamp", 0.0, -12.0, 12.0)
        set(v) = putDbl("eq.preamp", v)

    var bassDB: Double
        get() = dbl("eq.bass", 0.0, -18.0, 18.0)
        set(v) = putDbl("eq.bass", v)

    var trebleDB: Double
        get() = dbl("eq.treble", 0.0, -18.0, 18.0)
        set(v) = putDbl("eq.treble", v)

    var bassFrequency: Double
        get() = dbl("eq.bassHz", 120.0, 40.0, 400.0)
        set(v) = putDbl("eq.bassHz", v)

    var trebleFrequency: Double
        get() = dbl("eq.trebleHz", 6000.0, 2000.0, 14000.0)
        set(v) = putDbl("eq.trebleHz", v)

    /** The ten EQ bands, stored as JSON under the FX key "eq.bands". */
    var eqBands: List<EQBand>
        get() {
            val raw = prefs.getString("eq.bands", null) ?: return EQPreset.flatBands()
            return try {
                val bands = json.decodeFromString<List<EQBand>>(raw)
                if (validBands(bands)) bands else EQPreset.flatBands()
            } catch (t: Throwable) {
                EQPreset.flatBands()
            }
        }
        set(v) {
            val bands = if (validBands(v)) v else EQPreset.flatBands()
            prefs.edit().putString("eq.bands", json.encodeToString(bands)).apply()
        }

    /** The raw JSON of eq.bands, for copying between devices without re-encoding. */
    var eqBandsJson: String?
        get() = prefs.getString("eq.bands", null)
        set(v) {
            val e = prefs.edit()
            if (v == null) e.remove("eq.bands") else e.putString("eq.bands", v)
            e.apply()
        }

    private fun dbl(key: String, def: Double, min: Double, max: Double): Double {
        val raw = prefs.getString(key, null) ?: return def
        val d = raw.toDoubleOrNull() ?: return def
        if (d.isNaN() || d.isInfinite()) return def
        return d.coerceIn(min, max)
    }

    private fun putDbl(key: String, v: Double) {
        prefs.edit().putString(key, v.toString()).apply()
    }

    companion object {
        const val PREFS = "sonora.fx.settings"

        /** Same checks FX uses when loading saved bands. */
        fun validBands(bands: List<EQBand>): Boolean {
            if (bands.size != 10) return false
            return bands.none {
                it.frequency.isNaN() || it.frequency.isInfinite() ||
                    it.gain.isNaN() || it.gain.isInfinite() ||
                    it.bandwidth.isNaN() || it.bandwidth.isInfinite()
            }
        }
    }
}
