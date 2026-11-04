package com.aimdi.xtanative.plugins.stocks

import com.aimdi.xtanative.ui.common.swipeTabs
import com.aimdi.xtanative.ui.post.PostIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.networks.stocks.TickerQuote
import com.aimdi.xtanative.networks.stocks.Watchlist
import com.aimdi.xtanative.networks.stocks.WatchlistEntries
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.NetworkProblemView
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginNotices
import com.aimdi.xtanative.ui.plugins.PluginTabs
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import kotlinx.coroutines.delay

/** While a tab is on screen its quotes are asked for this often, the caches answering what is fresh. */
private const val REFRESH_EVERY_MS = 60_000L

/**
 * Stocks' home tab, as XTA's StocksScreen: Watchlist (the quotes, in the
 * reader's order), Posts (X's posts about the watched tickers, one ticker
 * at a time from the strip above them), Trending (the tickers trending on
 * Yahoo and their posts) and Markets (the indices and coins), with Add
 * ticker and the settings beside them. Read-only: quotes are shown, nothing
 * is traded, and the watchlist stays on this device.
 */
@Composable
internal fun StocksHome(app: StocksApp, frame: PluginFrame) {
    val model = rememberKept("stocks-home") { StocksHomeModel(app, it) }
    val view by model.state.collectAsState()
    val entries by app.watchlist.state.collectAsState()
    val unreadable by app.watchlist.failed.collectAsState()
    var adding by rememberSaveable { mutableStateOf(false) }
    var managing by rememberSaveable { mutableStateOf(false) }
    val notices = remember { PluginNotices() }
    LaunchedEffect(model, view.tab, entries?.ids, view.trending) {
        while (true) {
            model.refreshQuotes()
            delay(REFRESH_EVERY_MS)
        }
    }
    RefreshesOn(frame.refresh) { model.pull() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            StocksHeader(app, view.tab, model::select, onAdd = { adding = true }, onManage = { managing = true })
            Box(Modifier.weight(1f).swipeTabs(StocksTab.entries, view.tab) { model.select(it) }) {
                val shown = entries
                when {
                    shown == null && unreadable -> NetworkProblemView(Problem.Other, pluginString(Strings.watchlist), { app.launch { app.watchlist.load() } })
                    shown == null -> LoadingRow()
                    else -> StocksTabBody(app, model, view, shown, frame) { adding = true }
                }
            }
        }
        NoticeHost(notices, Modifier.padding(frame.padding))
    }
    if (adding) {
        StocksAddSheet(app, onDismiss = { adding = false }) { entered ->
            adding = false
            app.launch { app.watchlist.add(entered) }
        }
    }
    if (managing) StocksManageSheet(app, onDismiss = { managing = false }, onRemoved = model::removed, say = notices::tell)
}

@Composable
private fun StocksTabBody(app: StocksApp, model: StocksHomeModel, view: StocksView, entries: WatchlistEntries, frame: PluginFrame, onAdd: () -> Unit) {
    val tickers by app.quotes.quotes.collectAsState()
    val crypto by app.crypto.markets.collectAsState()
    val quotes = remember(tickers, crypto) { tickers.quotes + crypto.quotes }
    val failed = remember(tickers, crypto) { tickers.failed + crypto.failed }
    val trending = view.trending
    when {
        view.tab == StocksTab.MARKETS -> StocksMarketsList(quotes, failed, app::open, model::retry, model::pull, view.refreshing, frame.padding, frame.toTop)
        view.tab == StocksTab.TRENDING && trending.isEmpty() && view.trendingLoading -> LoadingRow()
        view.tab == StocksTab.TRENDING && trending.isEmpty() -> EmptyStocks(app, model, view, frame, onAdd)
        view.tab == StocksTab.TRENDING -> PostsHome(app, model, view, trending, entries, quotes, failed, frame)
        entries.ids.isEmpty() -> EmptyStocks(app, model, view, frame, onAdd)
        view.tab == StocksTab.POSTS -> PostsHome(app, model, view, entries.ids, entries, quotes, failed, frame)
        else -> StocksWatchlistList(app, entries, tickers, quotes, failed, onAdd, model::retry, model::pull, view.refreshing, frame)
    }
}

/** The strip of [symbols] over "Posts" and the ticker they are filtered to, over the posts about them. */
@Composable
private fun PostsHome(
    app: StocksApp,
    model: StocksHomeModel,
    view: StocksView,
    symbols: List<String>,
    entries: WatchlistEntries,
    quotes: Map<String, TickerQuote>,
    failed: Set<String>,
    frame: PluginFrame,
) {
    val filter = view.filter
    val query = Watchlist.postsQuery(if (filter == null) symbols else listOf(filter), entries.assets)
    // One ticker's posts, also when the watchlist holds only that one, put the followed accounts' first.
    val followedFirst = filter != null || symbols.size == 1
    val feed = rememberKept("stocks-posts:$followedFirst:$query") { StocksPostsFeed(app, query, signals = true, followedFirst = followedFirst, scope = it) }
    Column(Modifier.fillMaxSize()) {
        StocksReel(symbols, entries.assets, quotes, failed, filter, model::toggleFilter, app::open)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(pluginString(Strings.posts), style = MaterialTheme.typography.titleMedium)
            if (filter != null) {
                Spacer(Modifier.width(8.dp))
                InputChip(
                    selected = true,
                    onClick = { model.toggleFilter(filter) },
                    label = { Text(entries.label(filter), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = { Icon(Icons.Default.Close, pluginString(Strings.reset_filters), Modifier.size(18.dp)) },
                )
            }
        }
        Hairline()
        Box(Modifier.weight(1f)) {
            StocksPostsList(app, feed, frame.padding, frame.toTop, onRefresh = model::pull)
        }
    }
}

/**
 * No ticker yet, or no trending one: what to do about it, and while the
 * watchlist is empty the trending tickers known, to add with a tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyStocks(app: StocksApp, model: StocksHomeModel, view: StocksView, frame: PluginFrame, onAdd: () -> Unit) {
    val trending = view.tab == StocksTab.TRENDING
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val message = when {
        !trending -> Strings.empty
        view.trendingFailed -> Strings.data_unavailable
        else -> Strings.trending_empty
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 24.dp + frame.padding.calculateBottomPadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Icon(if (trending) PostIcons.TrendingUp else StocksIcons.ShowChart, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline) }
        item { Text(pluginString(message), Modifier.padding(top = 16.dp), textAlign = TextAlign.Center) }
        if (view.tab != StocksTab.WATCHLIST) {
            item { Text(pluginString(Strings.feed_hint), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = muted, textAlign = TextAlign.Center) }
        }
        item {
            Button(onClick = if (trending) model::retryTrending else onAdd, modifier = Modifier.padding(top = 16.dp)) {
                Icon(if (trending) Icons.Default.Refresh else Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(pluginString(if (trending) Strings.retry else Strings.add))
            }
        }
        if (!trending && view.trending.isNotEmpty()) {
            item { Text(pluginString(Strings.trending), Modifier.padding(top = 24.dp, bottom = 8.dp), textAlign = TextAlign.Center) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    view.trending.take(8).forEach { symbol ->
                        AssistChip(onClick = { app.launch { app.watchlist.add(symbol) } }, label = { Text("$$symbol") })
                    }
                }
            }
        }
    }
}

/** The four tabs, with Add ticker and the settings at their end. */
@Composable
private fun StocksHeader(app: StocksApp, selected: StocksTab, onSelect: (StocksTab) -> Unit, onAdd: () -> Unit, onManage: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PluginTabs(StocksTab.entries.map { pluginString(it.label) }, selected.ordinal, { onSelect(StocksTab.entries[it]) }, Modifier.weight(1f))
        IconButton(onClick = onAdd) { Icon(Icons.Default.Add, pluginString(Strings.add)) }
        StocksMenu(app, onManage)
    }
}

/** The sliders: the watchlist itself, and how Stocks shows things. */
@Composable
private fun StocksMenu(app: StocksApp, onManage: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val chart by app.chart.collectAsState()
    val fold by app.foldSignals.collectAsState()
    Box {
        IconButton(onClick = { open = true }) { Icon(PostIcons.Tune, pluginString(Strings.settings_title)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(pluginString(Strings.manage_watchlist)) },
                onClick = {
                    open = false
                    onManage()
                },
                leadingIcon = { Spacer(Modifier.size(24.dp)) },
            )
            CheckedItem(pluginString(Strings.ticker_chart), chart) {
                open = false
                app.toggleChart()
            }
            CheckedItem(pluginString(Strings.fold_signal_posts), fold) {
                open = false
                app.toggleFoldSignals()
            }
        }
    }
}

@Composable
private fun CheckedItem(text: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = { if (checked) Icon(Icons.Default.Check, null) else Spacer(Modifier.size(24.dp)) },
        modifier = Modifier.semantics {
            role = Role.Checkbox
            toggleableState = ToggleableState(checked)
        },
    )
}
