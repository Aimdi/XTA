package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.CryptoAddresses
import okhttp3.HttpUrl

/**
 * GeckoTerminal's public pools API (no key, no account), asked for a token's
 * price when DexScreener refuses, rests or has no market for it.
 */
object GeckoTerminal {
    const val HOST = "api.geckoterminal.com"

    /** GeckoTerminal's own ids for the networks DexScreener spells otherwise. */
    val NETWORKS: Map<String, String> = mapOf(
        "ethereum" to "eth",
        "polygon" to "polygon_pos",
        "avalanche" to "avax",
        "fantom" to "ftm",
        "cronos" to "cro",
        "sui" to "sui-network",
        "polygonzkevm" to "polygon-zkevm",
    )

    /** The network [chain] (as DexScreener spells it) is at GeckoTerminal. */
    fun network(chain: String): String = chain.lowercase().let { NETWORKS[it] ?: it }

    /** The pools [asset] trades in, the deepest and busiest first. */
    fun poolsUrl(asset: CryptoAsset): HttpUrl = HttpUrl.Builder().scheme("https").host(HOST)
        .addPathSegments("api/v2/networks").addPathSegment(network(asset.chain))
        .addPathSegment("tokens").addPathSegment(asset.address).addPathSegment("pools")
        .addQueryParameter("page", "1")
        .build()

    /**
     * [asset]'s market from its pools: the first one it is the base token of,
     * whose figures are its own (a pool quoting it prices the other token).
     * Null when no pool is, or that pool has no price.
     */
    fun market(json: J, asset: CryptoAsset): CryptoMarket? {
        val pool = json["data"].list.firstOrNull { isBase(it, asset) } ?: return null
        val figures = pool["attributes"]
        val price = finiteFigure(figures["base_token_price_usd"].double, atLeast = 0.0) ?: return null
        val moves = figures["price_change_percentage"]
        return CryptoMarket(
            asset = asset,
            pairAddress = figures["address"].string,
            price = price,
            changePercent = finiteFigure(moves["h24"].double),
            change1h = finiteFigure(moves["h1"].double),
            change6h = finiteFigure(moves["h6"].double),
            liquidity = finiteFigure(figures["reserve_in_usd"].double) ?: 0.0,
            volume24h = finiteFigure(figures["volume_usd"]["h24"].double, atLeast = 0.0),
            marketCap = finiteFigure(figures["market_cap_usd"].double, atLeast = 0.0),
            fdv = finiteFigure(figures["fdv_usd"].double, atLeast = 0.0),
        )
    }

    /** A base token's id is `{network}_{address}`. */
    private fun isBase(pool: J, asset: CryptoAsset): Boolean {
        val id = pool["relationships"]["base_token"]["data"]["id"].string ?: return false
        val prefix = "${network(asset.chain)}_"
        return id.startsWith(prefix) && CryptoAddresses.same(id.removePrefix(prefix), asset.address)
    }
}
