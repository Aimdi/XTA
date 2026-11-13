package com.aimdi.xtanative.networks.stocks

import java.net.URLDecoder
import java.net.URLEncoder

/** A screen of the Stocks plugin, as its route names it. */
sealed interface StocksRoute {
    /** A ticker's page, as a cashtag opens it: [symbol] without its `$`. */
    data class Ticker(val symbol: String) : StocksRoute

    /** A token followed by its contract. */
    data class Asset(val asset: CryptoAsset) : StocksRoute
}

/** The Stocks plugin's routes: `ticker?symbol=…` and `asset?token=…` (the token as its watchlist row keeps it). */
object StocksRoutes {
    private val symbol = Regex("^[A-Za-z0-9.^=_-]{1,24}$")

    /** [raw]'s ticker page; a cashtag's `$` is left out. Null for what is no symbol at all. */
    fun ticker(raw: String): String? = raw.trim().removePrefix("$").takeIf(symbol::matches)?.let { "ticker?" + query("symbol" to it) }

    fun asset(asset: CryptoAsset): String = "asset?" + query("token" to asset.encode())

    fun parse(route: String): StocksRoute? {
        val values = route.substringAfter('?', "").split('&').filter { '=' in it }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }
        return when (route.substringBefore('?')) {
            "ticker" -> values["symbol"]?.takeIf(symbol::matches)?.let(StocksRoute::Ticker)
            "asset" -> values["token"]?.let(CryptoAsset::decode)?.let(StocksRoute::Asset)
            else -> null
        }
    }

    private fun query(vararg values: Pair<String, String>): String =
        values.joinToString("&") { (key, value) -> "$key=" + URLEncoder.encode(value, Charsets.UTF_8) }
}
