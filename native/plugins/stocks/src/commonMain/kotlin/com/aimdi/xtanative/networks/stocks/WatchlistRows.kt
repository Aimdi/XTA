package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A watched ticker or token as XTA's `stock_subscription` table keeps it, and its backup's `stockSubscriptions` rows. */
data class WatchlistEntryRow(val id: String, val symbol: String, val inFeed: Boolean = true, val createdAt: String? = null)

/**
 * The watchlist's rows as XTA's stocks_store.dart writes them: a ticker
 * upper-cased under its lower-case id, a token encoded under its contract
 * id. Kept as the JSON its backup carries, so a restore and an export are
 * the same rows.
 */
object WatchlistRows {
    private val sqlite = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun decode(text: String?): List<WatchlistEntryRow> = J.parse(text.orEmpty()).list.mapNotNull { row ->
        if (row.raw !is JsonObject) return@mapNotNull null
        WatchlistEntryRow(
            id = row["id"].string ?: return@mapNotNull null,
            symbol = row["symbol"].string ?: return@mapNotNull null,
            inFeed = row["in_feed"].long?.let { it == 1L } ?: row["in_feed"].boolean ?: true,
            createdAt = row["created_at"].string,
        )
    }

    fun encode(rows: List<WatchlistEntryRow>): String = JsonArray(
        rows.map { row ->
            buildJsonObject {
                put("id", row.id)
                put("symbol", row.symbol)
                put("in_feed", if (row.inFeed) 1 else 0)
                put("created_at", row.createdAt)
            }
        },
    ).toString()

    /** The symbols the rows store, for [Watchlist.read]. */
    fun symbols(rows: List<WatchlistEntryRow>): List<String> = rows.map { it.symbol }

    /** [entered] (a ticker as typed, or an encoded token) watched; one already watched, or neither, changes nothing. */
    fun added(rows: List<WatchlistEntryRow>, entered: String, now: Instant, zone: ZoneId = ZoneId.systemDefault()): List<WatchlistEntryRow> {
        val row = Watchlist.row(entered) ?: return rows
        if (rows.any { it.id == row.id }) return rows
        return rows + WatchlistEntryRow(row.id, row.symbol, createdAt = sqlite.format(now.atZone(zone)))
    }

    /** [symbol] (a ticker, or a token's id) no longer watched. */
    fun removed(rows: List<WatchlistEntryRow>, symbol: String): List<WatchlistEntryRow> {
        val id = if (CryptoAsset.contractForId(symbol) == null) symbol.lowercase() else symbol
        return rows.filter { it.id != id }
    }

    /** [asset]'s row rewritten with the reader's choice of searching its cashtag too; the id stays, so groups keep it. */
    fun withCashtag(rows: List<WatchlistEntryRow>, asset: CryptoAsset, include: Boolean): List<WatchlistEntryRow> =
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
