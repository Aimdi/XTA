package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.json.J
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YahooFinanceTest {
    @Test
    fun anIndexSymbolIsEncodedOnce() {
        val url = YahooFinance.chartUrl("^ndx", range = "1d", interval = "5m")
        assertTrue(url.toString().startsWith("https://query1.finance.yahoo.com"))
        assertEquals("/v8/finance/chart/%5ENDX", url.encodedPath, "not %255ENDX")
        assertEquals("^NDX", url.pathSegments.last())
    }

    @Test
    fun theChartIsAskedForUpperCasedWithARangeAndInterval() {
        val url = YahooFinance.chartUrl("aapl")
        assertEquals("/v8/finance/chart/AAPL", url.encodedPath)
        assertEquals("1mo", url.queryParameter("range"))
        assertEquals("1d", url.queryParameter("interval"))
        assertEquals(null, url.queryParameter("includePrePost"))
        val day = YahooFinance.chartUrl("AAPL", range = "5d", interval = "30m", includePrePost = true)
        assertEquals("5d", day.queryParameter("range"))
        assertEquals("30m", day.queryParameter("interval"))
        assertEquals("true", day.queryParameter("includePrePost"))
    }

    @Test
    fun searchAndTrendingGoToThePublicHost() {
        val search = YahooFinance.searchUrl("apple", quotes = 8, news = 2)
        assertEquals("/v1/finance/search", search.encodedPath)
        assertEquals("apple", search.queryParameter("q"))
        assertEquals("8", search.queryParameter("quotesCount"))
        assertEquals("2", search.queryParameter("newsCount"))
        assertEquals("/v1/finance/trending/US", YahooFinance.trendingUrl().encodedPath)
    }

    @Test
    fun searchKeepsWhatAWatchlistWantsAndDropsANumberedDepositaryReceipt() {
        val hits = YahooFinance.searchHits(J.parse(fixture("yahoo_search.json")))
        assertEquals(listOf("AAPL", "SPY"), hits.map { it.symbol })
        assertEquals(TickerSearchHit("AAPL", "Apple Inc.", "EQUITY", "NASDAQ"), hits.first())
        assertEquals("SPDR S&P 500 ETF Trust", hits.last().name, "the long name when there is no short one")
        assertEquals("PCX", hits.last().exchange, "the exchange code when it has no display name")
    }

    @Test
    fun aReshapedSearchIsAnEmptyListNotAThrow() {
        for (body in listOf("null", "\"nope\"", """{"quotes":"nope"}""")) assertTrue(YahooFinance.searchHits(J.parse(body)).isEmpty(), body)
    }

    @Test
    fun headlinesAreTheStoriesThatHaveATitle() {
        val news = YahooFinance.news(J.parse(fixture("yahoo_search.json")))
        assertEquals(listOf(TickerNewsItem("Apple builds in Texas", "https://example.com/a", "WSJ")), news)
    }

    @Test
    fun trendingIsTheSymbolsTheHostListsUpperCased() {
        assertEquals(listOf("NVDA", "BTC-USD", "^GSPC"), YahooFinance.trending(J.parse(fixture("yahoo_trending.json"))))
        assertTrue(YahooFinance.trending(J.parse("""{"finance":{}}""")).isEmpty(), "a missing result is nothing to show")
    }
}
