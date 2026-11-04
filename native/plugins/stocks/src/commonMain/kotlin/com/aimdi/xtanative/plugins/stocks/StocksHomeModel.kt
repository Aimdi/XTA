package com.aimdi.xtanative.plugins.stocks

import com.aimdi.xtanative.networks.stocks.CryptoAsset
import com.aimdi.xtanative.networks.stocks.Tickers
import com.aimdi.xtanative.ui.plugins.PluginText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** Stocks' four tabs, in XTA's order. */
internal enum class StocksTab(val label: PluginText) {
    WATCHLIST(Strings.watchlist),
    POSTS(Strings.posts),
    TRENDING(Strings.trending),
    MARKETS(Strings.markets),
}

/**
 * What Stocks' home shows: the tab, the ticker each posts tab is filtered
 * to, the trending tickers once asked for, and whether a pull is reading
 * the quotes again.
 */
internal data class StocksView(
    val tab: StocksTab = StocksTab.WATCHLIST,
    val filters: Map<StocksTab, String> = emptyMap(),
    val trending: List<String> = emptyList(),
    val trendingLoading: Boolean = false,
    val trendingFailed: Boolean = false,
    val refreshing: Boolean = false,
) {
    val filter: String? get() = filters[tab]
}

/**
 * Stocks' home as XTA's StocksScreen runs it: the quotes of the tab shown
 * (the caches' two-minute freshness keeps asking again cheap), the coins'
 * readings on the watchlist, the tokens' markets, and the trending tickers,
 * kept as long as the screen is.
 */
internal class StocksHomeModel(private val app: StocksApp, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(StocksView())
    val state: StateFlow<StocksView> = mutable.asStateFlow()

    /** [tab] shown; the screen then asks for its quotes. Trending tickers that did not come are asked for again. */
    fun select(tab: StocksTab) = mutable.update { view ->
        view.copy(tab = tab, trendingFailed = view.trendingFailed && !(tab == StocksTab.TRENDING && view.trending.isEmpty()))
    }

    /** The posts filtered to [symbol], or no longer when they were. */
    fun toggleFilter(symbol: String) = mutable.update { view ->
        val tab = view.tab
        view.copy(filters = if (view.filters[tab] == symbol) view.filters - tab else view.filters + (tab to symbol))
    }

    /** [symbol] left the watchlist: no tab stays filtered to it. */
    fun removed(symbol: String) = mutable.update { view -> view.copy(filters = view.filters.filterValues { it != symbol }) }

    /** The quotes the tab shows, asked again when [force]d, else when they are stale; the trending tickers on their tab, once. */
    suspend fun refreshQuotes(force: Boolean = false) {
        val view = state.value
        if (view.tab == StocksTab.TRENDING && view.trending.isEmpty() && !view.trendingLoading && !view.trendingFailed) loadTrending()
        val entries = app.watchlist.entries
        val symbols = when (view.tab) {
            StocksTab.TRENDING -> state.value.trending
            StocksTab.MARKETS -> Tickers.MARKET_SYMBOLS
            else -> entries.ids
        }
        val stocks = symbols.filter { CryptoAsset.contractForId(it) == null }
        listOfNotNull(
            scope.launch { app.quietly { if (force) app.quotes.refresh(stocks) else app.quotes.ensure(stocks) } },
            if (view.tab == StocksTab.WATCHLIST) scope.launch { app.quietly { app.quotes.ensureReadings(stocks) } } else null,
            if (view.tab <= StocksTab.POSTS) scope.launch { app.quietly { app.crypto.ensure(entries.assets.values, force) } } else null,
        ).joinAll()
    }

    /** A pull on a list: its quotes read again, and on Trending the tickers too. */
    fun pull() {
        if (state.value.refreshing) return
        mutable.update { it.copy(refreshing = true) }
        scope.launch {
            try {
                if (state.value.tab == StocksTab.TRENDING) loadTrending()
                refreshQuotes(force = true)
            } finally {
                mutable.update { it.copy(refreshing = false) }
            }
        }
    }

    /** The trending tickers, asked for again. */
    fun retryTrending() {
        scope.launch {
            loadTrending()
            refreshQuotes()
        }
    }

    /** A row without data asks again for its own quote. */
    fun retry(symbol: String) {
        val asset = app.watchlist.entries.assets[symbol]
        app.launch { if (asset == null) app.quotes.refresh(listOf(symbol)) else app.crypto.ensure(listOf(asset), force = true) }
    }

    private suspend fun loadTrending() {
        if (state.value.trendingLoading) return
        mutable.update { it.copy(trendingLoading = true, trendingFailed = false) }
        val trending = try {
            Tickers.trendingCashtags(app.tickers.fetchTrending().items)
        } catch (e: CancellationException) {
            mutable.update { it.copy(trendingLoading = false) }
            throw e
        } catch (_: Exception) {
            null
        }
        mutable.update { it.copy(trending = trending ?: it.trending, trendingLoading = false, trendingFailed = trending == null) }
    }
}
