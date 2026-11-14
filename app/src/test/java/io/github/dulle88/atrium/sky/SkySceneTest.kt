package io.github.dulle88.atrium.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SkySceneTest {
    private fun scene(iso: String): SkyScene {
        val ms = Instant.parse(iso).toEpochMilli()
        return SkyScene.from(SkyClock.skyState(ms), ms, 1920f, 1080f)
    }

    @Test fun starRotationStaysWithinOneTurn() {
        val s = scene("2026-06-21T11:00:00Z")
        assertTrue(s.starRotation >= 0f && s.starRotation < (2 * Math.PI).toFloat())
    }

    @Test fun starsOffAtNoonAndOnAtMidnight() {
        assertEquals(0f, scene("2026-06-21T11:00:00Z").nightFactor, 1e-6f)
        assertEquals(1f, scene("2026-12-21T22:00:00Z").nightFactor, 1e-6f)
    }

    @Test fun horizonGlowOnlyAroundSunriseAndSunset() {
        assertTrue(scene("2026-10-29T15:40:00Z").glowAmount > 0.5f) // about sunset in Novi Sad
        assertTrue(scene("2026-10-29T11:00:00Z").glowAmount < 0.01f)
    }

    @Test fun paletteIsDarkAtNightAndBrightByDay() {
        assertTrue(SkyPalette.zenith(-30.0).b < 0.2f)
        assertTrue(SkyPalette.horizon(45.0).r > 0.5f)
    }

    @Test fun fullMoonIsBrightAtCentreAndNewMoonIsDark() {
        val c = MoonTexture.SIZE / 2 * MoonTexture.SIZE + MoonTexture.SIZE / 2
        // Lighting lives in alpha now, so measure the alpha-weighted red channel.
        fun weighted(px: Int) = ((px shr 16) and 0xFF) * (px ushr 24) / 255
        val full = weighted(MoonTexture.render(180)[c])
        val new = weighted(MoonTexture.render(0)[c])
        assertTrue("full $full", full > 90)
        assertTrue("new $new", new < 30)
    }
}
