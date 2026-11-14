package io.github.dulle88.atrium.sky

import kotlin.math.floor

/**
 * Seeded value noise ported from the approved sky-window mockup (mulberry32 RNG, a 256-entry
 * permutation table, smoothstep-interpolated lattice values and fbm with 2.03 lacunarity), so the
 * TV draws the same cloud and moon shapes the mockup showed.
 */
object Noise {
    /** mulberry32: a tiny deterministic RNG returning 0..1, identical to the mockup's `rng`. */
    class Rng(seed: Int) {
        private var s = seed
        fun next(): Float {
            s += 0x6D2B79F5
            var t = (s xor (s ushr 15)) * (1 or s)
            t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
            return (((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0).toFloat()
        }
    }

    private val perm = IntArray(512).also { out ->
        val r = Rng(42)
        val p = IntArray(256) { it }
        for (i in 255 downTo 1) {
            val j = floor(r.next() * (i + 1)).toInt()
            val t = p[i]; p[i] = p[j]; p[j] = t
        }
        for (i in 0 until 512) out[i] = p[i and 255]
    }

    private fun h2(i: Int, j: Int): Float = perm[(perm[i and 255] + j) and 255] / 255f

    fun value(x: Float, y: Float): Float {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val xf = x - xi
        val yf = y - yi
        val u = xf * xf * (3 - 2 * xf)
        val v = yf * yf * (3 - 2 * yf)
        val a = h2(xi, yi)
        val b = h2(xi + 1, yi)
        val c = h2(xi, yi + 1)
        val d = h2(xi + 1, yi + 1)
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
    }

    /** Fractal noise normalised to 0..1. */
    fun fbm(x: Float, y: Float, octaves: Int): Float {
        var t = 0f
        var a = 0.5f
        var f = 1f
        var n = 0f
        for (i in 0 until octaves) {
            t += a * value(x * f, y * f)
            n += a
            f *= 2.03f
            a *= 0.5f
        }
        return t / n
    }

    fun smooth(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}
