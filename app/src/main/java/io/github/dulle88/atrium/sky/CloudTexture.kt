package io.github.dulle88.atrium.sky

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Cloud colours: sunlit side, shaded underside and the thin edge glow. Channels are 0..1. */
data class CloudPalette(val lit: Rgb, val shade: Rgb, val rim: Rgb) {
    fun mix(o: CloudPalette, t: Float) = CloudPalette(lit.mix(o.lit, t), shade.mix(o.shade, t), rim.mix(o.rim, t))
}

/**
 * Inputs to [CloudTexture.palette], quantised so sprites are re-coloured only when the light
 * changes noticeably: sun elevation in 2-degree steps from -12 to 30 (21 steps), dawn or dusk,
 * and moonlight in quarters.
 */
data class CloudLightKey(val elevationStep: Int, val dawn: Boolean, val moonStep: Int) {
    val elevation: Float get() = elevationStep * 2f

    companion object {
        fun of(sunElevation: Double, dawn: Boolean, moonlight: Float) = CloudLightKey(
            (sunElevation.coerceIn(-12.0, 30.0) / 2).roundToInt(), dawn, (moonlight.coerceIn(0f, 1f) * 4).roundToInt()
        )
    }
}

/**
 * Realistic cumulus sprites, ported from the mockup's `makeCloud`: an elliptical envelope eroded
 * by low-frequency fbm plus fine detail, a flat base, self-shadowing towards the light and a rim
 * tint. Shapes (alpha and lighting terms) are computed once; colouring a shape is cheap, so a
 * palette change only re-colours.
 */
object CloudTexture {
    private fun c(r: Int, g: Int, b: Int) = Rgb(r / 255f, g / 255f, b / 255f)
    private fun p(lit: Rgb, shade: Rgb, rim: Rgb) = CloudPalette(lit, shade, rim)

    // The mockup's P table.
    val SUNRISE = p(c(255, 216, 188), c(146, 116, 150), c(255, 196, 150))
    val MORNING = p(c(255, 255, 255), c(170, 184, 208), c(255, 255, 255))
    val NOON = p(c(255, 255, 255), c(166, 180, 206), c(255, 255, 255))
    val SUNSET = p(c(255, 192, 150), c(118, 82, 116), c(255, 164, 104))
    val NIGHT = p(c(120, 134, 170), c(24, 30, 52), c(168, 184, 220))
    val MOON = p(c(152, 166, 202), c(30, 38, 64), c(205, 218, 246))

    /**
     * Palette for a sun elevation (degrees), interpolated through the mockup's presets: the warm
     * dawn/dusk palette at 1 degree, morning at 18, noon at 28, and the night palette (brighter
     * under a bright moon, [moonlight] 0..1) fading in as the sun drops below the horizon.
     */
    fun palette(sunElevation: Float, dawn: Boolean, moonlight: Float): CloudPalette {
        val warm = if (dawn) SUNRISE else SUNSET
        val day = when {
            sunElevation <= 1f -> warm
            sunElevation <= 18f -> warm.mix(MORNING, Noise.smooth(1f, 18f, sunElevation))
            else -> MORNING.mix(NOON, Noise.smooth(18f, 28f, sunElevation))
        }
        val night = NIGHT.mix(MOON, moonlight.coerceIn(0f, 1f))
        return day.mix(night, 1 - Noise.smooth(-10f, 0f, sunElevation))
    }

    fun palette(key: CloudLightKey): CloudPalette = palette(key.elevation, key.dawn, key.moonStep / 4f)

    /** Per-pixel coverage (0 = empty), light (0..1) and rim weight, independent of colour. */
    class Shape(val width: Int, val height: Int, val alpha: FloatArray, val light: FloatArray, val rim: FloatArray)

    /**
     * The cloud's shape. On the Android 9 boxes this computation occasionally came back with garbage in
     * its arrays (alpha or light around -1e30, drawn as a black cloud), a different sprite on each run and
     * never in JVM tests, so it is computed until two runs agree and values are in range.
     */
    fun shape(seed: Int, gw: Int, gh: Int): Shape {
        var prev = computeShape(seed, gw, gh)
        repeat(MAX_SHAPE_TRIES) {
            val next = computeShape(seed, gw, gh)
            if (prev.sameAs(next) && next.inRange()) return next
            prev = next
        }
        return prev.clamped()
    }

    private const val MAX_SHAPE_TRIES = 6

    private fun Shape.sameAs(o: Shape) =
        alpha.contentEquals(o.alpha) && light.contentEquals(o.light) && rim.contentEquals(o.rim)

    private fun FloatArray.allIn01() = all { it >= 0f && it <= 1f }

    private fun Shape.inRange() = alpha.allIn01() && light.allIn01() && rim.allIn01()

    private fun FloatArray.clamp01() = FloatArray(size) { i -> val v = this[i]; if (v >= 0f && v <= 1f) v else 0f }

    private fun Shape.clamped() = Shape(width, height, alpha.clamp01(), light.clamp01(), rim.clamp01())

    private fun computeShape(seed: Int, gw: Int, gh: Int): Shape {
        val aspect = gw.toFloat() / gh
        val d = FloatArray(gw * gh)
        val so = seed * 7.31f
        for (y in 0 until gh) fillDensityRow(d, y, gw, gh, aspect, so)
        val alpha = FloatArray(gw * gh)
        val light = FloatArray(gw * gh)
        val rim = FloatArray(gw * gh)
        for (y in 0 until gh) fillLightRow(d, alpha, light, rim, y, gw, gh)
        return Shape(gw, gh, alpha, light, rim)
    }

    private fun fillDensityRow(d: FloatArray, y: Int, gw: Int, gh: Int, aspect: Float, so: Float) {
        val base = 0.74f
        for (x in 0 until gw) {
            val u = x.toFloat() / gw
            val v = y.toFloat() / gh
            val dx = (u - 0.5f) / 0.47f
            val dy = (v - 0.56f) / 0.44f
            var env = 1 - (dx * dx + dy * dy) + (0.56f - v) * 0.25f
            if (v > base) env -= (v - base) * 6
            // The noise terms add at most 0.905, so this pixel stays empty; skip its fbm.
            if (env * 1.05f + 0.905f <= 0f) { d[y * gw + x] = -1f; continue }
            val n = Noise.fbm(u * aspect * 2.1f + so, v * 2.1f + so * 0.6f, 6)
            val f = Noise.fbm(u * aspect * 7 + so * 2, v * 7, 4)
            d[y * gw + x] = env * 1.05f + (n - 0.5f) * 1.7f + (f - 0.5f) * 0.35f - 0.12f
        }
    }

    private fun density(d: FloatArray, gw: Int, gh: Int, x: Float, y: Float): Float {
        val xi = x.roundToInt()
        val yi = y.roundToInt()
        return if (xi < 0 || yi < 0 || xi >= gw || yi >= gh) 0f else max(0f, d[yi * gw + xi])
    }

    private fun fillLightRow(d: FloatArray, alpha: FloatArray, light: FloatArray, rim: FloatArray, y: Int, gw: Int, gh: Int) {
        val lx = 0.55f
        val ly = -0.83f
        val step = gh * 0.06f
        for (x in 0 until gw) {
            val i = y * gw + x
            val dd = d[i]
            val a = Noise.smooth(0f, 0.5f, dd)
            if (a <= 0f) continue
            val occ = (density(d, gw, gh, x + lx * step, y + ly * step) + density(d, gw, gh, x + lx * step * 2.2f, y + ly * step * 2.2f) +
                density(d, gw, gh, x + lx * step * 4, y + ly * step * 4)) / 3
            alpha[i] = a
            light[i] = (1 - min(0.7f, occ * 0.75f)) * (0.8f + 0.2f * (1 - y.toFloat() / gh))
            rim[i] = (1 - Noise.smooth(0f, 0.3f, dd)) * 0.5f
        }
    }

    /** Colours [s] with [p] into [out] (non-premultiplied ARGB, as `Bitmap.createBitmap(int[])` expects). */
    fun paint(s: Shape, p: CloudPalette, out: IntArray = IntArray(s.width * s.height)): IntArray {
        for (i in 0 until s.width * s.height) {
            val a = s.alpha[i]
            if (a <= 0f) { out[i] = 0; continue }
            val l = s.light[i]
            val rm = s.rim[i]
            fun ch(shade: Float, lit: Float, rimC: Float): Int {
                val v = shade + (lit - shade) * l
                return ((v + (rimC - v) * rm) * 255f).roundToInt().coerceIn(0, 255)
            }
            val r = ch(p.shade.r, p.lit.r, p.rim.r)
            val g = ch(p.shade.g, p.lit.g, p.rim.g)
            val b = ch(p.shade.b, p.lit.b, p.rim.b)
            out[i] = ((a * 255f).roundToInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b
        }
        return out
    }

    fun render(seed: Int, gw: Int, gh: Int, p: CloudPalette): IntArray = paint(shape(seed, gw, gh), p)

    /** One cloud of a layer: sprite size in px (half resolution) and its start position. */
    class Placement(val seed: Int, val width: Int, val height: Int, val x: Float, val y: Float)

    /**
     * The mockup's `layers`: far clouds (6, about 0.15 W, 125 s across, y 0-16 %) and near clouds
     * (4, about 0.30 W, 72 s across, y 8-24 %), with sprites at half resolution for a [screenW] px canvas.
     */
    class Layer(val count: Int, val widthFrac: Float, val crossingS: Float, val yMin: Float, val yMax: Float, val alpha: Float, val seed: Int) {
        fun placements(screenW: Float): List<Placement> {
            val r = Noise.Rng(seed * 101)
            return List(count) { i ->
                val w = max(40, (widthFrac * screenW * (0.8f + r.next() * 0.4f) * 0.5f).roundToInt())
                val h = max(20, (w * 0.46f).roundToInt())
                Placement(seed * 17 + i, w, h, r.next(), yMin + r.next() * (yMax - yMin))
            }
        }
    }

    val FAR = Layer(6, 0.15f, 125f, 0f, 0.16f, 0.82f, 3)
    val NEAR = Layer(4, 0.30f, 72f, 0.08f, 0.24f, 0.95f, 9)
    val LAYERS = listOf(FAR, NEAR)
}
