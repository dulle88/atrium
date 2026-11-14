package io.github.dulle88.atrium.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.dulle88.atrium.data.db.MediaItem
import io.github.dulle88.atrium.portal.Notice
import io.github.dulle88.atrium.portal.PhoneContact
import io.github.dulle88.atrium.portal.PortalState
import io.github.dulle88.atrium.sky.SkyClock
import io.github.dulle88.atrium.sky.WindowRect
import io.github.dulle88.atrium.ui.components.ClockArea
import io.github.dulle88.atrium.ui.components.ClockCalendarArea
import io.github.dulle88.atrium.ui.components.NoticePlayer
import io.github.dulle88.atrium.ui.components.PortalInfoOverlay
import io.github.dulle88.atrium.ui.components.Slideshow
import io.github.dulle88.atrium.ui.components.SkyBackground
import io.github.dulle88.atrium.ui.components.SlideshowPlan
import io.github.dulle88.atrium.util.AppClock
import io.github.dulle88.atrium.util.HolidayCalendar
import java.time.Instant

/** TV overscan: keep panels inside the middle 90 % of the screen on each axis. */
private const val SAFE_AREA_FRACTION = 0.05f
private const val TOP_BAR_WEIGHT = 0.26f
private val GAP = 16.dp

@Composable
@Suppress("FunctionName")
fun LobbyScreen(
    mediaItems: List<MediaItem>,
    usbMediaItems: List<MediaItem>,
    nowMs: Long,
    notices: List<Notice>,
    slideshowOrder: List<String>,
    slideshowSeconds: Int,
    phones: List<PhoneContact>,
    portalState: PortalState,
    showPortalInfo: Boolean,
    onClockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val arranged = remember(mediaItems, slideshowOrder, slideshowSeconds) {
        SlideshowPlan.arrange(mediaItems, slideshowOrder, slideshowSeconds)
    }
    val displayItems = if (usbMediaItems.isNotEmpty()) usbMediaItems else arranged
    val date = Instant.ofEpochMilli(nowMs).atZone(AppClock.ZONE).toLocalDate()
    val greeting = HolidayCalendar.greetingFor(date)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val minuteMs = nowMs / 60_000 * 60_000
    val sky = remember(nowMs / 30_000) { SkyClock.skyState(nowMs) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color.Black)) {
        val padX = maxWidth * SAFE_AREA_FRACTION
        val padY = maxHeight * SAFE_AREA_FRACTION
        // The top bar as fractions of the screen: the sky window where the sun, moon and clouds show.
        val window = WindowRect(
            SAFE_AREA_FRACTION, SAFE_AREA_FRACTION, 1 - 2 * SAFE_AREA_FRACTION,
            (maxHeight - padY * 2 - GAP) * TOP_BAR_WEIGHT / maxHeight
        )

        // Full-bleed: the sky runs edge to edge, only the panels sit inside the safe area.
        // Minute resolution: the sky has nothing that moves per second, so it need not redraw per second.
        SkyBackground(
            state = sky, epochMs = minuteMs, window = window, modifier = Modifier.fillMaxSize()
        )

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = padX, vertical = padY)) {
            Box(modifier = Modifier.fillMaxWidth().weight(TOP_BAR_WEIGHT)) {
                ClockArea(nowMs = nowMs, greeting = greeting, onClick = onClockClick, focusRequester = focusRequester)
            }

            Spacer(modifier = Modifier.height(GAP))

            Row(modifier = Modifier.fillMaxWidth().weight(1f - TOP_BAR_WEIGHT)) {
                Box(modifier = Modifier.weight(0.74f).fillMaxHeight()) {
                    when (val content = GalleryResolver.resolve(notices, nowMs)) {
                        is GalleryContent.Notices -> NoticePlayer(notices = content.notices, photoSeconds = slideshowSeconds)
                        GalleryContent.Slideshow -> Slideshow(mediaItems = displayItems)
                    }
                }

                Spacer(modifier = Modifier.width(GAP))

                Box(modifier = Modifier.weight(0.26f).fillMaxHeight()) {
                    ClockCalendarArea(nowMs = minuteMs, phones = phones)
                }
            }
        }

        if (showPortalInfo) PortalInfoOverlay(portalState)
    }
}
