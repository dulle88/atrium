package io.github.dulle88.atrium.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val GlassShape = RoundedCornerShape(16.dp)

/** Deep navy, about 80 % opaque at the top and 74 % at the bottom: reads as glass without backdrop blur. */
private val GlassFill = Brush.verticalGradient(listOf(Color(0xCC1B2A52), Color(0xBD0A1230)))

val GlassBorder = Color.White.copy(alpha = 0.18f)

/** Shared card look for every panel over the sky. */
fun Modifier.glassPanel(borderWidth: Dp = 1.dp, borderColor: Color = GlassBorder): Modifier =
    clip(GlassShape).background(GlassFill).border(borderWidth, borderColor, GlassShape)
