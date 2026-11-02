package com.aimdi.xtanative.networks.stocks

/** A watchlist read from its stored rows: the ids in the order shown, and the tokens among them by id. */
data class WatchlistEntries(val ids: List<String>, val assets: Map<String, CryptoAsset>) {
    /** What the manage sheet calls an entry: a token's [CryptoAsset.label], else the cashtag. */
    fun label(id: String): String = assets[id]?.label ?: "$$id"
}

/** How XTA stores a watchlist entry (`stock_subscription`): a lower-case id, and the ticker or the encoded token. */
data class WatchlistRow(val id: String, val symbol: String)

/**
 * The watchlist's reading rules, as XTA's stocks_store.dart and
 * stocks_watchlist_query.dart apply them: what counts as a ticker, the order
 * rows are shown in, and the X searches for their posts. Storing the rows is
 * the store's.
 */
object Watchlist {
    /** How many terms one posts search takes: X truncates very long OR groups. */
    const val FEED_SYMBOL_CAP = 20

    private val tickerPattern = Regex("^[A-Za-z0-9.^-]{1,10}$")

    /** The ticker in what a reader typed (`aapl`, `$AAPL`, `BRK.B`, `^GSPC`), upper-cased; null when it is not one. */
    fun normaliseTicker(raw: String): String? {
        val text = raw.trim().let { if (it.startsWith('$')) it.substring(1).trim() else it }
        return if (tickerPattern.matches(text)) text.uppercase() else null
    }

    /** The row [entered] (a ticker, or a token as [CryptoAsset.encode] wrote it) is stored as; null for neither. */
    fun row(entered: String): WatchlistRow? {
        val asset = CryptoAsset.decode(entered)
        val symbol = asset?.encode() ?: normaliseTicker(entered) ?: return null
        return WatchlistRow(asset?.id ?: symbol.lowercase(), symbol)
    }

    /**
     * The watchlist in its stored rows' [stored] symbols: tokens by their id,
     * tickers as stored, sorted by label, then in the reader's [order].
     */
    fun read(stored: List<String>, order: List<String>?): WatchlistEntries {
        val assets = stored.mapNotNull(CryptoAsset::decode).associateBy { it.id }
        val ids = stored.map { CryptoAsset.decode(it)?.id ?: it }
        val labelled = WatchlistEntries(ids, assets)
        return WatchlistEntries(ordered(ids.sortedBy(labelled::label), order), assets)
    }

    /**
     * [symbols] in the reader's dragged [order], those it does not name after
     * them in the order they came: an id that left the watchlist is skipped,
     * a new one joins at the end.
     */
    fun ordered(symbols: List<String>, order: List<String>?): List<String> {
        if (order.isNullOrEmpty()) return symbols
        val present = symbols.toSet()
        val arranged = order.filter { it in present }
        val placed = arranged.toSet()
        return arranged + symbols.filter { it !in placed }
    }

    /**
     * The X search for the posts about [symbols]: `($AAPL OR $TSLA)`, a single
     * `$AAPL`, or empty for none. A token is searched by its quoted contract,
     * plus its cashtag when the reader opted in on [assets]. At most
     * [FEED_SYMBOL_CAP] terms, each once.
     */
    fun postsQuery(symbols: Iterable<String>, assets: Map<String, CryptoAsset> = emptyMap()): String {
        val terms = LinkedHashSet<String>()
        for (raw in symbols) {
            val symbol = raw.trim()
            if (symbol.isEmpty()) continue
            terms.addAll(termsFor(symbol, assets[symbol]))
            if (terms.size >= FEED_SYMBOL_CAP) break
        }
        return when (terms.size) {
            0 -> ""
            1 -> terms.single()
            else -> terms.joinToString(" OR ", "(", ")")
        }
    }

    /** A ticker page's posts search: the cashtag people write (`$SPX`, `$BTC`), not the host's name for it. */
    fun tickerPostsQuery(symbol: String): String = "$${Tickers.spokenCashtag(symbol)}"

    private fun termsFor(symbol: String, asset: CryptoAsset?): List<String> {
        if (asset != null) return asset.searchTerms
        val contract = CryptoAsset.contractForId(symbol)
        return listOf(if (contract == null) "$${Tickers.spokenCashtag(symbol)}" else "\"$contract\"")
    }
}
