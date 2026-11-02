package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import okhttp3.HttpUrl

/**
 * DexScreener's public endpoints (docs.dexscreener.com/api/reference): no
 * key, no account, no cookies. `/latest/dex/search` finds tokens by name,
 * symbol or contract on every network at once; `/token-pairs/v1/{chain}/{address}`
 * prices one exact contract.
 */
object DexScreener {
    const val HOST = "api.dexscreener.com"

    /** The search for [query], without a leading cashtag `$`; a contract keeps its case. */
    fun searchUrl(query: String): HttpUrl = HttpUrl.Builder().scheme("https").host(HOST)
        .addPathSegments("latest/dex/search")
        .addQueryParameter("q", query.trim().removePrefix("$"))
        .build()

    /** The pools of [asset]'s exact contract on its network, which price it. */
    fun tokenUrl(asset: CryptoAsset): HttpUrl = HttpUrl.Builder().scheme("https").host(HOST)
        .addPathSegments("token-pairs/v1").addPathSegment(asset.chain).addPathSegment(asset.address)
        .build()

    /** The token's pool on DexScreener's site, which "open in browser" leads to. */
    fun pairPageUrl(asset: CryptoAsset, pairAddress: String): HttpUrl = HttpUrl.Builder().scheme("https").host("dexscreener.com")
        .addPathSegment(asset.chain).addPathSegment(pairAddress)
        .build()

    /**
     * One market per token from a search or token-pairs answer (a list, or
     * `{pairs: […]}`), priced by the pairs it is the base token of. Equal
     * symbols on different contracts never merge; the most liquid come first.
     */
    fun markets(json: J): List<CryptoMarket> = bestPerToken(pairsIn(json).mapNotNull { marketFor(it, it["baseToken"], priced = true) })

    /**
     * Every network [address] is a token on, from pairs that name it on either
     * side. As quote currency a token gets its identity but no price: the
     * pair's `priceUsd` is the other token's.
     */
    fun marketsForAddress(json: J, address: String): List<CryptoMarket> {
        fun matches(token: J) = CryptoAddresses.same(token["address"].string.orEmpty(), address)
        return bestPerToken(
            pairsIn(json).mapNotNull { pair ->
                when {
                    matches(pair["baseToken"]) -> marketFor(pair, pair["baseToken"], priced = true)
                    matches(pair["quoteToken"]) -> marketFor(pair, pair["quoteToken"], priced = false)
                    else -> null
                }
            },
        )
    }

    /** [asset]'s market in a token-pairs answer: only a pool it is the base token of prices it. */
    fun marketOf(json: J, asset: CryptoAsset): CryptoMarket? = markets(json).firstOrNull { it.asset.id == asset.id }

    private fun pairsIn(json: J): List<J> = if (json.raw is JsonArray) json.list else json["pairs"].list

    private fun marketFor(pair: J, token: J, priced: Boolean): CryptoMarket? {
        val asset = CryptoAsset.of(pair["chainId"].string, token["address"].string, token["symbol"].string, token["name"].string) ?: return null
        val stats = if (priced) pair else J(null)
        return CryptoMarket(
            asset = asset,
            pairAddress = pair["pairAddress"].string,
            price = finiteFigure(stats["priceUsd"].double, atLeast = 0.0),
            changePercent = finiteFigure(stats["priceChange"]["h24"].double),
            change1h = finiteFigure(stats["priceChange"]["h1"].double),
            change6h = finiteFigure(stats["priceChange"]["h6"].double),
            liquidity = finiteFigure(pair["liquidity"]["usd"].double) ?: 0.0,
            volume24h = finiteFigure(stats["volume"]["h24"].double, atLeast = 0.0),
            marketCap = finiteFigure(stats["marketCap"].double, atLeast = 0.0),
            fdv = finiteFigure(stats["fdv"].double, atLeast = 0.0),
        )
    }

    /** One market per token: a priced one beats an unpriced one, then the deeper pool wins. */
    private fun bestPerToken(markets: List<CryptoMarket>): List<CryptoMarket> {
        val best = LinkedHashMap<String, CryptoMarket>()
        for (market in markets) {
            val current = best[market.asset.id]
            val better = current == null ||
                (market.priced && !current.priced) ||
                (market.priced == current.priced && market.liquidity > current.liquidity)
            if (better) best[market.asset.id] = market
        }
        return best.values.sortedByDescending { it.liquidity }
    }
}
