package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * The live price hosts, as XTA's test/live/sources_smoke_test.dart reads
 * them: a share's day from Yahoo Finance, and Bitcoin from the first coin
 * source that answers. Skipped unless RUN_LIVE=true.
 */
class StocksLiveTest {
    private fun live() = assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read the live price hosts")

    @Test
    fun aStockQuote() = runBlocking {
        live()
        val quote = TickerClient(OkHttpXHttp()).fetchChart("AAPL", TickerRange.DAY)
        assertEquals("AAPL", quote.symbol)
        assertTrue((quote.displayPrice ?: 0.0) > 0, "AAPL has a price: ${quote.displayPrice}")
        println("LIVE AAPL ${quote.displayPrice} ${quote.currency}, ${quote.points.size} bars, ${quote.kind}")
    }

    @Test
    fun aCryptoQuote() = runBlocking {
        live()
        val quote = TickerClient(OkHttpXHttp()).rowQuote("BTC")
        assertEquals("BTC-USD", quote.symbol)
        assertEquals(ChangeWindow.ROLLING_24H, quote.changeWindow)
        assertTrue((quote.displayPrice ?: 0.0) > 1000, "Bitcoin has its price: ${quote.displayPrice}")
        println("LIVE BTC ${quote.displayPrice} ${quote.currency} (USDT is Binance's), ${StockNumbers.percent(quote.changePercent ?: 0.0)} over 24h")
    }
}
