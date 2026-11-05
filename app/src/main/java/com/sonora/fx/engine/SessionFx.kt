package com.sonora.fx.engine

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import com.sonora.fx.diag.Log

/** What every attached session should be doing right now. */
data class EngineParams(
    val active: Boolean,
    val eqGains: FloatArray,
    val eqInUse: Boolean,
    val inputGainDb: Float,
    val limiter: Boolean,
    val ceilingDb: Float = -0.5f,
    val releaseMs: Float = 120f,
    /** Per-channel trim for Stereo Field balance, dB (<= 0). */
    val balanceL: Float = 0f,
    val balanceR: Float = 0f,
    /** Bass Distance: low band (<150 Hz) gain change and ratio. */
    val bassGainDb: Float = 0f,
    val bassRatio: Float = 1f,
    /** Studio reverb (or Room Depth reflections); null = no EnvironmentalReverb. */
    val envReverb: EnvironmentalReverb.Settings? = null,
    /** Classic reverb preset; null = off. */
    val presetReverb: Short? = null,
    /** Virtualizer strength 0..1000; null = off. */
    val virtStrength: Short? = null,
    val virtMode: Int = Virtualizer.VIRTUALIZATION_MODE_AUTO,
)

/**
 * The effect chain on one audio session: DynamicsProcessing with a pre-EQ
 * (EQ bands + tone shelves), input gain (preamp) and a limiter.
 */
class SessionFx(val session: Int, val full: Boolean = true) {

    /**
     * Null when [full] is false: in whole-phone mode the output mix (session 0)
     * carries EQ, dynamics and spatial, and app sessions only get reverb, which
     * Android can't run on the output mix.
     */
    private val dp: DynamicsProcessing?

    init {
        dp = if (!full) null else {
            val cfg = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                CHANNELS,
                /* preEqInUse = */ true, EqCurve.BANDS,
                /* mbcInUse = */ true, 2,
                /* postEqInUse = */ false, 0,
                /* limiterInUse = */ true,
            ).setPreferredFrameDuration(10f).build()
            DynamicsProcessing(PRIORITY, session, cfg)
        }
    }

    /** Applies [p]. Throws if the effect is no longer usable. */
    fun apply(p: EngineParams) {
        val dp = dp
        if (dp != null) applyDynamics(dp, p)
        // Reverb is an insert on app sessions; on the output mix (0) Android
        // makes it an auxiliary effect that players must opt into, so skip it there.
        if (session != 0) applyReverb(p)
        if (full) applyVirtualizer(p)
    }

    private fun applyDynamics(dp: DynamicsProcessing, p: EngineParams) {
        val eq = DynamicsProcessing.Eq(true, p.active && p.eqInUse, EqCurve.BANDS)
        for (i in 0 until EqCurve.BANDS) {
            eq.setBand(i, DynamicsProcessing.EqBand(true, EqCurve.cutoffs[i], p.eqGains[i]))
        }
        dp.setPreEqAllChannelsTo(eq)
        dp.setInputGainbyChannel(0, if (p.active) p.inputGainDb + p.balanceL else 0f)
        dp.setInputGainbyChannel(1, if (p.active) p.inputGainDb + p.balanceR else 0f)

        // Bass Distance: the direct bass under 150 Hz steps back a little.
        val bassOn = p.active && (p.bassGainDb != 0f || p.bassRatio != 1f)
        val mbc = DynamicsProcessing.Mbc(true, bassOn, 2)
        mbc.setBand(0, DynamicsProcessing.MbcBand(
            true, 150f, 50f, 200f, p.bassRatio, -24f, 6f, -90f, 1f, 0f, p.bassGainDb))
        mbc.setBand(1, DynamicsProcessing.MbcBand(
            true, 20000f, 10f, 100f, 1f, 0f, 0f, -90f, 1f, 0f, 0f))
        dp.setMbcAllChannelsTo(mbc)
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

    private var env: EnvironmentalReverb? = null
    private var preset: PresetReverb? = null
    private var virt: Virtualizer? = null
    private var envFailed = false
    private var presetFailed = false
    private var virtFailed = false

    private fun applyReverb(p: EngineParams) {
        val e = if (p.active) p.envReverb else null
        if (e == null) {
            env?.let { runCatching { it.enabled = false; it.release() } }
            env = null
        } else if (!envFailed) {
            try {
                val fx = env ?: EnvironmentalReverb(PRIORITY, session).also { env = it }
                fx.properties = e
                fx.enabled = true
            } catch (t: Throwable) {
                envFailed = true
                env = null
                Log.w(TAG, "reverb unavailable on session $session: $t")
            }
        }

        val pr = if (p.active) p.presetReverb else null
        if (pr == null) {
            preset?.let { runCatching { it.enabled = false; it.release() } }
            preset = null
        } else if (!presetFailed) {
            try {
                val fx = preset ?: PresetReverb(PRIORITY, session).also { preset = it }
                fx.preset = pr
                fx.enabled = true
            } catch (t: Throwable) {
                presetFailed = true
                preset = null
                Log.w(TAG, "classic reverb unavailable on session $session: $t")
            }
        }
    }

    private fun applyVirtualizer(p: EngineParams) {
        val st = if (p.active) p.virtStrength else null
        if (st == null) {
            virt?.let { runCatching { it.enabled = false; it.release() } }
            virt = null
            return
        }
        if (virtFailed) return
        try {
            val fx = virt ?: Virtualizer(PRIORITY, session).also { virt = it }
            if (fx.strengthSupported) fx.setStrength(st)
            runCatching { fx.forceVirtualizationMode(p.virtMode) }
            fx.enabled = true
        } catch (t: Throwable) {
            virtFailed = true
            virt = null
            Log.w(TAG, "spatial unavailable on session $session: $t")
        }
    }

    fun release() {
        listOfNotNull(env, preset, virt).forEach { fx -> runCatching { fx.enabled = false; fx.release() } }
        env = null; preset = null; virt = null
        dp?.let { runCatching { it.enabled = false }; runCatching { it.release() } }
    }

    companion object {
        private const val TAG = "SonoraFX.Session"
        private const val CHANNELS = 2
        // Above normal so Sonora FX wins control when another app also attaches.
        private const val PRIORITY = 100
    }
}
