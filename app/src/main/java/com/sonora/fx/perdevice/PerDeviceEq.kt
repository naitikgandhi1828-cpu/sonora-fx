package com.sonora.fx.perdevice

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.sonora.fx.diag.Log
import android.widget.Toast
import com.sonora.fx.FxApplication
import com.sonora.fx.settings.EQBand
import com.sonora.fx.settings.EQPreset
import com.sonora.fx.settings.FxSettings
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

/**
 * Separate EQ per output device, like Sonora's "Separate EQ per device".
 *
 * Watches which output is active (phone speaker, wired, USB, each Bluetooth
 * device by name). On a change it saves the EQ being left and loads the EQ for
 * the new device. A device never seen before starts from the current EQ. The
 * EQ on/off switch and everything outside the EQ stay shared.
 */
object PerDeviceEq {

    private const val TAG = "SonoraFX.PerDevice"
    private const val PD_PREFS = "sonora.fx.perdevice"
    private const val LAST = "last.key"

    private var started = false
    private lateinit var app: Context
    private var audio: AudioManager? = null
    private lateinit var main: Handler
    private lateinit var pd: SharedPreferences

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val check = Runnable { check() }

    private val _label = MutableStateFlow("Phone Speaker")
    /** Name of the output the EQ currently belongs to. */
    val label: StateFlow<String> = _label

    fun start(context: Context) {
        try {
            if (started) return
            started = true
            app = context.applicationContext
            audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            pd = app.getSharedPreferences(PD_PREFS, Context.MODE_PRIVATE)
            main = Handler(Looper.getMainLooper())
            audio!!.registerAudioDeviceCallback(object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = schedule()
                override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = schedule()
            }, main)
            schedule()
        } catch (t: Throwable) {
            Log.w(TAG, "start failed", t)
        }
    }

    private fun schedule() {
        // Let the route settle: Bluetooth often reports in several steps.
        main.removeCallbacks(check)
        main.postDelayed(check, 700L)
    }

    private fun check() {
        try {
            val (key, label) = currentDevice()
            _label.value = label
            val last = pd.getString(LAST, null)
            if (key == last) return

            val s = FxApplication.settings
            if (!s.perDeviceEq) {
                // One shared EQ: just remember where we are, swap nothing.
                pd.edit().putString(LAST, key).apply()
                return
            }
            if (last != null) save(s, last)
            val loaded = load(s, key)
            pd.edit().putString(LAST, key).apply()
            Log.i(TAG, "output $last -> $key ${if (loaded) "(loaded)" else "(new)"}")

            // Stay quiet on the first run; afterwards name the active EQ when on.
            if (last != null && s.eqOn) {
                Toast.makeText(app, "Equalizer: $label", Toast.LENGTH_SHORT).show()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "check failed", t)
        }
    }

    private fun save(s: FxSettings, k: String) {
        pd.edit().apply {
            val bands = s.eqBandsJson
            if (bands != null) putString("$k|bands", bands) else remove("$k|bands")
            putString("$k|preset", s.eqPresetName)
            putString("$k|preamp", s.eqPreampDB.toString())
            putString("$k|bass", s.bassDB.toString())
            putString("$k|treble", s.trebleDB.toString())
            putString("$k|bassHz", s.bassFrequency.toString())
            putString("$k|trebleHz", s.trebleFrequency.toString())
        }.apply()
    }

    private fun load(s: FxSettings, k: String): Boolean {
        val preset = pd.getString("$k|preset", null) ?: return false // never seen: keep current EQ

        val storedJson = pd.getString("$k|bands", null)
        val bands: List<EQBand> = try {
            if (storedJson != null) json.decodeFromString<List<EQBand>>(storedJson)
            else EQPreset.flatBands()
        } catch (t: Throwable) {
            Log.w(TAG, "bad saved bands for $k", t)
            EQPreset.flatBands()
        }
        if (FxSettings.validBands(bands)) s.eqBands = bands

        s.eqPresetName = preset
        s.eqPreampDB = num("$k|preamp", s.eqPreampDB)
        s.bassDB = num("$k|bass", s.bassDB)
        s.trebleDB = num("$k|treble", s.trebleDB)
        s.bassFrequency = num("$k|bassHz", s.bassFrequency)
        s.trebleFrequency = num("$k|trebleHz", s.trebleFrequency)
        return true
    }

    private fun num(key: String, fallback: Double): Double {
        val v = pd.getString(key, null) ?: return fallback
        val d = v.toDoubleOrNull() ?: return fallback
        return if (d.isNaN() || d.isInfinite()) fallback else d
    }

    /** {key, label} for the output Android is most likely using right now. */
    private fun currentDevice(): Pair<String, String> {
        var bt: Pair<String, String>? = null
        var usb: Pair<String, String>? = null
        var wired: Pair<String, String>? = null
        var aid: Pair<String, String>? = null

        val all = audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: emptyArray()
        for (d in all) {
            val name = d.productName?.toString()?.trim().orEmpty()
            when (d.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER ->
                    bt = "bt:$name" to name.ifEmpty { "Bluetooth" }

                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_ACCESSORY ->
                    usb = "usb:$name" to name.ifEmpty { "USB audio" }

                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES ->
                    wired = "wired" to "Wired headphones"

                AudioDeviceInfo.TYPE_HEARING_AID ->
                    aid = "aid:$name" to name.ifEmpty { "Hearing aid" }
            }
        }
        return bt ?: aid ?: usb ?: wired ?: ("speaker" to "Phone Speaker")
    }
}
