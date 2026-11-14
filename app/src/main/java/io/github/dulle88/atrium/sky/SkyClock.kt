package io.github.dulle88.atrium.sky

import io.github.dulle88.atrium.BuildConfig
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class SolarPosition(
    val elevationDeg: Double,
    val azimuthDeg: Double,
    val hourAngleDeg: Double,
    val declinationRad: Double
)

data class SunTimes(val sunriseMinute: Int?, val sunsetMinute: Int?, val solarNoonMinute: Int)

data class SkyState(
    val sunElevation: Double,
    val sunAzimuth: Double,
    val sunHourAngle: Double,
    val moonElevation: Double,
    val moonAzimuth: Double,
    val moonElongationDeg: Double,
    val moonIllumination: Double,
    val dayFactor: Double,
    val nightFactor: Double
)

object SkyClock {
    const val LAT = BuildConfig.LATITUDE
    const val LON = BuildConfig.LONGITUDE
    private const val RAD = PI / 180.0
    private const val MS_PER_DAY = 86_400_000.0
    private const val OBLIQUITY = 23.439 * RAD
    private const val HORIZON_ELEVATION = -0.833

    fun smooth(a: Double, b: Double, x: Double): Double {
        val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    fun sunPosition(epochMs: Long): SolarPosition {
        val utc = Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC)
        val hr = utc.hour + utc.minute / 60.0
        val g = 2 * PI / 365 * (utc.dayOfYear - 1 + (hr - 12) / 24)
        val eq = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) -
            0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val utcMin = utc.hour * 60 + utc.minute + utc.second / 60.0
        var ha = ((utcMin + eq + 4 * LON) / 4 - 180) % 360
        if (ha < -180) ha += 360
        if (ha > 180) ha -= 360
        return SolarPosition(elevation(decl, ha), azimuth(decl, ha), ha, decl)
    }

    fun elevation(declRad: Double, hourAngleDeg: Double): Double {
        val h = hourAngleDeg * RAD
        val c = sin(LAT * RAD) * sin(declRad) + cos(LAT * RAD) * cos(declRad) * cos(h)
        return 90 - acos(c.coerceIn(-1.0, 1.0)) / RAD
    }

    /** Degrees from south, positive towards west. */
    fun azimuth(declRad: Double, hourAngleDeg: Double): Double {
        val h = hourAngleDeg * RAD
        return atan2(sin(h), cos(h) * sin(LAT * RAD) - tan(declRad) * cos(LAT * RAD)) / RAD
    }

    /** Fraction of the synodic month, 0 = new moon, 0.5 = full moon. */
    fun moonAge(epochMs: Long): Double {
        val jd = epochMs / MS_PER_DAY + 2440587.5
        val a = ((jd - 2451550.1) / 29.530588853) % 1.0
        return if (a < 0) a + 1 else a
    }

    fun sunTimes(date: LocalDate, zone: ZoneId): SunTimes {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        fun clockMinute(ms: Long): Int {
            val t = Instant.ofEpochMilli(ms).atZone(zone)
            return t.hour * 60 + t.minute
        }
        var rise: Int? = null
        var set: Int? = null
        var noon = 720
        var best = -99.0
        var prev: Double? = null
        var ms = start
        while (ms < end) {
            val e = sunPosition(ms).elevationDeg
            if (e > best) { best = e; noon = clockMinute(ms) }
            if (prev != null) {
                if (prev <= HORIZON_ELEVATION && e > HORIZON_ELEVATION && rise == null) rise = clockMinute(ms)
                if (prev > HORIZON_ELEVATION && e <= HORIZON_ELEVATION) set = clockMinute(ms)
            }
            prev = e
            ms += 60_000L
        }
        return SunTimes(rise, set, noon)
    }

    /** Sun's ecliptic longitude in degrees; n = days since J2000. */
    private fun eclipticLongitudeSun(n: Double): Double {
        val g = (357.528 + 0.9856003 * n) * RAD
        val l = 280.460 + 0.9856474 * n
        return l + 1.915 * sin(g) + 0.020 * sin(2 * g)
    }

    private fun rightAscensionDeg(lambdaDeg: Double): Double {
        val l = lambdaDeg * RAD
        return atan2(cos(OBLIQUITY) * sin(l), cos(l)) / RAD
    }

    private fun declinationRad(lambdaDeg: Double): Double =
        asin(sin(OBLIQUITY) * sin(lambdaDeg * RAD))

    fun skyState(epochMs: Long): SkyState {
        val sun = sunPosition(epochMs)
        val age = moonAge(epochMs)
        val elong = age * 360.0
        val n = epochMs / MS_PER_DAY + 2440587.5 - 2451545.0
        val lambdaSun = eclipticLongitudeSun(n)
        val lambdaMoon = lambdaSun + elong
        val moonDec = declinationRad(lambdaMoon)
        val raDiff = rightAscensionDeg(lambdaSun) - rightAscensionDeg(lambdaMoon)
        val moonHa = (sun.hourAngleDeg + raDiff + 540) % 360 - 180
        val s = sun.elevationDeg
        return SkyState(
            sunElevation = s,
            sunAzimuth = sun.azimuthDeg,
            sunHourAngle = sun.hourAngleDeg,
            moonElevation = elevation(moonDec, moonHa),
            moonAzimuth = azimuth(moonDec, moonHa),
            moonElongationDeg = elong,
            moonIllumination = (1 - cos(elong * RAD)) / 2,
            dayFactor = smooth(-6.0, 10.0, s),
            nightFactor = 1 - smooth(-18.0, -4.0, s)
        )
    }
}
