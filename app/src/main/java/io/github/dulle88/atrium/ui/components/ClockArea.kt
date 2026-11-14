package io.github.dulle88.atrium.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.dulle88.atrium.util.AppClock
import io.github.dulle88.atrium.util.Greeting
import io.github.dulle88.atrium.util.TextBalance
import kotlinx.coroutines.delay
import java.time.Instant

private val DAY_NAMES = arrayOf("Nedelja", "Ponedeljak", "Utorak", "Sreda", "Četvrtak", "Petak", "Subota")
private val MONTH_NAMES = arrayOf("januar", "februar", "mart", "april", "maj", "jun", "jul", "avgust", "septembar", "oktobar", "novembar", "decembar")

/** Greetings longer than this use the smaller sizes (the mockup's `.long`). */
private const val LONG_GREETING = 30

/** How long the focus outline stays after a remote key press. */
private const val OUTLINE_MS = 5_000L

/**
 * The top bar: a transparent window onto the sky. Clock on the left, then the day and date, or on
 * a holiday the greeting in their place. The right ~12 % stays clear for the sun or moon. Text gets
 * a tight dark outline-shadow, a soft drop shadow and a soft dark glow behind each group, so white
 * text stays readable over white clouds.
 *
 * Sizes follow the mockup in units of `cq` (1 % of the screen width, i.e. the bar width / 90).
 */
@Composable
fun ClockArea(
    nowMs: Long,
    greeting: Greeting?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val t = Instant.ofEpochMilli(nowMs).atZone(AppClock.ZONE)
    val hours = "%02d".format(t.hour)
    val minutes = "%02d".format(t.minute)
    val dayName = DAY_NAMES[t.dayOfWeek.value % 7]
    val dateString = "${t.dayOfMonth}. ${MONTH_NAMES[t.monthValue - 1]} ${t.year}"
    val colonVisible = t.second % 2 == 0

    var focused by remember { mutableStateOf(false) }
    // The clock is the only focusable element, so it always has focus; like CSS :focus-visible, the
    // outline shows only for a few seconds after a remote key press, keeping the window clear otherwise.
    var keyPresses by remember { mutableIntStateOf(0) }
    var outline by remember { mutableStateOf(false) }
    LaunchedEffect(keyPresses) {
        if (keyPresses == 0) return@LaunchedEffect
        outline = true
        delay(OUTLINE_MS)
        outline = false
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(if (focused && outline) Modifier.border(1.5.dp, Color.White.copy(alpha = 0.5f), FocusShape) else Modifier)
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown) keyPresses++; false }
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick, indication = null, interactionSource = remember { MutableInteractionSource() }),
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        // Height caps the digits so density and font scale cannot clip them; everything else scales with them.
        val cq: Dp = minOf(maxWidth / 90f, maxHeight * 0.82f / 10.4f)
        val cqPx = with(density) { cq.toPx() }
        fun sp(units: Float) = with(density) { (cq * units).toSp() }
        val shadows = TextShadows(cqPx)

        val digitSize = sp(10.4f)
        val digitStyle = TextStyle(
            fontSize = digitSize, lineHeight = digitSize, fontWeight = FontWeight.Bold,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(start = cq * 3.4f, end = cq * 12f),
            horizontalArrangement = Arrangement.spacedBy(cq * 3.4f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.textGlow(cqPx), verticalAlignment = Alignment.CenterVertically) {
                var glyphs by remember { mutableStateOf<GlyphBox?>(null) }
                ShadowedText(hours, digitStyle, Color.White, shadows.digits, onTextLayout = {
                    // Digits stand on the baseline and are as tall as Roboto's capitals (0.711 em).
                    val base = it.firstBaseline
                    val g = GlyphBox(it.size.height, base - ROBOTO_CAP_HEIGHT * cqPx * 10.4f, base)
                    if (g != glyphs) glyphs = g
                })
                ColonDots(glyphs, cqPx * 10.4f, if (colonVisible) 1f else 0.55f, shadows.digits)
                ShadowedText(minutes, digitStyle, Color.White, shadows.digits)
            }
            if (greeting == null) {
                Column(Modifier.textGlow(cqPx)) {
                    val day = sp(3.3f)
                    val date = sp(2.5f)
                    ShadowedText(dayName, TextStyle(fontSize = day, lineHeight = day * 1.15f, fontWeight = FontWeight.Medium), Color.White, shadows.text)
                    ShadowedText(dateString, TextStyle(fontSize = date, lineHeight = date * 1.2f, fontWeight = FontWeight.Medium), Color.White, shadows.text)
                }
            } else {
                // Serbian large and bold (at most two balanced lines), English below; long greetings start smaller
                // and anything that still overflows shrinks a step at a time.
                val long = greeting.sr.length > LONG_GREETING
                var shrink by remember(greeting) { mutableFloatStateOf(1f) }
                var balanced by remember(greeting) { mutableStateOf(false) }
                val onLayout: (TextLayoutResult) -> Unit = { if (it.hasVisualOverflow && shrink > 0.5f) shrink *= 0.92f }
                val onSerbianLayout: (TextLayoutResult) -> Unit = {
                    if (!balanced && it.lineCount > 1) balanced = true else onLayout(it)
                }
                val sr = sp((if (long) 2.8f else 3.6f) * shrink)
                val en = sp((if (long) 1.7f else 2.1f) * shrink)
                Column(Modifier.weight(1f, fill = false).widthIn(max = cq * 46f).textGlow(cqPx)) {
                    ShadowedText(
                        if (balanced) TextBalance.twoLines(greeting.sr) else greeting.sr,
                        TextStyle(fontSize = sr, lineHeight = sr * 1.1f, fontWeight = FontWeight.Bold),
                        Color.White, shadows.text, maxLines = 2, onTextLayout = onSerbianLayout
                    )
                    ShadowedText(
                        greeting.en, TextStyle(fontSize = en, lineHeight = en * 1.25f),
                        Color.White, shadows.text, Modifier.padding(top = cq * 0.4f), maxLines = 1, onTextLayout = onLayout
                    )
                }
            }
        }
    }
}

private val FocusShape = RoundedCornerShape(16.dp)

/** Navy of the mockup's text shadows and glow, rgb(10,24,52) / rgb(8,18,44). */
private val ShadowNavy = Color(10, 24, 52)
private val GlowNavy = Color(8, 18, 44)

/** Each text is drawn twice: once with a soft drop shadow underneath, once with a tight dark outline on top. */
private class TextShadows(cq: Float) {
    val digits = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, cq * 0.25f), blurRadius = cq * 1.4f) to
        Shadow(ShadowNavy.copy(alpha = 0.8f), Offset.Zero, blurRadius = cq * 0.35f)
    val text = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, cq * 0.15f), blurRadius = cq * 0.9f) to
        Shadow(ShadowNavy.copy(alpha = 0.85f), Offset.Zero, blurRadius = cq * 0.3f)
}

@Composable
private fun ShadowedText(
    text: String,
    style: TextStyle,
    color: Color,
    shadows: Pair<Shadow, Shadow>,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {}
) {
    val softWrap = maxLines > 1
    Box(modifier) {
        Text(text, style = style.copy(shadow = shadows.first), color = color, maxLines = maxLines, softWrap = softWrap)
        Text(text, style = style.copy(shadow = shadows.second), color = color, maxLines = maxLines, softWrap = softWrap, onTextLayout = onTextLayout)
    }
}

/**
 * A soft dark elliptical glow behind a text group, reaching 2.4 cq past its sides and 1.4 cq past
 * its top and bottom: rgba(8,18,44,.42) at the centre, .18 at 70 %, clear at the edge.
 */
private fun Modifier.textGlow(cq: Float): Modifier = drawWithCache {
    val rx = size.width / 2 + cq * 2.4f
    val ry = size.height / 2 + cq * 1.4f
    val c = Offset(size.width / 2, size.height / 2)
    val brush = Brush.radialGradient(
        0f to GlowNavy.copy(alpha = 0.42f), 0.7f to GlowNavy.copy(alpha = 0.18f), 1f to GlowNavy.copy(alpha = 0f),
        center = c, radius = ry
    )
    onDrawBehind { scale(rx / ry, 1f, c) { drawCircle(brush, ry, c) } }
}

private const val ROBOTO_CAP_HEIGHT = 0.711f

/** Where the digit glyphs sit inside their text box: box height, glyph top and bottom, in px. */
private data class GlyphBox(val height: Int, val top: Float, val bottom: Float)

/**
 * The clock's colon as two round dots, centred on the digits' glyphs (not on the text baseline like a
 * ":" character), with the same shadows as the digits. [fontPx] is the digit font size in px.
 */
@Composable
private fun ColonDots(glyphs: GlyphBox?, fontPx: Float, alpha: Float, shadows: Pair<Shadow, Shadow>) {
    val density = LocalDensity.current
    val width = with(density) { (fontPx * 0.32f).toDp() }
    if (glyphs == null) {
        Spacer(Modifier.width(width))
        return
    }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    Canvas(Modifier.width(width).height(with(density) { glyphs.height.toDp() })) {
        val cx = size.width / 2
        val cy = (glyphs.top + glyphs.bottom) / 2
        val dy = (glyphs.bottom - glyphs.top) * 0.27f
        val r = fontPx * 0.066f
        paint.color = Color.White.copy(alpha = alpha).toArgb()
        drawIntoCanvas { c ->
            for (i in 0..1) {
                val sh = if (i == 0) shadows.first else shadows.second
                paint.setShadowLayer(sh.blurRadius, sh.offset.x, sh.offset.y, sh.color.toArgb())
                c.nativeCanvas.drawCircle(cx, cy - dy, r, paint)
                c.nativeCanvas.drawCircle(cx, cy + dy, r, paint)
            }
        }
    }
}
