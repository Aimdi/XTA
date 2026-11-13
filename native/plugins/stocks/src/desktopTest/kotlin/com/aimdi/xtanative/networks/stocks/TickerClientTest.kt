package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

class TickerClientTest {
    /** The smallest chart answer the parser takes, for [symbol]. */
    private fun chart(symbol: String, meta: Map<String, Any?> = emptyMap()) =
        answer(yahooChart(mapOf("symbol" to symbol, "currency" to "USD", "regularMarketPrice" to 20.73) + meta, listOf(1767225600, 1767312000), listOf(19.0, 20.73)).raw)

    /** A coin's week chart: 97 half-hour bars, so its last 24 hours start 48 bars before the last, at 148. */
    private fun coinChart(symbol: String) = answer(
        yahooChart(
            mapOf("symbol" to symbol, "currency" to "USD", "instrumentType" to "CRYPTOCURRENCY", "chartPreviousClose" to 1.0),
            (0 until 97).map { 1760083200L + it * 1800 },
            (0 until 97).map { 100.0 + it },
        ).raw,
    )

    private val notFound = answer("no", 404)
    private val HttpUrl.symbol: String get() = pathSegments.last()

    /** Yahoo alone: no exchanges, so coins go to Yahoo too, as in XTA's own client tests. */
    private fun yahooOnly(respond: suspend (HttpUrl) -> XResponse): Pair<TickerClient, FakeHttp> {
        val http = FakeHttp(respond)
        return TickerClient(http, coinSources = emptyList(), health = PriceSourceHealth()) to http
    }

    @Test
    fun theNamesACashtagIsTriedUnderFallThroughToThePairWhenTheBareNameIsUnknown() = runBlocking {
        val (client, http) = yahooOnly { url -> if (url.symbol == "SHIB-USD") chart("SHIB-USD") else notFound }
        assertEquals("SHIB-USD", client.fetchQuote("SHIB").symbol)
        assertEquals(listOf("SHIB", "SHIB-USD"), http.asked.map { it.symbol })
    }

    @Test
    fun aNetworkFailureIsNotRetriedUnderOtherNames() = runBlocking {
        val (client, http) = yahooOnly { throw IOException("no route") }
        val error = assertFailsWith<IOException> { client.fetchQuote("BTC") }
        assertEquals(TickerErrorKind.UNAVAILABLE, TickerErrorKind.of(error))
        assertEquals(1, http.asked.size, "the name was never the problem")
    }

    @Test
    fun aNameNothingAnswersForIsNotFound() = runBlocking {
        val (client, _) = yahooOnly { notFound }
        val error = assertFailsWith<NetworkRequestException> { client.fetchQuote("NOPE") }
        assertEquals(PriceHosts.YAHOO, error.network)
        assertEquals(404, error.code)
        assertEquals(TickerErrorKind.NOT_FOUND, TickerErrorKind.of(error))
    }

    @Test
    fun anIndexThatDoesNotAnswerNeverBecomesAPair() = runBlocking {
        val (client, http) = yahooOnly { url -> if (url.symbol.endsWith("-USD")) chart("NDX-USD") else notFound }
        assertEquals(TickerErrorKind.NOT_FOUND, TickerErrorKind.of(assertFailsWith<NetworkRequestException> { client.fetchQuote("NDX") }))
        assertEquals(listOf("^NDX"), http.asked.map { it.symbol }, "NDX-USD is a token, not the index")
        assertEquals("/v8/finance/chart/%5ENDX", http.asked.single().encodedPath)
    }

    @Test
    fun aRefusalIsUnavailableAndA200ThatIsNotJsonIsABadResponse() = runBlocking {
        val refused = assertFailsWith<NetworkRequestException> { yahooOnly { answer("", 429) }.first.fetchQuote("AAPL") }
        assertEquals(429, refused.code)
        assertEquals(TickerErrorKind.UNAVAILABLE, TickerErrorKind.of(refused))
        val garbled = assertFailsWith<TickerException> { yahooOnly { answer("<html>") }.first.fetchQuote("AAPL") }
        assertEquals(TickerErrorKind.BAD_RESPONSE, garbled.kind)
    }

    @Test
    fun aGoodAnswerBecomesAQuoteAndTheRequestCarriesAnAgent() = runBlocking {
        val (client, http) = yahooOnly { chart("AAPL") }
        val quote = client.fetchQuote("AAPL", range = "5d", interval = "30m")
        assertEquals(2, quote.points.size)
        assertEquals(TickerClient.DEFAULT_USER_AGENT, http.headers.single()["User-Agent"])
        assertEquals("5d", http.asked.single().queryParameter("range"))
        assertEquals("30m", http.asked.single().queryParameter("interval"))
    }

    @Test
    fun theDaysChartCarriesItsExtendedHoursAndNoOtherRangeDoes() = runBlocking {
        val (client, http) = yahooOnly { chart("AAPL") }
        client.fetchChart("AAPL", TickerRange.DAY)
        client.fetchChart("AAPL", TickerRange.YEAR)
        assertEquals(listOf("1d" to "true", "1y" to null), http.asked.map { it.queryParameter("range") to it.queryParameter("includePrePost") })
    }

    @Test
    fun searchNewsAndTrendingAreReadFromTheHost() = runBlocking {
        val (client, http) = yahooOnly { url ->
            if (url.encodedPath.contains("trending")) answer(fixture("yahoo_trending.json")) else answer(fixture("yahoo_search.json"))
        }
        val hits = client.searchSymbols("apple")
        assertEquals(listOf("AAPL", "SPY"), hits.items.map { it.symbol })
        assertNull(hits.next, "the host has no further pages")
        assertEquals("Apple builds in Texas", client.fetchNews("AAPL").items.single().title)
        assertEquals(listOf("NVDA", "BTC-USD", "^GSPC"), client.fetchTrending().items)
        val (search, news) = http.asked
        assertEquals("apple" to ("8" to "0"), search.queryParameter("q") to (search.queryParameter("quotesCount") to search.queryParameter("newsCount")))
        assertEquals("AAPL" to ("0" to "6"), news.queryParameter("q") to (news.queryParameter("quotesCount") to news.queryParameter("newsCount")))
    }

    @Test
    fun aShareRowIsItsDayAgainstThePreviousCloseWithItsExtendedHours() = runBlocking {
        val (client, http) = yahooOnly { url -> chart(url.symbol) }
        val quote = client.rowQuote("AAPL")
        assertEquals(ChangeWindow.PREVIOUS_CLOSE, quote.changeWindow)
        assertEquals("1d" to "true", http.asked.single().let { it.queryParameter("range") to it.queryParameter("includePrePost") })
    }

    @Test
    fun aCoinRowIsAskedForOverTheLast24HoursNotSinceMidnight() = runBlocking {
        val (client, http) = yahooOnly { url -> coinChart(url.symbol) }
        val quote = client.rowQuote("BTC")
        assertEquals("BTC-USD", http.asked.single().symbol)
        assertEquals(mapOf("range" to "5d", "interval" to "30m"), http.asked.single().let { url -> url.queryParameterNames.associateWith { url.queryParameter(it) } })
        assertEquals(ChangeWindow.ROLLING_24H, quote.changeWindow)
        assertEquals(148.0, quote.previousClose)
    }

    @Test
    fun aPairTheCoinListDoesNotNameIsAskedForAgainOver24Hours() = runBlocking {
        val (client, http) = yahooOnly { url -> if (url.symbol == "PEPE") notFound else coinChart(url.symbol) }
        assertEquals(ChangeWindow.ROLLING_24H, client.rowQuote("PEPE").changeWindow)
        assertEquals(listOf("PEPE 1d", "PEPE-USD 1d", "PEPE-USD 5d"), http.asked.map { "${it.symbol} ${it.queryParameter("range")}" })
    }

    @Test
    fun aPickedListingIsAskedForAsTheListingAlone() = runBlocking {
        val (client, http) = yahooOnly { url -> chart(url.symbol, mapOf("instrumentType" to "EQUITY")) }
        assertEquals(TickerKind.STOCK, client.rowQuote("SOL", TickerInstrument.STOCK).kind)
        assertEquals(listOf("SOL"), http.asked.map { it.symbol }, "not SOL-USD")
    }

    @Test
    fun aTickerPageShowsTheRangeAndKeepsTodaysQuoteForItsFigures() = runBlocking {
        var dayFails = false
        val (client, http) = yahooOnly { url -> if (dayFails && url.queryParameter("range") == "1d") answer("", 503) else chart(url.symbol) }
        val day = client.page("AAPL", TickerRange.DAY)
        assertSame(day.chart, day.day, "the day's chart is today's quote")
        val week = client.page("AAPL", TickerRange.WEEK)
        assertNotNull(week.day)
        assertEquals(listOf("1d", "5d", "1d"), http.asked.map { it.queryParameter("range") })
        http.clear()
        client.page("AAPL", TickerRange.MONTH, day = day.chart)
        assertEquals(listOf("1mo"), http.asked.map { it.queryParameter("range") }, "today's quote is not asked for again")
        dayFails = true
        assertNull(client.page("AAPL", TickerRange.YEAR).day, "the figures are extra: their failure is no failure")
    }

    /** Coinbase refuses, Kraken lists only Bitcoin, everyone else has nothing. */
    private fun exchanges(url: HttpUrl): XResponse = when {
        url.host == "api.exchange.coinbase.com" -> answer("", 429)
        url.host == "api.kraken.com" && url.queryParameter("pair") == "XBTUSD" -> answer(
            mapOf(
                "error" to emptyList<String>(),
                "result" to mapOf("XXBTZUSD" to (0 until 10).map { i -> listOf(1793318400L + i * 1800, "${99.5 + i}", "${101.0 + i}", "${99.0 + i}", "${100.0 + i}", "0", "1", 1) }),
            ),
        )
        url.host == "api.kraken.com" -> answer(mapOf("error" to listOf("EQuery:Unknown asset pair")))
        else -> answer("{}", 404)
    }

    @Test
    fun aCoinIsAskedOfTheExchangesAndARefusingOneRests() = runBlocking {
        val health = PriceSourceHealth()
        val http = FakeHttp { exchanges(it) }
        val client = TickerClient(http, health = health)
        val quote = client.fetchQuote("BTC", TickerRange.WEEK.range, TickerRange.WEEK.interval)
        assertEquals("BTC-USD", quote.symbol)
        assertEquals(109.0, quote.price)
        assertEquals(listOf("api.exchange.coinbase.com", "api.kraken.com"), http.hosts)
        assertTrue(health.resting(PriceHosts.COINBASE))
        http.clear()
        client.fetchQuote("BTC", TickerRange.WEEK.range, TickerRange.WEEK.interval)
        assertEquals(listOf("api.kraken.com"), http.hosts, "Coinbase rests")
    }

    @Test
    fun aStockIsAskedOfYahooAloneAndANameYahooDoesNotListIsThenTriedAsACoin() = runBlocking {
        val http = FakeHttp { exchanges(it) }
        val client = TickerClient(http, health = PriceSourceHealth())
        val week = TickerRange.WEEK
        assertFailsWith<NetworkRequestException> { client.fetchQuote("AAPL", week.range, week.interval, prefer = TickerInstrument.STOCK) }
        assertEquals(listOf(YahooFinance.HOST), http.hosts)
        http.clear()
        val unpriced = assertFailsWith<TickerException> { client.fetchQuote("NOPE", week.range, week.interval) }
        assertEquals(TickerErrorKind.UNAVAILABLE, unpriced.kind, "Coinbase refused, so the coin may exist")
        assertEquals(listOf(YahooFinance.HOST, "api.exchange.coinbase.com", "api.kraken.com", "api.binance.com", YahooFinance.HOST), http.hosts)
    }
}
