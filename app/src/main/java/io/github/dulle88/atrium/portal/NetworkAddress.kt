package io.github.dulle88.atrium.portal

import android.content.Context
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkAddress {
    /** The first site-local IPv4 address on an active, non-loopback interface (e.g. 172.20.10.4 on an iPhone hotspot). */
    fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    /**
     * The TV's own address when its system hotspot (Settings > Hotspot) is on, otherwise null. Android 9/10 give apps no way
     * to read the hotspot's name or password, and the Wi-Fi radio cannot be a client at the same time, so it is recognised by
     * the hidden `isWifiApEnabled` when reachable, or else by "not connected to any Wi-Fi but a Wi-Fi/AP interface has an
     * address". Wi-Fi Direct (p2p*) and Ethernet interfaces never count.
     */
    @Suppress("DEPRECATION")
    fun softApIpv4(context: Context): String? = runCatching {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val apFlag = runCatching { WifiManager::class.java.getMethod("isWifiApEnabled").invoke(wm) as? Boolean }.getOrNull()
        if (apFlag == false) return@runCatching null
        val info = wm.connectionInfo
        val clientConnected = info != null && info.networkId != -1 && info.supplicantState == SupplicantState.COMPLETED
        if (apFlag == null && clientConnected) return@runCatching null
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.name.startsWith("p2p") && !it.name.startsWith("eth") }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()
}
