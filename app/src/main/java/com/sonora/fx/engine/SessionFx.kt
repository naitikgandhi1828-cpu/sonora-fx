package com.sonora.fx.engine

import android.media.audiofx.DynamicsProcessing

/** What every attached session should be doing right now. */
data class EngineParams(
    val active: Boolean,
    val eqGains: FloatArray,
    val eqInUse: Boolean,
    val inputGainDb: Float,
    val limiter: Boolean,
    val ceilingDb: Float = -0.5f,
    val releaseMs: Float = 120f,
)

/**
 * The effect chain on one audio session: DynamicsProcessing with a pre-EQ
 * (EQ bands + tone shelves), input gain (preamp) and a limiter.
 */
class SessionFx(val session: Int) {

    private val dp: DynamicsProcessing

    init {
        val cfg = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CHANNELS,
            /* preEqInUse = */ true, EqCurve.BANDS,
            /* mbcInUse = */ false, 0,
            /* postEqInUse = */ false, 0,
            /* limiterInUse = */ true,
        ).setPreferredFrameDuration(10f).build()
        dp = DynamicsProcessing(PRIORITY, session, cfg)
    }

    /** Applies [p]. Throws if the effect is no longer usable. */
    fun apply(p: EngineParams) {
        val eq = DynamicsProcessing.Eq(true, p.active && p.eqInUse, EqCurve.BANDS)
        for (i in 0 until EqCurve.BANDS) {
            eq.setBand(i, DynamicsProcessing.EqBand(true, EqCurve.cutoffs[i], p.eqGains[i]))
        }
        dp.setPreEqAllChannelsTo(eq)
        dp.setInputGainAllChannelsTo(if (p.active) p.inputGainDb else 0f)
        dp.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(
                /* inUse = */ true,
                /* enabled = */ p.active && p.limiter,
                /* linkGroup = */ 0,
                /* attackTime ms = */ 1f,
                /* releaseTime ms = */ p.releaseMs,
                /* ratio = */ 20f,
                /* threshold dB = */ p.ceilingDb,
                /* postGain dB = */ 0f,
            )
        )
        dp.enabled = p.active
    }

    fun release() {
        runCatching { dp.enabled = false }
        runCatching { dp.release() }
    }

    companion object {
        private const val CHANNELS = 2
        // Above normal so Sonora FX wins control when another app also attaches.
        private const val PRIORITY = 100
    }
}
