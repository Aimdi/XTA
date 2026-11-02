package com.aimdi.xtanative.networks.stocks

import java.time.Duration

/**
 * What the add sheet's search found for [query]: listed symbols, or tokens,
 * by name or, when a contract was pasted ([address]), that exact contract on
 * each network it exists on.
 */
data class StocksSearchResult(
    val query: String,
    val crypto: Boolean,
    val address: CryptoAddressInput? = null,
    val stocks: List<TickerSearchHit> = emptyList(),
    val tokens: List<CryptoMarket> = emptyList(),
) {
    /** The ticker the reader typed, offered on its own when no hit is that exact symbol; null for tokens or a non-ticker. */
    val typedTicker: String?
        get() = if (crypto) null else Watchlist.normaliseTicker(query)?.takeIf { typed -> stocks.none { it.symbol == typed } }
}

/**
 * The add sheet's search, as XTA's stocks_search_store.dart runs it: symbols
 * on the stocks chip, tokens on the crypto chip, and a pasted contract
 * address switches to tokens on its own (no stock has one).
 */
class StocksSearch(private val tickers: TickerClient, private val tokens: CryptoClient) {
    /** What a search for [query] will be, before it runs: the trimmed query, a pasted address, and whether it reads tokens. */
    fun plan(query: String, crypto: Boolean): StocksSearchResult {
        val trimmed = query.trim()
        val address = CryptoAddresses.detect(trimmed)
        return StocksSearchResult(trimmed, crypto = address != null || crypto, address = address)
    }

    /** The results for [query]; nothing is asked for an empty one. */
    suspend fun run(query: String, crypto: Boolean): StocksSearchResult {
        val planned = plan(query, crypto)
        if (planned.query.isEmpty()) return planned
        val address = planned.address
        return when {
            address != null -> planned.copy(tokens = tokens.resolve(address).items)
            planned.crypto -> planned.copy(tokens = tokens.search(planned.query).items)
            else -> planned.copy(stocks = tickers.searchSymbols(planned.query).items)
        }
    }

    companion object {
        /** How long typing pauses before a search goes out; a pasted address goes at once. */
        val DEBOUNCE: Duration = Duration.ofMillis(300)
    }
}
