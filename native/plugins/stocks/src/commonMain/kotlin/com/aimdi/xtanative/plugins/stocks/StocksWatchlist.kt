package com.aimdi.xtanative.plugins.stocks

import com.aimdi.xtanative.networks.stocks.CryptoAsset
import com.aimdi.xtanative.networks.stocks.Watchlist
import com.aimdi.xtanative.networks.stocks.WatchlistEntries
import com.aimdi.xtanative.networks.stocks.WatchlistRows
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.backup.StockSubscriptionRow
import com.aimdi.xtanative.store.settings.StocksPrefs
import com.aimdi.xtanative.ui.plugins.PluginSettings
import com.aimdi.xtanative.ui.plugins.observe
import com.aimdi.xtanative.ui.plugins.set
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The tickers and tokens the reader watches, as XTA's StocksWatchlistStore
 * keeps them: rows in the plugin's folder, as XTA's backup carries them
 * (`stockSubscriptions`), shown by label in the order the reader dragged
 * them into (StocksPrefs.watchlistOrder, one of the app's settings).
 */
internal class StocksWatchlist(private val file: StoreFile, private val settings: PluginSettings, scope: CoroutineScope) {
    private val rows = MutableStateFlow<List<StockSubscriptionRow>?>(null)
    private val unreadable = MutableStateFlow(false)
    private val turns = Mutex()

    /** The watchlist shown: ids in order, the tokens among them; null until its rows are read. */
    val state: StateFlow<WatchlistEntries?> = combine(rows, settings.observe(StocksPrefs.watchlistOrder)) { stored, order ->
        stored?.let { Watchlist.read(WatchlistRows.symbols(it), order) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Whether the last read of the rows failed; nothing is written over them then. */
    val failed: StateFlow<Boolean> = unreadable.asStateFlow()

    /** What is shown now; empty until read. */
    val entries: WatchlistEntries get() = state.value ?: EMPTY

    suspend fun load(): List<StockSubscriptionRow> = turns.withLock { current() }

    /** [entered], a ticker as typed or a token as encoded, watched. */
    suspend fun add(entered: String) = change { WatchlistRows.added(it, entered, Instant.now()) }

    /** [symbol], a ticker or a token's id, no longer watched. */
    suspend fun remove(symbol: String) = change { WatchlistRows.removed(it, symbol) }

    suspend fun setIncludeCashtag(asset: CryptoAsset, include: Boolean) = change { WatchlistRows.withCashtag(it, asset, include) }

    /** The row at [from] dropped at [to]; the new order is kept with the reader's settings. */
    suspend fun reorder(from: Int, to: Int) = settings.set(StocksPrefs.watchlistOrder, WatchlistRows.moved(entries.ids, from, to))

    /** The rows as XTA's backup writes them. */
    suspend fun export(): String = WatchlistRows.encode(load())

    /** An XTA backup's rows, in place of the ones here. */
    suspend fun import(text: String) = turns.withLock {
        val next = WatchlistRows.decode(text)
        file.write(WatchlistRows.encode(next))
        unreadable.value = false
        rows.value = next
    }

    private suspend fun change(edit: (List<StockSubscriptionRow>) -> List<StockSubscriptionRow>) = turns.withLock {
        val next = edit(current())
        file.write(WatchlistRows.encode(next))
        rows.value = next
    }

    private suspend fun current(): List<StockSubscriptionRow> = rows.value ?: try {
        WatchlistRows.decode(file.read()).also {
            unreadable.value = false
            rows.value = it
        }
    } catch (e: IOException) {
        unreadable.value = true
        throw e
    }

    private companion object {
        val EMPTY = WatchlistEntries(emptyList(), emptyMap())
    }
}
