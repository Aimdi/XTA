package com.aimdi.xtanative.networks.stocks

import java.time.Duration
import java.time.Instant

/** What a quote row, a strip entry or a cashtag chip shows where its figures go. A dash used to stand for the first three. */
enum class StockRowState {
    /** No answer yet: a shimmer where the price and its move will be. */
    LOADING,

    /** A price during a session, regular or extended. */
    LIVE,

    /** The last close while the exchange has no session (night, weekend, holiday), with that close's day and a muted move. */
    CLOSED,

    /** The lookup found nothing to show. */
    NO_DATA,
}

/**
 * The day a closed market's close is from: shown as its weekday ("Close
 * Fri") while it is less than six days old, else as its date. [at] is null
 * when the quote does not say, and the tag is "Market closed".
 */
data class ClosedTag(val at: Instant?, val weekday: Boolean)

/**
 * Who a row is: its title, a token's network chip, the cashtag of its second
 * line (`$SPX` before `· Index`, null when the title already is the symbol),
 * what it is, and for a token its address cut in the middle as that line.
 */
data class RowIdentity(
    val title: String,
    val network: String? = null,
    val cashtag: String? = null,
    val kind: TickerKind? = null,
    val shortAddress: String? = null,
)

/** What a markets section says about the markets in it. */
enum class StocksSectionKind {
    /** Nothing has answered yet. */
    UNKNOWN,

    /** Coins: trading now, moves over 24 hours. */
    LIVE_24H,

    /** At least one exchange in the section trades now. */
    LIVE_DAY,

    /** None trades now: the rows show their last closes. */
    CLOSED,
}

/** A section's state, and for a closed one the time of its newest close. */
data class StocksSection(val kind: StocksSectionKind, val lastClose: Instant? = null)

/**
 * One way to read a cashtag that names both a coin and a listed security:
 * the reading, the name it goes by and what it is.
 */
data class StocksReading(val instrument: TickerInstrument, val name: String, val kind: TickerKind)

/** The rules XTA's quote rows follow, from stocks_row_state.dart and stocks_instruments.dart, as data a screen draws. */
object QuoteRows {
    private val CLOSE_AS_WEEKDAY: Duration = Duration.ofDays(6)

    /** The markets page's index section, in its order. */
    val INDICES: List<String> = Tickers.MARKET_SYMBOLS.filter { Tickers.knownKind(it) != TickerKind.CRYPTO }

    /** The markets page's coin section, in its order. */
    val COINS: List<String> = Tickers.MARKET_SYMBOLS.filter { Tickers.knownKind(it) == TickerKind.CRYPTO }

    /** The state of a row showing [quote] at [now]; [failed] is whether its last lookup found nothing. An answer beats a failure. */
    fun state(quote: TickerQuote?, failed: Boolean, now: Instant): StockRowState = when {
        quote == null -> if (failed) StockRowState.NO_DATA else StockRowState.LOADING
        quote.displayPrice == null -> StockRowState.NO_DATA
        quote.sessionAt(now) == TickerSession.CLOSED -> StockRowState.CLOSED
        else -> StockRowState.LIVE
    }

    /** The close a closed market shows: its regular-session time, else its last bar's. */
    fun closedTag(quote: TickerQuote, now: Instant): ClosedTag {
        val at = quote.regularMarketTime ?: quote.points.lastOrNull()?.at ?: return ClosedTag(null, weekday = false)
        return ClosedTag(at, weekday = Duration.between(at, now) < CLOSE_AS_WEEKDAY)
    }

    /**
     * What [symbol]'s row says it is: the quote's own word when it has one,
     * else what the symbol is known to be, unless the reader picked the
     * listed security behind a coin's letters ([reading]).
     */
    fun kindOf(symbol: String, quote: TickerQuote?, reading: TickerInstrument = TickerInstrument.AUTO): TickerKind? =
        quote?.kind ?: if (reading == TickerInstrument.STOCK) TickerKind.STOCK else Tickers.knownKind(symbol)

    /** The name over [symbol]'s row: an index's or a coin's own, else the quote's. A picked listing goes by the quote's alone. */
    fun name(symbol: String, quote: TickerQuote?, reading: TickerInstrument = TickerInstrument.AUTO): String? =
        if (reading == TickerInstrument.STOCK) quote?.shortName else Tickers.name(symbol) ?: quote?.shortName

    /** A markets row: named before any price arrives, over `$SPX · Index`. */
    fun marketsRow(symbol: String, quote: TickerQuote?): RowIdentity = RowIdentity(
        title = Tickers.name(symbol) ?: quote?.shortName ?: symbol,
        cashtag = "$${Tickers.spokenCashtag(symbol)}",
        kind = kindOf(symbol, quote),
    )

    /**
     * A watchlist row: a token by its symbol and network over its cut
     * address; a ticker by its name over `$SYM · Kind`, or by its symbol over
     * the kind alone when no name is known.
     */
    fun watchlistRow(symbol: String, quote: TickerQuote?, asset: CryptoAsset?, reading: TickerInstrument = TickerInstrument.AUTO): RowIdentity {
        if (asset != null) return RowIdentity(asset.bareLabel, network = asset.chainName, shortAddress = asset.shortAddress)
        val name = name(symbol, quote, reading)
        return RowIdentity(
            title = name ?: Tickers.spokenCashtag(symbol),
            cashtag = name?.let { "$${Tickers.spokenCashtag(symbol)}" },
            kind = kindOf(symbol, quote, reading),
        )
    }

    /** An entry of the strip above the posts: the symbol (a token's label and network), nothing else. */
    fun stripEntry(symbol: String, asset: CryptoAsset?): RowIdentity =
        RowIdentity(asset?.bareLabel ?: Tickers.spokenCashtag(symbol), network = asset?.chainName)

    /** The state of a section showing [quotes] at [now]. */
    fun section(quotes: Iterable<TickerQuote?>, now: Instant): StocksSection {
        val known = quotes.filterNotNull().filter { it.displayPrice != null }
        return when {
            known.isEmpty() -> StocksSection(StocksSectionKind.UNKNOWN)
            known.all { it.tradesAroundTheClock } -> StocksSection(StocksSectionKind.LIVE_24H)
            known.any { it.sessionAt(now) != TickerSession.CLOSED } -> StocksSection(StocksSectionKind.LIVE_DAY)
            else -> StocksSection(StocksSectionKind.CLOSED, known.mapNotNull { it.regularMarketTime }.maxOrNull())
        }
    }

    /**
     * The two readings of [symbol] while the reader has not picked one
     * ([pick] is [TickerInstrument.AUTO]) and a [listing] of the same letters
     * answered: the coin first (shown until then, named from [shown]), then the
     * listed security. Null when there is nothing to choose between.
     */
    fun readings(symbol: String, listing: TickerQuote?, pick: TickerInstrument, shown: TickerQuote?): List<StocksReading>? {
        if (listing == null || pick != TickerInstrument.AUTO) return null
        val spoken = Tickers.spokenCashtag(symbol)
        return listOf(
            StocksReading(TickerInstrument.CRYPTO, name(symbol, shown) ?: spoken, TickerKind.CRYPTO),
            StocksReading(TickerInstrument.STOCK, listing.shortName ?: spoken, listing.kind ?: TickerKind.STOCK),
        )
    }
}
