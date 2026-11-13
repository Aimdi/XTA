package com.aimdi.xtanative.plugins.stocks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.networks.stocks.QuoteRows
import com.aimdi.xtanative.networks.stocks.StocksReading
import com.aimdi.xtanative.networks.stocks.TickerInstrument
import com.aimdi.xtanative.networks.stocks.TickerQuote
import com.aimdi.xtanative.networks.stocks.TickerQuotes
import com.aimdi.xtanative.networks.stocks.WatchlistEntries
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.subscriptions.rememberRowDrag

/**
 * The watchlist as quote rows under its title and count
 * (stocks_watchlist_list.dart), ending in a button to add a ticker. A long
 * press lifts a row to drag it elsewhere; a tap opens its page. A symbol
 * naming both a coin and a listed security shows its two readings under it
 * until the reader picks one; [tickers] has what the cache found of them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StocksWatchlistList(
    app: StocksApp,
    entries: WatchlistEntries,
    tickers: TickerQuotes,
    quotes: Map<String, TickerQuote>,
    failed: Set<String>,
    onAdd: () -> Unit,
    onRetry: (String) -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
    frame: PluginFrame,
) {
    val list = rememberLazyListState()
    val drag = rememberRowDrag(list)
    val symbols = entries.ids
    val keys: List<Any> = symbols
    ScrollsToTop(list, frame.toTop)
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            item(key = "watchlist-header") { SectionHeader(pluginString(Strings.my_watchlist), pluginPlural(Strings.ticker_count, symbols.size, symbols.size)) }
            items(drag.shown(keys), key = { it }) { key ->
                val symbol = key as String
                val dragged = drag.key == key
                Column(
                    Modifier
                        .graphicsLayer { translationY = if (dragged) drag.offset else 0f }
                        .background(if (dragged) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background)
                        .pointerInput(key, keys) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { drag.start(key, keys) },
                                onDrag = { change, amount ->
                                    change.consume()
                                    drag.drag(amount.y)
                                },
                                onDragEnd = { drag.end()?.let { (from, to) -> app.launch { app.watchlist.reorder(from, to) } } },
                                onDragCancel = { drag.end() },
                            )
                        },
                ) {
                    WatchlistRow(app, symbol, entries, tickers, quotes[symbol], symbol in failed, onRetry)
                    Hairline()
                }
            }
            item(key = "watchlist-footer") { Footer(symbols.size, onAdd) }
        }
    }
}

@Composable
private fun WatchlistRow(app: StocksApp, symbol: String, entries: WatchlistEntries, tickers: TickerQuotes, quote: TickerQuote?, failed: Boolean, onRetry: (String) -> Unit) {
    val asset = entries.assets[symbol]
    val key = symbol.uppercase()
    val reading = tickers.picks[key] ?: TickerInstrument.AUTO
    val identity = QuoteRows.watchlistRow(symbol, quote, asset, reading)
    val readings = if (asset == null) QuoteRows.readings(symbol, tickers.listings[key], reading, tickers.quotes[key]) else null
    // The coin is what a cashtag naming both is shown as until the reader picks.
    val choices: (@Composable () -> Unit)? = if (readings == null) {
        null
    } else {
        @Composable { ReadingChoices(readings, shown = TickerInstrument.CRYPTO) { pick -> app.launch { app.quotes.choose(symbol, pick) } } }
    }
    StocksQuoteRow(
        title = identity.title,
        network = identity.network,
        subtitle = identity.shortAddress ?: subtitleText(identity.cashtag, identity.kind),
        quote = quote,
        failed = failed,
        onTap = { app.open(symbol) },
        onRetry = { onRetry(symbol) },
        warning = if (readings == null) null else pluginString(Strings.two_readings),
        choices = choices,
    )
}

/** Two outlined choices under a watchlist row, name over kind, the reading shown now outlined in the accent. */
@Composable
private fun ReadingChoices(readings: List<StocksReading>, shown: TickerInstrument, onChoose: (TickerInstrument) -> Unit) {
    val accent = stocksAccent()
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        readings.forEach { reading ->
            val current = reading.instrument == shown
            OutlinedButton(
                onClick = { onChoose(reading.instrument) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { selected = current },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(if (current) 1.5.dp else 1.dp, if (current) accent else MaterialTheme.colorScheme.outlineVariant),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(reading.name, style = MaterialTheme.typography.bodyMedium, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    kindWord(reading.kind)?.let {
                        Text(pluginString(it), style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Add ticker, and how to reorder once there is more than one. */
@Composable
private fun Footer(count: Int, onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedButton(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(pluginString(Strings.add))
        }
        if (count > 1) {
            Box(Modifier.padding(top = 12.dp)) {
                Text(pluginString(Strings.reorder_hint), style = MaterialTheme.typography.bodySmall, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}
