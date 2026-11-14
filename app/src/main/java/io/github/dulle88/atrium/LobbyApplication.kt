package io.github.dulle88.atrium

import android.app.Application
import io.github.dulle88.atrium.data.db.AppDatabase
import io.github.dulle88.atrium.data.db.RoomKvStore
import io.github.dulle88.atrium.util.AppClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class LobbyApplication : Application() {
    lateinit var appClock: AppClock
        private set

    override fun onCreate() {
        super.onCreate()
        appClock = AppClock(RoomKvStore(AppDatabase.getInstance(this).kvDao()))
        runBlocking(Dispatchers.IO) {
            appClock.load()
            appClock.checkForDeviceClockJump() // baseline, so an early NTP sync is caught by the first tick
        }
    }
}
