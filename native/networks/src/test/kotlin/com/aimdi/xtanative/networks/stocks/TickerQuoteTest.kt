package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TickerQuoteTest {
    private val aapl = mapOf("symbol" to "AAPL", "currency" to "USD")
    private val threeDays = listOf(1767225600L, 1767312000L, 1767398400L)

    private fun quote(json: J, symbol: String = "AAPL"): TickerQuote = assertNotNull(YahooFinance.quote(json, symbol))

    private fun chart(meta: Map<String, Any?> = aapl, timestamps: List<Long> = threeDays, closes: List<Any?> = listOf(100.0, 110.0, 120.0)) =
        quote(yahooChart(meta, timestamps, closes))

    @Test
    fun aPriceHistoryIsItsClosesAndTheirDatesInOrder() {
        val quote = chart()
        assertEquals("AAPL", quote.symbol)
        assertEquals("USD", quote.currency)
        assertEquals(listOf(100.0, 110.0, 120.0), quote.points.map { it.close })
        assertTrue(quote.points.first().at.isBefore(quote.points.last().at))
    }

    @Test
    fun aDayTheMarketWasShutIsSkippedNotCountedAsZero() {
        assertEquals(listOf(100.0, 120.0), chart(timestamps = listOf(1, 2, 3), closes = listOf(100.0, null, 120.0)).points.map { it.close })
    }

    @Test
    fun integersAndNumericStringsAreBothPricesAndMismatchedListsStopAtTheShorter() {
        assertEquals(listOf(100.0, 110.5, 120.0), chart(closes = listOf(100, "110.5", 120.0)).points.map { it.close })
        assertEquals(1, chart(timestamps = listOf(1, 2, 3), closes = listOf(1.0)).points.size)
    }

    @Test
    fun theDaysMoveIsMeasuredAgainstThePreviousClose() {
        val up = chart(meta = mapOf("symbol" to "AAPL", "regularMarketPrice" to 110.0, "chartPreviousClose" to 100.0))
        assertEquals(10.0, up.change!!, 0.001)
        assertEquals(10.0, up.changePercent!!, 0.001)
        assertEquals(true, up.isUp)
        val down = chart(meta = mapOf("regularMarketPrice" to 90.0, "chartPreviousClose" to 100.0))
        assertEquals(-10.0, down.changePercent!!, 0.001)
        assertEquals(false, down.isUp)
    }

    @Test
    fun withoutALivePriceTheLastCloseIsThePriceAndWithoutACloseThereIsNoMove() {
        assertEquals(30.0, chart(closes = listOf(100.0, 110.0, 130.0), meta = mapOf("chartPreviousClose" to 100.0)).change!!, 0.001)
        val unknown = chart(meta = emptyMap())
        assertNull(unknown.change)
        assertNull(unknown.changePercent)
        assertNull(unknown.isUp, "unknown is not the same as flat")
        assertNull(chart(meta = mapOf("regularMarketPrice" to 5.0, "chartPreviousClose" to 0.0)).changePercent, "no dividing by a zero close")
        assertEquals("AAPL", unknown.symbol, "the symbol asked for when the answer omits its own")
    }

    @Test
    fun theFiguresTheMarketPageShowsAreReadWhenSent() {
        val quote = quote(J.parse(fixture("yahoo_chart_day.json")))
        assertEquals(48_200_000.0, quote.volume)
        assertEquals(260.1, quote.yearHigh)
        assertEquals(169.21, quote.yearLow, "a numeric string")
        assertEquals("Apple Inc.", quote.shortName)
        assertEquals(201.0, quote.dayHigh)
        assertEquals(189.5, quote.dayLow)
        assertEquals(195.0, quote.dayOpen, "the first open inside the regular session")
        assertEquals(TickerKind.STOCK, quote.kind)
        assertEquals(Instant.ofEpochSecond(1760126400), quote.regularMarketTime)
        val bare = chart(meta = emptyMap())
        assertNull(bare.volume)
        assertNull(bare.yearHigh)
        assertEquals(3, bare.points.size, "a symbol quoted without them still charts")
    }

    @Test
    fun aPreMarketPrintIsReportedBesideNotInsteadOfThePrice() {
        val quote = chart(meta = mapOf("regularMarketPrice" to 100.0, "marketState" to "PRE", "preMarketPrice" to 101.5))
        assertEquals(100.0, quote.displayPrice)
        assertEquals(101.5, quote.extendedPrint?.price)
        assertEquals(1.5, quote.extendedChangePercent!!, 0.001)
        assertTrue(quote.isPreMarket)
    }

    @Test
    fun aDayWithoutBarsIsStillAQuoteWithItsMove() {
        val quote = quote(J.parse(fixture("yahoo_chart_weekend.json")), "^NDX")
        assertTrue(quote.points.isEmpty())
        assertEquals(24172.03, quote.displayPrice!!, 1e-9)
        assertEquals(108.63 / 24063.4 * 100, quote.changePercent!!, 1e-9)
        assertEquals(TickerKind.MARKET_INDEX, quote.kind)
        assertEquals(Instant.ofEpochSecond(1761940800), quote.regularMarketTime)
        assertNull(YahooFinance.quote(yahooChart(mapOf("symbol" to "^NDX"), emptyList(), emptyList()), "^NDX"), "neither a price nor bars is no quote")
    }

    @Test
    fun theBadgeReadsTheHostsWordForTheInstrument() {
        fun kindOf(type: String?) = chart(meta = mapOf("instrumentType" to type)).kind
        assertEquals(TickerKind.STOCK, kindOf("EQUITY"))
        assertEquals(TickerKind.ETF, kindOf("ETF"))
        assertEquals(TickerKind.CRYPTO, kindOf("CRYPTOCURRENCY"))
        assertEquals(TickerKind.FUND, kindOf("MUTUALFUND"))
        assertEquals(TickerKind.FUTURE, kindOf("FUTURE"))
        assertEquals(TickerKind.CURRENCY, kindOf("CURRENCY"))
        assertNull(kindOf(null))
    }

    @Test
    fun aCoinOverTheLast24HoursIsMeasuredAgainstTheBarADayBeforeTheLast() {
        fun coin(bars: Int) = chart(
            meta = mapOf("symbol" to "BTC-USD", "instrumentType" to "CRYPTOCURRENCY", "chartPreviousClose" to 1.0),
            timestamps = (0 until bars).map { 1767225600L + it * 1800 },
            closes = (0 until bars).map { 100.0 + it },
        )
        val day = coin(97).rolling24h()
        assertEquals(ChangeWindow.ROLLING_24H, day.changeWindow)
        assertEquals(148.0, day.previousClose, "48 half hours before 196")
        assertEquals(196.0, day.displayPrice)
        assertEquals(48.0 / 148 * 100, day.changePercent!!, 1e-9)
        assertEquals(49, day.points.size)
        assertEquals(148.0, day.points.first().close)
        assertEquals(TickerKind.CRYPTO, day.kind)
        val short = coin(24)
        assertSame(short, short.rolling24h(), "bars that do not reach back a day leave the quote as it was")
        assertEquals(ChangeWindow.PREVIOUS_CLOSE, short.changeWindow)
    }

    @Test
    fun aPayloadThatNoLongerFitsGivesUpRatherThanThrowing() {
        for (body in listOf("null", "\"nonsense\"", "{}", """{"chart":"nope"}""", """{"chart":{"result":[]}}""", """{"chart":{"result":[{"meta":{}}]}}""")) {
            assertNull(YahooFinance.quote(J.parse(body), "AAPL"), body)
        }
        assertNull(YahooFinance.quote(yahooChart(aapl, threeDays, listOf(null, null, null)), "AAPL"), "no usable closes is nothing to draw")
    }

    @Test
    fun theSessionFollowsTheTradingPeriodsTheHostSends() {
        val quote = quote(J.parse(fixture("yahoo_chart_day.json")))
        fun at(seconds: Long) = Instant.ofEpochSecond(seconds)
        assertEquals(TickerSession.PRE, quote.sessionAt(at(1760083200 + 60)))
        assertEquals(TickerSession.REGULAR, quote.sessionAt(at(1760103000 + 60)))
        assertEquals(TickerSession.POST, quote.sessionAt(at(1760126400 + 60)))
        assertEquals(TickerSession.CLOSED, quote.sessionAt(at(1760140800 + 60)))
        assertEquals(TickerSession.CLOSED, quote.sessionAt(at(1760083200 - 60)))
    }

    @Test
    fun aCoinIsNeverClosedAndWithoutPeriodsTheSessionIsUnknown() {
        val coin = chart(meta = mapOf("instrumentType" to "CRYPTOCURRENCY", "regularMarketPrice" to 1.0))
        assertEquals(TickerSession.REGULAR, coin.sessionAt(Instant.parse("2026-01-01T03:00:00Z")))
        assertNull(coin.extendedPrint)
        val bare = chart(meta = mapOf("regularMarketPrice" to 1))
        assertNull(bare.sessionAt(Instant.ofEpochSecond(1767225600)), "it says nothing rather than guessing")
        assertNull(bare.dayOpen)
        assertEquals(TickerSession.PRE, chart(meta = mapOf("marketState" to "PREPRE")).sessionAt(Instant.EPOCH), "older payloads name the session")
    }

    @Test
    fun aBarAfterTheCloseIsTheAfterHoursPrintApartFromThePrice() {
        val quote = quote(J.parse(fixture("yahoo_chart_day.json")))
        assertEquals(200.0, quote.displayPrice, "the regular close, measured against the previous close")
        assertEquals(10.0 / 190 * 100, quote.changePercent!!, 1e-9)
        assertTrue(quote.isAfterHours)
        assertEquals(202.0, quote.extendedPrint?.price)
        assertEquals(1.0, quote.extendedChangePercent!!, 1e-9)
    }

    @Test
    fun aBarInsideTodaysPreMarketIsTheEarlyPrintAndDuringTheSessionThereIsNone() {
        val periods = mapOf(
            "pre" to mapOf("start" to 1760083200, "end" to 1760103000),
            "regular" to mapOf("start" to 1760103000, "end" to 1760126400),
            "post" to mapOf("start" to 1760126400, "end" to 1760140800),
        )
        val meta = mapOf("regularMarketPrice" to 200.0, "chartPreviousClose" to 190.0, "currentTradingPeriod" to periods)
        val early = chart(meta, listOf(1760083500, 1760083800), listOf(191, 192))
        assertTrue(early.isPreMarket)
        assertEquals(192.0, early.extendedPrint?.price)
        assertNull(chart(meta, listOf(1760103000, 1760103300), listOf(195, 200)).extendedPrint)
    }

    @Test
    fun theMoveToAPointIsMeasuredFromWhereTheChartBegan() {
        val quote = chart(meta = mapOf("chartPreviousClose" to 100.0))
        assertEquals(QuoteMove(20.0, 20.0), quote.moveTo(120.0))
        assertNull(quote.moveTo(null))
        assertNull(chart(meta = mapOf("chartPreviousClose" to 0.0)).moveTo(1.0))
        assertNull(chart(meta = emptyMap()).moveTo(1.0), "no reference, no move")
    }
}
