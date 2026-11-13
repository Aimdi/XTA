package com.aimdi.xtanative.plugins.stocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.networks.stocks.QuoteRows
import com.aimdi.xtanative.networks.stocks.StockNumbers
import com.aimdi.xtanative.networks.stocks.StockRowState
import com.aimdi.xtanative.networks.stocks.TickerQuote
import com.aimdi.xtanative.ui.plugins.pluginString
import java.time.Instant

/**
 * One quote line (stocks_quote_row.dart): the name over `$SPX · Index` on
 * the left, the price over its move on the right, the day's line between
 * them while the market trades. Grey bars while it loads, the close and its
 * day while the market is closed, and a retry beside the reason when there
 * is no data. [choices] go under the row, [warning] in amber in place of its
 * second line.
 */
@Composable
internal fun StocksQuoteRow(
    title: String,
    quote: TickerQuote?,
    onTap: () -> Unit,
    subtitle: String? = null,
    network: String? = null,
    failed: Boolean = false,
    showWindow: Boolean = true,
    showCloseDay: Boolean = true,
    onRetry: (() -> Unit)? = null,
    warning: String? = null,
    choices: (@Composable () -> Unit)? = null,
) {
    val now = Instant.now()
    val state = QuoteRows.state(quote, failed, now)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clickable(onClick = onTap).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
                BoxWithConstraints {
                    val room = maxWidth
                    val roomy = room >= 280.dp && LocalDensity.current.fontScale * 14 <= 18
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val reason = if (state == StockRowState.NO_DATA) pluginString(Strings.no_price_reason) else null
                        Identity(title, network, warning ?: reason ?: subtitle, whole = warning != null || reason != null, warning = warning != null, Modifier.weight(1f))
                        if (roomy && warning == null && state == StockRowState.LIVE && quote != null && quote.points.size >= 2) {
                            Spacer(Modifier.width(12.dp))
                            Sparkline(quote, Modifier.size(width = 64.dp, height = 24.dp))
                        }
                        when {
                            state == StockRowState.LOADING -> {
                                Spacer(Modifier.width(12.dp))
                                FiguresSkeleton()
                            }
                            state != StockRowState.NO_DATA && quote != null -> {
                                Spacer(Modifier.width(12.dp))
                                Box(Modifier.widthIn(max = room * 0.45f)) {
                                    Figures(quote, closed = state == StockRowState.CLOSED, caption = if (state == StockRowState.CLOSED) showCloseDay else showWindow, now = now)
                                }
                            }
                        }
                    }
                }
            }
            if (state == StockRowState.NO_DATA && onRetry != null) RetryButton(onRetry)
        }
        choices?.invoke()
    }
}

/** The title and network over the second line: a subtitle on one line, a reason or a caution ([whole]) read whole on two. */
@Composable
private fun Identity(title: String, network: String?, second: String?, whole: Boolean, warning: Boolean, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(3f, fill = false))
            network?.let {
                Spacer(Modifier.width(8.dp))
                NetworkChip(it, Modifier.weight(2f, fill = false))
            }
        }
        second?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 13.sp,
                color = if (warning) warningColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (whole) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** The price over its move, and beside the move what it is measured over or a closed market's day. Grey while the market is closed. */
@Composable
private fun Figures(quote: TickerQuote, closed: Boolean, caption: Boolean, now: Instant) {
    val symbols = numberSymbols()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val price = quote.displayPrice ?: return
    val percent = quote.changePercent
    val note = when {
        !caption -> null
        closed -> closedText(QuoteRows.closedTag(quote, now))
        percent == null -> null
        else -> windowText(quote.changeWindow)
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(
            StockNumbers.price(price, symbols),
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 15.sp,
            fontWeight = if (closed) FontWeight.Normal else FontWeight.Medium,
            color = if (closed) muted else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        if (note != null || percent != null) {
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                note?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp, color = muted, maxLines = 1) }
                percent?.let {
                    Text(StockNumbers.percent(it, symbols), style = MaterialTheme.typography.labelMedium, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (closed) muted else trendColor(it), maxLines = 1)
                }
            }
        }
    }
}

/** A token's network beside its symbol: `DOT [Base]`. */
@Composable
internal fun NetworkChip(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** "↻ Retry" beside a row that has no data, or the arrow alone, the words its label, where they would crowd the row out. */
@Composable
internal fun RetryButton(onRetry: () -> Unit) {
    BoxWithConstraints(Modifier.padding(end = 8.dp)) {
        val label = pluginString(Strings.retry)
        if (LocalDensity.current.fontScale > 1.3f) {
            IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, label, Modifier.size(20.dp)) }
        } else {
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        }
    }
}

/** The grey bars where a loading row's price and move will be: stacked for a row, one for a strip's entry ([inline]). */
@Composable
internal fun FiguresSkeleton(inline: Boolean = false) {
    val bone = MaterialTheme.colorScheme.surfaceContainerHighest
    val scale = LocalDensity.current.fontScale
    if (inline) return Box(Modifier.size(width = 72.dp, height = (12 * scale).dp).clip(RoundedCornerShape(4.dp)).background(bone))
    Column(horizontalAlignment = Alignment.End) {
        Box(Modifier.size(width = 76.dp, height = (14 * scale).dp).clip(RoundedCornerShape(4.dp)).background(bone))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.size(width = 52.dp, height = (12 * scale + 6).dp).clip(RoundedCornerShape(6.dp)).background(bone))
    }
}

/**
 * The day's line at thumbnail size, with the previous close as a faint
 * dashed baseline: above it green, below it red. Too small for a guide
 * ([baseline] off), it is the line alone.
 */
@Composable
internal fun Sparkline(quote: TickerQuote, modifier: Modifier = Modifier, baseline: Boolean = true) {
    val closes = quote.points.map { it.close }
    val line = trendColor(quote.changePercent)
    val guide = MaterialTheme.colorScheme.outlineVariant
    val base = quote.previousClose.takeIf { baseline }
    Canvas(modifier) {
        if (closes.size < 2) return@Canvas
        val values = closes + listOfNotNull(base)
        val low = values.min()
        val high = values.max()
        val span = (high - low).takeIf { it != 0.0 } ?: 1.0
        fun y(value: Double) = (size.height - (value - low) / span * size.height).toFloat()
        base?.let { b ->
            var x = 0f
            while (x < size.width) {
                drawLine(guide, Offset(x, y(b)), Offset(x + 2.dp.toPx(), y(b)), strokeWidth = 1.dp.toPx())
                x += 4.dp.toPx()
            }
        }
        val path = Path()
        closes.forEachIndexed { index, close ->
            val point = Offset(index / (closes.size - 1f) * size.width, y(close))
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, line, style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round))
    }
}
