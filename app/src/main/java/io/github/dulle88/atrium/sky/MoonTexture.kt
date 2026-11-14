package io.github.dulle88.atrium.sky

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Procedural near side of the moon, ported from the mockup's `moonSprite`: bright, smooth highlands,
 * soft diffuse grey seas (a regional mask of the real maria shaped by fbm, no craters, so no dark
 * spots), small soft Tycho and Copernicus highlights, real phase lighting with a soft terminator,
 * and optional faint earthshine on the unlit side. Lighting lives in alpha, so with no earthshine
 * the dark side is transparent.
 */
object MoonTexture {
    const val SIZE = 160

    /** Maria centres and radii in disc coordinates (x right, y down): x, y, rx, ry. */
    private val MARIA = arrayOf(
        floatArrayOf(-0.55f, -0.05f, 0.36f, 0.55f), floatArrayOf(-0.27f, -0.42f, 0.27f, 0.25f),
        floatArrayOf(0.17f, -0.38f, 0.17f, 0.16f), floatArrayOf(0.3f, -0.07f, 0.22f, 0.18f),
        floatArrayOf(0.72f, -0.24f, 0.11f, 0.12f), floatArrayOf(0.6f, 0.15f, 0.13f, 0.2f),
        floatArrayOf(0.37f, 0.32f, 0.1f, 0.1f), floatArrayOf(-0.2f, 0.38f, 0.2f, 0.14f),
        floatArrayOf(-0.52f, 0.38f, 0.1f, 0.1f)
    )

    /** Bright spots: x, y, size, as Tycho and Copernicus. */
    private val HIGHLIGHTS = arrayOf(floatArrayOf(-0.12f, 0.68f, 0.035f), floatArrayOf(-0.38f, -0.1f, 0.03f))

    private val albedo: FloatArray by lazy { buildAlbedo() }

    private fun buildAlbedo(): FloatArray {
        val out = FloatArray(SIZE * SIZE)
        for (j in 0 until SIZE) for (i in 0 until SIZE) {
            val nx = (i + 0.5f) / SIZE * 2 - 1
            val ny = (j + 0.5f) / SIZE * 2 - 1
            if (nx * nx + ny * ny > 1f) continue
            var alb = 0.8f + (Noise.fbm(nx * 5 + 4, ny * 5 + 9, 5) - 0.5f) * 0.12f +
                (Noise.fbm(nx * 18, ny * 18, 3) - 0.5f) * 0.04f
            var region = 0f
            for (m in MARIA) {
                val ex = (nx - m[0]) / (m[2] * 1.25f)
                val ey = (ny - m[1]) / (m[3] * 1.25f)
                region = maxOf(region, exp(-(ex * ex + ey * ey) * 0.9f))
            }
            val sea = (region * (0.55f + 0.9f * Noise.fbm(nx * 2.2f + 5, ny * 2.2f + 1, 5)) - 0.12f).coerceIn(0f, 1f)
            alb *= 1 - 0.26f * sea
            for (h in HIGHLIGHTS) {
                val tx = nx - h[0]
                val ty = ny - h[1]
                alb = min(1f, alb + 0.12f * exp(-(tx * tx + ty * ty) / (h[2] * h[2] * 2)))
            }
            out[j * SIZE + i] = alb
        }
        return out
    }

    /**
     * ARGB pixels (non-premultiplied) of the moon lit for [elongationDeg] (0 new, 180 full; up to 180
     * waxing, lit from the right). [earthshine] (about 0.1 at night) keeps the dark side faintly visible.
     */
    fun render(elongationDeg: Int, earthshine: Float = 0f): IntArray {
        val e = elongationDeg * PI.toFloat() / 180f
        val lx = sin(e)
        val lz = -cos(e)
        val px = IntArray(SIZE * SIZE)
        for (j in 0 until SIZE) for (i in 0 until SIZE) {
            val nx = (i + 0.5f) / SIZE * 2 - 1
            val ny = (j + 0.5f) / SIZE * 2 - 1
            val r2 = nx * nx + ny * ny
            if (r2 > 1f) continue
            val nz = sqrt(1 - r2)
            val lit = Noise.smooth(-0.03f, 0.22f, nx * lx + nz * lz)
            val v = min(255f, albedo[j * SIZE + i] * 300f * (0.25f + 0.75f * lit))
            val edge = ((1 - sqrt(r2)) * SIZE * 0.5f).coerceIn(0f, 1f)
            val alpha = edge * min(1f, lit + earthshine * (1 - lit)) * 255f
            px[j * SIZE + i] = (alpha.toInt() shl 24) or (v.toInt() shl 16) or ((v * 0.975f).toInt() shl 8) or (v * 0.925f).toInt()
        }
        return px
    }
}
