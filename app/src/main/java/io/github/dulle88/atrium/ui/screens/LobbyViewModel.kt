package io.github.dulle88.atrium.ui.screens

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.dulle88.atrium.LobbyApplication
import io.github.dulle88.atrium.data.db.AppDatabase
import io.github.dulle88.atrium.data.db.MediaItem
import io.github.dulle88.atrium.data.db.RoomKvStore
import io.github.dulle88.atrium.data.repository.MediaRepository
import io.github.dulle88.atrium.portal.AndroidPortalBackend
import io.github.dulle88.atrium.portal.NetworkAddress
import io.github.dulle88.atrium.portal.Notice
import io.github.dulle88.atrium.portal.PhoneContact
import io.github.dulle88.atrium.portal.PhoneContacts
import io.github.dulle88.atrium.portal.PortalInfo
import io.github.dulle88.atrium.portal.PortalServer
import io.github.dulle88.atrium.portal.PortalState
import io.github.dulle88.atrium.portal.DefaultSlides
import io.github.dulle88.atrium.portal.PortalStore
import io.github.dulle88.atrium.portal.WifiDirectHotspot
import io.github.dulle88.atrium.util.AppClock
import io.github.dulle88.atrium.util.UsbMediaScanner
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LobbyViewModel(application: Application) : AndroidViewModel(application) {
    private val clock = (application as LobbyApplication).appClock
    private val mediaRepository = MediaRepository(application)
    private val portalStore = PortalStore(RoomKvStore(AppDatabase.getInstance(application).kvDao()))
    private val backend = AndroidPortalBackend(application, mediaRepository, portalStore, clock)
    val hotspot = WifiDirectHotspot(application)
    @Volatile private var server: PortalServer? = null
    private var lastStartAttemptMs: Long? = null

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val _usbMediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val usbMediaItems: StateFlow<List<MediaItem>> = _usbMediaItems.asStateFlow()

    private val _nowMs = MutableStateFlow(clock.now())
    val nowMs: StateFlow<Long> = _nowMs.asStateFlow()

    /** Published notices (active and scheduled); the screen picks the active ones each second. */
    private val _notices = MutableStateFlow<List<Notice>>(emptyList())
    val notices: StateFlow<List<Notice>> = _notices.asStateFlow()

    private val _slideshowOrder = MutableStateFlow<List<String>>(emptyList())
    val slideshowOrder: StateFlow<List<String>> = _slideshowOrder.asStateFlow()

    private val _slideshowSeconds = MutableStateFlow(PortalStore.DEFAULT_SECONDS)
    val slideshowSeconds: StateFlow<Int> = _slideshowSeconds.asStateFlow()

    private val _phones = MutableStateFlow(PhoneContacts.DEFAULTS)
    val phones: StateFlow<List<PhoneContact>> = _phones.asStateFlow()

    private val _portalState = MutableStateFlow<PortalState>(PortalState.NoNetwork)
    val portalState: StateFlow<PortalState> = _portalState.asStateFlow()

    private val _showPortalInfo = MutableStateFlow(false)
    val showPortalInfo: StateFlow<Boolean> = _showPortalInfo.asStateFlow()
    private var hideJob: kotlinx.coroutines.Job? = null

    // USB mount/unmount receiver (unchanged behaviour)
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action ?: return) {
                Intent.ACTION_MEDIA_MOUNTED -> scanUsb()
                Intent.ACTION_MEDIA_REMOVED,
                Intent.ACTION_MEDIA_UNMOUNTED,
                Intent.ACTION_MEDIA_EJECT -> _usbMediaItems.value = emptyList()
            }
        }
    }

    init {
        val usbFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addDataScheme("file")
        }
        application.registerReceiver(usbReceiver, usbFilter)
        scanUsb()

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                DefaultSlides.seedIfFirstRun(
                    application, RoomKvStore(AppDatabase.getInstance(application).kvDao()),
                    mediaRepository, portalStore.slideshowSeconds()
                )
            }
            mediaRepository.mediaItemsFlow().collect { _mediaItems.value = it }
        }

        // One-second tick: time, clock-jump check, notice expiry, slideshow settings, phones, last-known-time persistence,
        // portal server health and address. One failed iteration must not stop the loop.
        viewModelScope.launch {
            var tick = 0
            while (true) {
                try {
                    clock.checkForDeviceClockJump()
                    _nowMs.value = clock.now()
                    _notices.value = backend.purgeNotices()
                    _slideshowOrder.value = portalStore.slideshowOrder()
                    _slideshowSeconds.value = portalStore.slideshowSeconds()
                    _phones.value = portalStore.phones()
                    if (tick % 60 == 0) clock.persistLastKnown()
                    if (tick % 10 == 5) hotspot.verify()
                    if (hotspot.credentials.value == null && tick % 30 == 0) hotspot.start(application)
                    val restarted = ensureServerRunning()
                    if (restarted || tick % 5 == 0) refreshPortalInfo()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "tick failed", e)
                }
                tick++
                delay(1000)
            }
        }
    }

    /** Starts the portal server if it is not running, at most once every 30 seconds. Returns true if it started. */
    private suspend fun ensureServerRunning(): Boolean {
        if (server?.isAlive == true) return false
        val now = SystemClock.elapsedRealtime()
        val last = lastStartAttemptMs
        if (last != null && now - last < SERVER_RETRY_MS) return false
        lastStartAttemptMs = now
        val app = getApplication<Application>()
        val started = withContext(Dispatchers.IO) {
            server?.stop()
            PortalServer.startOnFreePort(backend) {
                app.assets.open("portal/index.html").bufferedReader().use { it.readText() }
                    .replace("{{TIME_ZONE}}", AppClock.ZONE.id)
            }
        }
        server = started
        if (started == null) Log.w(TAG, "portal server could not start on any port")
        return started != null
    }

    private suspend fun refreshPortalInfo() {
        val ip = withContext(Dispatchers.IO) { NetworkAddress.localIpv4() }
        val apIp = withContext(Dispatchers.IO) { NetworkAddress.softApIpv4(getApplication()) }
        val running = server?.takeIf { it.isAlive }
        val wifi = hotspot.credentials.value
        // System hotspot wins: Wi-Fi Direct cannot run beside it. Then Wi-Fi Direct's fixed address, then whatever the LAN gave.
        val host = apIp ?: if (wifi != null) WifiDirectHotspot.GROUP_OWNER_IP else ip
        _portalState.value = when {
            host == null -> PortalState.NoNetwork
            running == null -> PortalState.NotRunning
            else -> PortalState.Running(
                PortalInfo("http://$host:${running.listeningPort}", portalStore.pin(), if (apIp == null) wifi else null, onHotspot = apIp != null)
            )
        }
    }

    fun hidePortalInfo() {
        hideJob?.cancel()
        _showPortalInfo.value = false
    }

    fun togglePortalInfo() {
        hideJob?.cancel()
        val show = !_showPortalInfo.value
        _showPortalInfo.value = show
    }

    override fun onCleared() {
        super.onCleared()
        server?.stop()
        try {
            getApplication<Application>().unregisterReceiver(usbReceiver)
        } catch (_: Exception) { }
    }

    private companion object {
        const val TAG = "LobbyViewModel"
        const val SERVER_RETRY_MS = 30_000L
    }

    private fun scanUsb() {
        viewModelScope.launch(Dispatchers.IO) {
            _usbMediaItems.value = UsbMediaScanner.scanForMedia(getApplication())
        }
    }
}
