package com.aimdi.xtanative.plugins.stocks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.networks.stocks.StockNumbers
import com.aimdi.xtanative.networks.stocks.TickerPoint
import com.aimdi.xtanative.networks.stocks.TickerQuote
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.plugins.currentLanguage
import java.time.Duration
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Room for the price labels down the right edge, and for the dates along the bottom. */
private val AxisWidth = 54.dp
private val AxisHeight = 22.dp

/** How many price gridlines the chart draws past the first. */
private const val GRID_LINES = 4

/** How long the price line takes to draw itself in, as X's stock cards do. */
private const val DRAW_IN_MS = 700

/**
 * The price line for a symbol (ticker_chart.dart), with axes and a scrubber:
 * drawn here rather than embedded, so no third-party page or script comes
 * in. Both axes are labelled, the previous close is a dashed line, and a
 * finger on the chart hands the point under it to [onScrub], null when it
 * lifts. A new [drawKey] (the range or the reading shown) draws the line in
 * again; a refreshed quote for the same one keeps it drawn.
 */
@Composable
internal fun TickerChart(quote: TickerQuote, onScrub: (TickerPoint?) -> Unit, drawKey: Any, modifier: Modifier = Modifier) {
    val points = quote.points
    val colors = ChartColors(
        line = trendColor(quote.changePercent),
        grid = MaterialTheme.colorScheme.outlineVariant,
        crosshair = MaterialTheme.colorScheme.onSurface,
        surface = MaterialTheme.colorScheme.surface,
    )
    val measurer = rememberTextMeasurer()
    val labels = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    val symbols = numberSymbols()
    val dates = axisDates(points)
    val reduced = LocalReducedMotion.current
    val drawn = remember { Animatable(0f) }
    var active by remember { mutableStateOf<Int?>(null) }
    val scrub by rememberUpdatedState(onScrub)
    val axis = with(LocalDensity.current) { AxisWidth.toPx() }
    LaunchedEffect(drawKey) {
        if (reduced) {
            drawn.snapTo(1f)
        } else {
            drawn.snapTo(0f)
            drawn.animateTo(1f, tween(DRAW_IN_MS, easing = EaseOutCubic))
        }
    }
    fun pointAt(x: Float, width: Float) {
        val plot = width - axis
        if (points.size < 2 || plot <= 0f) return
        val index = ((x / plot).coerceIn(0f, 1f) * (points.size - 1)).roundToInt()
        if (index != active) {
            active = index
            scrub(points[index])
        }
    }
    fun lift() {
        if (active == null) return
        active = null
        scrub(null)
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(points) {
                detectTapGestures(onPress = { at ->
                    pointAt(at.x, size.width.toFloat())
                    tryAwaitRelease()
                    lift()
                })
            }
            .pointerInput(points) {
                detectHorizontalDragGestures(
                    onDragStart = { at -> pointAt(at.x, size.width.toFloat()) },
                    onDragEnd = ::lift,
                    onDragCancel = ::lift,
                    onHorizontalDrag = { change, _ -> pointAt(change.position.x, size.width.toFloat()) },
                )
            },
    ) {
        chart(points.map { it.close }, quote.previousClose, colors, active, drawn.value)
        val plot = Size(size.width - AxisWidth.toPx(), size.height - AxisHeight.toPx())
        if (points.size >= 2 && plot.width > 0f && plot.height > 0f) {
            prices(points.map { it.close }, quote.previousClose, plot).forEach { (value, y) ->
                label(measurer, StockNumbers.price(value, symbols), Offset(plot.width + 6.dp.toPx(), y - 6.dp.toPx()), AxisWidth.toPx() - 8.dp.toPx(), labels)
            }
            if (dates.size == 3) {
                val y = plot.height + 4.dp.toPx()
                label(measurer, dates[0], Offset(0f, y), plot.width / 3, labels)
                label(measurer, dates[1], Offset(plot.width / 2 - 24.dp.toPx(), y), plot.width / 3, labels)
                label(measurer, dates[2], Offset(plot.width - 52.dp.toPx(), y), plot.width / 3, labels)
            }
        }
    }
}

/** What the chart is drawn in. */
private class ChartColors(val line: Color, val grid: Color, val crosshair: Color, val surface: Color)

/** The span of [closes], the previous close ([base]) inside it, or the reference line it draws would sit off the chart. */
private fun span(closes: List<Double>, base: Double?): Pair<Double, Double> {
    val low = closes.min()
    val high = closes.max()
    return if (base == null) low to high else min(low, base) to max(high, base)
}

/** The height on a [plot] of [height] px a [value] sits at, between [low] and [high]. */
private fun yOf(value: Double, low: Double, high: Double, height: Float): Float =
    if (high == low) height / 2 else (height - (value - low) / (high - low) * height).toFloat()

/** The gridlines' values and heights, the lowest first. */
private fun prices(closes: List<Double>, base: Double?, plot: Size): List<Pair<Double, Float>> {
    val (low, high) = span(closes, base)
    return (0..GRID_LINES).map { i -> (low + (high - low) * i / GRID_LINES).let { it to yOf(it, low, high, plot.height) } }
}

/**
 * The gridlines, the dashed previous close, and the line with its shading
 * drawn in from the left as far as [drawn] (0 to 1); the point [active]
 * under the finger is a ring on a crosshair, which the line runs through.
 */
private fun DrawScope.chart(closes: List<Double>, base: Double?, colors: ChartColors, active: Int?, drawn: Float) {
    val plot = Size(size.width - AxisWidth.toPx(), size.height - AxisHeight.toPx())
    if (closes.size < 2 || plot.width <= 0f || plot.height <= 0f) return
    val (low, high) = span(closes, base)
    fun x(i: Int) = plot.width * i / (closes.size - 1)
    fun y(value: Double) = yOf(value, low, high, plot.height)
    prices(closes, base, plot).forEach { (_, at) -> drawLine(colors.grid.copy(alpha = 0.4f), Offset(0f, at), Offset(plot.width, at), 0.5.dp.toPx()) }
    base?.let { dashed(colors.grid, y(it), plot.width) }
    val path = Path().apply {
        moveTo(x(0), y(closes[0]))
        for (i in 1 until closes.size) lineTo(x(i), y(closes[i]))
    }
    val edge = 4.dp.toPx()
    clipRect(-edge, -edge, plot.width * drawn + edge, plot.height + edge) {
        val shade = Path().apply {
            addPath(path)
            lineTo(plot.width, plot.height)
            lineTo(0f, plot.height)
            close()
        }
        drawPath(shade, Brush.verticalGradient(listOf(colors.line.copy(alpha = 0.22f), colors.line.copy(alpha = 0f)), startY = 0f, endY = plot.height))
        drawPath(path, colors.line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    val index = active?.takeIf { it in closes.indices } ?: return
    val at = Offset(x(index), y(closes[index]))
    drawLine(colors.crosshair.copy(alpha = 0.45f), Offset(at.x, 0f), Offset(at.x, plot.height), 1.dp.toPx())
    drawCircle(colors.surface, 5.dp.toPx(), at)
    drawCircle(colors.line, 5.dp.toPx(), at, style = Stroke(2.5.dp.toPx()))
}

/** The previous close at [y], dashed so it never reads as part of the price line. */
private fun DrawScope.dashed(color: Color, y: Float, width: Float) {
    var x = 0f
    while (x < width) {
        drawLine(color, Offset(x, y), Offset(min(x + 4.dp.toPx(), width), y), 1.dp.toPx())
        x += 8.dp.toPx()
    }
}

/** One axis label at [at], cut short with an ellipsis past [width] px. */
private fun DrawScope.label(measurer: TextMeasurer, text: String, at: Offset, width: Float, style: TextStyle) {
    if (width < 1f || at.x >= size.width || at.y >= size.height) return
    drawText(measurer, text, at, style, TextOverflow.Ellipsis, softWrap = false, maxLines = 1, size = Size(width, Float.NaN))
}

/**
 * The bottom axis' three dates, in the reader's language: a day of minute
 * bars by the time, a month of daily closes by the day, years by the month.
 * The span the bars cover decides, not the range asked for.
 */
@Composable
private fun axisDates(points: List<TickerPoint>): List<String> {
    val language = currentLanguage()
    return remember(points, language) {
        if (points.size < 2) return@remember emptyList()
        val locale = Locale.forLanguageTag(language)
        val days = Duration.between(points.first().at, points.last().at).toDays()
        val format = when {
            days >= 200 -> DateTimeFormatter.ofPattern("MMM y", locale)
            days >= 2 -> DateTimeFormatter.ofPattern(monthDay(locale), locale)
            else -> DateTimeFormatter.ofPattern("HH:mm", locale)
        }
        listOf(points.first(), points[points.size / 2], points.last()).map { format.format(it.at.atZone(ZoneId.systemDefault())) }
    }
}

/** The language's short date without its year: `M/d`, `dd.MM`, `MM/dd`. */
private fun monthDay(locale: Locale): String =
    DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale)
        .replace(Regex("[^\\p{L}']*y+[^\\p{L}']*"), " ")
        .trim()
