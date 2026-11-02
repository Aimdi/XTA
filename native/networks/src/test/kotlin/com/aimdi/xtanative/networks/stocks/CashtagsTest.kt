package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.x.model.XTimelinePage
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CashtagsTest {
    private val now: Instant = Instant.parse("2026-11-02T15:00:00Z")

    private fun live(symbol: String, price: Double) = TickerQuote(
        symbol = symbol,
        currency = "USD",
        price = price,
        previousClose = 100.0,
        regularPeriod = TradingPeriod(now.minus(Duration.ofHours(2)), now.plus(Duration.ofHours(2))),
        points = (0 until 6).map { i -> TickerPoint(now.minus(Duration.ofMinutes(60 - i * 10L)), 100 + (price - 100) * i / 5) },
    )

    private fun closed(symbol: String, price: Double) = TickerQuote(
        symbol = symbol,
        currency = "USD",
        price = price,
        previousClose = 100.0,
        regularMarketTime = now.minus(Duration.ofDays(1)),
        regularPeriod = TradingPeriod(now.minus(Duration.ofHours(30)), now.minus(Duration.ofHours(24))),
        points = emptyList(),
    )

    @Test
    fun cashtagsOnAPostAreUpperCasedAndUnique() {
        assertEquals(listOf("AAPL", "TSLA"), Cashtags.of(post("1", cashtags = listOf("aapl", "AAPL", "TSLA"))))
        assertTrue(Cashtags.of(post("2")).isEmpty(), "a post with no symbols has none")
    }

    @Test
    fun theStripListsTheFirstFourCashtagsInOrderEachWithItsPriceAndMove() {
        val symbols = listOf("AAPL", "NVDA", "TSLA", "MSFT", "AMZN")
        val quotes = symbols.associateWith { live(it, if (it == "NVDA") 95.0 else 105.0) }
        val strip = Cashtags.strip(post("1", cashtags = symbols), quotes, emptySet(), now)
        assertEquals(listOf("AAPL", "NVDA", "TSLA", "MSFT"), strip.map { it.symbol }, "four at most, in the order the post names them")
        assertEquals(listOf("+5.00%", "−5.00%", "+5.00%", "+5.00%"), strip.map { StockNumbers.percent(it.changePercent!!) })
        assertTrue(strip.all { it.state == StockRowState.LIVE && it.sparkline.size == 6 && !it.muted })
        assertEquals(Trend.DOWN, StockNumbers.trend(strip[1].changePercent))
    }

    @Test
    fun aGreyBarWhileLoadingNoPriceWithoutOneAndAClosedMarketMuted() {
        val strip = Cashtags.strip(post("1", cashtags = listOf("TSLA", "NVDA", "MSFT")), mapOf("MSFT" to closed("MSFT", 105.0)), setOf("NVDA"), now)
        assertEquals(listOf(StockRowState.LOADING, StockRowState.NO_DATA, StockRowState.CLOSED), strip.map { it.state })
        assertNull(strip[0].price)
        assertNull(strip[1].price, "No price")
        assertNull(strip[1].changePercent)
        assertEquals(105.0, strip[2].price)
        assertEquals(5.0, strip[2].changePercent!!, 1e-9)
        assertTrue(strip[2].muted, "a closed market is not news")
        assertTrue(strip[2].sparkline.isEmpty(), "no line for a closed market")
    }

    @Test
    fun aRepostsStripIsTheOriginalsAndAPostWithoutCashtagsHasNone() {
        val original = post("1", author = "b", cashtags = listOf("AAPL"))
        val repost = post("2", author = "a", reposted = original)
        assertEquals(listOf("AAPL"), Cashtags.stripSymbols(repost))
        assertTrue(Cashtags.strip(post("3"), emptyMap(), emptySet(), now).isEmpty())
    }

    @Test
    fun aTelegramInviteWithThreeOrMoreCashtagsIsASignalGroupPostEitherAloneIsNot() {
        fun signal(cashtags: List<String>, links: List<String> = emptyList()) = SignalGroups.isSignalPost(post("p", cashtags = cashtags, links = links))
        assertTrue(signal(listOf("BTC", "ETH", "SOL"), listOf("https://t.me/pumps")))
        assertTrue(signal(listOf("BTC", "ETH", "SOL"), listOf("https://telegram.me/pumps")))
        assertTrue(signal(listOf("BTC", "ETH", "SOL"), listOf("https://T.ME/pumps")))
        assertFalse(signal(listOf("BTC", "ETH"), listOf("https://t.me/pumps")))
        assertFalse(signal(listOf("BTC", "ETH", "SOL"), listOf("https://example.com/t.me")))
        assertFalse(signal(listOf("BTC", "ETH", "SOL")))
        assertFalse(signal(listOf("BTC", "btc", "ETH"), listOf("https://t.me/pumps")), "the same cashtag twice counts once")
    }

    @Test
    fun signalGroupPostsGoToTheEndOfTheirPageBothGroupsInTheirOrderNothingHidden() {
        val page = listOf(
            thread("1", post("1", cashtags = listOf("A", "B", "C"), links = listOf("https://t.me/x"))),
            thread("2", post("2", cashtags = listOf("AAPL"))),
            thread("3", post("3", cashtags = listOf("D", "E", "F"), links = listOf("http://www.t.me/y"))),
            thread("4", post("4")),
        )
        assertEquals(listOf("2", "4", "1", "3"), SignalGroups.last(page).map { it.id })
        assertEquals(3, SignalGroups.cashtags(page[0]), "its banner counts its cashtags")
        assertNull(SignalGroups.cashtags(page[1]))
        val feedPage = SignalGroups.page(XTimelinePage(page, bottomCursor = "more", topCursor = null))
        assertEquals(listOf("2", "4", "1", "3"), feedPage.items.map { it.id })
        assertEquals("more", feedPage.next)
    }
}
