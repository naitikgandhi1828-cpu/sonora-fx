package com.sonora.fx.engine

import com.sonora.fx.settings.EQBand
import com.sonora.fx.settings.EQBandType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * Turns the parametric EQ (bands with type, frequency, gain and bandwidth in
 * octaves), plus the bass/treble tone shelves, into the per-band gains of
 * Android's DynamicsProcessing graphic EQ.
 *
 * DynamicsProcessing has no Q control: each of its bands applies one gain from
 * the previous cutoff up to its own. So the combined response of the real
 * filters (RBJ cookbook biquads) is evaluated at the centre of [BANDS]
 * log-spaced bands, and that gain is what each band gets.
 */
object EqCurve {

    const val BANDS = 32
    private const val FS = 48000.0
    private const val F_LO = 20.0
    private const val F_HI = 20000.0
    private const val MIN_DB = -40f
    private const val MAX_DB = 30f

    /** Upper edge of each band, in Hz. */
    val cutoffs: FloatArray
    /** Where each band's gain is sampled (geometric centre), in Hz. */
    val centres: DoubleArray

    init {
        val edges = DoubleArray(BANDS + 1) { i -> F_LO * (F_HI / F_LO).pow(i.toDouble() / BANDS) }
        cutoffs = FloatArray(BANDS) { i -> edges[i + 1].toFloat() }
        centres = DoubleArray(BANDS) { i -> sqrt(edges[i] * edges[i + 1]) }
    }

    /** One filter stage, normalised so a0 == 1. */
    private class Biquad(
        val b0: Double, val b1: Double, val b2: Double,
        val a1: Double, val a2: Double,
    ) {
        fun magnitudeDb(f: Double): Double {
            val w = 2 * PI * f / FS
            val c1 = cos(w); val s1 = sin(w)
            val c2 = cos(2 * w); val s2 = sin(2 * w)
            val nr = b0 + b1 * c1 + b2 * c2
            val ni = -(b1 * s1 + b2 * s2)
            val dr = 1 + a1 * c1 + a2 * c2
            val di = -(a1 * s1 + a2 * s2)
            val num = nr * nr + ni * ni
            val den = dr * dr + di * di
            if (num <= 0.0 || den <= 0.0) return MIN_DB.toDouble()
            return 10 * log10(num / den)
        }
    }

    private fun make(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
        Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

    private fun clampF(f: Double) = f.coerceIn(10.0, FS * 0.49)

    /** alpha for a bandwidth given in octaves (AVAudioUnitEQ style). */
    private fun alphaBw(w0: Double, octaves: Double): Double {
        val bw = octaves.coerceIn(0.05, 5.0)
        return sin(w0) * sinh(ln(2.0) / 2 * bw * w0 / sin(w0))
    }

    private fun peak(f0: Double, db: Double, octaves: Double): Biquad {
        val a = 10.0.pow(db / 40)
        val w0 = 2 * PI * clampF(f0) / FS
        val al = alphaBw(w0, octaves)
        val c = cos(w0)
        return make(1 + al * a, -2 * c, 1 - al * a, 1 + al / a, -2 * c, 1 - al / a)
    }

    private fun lowShelf(f0: Double, db: Double): Biquad {
        val a = 10.0.pow(db / 40)
        val w0 = 2 * PI * clampF(f0) / FS
        val c = cos(w0)
        val al = sin(w0) / 2 * sqrt(2.0) // shelf slope S = 1
        val sa = 2 * sqrt(a) * al
        return make(
            a * ((a + 1) - (a - 1) * c + sa),
            2 * a * ((a - 1) - (a + 1) * c),
            a * ((a + 1) - (a - 1) * c - sa),
            (a + 1) + (a - 1) * c + sa,
            -2 * ((a - 1) + (a + 1) * c),
            (a + 1) + (a - 1) * c - sa,
        )
    }

    private fun highShelf(f0: Double, db: Double): Biquad {
        val a = 10.0.pow(db / 40)
        val w0 = 2 * PI * clampF(f0) / FS
        val c = cos(w0)
        val al = sin(w0) / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * al
        return make(
            a * ((a + 1) + (a - 1) * c + sa),
            -2 * a * ((a - 1) + (a + 1) * c),
            a * ((a + 1) + (a - 1) * c - sa),
            (a + 1) - (a - 1) * c + sa,
            2 * ((a - 1) - (a + 1) * c),
            (a + 1) - (a - 1) * c - sa,
        )
    }

    private fun lowPass(f0: Double, octaves: Double): Biquad {
        val w0 = 2 * PI * clampF(f0) / FS
        val c = cos(w0)
        val al = alphaBw(w0, octaves)
        return make((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al)
    }

    private fun highPass(f0: Double, octaves: Double): Biquad {
        val w0 = 2 * PI * clampF(f0) / FS
        val c = cos(w0)
        val al = alphaBw(w0, octaves)
        return make((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al)
    }

    private fun stage(b: EQBand): Biquad? {
        if (b.bypass) return null
        val f = b.frequency.toDouble()
        val g = b.gain.toDouble()
        val bw = b.bandwidth.toDouble()
        return when (b.type) {
            EQBandType.parametric -> if (g == 0.0) null else peak(f, g, bw)
            EQBandType.lowShelf -> if (g == 0.0) null else lowShelf(f, g)
            EQBandType.highShelf -> if (g == 0.0) null else highShelf(f, g)
            EQBandType.lowPass -> lowPass(f, bw)
            EQBandType.highPass -> highPass(f, bw)
        }
    }

    /**
     * Gains in dB for each of the [BANDS] bands. [bands] may be null (EQ off);
     * the tone shelves are applied either way.
     */
    fun gains(
        bands: List<EQBand>?,
        bassDb: Double, bassHz: Double,
        trebleDb: Double, trebleHz: Double,
    ): FloatArray = response(bands, bassDb, bassHz, trebleDb, trebleHz, centres)

    /** Combined response in dB at any [freqs] (for drawing the curve). */
    fun response(
        bands: List<EQBand>?,
        bassDb: Double, bassHz: Double,
        trebleDb: Double, trebleHz: Double,
        freqs: DoubleArray,
    ): FloatArray {
        val stages = ArrayList<Biquad>()
        bands?.forEach { b -> stage(b)?.let(stages::add) }
        if (bassDb != 0.0) stages += lowShelf(bassHz, bassDb)
        if (trebleDb != 0.0) stages += highShelf(trebleHz, trebleDb)
        return FloatArray(freqs.size) { i ->
            val f = freqs[i]
            stages.sumOf { it.magnitudeDb(f) }.toFloat().coerceIn(MIN_DB, MAX_DB)
        }
    }

    /** The pre-amp cut auto pre-amp applies for these gains (<= 0). */
    fun autoCut(gains: FloatArray): Float = -(gains.maxOrNull()?.coerceAtLeast(0f) ?: 0f)
}
