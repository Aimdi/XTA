package com.aimdi.xtanative.ui.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.intro_illustration_flow
import com.aimdi.xtanative.ui.generated.resources.intro_illustration_orbit
import com.aimdi.xtanative.ui.generated.resources.intro_illustration_phone
import com.aimdi.xtanative.ui.generated.resources.intro_page_of
import com.aimdi.xtanative.ui.home.HomeIcons
import com.aimdi.xtanative.ui.media.ReaderIcons
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import org.jetbrains.compose.resources.stringResource

/** How tall a card's drawing stands. */
private val DRAWING_HEIGHT = 200.dp

/** One picture for a screen reader: [label], and nothing of what it is drawn with. */
private fun Modifier.picture(label: String): Modifier = clearAndSetSemantics {
    contentDescription = label
    role = Role.Image
}

/** The intro's colours, as XTA's introTokens names them for these drawings. */
private class DrawingColours(val ink: Color, val secondary: Color, val accent: Color, val surface: Color, val hairline: Color) {
    /** A placeholder's bone, lifted from the surface it sits on (introBoneColor). */
    val bone: Color get() = ink.copy(alpha = 0.16f).compositeOver(surface)
}

@Composable
private fun drawingColours(): DrawingColours {
    val scheme = MaterialTheme.colorScheme
    return DrawingColours(scheme.onBackground, scheme.onSurfaceVariant, scheme.primary, scheme.surface, scheme.outlineVariant)
}

/** The launcher mark (XtaMarkPainter): two congruent ellipses crossed at ±45°, stroked with one even width. */
@Composable
internal fun XtaMark(size: Dp, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onBackground) {
    Canvas(modifier.size(size)) { drawXtaMark(color) }
}

private fun DrawScope.drawXtaMark(color: Color) {
    val side = size.minDimension
    val oval = Size((2 * IntroDrawing.MARK_SEMI_MAJOR * side).toFloat(), (2 * IntroDrawing.MARK_SEMI_MINOR * side).toFloat())
    val stroke = Stroke((IntroDrawing.MARK_STROKE * side).toFloat())
    listOf(45f, -45f).forEach { degrees ->
        rotate(degrees, center) { drawOval(color, topLeft = center - Offset(oval.width / 2, oval.height / 2), size = oval, style = stroke) }
    }
}

/** A source on the welcome card: where it sits on the mark's ellipses, and its mark until its plugin runs. */
private class OrbitSlot(val id: String, val angle: Double, val t: Double, val brand: Color)

/**
 * X where the two ellipses cross at the top; the others on the four tips,
 * the points farthest apart (intro_orbit_illustration.dart).
 */
private val ORBIT = listOf(
    OrbitSlot("x", PI / 4, 0.692, Color.Unspecified),
    OrbitSlot("bluesky", -PI / 4, 0.0, Color(0xFF0085FF)),
    OrbitSlot("mastodon", PI / 4, 0.5, Color(0xFF6364FF)),
    OrbitSlot("reddit", PI / 4, 0.0, Color(0xFFFF4500)),
    OrbitSlot("substack", -PI / 4, 0.5, Color(0xFFFF6719)),
)

/** How far the chips drift each way, in turns of their ellipse. */
private const val DRIFT_TURNS = 3.0 / 360

/**
 * The welcome card (IntroOrbitIllustration): the XTA mark with the sources'
 * marks on its ellipses, drifting a few degrees back and forth; still while
 * motion is reduced.
 */
@Composable
internal fun OrbitIllustration() {
    val label = stringResource(Res.string.intro_illustration_orbit)
    val drift = if (LocalReducedMotion.current) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "orbit")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(6_000, easing = LinearEasing)), label = "drift").value
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(DRAWING_HEIGHT).picture(label), contentAlignment = Alignment.Center) {
        val mark = min(maxWidth, maxHeight) * 0.62f
        val chip = 44.dp
        XtaMark(mark)
        ORBIT.forEach { slot ->
            val t = slot.t + DRIFT_TURNS * sin(2 * PI * drift)
            SourceChip(slot.id, slot.brand, chip, Modifier.offset { orbitOffset(mark.toPx(), slot.angle, t) })
        }
    }
}

private fun orbitOffset(mark: Float, angle: Double, t: Double): IntOffset {
    val (x, y) = IntroDrawing.orbitPoint(mark.toDouble(), angle, t)
    return IntOffset(x.roundToInt(), y.roundToInt())
}

/** A source's mark on a disc of the surface with a hairline (IntroSourceChip): its plugin's icon once it runs, else its initial in its colour. */
@Composable
private fun SourceChip(id: String, brand: Color, size: Dp, modifier: Modifier = Modifier) {
    val colours = drawingColours()
    val plugins by LocalPlugins.current.running.collectAsState()
    val running = plugins.firstOrNull { it.plugin.id == id }?.plugin
    Box(
        modifier.size(size).clip(CircleShape).background(colours.surface).border(1.dp, colours.hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            id == "x" -> Icon(ReaderIcons.XMark, contentDescription = null, tint = colours.ink, modifier = Modifier.size(size * 0.5f))
            running != null -> Icon(running.icon, contentDescription = null, tint = running.brandColor, modifier = Modifier.size(size * 0.55f))
            else -> Text(id.take(1).uppercase(), color = brand, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
        }
    }
}

/**
 * The device card (IntroPhoneIllustration): a phone holding its posts, a
 * like pulsing once and a bookmark on them, and the accent's check at its
 * corner. Nothing leaves it, and the empty room says so.
 */
@Composable
internal fun PhoneIllustration() {
    val label = stringResource(Res.string.intro_illustration_phone)
    BoxWithConstraints(Modifier.fillMaxWidth().height(DRAWING_HEIGHT).picture(label), contentAlignment = Alignment.Center) {
        val height = maxHeight * 0.82f
        val width = min(maxWidth * 0.44f, height / 1.75f)
        val rows = IntroDrawing.phoneRows(width.value.toDouble(), height.value.toDouble())
        val glyphs = List(rows) { row ->
            when (row) {
                0 -> Icons.Filled.Favorite
                rows - 1 -> HomeIcons.BookmarkFilled
                else -> null
            }
        }
        Phone(width, height, glyphs, pulseFirst = true, badge = true)
    }
}

/**
 * XTA's IntroPhone, drawn: its outline on the surface, the notch, and as many
 * placeholder posts as fit, [glyphs] at the end of their rows; [badge] puts
 * the accent's check on its bottom corner.
 */
@Composable
private fun Phone(width: Dp, height: Dp, glyphs: List<ImageVector?> = emptyList(), pulseFirst: Boolean = false, badge: Boolean = false) {
    val colours = drawingColours()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val painters = glyphs.map { it?.let { vector -> rememberVectorPainter(vector) } }
    val check = rememberVectorPainter(Icons.Filled.Check)
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val pulse = rememberPulse(pulseFirst)
    Canvas(Modifier.size(width, height)) {
        val unit = (size.width * IntroDrawing.PHONE_UNIT).toFloat()
        val corner = CornerRadius(size.width * 0.2f)
        val outline = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, corner)) }
        drawPath(outline, colours.surface)
        clipPath(outline) {
            val notch = size.width * 0.3f
            drawRoundRect(colours.secondary, Offset((size.width - notch) / 2, unit * 1.4f), Size(notch, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
            val left = unit * 0.8f
            val right = size.width - unit * 0.8f
            var top = unit * 1.4f + 2.dp.toPx() + unit
            painters.ifEmpty { List(IntroDrawing.phoneRows(width.value.toDouble(), height.value.toDouble())) { null } }.forEachIndexed { row, painter ->
                if (row > 0) top += unit
                val glyph = minOf(16.dp.toPx(), unit * 1.25f)
                drawSkeletonRow(left, right, top, unit, colours.bone, painter?.let { glyph }, rtl)
                if (painter != null) {
                    val x = if (rtl) left else right - glyph
                    val scale = if (row == 0) pulse else 1f
                    drawGlyph(painter, Offset(x, top), glyph, scale, colours.accent)
                }
                top += unit * 1.25f
            }
        }
        drawPath(outline, colours.hairline, style = Stroke(1.dp.toPx()))
        if (badge) {
            val disc = unit * 2.5f
            val inset = 8.dp.toPx()
            val centre = Offset(if (rtl) -inset + disc / 2 else size.width + inset - disc / 2, size.height + inset - disc / 2)
            drawCircle(colours.accent, disc / 2, centre)
            drawGlyph(check, centre - Offset(disc * 0.3f, disc * 0.3f), disc * 0.6f, 1f, onAccent)
        }
    }
}

/** One placeholder post (IntroSkeletonRow): the avatar, two bars, and room for a glyph of [trailing] size at its end. */
private fun DrawScope.drawSkeletonRow(left: Float, right: Float, top: Float, unit: Float, bone: Color, trailing: Float?, rtl: Boolean) {
    val bar = unit / 2
    val end = right - (trailing?.let { it + bar } ?: 0f)
    val start = left + unit + bar
    val room = end - start
    fun x(from: Float, width: Float) = if (rtl) left + right - from - width else from
    val avatarX = x(left, unit)
    drawCircle(bone, unit / 2, Offset(avatarX + unit / 2, top + unit / 2))
    drawRoundRect(bone, Offset(x(start, room * 0.7f), top), Size(room * 0.7f, bar), CornerRadius(bar / 2))
    drawRoundRect(bone, Offset(x(start, room * 0.45f), top + bar * 1.5f), Size(room * 0.45f, bar), CornerRadius(bar / 2))
}

private fun DrawScope.drawGlyph(painter: VectorPainter, at: Offset, side: Float, scale: Float, tint: Color) {
    translate(at.x + side * (1 - scale) / 2, at.y + side * (1 - scale) / 2) {
        with(painter) { draw(Size(side * scale, side * scale), colorFilter = ColorFilter.tint(tint)) }
    }
}

/** 1 → 1.15 → 1 once as it first shows (IntroPulseOnce); 1 throughout while motion is reduced or [on] is not. */
@Composable
private fun rememberPulse(on: Boolean): Float {
    val reduced = LocalReducedMotion.current
    val scale = remember { Animatable(1f) }
    LaunchedEffect(on, reduced) {
        if (!on || reduced) return@LaunchedEffect
        scale.animateTo(1.15f, tween(250, easing = FastOutSlowInEasing))
        scale.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
    }
    return scale.value
}

/**
 * The account card (IntroFlowIllustration): posts flowing from X into the
 * phone, one way only; with accounts signed in, X's mark gives way to their
 * initials, up to three, overlapping.
 */
@Composable
internal fun FlowIllustration(accounts: List<String>, height: Dp = DRAWING_HEIGHT) {
    val label = stringResource(Res.string.intro_illustration_flow)
    BoxWithConstraints(Modifier.fillMaxWidth().height(height).picture(label)) {
        val chip = (min(maxWidth, maxHeight) * 0.26f).coerceIn(48.dp, 72.dp)
        val phoneWidth = maxWidth * 0.32f
        val phoneHeight = min(maxHeight, phoneWidth * 1.9f)
        Row(Modifier.fillMaxWidth().height(maxHeight), verticalAlignment = Alignment.CenterVertically) {
            if (accounts.isEmpty()) SourceChip("x", Color.Unspecified, chip) else Initials(accounts, chip)
            FlowArrow(Modifier.weight(1f).height(chip))
            Phone(phoneWidth, phoneHeight)
        }
    }
}

/** Up to three accounts' initials on accent discs, each ringed apart from the one it overlaps. */
@Composable
private fun Initials(accounts: List<String>, size: Dp) {
    val shown = accounts.take(3)
    val overlap = size * 0.3f
    val colours = drawingColours()
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val ring = MaterialTheme.colorScheme.background
    Box(Modifier.width(size + (size - overlap) * (shown.size - 1)).height(size)) {
        shown.forEachIndexed { index, name ->
            Box(
                Modifier.padding(start = (size - overlap) * index).size(size).clip(CircleShape).background(colours.accent)
                    .let { if (shown.size > 1) it.border(2.dp, ring, CircleShape) else it },
                contentAlignment = Alignment.Center,
            ) {
                Text(name.take(1).uppercase(), color = onAccent, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** A stroke ending in a chevron, drawn in once from X towards the phone, mirrored right to left (IntroFlowArrow). */
@Composable
private fun FlowArrow(modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) { if (!reduced) progress.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) else progress.snapTo(1f) }
    val colour = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
    val mirrored = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier) {
        val shown = progress.value
        if (shown <= 0f) return@Canvas
        val y = size.height / 2
        val inset = 12.dp.toPx()
        val head = minOf(10.dp.toPx(), size.width / 4)
        val way = if (mirrored) -1f else 1f
        val from = Offset(if (mirrored) size.width - inset else inset, y)
        val to = Offset(if (mirrored) inset else size.width - inset, y)
        val strokes = listOf(
            from to to,
            Offset(to.x - way * head, y - head) to to,
            to to Offset(to.x - way * head, y + head),
        )
        val lengths = strokes.map { (a, b) -> (b - a).getDistance() }
        var left = lengths.sum() * shown
        strokes.forEachIndexed { index, (a, b) ->
            if (left <= 0f) return@forEachIndexed
            val share = (left / lengths[index]).coerceAtMost(1f)
            drawLine(colour, a, a + (b - a) * share, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            left -= lengths[index]
        }
    }
}

/**
 * Five dots under the card, the current one a pill (IntroPageIndicator),
 * said as "Page n of 5" when the page changes.
 */
@Composable
internal fun PageIndicator(current: Int, total: Int) {
    val label = stringResource(Res.string.intro_page_of, current + 1, total)
    val reduced = LocalReducedMotion.current
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = label
            liveRegion = LiveRegionMode.Polite
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        repeat(total) { index ->
            val width by animateDpAsState(if (index == current) 24.dp else 8.dp, tween(if (reduced) 0 else 250), label = "dot")
            Box(Modifier.size(width, 8.dp).clip(RoundedCornerShape(4.dp)).background(if (index == current) active else inactive))
        }
    }
}
