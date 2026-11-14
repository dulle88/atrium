package io.github.dulle88.atrium.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyMotionTest {
    @Test fun starsDriftOneToTwoPixelsPerSecondOnA720pScreen() {
        val w = 1280f
        val speed = (SkyMotion.starDriftX(20f, w) - SkyMotion.starDriftX(10f, w)) / 10f
        assertTrue("speed $speed", speed in 1f..2f)
        assertEquals(SkyMotion.starDriftX(0f, w), SkyMotion.starDriftX(SkyMotion.TIME_WRAP_S, w) % w, 0.01f)
    }

    @Test fun twinkleSwingsWidely() {
        var lo = 1f
        var hi = 0f
        var t = 0f
        while (t < 10f) {
            val v = SkyMotion.twinkle(t, 1.3f, 0.4f)
            lo = minOf(lo, v); hi = maxOf(hi, v); t += 0.01f
        }
        assertTrue("range $lo..$hi", lo < 0.4f && hi > 0.98f)
    }

    @Test fun sunBreathesFourPercentEitherWayOverNineSeconds() {
        val values = (0 until 900).map { SkyMotion.sunBreath(it / 100f) }
        assertTrue(values.all { it in 0.959f..1.041f })
        assertTrue(values.max() - values.min() > 0.075f)
        assertEquals(SkyMotion.sunBreath(1.3f), SkyMotion.sunBreath(10.3f), 1e-4f)
    }

    @Test fun shootingStarsCrossTheTopBandEveryFifteenToThirtySecondsForAboutASecond() {
        val m = SkyMotion.Meteor()
        val starts = mutableListOf<Float>()
        val durations = mutableListOf<Float>()
        var wasActive = false
        var start = 0f
        var t = 0f
        while (t < SkyMotion.TIME_WRAP_S) {
            val active = SkyMotion.meteor(t, m)
            if (active) {
                assertTrue(m.brightness in 0f..1f)
                assertTrue("head in sky ${m.headX},${m.headY}", m.headX in -0.2f..1.2f && m.headY in 0f..1f)
                if (m.brightness > 0.9f) assertTrue("head in top band ${m.headY}", m.headY in 0.05f..0.3f)
            }
            if (active && !wasActive) { start = t; starts += t }
            if (!active && wasActive) durations += t - start
            wasActive = active
            t += 0.01f
        }
        assertTrue("count ${starts.size}", starts.size in 120..240)
        for (i in 1 until starts.size) {
            val gap = starts[i] - starts[i - 1]
            assertTrue("gap $gap at ${starts[i]}", gap in 14.9f..30.1f)
        }
        for (d in durations) assertTrue("duration $d", d in 0.78f..1.22f)
    }
}
