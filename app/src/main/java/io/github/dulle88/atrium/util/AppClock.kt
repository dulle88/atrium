package io.github.dulle88.atrium.util

import io.github.dulle88.atrium.BuildConfig
import io.github.dulle88.atrium.data.db.KvStore
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * The only source of time for display and logic. Device time plus a persisted correction.
 *
 * The correction remembers where it came from ([SOURCE_USER] for a time set from the portal,
 * [SOURCE_FALLBACK] for the reboot fallback) so a later jump of the device clock, such as an
 * NTP sync, can be handled: a fallback correction is dropped once the device clock is real again,
 * and a user correction is adjusted so app time stays continuous.
 */
class AppClock(
    private val store: KvStore,
    /** Monotonic milliseconds since boot. Unit tests must inject this. */
    private val elapsedNow: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val deviceNow: () -> Long = System::currentTimeMillis
) {
    @Volatile private var offsetMs = 0L
    @Volatile private var lastKnownMs = 0L
    @Volatile private var source = SOURCE_NONE

    private val lock = Mutex()
    private var lastDeviceMs = 0L
    private var lastElapsedMs = 0L
    private var hasBaseline = false

    suspend fun load() {
        offsetMs = store.get(KEY_OFFSET)?.toLongOrNull() ?: 0L
        lastKnownMs = store.get(KEY_LAST_KNOWN)?.toLongOrNull() ?: 0L
        source = store.get(KEY_SOURCE) ?: SOURCE_NONE
        if (deviceNow() + offsetMs < lastKnownMs) {
            // The box lost its time (no battery-backed clock). Continue from the last known time.
            offsetMs = lastKnownMs - deviceNow()
            source = SOURCE_FALLBACK
            store.put(KEY_SOURCE, source)
            store.put(KEY_OFFSET, offsetMs.toString())
        }
    }

    fun now(): Long = deviceNow() + offsetMs

    fun nowZoned(): ZonedDateTime = Instant.ofEpochMilli(now()).atZone(ZONE)

    suspend fun setTime(epochMs: Long) = lock.withLock {
        offsetMs = epochMs - deviceNow()
        lastKnownMs = epochMs
        source = SOURCE_USER
        store.put(KEY_LAST_KNOWN, lastKnownMs.toString())
        store.put(KEY_OFFSET, offsetMs.toString())
        store.put(KEY_SOURCE, source)
    }

    suspend fun persistLastKnown() {
        lastKnownMs = now()
        store.put(KEY_LAST_KNOWN, lastKnownMs.toString())
    }

    /**
     * Compares how far the device clock moved with how far the monotonic clock moved since the
     * previous call. Call it regularly (the UI tick does, once a second). The first call only
     * records a baseline.
     */
    suspend fun checkForDeviceClockJump(): Unit = lock.withLock {
        val device = deviceNow()
        val elapsed = elapsedNow()
        if (!hasBaseline) {
            lastDeviceMs = device
            lastElapsedMs = elapsed
            hasBaseline = true
            return@withLock
        }
        val jump = (device - lastDeviceMs) - (elapsed - lastElapsedMs)
        lastDeviceMs = device
        lastElapsedMs = elapsed
        if (abs(jump) <= JUMP_THRESHOLD_MS) return@withLock
        when (source) {
            SOURCE_FALLBACK -> if (device >= lastKnownMs) {
                // The device clock is real again (for example NTP). Drop the reboot fallback.
                offsetMs = 0L
                source = SOURCE_NONE
                store.put(KEY_OFFSET, "0")
                store.put(KEY_SOURCE, source)
            } else {
                // Still behind the last known time: keep app time continuous.
                offsetMs -= jump
                store.put(KEY_OFFSET, offsetMs.toString())
            }
            SOURCE_USER -> {
                offsetMs -= jump
                store.put(KEY_OFFSET, offsetMs.toString())
            }
        }
    }

    companion object {
        val ZONE: ZoneId = ZoneId.of(BuildConfig.TIME_ZONE)
        private const val KEY_OFFSET = "clock_offset_ms"
        private const val KEY_LAST_KNOWN = "clock_last_known_ms"
        private const val KEY_SOURCE = "clock_offset_source"
        private const val SOURCE_NONE = "none"
        private const val SOURCE_FALLBACK = "fallback"
        private const val SOURCE_USER = "user"
        private const val JUMP_THRESHOLD_MS = 2 * 60_000L
    }
}
