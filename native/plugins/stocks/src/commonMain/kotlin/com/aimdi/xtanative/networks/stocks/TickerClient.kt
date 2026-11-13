package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.repairs.RepairKind
import com.aimdi.xtanative.core.repairs.RepairSlot
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.networks.Page
import java.time.Duration
import okhttp3.HttpUrl

/** What a ticker page shows above its posts: the chart of the range picked, and today's quote for the key figures. */
data class TickerPage(val chart: TickerQuote, val day: TickerQuote?)

/**
 * Price history, search, trending and headlines for a symbol, from public
 * hosts that need no key or account, as XTA's ticker_client.dart reads them.
 *
 * A coin (`BTC-USD`) is asked of the exchanges in [CoinSourceChain] first and
 * of Yahoo last, so the stocks keep Yahoo's budget; [coinSources] replaces
 * the exchanges, and an empty list leaves coins to Yahoo alone.
 *
 * A read fails with a [com.aimdi.xtanative.networks.NetworkRequestException]
 * when a host refuses, an [java.io.IOException] when none answers, and a
 * [TickerException] for what no single answer says (no candidate name
 * answered, every source refused); [TickerErrorKind.of] reads any of them.
 */
class TickerClient(
    private val http: XHttp,
    coinSources: List<CoinSource>? = null,
    health: PriceSourceHealth = PriceSourceHealth.shared,
    /**
     * A browser-ish agent: the host refuses an empty one. XTA can repair it
     * (`stocks.user_agent`), so it is read at each request: a repair
     * published meanwhile reaches the next one.
     */
    private val userAgent: () -> String = { DEFAULT_USER_AGENT },
    private val timeout: Duration = PriceHttp.TIMEOUT,
) {
    private val coins = CoinSourceChain(
        coinSources ?: listOf(CoinbaseSource(http, userAgent, timeout), KrakenSource(http, userAgent, timeout), BinanceSource(http, userAgent, timeout)),
        health,
    )

    /**
     * The first of [symbol]'s candidate names ([Tickers.candidates]) that
     * answers with a price history. Only "no such symbol" or an answer
     * without a chart moves on to the next name: a refusal or a timeout says
     * nothing about the name, so it ends the lookup at once.
     */
    suspend fun fetchQuote(
        symbol: String,
        range: String = "1mo",
        interval: String = "1d",
        includePrePost: Boolean = false,
        prefer: TickerInstrument = TickerInstrument.AUTO,
    ): TickerQuote {
        var last: Throwable? = null
        for (candidate in Tickers.candidates(symbol, prefer)) {
            val asked = priceResult { quoteOf(candidate, range, interval, includePrePost) }
            asked.onSuccess { return it }
            val error = asked.exceptionOrNull()!!
            if (error.tickerKind == TickerErrorKind.UNAVAILABLE) throw error
            last = error
        }
        throw last ?: TickerException(TickerErrorKind.NOT_FOUND, "No such symbol: $symbol")
    }

    /** [symbol]'s chart over [range]; the day's chart carries its extended hours. */
    suspend fun fetchChart(symbol: String, range: TickerRange, prefer: TickerInstrument = TickerInstrument.AUTO): TickerQuote =
        fetchQuote(symbol, range.range, range.interval, range.includesExtendedHours, prefer)

    /**
     * A ticker page's chart over [range] and today's quote beside it: the
     * chart itself on the day's range, else [day] when the page has one, else
     * a fresh one-day read (none when that fails: the figures are extra).
     */
    suspend fun page(symbol: String, range: TickerRange, prefer: TickerInstrument = TickerInstrument.AUTO, day: TickerQuote? = null): TickerPage {
        val chart = fetchChart(symbol, range, prefer)
        val today = if (range == TickerRange.DAY) chart else day ?: priceResult { fetchChart(symbol, TickerRange.DAY, prefer) }.getOrNull()
        return TickerPage(chart, today)
    }

    /**
     * The quote a row, a strip entry or a cashtag shows for [symbol]: a share
     * or an index over its day against the previous close, with its extended
     * hours; a coin over the last 24 hours of its week chart. A known coin is
     * asked for as a coin unless the reader [pick]ed the listed security of the
     * same letters; anything else that turns out to be a coin is asked for
     * again over 24 hours, keeping the day chart if that fails.
     */
    suspend fun rowQuote(symbol: String, pick: TickerInstrument = TickerInstrument.AUTO): TickerQuote = when {
        pick == TickerInstrument.STOCK -> fetchChart(symbol, TickerRange.DAY, TickerInstrument.STOCK)
        pick == TickerInstrument.CRYPTO || Tickers.knownKind(symbol) == TickerKind.CRYPTO -> overLastDay(symbol)
        else -> {
            val day = fetchChart(symbol, TickerRange.DAY)
            if (!day.tradesAroundTheClock) day else priceResult { overLastDay(day.symbol) }.getOrElse { day }
        }
    }

    /** Symbols matching [query], as the add sheet lists them; the host has no further pages. */
    suspend fun searchSymbols(query: String): Page<TickerSearchHit> =
        Page(YahooFinance.searchHits(get(YahooFinance.searchUrl(query, quotes = 8, news = 0))), null)

    /** Headlines about [symbol]. */
    suspend fun fetchNews(symbol: String): Page<TickerNewsItem> =
        Page(YahooFinance.news(get(YahooFinance.searchUrl(symbol, quotes = 0, news = 6))), null)

    /** The symbols trending in [region], in the host's spelling ([Tickers.trendingCashtags] makes cashtags of them). */
    suspend fun fetchTrending(region: String = "US"): Page<String> = Page(YahooFinance.trending(get(YahooFinance.trendingUrl(region))), null)

    private suspend fun overLastDay(symbol: String): TickerQuote = fetchChart(symbol, TickerRange.WEEK, TickerInstrument.CRYPTO).rolling24h()

    private suspend fun quoteOf(candidate: String, range: String, interval: String, includePrePost: Boolean): TickerQuote {
        val url = YahooFinance.chartUrl(candidate, range, interval, includePrePost)
        val window = if (CoinSourceChain.isCoinPair(candidate)) ChartWindow.parse(range, interval) else null
        return if (window == null) yahoo(candidate, url) else coins.chart(candidate, window) { yahoo(candidate, url) }
    }

    private suspend fun yahoo(symbol: String, url: HttpUrl): TickerQuote =
        YahooFinance.quote(get(url), symbol) ?: throw TickerException(TickerErrorKind.BAD_RESPONSE, "No price history in the response for $symbol")

    private suspend fun get(url: HttpUrl): J = PriceHttp.getJson(http, url, PriceHosts.YAHOO, userAgent(), timeout)

    companion object {
        /** The agent XTA ships for the price hosts. */
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Android) XTA"

        /** The price hosts' agent as `repairs.json` may replace it; give a reading of [RepairSlot.valueIn] as `userAgent`. */
        val USER_AGENT_SLOT = RepairSlot("stocks.user_agent", DEFAULT_USER_AGENT, RepairKind.HEADER)

        /** Every Stocks value `repairs.json` may replace. */
        val REPAIR_SLOTS = listOf(USER_AGENT_SLOT)
    }
}
