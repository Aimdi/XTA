package com.aimdi.xtanative.plugins.stocks

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.networks.stocks.StocksRoute
import com.aimdi.xtanative.networks.stocks.StocksRoutes
import com.aimdi.xtanative.networks.stocks.WatchlistRows
import com.aimdi.xtanative.store.backup.BackupKeys
import com.aimdi.xtanative.ui.plugins.PluginBackupSection
import com.aimdi.xtanative.ui.plugins.PluginBody
import com.aimdi.xtanative.ui.plugins.PluginCashtagAction
import com.aimdi.xtanative.ui.plugins.PluginFootprint
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginScreen
import com.aimdi.xtanative.ui.plugins.XtaPlugin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Stocks as a downloadable plugin, as XTA's Stocks plugin reads the markets:
 * a watchlist of tickers and tokens with their quotes, X's posts about them,
 * the trending tickers and the markets, each ticker's and token's page, and
 * the price strip under an X post that names tickers, whose cashtags open
 * the ticker's page and, held, offer it to the watchlist, and the network a
 * watched token's contract address is on, where an X post names it.
 * Read-only: quotes are shown, nothing is traded, no account is needed, and
 * the watchlist stays on the device.
 */
class StocksPlugin : XtaPlugin() {
    private lateinit var app: StocksApp

    override val id: String = "stocks"

    /** StockTwits green, the colour of a rising tape on that app. */
    override val brandColor: Color = Color(0xFF00C805)
    override val icon: ImageVector get() = StocksIcons.CandlestickChart

    /** Green is a rising price's, not Stocks' chrome: its mark on Home follows the text colour (XTA's brandsChrome). */
    override val brandsChrome: Boolean get() = false
    override val category: Category = Category.MARKETS

    override fun title(language: String): String = Strings.plugin_name.get(language)

    override fun description(language: String): String = Strings.plugin_description.get(language)

    override fun attach(host: PluginHost) {
        app = StocksApp(host)
    }

    override fun detach() = app.close()

    override val homeTab: PluginBody = { frame -> StocksHome(app, frame) }

    override fun screen(route: String): PluginScreen? = when (val opened = StocksRoutes.parse(route)) {
        is StocksRoute.Ticker -> tickerScreen(opened.symbol)
        is StocksRoute.Asset -> assetScreen(opened)
        null -> null
    }

    private fun tickerScreen(symbol: String): PluginScreen = { TickerScreen(app, symbol) }

    private fun assetScreen(route: StocksRoute.Asset): PluginScreen = { CryptoAssetScreen(app, route.asset) }

    override val cashtagStrip: @Composable (List<String>) -> Unit = { symbols -> CashtagStrip(app, symbols) }

    override fun routeForCashtag(symbol: String): String? = StocksRoutes.ticker(symbol)

    /** Add to watchlist, or Remove from watchlist for one watched, as XTA's cashtag sheet offers while Stocks runs. */
    override fun cashtagActions(symbol: String): List<PluginCashtagAction> {
        val watched = WatchlistRows.watches(app.watchlist.entries.ids, symbol)
        return listOf(
            PluginCashtagAction(if (watched) Icons.Filled.Star else Icons.Outlined.Star, { language -> (if (watched) Strings.unwatch else Strings.watch).get(language) }) {
                if (watched) app.watchlist.remove(symbol) else app.watchlist.add(symbol)
            },
        )
    }

    /** The network of each watched token by its contract, which an X post writes before the address, as XTA's does. */
    override val contractNetworks: Flow<Map<String, String>>
        get() = app.watchlist.state.map { it?.contractNetworks.orEmpty() }

    /** The watchlist as XTA's backup holds it: its `stockSubscriptions` rows. Its order and the rest are in the app's settings. */
    override val backupSections: List<PluginBackupSection>
        get() = listOf(PluginBackupSection(BackupKeys.STOCK_SUBSCRIPTIONS, export = { app.watchlist.export() }, import = { app.watchlist.import(it) }))

    override suspend fun footprint(): PluginFootprint = PluginFootprint(app.watchlist.load().size)
}
