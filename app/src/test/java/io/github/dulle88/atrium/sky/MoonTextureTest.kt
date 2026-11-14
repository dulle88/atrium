package io.github.dulle88.atrium.sky

import org.junit.Assert.assertTrue
import org.junit.Test

class MoonTextureTest {
    private fun centreAlpha(elong: Int): Int {
        val c = MoonTexture.SIZE / 2
        return MoonTexture.render(elong)[c * MoonTexture.SIZE + c] ushr 24
    }

    @Test fun newMoonCentreIsTransparent() = assertTrue(centreAlpha(0) <= 5)
    @Test fun fullMoonCentreIsOpaque() = assertTrue(centreAlpha(180) >= 250)
    @Test fun outsideDiscStaysTransparent() = assertTrue(MoonTexture.render(180)[0] ushr 24 == 0)

    @Test fun fullMoonHasNoDarkSpots() {
        val px = MoonTexture.render(180)
        val n = MoonTexture.SIZE
        var lo = 255
        var hi = 0
        for (j in 0 until n) for (i in 0 until n) {
            val x = (i + 0.5f) / n * 2 - 1
            val y = (j + 0.5f) / n * 2 - 1
            if (x * x + y * y > 0.9f) continue
            val r = (px[j * n + i] shr 16) and 0xFF
            lo = minOf(lo, r); hi = maxOf(hi, r)
        }
        assertTrue("darkest sea $lo", lo >= 130)
        assertTrue("seas visible $lo..$hi", hi - lo >= 30)
    }

    @Test fun earthshineKeepsTheDarkSideFaint() {
        val c = MoonTexture.SIZE / 2
        val a = MoonTexture.render(0, earthshine = 0.1f)[c * MoonTexture.SIZE + c] ushr 24
        assertTrue("alpha $a", a in 20..30)
    }

    @Test fun waxingIsLitFromTheRightAndWaningFromTheLeft() {
        val n = MoonTexture.SIZE
        val row = n / 2 * n
        fun a(e: Int, x: Int) = MoonTexture.render(e)[row + x] ushr 24
        assertTrue(a(90, n * 3 / 4) > 200 && a(90, n / 4) < 20)
        assertTrue(a(270, n / 4) > 200 && a(270, n * 3 / 4) < 20)
    }
}
