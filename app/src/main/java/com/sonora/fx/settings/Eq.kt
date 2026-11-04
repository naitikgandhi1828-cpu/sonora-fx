package com.sonora.fx.settings

import kotlinx.serialization.Serializable

/** Filter shape of one EQ band. Matches the original Sonora FX model. */
@Serializable
enum class EQBandType(val label: String) {
    parametric("Peak"),
    lowPass("Low Pass"),
    highPass("High Pass"),
    lowShelf("Low Shelf"),
    highShelf("High Shelf"),
}

/** One band of the parametric equalizer. */
@Serializable
data class EQBand(
    val id: String = newId(),
    val frequency: Float,
    val gain: Float = 0f,
    val bandwidth: Float = 0.5f,
    val type: EQBandType = EQBandType.parametric,
    val bypass: Boolean = false,
) {
    companion object {
        private var counter = 0L
        fun newId(): String = "b${System.nanoTime()}_${counter++}"
    }
}

/** A named EQ preset: a label plus its ten bands. */
@Serializable
data class EQPreset(
    val id: String = EQBand.newId(),
    val name: String,
    val bands: List<EQBand>,
) {
    companion object {
        /** The ten fixed centre frequencies, 31 Hz … 16 kHz, like FX. */
        val standardFrequencies: List<Float> =
            listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

        fun flatBands(): List<EQBand> =
            standardFrequencies.map { EQBand(frequency = it, gain = 0f) }

        val flat = EQPreset(name = "Flat", bands = flatBands())

        private fun of(name: String, vararg g: Double) = EQPreset(
            id = "builtin.$name",
            name = name,
            bands = standardFrequencies.mapIndexed { i, f -> EQBand(id = "b$i", frequency = f, gain = g[i].toFloat()) },
        )

        /** Built-in presets, 31 Hz … 16 kHz. */
        val builtIn: List<EQPreset> = listOf(
            flat,
            of("Acoustic", 4.5, 4.5, 3.5, 1.0, 1.5, 1.5, 3.0, 3.5, 3.0, 1.5),
            of("Bass Boost", 5.5, 4.5, 3.5, 2.5, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            of("Bass Cut", -5.5, -4.5, -3.5, -2.5, -1.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            of("Classical", 4.5, 3.5, 3.0, 2.5, -1.5, -1.5, 0.0, 2.0, 3.0, 3.5),
            of("Dance", 3.5, 6.5, 5.0, 0.0, 2.0, 3.5, 5.0, 4.5, 3.5, 0.0),
            of("Deep", 5.0, 3.5, 1.5, 1.0, 3.0, 2.5, 1.5, -2.0, -3.5, -4.5),
            of("Electronic", 4.0, 3.5, 1.0, 0.0, -2.0, 2.0, 1.0, 1.0, 4.0, 5.0),
            of("Hip-Hop", 5.0, 4.0, 1.5, 3.0, -1.0, -1.0, 1.5, -0.5, 2.0, 3.0),
            of("Jazz", 4.0, 3.0, 1.5, 2.0, -1.5, -1.5, 0.0, 1.5, 3.0, 3.5),
            of("Latin", 4.5, 3.0, 0.0, 0.0, -1.5, -1.5, -1.5, 0.0, 3.0, 4.5),
            of("Loudness", 6.0, 4.0, 0.0, 0.0, -2.0, 0.0, -1.0, -5.0, 5.0, 1.0),
            of("Lounge", -3.0, -1.5, -0.5, 1.5, 4.0, 2.5, 0.0, -1.5, 2.0, 1.0),
            of("Piano", 3.0, 2.0, 0.0, 2.5, 3.0, 1.5, 3.5, 4.5, 3.0, 3.5),
            of("Pop", -1.5, -1.0, 0.0, 2.0, 4.0, 4.0, 2.0, 0.0, -1.0, -1.5),
            of("R&B", 2.5, 7.0, 5.5, 1.5, -2.5, -1.5, 2.5, 3.0, 3.0, 4.0),
            of("Rock", 5.0, 4.0, 3.0, 1.5, -0.5, -1.0, 0.5, 2.5, 3.5, 4.5),
            of("Small Speakers", 5.5, 4.0, 3.5, 2.5, 1.0, 0.0, -1.0, -2.5, -3.5, -4.0),
            of("Spoken Word", -3.5, -0.5, 0.0, 0.5, 3.5, 4.5, 4.5, 4.0, 2.0, 0.0),
            of("Treble Boost", 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.5, 3.5, 4.5, 5.5),
            of("Treble Cut", 0.0, 0.0, 0.0, 0.0, 0.0, -1.0, -2.5, -3.5, -4.5, -5.5),
            of("Vocal Boost", -1.5, -3.0, -3.0, 1.5, 3.5, 3.5, 3.0, 1.5, 0.0, -1.5),
            of("V-Shape", 5.0, 4.0, 2.0, 0.0, -2.0, -2.0, 0.0, 2.0, 4.0, 5.0),
        )
    }
}
