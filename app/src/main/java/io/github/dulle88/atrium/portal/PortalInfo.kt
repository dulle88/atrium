package io.github.dulle88.atrium.portal

/**
 * [wifi] is the TV's own Wi-Fi Direct network; when present, [url] is the fixed address on it. [onHotspot] means the TV's
 * system hotspot is on: [url] is its address, and no join QR is shown because the app cannot read the hotspot's name or password.
 */
data class PortalInfo(val url: String, val pin: String, val wifi: HotspotCredentials? = null, val onHotspot: Boolean = false)

/** What the portal overlay should show. */
sealed interface PortalState {
    data class Running(val info: PortalInfo) : PortalState
    /** No site-local IPv4 address: the TV is not on the hotspot. */
    data object NoNetwork : PortalState
    /** The TV has an address but the web server is down (it is retried every 30 seconds). */
    data object NotRunning : PortalState
}
