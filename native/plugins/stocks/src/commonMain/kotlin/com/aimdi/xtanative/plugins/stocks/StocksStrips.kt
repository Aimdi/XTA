package com.aimdi.xtanative.plugins.stocks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.networks.stocks.CashtagEntry
import com.aimdi.xtanative.networks.stocks.Cashtags
import com.aimdi.xtanative.networks.stocks.CryptoAsset
import com.aimdi.xtanative.networks.stocks.QuoteRows
import com.aimdi.xtanative.networks.stocks.RowIdentity
import com.aimdi.xtanative.networks.stocks.StockNumbers
import com.aimdi.xtanative.networks.stocks.StockRowState
import com.aimdi.xtanative.networks.stocks.TickerQuote
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.plugins.pluginString
import java.time.Instant
import kotlin.math.max

/**
 * The watched (or trending) tickers as a flat strip above the posts
 * (stocks_watchlist_reel.dart): each entry its symbol over its price and
 * move, thin lines between them. A tap filters the posts to it, a long
 * press opens its page.
 */
@Composable
internal fun StocksReel(
    symbols: List<String>,
    assets: Map<String, CryptoAsset>,
    quotes: Map<String, TickerQuote>,
    failed: Set<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val entry = max(48f, 40f * LocalDensity.current.fontScale).dp
    val line = MaterialTheme.colorScheme.outlineVariant
    Column {
        LazyRow(
            Modifier.fillMaxWidth().height(entry + 12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(symbols, key = { _, symbol -> symbol }) { index, symbol ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (index > 0) VerticalDivider(Modifier.height(entry - 16.dp), color = line)
                    ReelEntry(QuoteRows.stripEntry(symbol, assets[symbol]), quotes[symbol], symbol in failed, selected == symbol, { onSelect(symbol) }, { onOpen(symbol) })
                }
            }
        }
        Hairline()
    }
}

/** `DOT Base` over `0,0₂875 +3,98%`; "No price" without one, a grey bar while it loads. The entry filtering the posts is in the accent, with a line under it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReelEntry(identity: RowIdentity, quote: TickerQuote?, failed: Boolean, selected: Boolean, onTap: () -> Unit, onLongPress: () -> Unit) {
    val now = Instant.now()
    val state = QuoteRows.state(quote, failed, now)
    val accent = stocksAccent()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val label = (listOfNotNull(identity.title, identity.network) + spokenFigures(quote, state, now, window = true)).joinToString(", ")
    Column(
        Modifier
            .heightIn(min = 48.dp)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                this.selected = selected
                onClick { onTap(); true }
                onLongClick { onLongPress(); true }
            }
            .drawBehind { if (selected) drawRect(accent, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx())) }
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        val name = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (selected) accent else Color.Unspecified)) { append(identity.title) }
            identity.network?.let { withStyle(SpanStyle(fontSize = 12.sp, color = muted)) { append(" $it") } }
        }
        Text(name, style = MaterialTheme.typography.labelLarge, fontSize = 14.sp, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        ReelFigures(quote, state)
    }
}

@Composable
private fun ReelFigures(quote: TickerQuote?, state: StockRowState) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR)
    val price = quote?.displayPrice
    when {
        state == StockRowState.LOADING -> FiguresSkeleton(inline = true)
        state == StockRowState.NO_DATA || price == null -> Text(pluginString(Strings.no_price), style = style, color = muted, maxLines = 1)
        else -> {
            val closed = state == StockRowState.CLOSED
            val percent = quote.changePercent
            val symbols = numberSymbols()
            val move = if (closed) muted else trendColor(percent)
            val text = buildAnnotatedString {
                append(StockNumbers.price(price, symbols))
                percent?.let { withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = move)) { append(" " + StockNumbers.percent(it, symbols)) } }
            }
            Text(text, style = style, color = if (closed) muted else MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

/**
 * The price strip at the foot of an X post that names tickers
 * (cashtag_quotes.dart), set in 16 dp from the start as XTA sets it, with a
 * hairline above and below: each of its first cashtags with the day's line,
 * the price and the move, on one line that scrolls sideways. A tap opens
 * the ticker's page.
 */
@Composable
internal fun CashtagStrip(app: StocksApp, symbols: List<String>) {
    val shown = remember(symbols) { symbols.take(Cashtags.STRIP_LIMIT) }
    if (shown.isEmpty()) return
    LaunchedEffect(shown) { app.launch { app.quotes.ensure(shown) } }
    val quotes by app.quotes.quotes.collectAsState()
    val entries = Cashtags.entries(shown, quotes.quotes, quotes.failed, Instant.now())
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.padding(start = 16.dp, top = 8.dp)) {
        Hairline()
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            entries.forEachIndexed { index, entry ->
                if (index > 0) VerticalDivider(Modifier.height(20.dp), color = line)
                CashtagEntryView(entry, first = index == 0) { app.open(entry.symbol) }
            }
        }
        Hairline()
    }
}

/** `AAPL ⌇ 344.63 −1.23%`: a grey bar while it loads, "No price" without one, a closed market in the secondary colour. */
@Composable
private fun CashtagEntryView(entry: CashtagEntry, first: Boolean, onTap: () -> Unit) {
    val label = (listOf("$" + entry.symbol) + spokenFigures(entry.quote, entry.state, Instant.now(), window = false, closeDay = false)).joinToString(", ")
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onTap)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                onClick { onTap(); true }
            }
            .padding(start = if (first) 0.dp else 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(entry.symbol, style = MaterialTheme.typography.bodyMedium, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        CashtagFigures(entry)
    }
}

@Composable
private fun CashtagFigures(entry: CashtagEntry) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val price = entry.price
    val quote = entry.quote
    when {
        entry.state == StockRowState.LOADING -> FiguresSkeleton(inline = true)
        price == null || quote == null -> Text(pluginString(Strings.no_price), style = MaterialTheme.typography.bodySmall, fontSize = 13.sp, color = muted)
        else -> {
            val symbols = numberSymbols()
            if (entry.sparkline.isNotEmpty()) {
                Sparkline(quote, Modifier.size(width = 32.dp, height = 14.dp), baseline = false)
                Spacer(Modifier.width(6.dp))
            }
            val figures = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR)
            Text(StockNumbers.price(price, symbols), style = figures, fontSize = 14.sp, color = if (entry.muted) muted else MaterialTheme.colorScheme.onSurface)
            entry.changePercent?.let {
                Spacer(Modifier.width(6.dp))
                Text(StockNumbers.percent(it, symbols), style = figures, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (entry.muted) muted else trendColor(it))
            }
        }
    }
}

/**
 * What a strip entry says to a screen reader after its name: that it loads,
 * that there is no price, or the price, a closed market's day ([closeDay])
 * and the move, with what the move is measured over ([window]) while it trades.
 */
@Composable
private fun spokenFigures(quote: TickerQuote?, state: StockRowState, now: Instant, window: Boolean, closeDay: Boolean = true): List<String> {
    val symbols = numberSymbols()
    val price = quote?.displayPrice?.let { StockNumbers.priceSpoken(it, symbols) }
    val percent = quote?.changePercent?.let { StockNumbers.percent(it, symbols) }
    return when {
        state == StockRowState.LOADING -> listOf(pluginString(Strings.loading_price))
        state == StockRowState.NO_DATA || quote == null || price == null -> listOf(pluginString(Strings.no_price))
        state == StockRowState.CLOSED -> listOfNotNull(price, if (closeDay) closedText(QuoteRows.closedTag(quote, now)) else null, percent)
        else -> listOfNotNull(price, percent, if (window && percent != null) windowText(quote.changeWindow) else null)
    }
}
