package com.aimdi.xtanative.plugins.stocks

import com.aimdi.xtanative.networks.stocks.TickerErrorKind
import com.aimdi.xtanative.networks.stocks.TickerInstrument
import com.aimdi.xtanative.networks.stocks.TickerNewsItem
import com.aimdi.xtanative.networks.stocks.TickerPoint
import com.aimdi.xtanative.networks.stocks.TickerQuote
import com.aimdi.xtanative.networks.stocks.TickerQuoteCache
import com.aimdi.xtanative.networks.stocks.TickerRange
import com.aimdi.xtanative.networks.stocks.Watchlist
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Everything a ticker page shows above its posts: the chart of [range] (its
 * move measured from the range's start), today's quote for the figures and
 * the session (kept while the reader flips ranges), the headlines, and the
 * point under the reader's finger.
 */
internal data class TickerDetail(
    val range: TickerRange = TickerRange.DAY,
    val instrument: TickerInstrument = TickerInstrument.AUTO,
    val quote: TickerQuote? = null,
    val dayQuote: TickerQuote? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
    /** The failure was the price host saying it has no such symbol, not a refusal or no answer. */
    val notFound: Boolean = false,
    val news: List<TickerNewsItem> = emptyList(),
    val scrubbed: TickerPoint? = null,
)

/**
 * A ticker page as XTA's TickerDetailStore loads it: a newer request always
 * wins, so switching range mid-flight never lets the old answer land; a
 * fresh day quote also feeds the watchlist and the cashtags in X's posts;
 * the reader's pick between a coin and the listed security of the same
 * letters is kept for every place the symbol shows. The posts below are
 * the cashtag people write (`$SPX`, `$BTC`), followed accounts first.
 */
internal class TickerModel(private val app: StocksApp, val symbol: String, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(TickerDetail(instrument = app.quotes.instrumentFor(symbol)))
    val state: StateFlow<TickerDetail> = mutable.asStateFlow()
    val posts = StocksPostsFeed(app, Watchlist.tickerPostsQuery(symbol), signals = false, followedFirst = true, scope = scope)
    private var generation = 0
    private var newsAsked = false
    private var loadedAt = Instant.EPOCH

    /** The reading the shared quotes are of: a day quote is handed on only while the page shows that one. */
    private var shared = state.value.instrument

    /** The chart, once: a page come back to keeps what it showed. */
    fun start() {
        if (state.value.quote == null && !state.value.loading) load()
    }

    /**
     * The day's chart is live: re-asked without a spinner while it shows; a
     * closed market's only as often as its close stays fresh, so the opening
     * bell is still caught.
     */
    fun refreshLive(now: Instant = Instant.now()) {
        val shown = state.value
        if (shown.range != TickerRange.DAY) return
        val fresh = TickerQuoteCache.freshFor(shown.dayQuote ?: shown.quote, now)
        if (fresh == TickerQuoteCache.CLOSED_TTL && Duration.between(loadedAt, now) < fresh) return
        load(silent = true)
    }

    /**
     * A [silent] refresh keeps what is on screen and never shows an error over
     * a chart that is merely a minute old, nor overtakes a load the reader is
     * waiting on, which may be for another range than the chart shown.
     */
    fun load(silent: Boolean = false) {
        if (silent && state.value.loading) return
        val asked = ++generation
        val (range, instrument) = state.value.let { it.range to it.instrument }
        if (!silent) mutable.update { it.copy(loading = true, failed = false, notFound = false) }
        loadedAt = Instant.now()
        scope.launch {
            var error: Exception? = null
            val page = try {
                app.tickers.page(symbol, range, instrument, state.value.dayQuote)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e
                null
            }
            if (asked != generation) return@launch
            if (page == null) {
                // Only a silent refresh keeps the chart: any other load was for a chart not shown yet.
                val notFound = error?.let { TickerErrorKind.of(it) } == TickerErrorKind.NOT_FOUND
                mutable.update { it.copy(loading = false, quote = if (silent) it.quote else null, failed = !silent || it.quote == null, notFound = notFound) }
                return@launch
            }
            mutable.update { it.copy(quote = page.chart, dayQuote = page.day, loading = false) }
            if (range == TickerRange.DAY && instrument == shared) app.quotes.remember(symbol, page.chart)
        }
    }

    /** The chart before stays up under the loading line until the new range's lands, so the price and all below keep their place. */
    fun selectRange(range: TickerRange) {
        if (range == state.value.range) return
        mutable.update { it.copy(range = range, scrubbed = null) }
        load()
    }

    /** The coin and the listed security are different instruments altogether, so today's figures go too. */
    fun selectInstrument(instrument: TickerInstrument) {
        if (instrument == state.value.instrument) return
        app.launch { app.quotes.choose(symbol, instrument) }
        shared = instrument
        mutable.update { it.copy(instrument = instrument, quote = null, dayQuote = null, scrubbed = null) }
        load()
    }

    fun scrub(point: TickerPoint?) = mutable.update { it.copy(scrubbed = point) }

    /** Headlines are extra: failing to get them changes nothing on screen. */
    fun loadNews() {
        if (newsAsked) return
        newsAsked = true
        scope.launch {
            app.quietly {
                val news = app.tickers.fetchNews(symbol).items
                mutable.update { it.copy(news = news) }
            }
        }
    }
}
