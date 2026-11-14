package io.github.dulle88.atrium.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudTextureTest {
    private val w = 96
    private val h = 44
    private fun alpha(px: Int) = px ushr 24
    private fun r(px: Int) = (px shr 16) and 0xFF
    private fun b(px: Int) = px and 0xFF

    @Test fun cloudHasSoftOpaqueBodyAndEmptyBorder() {
        val px = CloudTexture.render(51, w, h, CloudTexture.NOON)
        val opaque = px.count { alpha(it) > 200 }
        val partial = px.count { alpha(it) in 1..200 }
        assertTrue("opaque $opaque", opaque > w * h / 10)
        assertTrue("soft edge $partial", partial > w * h / 20)
        for (x in 0 until w) {
            assertEquals("top row x=$x", 0, alpha(px[x]))
            assertEquals("bottom row x=$x", 0, alpha(px[(h - 1) * w + x]))
        }
        for (y in 0 until h) {
            assertEquals(0, alpha(px[y * w]))
            assertEquals(0, alpha(px[y * w + w - 1]))
        }
    }

    @Test fun baseIsFlatterAndDarkerThanTheTop() {
        val px = CloudTexture.render(51, w, h, CloudTexture.NOON)
        fun rowCoverage(y: Int) = (0 until w).sumOf { alpha(px[y * w + it]) }
        // Below the flat base (74 % of the height) coverage falls off fast.
        assertTrue(rowCoverage((h * 0.9f).toInt()) < rowCoverage((h * 0.6f).toInt()) / 2)
        val lit = px.filter { alpha(it) > 200 }
        assertTrue("noon cloud has shade and highlights", lit.maxOf { r(it) } - lit.minOf { r(it) } > 40)
        assertTrue(lit.maxOf { r(it) } >= 240)
    }

    @Test fun differentSeedsGiveDifferentShapes() {
        assertNotEquals(CloudTexture.render(51, w, h, CloudTexture.NOON).toList(), CloudTexture.render(52, w, h, CloudTexture.NOON).toList())
    }

    @Test fun recolouringKeepsTheShape() {
        val s = CloudTexture.shape(60, w, h)
        val day = CloudTexture.paint(s, CloudTexture.NOON)
        val night = CloudTexture.paint(s, CloudTexture.NIGHT)
        for (i in day.indices) assertEquals(alpha(day[i]), alpha(night[i]))
        val i = day.indices.first { alpha(day[it]) > 200 }
        assertTrue("night is darker", r(night[i]) < r(day[i]))
        assertTrue("night is blue", b(night[i]) > r(night[i]))
    }

    @Test fun paletteHitsTheMockupPresets() {
        assertPalette(CloudTexture.NOON, CloudTexture.palette(28f, false, 0f))
        assertPalette(CloudTexture.MORNING, CloudTexture.palette(18f, true, 0f))
        assertPalette(CloudTexture.SUNRISE, CloudTexture.palette(1f, true, 0f))
        assertPalette(CloudTexture.SUNSET, CloudTexture.palette(1f, false, 0f))
        assertPalette(CloudTexture.NIGHT, CloudTexture.palette(-20f, false, 0f))
        assertPalette(CloudTexture.MOON, CloudTexture.palette(-20f, false, 1f))
    }

    @Test fun paletteBlendsSmoothlyBetweenPresets() {
        val mid = CloudTexture.palette(9.5f, false, 0f)
        assertTrue(mid.shade.r < CloudTexture.MORNING.shade.r && mid.shade.r > CloudTexture.SUNSET.shade.r)
        var prev = CloudTexture.palette(-12f, false, 0f)
        var e = -11.9f
        while (e < 30f) {
            val p = CloudTexture.palette(e, false, 0f)
            assertTrue("jump at $e", kotlin.math.abs(p.shade.r - prev.shade.r) < 0.02f && kotlin.math.abs(p.lit.g - prev.lit.g) < 0.02f)
            prev = p
            e += 0.1f
        }
    }

    @Test fun lightKeyQuantisesToAboutTwentyDaySteps() {
        val keys = (-200..400).map { CloudLightKey.of(it / 10.0, false, 0f) }.toSet()
        assertTrue("steps ${keys.size}", keys.size in 18..23)
        assertEquals(CloudLightKey.of(12.2, false, 0.3f), CloudLightKey.of(12.6, false, 0.2f))
        assertPalette(CloudTexture.NOON, CloudTexture.palette(CloudLightKey.of(29.0, false, 0f)))
    }

    @Test fun layersMatchTheMockup() {
        val far = CloudTexture.FAR.placements(1280f)
        val near = CloudTexture.NEAR.placements(1280f)
        assertEquals(6, far.size)
        assertEquals(4, near.size)
        // Half-resolution sprites drawn at 2x: about 0.15 W and 0.30 W wide on screen.
        for (p in far) assertTrue(p.width * 2 in (0.15f * 1280 * 0.8f).toInt() - 1..(0.15f * 1280 * 1.2f).toInt() + 1 && p.y in 0f..0.16f)
        for (p in near) assertTrue(p.width * 2 in (0.30f * 1280 * 0.8f).toInt() - 1..(0.30f * 1280 * 1.2f).toInt() + 1 && p.y in 0.08f..0.24f)
        assertEquals(far.map { it.seed }.toSet().size, 6)
    }
}

/** Palettes equal to within float rounding. */
fun assertPalette(expected: CloudPalette, actual: CloudPalette) {
    for ((e, a) in listOf(expected.lit to actual.lit, expected.shade to actual.shade, expected.rim to actual.rim)) {
        org.junit.Assert.assertEquals("$expected vs $actual", e.r, a.r, 1e-5f)
        org.junit.Assert.assertEquals("$expected vs $actual", e.g, a.g, 1e-5f)
        org.junit.Assert.assertEquals("$expected vs $actual", e.b, a.b, 1e-5f)
    }
}
