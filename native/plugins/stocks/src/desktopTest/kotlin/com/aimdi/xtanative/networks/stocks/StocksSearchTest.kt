package com.aimdi.xtanative.networks.stocks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class StocksSearchTest {
    private val bonk = "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263"

    private val http = FakeHttp { url ->
        when (url.host) {
            YahooFinance.HOST -> answer(fixture("yahoo_search.json"))
            else -> answer(
                mapOf(
                    "pairs" to listOf(
                        mapOf("chainId" to "solana", "baseToken" to mapOf("address" to bonk, "symbol" to "Bonk", "name" to "Bonk"), "priceUsd" to "0.00002"),
                    ),
                ),
            )
        }
    }
    private val health = PriceSourceHealth()
    private val search = StocksSearch(TickerClient(http, health = health), CryptoClient(http, health))

    @Test
    fun aPastedAddressSwitchesToTokensAtOnceAndAsksForNoStock() = runBlocking {
        val result = search.run("  $bonk ", crypto = false)
        assertTrue(result.crypto)
        assertEquals(CryptoAddressKind.SOLANA, result.address?.kind)
        assertEquals(bonk, result.tokens.single().asset.address)
        assertEquals(listOf(DexScreener.HOST), http.hosts, "no stock search for an address")
        assertNull(result.typedTicker)
    }

    @Test
    fun theStocksChipSearchesSymbolsAndOffersTheTypedTickerWhenNoHitIsIt() = runBlocking {
        val result = search.run("apple", crypto = false)
        assertEquals(listOf("AAPL", "SPY"), result.stocks.map { it.symbol })
        assertEquals("APPLE", result.typedTicker)
        assertNull(search.run("aapl", crypto = false).typedTicker, "\$AAPL is a hit already")
        assertEquals(listOf(YahooFinance.HOST, YahooFinance.HOST), http.hosts)
    }

    @Test
    fun theCryptoChipSearchesTokensByName() = runBlocking {
        val result = search.run("bonk", crypto = true)
        assertEquals("Bonk", result.tokens.single().asset.symbol)
        assertNull(result.address)
        assertEquals("bonk", http.asked.single().queryParameter("q"))
    }

    @Test
    fun anEmptyQueryAsksNothing() = runBlocking {
        assertEquals(StocksSearchResult("", crypto = true), search.run("   ", crypto = true))
        assertTrue(http.asked.isEmpty())
    }
}
