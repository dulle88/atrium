package io.github.dulle88.atrium.sky

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Time-driven sky motion shared by both renderers. `time` is animation seconds that wrap at
 * [TIME_WRAP_S]; every periodic motion here divides that wrap so nothing jumps when it resets.
 */
object SkyMotion {
    const val TIME_WRAP_S = 3600f

    private const val STAR_DRIFT_TURNS = 4f // screen widths per wrap: about 1.4 px/s at 1280 px
    private const val METEOR_SLOT_S = 22.5f
    private const val METEOR_JITTER_S = 7.5f

    private fun frac(x: Float) = x - floor(x)

    /** Horizontal star drift in px, added to the daily rotation and wrapped by the renderer. */
    fun starDriftX(time: Float, w: Float): Float = frac(time * STAR_DRIFT_TURNS / TIME_WRAP_S) * w

    /** Star brightness multiplier, 0.3..1. */
    fun twinkle(time: Float, speed: Float, phase: Float): Float = 0.3f + 0.35f * (1f + sin(time * speed + phase))

    /** Sun and moon glow scale, breathing by 4 % either way every 9 s. */
    fun sunBreath(time: Float): Float = 1f + 0.04f * sin(time * (2 * PI / 9.0).toFloat())

    /** A shooting star's state; reused between frames so drawing allocates nothing. */
    class Meteor {
        /** Head position as fractions of canvas width and height. */
        var headX = 0f
        var headY = 0f
        /** Unit direction of travel in pixel space. */
        var dirX = 0f
        var dirY = 0f
        /** Tail length as a fraction of canvas width. */
        var tail = 0f
        /** 0..1 envelope: quick fade-in, slower fade-out. */
        var brightness = 0f
    }

    private fun hash(n: Int, salt: Int): Float {
        var x = n * 0x27d4eb2d + salt * 0x165667b1
        x = x xor (x ushr 15)
        x *= -0x7a143595
        x = x xor (x ushr 13)
        x *= -0x3d4d51cb
        x = x xor (x ushr 16)
        return (x ushr 8) / 16_777_216f
    }

    /**
     * Fills [out] and returns true while a shooting star is in flight. One meteor per 22.5 s slot,
     * starting at a random point in the slot's first 7.5 s, so gaps run 15..30 s; each lasts 0.8..1.2 s.
     * [aspect] is height / width, used to keep the path straight in pixels.
     */
    fun meteor(time: Float, out: Meteor, aspect: Float = 9f / 16f): Boolean {
        val slot = floor(time / METEOR_SLOT_S).toInt()
        val start = slot * METEOR_SLOT_S + hash(slot, 1) * METEOR_JITTER_S
        val duration = 0.8f + 0.4f * hash(slot, 2)
        val p = (time - start) / duration
        if (p < 0f || p >= 1f) return false

        // Shallow paths through the top bar's sky window, starting in its upper half.
        val angle = (6f + 10f * hash(slot, 3)) * (PI / 180.0).toFloat()
        val leftward = hash(slot, 4) < 0.5f
        out.dirX = cos(angle) * if (leftward) -1f else 1f
        out.dirY = sin(angle)
        val x0 = 0.3f + 0.35f * hash(slot, 5)
        val y0 = 0.06f + 0.06f * hash(slot, 6)
        val travel = 0.24f * p // fraction of width
        out.headX = x0 + out.dirX * travel
        out.headY = y0 + out.dirY * travel / aspect
        out.tail = 0.1f * sqrt(p.coerceAtMost(0.4f) / 0.4f)
        out.brightness = if (p < 0.12f) p / 0.12f else (1f - p) / 0.88f
        return true
    }
}
