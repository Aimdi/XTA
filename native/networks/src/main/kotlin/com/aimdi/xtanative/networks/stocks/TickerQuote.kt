package com.aimdi.xtanative.networks.stocks

import java.time.Duration
import java.time.Instant

/** One close of a price line. */
data class TickerPoint(val at: Instant, val close: Double)

/** The part of a trading day a moment falls in. */
enum class TickerSession { REGULAR, PRE, POST, CLOSED }

/** One stretch of an exchange's day: pre-market, regular or after hours. */
data class TradingPeriod(val start: Instant, val end: Instant) {
    operator fun contains(moment: Instant): Boolean = !moment.isBefore(start) && moment.isBefore(end)
}

/** A print outside regular hours, and which session it belongs to. */
data class ExtendedPrint(val session: TickerSession, val price: Double)

/**
 * What a quote's move is measured against: the previous session's close
 * (shares, indices), or the price 24 hours earlier (coins and tokens, which
 * have no close). Rows caption it `1D` and `24h`.
 */
enum class ChangeWindow { PREVIOUS_CLOSE, ROLLING_24H }

/** A move from a quote's reference to some price: the absolute change and the percentage. */
data class QuoteMove(val change: Double, val percent: Double)

/**
 * A symbol's recent price history and what the market page shows beside it,
 * as XTA's ticker_quote.dart holds it. Every figure is optional: a host can
 * send history without a live price, or a price without bars.
 */
data class TickerQuote(
    val symbol: String,
    val currency: String?,
    /** The latest regular-session price; the change is measured from [previousClose]. */
    val price: Double?,
    val previousClose: Double?,
    val points: List<TickerPoint>,
    val volume: Double? = null,
    val yearHigh: Double? = null,
    val yearLow: Double? = null,
    val shortName: String? = null,
    val dayHigh: Double? = null,
    val dayLow: Double? = null,
    val dayOpen: Double? = null,
    /** The session the host says is live (older payloads), with its extended prints. */
    val marketState: String? = null,
    val preMarketPrice: Double? = null,
    val postMarketPrice: Double? = null,
    /** Today's trading periods; null when the host did not send them. */
    val prePeriod: TradingPeriod? = null,
    val regularPeriod: TradingPeriod? = null,
    val postPeriod: TradingPeriod? = null,
    /** Coins trade around the clock, so they are never closed. */
    val tradesAroundTheClock: Boolean = false,
    /** The host's word for the instrument: `EQUITY`, `INDEX`, `ETF`, … */
    val instrumentType: String? = null,
    /** When the latest regular price was set: the close a closed market shows, and its day. */
    val regularMarketTime: Instant? = null,
    val changeWindow: ChangeWindow = ChangeWindow.PREVIOUS_CLOSE,
) {
    /** What the instrument is, from [instrumentType]; null for a type the badge has no word for. */
    val kind: TickerKind?
        get() = when (instrumentType) {
            "INDEX" -> TickerKind.MARKET_INDEX
            "EQUITY" -> TickerKind.STOCK
            "ETF" -> TickerKind.ETF
            "CRYPTOCURRENCY" -> TickerKind.CRYPTO
            "MUTUALFUND" -> TickerKind.FUND
            "FUTURE" -> TickerKind.FUTURE
            "CURRENCY" -> TickerKind.CURRENCY
            else -> if (tradesAroundTheClock) TickerKind.CRYPTO else null
        }

    /** The regular-session print: an after-hours price never sits beside a regular-hours move. */
    val displayPrice: Double? get() = price ?: points.lastOrNull()?.close

    /** The move from [previousClose] to [displayPrice]; null when either is missing. */
    val change: Double?
        get() {
            val now = displayPrice ?: return null
            val before = previousClose ?: return null
            return now - before
        }

    /** [change] as a percentage of [previousClose]; null against a zero close rather than a division by it. */
    val changePercent: Double?
        get() {
            val delta = change ?: return null
            val before = previousClose ?: return null
            return if (before == 0.0) null else delta / before * 100
        }

    /** True when up on the day; null when there is nothing to compare with, which is not flat. */
    val isUp: Boolean? get() = change?.let { it >= 0 }

    /** The latest pre-market or after-hours print, when there is one. */
    val extendedPrint: ExtendedPrint? get() = legacyExtendedPrint() ?: extendedPrintFromBars()

    /** The extended print's move against the regular price. */
    val extendedChangePercent: Double?
        get() {
            val extended = extendedPrint?.price ?: return null
            val regular = price ?: return null
            return if (regular == 0.0) null else (extended - regular) / regular * 100
        }

    val isPreMarket: Boolean get() = extendedPrint?.session == TickerSession.PRE

    val isAfterHours: Boolean get() = extendedPrint?.session == TickerSession.POST

    /** Which session [now] falls in; null when the payload cannot say. A coin is always in session. */
    fun sessionAt(now: Instant): TickerSession? {
        if (tradesAroundTheClock) return TickerSession.REGULAR
        val regular = regularPeriod ?: return legacySession()
        return when {
            now in regular -> TickerSession.REGULAR
            prePeriod?.contains(now) == true -> TickerSession.PRE
            postPeriod?.contains(now) == true -> TickerSession.POST
            else -> TickerSession.CLOSED
        }
    }

    /**
     * The move from where the chart began ([previousClose]) to [price]: the
     * ticker page's line under its price, also for a point under the finger.
     * Null when either end is missing or the reference is zero.
     */
    fun moveTo(price: Double?): QuoteMove? {
        val base = previousClose ?: return null
        val to = price ?: return null
        val change = to - base
        return if (base == 0.0) null else QuoteMove(change, change / base * 100)
    }

    /**
     * This quote measured over the last 24 hours of its bars rather than
     * against [previousClose]: the bars of the last day, with the close of the
     * bar 24 hours before the last as the reference. Itself when the bars do
     * not reach back that far.
     */
    fun rolling24h(): TickerQuote {
        val last = points.lastOrNull() ?: return this
        val cutoff = last.at.minus(Duration.ofHours(24))
        val before = points.lastOrNull { !it.at.isAfter(cutoff) } ?: return this
        return TickerQuote(
            symbol = symbol,
            currency = currency,
            price = price ?: last.close,
            previousClose = before.close,
            points = points.filter { !it.at.isBefore(before.at) },
            volume = volume,
            yearHigh = yearHigh,
            yearLow = yearLow,
            shortName = shortName,
            tradesAroundTheClock = tradesAroundTheClock,
            instrumentType = instrumentType,
            regularMarketTime = regularMarketTime,
            changeWindow = ChangeWindow.ROLLING_24H,
        )
    }

    private fun legacyExtendedPrint(): ExtendedPrint? = when {
        marketState == "PRE" && preMarketPrice != null -> ExtendedPrint(TickerSession.PRE, preMarketPrice)
        (marketState == "POST" || marketState == "POSTPOST") && postMarketPrice != null -> ExtendedPrint(TickerSession.POST, postMarketPrice)
        else -> null
    }

    /** With pre/post bars included, a bar past the close is the after-hours print and one in the pre-market the early one. */
    private fun extendedPrintFromBars(): ExtendedPrint? {
        val regular = regularPeriod
        val last = points.lastOrNull()
        return when {
            tradesAroundTheClock || regular == null || last == null -> null
            !last.at.isBefore(regular.end) -> ExtendedPrint(TickerSession.POST, last.close)
            prePeriod?.contains(last.at) == true -> ExtendedPrint(TickerSession.PRE, last.close)
            else -> null
        }
    }

    private fun legacySession(): TickerSession? = when (marketState) {
        "REGULAR" -> TickerSession.REGULAR
        "PRE", "PREPRE" -> TickerSession.PRE
        "POST", "POSTPOST" -> TickerSession.POST
        "CLOSED" -> TickerSession.CLOSED
        else -> null
    }
}
