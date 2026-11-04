package com.aimdi.xtanative.networks.stocks

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

/**
 * The shared last prints: quotes by symbol, the symbols whose last lookup
 * found nothing (a row says "no data" rather than looking like it loads), the
 * listed securities found behind coin-first cashtags, and the readings the
 * reader picked.
 */
data class TickerQuotes(
    val quotes: Map<String, TickerQuote> = emptyMap(),
    val failed: Set<String> = emptySet(),
    val listings: Map<String, TickerQuote> = emptyMap(),
    val picks: Map<String, TickerInstrument> = emptyMap(),
)

/** Tokens' markets by asset id, and the ids whose last lookup found no market. */
data class CryptoQuotes(val markets: Map<String, CryptoMarket> = emptyMap(), val failed: Set<String> = emptySet()) {
    /** The markets as row quotes, to sit beside the tickers' in one list. */
    val quotes: Map<String, TickerQuote> get() = markets.mapValues { it.value.quote }
}

/** When each key was last asked for and which are being asked for now: the freshness both quote caches keep. */
internal class Freshness(private val ttl: Duration, private val now: () -> Instant) {
    private val lock = Any()
    private val fetchedAt = HashMap<String, Instant>()
    private val inflight = HashSet<String>()

    /** Whether [key] should be asked for: not being asked already, and never asked, stale, or [force]d. */
    fun due(key: String, force: Boolean = false): Boolean = synchronized(lock) {
        key !in inflight && (force || fetchedAt[key]?.let { Duration.between(it, now()) >= ttl } ?: true)
    }

    fun begin(key: String): Unit = synchronized(lock) { inflight.add(key) }

    /** [key] is no longer being asked for; [asked] when an answer or a failure came, which starts its freshness. */
    fun end(key: String, asked: Boolean): Unit = synchronized(lock) {
        inflight.remove(key)
        if (asked) fetchedAt[key] = now()
    }

    fun mark(key: String): Unit = synchronized(lock) { fetchedAt[key] = now() }

    fun forget(key: String): Unit = synchronized(lock) { fetchedAt.remove(key) }
}

/**
 * In-memory last prints shared by the stocks tab, the ticker page and the
 * cashtags in posts, as XTA's ticker_quote_cache.dart keeps them: a quote
 * lives [TTL] so a `$AAPL` in the feed shows its move without every strip
 * asking again, at most [MAX_CONCURRENT] lookups at a time.
 *
 * Each entry is a row quote ([TickerClient.rowQuote]): a share's day against
 * the previous close, a coin's last 24 hours. A cashtag that names both a
 * coin and a listed security is read as the coin until the reader picks the
 * other reading ([choose]); [onPicks] hands the picks on to be kept
 * ([TickerPicks.encode]).
 */
class TickerQuoteCache(
    private val client: TickerClient,
    picks: Map<String, TickerInstrument> = emptyMap(),
    private val onPicks: (Map<String, TickerInstrument>) -> Unit = {},
    now: () -> Instant = Instant::now,
) {
    private val freshness = Freshness(TTL, now)
    private val lock = Any()
    private val listingsAsked = HashSet<String>()
    private val state = MutableStateFlow(TickerQuotes(picks = picks.mapKeys { it.key.uppercase() }))

    val quotes: StateFlow<TickerQuotes> = state.asStateFlow()

    /** [symbol]'s quote at hand, without asking for one. */
    fun peek(symbol: String): TickerQuote? = state.value.quotes[symbol.uppercase()]

    /** The reading the reader picked for [symbol]; [TickerInstrument.AUTO] until they pick. */
    fun instrumentFor(symbol: String): TickerInstrument = state.value.picks[symbol.uppercase()] ?: TickerInstrument.AUTO

    /** The listed security an unpicked coin-first cashtag also names, once [ensureReadings] found one. */
    fun listingFor(symbol: String): TickerQuote? = state.value.listings[symbol.uppercase()]

    /** The two readings a watchlist row offers for [symbol], or null ([QuoteRows.readings]). */
    fun readings(symbol: String): List<StocksReading>? = QuoteRows.readings(symbol, listingFor(symbol), instrumentFor(symbol), peek(symbol))

    /** Keeps [instrument] as [symbol]'s reading and asks for it, showing it as loading rather than the other reading's price. */
    suspend fun choose(symbol: String, instrument: TickerInstrument) {
        val key = symbol.uppercase()
        if (instrumentFor(key) == instrument) return
        val picked = state.updateAndGet { current ->
            current.copy(
                picks = if (instrument == TickerInstrument.AUTO) current.picks - key else current.picks + (key to instrument),
                listings = current.listings - key,
                quotes = current.quotes - key,
            )
        }
        onPicks(picked.picks)
        refresh(listOf(key))
    }

    /**
     * The readings kept on the device ([TickerPicks.decode]), once they have
     * been read or after a restore: a symbol whose reading changed is asked
     * for again rather than shown under the other reading's price.
     */
    fun restore(picks: Map<String, TickerInstrument>) {
        val kept = picks.mapKeys { it.key.uppercase() }
        val before = state.value.picks
        if (kept == before) return
        val changed = (kept.keys + before.keys).filter { kept[it] != before[it] }
        state.update { it.copy(picks = kept, quotes = it.quotes - changed.toSet(), listings = it.listings - changed.toSet()) }
        changed.forEach(freshness::forget)
    }

    /**
     * Looks once per symbol for the listed security behind each unpicked
     * coin-first cashtag among [symbols]. Only the watchlist asks: the
     * markets page's coins are coins.
     */
    suspend fun ensureReadings(symbols: Iterable<String>) {
        val keys = symbols.filter { Tickers.isAmbiguous(it) && instrumentFor(it) == TickerInstrument.AUTO }
            .map { it.uppercase() }
            .filter { synchronized(lock) { listingsAsked.add(it) } }
        coroutineScope { keys.map { key -> async { findListing(key) } }.awaitAll() }
    }

    /**
     * Takes a quote read elsewhere: the ticker page's day chart. A coin's day
     * chart runs from midnight, not over 24 hours as its row does, so it is
     * left out.
     */
    fun remember(symbol: String, quote: TickerQuote) {
        if (quote.tradesAroundTheClock && quote.changeWindow != ChangeWindow.ROLLING_24H) return
        val key = symbol.uppercase()
        freshness.mark(key)
        store(key, quote)
    }

    /** Asks for every symbol of [symbols] that is missing or stale, [MAX_CONCURRENT] at a time, so a long list fills in whole. */
    suspend fun ensure(symbols: Iterable<String>) {
        val needed = symbols.map { it.uppercase() }.distinct().filter { freshness.due(it) }
        for (batch in needed.chunked(MAX_CONCURRENT)) {
            coroutineScope { batch.map { key -> async { fetch(key) } }.awaitAll() }
        }
    }

    /** Pull-to-refresh and a row's retry: forget when [symbols] were asked for, and ask again; a row without data shows it loads. */
    suspend fun refresh(symbols: Iterable<String>) {
        val keys = symbols.map { it.uppercase() }
        keys.forEach(freshness::forget)
        state.update { it.copy(failed = it.failed - keys.toSet()) }
        ensure(keys)
    }

    private suspend fun fetch(key: String) {
        freshness.begin(key)
        var asked = false
        try {
            val answer = priceResult { client.rowQuote(key, instrumentFor(key)) }
            asked = true
            answer.onSuccess { store(key, it) }.onFailure { state.update { current -> current.copy(failed = current.failed + key) } }
        } finally {
            freshness.end(key, asked)
        }
    }

    private suspend fun findListing(key: String) {
        val listing = priceResult { client.fetchChart(key, TickerRange.DAY, TickerInstrument.STOCK) }.getOrNull() ?: return
        if (listing.tradesAroundTheClock || instrumentFor(key) != TickerInstrument.AUTO) return
        state.update { it.copy(listings = it.listings + (key to listing)) }
    }

    private fun store(key: String, quote: TickerQuote) {
        state.update { it.copy(quotes = it.quotes + (key to quote), failed = it.failed - key) }
    }

    companion object {
        /** How long a quote is fresh. */
        val TTL: Duration = Duration.ofMinutes(2)

        /** How many lookups run at a time. */
        const val MAX_CONCURRENT = 4
    }
}

/**
 * Watched tokens' markets by asset id, as XTA's crypto_quote_store.dart keeps
 * them: fresh for [TickerQuoteCache.TTL], [TickerQuoteCache.MAX_CONCURRENT]
 * at a time. A token whose lookup found no market is in [CryptoQuotes.failed],
 * so its row says so; a failed lookup never replaces a market with another.
 */
class CryptoQuoteCache(private val client: CryptoClient, now: () -> Instant = Instant::now) {
    private val freshness = Freshness(TickerQuoteCache.TTL, now)
    private val state = MutableStateFlow(CryptoQuotes())

    val markets: StateFlow<CryptoQuotes> = state.asStateFlow()

    /** Asks for each of [assets] that is missing or stale; [force] (pull-to-refresh, a retry) asks again and shows them loading. */
    suspend fun ensure(assets: Iterable<CryptoAsset>, force: Boolean = false) {
        val all = assets.distinctBy { it.id }
        if (force) state.update { it.copy(failed = it.failed - all.map(CryptoAsset::id).toSet()) }
        val needed = all.filter { freshness.due(it.id, force) }
        for (batch in needed.chunked(TickerQuoteCache.MAX_CONCURRENT)) {
            coroutineScope { batch.map { asset -> async { fetch(asset) } }.awaitAll() }
        }
    }

    private suspend fun fetch(asset: CryptoAsset) {
        freshness.begin(asset.id)
        var asked = false
        try {
            val market = priceResult { client.fetch(asset) }.also { asked = true }.getOrNull()
            state.update { current ->
                if (market == null) {
                    current.copy(failed = current.failed + asset.id)
                } else {
                    current.copy(markets = current.markets + (asset.id to market), failed = current.failed - asset.id)
                }
            }
        } finally {
            freshness.end(asset.id, asked)
        }
    }
}
