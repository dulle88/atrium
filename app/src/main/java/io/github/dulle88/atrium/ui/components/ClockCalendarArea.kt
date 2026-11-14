package io.github.dulle88.atrium.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.draw.alpha
import io.github.dulle88.atrium.portal.PhoneContact
import io.github.dulle88.atrium.portal.PhoneContacts
import io.github.dulle88.atrium.util.PhoneFormat
import io.github.dulle88.atrium.util.AppClock
import io.github.dulle88.atrium.util.HolidayCalendar
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/** How long the calendar shows before the phones, and each page of phones (at least [PHONES_MIN_MS] in all). */
private const val CALENDAR_MS = 30_000L
private const val PHONE_PAGE_MS = 12_000L
private const val PHONES_MIN_MS = 20_000L

@Composable
fun ClockCalendarArea(
    nowMs: Long,
    phones: List<PhoneContact>,
    modifier: Modifier = Modifier
) {
    // Rotation state: true = show calendar, false = show phone numbers
    var showCalendar by remember { mutableStateOf(true) }
    val pages = remember(phones) { PhoneContacts.pages(phones) }
    val pageCount by rememberUpdatedState(pages.size)

    // Calendar for 30 s, then the phones: 20 s for one page, 12 s per page when there are more.
    LaunchedEffect(Unit) {
        while (true) {
            if (showCalendar) {
                delay(CALENDAR_MS)
                showCalendar = false
            } else {
                delay(maxOf(PHONES_MIN_MS, PHONE_PAGE_MS * pageCount))
                showCalendar = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .glassPanel()
    ) {
        // With no contacts the phone panel is skipped and the calendar stays.
        Crossfade(
            targetState = showCalendar || pages.isEmpty(),
            animationSpec = tween(durationMillis = 500),
            label = "calendar_phone_rotation"
        ) { displayCalendar ->
            if (displayCalendar) {
                CalendarView(nowMs, Modifier.fillMaxSize())
            } else {
                PhoneNumbersView(pages, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** The "Važni telefoni" panel. With more than one page it crossfades from page to page, with dots showing which. */
@Composable
private fun PhoneNumbersView(pages: List<List<PhoneContact>>, modifier: Modifier = Modifier) {
    if (pages.isEmpty()) return
    var page by remember(pages) { mutableIntStateOf(0) }
    LaunchedEffect(pages) {
        while (pages.size > 1) {
            delay(PHONE_PAGE_MS)
            page = (page + 1) % pages.size
        }
    }
    val fullest = pages.maxBy { it.size }

    // Laid out for a 240x330 dp panel, scaled to the real one so small screens do not clip it.
    // A full page of four can be taller than the panel; ScaleDownToFit then shrinks the whole panel.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val s = minOf(maxWidth / 240.dp, maxHeight / 330.dp).coerceIn(0.6f, 1.3f)
        ScaleDownToFit(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp * s),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Važni telefoni",
                    fontSize = 20.sp * s,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(bottom = 20.dp * s)
                )
                // The fullest page, invisible, holds the height so the title, the dots and the scale stay
                // put while a shorter page shows.
                Box(Modifier.fillMaxWidth()) {
                    Column(Modifier.alpha(0f)) { fullest.forEach { PhoneCard(it, s) } }
                    Crossfade(targetState = page, animationSpec = tween(durationMillis = 600), label = "phone_page") { p ->
                        Column { pages.getOrNull(p)?.forEach { PhoneCard(it, s) } }
                    }
                }
                if (pages.size > 1) {
                    Row(
                        modifier = Modifier.padding(top = 10.dp * s),
                        horizontalArrangement = Arrangement.spacedBy(8.dp * s)
                    ) {
                        repeat(pages.size) { i ->
                            Box(
                                Modifier
                                    .size(7.dp * s)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = if (i == page) 0.8f else 0.25f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneCard(contact: PhoneContact, s: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp * s)
    ) {
        // Card background with glassmorphism effect
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Color.White.copy(alpha = 0.05f),
                    RoundedCornerShape(12.dp)
                )
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(12.dp * s)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp * s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Phone icon - thin outline matching Lucide design
                Box(
                    modifier = Modifier
                        .size(32.dp * s)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF3B82F6).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Phone icon - simple rounded rectangle with arc (smartphone style)
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.size(20.dp * s)
                    ) {
                        val strokeWidth = 2f
                        val color = androidx.compose.ui.graphics.Color(0xFF60A5FA)
                        val cornerRadius = size.width * 0.15f

                        // Draw rounded rectangle (phone body)
                        drawRoundRect(
                            color = color,
                            topLeft = androidx.compose.ui.geometry.Offset(
                                size.width * 0.2f,
                                size.height * 0.1f
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                size.width * 0.6f,
                                size.height * 0.8f
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidth,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )

                        // Draw small arc at bottom (speaker/microphone indicator)
                        val arcPath = androidx.compose.ui.graphics.Path().apply {
                            addArc(
                                androidx.compose.ui.geometry.Rect(
                                    left = size.width * 0.35f,
                                    top = size.height * 0.72f,
                                    right = size.width * 0.65f,
                                    bottom = size.height * 0.82f
                                ),
                                startAngleDegrees = 0f,
                                sweepAngleDegrees = 180f
                            )
                        }
                        drawPath(
                            path = arcPath,
                            color = color,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidth,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )
                    }
                }

                // Contact info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = contact.name,
                        fontSize = 14.sp * s,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = PhoneFormat.serbian(contact.number),
                        fontSize = 13.sp * s,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.6f),
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Centres [content] measured at its natural height and, if that is taller than this layout, scales it
 * down uniformly (about its centre) until it fits. Content that fits is placed exactly as it is.
 */
@Composable
private fun ScaleDownToFit(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val placeable = measurables.single()
            .measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity))
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val scale = if (placeable.height > height) height / placeable.height.toFloat() else 1f
        layout(width, height) {
            placeable.placeWithLayer((width - placeable.width) / 2, (height - placeable.height) / 2) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

private fun calendarFor(nowMs: Long): Calendar = Calendar.getInstance(TimeZone.getTimeZone(AppClock.ZONE.id)).apply { timeInMillis = nowMs }

@Composable
private fun CalendarView(nowMs: Long, modifier: Modifier = Modifier) {
    val currentTime = remember(nowMs / 60_000) { calendarFor(nowMs) }
    // Custom Latin Serbian month/year formatting
    val monthYearFormat = remember {
        object {
            fun format(calendar: Calendar): String {
                val monthNames = arrayOf("Januar", "Februar", "Mart", "April", "Maj", "Jun", "Jul", "Avgust", "Septembar", "Oktobar", "Novembar", "Decembar")
                val month = calendar.get(Calendar.MONTH)
                val year = calendar.get(Calendar.YEAR)
                return "${monthNames[month]} $year"
            }
        }
    }
    
    // Six weeks need about 260x365 dp at scale 1; scale from the real panel so small screens do not clip.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val s = minOf(maxWidth / 260.dp, maxHeight / 365.dp).coerceIn(0.6f, 1.3f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp * s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Month and Year header
            Text(
                text = monthYearFormat.format(currentTime),
                fontSize = 24.sp * s,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp * s)
            )

            // Calendar grid
            MonthCalendarGrid(currentTime, s)
        }
    }
}


@Composable
private fun MonthCalendarGrid(calendar: Calendar, s: Float) {
    val today = calendar.get(Calendar.DAY_OF_MONTH)
    val currentMonth = calendar.get(Calendar.MONTH)
    val currentYear = calendar.get(Calendar.YEAR)
    
    // Get first day of month
    val firstDay = Calendar.getInstance(calendar.timeZone).apply {
        set(currentYear, currentMonth, 1)
    }
    val firstDayOfWeek = firstDay.get(Calendar.DAY_OF_WEEK)
    val startOffset = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2
    
    // Get days in month
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Day headers - single letter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("P", "U", "S", "Č", "P", "S", "N").forEach { day ->
                Text(
                    text = day,
                    fontSize = 14.sp * s,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp * s))
        
        // Calendar days grid
        var dayCounter = 1
        for (week in 0..5) {
            if (dayCounter > daysInMonth) break
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (dayOfWeek in 0..6) {
                    val cellIndex = week * 7 + dayOfWeek
                    
                    if (cellIndex < startOffset || dayCounter > daysInMonth) {
                        // Empty cell
                        Box(modifier = Modifier.weight(1f).height(40.dp * s))
                    } else {
                        // Day cell
                        val isToday = dayCounter == today
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp * s)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isToday) {
                                // Today - emerald green gradient with glow
                                Box(
                                    modifier = Modifier
                                        .size(40.dp * s)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF34D399), // emerald-400
                                                    Color(0xFF14B8A6)  // teal-500
                                                )
                                            ),
                                            RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayCounter.toString(),
                                        fontSize = 14.sp * s,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            } else {
                                // Regular day
                                val isHoliday = HolidayCalendar.greetingFor(java.time.LocalDate.of(currentYear, currentMonth + 1, dayCounter)) != null
                                Text(
                                    text = dayCounter.toString(),
                                    fontSize = 14.sp * s,
                                    fontWeight = if (isHoliday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isHoliday) Color(0xFFFFD9A8) else Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                        dayCounter++
                    }
                }
            }
        }
    }
}
