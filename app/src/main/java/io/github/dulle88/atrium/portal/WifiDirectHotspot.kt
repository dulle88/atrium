package io.github.dulle88.atrium.portal

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Name and password of the network the TV is broadcasting. */
data class HotspotCredentials(val ssid: String, val passphrase: String) {
    /** Payload phone cameras understand: scanning it offers to join the network. */
    val qrPayload: String get() = "WIFI:T:WPA;S:${esc(ssid)};P:${esc(passphrase)};;"
    private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:")
}

/**
 * The TV as its own Wi-Fi network, using a Wi-Fi Direct group (Android 9 has no public hotspot API for apps).
 * Phones see it as an ordinary "DIRECT-xx-..." WPA2 network. SSID and passphrase are chosen by the system.
 * While the group is up the TV's normal Wi-Fi connection may drop.
 */
class WifiDirectHotspot(context: Context) {
    private val appContext = context.applicationContext
    private val manager = context.applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var lastStartMs = -20_000L

    private val _credentials = MutableStateFlow<HotspotCredentials?>(null)
    val credentials: StateFlow<HotspotCredentials?> = _credentials.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        val m = manager ?: return fail("Wi-Fi Direct not available")
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastStartMs < 20_000) return // a previous attempt is still settling
        lastStartMs = now
        val ch = channel ?: m.initialize(context.applicationContext, Looper.getMainLooper(), null).also { channel = it }
        _error.value = null
        // Clear any stale group first, otherwise createGroup fails with BUSY.
        m.removeGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = create(m, ch)
            override fun onFailure(reason: Int) = create(m, ch)
        })
    }

    @SuppressLint("MissingPermission")
    private fun create(m: WifiP2pManager, ch: WifiP2pManager.Channel) {
        val listener = object : WifiP2pManager.ActionListener {
            override fun onSuccess() = readGroup(m, ch, attempts = 10)
            override fun onFailure(reason: Int) = fail("createGroup failed, reason $reason")
        }
        val config = fixedConfig()
        if (config != null) m.createGroup(ch, config, listener) else m.createGroup(ch, listener)
    }

    /**
     * Android 10+ lets us choose the network name and password, and the band: the 2.4 GHz band is used because on the
     * Mortal T1 a group on a different 5 GHz channel than the box's own Wi-Fi was created but never transmitted.
     * The name and password are derived from this box's id, so they are stable and differ per box.
     */
    @android.annotation.TargetApi(29)
    private fun fixedConfig(): WifiP2pConfig? {
        if (android.os.Build.VERSION.SDK_INT < 29) return null
        val id = android.provider.Settings.Secure.getString(appContext.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: return null
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest("atrium:$id".toByteArray())
        val alphabet = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val pass = (0 until 10).map { alphabet[(digest[it].toInt() and 0xFF) % alphabet.length] }.joinToString("")
        val tag = digest.take(2).joinToString("") { "%02x".format(it) }.take(3)
        return WifiP2pConfig.Builder()
            .setNetworkName("DIRECT-AT-Atrium-$tag")
            .setPassphrase(pass)
            .setGroupOperatingBand(WifiP2pConfig.GROUP_OWNER_BAND_2GHZ)
            .enablePersistentMode(true)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun readGroup(m: WifiP2pManager, ch: WifiP2pManager.Channel, attempts: Int) {
        m.requestGroupInfo(ch) { group: WifiP2pGroup? ->
            val pass = group?.passphrase
            if (group != null && !pass.isNullOrEmpty()) {
                Log.i(TAG, "group up: ${group.networkName} owner=${group.isGroupOwner}")
                _credentials.value = HotspotCredentials(group.networkName, pass)
            } else if (attempts > 0) {
                android.os.Handler(Looper.getMainLooper()).postDelayed({ readGroup(m, ch, attempts - 1) }, 500)
            } else fail("group created but no passphrase")
        }
    }

    /** Forgets the credentials when the system has torn the group down (Wi-Fi changes, system hotspot on), so it is rebuilt. */
    fun verify() {
        val m = manager ?: return
        val ch = channel ?: return
        if (_credentials.value == null) return
        try {
            m.requestGroupInfo(ch) { group -> if (group == null) _credentials.value = null }
        } catch (_: SecurityException) { }
    }

    fun stop() {
        val m = manager ?: return
        val ch = channel ?: return
        m.removeGroup(ch, null)
        _credentials.value = null
    }

    private fun fail(msg: String) { Log.w(TAG, msg); _error.value = msg }

    companion object {
        private const val TAG = "WifiDirectHotspot"
        /** Address of the group owner; Android fixes it, so the portal is always reachable here on the TV's network. */
        const val GROUP_OWNER_IP = "192.168.49.1"
    }
}
