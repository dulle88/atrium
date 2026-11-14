package io.github.dulle88.atrium.util

import io.github.dulle88.atrium.FakeKvStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class AppClockTest {
    @Test fun setTimeShiftsNowAndFollowsDeviceTime() = runBlocking {
        var device = 1_000_000L
        val clock = AppClock(FakeKvStore()) { device }
        clock.load()
        clock.setTime(5_000_000L)
        assertEquals(5_000_000L, clock.now())
        device += 1_000
        assertEquals(5_001_000L, clock.now())
    }

    @Test fun correctionSurvivesRestart() = runBlocking {
        val store = FakeKvStore()
        val first = AppClock(store) { 1_000_000L }
        first.load()
        first.setTime(5_000_000L)
        val second = AppClock(store) { 1_500_000L }
        second.load()
        assertEquals(5_500_000L, second.now())
    }

    @Test fun resetDeviceClockContinuesFromLastKnownTime() = runBlocking {
        val store = FakeKvStore()
        var device = 1_000_000L
        val before = AppClock(store) { device }
        before.load()
        before.setTime(5_000_000L)
        device = 1_600_000L
        before.persistLastKnown() // last known = 5_600_000
        val afterReboot = AppClock(store) { 0L } // box lost its time
        afterReboot.load()
        assertEquals(5_600_000L, afterReboot.now())
    }

    @Test fun settingEarlierTimeIsNotTreatedAsReset() = runBlocking {
        val store = FakeKvStore()
        val clock = AppClock(store) { 10_000_000L }
        clock.load()
        clock.setTime(9_000_000L)
        val restarted = AppClock(store) { 10_000_500L }
        restarted.load()
        assertEquals(9_000_500L, restarted.now())
    }

    @Test fun zoneIsBelgrade() = assertEquals(ZoneId.of("Europe/Belgrade"), AppClock.ZONE)

    private val year = 365L * 86_400_000L

    @Test fun fallbackOffsetIsClearedWhenNtpCorrectsDeviceClock() = runBlocking {
        val store = FakeKvStore()
        val real = 1_800_000_000_000L
        store.map["clock_last_known_ms"] = real.toString()
        var device = real - 11 * year // box booted at its factory date
        var elapsed = 5_000L
        val clock = AppClock(store, { elapsed }) { device }
        clock.load()
        assertEquals(real, clock.now())
        assertEquals("fallback", store.map["clock_offset_source"])
        clock.checkForDeviceClockJump() // baseline
        elapsed += 10_000
        device = real + 10_000 // NTP sets the real time
        clock.checkForDeviceClockJump()
        assertEquals(real + 10_000, clock.now())
        assertEquals("0", store.map["clock_offset_ms"])
        assertEquals("none", store.map["clock_offset_source"])
    }

    @Test fun userSetTimeStaysContinuousAcrossDeviceClockJump() = runBlocking {
        val store = FakeKvStore()
        var device = 1_000_000_000_000L
        var elapsed = 0L
        val clock = AppClock(store, { elapsed }) { device }
        clock.load()
        clock.setTime(1_800_000_000_000L)
        assertEquals("user", store.map["clock_offset_source"])
        clock.checkForDeviceClockJump()
        elapsed += 1_000
        device += 1_000 + year // device clock jumps forward a year
        clock.checkForDeviceClockJump()
        assertEquals(1_800_000_001_000L, clock.now())
        val restarted = AppClock(store, { elapsed }) { device }
        restarted.load()
        assertEquals(1_800_000_001_000L, restarted.now())
    }

    @Test fun noJumpLeavesOffsetUnchanged() = runBlocking {
        val store = FakeKvStore()
        var device = 1_000_000_000_000L
        var elapsed = 0L
        val clock = AppClock(store, { elapsed }) { device }
        clock.load()
        clock.setTime(1_800_000_000_000L)
        clock.checkForDeviceClockJump()
        elapsed += 60_000
        device += 60_000 + 90_000 // 90 s of drift is under the 2 min threshold
        clock.checkForDeviceClockJump()
        assertEquals(1_800_000_150_000L, clock.now())
    }

    @Test fun jumpWithNoCorrectionDoesNothing() = runBlocking {
        val store = FakeKvStore()
        var device = 1_800_000_000_000L
        var elapsed = 0L
        val clock = AppClock(store, { elapsed }) { device }
        clock.load()
        clock.checkForDeviceClockJump()
        elapsed += 1_000
        device += year
        clock.checkForDeviceClockJump()
        assertEquals(device, clock.now())
        assertEquals(null, store.map["clock_offset_ms"])
    }

    @Test fun setTimeWritesLastKnownBeforeOffset() = runBlocking {
        val order = mutableListOf<String>()
        val store = object : io.github.dulle88.atrium.data.db.KvStore {
            val map = mutableMapOf<String, String>()
            override suspend fun get(key: String) = map[key]
            override suspend fun put(key: String, value: String) { order += key; map[key] = value }
            override suspend fun delete(key: String) { map.remove(key) }
        }
        val clock = AppClock(store, { 0L }) { 1_000_000L }
        clock.load()
        clock.setTime(5_000_000L)
        assertTrue(order.toString(), order.indexOf("clock_last_known_ms") < order.indexOf("clock_offset_ms"))
    }
}
