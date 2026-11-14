package io.github.dulle88.atrium.sky

import kotlin.math.PI
import kotlin.math.exp

/** Full-screen sky behind the panels, in pixels for a given canvas size. The sun and moon live in the top bar ([SkyWindow]). */
data class SkyScene(
    val zenith: Rgb,
    val horizon: Rgb,
    val horizonY: Float,
    /** Warm horizon glow under the sun around sunrise and sunset. */
    val glowX: Float,
    val glowY: Float,
    val glowAmount: Float,
    val glowColor: Rgb,
    val nightFactor: Float,
    val starRotation: Float
) {
    companion object {
        fun from(state: SkyState, epochMs: Long, width: Float, height: Float): SkyScene {
            val s = state.sunElevation
            val hy = height * 0.86f
            val sx = (0.5 + state.sunAzimuth.coerceIn(-130.0, 130.0) / 130.0 * 0.45).toFloat() * width
            val glowAmount = exp(-(((s + 1.5) / 6.5) * ((s + 1.5) / 6.5))).toFloat()
            val dawn = state.sunHourAngle < 0
            val glowColor = if (dawn) Rgb(1f, 150 / 255f, 120 / 255f) else Rgb(1f, 128 / 255f, 62 / 255f)
            return SkyScene(
                zenith = SkyPalette.zenith(s),
                horizon = SkyPalette.horizon(s),
                horizonY = hy,
                glowX = sx, glowY = hy,
                glowAmount = glowAmount,
                glowColor = glowColor,
                nightFactor = state.nightFactor.toFloat(),
                starRotation = ((epochMs / 86_400_000.0 * 0.9) % (2 * PI)).toFloat()
            )
        }
    }
}
