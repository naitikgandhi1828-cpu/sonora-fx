package com.sonora.fx.engine

import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.PresetReverb
import kotlin.math.log10
import kotlin.math.ln

/** One reverb room: the starting point its tile loads into the controls. */
data class Room(
    val id: String,
    val name: String,
    val decayS: Double,
    val sizePct: Double,
    val preDelayMs: Double,
    val diffusionPct: Double,
    val trebleDecay: Double,
    val highCutHz: Double,
    val earlyPct: Double,
    /** How much Bass Distance applies in this room (small rooms 0, cathedral 1). */
    val bassScale: Double,
    val classic: Short,
)

object Rooms {
    val all = listOf(
        Room("booth", "Vocal Booth", 0.5, 20.0, 5.0, 60.0, 0.6, 9000.0, 60.0, 0.0, PresetReverb.PRESET_SMALLROOM),
        Room("drum", "Drum Room", 0.7, 30.0, 8.0, 70.0, 0.7, 10000.0, 55.0, 0.0, PresetReverb.PRESET_SMALLROOM),
        Room("small", "Small Room", 0.9, 35.0, 10.0, 75.0, 0.7, 9000.0, 50.0, 0.0, PresetReverb.PRESET_MEDIUMROOM),
        Room("ambience", "Ambience", 1.2, 50.0, 12.0, 90.0, 0.8, 12000.0, 30.0, 0.2, PresetReverb.PRESET_MEDIUMROOM),
        Room("chamber", "Chamber", 1.9, 55.0, 26.0, 85.0, 0.7, 10000.0, 40.0, 0.3, PresetReverb.PRESET_LARGEROOM),
        Room("vplate", "Vocal Plate", 1.6, 60.0, 15.0, 100.0, 0.9, 14000.0, 20.0, 0.2, PresetReverb.PRESET_PLATE),
        Room("bplate", "Bright Plate", 2.2, 65.0, 10.0, 100.0, 1.0, 16000.0, 20.0, 0.3, PresetReverb.PRESET_PLATE),
        Room("concert", "Concert Hall", 2.6, 80.0, 25.0, 90.0, 0.6, 9000.0, 40.0, 0.6, PresetReverb.PRESET_MEDIUMHALL),
        Room("warm", "Warm Hall", 3.2, 85.0, 30.0, 90.0, 0.45, 7000.0, 35.0, 0.6, PresetReverb.PRESET_LARGEHALL),
        Room("arena", "Arena", 4.5, 95.0, 40.0, 80.0, 0.5, 8000.0, 45.0, 0.8, PresetReverb.PRESET_LARGEHALL),
        Room("cathedral", "Cathedral", 6.5, 95.0, 45.0, 90.0, 0.35, 7000.0, 30.0, 1.0, PresetReverb.PRESET_LARGEHALL),
        Room("dark", "Dark Space", 8.0, 100.0, 50.0, 100.0, 0.25, 4000.0, 20.0, 1.0, PresetReverb.PRESET_LARGEHALL),
        Room("infinite", "Infinite", 12.0, 100.0, 60.0, 100.0, 0.5, 9000.0, 10.0, 1.0, PresetReverb.PRESET_LARGEHALL),
    )

    fun byId(id: String?): Room = all.firstOrNull { it.id == id } ?: all.first { it.id == "chamber" }
}

/** User-facing reverb settings, as the screen shows them. */
data class ReverbSpec(
    val classic: Boolean,
    val room: Room,
    val mixDb: Double,
    val decayS: Double,
    val sizePct: Double,
    val preDelayMs: Double,
    val clarityPct: Double,
    val trebleDecay: Double,
    val highCutHz: Double,
    val diffusionPct: Double,
    val earlyPct: Double,
)

/** Translation of Sonora's controls onto Android's EnvironmentalReverb. */
object ReverbModel {

    /** Android's reverb rings 2–3× longer than its "decay time" says. */
    private const val ANDROID_DECAY_FACTOR = 3.0

    /** Gap before the room answers: 15–40 ms by room size, scaled by Clarity. */
    fun clarityGapMs(sizePct: Double, clarityPct: Double): Double =
        (15.0 + 25.0 * (sizePct / 100.0)) * (clarityPct / 100.0)

    fun settings(r: ReverbSpec): EnvironmentalReverb.Settings = EnvironmentalReverb.Settings().apply {
        roomLevel = 0
        roomHFLevel = mb(-1200.0 * ln(20000.0 / r.highCutHz.coerceIn(1000.0, 20000.0)) / ln(2.0), -9000, 0)
        decayTime = (r.decayS * 1000.0 / ANDROID_DECAY_FACTOR).toInt().coerceIn(100, 20000)
        decayHFRatio = (r.trebleDecay * 1000.0).toInt().coerceIn(100, 2000).toShort()
        reflectionsLevel = if (r.earlyPct <= 0.0) -9000 else mb(2000.0 * log10(r.earlyPct / 100.0), -9000, 1000)
        reflectionsDelay = r.preDelayMs.toInt().coerceIn(0, 300)
        reverbLevel = mb(r.mixDb * 100.0, -9000, 2000)
        reverbDelay = clarityGapMs(r.sizePct, r.clarityPct).toInt().coerceIn(0, 100)
        diffusion = (r.diffusionPct * 10).toInt().coerceIn(0, 1000).toShort()
        density = (r.sizePct * 10).toInt().coerceIn(0, 1000).toShort()
    }

    /** Early reflections only (no tail), used by Spatial's Room Depth when reverb is off. */
    fun depthOnly(depthPct: Double): EnvironmentalReverb.Settings = EnvironmentalReverb.Settings().apply {
        roomLevel = 0
        roomHFLevel = -600
        decayTime = 100
        decayHFRatio = 500
        reflectionsLevel = if (depthPct <= 0.0) -9000 else mb(2000.0 * log10(depthPct / 100.0) - 300.0, -9000, 1000)
        reflectionsDelay = 8
        reverbLevel = -9000
        reverbDelay = 0
        diffusion = 800
        density = 500
    }

    private fun mb(v: Double, min: Int, max: Int): Short = v.toInt().coerceIn(min, max).toShort()
}
