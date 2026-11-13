package com.aimdi.xtanative.plugins.stocks

import com.aimdi.xtanative.core.feed.FeedMember
import com.aimdi.xtanative.networks.stocks.CryptoAsset
import com.aimdi.xtanative.networks.stocks.CryptoClient
import com.aimdi.xtanative.networks.stocks.CryptoQuoteCache
import com.aimdi.xtanative.networks.stocks.FollowedTickerSource
import com.aimdi.xtanative.networks.stocks.StocksRoutes
import com.aimdi.xtanative.networks.stocks.StocksSearch
import com.aimdi.xtanative.networks.stocks.TickerClient
import com.aimdi.xtanative.networks.stocks.TickerPicks
import com.aimdi.xtanative.networks.stocks.TickerQuoteCache
import com.aimdi.xtanative.store.settings.StocksPrefs
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.observe
import com.aimdi.xtanative.ui.plugins.set
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the Stocks plugin's screens share while it runs, as XTA's providers
 * share it: the price clients, taking the agent `repairs.json` may repair at
 * each request; the quotes the tabs, the ticker page and the cashtags in X's
 * posts all show; the watchlist, kept in the plugin's folder as XTA's
 * backup rows; the posts of followed accounts looked for per ticker; and
 * which signal-group posts the reader folded.
 */
internal class StocksApp(val host: PluginHost) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val agent = { TickerClient.USER_AGENT_SLOT.valueIn(host.repairs.value) }
    val tickers = TickerClient(host.http, userAgent = agent)
    val tokens = CryptoClient(host.http, userAgent = agent)
    val search = StocksSearch(tickers, tokens)
    val quotes = TickerQuoteCache(tickers, onPicks = { picks -> launch { host.settings.set(StocksPrefs.tickerInstruments, TickerPicks.encode(picks)) } })
    val crypto = CryptoQuoteCache(tokens)
    val watchlist = StocksWatchlist(host.store.file(WATCHLIST_FILE), host.settings, scope)
    val followed = FollowedTickerSource(search = { query -> host.x.search(query).threads }, scope = scope)

    /** The chart on a ticker page, and whether signal-group posts arrive folded: the reader's settings. */
    val chart: StateFlow<Boolean> = host.settings.observe(StocksPrefs.tickerChart).stateIn(scope, SharingStarted.Eagerly, StocksPrefs.tickerChart.default)
    val foldSignals: StateFlow<Boolean> = host.settings.observe(StocksPrefs.foldSignalPosts).stateIn(scope, SharingStarted.Eagerly, StocksPrefs.foldSignalPosts.default)

    private val folds = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /** What the reader chose per signal-group post, folded or open; one they did not touch follows the setting. */
    val signalFolds: StateFlow<Map<String, Boolean>> = folds.asStateFlow()

    init {
        scope.launch { host.settings.observe(StocksPrefs.tickerInstruments).collect { quotes.restore(TickerPicks.decode(it)) } }
        launch { watchlist.load() }
    }

    fun close() = scope.cancel()

    /** Runs [block] beyond the screen that asked for it; a failure ends it quietly. */
    fun launch(block: suspend () -> Unit) {
        scope.launch { quietly(block) }
    }

    /** Runs [block]; a failure ends it quietly, as a quote that does not come is shown as such. */
    suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    fun fold(id: String, folded: Boolean) = folds.update { it + (id to folded) }

    fun toggleChart() = launch { host.settings.set(StocksPrefs.tickerChart, !chart.value) }

    fun toggleFoldSignals() = launch { host.settings.set(StocksPrefs.foldSignalPosts, !foldSignals.value) }

    /** The X accounts the reader follows, whose posts about one ticker go first. */
    fun follows(): List<FeedMember> = host.session.value?.following.orEmpty().map { it.member }

    /** A ticker's page, or a token's when the watchlist holds it as one. */
    fun open(symbol: String) {
        val asset = watchlist.entries.assets[symbol]
        if (asset != null) openAsset(asset) else StocksRoutes.ticker(symbol)?.let(host::open)
    }

    fun openAsset(asset: CryptoAsset) = host.open(StocksRoutes.asset(asset))

    private companion object {
        /** The watchlist's rows, as XTA's backup writes them. */
        const val WATCHLIST_FILE = "watchlist.json"
    }
}
