package com.aimdi.xtanative.plugins.stocks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.stocks.ExtendedPrint
import com.aimdi.xtanative.networks.stocks.StockNumbers
import com.aimdi.xtanative.networks.stocks.TickerInstrument
import com.aimdi.xtanative.networks.stocks.TickerNewsItem
import com.aimdi.xtanative.networks.stocks.TickerQuote
import com.aimdi.xtanative.networks.stocks.TickerRange
import com.aimdi.xtanative.networks.stocks.TickerSession
import com.aimdi.xtanative.networks.stocks.Tickers
import com.aimdi.xtanative.networks.stocks.WatchlistRows
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.delay

/** A day's chart is live data: while it is on screen it is asked for again this often, without a spinner. */
private const val LIVE_EVERY_MS = 60_000L

/**
 * A ticker, as XTA's ticker screen shows one: what the symbol has done
 * lately (the chart is drawn from prices read here, no third-party page
 * embedded, and Stock chart switches it off with every request it makes),
 * its headlines, and the posts about it, followed accounts first. The star
 * puts it on the watchlist.
 */
@Composable
internal fun TickerScreen(app: StocksApp, symbol: String) {
    val model = rememberKept("ticker:$symbol") { TickerModel(app, symbol, it) }
    val state by model.state.collectAsState()
    val chart by app.chart.collectAsState()
    val entries by app.watchlist.state.collectAsState()
    val watched = entries?.let { WatchlistRows.watches(it.ids, symbol) } == true
    LaunchedEffect(model) { model.loadNews() }
    LaunchedEffect(model, chart) {
        if (!chart) return@LaunchedEffect
        model.start()
        while (true) {
            delay(LIVE_EVERY_MS)
            model.refreshLive()
        }
    }
    PluginScaffold(
        "$" + Tickers.spokenCashtag(symbol),
        onBack = app.host::back,
        actions = { WatchButton(watched) { app.launch { if (watched) app.watchlist.remove(symbol) else app.watchlist.add(symbol) } } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            StocksPostsList(app, model.posts, PaddingValues(), toTop = 0) {
                if (chart) item(key = "ticker-panel") { TickerPanel(model, state) }
                if (state.news.isNotEmpty()) item(key = "ticker-news") { TickerNews(state.news, app.host::openLink) }
            }
        }
    }
}

/** Everything above a ticker's posts, in the order Apple Stocks and Google Finance share: which instrument, the price and its move, the session, the chart, the ranges and today's figures. */
@Composable
private fun TickerPanel(model: TickerModel, state: TickerDetail) {
    Column(Modifier.fillMaxWidth()) {
        if (Tickers.isAmbiguous(model.symbol)) InstrumentPicker(state.instrument, model::selectInstrument)
        val quote = state.quote
        when {
            quote != null -> {
                QuoteHeader(model.symbol, state, quote)
                Spacer(Modifier.height(8.dp))
                Box {
                    TickerChart(quote, model::scrub, drawKey = state.range to state.instrument)
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                }
            }
            state.failed -> Unavailable({ model.load() }, notFound = state.notFound)
            // The first chart's place is held while it loads.
            else -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        RangePicker(state.range, model::selectRange)
        state.dayQuote?.let { TickerStats(it, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuoteHeader(symbol: String, state: TickerDetail, quote: TickerQuote) {
    val numbers = numberSymbols()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val scrubbed = state.scrubbed
    val price = scrubbed?.close ?: quote.displayPrice
    val session = (state.dayQuote ?: quote).sessionAt(Instant.now())
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$" + Tickers.spokenCashtag(symbol), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            if (session != null && !quote.tradesAroundTheClock) SessionLabel(session)
        }
        quote.shortName?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = muted) }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
            val spoken = if (price == null) Modifier else Modifier.clearAndSetSemantics { contentDescription = StockNumbers.priceSpoken(price, numbers) }
            Text(
                price?.let { StockNumbers.price(it, numbers) } ?: StockNumbers.PLACEHOLDER,
                spoken,
                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = TABULAR),
                fontWeight = FontWeight.ExtraBold,
            )
            quote.currency?.let { Text(it, Modifier.padding(start = 6.dp, bottom = 4.dp), style = MaterialTheme.typography.bodyMedium, color = muted) }
        }
        // The move since the range began, or up to the point under the finger, and what it is measured over.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
            quote.moveTo(price)?.let { move ->
                Text(
                    "${StockNumbers.change(move.change, numbers)} (${StockNumbers.percent(move.percent, numbers)})",
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR),
                    fontWeight = FontWeight.Bold,
                    color = trendColor(move.percent),
                )
            }
            Text(scrubbed?.let { scrubText(it.at) } ?: rangeText(state.range), style = MaterialTheme.typography.bodySmall, color = muted)
        }
        if (scrubbed == null) quote.extendedPrint?.let { ExtendedLine(quote, it) }
    }
}

/** `After hours  190.10  +0.14%`: the extended print, kept apart from the regular price so the move above stays the regular session's. */
@Composable
private fun ExtendedLine(quote: TickerQuote, extended: ExtendedPrint) {
    val numbers = numberSymbols()
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(pluginString(if (extended.session == TickerSession.PRE) Strings.pre_market else Strings.after_hours), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            StockNumbers.price(extended.price, numbers),
            Modifier.clearAndSetSemantics { contentDescription = StockNumbers.priceSpoken(extended.price, numbers) },
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR),
            fontWeight = FontWeight.Bold,
        )
        quote.extendedChangePercent?.let {
            Text(StockNumbers.percent(it, numbers), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = trendColor(it))
        }
    }
}

/** A dot, green while the market trades, and the session's name. */
@Composable
private fun SessionLabel(session: TickerSession) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val text = when (session) {
        TickerSession.REGULAR -> Strings.market_open
        TickerSession.PRE -> Strings.pre_market
        TickerSession.POST -> Strings.after_hours
        TickerSession.CLOSED -> Strings.market_closed
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (session == TickerSession.REGULAR) liveColor() else muted))
        Spacer(Modifier.width(6.dp))
        Text(pluginString(text), style = MaterialTheme.typography.labelMedium, color = muted)
    }
}

/** `$ETH` is a coin and a listed company: the reader picks which is charted. */
@Composable
private fun InstrumentPicker(instrument: TickerInstrument, onSelect: (TickerInstrument) -> Unit) {
    val stock = instrument == TickerInstrument.STOCK
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !stock, onClick = { if (stock) onSelect(TickerInstrument.CRYPTO) }, label = { Text(pluginString(Strings.crypto)) })
        FilterChip(selected = stock, onClick = { onSelect(TickerInstrument.STOCK) }, label = { Text(pluginString(Strings.plugin_name)) })
    }
}

@Composable
private fun RangePicker(selected: TickerRange, onSelect: (TickerRange) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TickerRange.entries.forEach { range ->
            FilterChip(selected = range == selected, onClick = { onSelect(range) }, label = { Text(rangeText(range)) })
        }
    }
}

/**
 * What the line cannot say, from a day's quote whatever range the chart
 * shows: where the day opened and the session before closed, its range,
 * how much changed hands, and where the price sits in the year.
 */
@Composable
private fun TickerStats(quote: TickerQuote, modifier: Modifier) {
    val numbers = numberSymbols()
    fun price(value: Double?) = value?.let { StockNumbers.price(it, numbers) }
    StatGrid(
        listOf(
            pluginString(Strings.open) to price(quote.dayOpen),
            pluginString(Strings.prev_close) to price(quote.previousClose),
            pluginString(Strings.day_high) to price(quote.dayHigh),
            pluginString(Strings.day_low) to price(quote.dayLow),
            pluginString(Strings.volume) to quote.volume?.let { StockNumbers.compact(it, numbers) },
            pluginString(Strings.year_high) to price(quote.yearHigh),
            pluginString(Strings.year_low) to price(quote.yearLow),
        ),
        modifier,
    )
}

/** Headlines for the symbol: a tap opens the article, nothing is posted. */
@Composable
private fun TickerNews(items: List<TickerNewsItem>, open: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)) {
        Text(pluginString(Strings.news), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
        items.take(4).forEach { item ->
            val url = item.url
            val publisher = item.publisher
            ListItem(
                headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = if (publisher == null) null else ({ Text(publisher) }),
                modifier = if (url == null) Modifier else Modifier.clickable { open(url) },
                colors = plainRow(),
            )
        }
    }
}

/** The short label a range goes by, abbreviated per language: a German reader expects `1T` and `1J`. */
@Composable
internal fun rangeText(range: TickerRange): String = pluginString(
    when (range) {
        TickerRange.DAY -> Strings.range_day
        TickerRange.WEEK -> Strings.range_week
        TickerRange.MONTH -> Strings.range_month
        TickerRange.SIX_MONTHS -> Strings.range_six_months
        TickerRange.YEAR -> Strings.range_year
        TickerRange.FIVE_YEARS -> Strings.range_five_years
    },
)

/** When the point under the finger was: its date and time, in the reader's language. */
@Composable
private fun scrubText(at: Instant): String {
    val locale = Locale.forLanguageTag(currentLanguage())
    val zoned = at.atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(zoned) + " " + DateTimeFormatter.ofPattern("HH:mm", locale).format(zoned)
}
