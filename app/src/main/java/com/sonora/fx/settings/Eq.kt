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
    }
}
