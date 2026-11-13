package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import okhttp3.HttpUrl

/** A symbol the host's search found. */
data class TickerSearchHit(val symbol: String, val name: String? = null, val type: String? = null, val exchange: String? = null) {
    /**
     * Equity, ETF, index, coin or fund: what a FinTwit watchlist wants. A
     * numbered depositary receipt (`AAPL01.BK`) is left out.
     */
    val isUseful: Boolean
        get() = when {
            type != null && type !in USEFUL_TYPES -> false
            symbol.any { it in '0'..'9' } && symbol.contains('.') -> false
            else -> symbol.isNotEmpty()
        }

    private companion object {
        val USEFUL_TYPES = setOf("EQUITY", "ETF", "INDEX", "CRYPTOCURRENCY", "MUTUALFUND")
    }
}

/** A headline for a symbol; opening it leaves the app, nothing is posted. */
data class TickerNewsItem(val title: String, val url: String? = null, val publisher: String? = null)

/**
 * Yahoo Finance's public chart, search and trending endpoints, as XTA reads
 * them: no key, no account, undocumented, so every field is optional and a
 * payload that no longer fits reads as nothing rather than throwing.
 */
object YahooFinance {
    const val HOST = "query1.finance.yahoo.com"

    /**
     * The chart of [symbol] (upper-cased, one path segment, so `^NDX` goes
     * out encoded once as `%5ENDX`). [includePrePost] adds the pre-market and
     * after-hours bars.
     */
    fun chartUrl(symbol: String, range: String = "1mo", interval: String = "1d", includePrePost: Boolean = false): HttpUrl =
        base().addPathSegments("v8/finance/chart").addPathSegment(symbol.uppercase())
            .addQueryParameter("range", range)
            .addQueryParameter("interval", interval)
            .apply { if (includePrePost) addQueryParameter("includePrePost", "true") }
            .build()

    /** The search for [query]: up to [quotes] symbols and [news] headlines (the ticker page asks for headlines alone). */
    fun searchUrl(query: String, quotes: Int = 8, news: Int = 0): HttpUrl =
        base().addPathSegments("v1/finance/search")
            .addQueryParameter("q", query)
            .addQueryParameter("quotesCount", "$quotes")
            .addQueryParameter("newsCount", "$news")
            .build()

    /** The symbols trending in [region], which the Trend tab reads. */
    fun trendingUrl(region: String = "US"): HttpUrl = base().addPathSegments("v1/finance/trending").addPathSegment(region).build()

    private fun base() = HttpUrl.Builder().scheme("https").host(HOST)

    /**
     * The quote in a chart answer (`chart.result[0]`): timestamps beside a
     * parallel list of closes, and a `meta` block. A bar missing either half
     * is skipped (a holiday leaves a null close). A day without bars is still
     * a quote when the host sends the last price; null when there is neither.
     */
    fun quote(json: J, symbol: String): TickerQuote? {
        val result = json["chart"]["result"][0]
        val meta = result["meta"]
        val points = pointsOf(result)
        if (points.isEmpty() && meta["regularMarketPrice"].double == null) return null
        val periods = meta["currentTradingPeriod"]
        val regular = periodOf(periods["regular"])
        return TickerQuote(
            symbol = meta["symbol"].string ?: symbol,
            currency = meta["currency"].string,
            price = meta["regularMarketPrice"].double,
            previousClose = meta["chartPreviousClose"].double ?: meta["previousClose"].double,
            points = points,
            volume = meta["regularMarketVolume"].double,
            yearHigh = meta["fiftyTwoWeekHigh"].double,
            yearLow = meta["fiftyTwoWeekLow"].double,
            shortName = meta["shortName"].string ?: meta["longName"].string,
            dayHigh = meta["regularMarketDayHigh"].double,
            dayLow = meta["regularMarketDayLow"].double,
            dayOpen = openOf(result, regular),
            marketState = meta["marketState"].string,
            preMarketPrice = meta["preMarketPrice"].double,
            postMarketPrice = meta["postMarketPrice"].double,
            prePeriod = periodOf(periods["pre"]),
            regularPeriod = regular,
            postPeriod = periodOf(periods["post"]),
            tradesAroundTheClock = meta["instrumentType"].string == "CRYPTOCURRENCY",
            instrumentType = meta["instrumentType"].string,
            regularMarketTime = meta["regularMarketTime"].long?.let(Instant::ofEpochSecond),
        )
    }

    /** The symbols a search found that a watchlist wants ([TickerSearchHit.isUseful]), upper-cased. */
    fun searchHits(json: J): List<TickerSearchHit> = json["quotes"].list.mapNotNull { quote ->
        val symbol = quote["symbol"].string?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        TickerSearchHit(
            symbol = symbol.uppercase(),
            name = quote["shortname"].string ?: quote["longname"].string,
            type = quote["quoteType"].string,
            exchange = quote["exchDisp"].string ?: quote["exchange"].string,
        ).takeIf { it.isUseful }
    }

    /** The headlines of a search answer that have a title. */
    fun news(json: J): List<TickerNewsItem> = json["news"].list.mapNotNull { story ->
        val title = story["title"].string?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        TickerNewsItem(title, story["link"].string, story["publisher"].string)
    }

    /** The trending symbols, upper-cased, in the host's order. */
    fun trending(json: J): List<String> = json["finance"]["result"][0]["quotes"].list.mapNotNull { quote ->
        quote["symbol"].string?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
    }

    private fun pointsOf(result: J): List<TickerPoint> {
        val timestamps = result["timestamp"].list
        val closes = result["indicators"]["quote"][0]["close"].list
        return timestamps.zip(closes).mapNotNull { (time, close) ->
            val seconds = time.long ?: return@mapNotNull null
            TickerPoint(Instant.ofEpochSecond(seconds), close.double ?: return@mapNotNull null)
        }
    }

    /** `{start, end}` in epoch seconds; anything else, or an end not after the start, is no period. */
    private fun periodOf(json: J): TradingPeriod? {
        val start = json["start"].long ?: return null
        val end = json["end"].long ?: return null
        return if (end <= start) null else TradingPeriod(Instant.ofEpochSecond(start), Instant.ofEpochSecond(end))
    }

    /** The first opening print inside today's regular session; without a session there is no telling. */
    private fun openOf(result: J, regular: TradingPeriod?): Double? {
        regular ?: return null
        val timestamps = result["timestamp"].list
        val opens = result["indicators"]["quote"][0]["open"].list
        return timestamps.zip(opens).firstNotNullOfOrNull { (time, open) ->
            val seconds = time.long ?: return@firstNotNullOfOrNull null
            open.double?.takeIf { Instant.ofEpochSecond(seconds) in regular }
        }
    }
}
