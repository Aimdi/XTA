package com.aimdi.xtanative.plugins.stocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.networks.stocks.QuoteRows
import com.aimdi.xtanative.networks.stocks.StocksSection
import com.aimdi.xtanative.networks.stocks.StocksSectionKind
import com.aimdi.xtanative.networks.stocks.TickerQuote
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.pluginString
import java.time.Instant

/**
 * A list's title and, at the end, what the reader should know about it:
 * `Indices  ● Closed, last close Fri, 10/30` or `My watchlist  2 tickers`.
 * [live] puts a green dot before [status] for markets trading now, a grey
 * one for closed ones, none when the status is not about a market.
 */
@Composable
internal fun SectionHeader(title: String, status: String? = null, live: Boolean? = null) {
    BoxWithConstraints(Modifier.fillMaxWidth().semantics { heading() }.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val room = maxWidth
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = if (status == null) room else room * 0.6f))
            if (status != null) {
                Spacer(Modifier.width(12.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    live?.let {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (it) liveColor() else muted.copy(alpha = 0.7f)))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(status, fontSize = 12.sp, color = muted, maxLines = 2, textAlign = TextAlign.End)
                }
            }
        }
    }
}

/** What a markets section says about the markets in it, or nothing while none answered. */
@Composable
internal fun sectionStatus(section: StocksSection): String? = when (section.kind) {
    StocksSectionKind.UNKNOWN -> null
    StocksSectionKind.LIVE_24H -> pluginString(Strings.status_live_24h)
    StocksSectionKind.LIVE_DAY -> pluginString(Strings.status_live_day)
    StocksSectionKind.CLOSED -> section.lastClose?.let { pluginString(Strings.status_closed, closeDateText(it)) } ?: pluginString(Strings.market_closed)
}

/**
 * The markets page (stocks_markets.dart): the indices and the coins it
 * always shows, in two sections that each say once whether their markets
 * trade and what the moves measure. Every row has its name before a price.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StocksMarketsList(quotes: Map<String, TickerQuote>, failed: Set<String>, onOpen: (String) -> Unit, onRetry: (String) -> Unit, onRefresh: () -> Unit, refreshing: Boolean, padding: PaddingValues, toTop: Int) {
    val now = Instant.now()
    val list = rememberLazyListState()
    ScrollsToTop(list, toTop)
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            marketSection("indices", QuoteRows.INDICES, quotes, failed, now, onOpen, onRetry) { pluginString(Strings.section_indices) }
            marketSection("coins", QuoteRows.COINS, quotes, failed, now, onOpen, onRetry) { pluginString(Strings.type_crypto) }
        }
    }
}

private fun LazyListScope.marketSection(
    key: String,
    symbols: List<String>,
    quotes: Map<String, TickerQuote>,
    failed: Set<String>,
    now: Instant,
    onOpen: (String) -> Unit,
    onRetry: (String) -> Unit,
    title: @Composable () -> String,
) {
    val section = QuoteRows.section(symbols.map { quotes[it] }, now)
    item(key = "header-$key") {
        SectionHeader(title(), sectionStatus(section), live = if (section.kind == StocksSectionKind.UNKNOWN) null else section.kind != StocksSectionKind.CLOSED)
    }
    items(symbols, key = { "$key-$it" }) { symbol ->
        val quote = quotes[symbol]
        val identity = QuoteRows.marketsRow(symbol, quote)
        // A row in a closed section leaves its day to the header; one closed among live markets says its own.
        StocksQuoteRow(
            title = identity.title,
            subtitle = subtitleText(identity.cashtag, identity.kind),
            quote = quote,
            failed = symbol in failed,
            showWindow = false,
            showCloseDay = section.kind != StocksSectionKind.CLOSED,
            onTap = { onOpen(symbol) },
            onRetry = { onRetry(symbol) },
        )
        Hairline()
    }
}
