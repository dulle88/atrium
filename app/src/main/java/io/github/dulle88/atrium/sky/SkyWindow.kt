package io.github.dulle88.atrium.sky

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** The top bar's rectangle as fractions of the canvas (left, top, width, height). */
data class WindowRect(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * Stylised sun and moon in the top-right corner of the top bar (the "sky window"), as in the
 * approved mockup: not astronomical, but driven by the sun's elevation and the moon's phase.
 */
data class SkyWindow(
    val sunX: Float,
    val sunY: Float,
    val sunRadius: Float,
    /** 0 = high and white, 1 = on the horizon, orange and flattened. */
    val sunWarmth: Float,
    val sunVisibility: Float,
    val moonX: Float,
    val moonY: Float,
    val moonRadius: Float,
    val moonVisibility: Float,
    val moonIllumination: Float,
    /** Moon phase angle in whole degrees (0 new, 180 full), the key for its sprite. */
    val moonElongation: Int,
    val clouds: CloudLightKey
) {
    companion object {
        /** Sun height in the bar: 0 when low, 1 at 28 degrees and above (the mockup's `sm(0,28,elev)`). */
        fun sunHigh(elevation: Double): Float = SkyClock.smooth(0.0, 28.0, elevation).toFloat()

        fun from(state: SkyState, width: Float, height: Float, bar: WindowRect): SkyWindow {
            val bx = bar.left * width
            val by = bar.top * height
            val bw = bar.width * width
            val bh = bar.height * height
            val s = state.sunElevation
            val high = sunHigh(s)
            val x = bx + bw * 0.91f
            val moonUp = SkyClock.smooth(-3.0, 5.0, state.moonElevation).toFloat()
            val night = (1 - SkyClock.smooth(-6.0, -1.0, s)).toFloat()
            return SkyWindow(
                sunX = x,
                sunY = by + bh * (0.8f - 0.5f * high),
                sunRadius = bh * 0.075f,
                sunWarmth = 1 - high,
                sunVisibility = SkyClock.smooth(-2.5, 0.5, s).toFloat(),
                moonX = x,
                moonY = by + bh * 0.4f,
                moonRadius = bh * 0.26f,
                moonVisibility = night * moonUp,
                moonIllumination = state.moonIllumination.toFloat(),
                moonElongation = state.moonElongationDeg.roundToInt() % 360,
                clouds = CloudLightKey.of(s, state.sunHourAngle < 0, moonUp * Noise.smooth(0.5f, 1f, state.moonIllumination.toFloat()))
            )
        }

        /**
         * Left edge in px of a cloud [spriteW] px wide (as drawn) that starts at [phase] (0..1) of its
         * path and crosses a [w] px canvas in about [crossingS] seconds. The period is rounded so it
         * divides [SkyMotion.TIME_WRAP_S], so nothing jumps when the animation clock wraps.
         */
        fun cloudX(phase: Float, spriteW: Float, w: Float, crossingS: Float, time: Float): Float {
            val span = w + spriteW
            val laps = max(1, (SkyMotion.TIME_WRAP_S / (crossingS * span / w)).roundToInt())
            val period = SkyMotion.TIME_WRAP_S / laps
            val f = phase + time / period
            return (f - floor(f)) * span - spriteW
        }
    }
}
