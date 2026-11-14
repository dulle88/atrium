package io.github.dulle88.atrium.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SkyClockTest {
    private val zone = ZoneId.of("Europe/Belgrade")

    @Test fun solarNoonElevationOn28Oct2026() {
        // Solar noon in Novi Sad is about 11:24 CET (10:24 UTC); declination is about -12.9 deg.
        val ms = Instant.parse("2026-10-28T10:24:00Z").toEpochMilli()
        val e = SkyClock.sunPosition(ms).elevationDeg
        assertTrue("elevation was $e", e in 31.0..33.0)
    }

    @Test fun summerDayIsLongerThanWinterDay() {
        val summer = SkyClock.sunTimes(LocalDate.of(2026, 6, 21), zone)
        val winter = SkyClock.sunTimes(LocalDate.of(2026, 12, 21), zone)
        val summerLen = summer.sunsetMinute!! - summer.sunriseMinute!!
        val winterLen = winter.sunsetMinute!! - winter.sunriseMinute!!
        assertTrue("summer $summerLen", summerLen in 920..970)   // 15h20m - 16h10m
        assertTrue("winter $winterLen", winterLen in 480..540)   // 8h00m - 9h00m
    }

    @Test fun sunriseBeforeNoonBeforeSunset() {
        val t = SkyClock.sunTimes(LocalDate.of(2026, 10, 28), zone)
        assertTrue(t.sunriseMinute!! < t.solarNoonMinute)
        assertTrue(t.solarNoonMinute < t.sunsetMinute!!)
    }

    @Test fun moonIsNewOnKnownNewMoon() {
        val ms = Instant.parse("2026-02-17T12:01:00Z").toEpochMilli()
        assertTrue(SkyClock.skyState(ms).moonIllumination < 0.03)
    }

    @Test fun moonIsFullOnKnownFullMoon() {
        val ms = Instant.parse("2026-03-03T11:38:00Z").toEpochMilli()
        assertTrue(SkyClock.skyState(ms).moonIllumination > 0.97)
    }

    @Test fun daylightFactorsAtMiddayAndMidnight() {
        val noon = SkyClock.skyState(Instant.parse("2026-06-21T11:00:00Z").toEpochMilli())
        assertEquals(1.0, noon.dayFactor, 1e-9)
        assertEquals(0.0, noon.nightFactor, 1e-9)
        val night = SkyClock.skyState(Instant.parse("2026-12-21T22:00:00Z").toEpochMilli())
        assertEquals(0.0, night.dayFactor, 1e-9)
        assertEquals(1.0, night.nightFactor, 1e-9)
    }

    @Test fun juneFullMoonIsLowInTheSky() {
        val ms = Instant.parse("2026-06-29T22:30:00Z").toEpochMilli()
        val e = SkyClock.skyState(ms).moonElevation
        assertTrue("moon elevation was $e", e < 30)
    }

    @Test fun decemberFullMoonIsHighInTheSky() {
        val ms = Instant.parse("2026-12-23T23:00:00Z").toEpochMilli()
        val e = SkyClock.skyState(ms).moonElevation
        assertTrue("moon elevation was $e", e > 55)
    }

    @Test fun sunriseUsesLocalClockOnSpringForwardDay() {
        val d = SkyClock.sunTimes(LocalDate.of(2026, 3, 29), zone).sunriseMinute!! -
            SkyClock.sunTimes(LocalDate.of(2026, 3, 28), zone).sunriseMinute!!
        assertTrue("diff $d", d in 55..62)
    }

    @Test fun sunsetUsesLocalClockOnFallBackDay() {
        val d = SkyClock.sunTimes(LocalDate.of(2026, 10, 25), zone).sunsetMinute!! -
            SkyClock.sunTimes(LocalDate.of(2026, 10, 24), zone).sunsetMinute!!
        assertTrue("diff $d", d in -62..-55)
    }

    /**
     * References from the NOAA Solar Calculator spreadsheet formulas (NOAA_Solar_Calculations_day.xls),
     * evaluated independently at the event time for 45.2517 N, 19.8369 E, refraction 0.833 deg.
     * Local clock minutes: 21 Jun (CEST) 04:52.9 and 20:32.1, 21 Dec (CET) 07:16.6 and 16:00.8,
     * 28 Oct (CET) 06:13.8 and 16:34.3.
     */
    private fun assertSunTimesNear(date: LocalDate, sunriseRef: Double, sunsetRef: Double) {
        val t = SkyClock.sunTimes(date, zone)
        assertEquals("sunrise $date", sunriseRef, t.sunriseMinute!!.toDouble(), 3.0)
        assertEquals("sunset $date", sunsetRef, t.sunsetMinute!!.toDouble(), 3.0)
    }

    @Test fun sunTimesMatchNoaaOnSummerSolstice() = assertSunTimesNear(LocalDate.of(2026, 6, 21), 292.88, 1232.05)

    @Test fun sunTimesMatchNoaaOnWinterSolstice() = assertSunTimesNear(LocalDate.of(2026, 12, 21), 436.64, 960.76)

    @Test fun sunTimesMatchNoaaOn28Oct() = assertSunTimesNear(LocalDate.of(2026, 10, 28), 373.83, 994.34)
}
