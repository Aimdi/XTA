package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl

/** One bar of a coin's chart. */
data class CoinBar(val at: Instant, val open: Double, val high: Double, val low: Double, val close: Double)

/** The exchanges' bars, and a coin's quote drawn from them as Yahoo draws one. */
object CoinBars {
    /** Coinbase's candles: `[time, low, high, open, close, volume]`, newest first. */
    fun coinbase(json: J): List<CoinBar> = json.list.mapNotNull { row ->
        val cells = row.list
        if (cells.size < 5) null else bar(cells[0].long?.let(Instant::ofEpochSecond), cells[3], cells[2], cells[1], cells[4])
    }

    /**
     * Kraken's OHLC bars `[time, open, high, low, close, vwap, volume, count]`
     * under Kraken's own name for the pair. An unknown pair is an error in
     * the body, not a status: [TickerErrorKind.NOT_FOUND]; any other error
     * there is [TickerErrorKind.UNAVAILABLE].
     */
    fun kraken(json: J): List<CoinBar> {
        val errors = json["error"].list.mapNotNull { it.string }
        if (errors.any { "Unknown asset pair" in it }) throw TickerException(TickerErrorKind.NOT_FOUND, "Kraken lists no such pair")
        if (errors.isNotEmpty()) throw TickerException(TickerErrorKind.UNAVAILABLE, "Kraken: ${errors.joinToString(", ")}")
        val rows = (json["result"].raw as? JsonObject)?.values?.firstOrNull { it is JsonArray }
        return J(rows).list.mapNotNull { row -> ohlc(row.list, Instant::ofEpochSecond) }
    }

    /** Binance's klines `[openTime ms, open, high, low, close, ...]`, oldest first. */
    fun binance(json: J): List<CoinBar> = json.list.mapNotNull { row -> ohlc(row.list, Instant::ofEpochMilli) }

    /**
     * A coin's quote from [bars] (any order), as Yahoo draws one: a day from
     * midnight UTC, a longer chart [span] back from the last bar, the last
     * close as the price and the close before the chart began as where it
     * started. The day's open, high and low are since midnight UTC whatever
     * the span. No bars at all is [TickerErrorKind.NOT_FOUND].
     */
    fun quote(pair: String, bars: List<CoinBar>, span: Duration, currency: String): TickerQuote {
        val sorted = bars.sortedBy { it.at }
        val last = sorted.lastOrNull() ?: throw TickerException(TickerErrorKind.NOT_FOUND, "No trades in $pair")
        val midnight = last.at.truncatedTo(ChronoUnit.DAYS)
        val start = if (span <= Duration.ofDays(1)) midnight else last.at.minus(span)
        val shown = sorted.filter { !it.at.isBefore(start) }
        val today = sorted.filter { !it.at.isBefore(midnight) }
        return TickerQuote(
            symbol = pair,
            currency = currency,
            price = last.close,
            previousClose = sorted.lastOrNull { it.at.isBefore(start) }?.close ?: shown.first().open,
            points = shown.map { TickerPoint(it.at, it.close) },
            shortName = Tickers.NAMES[Tickers.spokenCashtag(pair)],
            dayOpen = today.first().open,
            dayHigh = today.maxOf { it.high },
            dayLow = today.minOf { it.low },
            tradesAroundTheClock = true,
            instrumentType = "CRYPTOCURRENCY",
            regularMarketTime = last.at,
        )
    }

    /** A bar from `[time, open, high, low, close, ...]`, its time read by [at]. */
    private fun ohlc(cells: List<J>, at: (Long) -> Instant): CoinBar? =
        if (cells.size < 5) null else bar(cells[0].long?.let(at), cells[1], cells[2], cells[3], cells[4])

    private fun bar(at: Instant?, open: J, high: J, low: J, close: J): CoinBar? {
        at ?: return null
        return CoinBar(at, open.double ?: return null, high.double ?: return null, low.double ?: return null, close.double ?: return null)
    }
}

/** A public price host the chain can ask for a coin's chart. */
interface CoinSource {
    /** How the source goes by, never translated; its failures carry this name. */
    val name: String

    /** The step it would answer [window] in, or null when it cannot cover the span in one answer. */
    fun stepFor(window: ChartWindow): Duration?

    /** [coin] (`BTC`) over [window]. */
    suspend fun chart(coin: String, window: ChartWindow): TickerQuote
}

/** Coinbase Exchange: candles of 1, 5 and 15 minutes, an hour, six hours or a day, at most 300 to an answer. */
class CoinbaseSource(
    private val http: XHttp,
    private val userAgent: String = TickerClient.DEFAULT_USER_AGENT,
    private val timeout: Duration = PriceHttp.TIMEOUT,
) : CoinSource {
    override val name: String = PriceHosts.COINBASE

    override fun stepFor(window: ChartWindow): Duration? = window.coarsestFit(STEPS, 300)

    override suspend fun chart(coin: String, window: ChartWindow): TickerQuote {
        val step = requireNotNull(stepFor(window)) { "Coinbase cannot cover $window" }
        val json = PriceHttp.getJson(http, candlesUrl(coin, step), name, userAgent, timeout)
        return CoinBars.quote("$coin-USD", CoinBars.coinbase(json), window.span, "USD")
    }

    companion object {
        val STEPS: List<Duration> = listOf(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(6), Duration.ofDays(1),
        )

        /** [coin]'s USD candles of [step], in seconds as Coinbase spells it. */
        fun candlesUrl(coin: String, step: Duration): HttpUrl = HttpUrl.Builder().scheme("https").host("api.exchange.coinbase.com")
            .addPathSegment("products").addPathSegment("$coin-USD").addPathSegment("candles")
            .addQueryParameter("granularity", "${step.seconds}")
            .build()
    }
}

/** Kraken: bars of 1 minute to 15 days, the latest 720 to an answer, under Kraken's own names for a few coins. */
class KrakenSource(
    private val http: XHttp,
    private val userAgent: String = TickerClient.DEFAULT_USER_AGENT,
    private val timeout: Duration = PriceHttp.TIMEOUT,
) : CoinSource {
    override val name: String = PriceHosts.KRAKEN

    override fun stepFor(window: ChartWindow): Duration? = window.coarsestFit(STEPS, 720)

    override suspend fun chart(coin: String, window: ChartWindow): TickerQuote {
        val step = requireNotNull(stepFor(window)) { "Kraken cannot cover $window" }
        val json = PriceHttp.getJson(http, ohlcUrl(coin, step), name, userAgent, timeout)
        return CoinBars.quote("$coin-USD", CoinBars.kraken(json), window.span, "USD")
    }

    companion object {
        val STEPS: List<Duration> = listOf(1L, 5, 15, 30, 60, 240, 1440, 10080, 21600).map(Duration::ofMinutes)

        /** Kraken's names for coins it spells otherwise. */
        val NAMES: Map<String, String> = mapOf("BTC" to "XBT", "DOGE" to "XDG")

        /** [coin]'s USD bars of [step], in minutes, under Kraken's name for the coin. */
        fun ohlcUrl(coin: String, step: Duration): HttpUrl = HttpUrl.Builder().scheme("https").host("api.kraken.com")
            .addPathSegments("0/public/OHLC")
            .addQueryParameter("pair", "${NAMES[coin] ?: coin}USD")
            .addQueryParameter("interval", "${step.toMinutes()}")
            .build()
    }
}

/** Binance: klines of 1 minute to a week, up to 1000 to an answer, quoted in USDT. Some regions are refused (451). */
class BinanceSource(
    private val http: XHttp,
    private val userAgent: String = TickerClient.DEFAULT_USER_AGENT,
    private val timeout: Duration = PriceHttp.TIMEOUT,
) : CoinSource {
    override val name: String = PriceHosts.BINANCE

    override fun stepFor(window: ChartWindow): Duration? = window.coarsestFit(INTERVALS.keys.toList(), 1000)

    override suspend fun chart(coin: String, window: ChartWindow): TickerQuote {
        val step = requireNotNull(stepFor(window)) { "Binance cannot cover $window" }
        val json = PriceHttp.getJson(http, klinesUrl(coin, step, window), name, userAgent, timeout)
        return CoinBars.quote("$coin-USD", CoinBars.binance(json), window.span, "USDT")
    }

    companion object {
        /** Binance's interval names by step, finest first. */
        val INTERVALS: Map<Duration, String> = linkedMapOf(
            Duration.ofMinutes(1) to "1m",
            Duration.ofMinutes(3) to "3m",
            Duration.ofMinutes(5) to "5m",
            Duration.ofMinutes(15) to "15m",
            Duration.ofMinutes(30) to "30m",
            Duration.ofHours(1) to "1h",
            Duration.ofHours(2) to "2h",
            Duration.ofHours(4) to "4h",
            Duration.ofHours(6) to "6h",
            Duration.ofHours(12) to "12h",
            Duration.ofDays(1) to "1d",
            Duration.ofDays(3) to "3d",
            Duration.ofDays(7) to "1w",
        )

        /** As many klines as [window]'s span needs at [step], and one more, at most 1000. */
        fun klinesUrl(coin: String, step: Duration, window: ChartWindow): HttpUrl {
            val limit = minOf(1000, ceil(window.span.seconds.toDouble() / step.seconds).toInt() + 1)
            return HttpUrl.Builder().scheme("https").host("api.binance.com")
                .addPathSegments("api/v3/klines")
                .addQueryParameter("symbol", "${coin}USDT")
                .addQueryParameter("interval", requireNotNull(INTERVALS[step]) { "Binance has no $step klines" })
                .addQueryParameter("limit", "$limit")
                .build()
        }
    }
}

/**
 * Asks [sources] in order for a coin's chart, then Yahoo, the host the stocks
 * need, kept last and never rested. The first answer wins.
 *
 * Only when every source that covers the chart said it has no such coin is
 * the answer "not found", so the cashtag may still be a listed security; a
 * refusal or a rest along the way makes it "unavailable" instead.
 */
class CoinSourceChain(val sources: List<CoinSource>, val health: PriceSourceHealth = PriceSourceHealth.shared) {
    suspend fun chart(pair: String, window: ChartWindow, yahoo: suspend () -> TickerQuote): TickerQuote {
        val coin = pair.dropLast(4)
        var refused = false
        for (source in sources) {
            if (source.stepFor(window) == null || !health.lists(source.name, coin)) continue
            if (health.resting(source.name)) {
                refused = true
                continue
            }
            val asked = priceResult { source.chart(coin, window) }
            asked.onSuccess {
                health.answered(source.name)
                return it
            }
            refused = settle(source.name, coin, asked.exceptionOrNull()!!) || refused
        }
        return lastResort(pair, yahoo, refused)
    }

    /** A coin [source] has no trades in is not asked of it again; any other failure rests it. True when it rested. */
    private fun settle(source: String, coin: String, error: Throwable): Boolean {
        if (error.tickerKind == TickerErrorKind.NOT_FOUND) {
            health.unlisted(source, coin)
            return false
        }
        health.refused(source)
        return true
    }

    private suspend fun lastResort(pair: String, yahoo: suspend () -> TickerQuote, refused: Boolean): TickerQuote {
        val asked = priceResult { yahoo() }
        val error = asked.exceptionOrNull() ?: return asked.getOrThrow()
        if (refused && error.tickerKind != TickerErrorKind.UNAVAILABLE) {
            throw TickerException(TickerErrorKind.UNAVAILABLE, "No source could price $pair: ${error.message}", error)
        }
        throw error
    }

    companion object {
        /** Whether [symbol] names a coin as Yahoo spells one: `BTC-USD`. */
        fun isCoinPair(symbol: String): Boolean = symbol.endsWith("-USD") && !symbol.startsWith('^') && symbol.length > 4
    }
}
