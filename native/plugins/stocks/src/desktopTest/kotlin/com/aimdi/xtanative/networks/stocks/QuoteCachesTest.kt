package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XResponse
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

class QuoteCachesTest {
    private var clock: Instant = Instant.parse("2026-11-02T15:00:00Z")
    private val open = 1760103000L

    private val HttpUrl.symbol: String get() = pathSegments.last()

    /** A one-day chart of [symbol] whose session periods are a day in October 2025. */
    private fun dayChart(symbol: String, type: String = "EQUITY", name: String? = null) = answer(
        yahooChart(
            mapOf(
                "symbol" to symbol,
                "currency" to "USD",
                "instrumentType" to type,
                "shortName" to name,
                "regularMarketPrice" to 200.0,
                "chartPreviousClose" to 190.0,
                "currentTradingPeriod" to mapOf("regular" to mapOf("start" to open, "end" to open + 23400)),
            ),
            listOf(open, open + 300),
            listOf(1, 2),
        ).raw,
    )

    /** A coin's week chart: 97 half-hour bars, so its last 24 hours start 48 bars before the last, at 148. */
    private fun coinChart(symbol: String) = answer(
        yahooChart(
            mapOf("symbol" to symbol, "currency" to "USD", "instrumentType" to "CRYPTOCURRENCY", "chartPreviousClose" to 1.0),
            (0 until 97).map { open + it * 1800L },
            (0 until 97).map { 100.0 + it },
        ).raw,
    )

    private fun cache(picks: Map<String, TickerInstrument> = emptyMap(), kept: MutableList<Map<String, TickerInstrument>> = mutableListOf(), respond: suspend (HttpUrl) -> XResponse): Pair<TickerQuoteCache, FakeHttp> {
        val http = FakeHttp(respond)
        val client = TickerClient(http, coinSources = emptyList(), health = PriceSourceHealth())
        return TickerQuoteCache(client, picks, onPicks = { kept.add(it) }, now = { clock }) to http
    }

    @Test
    fun everySymbolIsFilledNotOnlyTheFirstFewWithOneDayQuotes() = runBlocking {
        val (cache, http) = cache { url -> dayChart(url.symbol) }
        val symbols = listOf("AAPL", "MSFT", "NVDA", "TSLA", "AMZN", "META", "GOOG", "NFLX", "AMD", "INTC")
        cache.ensure(symbols)
        assertEquals(symbols.toSet(), cache.quotes.value.quotes.keys)
        assertTrue(http.asked.all { it.queryParameter("range") == "1d" && it.queryParameter("includePrePost") == "true" })

        cache.ensure(listOf("aapl"))
        assertEquals(10, http.asked.size, "a fresh quote is not asked for again")
        clock = clock.plus(TickerQuoteCache.TTL)
        cache.ensure(listOf("AAPL"))
        assertEquals(11, http.asked.size, "a stale one is")
        cache.refresh(listOf("AAPL"))
        assertEquals(12, http.asked.size, "pull-to-refresh asks again")
    }

    @Test
    fun aSymbolWhoseLookupFailedIsRememberedUntilItAnswers() = runBlocking {
        var podAnswers = false
        val (cache, _) = cache { url -> if (url.symbol.startsWith("POD") && !podAnswers) answer("no", 404) else dayChart(url.symbol) }
        cache.ensure(listOf("POD", "AAPL"))
        assertEquals(setOf("POD"), cache.quotes.value.failed)
        assertEquals(setOf("AAPL"), cache.quotes.value.quotes.keys)

        podAnswers = true
        cache.refresh(listOf("pod"))
        assertTrue(cache.quotes.value.failed.isEmpty())
        assertEquals(setOf("AAPL", "POD"), cache.quotes.value.quotes.keys)
    }

    @Test
    fun aCoinIsAskedForOverTheLast24HoursNotSinceMidnight() = runBlocking {
        val (cache, http) = cache { url -> coinChart(url.symbol) }
        cache.ensure(listOf("BTC"))
        assertEquals("BTC-USD", http.asked.single().symbol)
        assertEquals("5d" to "30m", http.asked.single().let { it.queryParameter("range") to it.queryParameter("interval") })
        assertEquals(ChangeWindow.ROLLING_24H, cache.peek("btc")?.changeWindow)
        assertEquals(148.0, cache.peek("BTC")?.previousClose)
    }

    @Test
    fun theTickerPageSharesAShareDayChartNotACoins() = runBlocking {
        val (cache, http) = cache { answer("", 500) }
        val share = YahooFinance.quote(J.parse(dayChart("AAPL").body), "AAPL")!!
        val coinDay = YahooFinance.quote(J.parse(dayChart("BTC-USD", type = "CRYPTOCURRENCY").body), "BTC-USD")!!
        cache.remember("aapl", share)
        cache.remember("BTC", coinDay)
        assertEquals(setOf("AAPL"), cache.quotes.value.quotes.keys)
        cache.ensure(listOf("AAPL"))
        assertTrue(http.asked.isEmpty(), "a remembered quote is fresh")
    }

    @Test
    fun aCoinFirstCashtagOnTheWatchlistFindsItsListingOnce() = runBlocking {
        val (cache, http) = cache { url -> if (url.symbol.endsWith("-USD")) coinChart(url.symbol) else dayChart(url.symbol, name = "Emeren Group Ltd") }
        cache.ensureReadings(listOf("SOL", "AAPL"))
        cache.ensureReadings(listOf("sol"))
        assertEquals(listOf("SOL"), http.asked.map { it.symbol }, "\$AAPL names one thing; \$SOL is asked for once")
        assertEquals("Emeren Group Ltd", cache.listingFor("sol")?.shortName)
        assertEquals(listOf(TickerInstrument.CRYPTO, TickerInstrument.STOCK), cache.readings("SOL")?.map { it.instrument })
    }

    @Test
    fun thePickIsKeptAndDecidesWhatTheSymbolIsAskedForAs() = runBlocking {
        val kept = mutableListOf<Map<String, TickerInstrument>>()
        val respond: suspend (HttpUrl) -> XResponse = { url -> if (url.symbol.endsWith("-USD")) coinChart(url.symbol) else dayChart(url.symbol, name = "Emeren Group Ltd") }
        val (cache, _) = cache(kept = kept, respond = respond)
        cache.ensureReadings(listOf("SOL"))
        cache.choose("sol", TickerInstrument.STOCK)
        assertEquals(listOf("SOL=stock"), TickerPicks.encode(kept.single()))
        assertEquals(TickerKind.STOCK, cache.peek("SOL")?.kind)
        assertNull(cache.readings("SOL"), "nothing left to choose")

        val (later, http) = cache(picks = TickerPicks.decode(TickerPicks.encode(kept.single())), respond = respond)
        assertEquals(TickerInstrument.STOCK, later.instrumentFor("sol"))
        later.ensure(listOf("SOL"))
        assertEquals(listOf("SOL"), http.asked.map { it.symbol }, "not SOL-USD")
        assertEquals("Emeren Group Ltd", QuoteRows.name("SOL", later.peek("SOL"), later.instrumentFor("SOL")))
    }

    @Test
    fun aTokenWhoseLookupFoundNoMarketSaysSoAndARetryAsksAgain() = runBlocking {
        val asset = CryptoAsset("ethereum", "0x1111111111111111111111111111111111111111", "SAME", "Same")
        var listed = false
        val http = FakeHttp { url ->
            when {
                url.host == DexScreener.HOST && listed -> answer(listOf(mapOf("chainId" to "ethereum", "baseToken" to mapOf("address" to asset.address, "symbol" to "SAME"), "priceUsd" to "0.05")))
                url.host == DexScreener.HOST -> answer("[]")
                else -> answer("", 404)
            }
        }
        val tokens = CryptoQuoteCache(CryptoClient(http, PriceSourceHealth()), now = { clock })
        tokens.ensure(listOf(asset))
        assertEquals(setOf(asset.id), tokens.markets.value.failed)
        tokens.ensure(listOf(asset))
        assertEquals(2, http.asked.size, "a fresh answer, even no market, is not asked for again")
        listed = true
        tokens.ensure(listOf(asset), force = true)
        assertTrue(tokens.markets.value.failed.isEmpty())
        assertEquals(0.05, tokens.markets.value.quotes[asset.id]?.displayPrice)
    }

    @Test
    fun theReadingsKeptOnTheDeviceArriveLateAndASymbolWhoseReadingChangedIsAskedAgain(): Unit = runBlocking {
        val (cache, http) = cache { url -> dayChart(url.symbol) }
        cache.ensure(listOf("ETH", "AAPL"))
        val asked = http.asked.size

        cache.restore(mapOf("eth" to TickerInstrument.STOCK))

        assertEquals(TickerInstrument.STOCK, cache.instrumentFor("ETH"))
        assertNull(cache.peek("ETH"), "the coin's price is not shown under the listing's reading")
        assertEquals(setOf("AAPL"), cache.quotes.value.quotes.keys)
        cache.restore(mapOf("ETH" to TickerInstrument.STOCK))
        cache.ensure(listOf("ETH", "AAPL"))
        assertEquals(asked + 1, http.asked.size, "only ETH is asked for again")
    }
}
