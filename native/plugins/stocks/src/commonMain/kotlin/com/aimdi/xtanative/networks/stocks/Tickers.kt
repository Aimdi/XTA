package com.aimdi.xtanative.networks.stocks

/** Which reading of an ambiguous cashtag the reader wants: `$SOL` is Solana and a listed company. */
enum class TickerInstrument { AUTO, STOCK, CRYPTO }

/**
 * What a quote is, as the badge on its row says: an index is not a share,
 * and a coin is not either, even when they spell the same.
 */
enum class TickerKind { MARKET_INDEX, STOCK, ETF, CRYPTO, FUND, FUTURE, CURRENCY }

/**
 * Spoken cashtags against the names the price host quotes them by, as XTA's
 * ticker_symbol.dart keeps them. FinTwit writes `$SPX` and `$BTC`; the chart
 * host wants `^GSPC` and `BTC-USD`. One set of tables serves both ways.
 */
object Tickers {
    /** Index (and a few well-known) names as people say them, to the host's names. Coins get `-USD` instead. */
    val ALIASES: Map<String, String> = linkedMapOf(
        "SPX" to "^GSPC",
        "SP500" to "^GSPC",
        "DJI" to "^DJI",
        "DOW" to "^DJI",
        "NDX" to "^NDX",
        "IXIC" to "^IXIC",
        "NASDAQ" to "^IXIC",
        "RUT" to "^RUT",
        "VIX" to "^VIX",
        "DAX" to "^GDAXI",
        "FTSE" to "^FTSE",
        "CAC" to "^FCHI",
        "N225" to "^N225",
        "NIKKEI" to "^N225",
        "HSI" to "^HSI",
    )

    /** The host's index names back to the cashtag people write. */
    val SPOKEN: Map<String, String> = linkedMapOf(
        "^GSPC" to "SPX",
        "^DJI" to "DJI",
        "^NDX" to "NDX",
        "^IXIC" to "NASDAQ",
        "^RUT" to "RUT",
        "^VIX" to "VIX",
        "^GDAXI" to "DAX",
        "^FTSE" to "FTSE",
        "^FCHI" to "CAC",
        "^N225" to "N225",
        "^HSI" to "HSI",
    )

    /** The indices and coins a markets page always shows, in this order. */
    val MARKET_SYMBOLS: List<String> = listOf("SPX", "NDX", "DJI", "RUT", "VIX", "DAX", "FTSE", "N225", "BTC", "ETH")

    /**
     * Coins whose cashtag means the coin far more often than the listing that
     * shares its letters (`$ETH` is Ethereum, not Ethan Allen). Only coins the
     * chart host files under a plain `-USD` pair.
     */
    val COIN_FIRST: Set<String> = linkedSetOf(
        "BTC", "ETH", "SOL", "XRP", "DOGE", "ADA", "AVAX", "LINK", "DOT", "LTC", "BNB", "TRX", "XLM", "BCH", "ETC",
    )

    /** Index and coin names as people say them, for a row to show before or without a price. Proper names. */
    val NAMES: Map<String, String> = linkedMapOf(
        "SPX" to "S&P 500",
        "SP500" to "S&P 500",
        "NDX" to "Nasdaq-100",
        "DJI" to "Dow Jones",
        "DOW" to "Dow Jones",
        "IXIC" to "Nasdaq Composite",
        "NASDAQ" to "Nasdaq Composite",
        "RUT" to "Russell 2000",
        "VIX" to "VIX",
        "DAX" to "DAX 40",
        "FTSE" to "FTSE 100",
        "CAC" to "CAC 40",
        "N225" to "Nikkei 225",
        "NIKKEI" to "Nikkei 225",
        "HSI" to "Hang Seng",
        "BTC" to "Bitcoin",
        "ETH" to "Ethereum",
    )

    /** How many of the host's trending symbols the Trend tab shows. */
    const val TRENDING_SHOWN = 16

    /** The cashtag people would search for, whatever the price host called the symbol: `^GSPC` is `SPX`. */
    fun spokenCashtag(raw: String): String {
        val upper = raw.trim().uppercase()
        val spoken = SPOKEN[upper] ?: upper
        return spoken.removeSuffix("-USD").removePrefix("^")
    }

    /** True when a cashtag could name both a coin and a listed security, so the reader can pick which. */
    fun isAmbiguous(symbol: String): Boolean = symbol.uppercase().trim() in COIN_FIRST

    /**
     * Every name to try for a cashtag, in order. An index name asks for its
     * index and nothing else: `$NDX` falling through to `NDX-USD` once charted
     * a token as the Nasdaq-100.
     */
    fun candidates(symbol: String, prefer: TickerInstrument = TickerInstrument.AUTO): List<String> {
        val upper = symbol.uppercase().trim()
        ALIASES[upper]?.let { return listOf(it) }
        val pair = if (!upper.contains('-') && !upper.startsWith('^')) "$upper-USD" else null
        return when {
            prefer == TickerInstrument.CRYPTO -> listOf(pair ?: upper)
            prefer == TickerInstrument.STOCK -> listOf(upper)
            upper in COIN_FIRST -> listOfNotNull(pair, upper)
            else -> listOfNotNull(upper, pair)
        }
    }

    /** What a symbol is known to be before anything is asked; null when only the price host can tell. */
    fun knownKind(symbol: String): TickerKind? {
        val upper = symbol.uppercase().trim()
        return when {
            upper in ALIASES || upper.startsWith('^') -> TickerKind.MARKET_INDEX
            upper in COIN_FIRST || upper.endsWith("-USD") -> TickerKind.CRYPTO
            else -> null
        }
    }

    /** The name a row shows for [symbol] from [NAMES], also for the host's spelling (`^GSPC`); else null. */
    fun name(symbol: String): String? = NAMES[spokenCashtag(symbol)]

    /** The Trend tab's cashtags from the host's trending symbols: the first [TRENDING_SHOWN], as people write them. */
    fun trendingCashtags(symbols: List<String>): List<String> = symbols.take(TRENDING_SHOWN).map(::spokenCashtag)
}

/**
 * The readings a reader picked for coin-first cashtags, as XTA keeps them in
 * its `other.ticker_instruments` setting: `SOL=stock`, `ETH=crypto`.
 */
object TickerPicks {
    /** The picks in [entries]; an entry that is not `SYMBOL=stock` or `SYMBOL=crypto` is left out. */
    fun decode(entries: List<String>): Map<String, TickerInstrument> = entries.mapNotNull { entry ->
        val parts = entry.split('=')
        val instrument = parts.getOrNull(1)?.let { name -> TickerInstrument.entries.firstOrNull { it.name.lowercase() == name } }
        if (parts.size != 2 || instrument == null || instrument == TickerInstrument.AUTO) null else parts[0].uppercase() to instrument
    }.toMap()

    /** [picks] as the setting's entries, for the store to keep. */
    fun encode(picks: Map<String, TickerInstrument>): List<String> = picks.map { (symbol, instrument) -> "$symbol=${instrument.name.lowercase()}" }
}
