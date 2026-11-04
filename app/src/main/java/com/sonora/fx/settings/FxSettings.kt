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
        get() = prefs.getBoolean("fx.catchAll", false)
        set(v) = prefs.edit().putBoolean("fx.catchAll", v).apply()

    /** Output limiter (Stage 2 key; not in the original FX file). */
    var limiterOn: Boolean
        get() = prefs.getBoolean("fx.limiter", true)
        set(v) = prefs.edit().putBoolean("fx.limiter", v).apply()

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
