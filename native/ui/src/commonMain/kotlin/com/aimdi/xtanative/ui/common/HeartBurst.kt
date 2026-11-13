package com.aimdi.xtanative.ui.common

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** How long X's like takes from the tap to the settled heart. */
const val HEART_BURST_MILLIS = 450

/**
 * X's like (XTA's HeartBurst), at [progress] from 0 to 1: the outline heart
 * shrinks to a dot, a circle blooms out of it and thins into a ring while
 * small dots scatter, and the filled heart pops back with a little overshoot.
 * The burst is painted around the heart's box without taking room, so the
 * row never moves; at rest (0, or 1) it is [outline] or [filled], as
 * [filledAtRest] asks.
 */
@Composable
fun HeartBurst(
    progress: Float,
    color: Color,
    size: Dp,
    filledAtRest: Boolean,
    outline: @Composable () -> Unit,
    filled: @Composable () -> Unit,
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        val t = progress
        if (t <= 0f || t >= 1f) {
            if (filledAtRest) filled() else outline()
            return@Box
        }
        Canvas(Modifier.fillMaxSize()) { drawBurst(t, color) }
        if (t < HeartBurstMotion.SHRINK_END) {
            val scale = 1 - HeartBurstMotion.phase(t, 0f, HeartBurstMotion.SHRINK_END)
            Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) { outline() }
        }
        if (t >= HeartBurstMotion.POP_START) {
            val scale = HeartBurstMotion.popScale(HeartBurstMotion.phase(t, HeartBurstMotion.POP_START, 1f))
            Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) { filled() }
        }
    }
}

/** The numbers of [HeartBurst]'s motion, as XTA's heart_burst.dart sets them. */
object HeartBurstMotion {
    const val SHRINK_END = 0.15f
    const val POP_START = 0.45f

    /** Where [t] is inside the stretch from [start] to [end], as 0 to 1. */
    fun phase(t: Float, start: Float, end: Float): Float = ((t - start) / (end - start)).coerceIn(0f, 1f)

    /** 0 → 1.2 → 1, the overshoot X's heart lands with. */
    fun popScale(p: Float): Float =
        if (p < 0.6f) 1.2f * EaseOutCubic.transform(p / 0.6f) else 1.2f - 0.2f * EaseInOutCubic.transform((p - 0.6f) / 0.4f)
}

/** The bloom's widest, as a share of the heart's box. */
private const val REACH = 0.7f
private const val DOT_PAIRS = 7

private fun DrawScope.drawBurst(t: Float, color: Color) {
    val radius = size.minDimension * REACH
    // A filled circle growing out of the dot, then hollowing into a ring that thins away.
    val grow = EaseOutCubic.transform(HeartBurstMotion.phase(t, 0.15f, 0.45f))
    val hollow = HeartBurstMotion.phase(t, 0.40f, 0.70f)
    if (grow > 0f && hollow < 1f) {
        val outer = radius * grow
        val width = outer * (1 - hollow)
        drawCircle(lerp(color, shifted(color, 25f), grow), radius = outer - width / 2, center = center, style = Stroke(width))
    }
    // Seven pairs of dots flying out of the ring and fading as they go.
    val fly = EaseOutCubic.transform(HeartBurstMotion.phase(t, 0.35f, 0.85f))
    if (fly <= 0f || fly >= 1f) return
    val distance = radius * (0.9f + 0.6f * fly)
    val dot = radius * 0.13f * (1 - fly)
    for (i in 0 until DOT_PAIRS) {
        val angle = -PI / 2 + i * 2 * PI / DOT_PAIRS
        val paint = shifted(color, i * 360f / DOT_PAIRS).copy(alpha = 1 - fly * 0.6f)
        listOf(-0.18 to 1f, 0.18 to 0.7f).forEach { (offset, scale) ->
            val reach = distance * (if (scale == 1f) 1f else 0.85f)
            val at = center + Offset((cos(angle + offset) * reach).toFloat(), (sin(angle + offset) * reach).toFloat())
            drawCircle(paint, radius = dot * scale, center = at)
        }
    }
}

/** [color] turned [degrees] around the colour wheel, for the dots' variety and the bloom's warming as it opens. */
private fun shifted(color: Color, degrees: Float): Color {
    val r = color.red
    val g = color.green
    val b = color.blue
    val high = max(r, max(g, b))
    val low = min(r, min(g, b))
    val lightness = (high + low) / 2
    val chroma = high - low
    if (chroma == 0f) return color
    val saturation = chroma / (1 - abs(2 * lightness - 1))
    val hue = when (high) {
        r -> 60 * (((g - b) / chroma) % 6)
        g -> 60 * ((b - r) / chroma + 2)
        else -> 60 * ((r - g) / chroma + 4)
    }
    val turned = ((hue + degrees) % 360 + 360) % 360
    return Color.hsl(turned, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f), color.alpha)
}
