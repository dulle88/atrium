package io.github.dulle88.atrium.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.dulle88.atrium.sky.CloudLightKey
import io.github.dulle88.atrium.sky.CloudTexture
import io.github.dulle88.atrium.sky.MoonTexture
import io.github.dulle88.atrium.sky.Rgb
import io.github.dulle88.atrium.sky.SkyMotion
import io.github.dulle88.atrium.sky.SkyScene
import io.github.dulle88.atrium.sky.SkyState
import io.github.dulle88.atrium.sky.SkyWindow
import io.github.dulle88.atrium.sky.WindowRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val CANVAS_FRAME_MS = 50L // about 20 fps keeps cheap TV boxes cool
private const val AGSL_FRAME_MS = 33L // about 30 fps
private const val FRAME_SLACK_MS = 8L // average wait for the next vsync after the delay
private const val MOON_EARTHSHINE = 0.1f

/**
 * Full-screen sky: gradient, stars and shooting stars everywhere, plus the "sky window" in the
 * top bar ([window], as fractions of the screen): far clouds, the sun or moon in its top-right
 * corner, near clouds. On API 33+ an AGSL shader draws the gradient and stars; the window is the
 * same bitmap drawing on both paths, so both look alike.
 */
@Composable
fun SkyBackground(
    state: SkyState,
    epochMs: Long,
    window: WindowRect,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableStateOf(0) }
    val sprites = rememberWindowSprites(state, widthPx, window)
    val sized = modifier.fillMaxSize().onSizeChanged { widthPx = it.width }
    if (Build.VERSION.SDK_INT >= 33) {
        val shader = remember {
            runCatching { RuntimeShader(SKY_SHADER) }
                .onFailure { Log.w("SkyBackground", "AGSL sky shader failed to compile, using Canvas", it) }
                .getOrNull()
        }
        var shaderFailed by remember { mutableStateOf(false) }
        if (shader != null && !shaderFailed) {
            AgslSky(shader, state, epochMs, window, sprites, sized) { shaderFailed = true }
        } else {
            CanvasSky(state, epochMs, window, sprites, sized)
        }
    } else {
        CanvasSky(state, epochMs, window, sprites, sized)
    }
}

/**
 * Animation seconds, wrapping at [SkyMotion.TIME_WRAP_S], updated about every [frameMs] day and night.
 * Only the draw phase reads it, so a tick redraws the sky without recomposing anything.
 */
@Composable
private fun rememberSkyTime(frameMs: Long): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(frameMs) {
        while (true) {
            withFrameNanos { time.floatValue = ((it / 1e9) % SkyMotion.TIME_WRAP_S).toFloat() }
            delay(frameMs - FRAME_SLACK_MS)
        }
    }
    return time
}

private fun Rgb.color(alpha: Float = 1f) = Color(r, g, b, alpha)
private fun rgb(r: Int, g: Int, b: Int) = Rgb(r / 255f, g / 255f, b / 255f)

// ---------------------------------------------------------------- Sky window (both renderers)

private class CloudSprite(val image: ImageBitmap, val placement: CloudTexture.Placement, val layer: CloudTexture.Layer)

private class WindowSprites(val far: List<CloudSprite>, val near: List<CloudSprite>, val moon: ImageBitmap?)

/**
 * Cloud and moon bitmaps, built on [Dispatchers.Default]. Cloud shapes are computed once per screen
 * width; they are re-coloured only when the quantised light ([CloudLightKey]) changes, and the moon
 * only when its phase moves by a degree. Nothing here runs per frame.
 */
@Composable
private fun rememberWindowSprites(state: SkyState, widthPx: Int, window: WindowRect): WindowSprites? {
    val win = remember(state, window) { SkyWindow.from(state, 1f, 1f, window) }
    val shapes by produceState<List<Pair<CloudTexture.Layer, List<Pair<CloudTexture.Placement, CloudTexture.Shape>>>>?>(null, widthPx) {
        if (widthPx <= 0) return@produceState
        value = withContext(Dispatchers.Default) {
            CloudTexture.LAYERS.map { layer ->
                layer to layer.placements(widthPx.toFloat()).map { it to CloudTexture.shape(it.seed, it.width, it.height) }
            }
        }
    }
    val clouds by produceState<List<List<CloudSprite>>?>(null, shapes, win.clouds) {
        val s = shapes ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val palette = CloudTexture.palette(win.clouds)
            s.map { (layer, list) ->
                list.map { (p, shape) ->
                    val px = CloudTexture.paint(shape, palette)
                    CloudSprite(Bitmap.createBitmap(px, p.width, p.height, Bitmap.Config.ARGB_8888).asImageBitmap(), p, layer)
                }
            }
        }
    }
    val moon by produceState<ImageBitmap?>(null, win.moonElongation) {
        value = withContext(Dispatchers.Default) {
            Bitmap.createBitmap(MoonTexture.render(win.moonElongation, MOON_EARTHSHINE), MoonTexture.SIZE, MoonTexture.SIZE, Bitmap.Config.ARGB_8888)
                .asImageBitmap()
        }
    }
    val c = clouds ?: return null
    return WindowSprites(c[0], c[1], moon)
}

/** Glow layers of the sun as in the mockup: radius multiple of the disc and alpha (low-sun extra added later). */
private val SUN_GLOWS = floatArrayOf(22f, 0.16f, 8f, 0.3f, 2.6f, 0.75f)

/**
 * Everything for the sky window that changes at most when the sky state changes (every 30 s):
 * brushes and positions. [drawFar], [drawBodies] and [drawNear] allocate nothing.
 */
private class WindowPainter(scope: CacheDrawScope, state: SkyState, window: WindowRect, private val sprites: WindowSprites?) {
    private val w = scope.size.width
    private val h = scope.size.height
    private val win = SkyWindow.from(state, w, h, window)
    private val sunCenter = Offset(win.sunX, win.sunY)
    private val moonCenter = Offset(win.moonX, win.moonY)
    private val r0 = win.sunRadius
    private val warm = win.sunWarmth
    private val sunVis = win.sunVisibility
    private val core = rgb(255, 255, 250).mix(rgb(255, 214, 160), warm)
    private val glow = rgb(255, 244, 214).mix(rgb(255, 140, 70), warm)

    private val glowRadii = FloatArray(3) { r0 * SUN_GLOWS[it * 2] }
    private val glowBrushes: List<Brush> = List(3) {
        val a = (SUN_GLOWS[it * 2 + 1] + if (it == 0) 0.1f * warm else 0f) * sunVis
        val s0 = 0.6f * r0 / glowRadii[it]
        Brush.radialGradient(
            s0 to glow.color(a), s0 + 0.35f * (1 - s0) to glow.color(a * 0.35f), 1f to glow.color(0f),
            center = sunCenter, radius = glowRadii[it]
        )
    }
    private val haze: Brush? = if (warm > 0.4f) Brush.radialGradient(
        0f to glow.color(0.28f * warm * sunVis), 1f to glow.color(0f), center = sunCenter, radius = r0 * 4
    ) else null
    private val disc = Brush.radialGradient(
        0f to core.color(sunVis), 0.8f to core.color(sunVis), 1f to core.mix(glow, 0.5f).color(sunVis),
        center = sunCenter, radius = r0
    )
    private val overGlow = Brush.radialGradient(
        0f to Color(1f, 240 / 255f, 215 / 255f, 0.35f * sunVis), 1f to Color(1f, 240 / 255f, 215 / 255f, 0f),
        center = sunCenter, radius = r0 * 3.2f
    )

    private val mr = win.moonRadius
    private val moonVis = win.moonVisibility
    private val moonHalo = Brush.radialGradient(
        0.2f to Color(200 / 255f, 215 / 255f, 1f, (0.08f + 0.24f * win.moonIllumination) * moonVis),
        1f to Color(150 / 255f, 180 / 255f, 1f, 0f),
        center = moonCenter, radius = mr * 4.5f
    )
    private val moonOffset = IntOffset((win.moonX - mr).roundToInt(), (win.moonY - mr).roundToInt())
    private val moonSize = IntSize((mr * 2).roundToInt(), (mr * 2).roundToInt())

    private fun DrawScope.clouds(list: List<CloudSprite>, t: Float) {
        for (c in list) {
            val p = c.placement
            val dw = p.width * 2
            val x = SkyWindow.cloudX(p.x, dw.toFloat(), w, c.layer.crossingS, t)
            drawImage(
                c.image, srcSize = IntSize(p.width, p.height),
                dstOffset = IntOffset(x.roundToInt(), (p.y * h).roundToInt()), dstSize = IntSize(dw, p.height * 2),
                alpha = c.layer.alpha, filterQuality = FilterQuality.Low
            )
        }
    }

    fun drawFar(scope: DrawScope, t: Float) { sprites?.let { scope.clouds(it.far, t) } }

    fun drawNear(scope: DrawScope, t: Float) {
        sprites?.let { scope.clouds(it.near, t) }
        if (sunVis > 0.01f) scope.drawCircle(overGlow, r0 * 3.2f, sunCenter, blendMode = BlendMode.Plus)
    }

    fun drawBodies(scope: DrawScope, t: Float) = with(scope) {
        val breathe = SkyMotion.sunBreath(t)
        if (sunVis > 0.01f) {
            scale(breathe, sunCenter) {
                for (i in 0 until 3) drawCircle(glowBrushes[i], glowRadii[i], sunCenter, blendMode = BlendMode.Plus)
            }
            haze?.let { scale(4.5f, 1f, sunCenter) { drawCircle(it, r0 * 4, sunCenter, blendMode = BlendMode.Plus) } }
            scale(1f, 1f - 0.08f * warm, sunCenter) { drawCircle(disc, r0, sunCenter) }
        }
        if (moonVis > 0.01f) {
            scale(breathe, moonCenter) { drawCircle(moonHalo, mr * 4.5f, moonCenter, blendMode = BlendMode.Plus) }
            sprites?.moon?.let {
                drawImage(it, dstOffset = moonOffset, dstSize = moonSize, alpha = moonVis, filterQuality = FilterQuality.High)
            }
        }
    }
}

// ---------------------------------------------------------------- AGSL (API 33+): gradient and stars

private const val SKY_SHADER = """
uniform float2 resolution;
uniform float time;
uniform float3 zenith;
uniform float3 horizon;
uniform float horizonY;
uniform float2 glowPos;
uniform float glowAmt;
uniform float3 glowColor;
uniform float night;
uniform float starRot;
uniform float starDrift;
uniform float2 meteorHead;
uniform float2 meteorTail;
uniform float meteorAmt;

float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }

half4 main(float2 frag) {
    float t = clamp(frag.y / horizonY, 0.0, 1.0);
    float3 col = mix(zenith, horizon, smoothstep(0.0, 1.0, t));
    if (frag.y > horizonY) {
        col = mix(horizon, mix(horizon, zenith, 0.35), (frag.y - horizonY) / (resolution.y - horizonY));
    }

    float gd = distance(frag, glowPos) / (resolution.x * 0.62);
    col = mix(col, glowColor, glowAmt * exp(-gd * gd * 6.0) * 0.6);

    float px = resolution.x / 1400.0;
    if (night > 0.01 && frag.y < horizonY) {
        float2 pivot = float2(resolution.x * 0.5, resolution.y * 1.15);
        float2 d0 = frag - pivot;
        float c = cos(-starRot);
        float s = sin(-starRot);
        float2 p = pivot + float2(d0.x * c - d0.y * s, d0.x * s + d0.y * c);
        p.x -= starDrift;
        float cell = resolution.x / 90.0;
        float2 g = floor(p / cell);
        // 90 cells per screen width, hashed periodically so the drift wraps without a jump.
        float2 gh = float2(mod(g.x, 90.0), g.y);
        float h = hash(gh);
        if (h > 0.93) {
            float2 sp = (g + float2(hash(gh + 1.7), hash(gh + 9.1))) * cell;
            float r = (0.6 + hash(gh + 4.2) * 1.4) * px * 1.4;
            float d = distance(p, sp);
            float tw = 0.3 + 0.35 * (1.0 + sin(time * (0.8 + hash(gh + 2.2) * 3.2) + h * 40.0));
            float fade = 1.0 - smoothstep(horizonY - resolution.y * 0.22, horizonY, frag.y);
            float b = (1.0 - smoothstep(0.0, r, d)) * tw * fade * night * (0.35 + 0.65 * hash(gh + 6.6));
            col += float3(b);
        }
    }

    if (meteorAmt > 0.0) {
        float2 ab = meteorHead - meteorTail;
        float2 ap = frag - meteorTail;
        float k = clamp(dot(ap, ab) / max(dot(ab, ab), 1.0), 0.0, 1.0);
        float d = length(ap - ab * k);
        float wdt = px * (0.8 + 2.2 * k);
        col += float3(0.95, 0.97, 1.0) * (1.0 - smoothstep(0.0, wdt, d)) * k * k * meteorAmt;
        float hd = distance(frag, meteorHead) / (px * 7.0);
        col += float3(0.9, 0.95, 1.0) * exp(-hd * hd) * meteorAmt * 0.6;
    }

    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

@RequiresApi(33)
@Composable
private fun AgslSky(
    shader: RuntimeShader,
    state: SkyState,
    epochMs: Long,
    window: WindowRect,
    sprites: WindowSprites?,
    modifier: Modifier,
    onFailure: () -> Unit
) {
    val time = rememberSkyTime(AGSL_FRAME_MS)
    val brush = remember(shader) { ShaderBrush(shader) }
    val meteor = remember { SkyMotion.Meteor() }
    Spacer(modifier.graphicsLayer().drawWithCache {
        val w = size.width
        val h = size.height
        val sc = SkyScene.from(state, epochMs, w, h)
        val painter = WindowPainter(this, state, window, sprites)
        val ok = try {
            shader.setFloatUniform("resolution", w, h)
            shader.setFloatUniform("zenith", sc.zenith.r, sc.zenith.g, sc.zenith.b)
            shader.setFloatUniform("horizon", sc.horizon.r, sc.horizon.g, sc.horizon.b)
            shader.setFloatUniform("horizonY", sc.horizonY)
            shader.setFloatUniform("glowPos", sc.glowX, sc.glowY)
            shader.setFloatUniform("glowAmt", sc.glowAmount)
            shader.setFloatUniform("glowColor", sc.glowColor.r, sc.glowColor.g, sc.glowColor.b)
            shader.setFloatUniform("night", sc.nightFactor)
            shader.setFloatUniform("starRot", sc.starRotation)
            true
        } catch (e: Exception) {
            Log.w("SkyBackground", "AGSL uniform update failed, switching to Canvas", e)
            onFailure()
            false
        }
        onDrawBehind {
            if (!ok) return@onDrawBehind
            val t = time.value
            val meteorAmt = if (sc.nightFactor > 0.3f && SkyMotion.meteor(t, meteor, h / w)) meteor.brightness * sc.nightFactor else 0f
            try {
                shader.setFloatUniform("time", t)
                shader.setFloatUniform("starDrift", SkyMotion.starDriftX(t, w))
                shader.setFloatUniform("meteorHead", meteor.headX * w, meteor.headY * h)
                shader.setFloatUniform(
                    "meteorTail",
                    meteor.headX * w - meteor.dirX * meteor.tail * w, meteor.headY * h - meteor.dirY * meteor.tail * w
                )
                shader.setFloatUniform("meteorAmt", meteorAmt)
            } catch (e: Exception) {
                Log.w("SkyBackground", "AGSL uniform update failed, switching to Canvas", e)
                onFailure()
                return@onDrawBehind
            }
            drawRect(brush)
            painter.drawFar(this, t)
            painter.drawBodies(this, t)
            painter.drawNear(this, t)
        }
    })
}

// ---------------------------------------------------------------- Canvas (API < 33)

private class Star(val x: Float, val y: Float, val brightness: Float, val size: Float, val phase: Float, val speed: Float, val tint: Float)

private val stars: List<Star> by lazy {
    val r = Random(21)
    List(520) {
        val m = Math.pow(r.nextDouble(), 3.2).toFloat()
        Star(r.nextFloat(), r.nextFloat() * 1.25f - 0.1f, 0.25f + 0.75f * (1 - m) * r.nextFloat() + 0.2f * m,
            0.5f + m * 1.8f, r.nextFloat() * 6.28f, 0.8f + r.nextFloat() * 3.2f, r.nextFloat())
    }
}

/** The brightest stars twinkle and are drawn one by one; the dimmer rest are baked into a bitmap once a minute. */
private const val TWINKLING_STARS = 60
private val twinkling: IntArray by lazy {
    stars.indices.sortedByDescending { stars[it].brightness * stars[it].size }.take(TWINKLING_STARS).toIntArray()
}
private val isTwinkling: BooleanArray by lazy { BooleanArray(stars.size).also { b -> twinkling.forEach { b[it] = true } } }

private fun Star.color(): Color = when {
    tint < 0.3f -> STAR_BLUE
    tint > 0.8f -> STAR_WARM
    else -> Color.White
}

private val STAR_BLUE = Color(0.75f, 0.82f, 1f)
private val STAR_WARM = Color(1f, 0.89f, 0.75f)

@Composable
private fun CanvasSky(state: SkyState, epochMs: Long, window: WindowRect, sprites: WindowSprites?, modifier: Modifier) {
    val time = rememberSkyTime(CANVAS_FRAME_MS)
    val meteor = remember { SkyMotion.Meteor() }
    val starLayer = remember { arrayOfNulls<Bitmap>(1) }
    val starPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    Spacer(modifier.graphicsLayer().drawWithCache {
        // Everything that changes at most once a minute is built here; the per-frame block below allocates nothing.
        val w = size.width
        val h = size.height
        val sc = SkyScene.from(state, epochMs, w, h)
        val painter = WindowPainter(this, state, window, sprites)
        val skyBrush = Brush.verticalGradient(
            0f to sc.zenith.color(), 0.86f to sc.horizon.color(), 1f to sc.horizon.mix(sc.zenith, 0.35f).color()
        )
        val glowBrush = if (sc.glowAmount > 0.01f) Brush.radialGradient(
            0f to sc.glowColor.color(0.62f * sc.glowAmount),
            0.35f to sc.glowColor.color(0.24f * sc.glowAmount),
            1f to sc.glowColor.color(0f),
            center = Offset(sc.glowX, sc.glowY), radius = w * 0.62f
        ) else null

        // Star positions after the daily rotation; per frame only drift and twinkle change.
        val n = stars.size
        val starX = FloatArray(n)
        val starY = FloatArray(n)
        val starAlpha = FloatArray(n)
        val hy = sc.horizonY
        if (sc.nightFactor > 0.01f) {
            val cr = cos(sc.starRotation)
            val sr = sin(sc.starRotation)
            val px = w * 0.5f
            val py = h * 1.15f
            stars.forEachIndexed { i, s ->
                val x0 = s.x * w - px
                val y0 = s.y * h - py
                starX[i] = px + x0 * cr - y0 * sr
                val y = ((py + x0 * sr + y0 * cr) % hy + hy) % hy
                starY[i] = y
                starAlpha[i] = s.brightness * sc.nightFactor * ((hy - y) / (h * 0.22f)).coerceIn(0f, 1f)
            }
        }
        val starScale = (w / 1400f) * 1.4f
        val staticStars: ImageBitmap? = if (sc.nightFactor > 0.01f && w >= 1f && hy >= 1f) {
            val bw = w.roundToInt()
            val bh = sc.horizonY.roundToInt().coerceAtLeast(1)
            val bmp = starLayer[0]?.takeIf { it.width == bw && it.height == bh }
                ?: Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also { starLayer[0] = it }
            bmp.eraseColor(0)
            val canvas = android.graphics.Canvas(bmp)
            stars.forEachIndexed { i, s ->
                if (isTwinkling[i]) return@forEachIndexed
                val a = starAlpha[i] * 0.65f // mean twinkle
                if (a < 0.02f) return@forEachIndexed
                starPaint.color = s.color().copy(alpha = a).toArgb()
                val x = ((starX[i] % w) + w) % w
                canvas.drawCircle(x, starY[i], s.size * starScale, starPaint)
            }
            bmp.asImageBitmap()
        } else null
        val pxUnit = w / 1400f

        onDrawBehind {
            val t = time.value
            drawRect(skyBrush)
            glowBrush?.let { drawRect(it) }

            if (sc.nightFactor > 0.01f) {
                val drift = SkyMotion.starDriftX(t, w)
                staticStars?.let {
                    drawImage(it, Offset(drift, 0f))
                    drawImage(it, Offset(drift - w, 0f))
                }
                for (i in twinkling) {
                    val s = stars[i]
                    val a = starAlpha[i] * SkyMotion.twinkle(t, s.speed, s.phase)
                    if (a < 0.02f) continue
                    val x = ((starX[i] + drift) % w + w) % w
                    drawCircle(s.color().copy(alpha = a), s.size * starScale, Offset(x, starY[i]))
                }

                if (sc.nightFactor > 0.3f && SkyMotion.meteor(t, meteor, h / w)) {
                    val b = meteor.brightness * sc.nightFactor
                    val mx = meteor.headX * w
                    val my = meteor.headY * h
                    val tx = meteor.dirX * meteor.tail * w
                    val ty = meteor.dirY * meteor.tail * w
                    val segments = 10
                    for (k in 0 until segments) {
                        val f0 = k / segments.toFloat()
                        val f1 = (k + 1) / segments.toFloat()
                        drawLine(
                            Color(0.95f, 0.97f, 1f, b * f1 * f1),
                            Offset(mx - tx * (1 - f0), my - ty * (1 - f0)), Offset(mx - tx * (1 - f1), my - ty * (1 - f1)),
                            strokeWidth = pxUnit * (0.8f + 2.2f * f1), cap = StrokeCap.Round
                        )
                    }
                    drawCircle(Color(0.9f, 0.95f, 1f, 0.35f * b), pxUnit * 7f, Offset(mx, my))
                    drawCircle(Color(1f, 1f, 1f, b), pxUnit * 2.2f, Offset(mx, my))
                }
            }

            painter.drawFar(this, t)
            painter.drawBodies(this, t)
            painter.drawNear(this, t)
        }
    })
}
