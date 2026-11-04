package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.CryptoAddresses
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

class CryptoClientTest {
    private val token = "0x1111111111111111111111111111111111111111"
    private val weth = "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"
    private val asset = CryptoAsset(chain = "ethereum", address = token, symbol = "SAME", name = "Same")
    private val pepe = "0x6982508145454Ce325dDbE47a25d4ec3d2311933"
    private val health = PriceSourceHealth()

    private val pools = answer(fixture("geckoterminal_pools.json"))
    private val dexPair = answer(listOf(mapOf(
        "chainId" to "ethereum",
        "baseToken" to mapOf("address" to token, "symbol" to "SAME", "name" to "Same"),
        "quoteToken" to mapOf("address" to weth, "symbol" to "WETH"),
        "pairAddress" to "0xdexpool",
        "priceUsd" to "0.04",
        "priceChange" to mapOf("h24" to 3),
        "liquidity" to mapOf("usd" to 900),
    )))

    private fun client(dex: XResponse, gecko: XResponse): Pair<CryptoClient, FakeHttp> {
        val http = FakeHttp { url -> if (url.host == DexScreener.HOST) dex else gecko }
        return CryptoClient(http, health) to http
    }

    @Test
    fun geckoTerminalIsAskedUnderItsOwnNameForTheNetwork() {
        assertEquals("https://api.geckoterminal.com/api/v2/networks/eth/tokens/$token/pools?page=1", GeckoTerminal.poolsUrl(asset).toString())
        assertEquals("polygon_pos", GeckoTerminal.network("polygon"))
        assertEquals("avax", GeckoTerminal.network("avalanche"))
        assertEquals("solana", GeckoTerminal.network("solana"))
        assertEquals("base", GeckoTerminal.network("Base"))
    }

    @Test
    fun geckoTerminalPricesTheTokenFromTheFirstPoolItIsTheBaseOfWithThatPoolsFigures() {
        val market = GeckoTerminal.market(J.parse(fixture("geckoterminal_pools.json")), asset)!!
        assertSame(asset, market.asset)
        assertEquals(0.05, market.price, "a pool quoting the token prices the other one")
        assertEquals(1.5, market.change1h)
        assertEquals(-2.25, market.change6h)
        assertEquals(12.5, market.changePercent)
        assertEquals(1000.5, market.liquidity)
        assertEquals(5000.0, market.volume24h)
        assertNull(market.marketCap)
        assertEquals(123456.0, market.fdv)
        assertEquals("0xpool", market.pairAddress)
    }

    @Test
    fun geckoTerminalHasNoMarketWhenTheTokenIsNoPoolsBaseOrThePoolHasNoPrice() {
        val pools = J.parse(fixture("geckoterminal_pools.json"))
        assertNull(GeckoTerminal.market(j(mapOf("data" to listOf(pools["data"][0].raw))), asset))
        val unpriced = pools["data"][1].raw.toString().replace("\"0.05\"", "\"n/a\"")
        assertNull(GeckoTerminal.market(J.parse("""{"data":[$unpriced]}"""), asset))
        assertNull(GeckoTerminal.market(J(null), asset))
    }

    @Test
    fun aTokensQuoteRejectsAPoolWhereTheTokenIsTheQuoteCurrency() = runBlocking {
        val (tokens, http) = client(answer(fixture("dexscreener_token_pairs.json")), pools)
        assertEquals(0.05, tokens.fetch(asset)!!.price)
        assertEquals("/token-pairs/v1/ethereum/$token", http.asked.single().encodedPath)
    }

    @Test
    fun dexScreenerIsAskedFirstAndGeckoTerminalNotAtAllWhenItAnswers() = runBlocking {
        val (tokens, http) = client(dexPair, pools)
        assertEquals(0.04, tokens.fetch(asset)!!.price)
        assertEquals(listOf(DexScreener.HOST), http.hosts)
        assertEquals(TickerClient.DEFAULT_USER_AGENT, http.headers.single()["User-Agent"])
    }

    @Test
    fun geckoTerminalAnswersWhenDexScreenerRefusesWhichLeavesDexScreenerResting() = runBlocking {
        val (tokens, http) = client(answer("", 429), pools)
        assertEquals(0.05, tokens.fetch(asset)!!.price)
        assertTrue(health.resting(PriceHosts.DEX_SCREENER))
        http.clear()
        tokens.fetch(asset)
        assertEquals(listOf(GeckoTerminal.HOST), http.hosts, "DexScreener rests")
    }

    @Test
    fun geckoTerminalAnswersWhenDexScreenerHasNoMarketWithoutRestingIt() = runBlocking {
        val (tokens, _) = client(answer("[]"), pools)
        assertEquals(0.05, tokens.fetch(asset)!!.price)
        assertFalse(health.resting(PriceHosts.DEX_SCREENER))
    }

    @Test
    fun noMarketAnywhereIsNoMarketAndNoAnswerAnywhereIsAFailure() = runBlocking {
        val (unknown, _) = client(answer("[]"), answer("""{"errors":[]}""", 404))
        assertNull(unknown.fetch(asset))
        assertFalse(health.resting(PriceHosts.GECKO_TERMINAL), "a 404 says it has no such token")
        val (down, _) = client(answer("", 503), answer("", 429))
        assertEquals(TickerErrorKind.UNAVAILABLE, assertFailsWith<TickerException> { down.fetch(asset) }.kind)
        assertTrue(health.resting(PriceHosts.GECKO_TERMINAL))
        assertEquals(TickerErrorKind.UNAVAILABLE, assertFailsWith<TickerException> { down.fetch(asset) }.kind, "both resting is no answer either")
    }

    @Test
    fun aPastedContractIsSearchedAndItsQuoteOnlyNetworksArePricedThroughTheirPools() = runBlocking {
        val http = FakeHttp { url ->
            if (url.encodedPath == "/latest/dex/search") {
                answer(mapOf("pairs" to listOf(mapOf(
                    "chainId" to "base",
                    "baseToken" to mapOf("address" to weth, "symbol" to "WETH", "name" to "WETH"),
                    "quoteToken" to mapOf("address" to pepe, "symbol" to "PEPE", "name" to "Pepe"),
                    "priceUsd" to "3500",
                ))))
            } else {
                answer(listOf(mapOf(
                    "chainId" to "base",
                    "baseToken" to mapOf("address" to pepe, "symbol" to "PEPE", "name" to "Pepe"),
                    "quoteToken" to mapOf("address" to weth, "symbol" to "WETH"),
                    "priceUsd" to "0.0000123",
                )))
            }
        }
        val markets = CryptoClient(http, health).resolve(CryptoAddresses.detect(pepe)!!)
        assertEquals(DexScreener.HOST, http.asked.first().host)
        assertEquals(pepe.lowercase(), http.asked.first().queryParameter("q"))
        assertEquals("/token-pairs/v1/base/${pepe.lowercase()}", http.asked.last().encodedPath)
        assertEquals("base", markets.items.single().asset.chain)
        assertEquals(0.0000123, markets.items.single().price!!, 1e-12)
    }

    @Test
    fun aPastedContractIsPricedOnAtMostSixNetworks() = runBlocking {
        val chains = listOf("ethereum", "base", "bsc", "arbitrum", "polygon", "optimism", "avalanche", "blast")
        val searched = mapOf("pairs" to chains.mapIndexed { index, chain ->
            mapOf("chainId" to chain, "baseToken" to mapOf("address" to pepe, "symbol" to "PEPE"), "priceUsd" to "1", "liquidity" to mapOf("usd" to 100 - index))
        })
        val http = FakeHttp { answer(searched) }
        val markets = CryptoClient(http, health).resolve(CryptoAddresses.detect(pepe)!!).items
        assertEquals(chains.take(CryptoClient.MAX_NETWORKS), markets.map { it.asset.chain })
        assertEquals(1, http.asked.size, "priced networks need no second look")
    }

    @Test
    fun aSearchListsTheTokensDexScreenerFound() = runBlocking {
        val http = FakeHttp { answer(fixture("dexscreener_search.json")) }
        val found = CryptoClient(http, health).search("\$PEPE")
        assertEquals("PEPE", http.asked.single().queryParameter("q"))
        assertEquals(listOf("WETH", "PEPE", "WETH"), found.items.map { it.asset.symbol }, "by depth, equal symbols on other contracts apart")
        assertNull(found.next)
    }

    private val HttpUrl.isSearch: Boolean get() = encodedPath == "/latest/dex/search"

    @Test
    fun aSearchThatIsRefusedFailsAsTheHostAnswered() {
        val http = FakeHttp { url -> if (url.isSearch) answer("", 503) else answer("[]") }
        val error = assertFailsWith<com.aimdi.xtanative.networks.NetworkRequestException> { runBlocking { CryptoClient(http, health).search("pepe") } }
        assertEquals(PriceHosts.DEX_SCREENER, error.network)
        assertEquals(503, error.code)
    }
}
