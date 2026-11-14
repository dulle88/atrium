package io.github.dulle88.atrium.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import io.github.dulle88.atrium.portal.Notice
import kotlinx.coroutines.delay
import java.io.File

private val NoticeShape = RoundedCornerShape(16.dp)

/** The bottom shade for text on a photo: clear for the top 30 %, then up to 88 % dark navy at the bottom edge. */
private val PhotoShade = Brush.verticalGradient(
    0f to Color.Transparent,
    0.30f to Color.Transparent,
    0.62f to Color(0x8C060A1A),
    1f to Color(0xE0060A1A)
)

private val NoticeTextStyle = TextStyle(
    color = Color.White,
    fontWeight = FontWeight.Bold,
    lineHeight = 1.16.em,
    shadow = Shadow(Color(0x8C000000), Offset(0f, 2f), blurRadius = 10f)
)

/**
 * Plays the active notices one after another. A photo stays for the slideshow pace and a text-only
 * notice for 15 s, with a crossfade between them. Text on a photo sits bottom-left over a dark shade
 * (layout A); a text-only notice is centred on a glass panel. Text shrinks to fit and never overflows.
 */
@Composable
fun NoticePlayer(notices: List<Notice>, photoSeconds: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dir = remember { File(context.filesDir, "notices") }
    val slides = remember(notices, photoSeconds) { NoticeSlides.build(notices, photoSeconds) }
    if (slides.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    val current = slides[index % slides.size]

    LaunchedEffect(slides, index) {
        if (slides.size <= 1) return@LaunchedEffect
        delay(slides[index % slides.size].durationMs)
        index = (index % slides.size + 1) % slides.size
    }

    Crossfade(
        targetState = current,
        animationSpec = tween(durationMillis = 1000),
        modifier = modifier.fillMaxSize(),
        label = "notices"
    ) { slide ->
        if (slide.photo != null) PhotoSlide(File(dir, slide.photo), slide.text) else TextSlide(slide.text.orEmpty())
    }
}

@Composable
private fun PhotoSlide(file: File, text: String?) {
    val context = LocalContext.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize().clip(NoticeShape).background(Color.Black)) {
        AsyncImage(
            // Decode at about screen size, never full resolution: the box has under 1 GB of RAM.
            model = remember(file) {
                ImageRequest.Builder(context).data(file).size(1280, 720).precision(Precision.INEXACT).build()
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (text != null) {
            val pad = maxWidth * 0.04f
            Box(modifier = Modifier.fillMaxSize().background(PhotoShade))
            FitText(
                text = text,
                baseSize = maxWidth * 0.052f,
                textAlign = TextAlign.Start,
                alignment = Alignment.BottomStart,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(0.62f).padding(pad)
            )
        }
    }
}

@Composable
private fun TextSlide(text: String) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().glassPanel()) {
        FitText(
            text = text,
            baseSize = maxWidth * 0.064f,
            textAlign = TextAlign.Center,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().padding(maxWidth * 0.06f)
        )
    }
}

/** Starts at [baseSize] scaled by text length and shrinks further until the text fits the box. */
@Composable
private fun FitText(text: String, baseSize: Dp, textAlign: TextAlign, alignment: Alignment, modifier: Modifier) {
    BoxWithConstraints(modifier = modifier, contentAlignment = alignment) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        val fontSp = remember(text, baseSize, maxW, maxH) {
            val start = with(density) { baseSize.toSp().value } * NoticeSlides.textScale(text.length)
            var size = start
            while (size > start * MIN_FIT_FRACTION) {
                val measured = measurer.measure(
                    text, NoticeTextStyle.copy(fontSize = size.sp, textAlign = textAlign),
                    constraints = Constraints(maxWidth = maxW)
                )
                if (measured.size.height <= maxH) break
                size *= 0.92f
            }
            size
        }
        Text(
            text = text,
            style = NoticeTextStyle.copy(fontSize = fontSp.sp, textAlign = textAlign),
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private const val MIN_FIT_FRACTION = 0.3f
