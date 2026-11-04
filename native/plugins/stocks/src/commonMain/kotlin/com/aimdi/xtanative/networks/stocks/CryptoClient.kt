package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.Page
import java.time.Duration
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl

/**
 * Tokens by name or contract and their prices, as XTA's crypto_client.dart
 * reads them: DexScreener first, GeckoTerminal when DexScreener refuses,
 * rests or has no market for the token. A host that refuses rests as the
 * coin exchanges do ([PriceSourceHealth]).
 */
class CryptoClient(
    private val http: XHttp,
    private val health: PriceSourceHealth = PriceSourceHealth.shared,
    private val userAgent: () -> String = { TickerClient.DEFAULT_USER_AGENT },
    private val timeout: Duration = PriceHttp.TIMEOUT,
) {
    /** Tokens whose name, symbol or contract matches [query], on every network; DexScreener has no further pages. */
    suspend fun search(query: String): Page<CryptoMarket> = Page(DexScreener.markets(get(DexScreener.searchUrl(query), PriceHosts.DEX_SCREENER)), null)

    /**
     * Every network a pasted contract is a token on (at most [MAX_NETWORKS]),
     * with its price. A network where the token only showed up as quote
     * currency is priced through its own pools, all at once.
     */
    suspend fun resolve(input: CryptoAddressInput): Page<CryptoMarket> = coroutineScope {
        val found = DexScreener.marketsForAddress(get(DexScreener.searchUrl(input.address), PriceHosts.DEX_SCREENER), input.address)
        Page(found.take(MAX_NETWORKS).map { market -> async { priced(market) } }.awaitAll(), null)
    }

    /**
     * [asset]'s market from DexScreener, else from GeckoTerminal; null when
     * neither has one. When neither could say (both refused or resting) it
     * fails with [TickerErrorKind.UNAVAILABLE].
     */
    suspend fun fetch(asset: CryptoAsset): CryptoMarket? {
        val dex = ask(PriceHosts.DEX_SCREENER) { DexScreener.marketOf(get(DexScreener.tokenUrl(asset), PriceHosts.DEX_SCREENER), asset) }
        dex.market?.let { return it }
        val gecko = ask(PriceHosts.GECKO_TERMINAL) { GeckoTerminal.market(get(GeckoTerminal.poolsUrl(asset), PriceHosts.GECKO_TERMINAL), asset) }
        if (gecko.market == null && dex.refused && gecko.refused) {
            throw TickerException(TickerErrorKind.UNAVAILABLE, "Neither DexScreener nor GeckoTerminal could price ${asset.id}", gecko.error ?: dex.error)
        }
        return gecko.market
    }

    private suspend fun priced(market: CryptoMarket): CryptoMarket =
        if (market.priced) market else priceResult { fetch(market.asset) }.getOrNull() ?: market

    private class Answer(val market: CryptoMarket?, val refused: Boolean, val error: Throwable? = null)

    /** What [source] says, unless it rests; a failure rests it. */
    private suspend fun ask(source: String, lookup: suspend () -> CryptoMarket?): Answer {
        if (health.resting(source)) return Answer(null, refused = true)
        val asked = priceResult { lookup() }
        val error = asked.exceptionOrNull()
        return if (error == null) {
            health.answered(source)
            Answer(asked.getOrNull(), refused = false)
        } else {
            health.refused(source)
            Answer(null, refused = true, error = error)
        }
    }

    /** [url]'s JSON; nothing (`J(null)`) for a status that means [host] has no such token. */
    private suspend fun get(url: HttpUrl, host: String): J = try {
        PriceHttp.getJson(http, url, host, userAgent(), timeout)
    } catch (error: NetworkRequestException) {
        if (error.code in PriceHosts.notFoundCodes(host)) J(null) else throw error
    }

    companion object {
        /** How many networks one pasted contract is priced on. */
        const val MAX_NETWORKS = 6
    }
}
