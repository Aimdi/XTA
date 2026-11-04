package com.aimdi.xtanative.plugins.stocks

import com.aimdi.xtanative.ui.post.PostIcons
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.stocks.CryptoAsset
import com.aimdi.xtanative.networks.stocks.CryptoMarket
import com.aimdi.xtanative.networks.stocks.DexScreener
import com.aimdi.xtanative.networks.stocks.StockNumbers
import com.aimdi.xtanative.ui.common.rememberCopyContract
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginNotices
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A token's market as its page shows it, and whether its posts also search its cashtag. */
internal data class CryptoDetail(val market: CryptoMarket? = null, val loading: Boolean = false, val failed: Boolean = false, val includeCashtag: Boolean = false)

/** A token's page as XTA's CryptoDetailStore reads it: its market from the DEX hosts, kept through a failed read. */
internal class CryptoAssetModel(private val app: StocksApp, val asset: CryptoAsset, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(CryptoDetail(includeCashtag = asset.includeCashtag))
    val state: StateFlow<CryptoDetail> = mutable.asStateFlow()
    private var started = false

    fun start() {
        if (started) return
        started = true
        load()
    }

    fun load() {
        if (state.value.loading) return
        mutable.update { it.copy(loading = true, failed = false) }
        scope.launch {
            val market = try {
                app.tokens.fetch(asset)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            mutable.update { it.copy(market = market ?: it.market, loading = false, failed = market == null) }
        }
    }

    /** The choice is the token's, so a watched token remembers it. */
    fun setIncludeCashtag(include: Boolean) {
        mutable.update { it.copy(includeCashtag = include) }
        if (asset.id in app.watchlist.entries.ids) app.launch { app.watchlist.setIncludeCashtag(asset, include) }
    }
}

/**
 * A token followed by its contract (crypto_asset_screen.dart): its price and
 * pool figures from DEX Screener, then X's posts that name the contract, and
 * its cashtag too when the reader asks for it.
 */
@Composable
internal fun CryptoAssetScreen(app: StocksApp, asset: CryptoAsset) {
    val model = rememberKept("asset:" + asset.id) { CryptoAssetModel(app, asset, it) }
    val state by model.state.collectAsState()
    val entries by app.watchlist.state.collectAsState()
    val watched = entries?.ids?.contains(asset.id) == true
    val notices = remember { PluginNotices() }
    val shown = asset.withCashtag(state.includeCashtag)
    val feed = rememberKept("asset-posts:" + shown.searchQuery) { StocksPostsFeed(app, shown.searchQuery, signals = true, followedFirst = true, scope = it) }
    LaunchedEffect(model) { model.start() }
    PluginScaffold(
        asset.label,
        onBack = app.host::back,
        actions = { WatchButton(watched) { app.launch { if (watched) app.watchlist.remove(asset.id) else app.watchlist.add(shown.encode()) } } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            StocksPostsList(app, feed, PaddingValues(), toTop = 0, onRefresh = model::load) {
                item(key = "asset-header") { CryptoHeader(shown, state, model, notices::tell) }
            }
            NoticeHost(notices)
        }
    }
}

/** Name, contract, price, pool figures and the posts choice above a token's posts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CryptoHeader(asset: CryptoAsset, state: CryptoDetail, model: CryptoAssetModel, say: (String) -> Unit) {
    val copy = rememberCopyContract(say)
    val outside = rememberOpenOutside()
    val pair = state.market?.pairAddress
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(2.dp)) { if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp)) {
            Text(asset.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            ContractLine(asset) { copy(asset.address) }
            if (state.failed && state.market == null) Unavailable(model::load)
            CryptoPrice(state.market)
            StatGrid(cryptoStats(state.market), Modifier.padding(vertical = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copy(asset.address) }) {
                    Icon(StocksIcons.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(pluginString(Strings.crypto_copy_contract))
                }
                OutlinedButton(onClick = { if (pair != null) outside(DexScreener.pairPageUrl(asset, pair).toString()) }, enabled = pair != null) {
                    Icon(PostIcons.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(pluginString(Strings.open_in_browser))
                }
            }
            PostsChoice(asset, state.includeCashtag, model::setIncludeCashtag)
        }
    }
}

/** `Ethereum · 0x6982…1933`: a tap or a long press copies the whole address. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContractLine(asset: CryptoAsset, onCopy: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val label = listOf(pluginString(Strings.crypto_contract), asset.chainName, asset.address).joinToString(", ")
    val hint = pluginString(Strings.crypto_copy_contract)
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .combinedClickable(onClick = onCopy, onLongClick = onCopy)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                onClick(hint) { onCopy(); true }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(asset.subtitle, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium, color = muted)
        Spacer(Modifier.width(6.dp))
        Icon(StocksIcons.ContentCopy, null, Modifier.size(16.dp), tint = muted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CryptoPrice(market: CryptoMarket?) {
    val numbers = numberSymbols()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val price = market?.price
    Row(verticalAlignment = Alignment.Bottom) {
        val spoken = if (price == null) Modifier else Modifier.clearAndSetSemantics { contentDescription = StockNumbers.priceSpoken(price, numbers) }
        Text(price?.let { StockNumbers.price(it, numbers) } ?: StockNumbers.PLACEHOLDER, spoken, style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = TABULAR), fontWeight = FontWeight.ExtraBold)
        Text("USD", Modifier.padding(start = 6.dp, bottom = 4.dp), style = MaterialTheme.typography.bodyMedium, color = muted)
    }
    market?.changePercent?.let { percent ->
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
            Text(StockNumbers.percent(percent, numbers), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = trendColor(percent))
            Text(pluginString(Strings.change_hours, 24), style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

/** The figures DEX pages put under a token: size, depth, turnover, and the shorter moves the day's figure hides. */
@Composable
private fun cryptoStats(market: CryptoMarket?): List<Pair<String, String?>> {
    val numbers = numberSymbols()
    fun usd(value: Double?) = value?.let { StockNumbers.compactUsd(it, numbers) }
    fun percent(value: Double?) = value?.let { StockNumbers.percent(it, numbers) }
    return listOf(
        pluginString(Strings.market_cap) to usd(market?.marketCap),
        pluginString(Strings.fdv) to usd(market?.fdv),
        pluginString(Strings.liquidity) to usd(market?.liquidity?.takeIf { it != 0.0 }),
        pluginString(Strings.volume_24h) to usd(market?.volume24h),
        pluginString(Strings.change_hours, 1) to percent(market?.change1h),
        pluginString(Strings.change_hours, 6) to percent(market?.change6h),
    )
}

/** Contract-only by default; the cashtag is a tap away, with the caveat that other tokens can share it beside the switch. */
@Composable
private fun PostsChoice(asset: CryptoAsset, includeCashtag: Boolean, onInclude: (Boolean) -> Unit) {
    val cashtag = asset.cashtag
    Column {
        if (cashtag != null) {
            FilterChip(selected = includeCashtag, onClick = { onInclude(!includeCashtag) }, label = { Text(pluginString(Strings.crypto_include_cashtag, cashtag)) })
        }
        val hint = if (includeCashtag && cashtag != null) pluginString(Strings.crypto_posts_hint_cashtag, cashtag) else pluginString(Strings.crypto_posts_hint)
        Text(hint, style = MaterialTheme.typography.bodySmall)
    }
}
