package io.github.dulle88.atrium.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyWindowTest {
    private val bar = WindowRect(0.05f, 0.05f, 0.9f, 0.228f)
    private val w = 1280f
    private val h = 720f
    private val bx = 0.05f * w
    private val by = 0.05f * h
    private val bw = 0.9f * w
    private val bh = 0.228f * h

    private fun state(sun: Double, moonElev: Double = 30.0, ill: Double = 1.0, ha: Double = 10.0) =
        SkyState(sun, 0.0, ha, moonElev, 0.0, 180.0, ill, 0.0, 0.0)

    private fun win(s: SkyState) = SkyWindow.from(s, w, h, bar)

    @Test fun sunSitsInTheTopRightCornerOfTheBar() {
        val noon = win(state(31.0))
        assertEquals(bx + bw * 0.91f, noon.sunX, 0.01f)
        assertEquals(by + bh * 0.3f, noon.sunY, 0.01f)
        assertEquals(bh * 0.075f, noon.sunRadius, 0.01f)
        assertEquals(0f, noon.sunWarmth, 1e-6f)
        val low = win(state(0.0))
        assertEquals(by + bh * 0.8f, low.sunY, 0.01f)
        assertEquals(1f, low.sunWarmth, 1e-6f)
        val mid = win(state(14.0))
        assertTrue(mid.sunY < low.sunY && mid.sunY > noon.sunY)
    }

    @Test fun sunShowsByDayAndMoonOnlyAtNightWhenUp() {
        val day = win(state(20.0))
        assertEquals(1f, day.sunVisibility, 1e-6f)
        assertEquals(0f, day.moonVisibility, 1e-6f)
        val night = win(state(-30.0))
        assertEquals(0f, night.sunVisibility, 1e-6f)
        assertEquals(1f, night.moonVisibility, 1e-6f)
        assertEquals(by + bh * 0.4f, night.moonY, 0.01f)
        assertEquals(bh * 0.26f, night.moonRadius, 0.01f)
        assertEquals(0f, win(state(-30.0, moonElev = -10.0)).moonVisibility, 1e-6f)
    }

    @Test fun cloudLightFollowsSunAndMoon() {
        assertPalette(CloudTexture.NOON, CloudTexture.palette(win(state(31.0)).clouds))
        assertPalette(CloudTexture.MOON, CloudTexture.palette(win(state(-30.0, ill = 1.0)).clouds))
        assertPalette(CloudTexture.NIGHT, CloudTexture.palette(win(state(-30.0, moonElev = -20.0)).clouds))
        assertTrue(win(state(0.0, ha = -80.0)).clouds.dawn)
    }

    @Test fun cloudsMoveRightAndDoNotJumpWhenTheClockWraps() {
        val sw = 200f
        val a = SkyWindow.cloudX(0.3f, sw, w, 72f, 10f)
        val b = SkyWindow.cloudX(0.3f, sw, w, 72f, 11f)
        assertTrue(b > a || b < 0f)
        assertEquals("about W/72 px per s", w / 72f, b - a, 1.5f)
        val before = SkyWindow.cloudX(0.3f, sw, w, 72f, SkyMotion.TIME_WRAP_S - 0.05f)
        val after = SkyWindow.cloudX(0.3f, sw, w, 72f, 0f)
        assertEquals(after, before, 1f)
        val x = SkyWindow.cloudX(0.99f, sw, w, 125f, 50f)
        assertTrue(x >= -sw && x <= w)
    }
}
