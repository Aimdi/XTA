package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.backup.StockSubscriptionRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonArray

/**
 * The watchlist's rows as XTA's stocks_store.dart writes them, its
 * `stock_subscription` table's and its backup's `stockSubscriptions` rows
 * ([StockSubscriptionRow]): a ticker upper-cased under its lower-case id, a
 * token encoded under its contract id. Kept as the JSON its backup carries,
 * so a restore and an export are the same rows.
 */
object WatchlistRows {
    private val sqlite = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun decode(text: String?): List<StockSubscriptionRow> = J.parse(text.orEmpty()).list.mapNotNull(StockSubscriptionRow::from)

    fun encode(rows: List<StockSubscriptionRow>): String = JsonArray(rows.map(StockSubscriptionRow::toJson)).toString()

    /** The symbols the rows store, for [Watchlist.read]. */
    fun symbols(rows: List<StockSubscriptionRow>): List<String> = rows.map { it.symbol }

    /** [entered] (a ticker as typed, or an encoded token) watched; one already watched, or neither, changes nothing. */
    fun added(rows: List<StockSubscriptionRow>, entered: String, now: Instant, zone: ZoneId = ZoneId.systemDefault()): List<StockSubscriptionRow> {
        val row = Watchlist.row(entered) ?: return rows
        if (rows.any { it.id == row.id }) return rows
        return rows + StockSubscriptionRow(row.id, row.symbol, createdAt = sqlite.format(now.atZone(zone)))
    }

    /** [symbol] (a ticker, or a token's id) no longer watched. */
    fun removed(rows: List<StockSubscriptionRow>, symbol: String): List<StockSubscriptionRow> {
        val id = if (CryptoAsset.contractForId(symbol) == null) symbol.lowercase() else symbol
        return rows.filter { it.id != id }
    }

    /** [asset]'s row rewritten with the reader's choice of searching its cashtag too; the id stays, so groups keep it. */
    fun withCashtag(rows: List<StockSubscriptionRow>, asset: CryptoAsset, include: Boolean): List<StockSubscriptionRow> =
        rows.map { if (it.id == asset.id) it.copy(symbol = asset.withCashtag(include).encode()) else it }

    /** Whether [symbol] is watched, as the ticker page's star asks: a ticker by its upper-cased symbol, a token by its id. */
    fun watches(ids: List<String>, symbol: String): Boolean = symbol.uppercase() in ids || symbol in ids

    /** The order the reader dragged into: the rows' ids with the one at [from] moved to [to]. */
    fun moved(ids: List<String>, from: Int, to: Int): List<String> {
        if (from !in ids.indices) return ids
        val next = ids.toMutableList()
        val row = next.removeAt(from)
        next.add(to.coerceIn(0, next.size), row)
        return next
    }
}
